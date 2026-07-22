# LTV Reader — Android-порт LocalText2Voice

Android-клиент для длинных TTS-аудиокниг и подкастов.

Это порт-форка [bobbibob/Text2Voice](https://github.com/bobbibob/Text2Voice) (он же
[estebanstifli/LocalText2Voice](https://github.com/estebanstifli/LocalText2Voice),
v1.2.1). Оригинал — Windows-десктоп на PySide6. Здесь — нативное Android-приложение
на Kotlin + Jetpack Compose.

## Что сохранено
- LTV-разметка (`{{voice "..."}}`, `{{pause 700ms}}`, `{{lang es}}` и т.д.) — парсер
  портирован 1:1, поведение и регулярки совпадают.
- Алгоритм текст-процессора: детекция глав, разбиение на чанки, безопасные
  границы предложений/параграфов, рандомизированные паузы.
- Модель данных `Project / Audiobook / Segment / VoiceConfig / StoredSegment` —
  переписана под Room/SQLDelight, поля и индексы совпадают.
- Аудио-пайплайн, микширование, экспорт субтитров SRT/ASS, обрезка хвостов.
- Eleven одинаковых локалей.
- Каталог голосов, раздел «Стили», галерея голосов, импорт DOCX/TXT/MD.

## Что изменилось
- UI: вместо `QStackedWidget` (14 190 строк) — Jetpack Compose с Material 3.
- TTS: вместо Windows-процессов — встроенный **Kokoro** (onnxruntime-android, NNAPI)
  + облачные API (OpenAI / ElevenLabs / Gemini / Azure / Custom HTTP). Движки
  Chatterbox / Qwen3 / OmniVoice / Piper — **только через удалённый engine-host**
  (см. ниже).
- FFmpeg: `ffmpeg-kit` вместо `ffmpeg.exe`.
- HTTP/MCP-сервер: убран из приложения. Вместо него — опциональный **remote host**
  в `/server-host` (Python), к которому Android подключается по Wi-Fi.

## Архитектура
```
app/                        Android-приложение (Kotlin, Compose)
  core/      текст, разметка, аудио-пайплайн, микшер, субтитры
  tts/       Kokoro + облачные API + сетевой клиент к engine-host
  data/      Room, DataStore, репозитории
  ui/        Compose-экраны, тема, waveform-канвас
  worker/    фоновые задачи (WorkManager + корутины)
server-host/                Python-бэкенд (тот же, что в оригинале)
  engine_host.py            запускает uvicorn + FastAPI
  http_app.py               HTTP/MCP-роуты
  ltv_service.py            бизнес-логика
  job_manager.py            очередь задач
docs/                       портирование, решения, ограничения
tools/                      вспомогательные скрипты
```

## Сборка
```bash
# Требуется Android Studio Hedgehog+ и JDK 17
./gradlew :app:assembleDebug

# Удалённый движковый хост (опционально, для Piper/Chatterbox/Qwen3/OmniVoice)
cd server-host
pip install -r requirements.txt
python engine_host.py --allow-lan
```

## Документация
- `docs/PORTING.md` — детальный разбор ограничений и принятых решений.
- `docs/LTV_MARKUP.md` — поведение разметки (совпадает с оригиналом).
- `docs/ROADMAP.md` — что сделано, что в работе, что отложено.
