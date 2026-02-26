# smarthome-core (core engine module v1)

This repo contains an **application-agnostic** core for your diploma project:
- `ParsedCommand` / `ValidatedCommand` JSON Schemas
- deterministic validator (minimal clarifications policy)
- Home Assistant execution plan builder (dry-run)

## Quick start

From this directory:

```bash
python -m smarthome_core.cli validate-gold
python -m smarthome_core.cli smoke
```

## Layout

- `smarthome_core/` — Python package (core engine)
- `schemas/` — JSON schemas
- `lexicon/` — modifiers, area synonyms, colors
- `registry/` — device registry
- `data/` — gold dataset (dual)
- `tests/` — pytest tests

## Notes

- `ADJUST_COLOR_TEMP` leaves `color_temp_kelvin=None` in `execution_plan` and emits
  `PARAM_DROPPED` warning. The runtime executor should resolve it via current state from HA.


## High-level API

- `parse_light_command_v1(text, context=...)` -> ParsedCommand v1
- `validate_parsed_command(parsed, ...)` -> ValidatedCommand v1 (+ execution_plan)
- `run_light_pipeline_v1(text, context=...)` -> PipelineResult(stage, parsed, validated)


## LLM-парсер (опционально)

The project now supports an **LLM-based parser** that produces `ParsedCommand` JSON and then
falls back to the rule parser on errors (recommended for safety).

Режимы парсера: `rules` (baseline), `llm` (только LLM), `llm_safe` (LLM + fallback).



- `--parser-mode ruless` — deterministic rule parser (baseline)
- `--parser-mode llm_safe` — LLM parser + fallback to rules (recommended)
- `--parser-mode llm` — LLM parser only (useful for measuring raw LLM accuracy)

### Evaluate (parser + validator end-to-end)

```bash
python -m smarthome_core.cli eval-e2e --parser-mode rulesss
python -m smarthome_core.cli eval-e2e --parser-mode llm_safe --llm-backend stub
```

### Plug a local LLM server (OpenAI-compatible)

If you run a local server that exposes `/v1/chat/completions` (many do), you can use:

```bash
python -m smarthome_core.cli eval-e2e \
  --parser-mode llm \
  --llm-backend openai_compat \
  --llm-base-url http://127.0.0.1:8000 \
  --llm-model YOUR_MODEL_NAME
```

The implementation is in `smarthome_core/parser_llm.py` + `smarthome_core/llm_client.py`.



### Короткие команды (и алиасы)

- `eval` — полный прогон (NLU + validator + end-to-end)
- `eval-nlu` — только парсер (ParsedCommand)
- `eval-val` — только валидатор (ValidatedCommand)
- `eval-e2e` — парсер→валидатор (главная продуктовая метрика)

Старые команды (`eval-all`, `eval-parsed`, `eval-validated`, `eval-pipeline`) оставлены как алиасы.
## Privacy

Core module provides `redact_text()` to support safe logging. Default recommendation: do not store raw audio; avoid storing raw text; if logging is needed, redact by policy.


## Evaluation

Run evaluations on the gold dataset and generate reports:

```bash
python -m smarthome_core.cli eval --root .
python -m smarthome_core.cli eval-nlu --root .
python -m smarthome_core.cli eval-val --root .
```

Reports are written into `reports/` by default.

Generate a stable smoke subset:

```bash
python -m smarthome_core.cli make-smoke-set --root .
```

This creates `tests/test_cases_smoke.jsonl`.


### Using an alternative dataset

```bash
python -m smarthome_core.cli eval --root . --dataset data/light_gold_dual_v1_ext1.jsonl
python -m smarthome_core.cli validate-gold --root . --dataset data/light_gold_dual_v1_ext1.jsonl
```


## Home Assistant execution (local)

Set token in env and run:

```bash
# Linux/macOS
export HA_TOKEN='...'
python -m smarthome_core.cli ha-dry-run --text 'в спальне сделай свет потише'
python -m smarthome_core.cli ha-exec --text 'в спальне сделай свет потише'

# Windows PowerShell
$env:HA_TOKEN = '...'
python -m smarthome_core.cli ha-dry-run --text "в спальне сделай свет потише"
python -m smarthome_core.cli ha-exec --text "в спальне сделай свет потише"
```

By default base URL is http://homeassistant.local:8123. Override with `--ha-url`.
