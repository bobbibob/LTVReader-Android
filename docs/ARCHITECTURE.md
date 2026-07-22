# Архитектура

Диаграмма компонентов и их взаимодействия.

```
┌─────────────────────────────────────────────────────────────────────┐
│                          Android (Kotlin)                            │
│                                                                       │
│  ┌──────────────────────┐    ┌────────────────────────────────┐    │
│  │     UI (Compose)     │    │  Background work                │    │
│  │                      │    │                                 │    │
│  │ EditorScreen         │    │  GenerationService (FGS)        │    │
│  │ GenerationScreen     │    │  GenerationPipeline (coroutine) │    │
│  │ MusicMixScreen       │    │                                 │    │
│  │ ReviewScreen         │    │  ┌─────────────────────────┐    │    │
│  │ VoicesScreen         │    │  │   TTS Engines           │    │    │
│  │ ProjectsScreen       │    │  │                         │    │    │
│  │ SettingsScreen       │    │  │  Kokoro (ONNX)          │    │    │
│  │                      │    │  │  OpenAI / ElevenLabs    │    │    │
│  └──────────┬───────────┘    │  │  Gemini / Azure         │    │    │
│             │ ViewModels     │  │  Custom HTTP            │    │    │
│             ↓                 │  │  RemoteHost client      │    │    │
│  ┌──────────────────────┐    │  └─────────────────────────┘    │    │
│  │  State (StateFlow)   │←──→│              ↓                  │    │
│  └──────────┬───────────┘    │  ┌─────────────────────────┐    │    │
│             ↓                 │  │  Audio processing       │    │    │
│  ┌──────────────────────┐    │  │                         │    │    │
│  │  Repositories        │    │  │  AudioEncoder (WAV)     │    │    │
│  │                      │    │  │  AudioMixer (ducking)   │    │    │
│  │  ProjectRepository   │    │  │  FFmpegBridge           │    │    │
│  │  AudiobookRepository │    │  │  SubtitleWriter         │    │    │
│  │  SettingsRepository  │    │  │  WaveformExtractor      │    │    │
│  └──────────┬───────────┘    │  └─────────────────────────┘    │    │
│             ↓                 └────────────────┬────────────────┘    │
│  ┌──────────────────────┐                      ↓                      │
│  │  Room + DataStore    │              ┌──────────────────┐           │
│  │                      │              │  ffmpeg-kit      │           │
│  │  projects            │              │  (native)        │           │
│  │  audiobooks          │              └──────────────────┘           │
│  │  segments            │                                            │
│  │  voices              │                                            │
│  │  dictionary          │                                            │
│  │  normalization       │                                            │
│  │  settings (Prefs)    │                                            │
│  └──────────────────────┘                                            │
│                                                                       │
│  ┌──────────────────────┐                                            │
│  │  Core (pure Kotlin)  │                                            │
│  │                      │                                            │
│  │  TextProcessor       │                                            │
│  │  LTVMarkupParser     │                                            │
│  │  TextNormalizer      │                                            │
│  │  ProjectManager      │                                            │
│  └──────────────────────┘                                            │
└─────────────────────────────────────────────────────────────────────┘
                              │
                              │ HTTP (LAN)
                              ↓
┌─────────────────────────────────────────────────────────────────────┐
│              server-host (Python, optional)                          │
│                                                                       │
│  ┌──────────────────────┐    ┌────────────────────────────────┐    │
│  │  FastAPI app         │    │  TTS Engines (Python)          │    │
│  │                      │    │                                 │    │
│  │  /info               │    │  Piper (subprocess)            │    │
│  │  /engines            │←──→│  Kokoro (kokoro-onnx)          │    │
│  │  /engines/{id}/...   │    │  Chatterbox (chatterbox-tts)   │    │
│  │  /jobs               │    │  Qwen3 TTS                     │    │
│  │  /jobs/{id}          │    │  OmniVoice                     │    │
│  │  /synthesize         │    │  Faster Whisper (verify)       │    │
│  │  /mcp                │    │                                 │    │
│  └──────────────────────┘    └────────────────────────────────┘    │
│                                                                       │
│  ┌──────────────────────┐                                            │
│  │  Settings (JSON)     │                                            │
│  │  engines/piper/      │                                            │
│  │  engines/chatterbox/ │                                            │
│  │  voices/             │                                            │
│  │  output/             │                                            │
│  └──────────────────────┘                                            │
└─────────────────────────────────────────────────────────────────────┘
```

## Уровни

| Уровень | Где | Что |
|---|---|---|
| Pure Kotlin | `core/` | text, markup, audio, normalization, subtitle, project |
| Data | `data/` | Room (DAO, entities), DataStore (Settings) |
| TTS | `tts/` | Engine interface + 5 cloud + 1 local + 4 remote |
| UI | `ui/` | Compose-экраны, ViewModel'и, тема, навигация |
| Worker | `worker/` | GenerationPipeline + Foreground Service |
| Server | `server-host/` | Python FastAPI бэкенд |
| Util | `util/` | LocaleHelper, Permissions, AudioPlayer |

## Зависимости между слоями

```
ui ────→ ViewModel ────→ Repository ────→ Room / DataStore
                                  ↑
                                  │
Generation ──→ Pipeline ──→ EngineRegistry ──→ TtsEngine
                                          │
                                          ├──→ Kokoro (ONNX)
                                          ├──→ OkHttp → Cloud APIs
                                          └──→ OkHttp → RemoteHost
```

Запрещено:
- `core/` → `tts/`, `data/`, `ui/`
- `data/` → `ui/`
- `tts/` → `ui/`

