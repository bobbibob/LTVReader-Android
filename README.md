# T2V

Самостоятельное Android-приложение для создания длинных TTS-аудиокниг,
озвучки и подкастов. Написано на Kotlin и Jetpack Compose.

## Возможности
- LTV-разметка (`{{voice "..."}}`, `{{pause 700ms}}`, `{{lang es}}` и т.д.) — парсер
  для точного управления озвучкой.
- Алгоритм текст-процессора: детекция глав, разбиение на чанки, безопасные
  границы предложений/параграфов, рандомизированные паузы.
- Модель данных `Project / Audiobook / Segment / VoiceConfig / StoredSegment` —
  переписана под Room/SQLDelight, поля и индексы совпадают.
- Аудио-пайплайн, микширование, экспорт субтитров SRT/ASS, обрезка хвостов.
- Eleven одинаковых локалей.
- Каталог голосов, раздел «Стили», галерея голосов, импорт DOCX/TXT/MD.

## Архитектура
- UI: Jetpack Compose с Material 3.
- TTS: только локальные Android-runtime и облачные API
  (OpenAI / ElevenLabs / Gemini / Azure / Custom HTTP).
- FFmpeg: `ffmpeg-kit` вместо `ffmpeg.exe`.

## Архитектура
```
app/                        Android-приложение (Kotlin, Compose)
  core/      текст, разметка, аудио-пайплайн, микшер, субтитры
  tts/       локальные Android-движки + облачные API
  data/      Room, DataStore, репозитории
  ui/        Compose-экраны, тема, waveform-канвас
  worker/    фоновые задачи (WorkManager + корутины)
docs/                       портирование, решения, ограничения
tools/                      вспомогательные скрипты
```

## Сборка
```bash
# Требуется Android Studio Hedgehog+ и JDK 17
./gradlew :app:assembleDebug

```

## Документация
- `docs/PORTING.md` — детальный разбор ограничений и принятых решений.
- `docs/LTV_MARKUP.md` — поведение разметки (совпадает с оригиналом).
- `docs/ROADMAP.md` — что сделано, что в работе, что отложено.
