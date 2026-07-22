# Repository Guidelines

Guidelines for AI agents and human contributors working on **LTV Reader** — an Android port of [LocalText2Voice](https://github.com/estebanstifli/LocalText2Voice).

## Project Structure & Module Organization

```
t2v/
├── app/                        Android-приложение (Kotlin, Compose)
│   ├── src/main/java/com/ltvreader/
│   │   ├── core/               бизнес-логика (text, markup, audio, subtitle, normalization)
│   │   ├── tts/                TTS-движки (Kokoro, OpenAI, ElevenLabs, …) + реестр
│   │   ├── data/               Room (AppDatabase, DAO, Entities) + DataStore
│   │   ├── ui/                 Compose-экраны, ViewModel'и, тема, навигация
│   │   ├── worker/             GenerationPipeline + Foreground Service
│   │   ├── server/             HTTP-клиент к engine-host
│   │   ├── util/               LocaleHelper, Permissions, AudioPlayer
│   │   └── app/                LTVApplication + AppContainer (DI)
│   ├── src/main/assets/        Kokoro-модель, FFmpeg-бинарь, локализуемые ресурсы
│   ├── src/main/res/values*/   strings.xml (11 локалей)
│   ├── src/test/               JVM unit-тесты
│   └── src/androidTest/        ART integration-тесты
├── server-host/                Python FastAPI-бэкенд (опционально)
├── docs/                       документация (PORTING, ROADMAP, LTV_MARKUP, …)
└── tools/                      install.sh, check_completeness.sh, inspect_layout.sh
```

Слои: `core/` → `tts/` → `worker/` → `ui/`. Зависимости направлены строго вверх, обратные ссылки запрещены.

## Build, Test, and Development Commands

```bash
# Сборка debug APK
./gradlew :app:assembleDebug

# Все unit-тесты (JVM)
./gradlew :app:testDebugUnitTest

# Интеграционные тесты (нужен эмулятор/устройство)
./gradlew :app:connectedDebugAndroidTest

# С линтингом и проверкой
./gradlew :app:lint :app:testDebugUnitTest

# Установка на устройство
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Server-host (опционально)
cd server-host && python engine_host.py --port 8765 --allow-lan
```

Перед PR всегда запускайте `assembleDebug` + `testDebugUnitTest`.

## Coding Style & Naming Conventions

- **Kotlin**: официальный стиль JetBrains (4 пробела, без табов). Включите `ktlint` в IDE.
- **Имена пакетов**: `com.ltvreader.<layer>.<feature>` (например, `com.ltvreader.tts.engines`).
- **Имена классов**: `PascalCase`. ViewModel'и — `XxxViewModel`. Sealed-иерархии — `Xxx`.
- **Имена файлов**: имя совпадает с главным классом (`KokoroTtsEngine.kt`).
- **Compose**: composable-функции — `PascalCase` (как в Material 3).
- **Тесты**: `XxxTest.kt`, методы — обратные кавычки с пробелами: `` `parses voice commands` ``.
- **JSON-ключи в API**: `camelCase` в Kotlin, `snake_case` в wire-формах (OpenAI, ElevenLabs).
- **Imports**: без wildcard'ов; сортируются автоматически.
- **Локализация**: каждая новая строка — в `values/strings.xml` И в 10 `values-<lang>/`.

## Testing Guidelines

- **Фреймворк**: JUnit 4 для unit, AndroidJUnit4 для integration.
- **Что покрывать тестами**: всё в `core/`, `tts/registry/`, `data/`, edge cases в `tts/engines/`.
- **Кор-тесты должны быть детерминированными**: не вызывать сеть, использовать `Random(seed)`.
- **Запуск перед PR**:
  ```bash
  ./gradlew :app:testDebugUnitTest
  ```
- **Smoke-тест движков** (`RegistrySmokeTest`) проверяет только метаданные `EngineInfo`, не сами движки.
- **Coverage** (цель): > 60% в `core/`, > 40% в `tts/`.

## Commit & Pull Request Guidelines

- **Conventional Commits** (как в `git log`): `feat:`, `fix:`, `docs:`, `ci:`, `refactor:`, `chore:`.
- **Один коммит = одна логическая правка**. Не мешайте фиксы с фичами.
- **Сообщение**: imperative mood, ≤ 72 символов в заголовке, тело — при необходимости.
- **PR**: ссылка на issue (`Closes #N`), краткое описание "что и почему", скриншоты для UI-изменений, отметка breaking changes.
- **Перед PR**:
  1. Проверить `tools/check_completeness.sh` — все компоненты на месте.
  2. Обновить `docs/CHANGELOG.md` и `docs/ROADMAP.md`, если применимо.
  3. Добавить тесты.
  4. Прогнать `./gradlew :app:testDebugUnitTest`.

## Agent-Specific Instructions

- **Не коммитьте крупные бинарники** (Kokoro .onnx, FFmpeg-бинарь) в git — только плейсхолдеры. Реальные модели скачиваются отдельно.
- **Не модифицируйте** `LocalText2Voice` upstream — это форк, синхронизация ручная.
- **При добавлении TTS-движка**: реализуйте `TtsEngine`, зарегистрируйте в `EngineRegistry.createEngine()` и `allEngineInfos()`, добавьте API-ключ в `SettingsRepository.Keys`.
- **При изменении LTV-разметки**: обновите парсер (`LTVMarkupParser`), подсветку (`MarkupHighlighter`), панель кнопок (`MarkupToolbar`) и тесты.
- **Compose-экраны**: передавайте `LocalContext.current` явно в `ViewModelFactory`; не полагайтесь на глобальный контекст.
- **FFmpeg**: `FFmpegBridge` запускает нативный бинарь через `Runtime.exec()`. Никаких JNI или внешних AAR.

