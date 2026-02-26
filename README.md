# SmartHome Assistant App

Мобильное приложение и локальная инфраструктура для голосового управления умным домом без облака.

## Что это и зачем

Мы хотим, чтобы ассистент:
- принимал голосовые команды на русском языке;
- переводил «человеческие» фразы в строгие команды Home Assistant;
- работал полностью локально: микрофон → телефон → домашний шлюз → Zigbee‑устройства;
- минимально мучил пользователя уточняющими вопросами, но если нужно — уточнял;
- уважал приватность (нет отправки данных во внешний интернет).

## Как всё устроено

```
Телефон (ASR + приложение) → SmartHome Gateway → Home Assistant → Zigbee → лампа (иное умное устройство)
                                     ↓
                                 LLM Bridge → llama.cpp → Qwen2.5
```

1. **Телефон**  
   Android-приложение записывает речь, переводит в текст с помощью Vosk (ASR «на борту») и озвучивает ответы через локальный TTS. Пользователь видит статус сети, журнал команд, настройки TTS и выбор режима парсера (Правила / LLM + правила / Только LLM).

2. **SmartHome Gateway (add-on для Home Assistant)**  
   HTTP-сервер на FastAPI. Принимает текст команды и решает, как её парсить:
   - *Правила* (`parser_mode=rules`): быстрый детерминированный разбор.
   - *LLM + правила* (`llm_safe`): сначала правила, если не сработали — спрашиваем локальную LLM.
   - *Только LLM* (`llm`/`llm_strict`): эксперименты в чистом LLM-режиме.
   После разбора шлюз валидирует ParsedCommand и вызывает Home Assistant.

3. **LLM Bridge**  
   Небольшой FastAPI-сервис (`llama_openai_bridge.py`), который переводит OpenAI-совместимые запросы в формат `llama-server`. Работает с Qwen2.5-7B (GGUF) через `llama.cpp` и возвращает строго один JSON-ответ.

4. **Home Assistant + Zigbee**  
   Home Assistant крутится на Raspberry Pi 5. Шлюз отправляет финальные сервисные вызовы (например, `light.turn_on` для лампы KOJIMA `light.lampa1`).

## Главные особенности

- **Полная автономность**: нет внешних API, всё крутится локально.
- **Локальная LLM**: Qwen2.5 используется как fallback, если правил не хватает.
- **Диагностика**: в приложении видно «сырые» запросы/ответы шлюза и локальные логи.
- **Прозрачные режимы**: пользователь сам решает, когда включать LLM.

## Как запустить

1. **Телефон (Android Studio)**
   - открыть `android_app/`;
   - собрать и установить приложение на устройство (Android 10+);
   - в настройках указать `Gateway URL`, `X-API-Key`, режим парсера и параметры TTS.

2. **SmartHome Gateway (Home Assistant)**
   - код add-on лежит в `smarthome_core/smarthome_gateway_addon/`;
   - скопировать add-on в `/addons` на устройстве с Home Assistant и задать токен, URL Supervisor proxy, `LLM_BASE_URL`.

3. **LLM Bridge + модель**
   - скачать `llama.cpp` и веса Qwen2.5-7B (например, `q5_k_m`);
   - запустить `llama-server`:
     `./llama-server -m C:/Models/Qwen25/qwen2.5-7b-instruct-q5_k_m.gguf -c 4096 -ngl 99 -t 12 --port 8081`;
   - запустить мост:
     `python -m uvicorn llama_openai_bridge:app --host 0.0.0.0 --port 8080`;
   - в конфиге add-on указать `LLM_BASE_URL=http://<PC>:8080`.

4. **Home Assistant / Zigbee**
   - настроить лампу в Home Assistant (`light.lampa1`), проверить API-ключ.

## Полезные команды

```powershell
# Тест LLM через OpenAI-совместимый endpoint
Invoke-WebRequest -Uri http://127.0.0.1:8080/v1/chat/completions `
  -Method Post `
  -ContentType "application/json" `
  -Body '{"model":"qwen2.5-7b-instruct","messages":[{"role":"user","content":"Привет!"}]}'

# Проверка gateway
curl -H "X-API-Key: local-dev-key" -d '{"text":"включи свет","parser_mode":"llm_safe"}' `
     http://homeassistant.local:8099/v1/command
```

## Структура

```
README.md
android_app/                             ← Android-клиент (Compose, Vosk, TTS)
smarthome_core/
    smarthome_core/                      ← ядро: правила, схемы, лексикон
    smarthome_gateway/                   ← FastAPI add-on
    smarthome_gateway_addon/             ← структура add-on для Home Assistant
    llama_openai_bridge.py               ← мост OpenAI ↔ llama.cpp
```
