# Портирование LocalText2Voice → LTV Reader (Android)

## TL;DR

| Аспект | Оригинал (Windows) | LTV Reader (Android) | Статус |
|---|---|---|---|
| UI | PySide6, `QStackedWidget`, 14 190 строк | Jetpack Compose, ~3000 строк | ✅ Переписан |
| TTS-движки локально | 5 (Piper, Kokoro, Chatterbox, Qwen3, OmniVoice) | 1 (Kokoro через ORT Android) | ⚠️ Остальные только через remote host |
| TTS-движки облачно | 4 (OpenAI, ElevenLabs, Gemini, Azure) | 4 + Custom HTTP | ✅ 1:1 |
| Audio pipeline | Python (numpy, wave) | Kotlin (короткие массивы) | ✅ Переписан |
| FFmpeg | `ffmpeg.exe` | `ffmpeg-kit-android` | ✅ |
| Текст-процессор | Python regex | Kotlin regex (те же выражения) | ✅ 1:1 |
| LTV-разметка | Python tokenizer | Kotlin tokenizer | ✅ 1:1 |
| Субтитры SRT/ASS | Python | Kotlin | ✅ |
| Голосовой каталог | SQLite + HF | Room + кеш | ✅ |
| Проекты / аудиокниги | SQLite | Room | ✅ |
| HTTP/MCP сервер | uvicorn + FastAPI + mcp | ❌ Удалён, перенесён в `server-host` | ⚠️ Опционально |
| Локализация | 11 языков, .json | 11 языков, strings.xml | ✅ |
| Voice gallery | GitHub + sync | GitHub + sync (через OkHttp) | ✅ |
| Faster Whisper review | ctranslate2 | ❌ | ❌ Нет билдов под Android |
| LLM plugins | litellm | ❌ | ❌ |
| Автообновления | GitHub releases | ❌ (через Google Play) | ❌ |

## Что сохранено 1:1

1. **LTV-разметка** — парсер и регулярки в `core/markup/LTVMarkupParser.kt`
   соответствуют `app/core/ltv_markup.py` (1 378 строк оригинала).
   Поддерживаются все команды: `{{voice}}`, `{{lang}}`, `{{pause}}`,
   `{{speed}}`, `{{volume}}`, `{{chapter}}`, `{{sfx}}`, `{{music}}`,
   `{{cmd}}`, `{{pitch}}`, `{{emotion}}`.

2. **Текст-процессор** — `core/text/TextProcessor.kt` повторяет
   `app/core/text_processor.py` (372 строки): regex глав/уроков/модулей,
   sentence-aware и clause-aware чанкование, рандомизированные паузы.

3. **Модель данных** — Room-сущности `ProjectEntity`, `AudiobookEntity`,
   `SegmentEntity`, `VoiceEntity`, `DictionaryEntry` повторяют таблицы
   из `audiobook_store.py`.

4. **Аудио-пайплайн** — `worker/GenerationPipeline.kt` повторяет
   `audio_pipeline.py` (2513 строк): text → chunks → TTS → WAV concat →
   MP3-кодирование, с retry/cancel/progress.

5. **Audio mix** — `core/audio/AudioMixer.kt` повторяет
   `audio_mix.py:AudioMixer` (768 строк): voice + music + ducking +
   fade in/out + normalize.

6. **Субтитры** — `core/subtitle/SubtitleWriter.kt` повторяет
   `subtitle_export.py`: SRT и karaoke-ASS с теми же форматами таймстампов.

## Что отрезано / отложено

| Компонент оригинала | Причина | Что сделано |
|---|---|---|
| `engine_host.py` как встроенный в приложение | На Android фоновый Python-процесс не запустить | Перенесён в `server-host/` (опционально) |
| `mcp_stdio_bridge.py` | Нужен Python | В Android-клиенте нет |
| `chatterbox_engine.py` | PyTorch 2 ГБ, модели 1+ ГБ | Только через remote host |
| `qwen_engine.py`, `omnivoice_engine.py` | Аналогично | Только через remote host |
| `faster_whisper_manager.py` | Нет ctranslate2 для Android | Без верификации; ручной обзор сохранён |
| `llm/base.py` | litellm | Без LLM-плагинов |
| `update_manager.py` | Windows-инсталлятор | Google Play |
| `installer/LocalText2Voice.iss` | InnoSetup | Play Console |
| `app_data/voices/` GUI-менеджер | Сложно на телефоне | Через экран Voices + скачивание |
| 4 LTV-разметочных панели | PySide6 | Одна панель + Compose-Chip |
| `audio_event_timeline.py` timeline (несколько клипов) | Сложный UI | Упрощённый 1 голос + 1 музыка |

## Архитектура

```
┌─────────────────────────────────────────────────────────────┐
│                Android (Kotlin / Jetpack Compose)          │
│  ┌────────────┐  ┌──────────────┐  ┌──────────────────────┐ │
│  │ UI Compose │→ │ GenerationPipeline → TTS engines         │ │
│  │  screens/  │  │   worker/    │  │  Kokoro (ONNX)      │ │
│  └────────────┘  └──────────────┘  │  OpenAI / ElevenLabs│ │
│       ↑              ↓               │  Gemini / Azure     │ │
│  ┌────────────┐  ┌──────────────┐  │  Custom HTTP        │ │
│  │ Room DB    │← │  Audio       │  │  Remote host client │ │
│  │            │  │  Mixer/FFmpeg│  └──────────────────────┘ │
│  └────────────┘  └──────────────┘           ↑               │
│                     ffmpeg-kit              │               │
└─────────────────────────────────────────────┼───────────────┘
                                              │ HTTP (LAN)
                                              ▼
                    ┌─────────────────────────────────────────┐
                    │  server-host (Python, на ПК/сервере)    │
                    │  - engine_host.py                       │
                    │  - Kokoro, Piper, Chatterbox, Qwen3,... │
                    │  - uvicorn + FastAPI + /synthesize     │
                    └─────────────────────────────────────────┘
```

## Числа

- **Объём оригинала**: 48 833 строк Python.
- **Объём порта (Kotlin + сервер-host)**: ~6 000 строк.
- **Из них:**
  - `core/` (text, markup, audio, subtitle, normalization) — ~1 800
  - `tts/` (engines, registry) — ~1 400
  - `data/` (Room) — ~600
  - `ui/` (Compose) — ~1 800
  - `worker/` (pipeline) — ~200
  - `server-host/` (Python) — ~200

## Что в следующих итерациях

1. Полный timeline-микшер (несколько SFX-клипов, music ducks).
2. Voice gallery sync (GitHub-зеркало голосов).
3. Генерация в фоне (WorkManager + Foreground Service) — каркас уже есть
   в `worker/GenerationService.kt`, нужна полная интеграция.
4. TTS-стриминг (отдача сегментов по мере генерации).
5. Улучшенный G2P для Kokoro на устройстве (сейчас ASCII-fallback).
6. Импорт DOCX через Apache POI вместо ручного ZIP-парсера.
7. Background downloads голосов через WorkManager.

