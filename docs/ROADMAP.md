# LTV Reader — Roadmap

Версии и статус разработки. Текущая итерация — **0.1.0** (alpha).

## ✅ Сделано (v0.1.0)

- [x] Структура Gradle-проекта (Kotlin DSL, AGP 8.5, Kotlin 1.9.24).
- [x] Манифест с разрешениями для TTS, файлов, ffmpeg.
- [x] `core/text/TextProcessor` — порт 1:1 с Python-версии (regex, чанки, главы).
- [x] `core/markup/LTVMarkupParser` — все команды (`{{voice}}`, `{{pause}}`, ...).
- [x] `core/markup/MarkupHighlighter` — подсветка в Compose.
- [x] `core/audio/AudioEncoder` — WAV 16-bit PCM I/O.
- [x] `core/audio/AudioMixer` — voice + music + ducking + fade + normalize.
- [x] `core/audio/FFmpegBridge` — обёртка над ffmpeg-kit (encode, concat, sidechain).
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
- [x] `ui/components/LTVScaffold` + `LTVTopBar` — нижняя навигация.
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
- [x] `docs/PORTING.md` и `docs/ROADMAP.md`.

## 🚧 В работе (v0.2.0)

- [ ] Реальный G2P для Kokoro (сейчас ASCII-fallback; нужен eSpeak-ng / Misaki).
- [ ] Стриминг: отдача сегментов по мере готовности.
- [ ] DOCX-импорт через Apache POI.
- [ ] Тесты для `TextProcessor`, `LTVMarkupParser`, `AudioMixer`.
- [ ] Сборка APK (debug), проверка на устройстве.
- [ ] Документирование установки Kokoro-модели в `assets/`.

## 🛣 Дальше (v0.3.0+)

- [ ] Voice gallery sync (GitHub каталог).
- [ ] Полный timeline-микшер (мульти-трек, SFX-события).
- [ ] Background downloads через WorkManager.
- [ ] SRT/ASS-экспорт через UI.
- [ ] Поддержка Wear OS (ограниченный UI).
- [ ] Tablet-адаптация (adaptive layout, two-pane).
- [ ] Auto-TTS (распознавание языка текста).
- [ ] Расширенные правила нормализации (полный EBU R128, люд-референс).
- [ ] Шаринг аудиокниги (Android Share Intent).
- [ ] Подписки на обновления голосов.
- [ ] Material You dynamic colors.

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
| Утечек памяти при генерации часа аудио | 0 (тест LeakCanary) |
| Покрытие тестами (core) | > 80 % |

