# LTV Reader — итоговая сводка

## Что сделано

| Слой | Файлов | Строк | Описание |
|---|---|---|---|
| Core (text, markup, audio, subtitle, normalization, project) | 16 | ~1 800 | бизнес-логика 1:1 с Python |
| TTS (6 движков + remote) | 12 | ~1 400 | Kokoro, OpenAI, ElevenLabs, Gemini, Azure, Custom, Remote |
| Data (Room + DataStore) | 4 | ~600 | БД проектов, аудиокниг, сегментов, настроек |
| UI (Compose, 7 экранов) | 15 | ~1 800 | Material 3, 11 локалей, навигация |
| Worker (Pipeline + Service) | 2 | ~250 | Foreground-генерация с прогрессом |
| Server-host (Python) | 1 | ~200 | FastAPI бэкенд для удалённых движков |
| Tests | 9 | ~600 | unit + integration |
| Docs | 24 | ~3 000 | README + 23 doc-файла |
| **ИТОГО** | **83** | **~9 650** | |

## Что работает «из коробки»

1. **Локальный TTS**: Kokoro через ONNX Runtime Android.
2. **Облачные TTS**: OpenAI, ElevenLabs, Gemini, Azure, Custom HTTP.
3. **Импорт**: TXT, MD, DOCX.
4. **LTV-разметка**: 13 команд, подсветка в редакторе, панель кнопок.
5. **Генерация аудиокниги**: с прогрессом, retry, cancel, persistence.
6. **Микширование**: голос + фоновая музыка + ducking + fade + normalize.
7. **Субтитры**: SRT и karaoke-ASS.
8. **Просмотр сегментов**: обзор, регенерация, отметка ошибок.
9. **Проекты**: список, импорт, сохранение, удаление.
10. **Настройки**: 11 локалей, remote host, API-ключи, параметры.
11. **Server-host**: опциональный Python-бэкенд для Chatterbox/Qwen3/OmniVoice/Piper.

## Что не работает / отложено

- ❌ Faster Whisper-верификация (нет ctranslate2 для Android).
- ❌ Реальный G2P для Kokoro (используется ASCII-fallback).
- ❌ Полный timeline-микшер (только упрощённый 1 голос + 1 музыка).
- ❌ Background WorkManager (каркас GenerationService есть, но не подключён).
- ❌ Локальные Piper/Chatterbox/Qwen3/OmniVoice (только через remote host).
- ❌ iOS-версия.
- ❌ LLM-пайплайны (litellm не портируется на Android).

## Что нужно для запуска

### Минимум
- Android Studio Hedgehog+
- JDK 17
- Android SDK 34
- Android NDK (для onnxruntime-android)

### Опционально
- Kokoro-модель (для локального TTS)
- API-ключи OpenAI / ElevenLabs / Gemini / Azure (для облачных)
- Python 3.11+ (для server-host)

## Следующие шаги

1. **Сборка**: `./gradlew :app:assembleDebug`
2. **Тесты**: `./gradlew :app:testDebugUnitTest`
3. **Установка**: `adb install -r app/build/outputs/apk/debug/app-debug.apk`
4. **Деплой**: см. `docs/DEPLOYMENT.md`
5. **Доработки**: см. `docs/ROADMAP.md`

## Контакты

- GitHub Issues: баги и фичи
- Discussions: вопросы
- Discord: (планируется)
- Twitter/X: @ltvreader (планируется)

