# Repository Guidelines

Guidelines for AI agents and human contributors working on **LTV Reader** — an Android port of [LocalText2Voice](https://github.com/estebanstifli/LocalText2Voice). Active development: **v0.1.0-alpha** (build APK 40 MB, run #29977084836 ✅).

## Project Structure & Module Organization

```
t2v/
├── app/                        Android-приложение (Kotlin, Compose)
│   ├── src/main/java/com/ltvreader/
│   │   ├── core/               бизнес-логика (text, markup, audio, subtitle, normalization, project)
│   │   ├── tts/                TTS-движки + реестр (Kokoro, OpenAI, ElevenLabs, Gemini, Azure, Custom, Remote)
│   │   ├── data/               Room (AppDatabase, 6 DAO, 6 Entities) + DataStore
│   │   ├── ui/                 Compose-экраны (7), ViewModel'и, тема, навигация
│   │   ├── worker/             GenerationPipeline + GenerationService (FGS)
│   │   ├── server/             EngineHostClient (HTTP к удалённому engine-host)
│   │   ├── util/               LocaleHelper, Permissions, AudioPlayer
│   │   └── app/                LTVApplication + AppContainer (ручной DI)
│   ├── src/main/assets/        Kokoro-модель, FFmpeg-бинарь (см. README каждого)
│   ├── src/main/res/values*/   strings.xml (11 локалей)
│   ├── src/test/               JVM unit-тесты (8 классов)
│   └── src/androidTest/        ART integration-тесты
├── server-host/                Python FastAPI-бэкенд (опционально)
├── docs/                       документация (PORTING, ROADMAP, LTV_MARKUP, FAQ, …)
└── tools/                      install.sh, check_completeness.sh, inspect_layout.sh
```

**Слои (зависимости только вверх)**: `core/` → `tts/` → `worker/` → `ui/`. Обратные ссылки запрещены.

## Build, Test, and Development Commands

```bash
# Сборка debug APK (главная цель CI)
./gradlew :app:assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk (~40 МБ)

# Unit-тесты (некоторые сейчас падают — см. ROADMAP; APK не блокируется)
./gradlew :app:testDebugUnitTest

# Интеграционные тесты (нужен эмулятор/устройство)
./gradlew :app:connectedDebugAndroidTest

# Линт
./gradlew :app:lint

# Установка на устройство
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Server-host (опционально)
cd server-host && pip install -r requirements.txt && python engine_host.py --port 8765 --allow-lan
```

CI: `.github/workflows/android.yml`. Триггер: push в main, PR, или `gh workflow run android.yml`.

## Coding Style & Naming Conventions

- **Kotlin**: официальный стиль JetBrains (4 пробела, без табов). Включите `ktlint` в IDE.
- **Имена пакетов**: `com.ltvreader.<layer>.<feature>` (`com.ltvreader.tts.engines`).
- **Классы**: `PascalCase`. ViewModel'и — `XxxViewModel`. Sealed-иерархии — `Xxx`.
- **Файлы**: имя совпадает с главным классом (`KokoroTtsEngine.kt`).
- **Composable-функции**: `PascalCase` (как в Material 3).
- **Тесты**: `XxxTest.kt`, методы — обратные кавычки с пробелами: `` `parses voice commands` ``.
- **JSON-ключи**: `camelCase` в Kotlin, `snake_case` в wire-формах.
- **Imports**: без wildcard'ов; сортируются автоматически.
- **Локализация**: новая строка → `values/strings.xml` И в 10 `values-<lang>/`.

## Testing Guidelines

- **Фреймворк**: JUnit 4 (unit), AndroidJUnit4 (integration).
- **Что покрывать**: всё в `core/`, edge cases в `tts/engines/`.
- **Тесты детерминированы**: не вызывать сеть; `Random(seed)` для воспроизводимости.
- **Запуск**:
  ```bash
  ./gradlew :app:testDebugUnitTest
  ```
- **Smoke-тест движков** (`RegistrySmokeTest`): только метаданные `EngineInfo`.
- **Coverage цель**: > 60% в `core/`, > 40% в `tts/`.

## Commit & Pull Request Guidelines

- **Conventional Commits**: `feat:`, `fix:`, `docs:`, `ci:`, `refactor:`, `chore:`.
- **Один коммит = одна логическая правка**.
- **Заголовок**: imperative mood, ≤ 72 символов.
- **PR**: ссылка на issue (`Closes #N`), краткое "что и почему", скриншоты для UI.
- **Перед PR**: `tools/check_completeness.sh`, обновить `docs/CHANGELOG.md`, прогнать тесты.

## Agent-Specific Instructions

- **Не коммитьте крупные бинарники** (Kokoro .onnx, FFmpeg-бинарь) — только плейсхолдеры.
- **Compose-экраны**: передавайте `LocalContext.current` явно в `ViewModelFactory`; не полагайтесь на глобальный контекст.
- **При добавлении TTS-движка**: реализуйте `TtsEngine`, зарегистрируйте в `EngineRegistry.createEngine()` + `allEngineInfos()`, добавьте API-ключ в `SettingsRepository.Keys`, добавьте `EngineInfo` с правильным `EngineKind` (Local/Cloud/Remote).
- **При изменении LTV-разметки**: обновите парсер (`LTVMarkupParser`), подсветку (`MarkupHighlighter`), панель кнопок (`MarkupToolbar`) и тесты.
- **Compose Material 3 1.2.x**: `NavigationBarItem` помечен как `@ExperimentalMaterial3Api`. Используйте `@OptIn(ExperimentalMaterial3Api::class)` или собственную реализацию (как `BottomNavButton` в `LTVScaffold.kt`).
- **FFmpeg**: `FFmpegBridge` запускает нативный бинарь через `Runtime.exec()`. Бинарь лежит в `assets/ffmpeg/<abi>/ffmpeg`. Никаких JNI или AAR-зависимостей.
- **override suspend fun** в реализациях `TtsEngine`: всегда указывайте `: Unit` явно, иначе Kotlin не считает override корректным.
- **JSON в kotlinx.serialization**: для `JsonObject?.get(key)?.jsonPrimitive` используйте `if (p.isString) p.content else null` вместо extension-функций (extension-метод на `JsonPrimitive` плохо резолвится из подклассов).
- **Regex со спецсимволами** (например, `\S\n`): используйте raw string `Regex("""[^\S\n]+""")`, иначе Kotlin выдаёт "Illegal escape".

