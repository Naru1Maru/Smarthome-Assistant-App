# SmartHome Gateway (локальный шлюз)

Лёгкое FastAPI-приложение, которое принимает текстовые команды от мобильного клиента, превращает их в структуру `ParsedCommand/ValidatedCommand` и вызывает Home Assistant. Собран как add-on, поэтому его удобно разворачивать рядом с HA.

## Назначение
```
Телефон (HTTP POST /v1/command)
      ↓
SmartHome Gateway ──> smarthome_core (парсер, валидатор)
      ↓
Home Assistant (REST/Supervisor proxy)
```
Дополнительно шлюз умеет ходить в локальную LLM (OpenAI-совместимый endpoint) и логировать команды в JSONL.

## Запуск на Windows (PowerShell)
```powershell
cd smarthome_core
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements_gateway.txt

$env:HA_URL = "http://homeassistant.local:8123"
$env:HA_TOKEN = "<Long Lived Token>"
$env:GATEWAY_API_KEY = "local-dev-key"
$env:SH_CORE_ROOT = "."                 # путь до каталога с assets
python -m uvicorn smarthome_gateway.main:app --host 0.0.0.0 --port 8099
```

Проверка:
```powershell
Invoke-RestMethod -Method Post `
  -Uri "http://127.0.0.1:8099/v1/command" `
  -Headers @{"X-API-Key"="local-dev-key"} `
  -ContentType "application/json" `
  -Body '{"text":"выключи свет в спальне","parser_mode":"llm_safe","dry_run":false}'
```
Health-check: `Invoke-RestMethod http://127.0.0.1:8099/health`

## Настройки LLM
Переменные окружения:
- `LLM_BASE_URL` — например `http://192.168.2.84:8080`
- `LLM_MODEL` — имя модели из OpenAI‑совместимого сервера
- `LLM_API_KEY` — если сервер требует ключ

Режимы парсера:
- `rules` — только правила
- `llm_safe` — правила + LLM при необходимости
- `llm` — всегда через LLM (для экспериментов)

Если LLM не сконфигурирована, `llm_safe` автоматически скатывается к `rules`, а `llm` вернёт ошибку.

## Add-on для Home Assistant
Структура add-on в `smarthome_core/smarthome_gateway_addon/`. Скопируйте каталог в `/addons/<название>` на устройстве с HA, обновите `config.yaml` и перезапустите add-on через Supervisor. Логи шлюза можно смотреть в UI Home Assistant.

## Логи
По умолчанию пишем JSONL в `<корень>/gateway_logs/commands.jsonl`. Формат компактный: время, текст (с учётом политики редактирования), режим парсера, результат и latency.
