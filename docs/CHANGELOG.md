# Changelog

Все значимые изменения в T2V документируются здесь.

## [Unreleased]

### Added
- XML-style audio tags for in-text music and SFX insertion:
  `<music>prompt</music>` and `<sfx>prompt</sfx>`.
  - `LTVMarkupParser.extractAudioTags()` returns the ordered list with
    positions; `parseSpans()` now breaks voice chunks at every audio tag
    boundary so the pipeline can place the clip exactly where the tag
    appeared in the source text.
  - `MarkupState` is unchanged; voice text never contains the prompt.
- `core/audio/AudioTagInserter` runs after TTS synthesis. For each tag it
  picks the selected music/sound generator from settings, generates a WAV
  into the audiobook folder, computes the timeline position from existing
  segment durations, and persists an `AudioClipEntity` on the right track.
  Default gain comes from `AudioEditProject` defaults so the editor slider
  controls the clip just like any other music/SFX.
- `MarkupHighlighter` now colours `<music>`/`<sfx>` tags with the accent
  palette. `MarkupToolbar` adds two new chips: Music and SFX.
- 11 locales gained `markup_music` and `markup_sfx` strings.
- `LTVMarkupAudioTagsTest` covers parser behaviour; existing
  `TextProcessorTest` was updated to consume the new `ProcessResult`.

### Fixed
- `AudioTimelineDao` now exposes `trackByType()` so the inserter can
  reuse a single track id per audiobook per category.

## [Unreleased]

### Added
- Restored the Kokoro download card in ModelsScreen: progress bar, byte/percent
  updates, cancel button, select-after-install. The previously hidden `if (false)`
  branch is gone.
- Expanded the Piper/VITS on-device catalog beyond Russian and the two English
  voices (Amy/Cori): German (Thorsten, Kerstin), French (Siwis, Tom),
  Spanish (Carlos es-ES, Ald es-MX), Italian (Riccardo), Chinese (Huayan) and
  Japanese (Kai). Each voice is bundled through the existing SherpaOnnx
  runtime and surfaced as a separate `PiperVoiceCard` grouped by language.
- `GenerationModelCatalog` reserves two cloud-only slots (`openai-music`,
  `elevenlabs-sound-clip`) marked `RuntimeInDevelopment` so they can be
  referenced from Info dialogs without being selectable.
- `PiperRussianCatalogTest` and `GenerationModelCatalogTest` grew to cover the
  new languages and to lock the in-development status of cloud-only entries.

### Changed
- ModelsScreen now groups local voices by language via `PiperVoiceGroup` and
  renders an Info dialog per voice with the same Piper TagDocs used elsewhere.
- `PiperRussianTtsEngine.piperVoice()` accepts an optional
  `approximateSizeBytes` override so smaller Piper models (x_low, low) report
  a believable download size.

### Notes
- Music/sound generator runtime works end-to-end: generator selection with
  available/unavailable labels, audio preview (play/stop per clip), default
  prompts, WAV header metadata parsing.
- BundledAssetGeneratorTest (6 JVM tests) verifies all 6 bundled WAV assets,
  header parsing, and keyword matching.
- Info dialog on every model/generator card in ModelsScreen, populated from
  `GenerationModelCatalog.TagDocs`: tagline, runtime, repository, license,
  supported/partial/ignored tags, examples and prompt help. Localized in 11
  languages via `info_*` strings. TagDocs now cover kokoro, piper_ru, pocket-tts,
  zipvoice, openai, elevenlabs, gemini, azure, custom_http, bundled,
  ElevenLabs SFX and LiteRT Stable Audio (music/clip).

### Changed
- All TagDocs strings and ModelsScreen user-facing copy are now Russian by
  default (UI приложения рассчитан на русскоязычного владельца). LTV-разметка
  `{{...}}` и имена движков остаются латиницей; locale-specific варианты
  остаются в 10 других values-* папках.

### Fixed
- Catalog quotes inside TagDocs strings (CUSTOM_HTTP_TAGS, ZIPVOICE_TAGS,
  PocketTTS) escaped correctly so the Kotlin parser accepts them.
- Resolved Composable context for the Info dialog labels by hoisting the
  LocalContext-relative strings to the screen scope.

### Notes
- Никакой локальной сборки: код проверяется только через GitHub Actions
  (`gh workflow run android.yml --ref codex/audio-production`). Подробности и
  правила верификации - в docs/AI_HANDOFF.md (раздел «Обязательные требования
  владельца»).

### Fixed
- `AudioMixerTest > writeSilence`: `AudioEncoder.readWav` переведён на `RandomAccessFile`
  с ручным little-endian чтением; dataSize/2 даёт корректное число сэмплов (11025 для 500 мс @ 22050 Гц).
- `LTVMarkupParserTest > pause ms/s`: `endsWith("ms")` теперь проверяется раньше `endsWith("s")`,
  поэтому `{{pause 0.7s}}` парсится как 700 мс, а `{{pause 700ms}}` — как 700 мс.
- `TextProcessorTest > clean`: `controlChars` включает `\u0000`, а `\n{3,}` схлопывается до `\n\n`.
- `TextNormalizerTest > currencies`: `currencyRegexPrefix` матчит `$5` и заменяет на "five dollars".
- `Num2WordsTest > english/spanish basic`: таблицы ONES_EN/ONES_ES и SCALES корректны.
- ROADMAP и AGENTS.md обновлены: упавшие тесты отмечены как починенные.


## [0.1.0] - 2026-07-23

### Added
- Полная Android-структура (Kotlin DSL, AGP 8.5, Kotlin 1.9.24).
- Core: TextProcessor, LTVMarkupParser, AudioEncoder, AudioMixer,
  FFmpegBridge, WaveformExtractor, SubtitleWriter, TextNormalizer,
  Num2Words, ProjectManager.
- TTS-движки: Kokoro (on-device, ORT Android NNAPI), OpenAI, ElevenLabs,
  Gemini, Azure, Custom HTTP, Remote Host client.
- Data: Room (AppDatabase + 6 entities + 6 DAOs), DataStore Settings.
- UI: 7 экранов (Editor, Generation, Review, Music Mix, Voices,
  Projects, Settings) на Jetpack Compose + Material 3.
- Локальный Kokoro через sherpa-onnx Android runtime.
- Точный размер Kokoro из Hugging Face и прогресс загрузки в процентах и байтах.
- Стабильная debug-подпись APK между GitHub Actions сборками для обновления без потери данных.
- Паузы наследуют PCM-формат TTS, поэтому Kokoro 24 кГц корректно собирается в итоговый WAV.
- Ошибка генерации отображается отдельно и больше не помечается как готовая аудиокнига.
- Debug-вариант явно использует постоянный CI-keystore; сертификат APK проверяется в workflow.
- Экран генерации прокручивается и показывает плеер итогового WAV; плеер также добавлен в Review.
- Четыре русских локальных Piper/VITS-голоса: Ирина, Денис, Дмитрий и Руслан.
- Загрузка и безопасная распаковка официальных Android-пакетов русских голосов с прогрессом.
- ElevenLabs Instant Voice Clone: выбор записи, подтверждение прав, создание и выбор клона.
- Выбранный в галерее ElevenLabs голос теперь действительно передаётся в URL синтеза.
- 11 локалей strings.xml: en, ru, es, fr, de, it, pt, zh, ja, hi, ar.
- Документация: README, PORTING, ROADMAP, LTV_MARKUP, QUICKSTART,
  INTERNALS, ARCHITECTURE, CHANGELOG.
- Тесты: 8 unit + 1 android e2e (JVM), 1 instrumentation (ART).
- Tools: `inspect_layout.sh`, `check_completeness.sh`, `install.sh`.

### Not included
- Реальный G2P для Kokoro (используется ASCII-fallback).
- Faster Whisper verification.
- Непроверенные локальные Chatterbox/Qwen3/OmniVoice.
- Импорт DOCX через Apache POI (используется ручной ZIP-парсер).
- Background WorkManager (каркас GenerationService есть).
