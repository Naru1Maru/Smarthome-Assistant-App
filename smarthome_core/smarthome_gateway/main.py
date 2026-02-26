from __future__ import annotations

import json
import os
import sys
import time
import uuid
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Dict, Optional, Literal

from fastapi import FastAPI, Header, HTTPException
from fastapi.responses import JSONResponse


class UTF8JSONResponse(JSONResponse):
    media_type = "application/json; charset=utf-8"
from pydantic import BaseModel, Field

# Ensure project root is on sys.path so `import smarthome_core` works
_PROJECT_ROOT = Path(__file__).resolve().parents[1]
if str(_PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(_PROJECT_ROOT))

from smarthome_core.assets import AssetPaths
from smarthome_core.io import load_json
from smarthome_core.pipeline import run_light_pipeline_v1
from smarthome_core.privacy import redact_text, should_log_raw_text, get_redaction_mode
from smarthome_core.ha_client import HomeAssistantClient
from smarthome_core.executor_ha import execute_validated_on_ha, ExecutionConfig, build_service_calls_from_validated
from smarthome_core.llm_client import OpenAICompatibleClient


ParserMode = Literal["rules", "llm_safe", "llm"]


class CommandContext(BaseModel):
    last_area_name: Optional[str] = None


class CommandRequest(BaseModel):
    text: str = Field(..., min_length=1, max_length=500)
    parser_mode: ParserMode = "rules"
    dry_run: bool = False
    context: Optional[CommandContext] = None
    request_id: Optional[str] = None


class TimingMs(BaseModel):
    parse: int
    validate: int
    execute: int


class CommandResponse(BaseModel):
    ok: bool
    status: Literal["EXECUTED", "DRY_RUN", "NEEDS_CLARIFICATION", "ERROR"]
    request_id: str
    say_text: str
    parser_mode_used: ParserMode
    parsed_command: Dict[str, Any]
    validated_command: Optional[Dict[str, Any]] = None
    calls: list[Dict[str, Any]] = []
    errors: list[Dict[str, Any]] = []
    clarification: Optional[Dict[str, Any]] = None
    timing_ms: TimingMs


def _env(name: str, default: Optional[str] = None) -> Optional[str]:
    v = os.getenv(name)
    if v is None or str(v).strip() == "":
        return default
    return str(v)


def _now_iso() -> str:
    return datetime.now(timezone.utc).isoformat()


def _append_jsonl(path: Path, obj: Dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("a", encoding="utf-8") as f:
        f.write(json.dumps(obj, ensure_ascii=False) + "\n")


def _say_text_for(status: str, clarification: Optional[Dict[str, Any]], errors: list[Dict[str, Any]]) -> str:
    if status == "NEEDS_CLARIFICATION":
        q = (clarification or {}).get("question")
        return str(q) if q else "Уточните, пожалуйста."
    if status in {"EXECUTED", "DRY_RUN"}:
        return "Готово."
    if errors:
        code = errors[0].get("code") or "ERROR"
        return f"Не могу выполнить команду: {code}."
    return "Не могу выполнить команду."


def _load_assets(root_dir: Path) -> Dict[str, Any]:
    paths = AssetPaths(root_dir)
    assets = {
        "paths": paths,
        "device_registry": load_json(paths.device_registry),
        "area_synonyms": load_json(paths.area_synonyms),
        "colors": load_json(paths.colors),
        "modifiers": load_json(paths.modifiers),
        "parsed_schema": None,
    }
    try:
        from smarthome_core.schema_utils import load_schema

        assets["parsed_schema"] = load_schema(paths.parsed_schema)
    except Exception:
        assets["parsed_schema"] = None
    return assets


def _make_llm_client() -> Optional[Any]:
    base_url = _env("LLM_BASE_URL")
    if not base_url:
        return None
    model = _env("LLM_MODEL", "local-model")
    api_key = _env("LLM_API_KEY")
    return OpenAICompatibleClient(base_url=base_url, api_key=api_key, model=model)


def _make_ha_client() -> HomeAssistantClient:
    ha_url = _env("HA_URL", "http://homeassistant.local:8123")
    token = _env("HA_TOKEN")
    if not token:
        raise RuntimeError("HA_TOKEN is not set")

    verify_tls = _env("HA_VERIFY_TLS", "1") != "0"
    timeout_s = float(_env("HA_TIMEOUT_S", "10"))
    return HomeAssistantClient(base_url=ha_url, token=token, timeout_s=timeout_s, verify_tls=verify_tls)


app = FastAPI(title="SmartHome Gateway", version="1.0", default_response_class=UTF8JSONResponse)


@app.on_event("startup")
def _startup() -> None:
    root_dir = Path(_env("SH_CORE_ROOT", str(_PROJECT_ROOT))).resolve()
    app.state.root_dir = root_dir
    app.state.assets = _load_assets(root_dir)
    app.state.llm_client = _make_llm_client()

    log_dir = Path(_env("GATEWAY_LOG_DIR", str(root_dir / "gateway_logs"))).resolve()
    app.state.log_path = log_dir / "commands.jsonl"


@app.get("/health")
def health() -> Dict[str, Any]:
    return {"ok": True, "time_utc": _now_iso(), "version": "1.0"}


@app.post("/v1/command", response_model=CommandResponse)
def command(req: CommandRequest, x_api_key: Optional[str] = Header(default=None, alias="X-API-Key")) -> CommandResponse:
    configured_key = _env("GATEWAY_API_KEY")
    if configured_key and x_api_key != configured_key:
        raise HTTPException(status_code=401, detail="Invalid or missing X-API-Key")

    request_id = req.request_id or str(uuid.uuid4())

    # Context
    ctx = (req.context.model_dump() if req.context is not None else {})
    ctx.setdefault("last_area_name", None)

    parser_mode = (req.parser_mode or "rules").strip().lower()
    llm_client = app.state.llm_client
    parsed_schema = app.state.assets.get("parsed_schema")

    # If llm requested but not configured:
    if parser_mode in {"llm_safe", "llm"} and llm_client is None:
        if parser_mode == "llm":
            raise HTTPException(status_code=400, detail="LLM is not configured (set LLM_BASE_URL)")
        parser_mode_used: ParserMode = "rules"
        llm_client_used = None
    else:
        parser_mode_used = parser_mode  # type: ignore
        llm_client_used = llm_client

    # Parse + validate via pipeline
    t_parse0 = time.perf_counter()
    pipeline_res = run_light_pipeline_v1(
        req.text,
        context=ctx,
        root_dir=app.state.root_dir,
        device_registry=app.state.assets["device_registry"],
        area_synonyms=app.state.assets["area_synonyms"],
        colors=app.state.assets["colors"],
        modifiers=app.state.assets["modifiers"],
        parser_mode=parser_mode_used,
        llm_client=llm_client_used,
        parsed_schema=parsed_schema,
    )
    t_parse1 = time.perf_counter()

    parsed = pipeline_res.parsed
    validated = pipeline_res.validated

    # Parsed-stage clarification
    if pipeline_res.stage == "PARSED_CLARIFICATION":
        clarification = parsed.get("clarification")
        status = "NEEDS_CLARIFICATION"
        errors: list[Dict[str, Any]] = []
        calls: list[Dict[str, Any]] = []
        timing = TimingMs(parse=int((t_parse1 - t_parse0) * 1000), validate=0, execute=0)
        say_text = _say_text_for(status, clarification, errors)
        _log(req.text, request_id, parser_mode_used, status, errors, calls, timing)
        return CommandResponse(
            ok=True,
            status=status,
            request_id=request_id,
            say_text=say_text,
            parser_mode_used=parser_mode_used,
            parsed_command=parsed,
            validated_command=None,
            calls=calls,
            errors=errors,
            clarification=clarification,
            timing_ms=timing,
        )

    # Validated-stage clarification
    if isinstance(validated, dict) and validated.get("clarification"):
        if validated.get("status") in {"NEEDS_CLARIFICATION", "NOT_EXECUTABLE"}:
            clarification = validated.get("clarification")
            status = "NEEDS_CLARIFICATION"
            errors = []
            calls = []
            timing = TimingMs(parse=int((t_parse1 - t_parse0) * 1000), validate=0, execute=0)
            say_text = _say_text_for(status, clarification, errors)
            _log(req.text, request_id, parser_mode_used, status, errors, calls, timing)
            return CommandResponse(
                ok=True,
                status=status,
                request_id=request_id,
                say_text=say_text,
                parser_mode_used=parser_mode_used,
                parsed_command=parsed,
                validated_command=validated,
                calls=calls,
                errors=errors,
                clarification=clarification,
                timing_ms=timing,
            )

    # Execute
    t_ex0 = time.perf_counter()
    if not isinstance(validated, dict):
        errors = [{"code": "NO_VALIDATED", "message": "validated_command is missing"}]
        calls = []
        ok = False
        status = "ERROR"
        t_ex1 = time.perf_counter()
    elif req.dry_run:
        cfg = ExecutionConfig(dry_run=True)
        calls, errors = build_service_calls_from_validated(
            validated,
            device_registry=app.state.assets["device_registry"],
            client=None,
            cfg=cfg,
        )
        ok = len(errors) == 0
        status = "DRY_RUN" if ok else "ERROR"
        t_ex1 = time.perf_counter()
    else:
        try:
            ha_client = _make_ha_client()
            cfg = ExecutionConfig(dry_run=False)
            exec_res = execute_validated_on_ha(
                validated,
                device_registry=app.state.assets["device_registry"],
                client=ha_client,
                cfg=cfg,
            )
            calls = exec_res.calls
            errors = exec_res.errors
            ok = exec_res.ok
            status = "EXECUTED" if ok else "ERROR"
        except Exception as e:
            calls = []
            errors = [{"code": "EXEC_ERROR", "message": str(e)}]
            ok = False
            status = "ERROR"
        t_ex1 = time.perf_counter()

    timing = TimingMs(parse=int((t_parse1 - t_parse0) * 1000), validate=0, execute=int((t_ex1 - t_ex0) * 1000))
    say_text = _say_text_for(status, None, errors)
    _log(req.text, request_id, parser_mode_used, status, errors, calls, timing)

    return CommandResponse(
        ok=ok,
        status=status,
        request_id=request_id,
        say_text=say_text,
        parser_mode_used=parser_mode_used,
        parsed_command=parsed,
        validated_command=validated,
        calls=calls,
        errors=errors,
        clarification=None,
        timing_ms=timing,
    )


def _log(
    raw_text: str,
    request_id: str,
    parser_mode_used: str,
    status: str,
    errors: list[Dict[str, Any]],
    calls: list[Dict[str, Any]],
    timing: TimingMs,
) -> None:
    device_registry = app.state.assets.get("device_registry") or {}
    allow_raw = should_log_raw_text(device_registry)
    redaction_mode = get_redaction_mode(device_registry)
    stored_text = raw_text if allow_raw else redact_text(raw_text, mode=redaction_mode)

    error_codes = [e.get("code") for e in errors][:3]
    services = []
    for c in calls[:5]:
        svc = c.get("service")
        if svc:
            services.append(svc)

    log_obj = {
        "time_utc": _now_iso(),
        "request_id": request_id,
        "text": stored_text,
        "parser_mode_used": parser_mode_used,
        "status": status,
        "ok": status in {"EXECUTED", "DRY_RUN", "NEEDS_CLARIFICATION"} and not errors,
        "errors": error_codes,
        "services": services,
        "timing_ms": timing.model_dump(),
    }

    try:
        _append_jsonl(app.state.log_path, log_obj)
    except Exception:
        # Logging must not break command execution.
        pass
