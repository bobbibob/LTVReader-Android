# Internals

Описание внутренних компонентов T2V.

## Lifecycle

```
MainActivity.onCreate
  └─ LTVApplication (инициализация DI)
       ├─ AppDatabase (Room, lazy)
       ├─ SettingsRepository (DataStore, lazy)
       ├─ EngineRegistry (lazy, читает settings)
       ├─ GenerationPipeline (lazy)
       └─ TextProcessor (lazy)

  └─ setContent { LTVApp() }
       └─ LTVTheme
            └─ LTVNavHost
                 ├─ EditorScreen
                 ├─ ProjectsScreen
                 ├─ GenerationScreen
                 ├─ ReviewScreen
                 ├─ MusicMixScreen
                 ├─ VoicesScreen
                 └─ SettingsScreen
```

## Потоки данных

```
User → Editor → save() → Room (ProjectEntity)
              ↓
        Generation → startGeneration()
              ↓
        TextProcessor.process()  → List<TextChunk>
              ↓
        для каждого chunk:
              ├─ TTS engine (локальный Android или облачный API)
              ├─ AudioEncoder.writeWav() → WAV
              └─ AudioEncoder.writeSilence() для пауз
              ↓
        FFmpegBridge.concat() / .encode() → MP3
              ↓
        Room (AudiobookEntity, SegmentEntity)
              ↓
        AudioMixScreen (микширование)
              ↓
        FFmpegBridge.applyMusicDucking() → финальный mix.mp3
              ↓
        Share Intent / сохранение в MediaStore
```

## Где живут движки

| Движок | Где работает | Где код |
|---|---|---|
| Kokoro | on-device, ORT Android | `tts/engines/KokoroTtsEngine.kt` |
| OpenAI | облако | `tts/engines/OpenAiTtsEngine.kt` |
| ElevenLabs | облако | `tts/engines/ElevenLabsTtsEngine.kt` |
| Gemini TTS | облако | `tts/engines/GeminiTtsEngine.kt` |
| Azure Speech | облако | `tts/engines/AzureTtsEngine.kt` |
| Custom HTTP | зависит | `tts/engines/CustomHttpTtsEngine.kt` |

## LTV-разметка

`LTVMarkupParser` — это pull-parser с поддержкой всех команд оригинала.
См. `docs/LTV_MARKUP.md`.

Подсветка в редакторе: `ui/markup/MarkupHighlighter.kt`.
Панель кнопок: `ui/components/MarkupToolbar.kt`.

## Тесты

```
app/src/test/                        # unit (JVM)
  core/text/TextProcessorTest.kt
  core/markup/LTVMarkupParserTest.kt
  core/audio/AudioMixerTest.kt
  core/subtitle/SubtitleWriterTest.kt
  core/normalization/TextNormalizerTest.kt
  core/normalization/Num2WordsTest.kt
  core/project/ProjectManagerTest.kt
  tts/RegistrySmokeTest.kt

app/src/androidTest/                 # integration (ART)
  EndToEndSmokeTest.kt
```

Запуск:
```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest
```
