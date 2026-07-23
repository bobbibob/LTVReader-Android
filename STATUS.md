# Текущий статус

**Сборка:** ✅ APK 40 МБ собирается (run #29977084836)
**Тесты:** ❌ 5 из ~30 unit-тестов падают

## Что лежит на полке

```
✅ Готово:
- Структура Android-проекта (Gradle, AGP 8.5)
- Core: text, markup, audio, subtitle, normalization
- TTS: Kokoro (on-device), OpenAI, ElevenLabs, Gemini, Azure, Custom, Remote
- Data: Room, DataStore
- UI: 7 Compose-экранов, 11 локалей
- Документация: 24 файла
- Python server-host
- AGENTS.md, GitHub Actions CI
- APK собирается (~40 МБ)

❌ Не готово:
- 5 unit-тестов падают
- FFmpeg — только плейсхолдер, реального бинарника нет
- Kokoro — только каркас, модели не подключены
- G2P для Kokoro — ASCII-fallback
- Faster Whisper — отключён
- Реальное тестирование на устройстве
- Публикация — отложена
```

## Что делаем прямо сейчас

Фаза 1: **починить 5 упавших тестов**. Это самая приоритетная задача,
потому что без зелёных тестов мы не знаем, не сломали ли мы что-то ещё.

| # | Тест | Файл | Что не так |
|---|---|---|---|
| 1 | `clean removes control chars` | `core/text/TextProcessorTest.kt` | `Hello\u0000World` не схлопывается |
| 2 | `pause supports ms s` | `core/markup/LTVMarkupParserTest.kt` | `0.7s` парсится как `0` (regex ловит `ms` раньше) |
| 3 | `writeSilence produces correct duration` | `core/audio/AudioMixerTest.kt` | Расчёт `22050 * 0.5` = 11025, а ожидаем другое |
| 4 | `english/spanish basic numbers` | `core/normalization/Num2WordsTest.kt` | Слова не совпадают с ожиданием |
| 5 | `currencies are expanded` | `core/normalization/TextNormalizerTest.kt` | `$5` не превращается в "dollars" |

После починки — `tools/check_completeness.sh` + `./gradlew :app:testDebugUnitTest`
должны быть полностью зелёные. Тогда закоммитим и поедем дальше.

## Следующие шаги (после тестов)

### Фаза 2 — реальный FFmpeg
- Скачать FFmpeg для Android (через `niccokunzmann/ffmpeg-kit` или
  собрать из исходников под NDK).
- Положить в `app/src/main/assets/ffmpeg/<abi>/ffmpeg`.
- Проверить, что `Runtime.exec` его запускает на устройстве.
- Прогнать E2E: текст → TTS → mix → MP3.

### Фаза 3 — Kokoro на устройстве
- Скачать `kokoro-v0_19.onnx` (~150 МБ) + `voices.bin` с HuggingFace.
- Положить в `app/src/main/assets/voices/kokoro/`.
- Проверить, что onnxruntime-android грузит модель.
- Проверить, что inference даёт валидный PCM.
- (Опционально) G2P через eSpeak-ng.

### Фаза 4 — полировка
- UI-тесты (Compose UI Test).
- Расширить покрытие тестов до > 60% в `core/`.
- Тесты с mock-сервером для engine-host.
- Документация по типичным проблемам (troubleshooting FAQ).

### Потом (если/когда захотим)
- Timeline-микшер (мульти-трек, SFX-события).
- Voice gallery sync.
- Material You.
- Background WorkManager.
- Tablet-адаптация.

## Чего точно не будет

- ❌ Локальные Chatterbox / Qwen3 / OmniVoice (только через remote host)
- ❌ Встроенный Python / MCP-сервер
- ❌ Faster Whisper на устройстве
- ❌ Публикация в Google Play (сейчас — APK через GitHub)

