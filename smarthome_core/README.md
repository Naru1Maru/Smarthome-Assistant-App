# Ядро SmartHome Assistant

Каталог `smarthome_core/` содержит независимый от приложения движок: правила, схемы JSON, валидатор и инструменты для интеграции с Home Assistant. Его можно использовать отдельно от мобильного клиента.

## Что внутри
- `smarthome_core/` — Python‑пакет с правилами, валидатором и LLM-парсером.
- `lexicon/` — словари комнат, цветов, модификаторов.
- `registry/` — пример реестра устройств/зон Home Assistant.
- `schemas/` — схемы `ParsedCommand` и `ValidatedCommand` для валидации JSON.
- `data/` — gold-датаcет фраз и ожидаемых команд.
- `tests/` — набор pytest для регрессии.
- `smarthome_gateway/` — FastAPI-приложение (add-on) поверх ядра.
- `llama_openai_bridge.py` — мост OpenAI API ↔ llama.cpp для локальной LLM.

## Быстрый старт
```powershell
cd smarthome_core
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements_gateway.txt  # включает зависимости ядра и шлюза
```

### Проверка правил и валидатора
```powershell
python -m smarthome_core.cli eval-nlu        # только парсер
python -m smarthome_core.cli eval-val        # только валидатор
python -m smarthome_core.cli eval            # полный прогон на gold-наборе
```
Отчёты складываются в `reports/`.

### Запуск пайплайна по одной команде
```powershell
python -m smarthome_core.cli run-light --text "включи свет в спальне"
```
Ответ содержит `stage` (parse/validate/execute), промежуточные JSONы и тайминги.

## Режимы парсера
- `rules` — детерминированные правила (минимальные уточнения).
- `llm` — только LLM (Qwen2.5 или другая OpenAI-совместимая модель).
- `llm_safe` — сначала правила, если не справились — LLM. Это режим по умолчанию для продакшена.

LLM настроена через параметры CLI или переменные окружения:
```
--llm-backend openai_compat
--llm-base-url http://127.0.0.1:8080
--llm-model qwen2.5-7b-instruct
--llm-api-key <если требуется>
```

## Запуск dry-run/exec против Home Assistant
```powershell
$env:HA_URL = "http://homeassistant.local:8123"
$env:HA_TOKEN = "<Long Lived Token>"
python -m smarthome_core.cli ha-dry-run --text "сделай свет потише в спальне"
python -m smarthome_core.cli ha-exec --text "выключи свет на кухне"
```
В dry-run ядро строит план без выполнения. `ha-exec` шлёт реальные вызовы через Supervisor proxy.

## LLM parser + fallback
Файл `smarthome_core/parser_llm.py` реализует структуру результата, валидацию по JSON Schema и fallback к правилам (если LLM вернула мусор или потребовала уточнение). Логи и статистику можно смотреть в `reports/llm_*.jsonl` после запуска `cli eval-e2e`.

## Дополнительные команды CLI
- `validate-gold` — проверка, что gold-датаcет согласован со схемами.
- `make-smoke-set` — формирует стабильный набор тестов для CI.
- `eval-e2e --parser-mode llm_safe` — главный KPI: сколько команд прошли всю цепочку (parse → validate).

## Приватность и логи
В модуле `privacy.py` есть `redact_text()` и политики логирования. Рекомендуем хранить только обезличенный текст и не собирать аудио, если это не требуется для отладки.
