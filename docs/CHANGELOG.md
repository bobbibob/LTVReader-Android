# Changelog

Все значимые изменения в LTV Reader документируются здесь.

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
- Server host: Python FastAPI бэкенд (`server-host/engine_host.py`).
- 11 локалей strings.xml: en, ru, es, fr, de, it, pt, zh, ja, hi, ar.
- Документация: README, PORTING, ROADMAP, LTV_MARKUP, QUICKSTART,
  INTERNALS, ARCHITECTURE, CHANGELOG.
- Тесты: 8 unit + 1 android e2e (JVM), 1 instrumentation (ART).
- Tools: `inspect_layout.sh`, `check_completeness.sh`, `install.sh`.

### Not included
- Реальный G2P для Kokoro (используется ASCII-fallback).
- Faster Whisper verification.
- Локальные Chatterbox/Qwen3/OmniVoice (только через remote host).
- Импорт DOCX через Apache POI (используется ручной ZIP-парсер).
- Background WorkManager (каркас GenerationService есть).

