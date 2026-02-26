# SmartHome Gateway v1 (dev)

This is a small local HTTP gateway that connects:

Mobile app (ASR text) -> smarthome_core (parse/validate) -> Home Assistant (execute)

It is designed so that you can later plug an LLM parser **without changing the mobile app**.

## Run on Windows (PowerShell)

From your project root (the folder that contains `smarthome_core/`, `registry/`, `lexicon/`, `schemas/`):

1) Create and activate venv (optional but recommended)

```powershell
python -m venv .venv
.\.venv\Scripts\Activate.ps1
```

2) Install deps

```powershell
pip install -r requirements_gateway.txt
```

3) Set environment variables

```powershell
$env:HA_URL = "http://homeassistant.local:8123"
$env:HA_TOKEN = "PASTE_YOUR_LONG_LIVED_TOKEN"
$env:GATEWAY_API_KEY = "local-dev-key"   # optional but recommended
$env:SH_CORE_ROOT = "."                  # project root with assets
```

4) Start gateway

```powershell
python -m uvicorn smarthome_gateway.main:app --host 0.0.0.0 --port 8099
```

5) Test

```powershell
Invoke-RestMethod -Method Post `
  -Uri "http://127.0.0.1:8099/v1/command" `
  -Headers @{"X-API-Key"="local-dev-key"} `
  -ContentType "application/json" `
  -Body '{"text":"в спальне сделай свет потише","parser_mode":"rules","dry_run":false}'
```

Health:

```powershell
Invoke-RestMethod http://127.0.0.1:8099/health
```

## LLM readiness

Gateway supports `parser_mode`:
- `rules` (default)
- `llm_safe` (LLM + fallback to rules)
- `llm` (LLM only)

To enable LLM later (OpenAI-compatible endpoint):

- `LLM_BASE_URL` (e.g. http://127.0.0.1:8000)
- `LLM_MODEL`
- `LLM_API_KEY` (optional)

If LLM is not configured:
- `llm_safe` will fall back to `rules`
- `llm` will return an error

## Logs

Gateway appends compact JSONL logs to:
- `${GATEWAY_LOG_DIR}/commands.jsonl` (default: `<project_root>/gateway_logs/commands.jsonl`)

By default it stores **redacted** text unless device registry allows raw logging.
