# SmarthomeAssistant (Android MVP)

MVP: push-to-talk (Vosk on-device ASR) -> text -> Gateway (LAN) -> Home Assistant -> voice response (Android TTS).

## Requirements
- Android Studio
- Android device (Samsung A72 OK) or emulator
- Java 17 (bundled with Android Studio)
- Gateway running in LAN

## Important: Gradle wrapper jar
This archive does **not** include `gradle/wrapper/gradle-wrapper.jar` (binary).
Fastest way to fix:
1) Create any new Android Studio project (Empty Activity) and let it sync.
2) Copy `gradle/wrapper/gradle-wrapper.jar` from that new project into this project at:
   `gradle/wrapper/gradle-wrapper.jar`

## Vosk model
Put Vosk model directory (unpacked) here:
`app/src/main/assets/models/vosk-model-small-ru-0.22/`

Inside it must contain: `am/ conf/ graph/ ivector/ README` and **file `uuid`**.
If your model doesn't have `uuid`, create:
`app/src/main/assets/models/vosk-model-small-ru-0.22/uuid`
with any text, e.g. `00000000-0000-0000-0000-000000000000`.

## Run
1) Open project in Android Studio
2) Sync Gradle
3) Run on device

## Gateway settings
- Gateway URL example: `http://192.168.1.10:8099`
- API key: your `X-API-Key`

The app sends `POST {GatewayUrl}/v1/command` with header `X-API-Key`.
