# Changelog

Все значимые изменения в T2V документируются здесь.

## [Unreleased]

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
