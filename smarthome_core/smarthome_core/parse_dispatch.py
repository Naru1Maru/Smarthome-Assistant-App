"""Parser selection / dispatch for v1 pipeline."""

from __future__ import annotations

from typing import Any, Dict, Optional

from .parser import parse_light_command_v1
from .parser_llm import parse_light_command_llm_v1
from .llm_client import LLMClient


def parse_light_command_v1_dispatch(
    text: str,
    *,
    parser_mode: str,
    context: Dict[str, Any],
    device_registry: Dict[str, Any],
    area_synonyms: Dict[str, Any],
    colors: Dict[str, Any],
    modifiers: Dict[str, Any],
    parsed_schema: Optional[Dict[str, Any]] = None,
    llm_client: Optional[LLMClient] = None,
    llm_fallback_to_rules: bool = True,
) -> Dict[str, Any]:
    """Select parser implementation.

    parser_mode:
      - "rules": robust rule parser (baseline)
      - "llm_safe": LLM parser + fallback to rules (recommended)
      - "llm": LLM parser without fallback (for evaluation)

    Notes:
      - For llm_* modes you must pass parsed_schema + llm_client.
    """
    mode = (parser_mode or "rules").strip().lower()
    # normalize aliases
    if mode == "rules":
        mode = "rules"
    if mode == "llm_only":
        mode = "llm"
    if mode == "llm_fallback":
        mode = "llm_safe"

    strict_llm = False
    if mode in {"llm_only", "llm_strict"}:
        strict_llm = True
        mode = "llm"

    def _run_rules() -> Dict[str, Any]:
        return parse_light_command_v1(
            text,
            context=context,
            device_registry=device_registry,
            area_synonyms=area_synonyms,
            colors=colors,
            modifiers=modifiers,
        )

    def _run_llm(fallback: bool) -> Dict[str, Any]:
        if parsed_schema is None or llm_client is None:
            raise ValueError("parsed_schema and llm_client are required for llm parser modes")
        return parse_light_command_llm_v1(
            text,
            context=context,
            device_registry=device_registry,
            area_synonyms=area_synonyms,
            colors=colors,
            modifiers=modifiers,
            parsed_schema=parsed_schema,
            client=llm_client,
            fallback_to_rules=fallback,
        )

    if mode == "rules":
        return _run_rules()

    if mode == "llm_safe":
        # Сначала правила (быстро и детерминированно)
        rules_parsed = _run_rules()
        if _should_accept_rules(rules_parsed):
            return rules_parsed
        # Не разобрали — подключаем LLM без fallback (чтобы не делать двойную работу)
        return _run_llm(fallback=False)

    if mode == "llm":
        fallback_enabled = llm_fallback_to_rules and not strict_llm
        return _run_llm(fallback=fallback_enabled)

    raise ValueError(f"Unknown parser_mode: {parser_mode}")


def _should_accept_rules(parsed: Dict[str, Any]) -> bool:
    """Return True if rule parser output достаточно хорошее, чтобы не звать LLM."""
    actions = parsed.get("actions") or []
    if not actions:
        return False

    if parsed.get("clarification") is not None:
        return False

    primary_intent = actions[0].get("intent")
    if primary_intent == "UNKNOWN":
        return False

    return True
