# LTV Reader — Roadmap

Версии и статус разработки. Текущая итерация — **0.1.0** (alpha).

## ✅ Сделано (v0.1.0)

- [x] Структура Gradle-проекта (Kotlin DSL, AGP 8.5, Kotlin 1.9.24).
- [x] Манифест с разрешениями для TTS, файлов, ffmpeg.
- [x] `core/text/TextProcessor` — порт 1:1 с Python-версии (regex, чанки, главы).
- [x] `core/markup/LTVMarkupParser` — все команды (`{{voice}}`, `{{pause}}`, `{{speed}}`, ...).
- [x] `core/markup/MarkupHighlighter` — подсветка в Compose.
- [x] `core/audio/AudioEncoder` — WAV 16-bit PCM I/O.
- [x] `core/audio/AudioMixer` — voice + music + ducking + fade + normalize.
- [x] `core/audio/FFmpegBridge` — обёртка над ffmpeg-бинарником из assets.
- [x] `core/audio/WaveformExtractor` — min/max envelope.
- [x] `core/subtitle/SubtitleWriter` — SRT + karaoke-ASS.
- [x] `core/normalization/TextNormalizer` — числа, валюты, даты, проценты, римские.
- [x] `core/project/ProjectManager` — импорт TXT/MD/DOCX.
- [x] `tts/engines/KokoroTtsEngine` — локально через onnxruntime-android (NNAPI).
- [x] `tts/engines/OpenAiTtsEngine`, `ElevenLabsTtsEngine`, `GeminiTtsEngine`,
       `AzureTtsEngine`, `CustomHttpTtsEngine` — через OkHttp + kotlinx-serialization.
- [x] `tts/engines/RemoteHostTtsEngine` — клиент к engine-host.
- [x] `tts/registry/EngineRegistry` — ленивая фабрика движков.
- [x] `server/EngineHostClient` — HTTP-клиент (OkHttp).
- [x] `data/AppDatabase` + 6 DAO + 6 entities (Room).
- [x] `data/SettingsRepository` — DataStore Preferences.
- [x] `worker/GenerationPipeline` — полный пайплайн с retry/cancel/progress.
- [x] `worker/GenerationService` — Foreground-сервис.
- [x] `ui/MainActivity` + `LTVTheme` (Material 3).
- [x] `ui/navigation/LTVNavHost` — 7 экранов.
- [x] `ui/components/LTVScaffold` + `LTVTopBar` + custom bottom nav.
- [x] `ui/waveform/WaveformCanvas` — Compose-канвас.
- [x] `ui/screens/editor` — текстовый редактор + LTV-подсветка.
- [x] `ui/screens/generation` — выбор движка, скорость, прогресс, кнопки.
- [x] `ui/screens/voices` — список голосов по движкам.
- [x] `ui/screens/projects` — список проектов.
- [x] `ui/screens/review` — список сегментов аудиокниги.
- [x] `ui/screens/music` — упрощённый микшер (voice + music + ducking).
- [x] `ui/screens/settings` — общие настройки + remote host + API-ключи.
- [x] `server-host/engine_host.py` — Python-бэкенд для удалённых движков.
- [x] 11 локализаций strings.xml (en/ru/es/fr/de/it/pt/zh/ja/hi/ar).
- [x] Манифест с `networkSecurityConfig` (cleartext к LAN).
- [x] `docs/PORTING.md`, `docs/ROADMAP.md`, `docs/LTV_MARKUP.md`.
- [x] 8 unit-тестов (JVM) + 1 android e2e (ART).
- [x] Tools: `inspect_layout.sh`, `check_completeness.sh`, `install.sh`.
- [x] GitHub Actions: `./gradlew :app:assembleDebug` собирает APK ~40 МБ.
- [x] `AGENTS.md` обновлён.

## 🚧 В работе (v0.2.0)

### Сначала — починить упавшие тесты

- [ ] `AudioMixerTest > writeSilence` — sample count 22050 vs 11025 (header /2)
- [ ] `LTVMarkupParserTest > pause ms/s` — `0.7s` не парсится: regex `raw.endsWith("s", true)` ловит `ms`
- [ ] `Num2WordsTest > english/spanish basic` — отдельные слова не совпадают с ожиданием
- [ ] `TextNormalizerTest > currencies` — `$5` → "dollars" отсутствует
- [ ] `TextProcessorTest > clean` — `Hello\u0000World` не схлопывается
- [ ] Сделать CI `test` job `continue-on-error: false` после починки

### Затем — реальные движки

- [ ] Скачать FFmpeg-бинарь (через NDK или готовый AAR),
      положить в `app/src/main/assets/ffmpeg/<abi>/ffmpeg`.
- [ ] Скачать Kokoro-модель `kokoro-v0_19.onnx` + `voices.bin` с HuggingFace,
      положить в `app/src/main/assets/voices/kokoro/`.
- [ ] Реальный G2P для Kokoro (сейчас ASCII-fallback).
- [ ] Тест микширования на реальном устройстве.
- [ ] Тест Kokoro-генерации на реальном устройстве.

## 🛣 Дальше (v0.3.0+)

- [ ] Voice gallery sync (GitHub каталог).
- [ ] Полный timeline-микшер (мульти-трек, SFX-события).
- [ ] Background downloads через WorkManager.
- [ ] SRT/ASS-экспорт через UI.
- [ ] Tablet-адаптация (adaptive layout, two-pane).
- [ ] Auto-TTS (распознавание языка текста).
- [ ] Material You dynamic colors.
- [ ] UI-тесты (Compose UI Test).
- [ ] Расширенные правила нормализации.
- [ ] Шаринг аудиокниги (Android Share Intent).

## ❌ Не будет

- ❌ Локальные Chatterbox / Qwen3 / OmniVoice — слишком тяжёлые модели.
- ❌ Встроенный Python / MCP / HTTP-сервер — не запустить на Android.
- ❌ Faster Whisper — нет билдов ctranslate2 под Android.
- ❌ LLM-пайплайны — слишком сложно для мобильного клиента, выносится в engine-host.
- ❌ Windows-only фичи (CreateDesktopShortcut, UAC, signed installer).

## Целевые метрики

| Метрика | Цель |
|---|---|
| Минимальный APK (только Kokoro) | < 30 МБ |
| APK с Kokoro + всеми облачными движками | < 60 МБ |
| Генерация 1 страницы текста на mid-range устройстве (Kokoro) | < 30 с |
| Время холодного старта | < 1.5 с |
| Покрытие тестами (core) | > 80 % |

## Публикация (отложено)

Публикация в Google Play / F-Droid **не входит в ближайшие планы**.
Причины: требует keystore, privacy policy, developer account ($25), и регулярного
поддержания качества. На данный момент проект распространяется через
GitHub Releases в виде APK — скачивание через
[GitHub Actions artifacts](../../actions) или прямой ссылке на .apk.
Когда/если решим публиковать — см. `docs/DEPLOYMENT.md` (пока draft).

