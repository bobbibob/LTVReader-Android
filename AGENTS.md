# Repository Guidelines

Guidelines for AI agents and human contributors working on **LTV Reader** — an Android port of [LocalText2Voice](https://github.com/estebanstifli/LocalText2Voice). Active development: **v0.1.0-alpha** (CI build #29990160060 in progress, last green: #29977084836).

## Project Structure & Module Organization

```
t2v/
├── app/                        Android-приложение (Kotlin, Compose)
│   ├── src/main/java/com/ltvreader/
│   │   ├── core/               бизнес-логика (text, markup, audio, subtitle, normalization, project)
│   │   ├── tts/                TTS-движки (Kokoro, OpenAI, ElevenLabs, Gemini, Azure, Custom, Remote)
│   │   ├── data/               Room (6 DAO, 6 Entities) + DataStore Settings
│   │   ├── ui/                 Compose-экраны (8: editor, generation, music, review, voices, projects, settings, models)
│   │   ├── worker/             GenerationPipeline + GenerationService
│   │   ├── server/             EngineHostClient + ModelRepository (HTTP к engine-host)
│   │   ├── util/               LocaleHelper, Permissions, AudioPlayer
│   │   └── app/                LTVApplication + AppContainer (ручной DI)
│   ├── src/main/assets/        Kokoro-модель, FFmpeg-бинарь (см. README каждого)
│   ├── src/main/res/values*/   strings.xml (11 локалей)
│   ├── src/test/               JVM unit-тесты (7 классов)
│   └── src/androidTest/        ART integration-тесты
├── server-host/                Python FastAPI-бэкенд (прокси HuggingFace + TTS)
├── docs/                       документация (PORTING, ROADMAP, LTV_MARKUP, FAQ, …)
└── tools/                      install.sh, check_completeness.sh, inspect_layout.sh
```

**Слои (зависимости только вверх)**: `core/` → `tts/` → `worker/` → `ui/`. Обратные ссылки запрещены.

## Build, Test, and Development Commands

```bash
# Сборка debug APK (главная цель CI)
./gradlew :app:assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk (~40 МБ)

# Unit-тесты (есть 1-2 фейла, APK не блокируется)
./gradlew :app:testDebugUnitTest

# Интеграционные тесты (нужен эмулятор/устройство)
./gradlew :app:connectedDebugAndroidTest

# Линт
./gradlew :app:lint

# Установка на устройство
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Скачать готовый APK из CI
gh run download 29977084836 -n app-debug

# Server-host (опционально, нужен для ModelsScreen)
cd server-host && pip install -r requirements.txt
python engine_host.py --port 8765 --allow-lan
# Эндпоинты: /info, /engines, /synthesize, /models, /local-models
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
- **Imports**: без wildcard'ов; только в начале файла; сортируются автоматически.
- **Локализация**: новая строка → `values/strings.xml` И в 10 `values-<lang>/`. **Не дублируйте** — будет ошибка `mergeDebugResources`.

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
- **Известные фейлы** (см. ROADMAP): 1-2 теста в `core/` (writeSilence, pause 0.7s).

## Commit & Pull Request Guidelines

- **Conventional Commits**: `feat:`, `fix:`, `docs:`, `ci:`, `refactor:`, `chore:`.
- **Один коммит = одна логическая правка**.
- **Заголовок**: imperative mood, ≤ 72 символов.
- **PR**: ссылка на issue (`Closes #N`), краткое "что и почему", скриншоты для UI.
- **Перед PR**: `tools/check_completeness.sh`, обновить `docs/CHANGELOG.md`, прогнать тесты.

## Текущий план работы (см. `docs/ROADMAP.md`)

1. **Сейчас**: починить оставшиеся 1-2 unit-теста в `core/` (writeSilence — переход на RandomAccessFile, pause 0.7s — проверить roundToInt).
2. **Потом**: подключить реальный FFmpeg-бинарь в `assets/ffmpeg/`.
3. **Потом**: подключить Kokoro-модель в `assets/voices/kokoro/`.
4. **Потом**: тесты на реальном устройстве.
5. **Потом**: UI-тесты, нормализация edge cases, полировка.
6. **В самом конце** (или никогда): публикация в Google Play / F-Droid.

## Agent-Specific Instructions

- **Не коммитьте крупные бинарники** (Kokoro .onnx, FFmpeg-бинарь) — только плейсхолдеры.
- **Compose-экраны**: передавайте `LocalContext.current` явно в `ViewModelFactory`; не полагайтесь на глобальный контекст.
- **При добавлении TTS-движка**: реализуйте `TtsEngine`, зарегистрируйте в `EngineRegistry.createEngine()` + `allEngineInfos()`, добавьте API-ключ в `SettingsRepository.Keys`, добавьте `EngineInfo` с правильным `EngineKind` (Local/Cloud/Remote).
- **При изменении LTV-разметки**: обновите парсер (`LTVMarkupParser`), подсветку (`MarkupHighlighter`), панель кнопок (`MarkupToolbar`) и тесты.
- **Compose Material 3 1.2.x**: `NavigationBarItem` помечен как `@ExperimentalMaterial3Api`. Используйте `@OptIn(ExperimentalMaterial3Api::class)` или собственную реализацию (как `BottomNavButton` в `LTVScaffold.kt`).
- **FFmpeg**: `FFmpegBridge` запускает нативный бинарь через `Runtime.exec()`. Бинарь лежит в `assets/ffmpeg/<abi>/ffmpeg`. Никаких JNI или AAR-зависимостей.
- **override suspend fun** в реализациях `TtsEngine`: всегда указывайте `: Unit` явно, иначе Kotlin не считает override корректным.
- **JSON в kotlinx.serialization**: для `JsonObject?.get(key)?.jsonPrimitive` используйте `if (p.isString) p.content else null` вместо extension-функций.
- **Regex со спецсимволами** (например, `\S\n`): используйте raw string `Regex("""[^\S\n]+""")`, иначе Kotlin выдаёт "Illegal escape".
- **strings.xml**: не дублируйте ключи — будет `Found item String/<name> more than one time` в `mergeDebugResources`.
- **Импорты в Kotlin**: только в начале файла. Если IDE вставил посреди — будет `imports are only allowed in the beginning of file`.
- **WAV read**: используйте `RandomAccessFile` с ручным little-endian чтением (`(b1 shl 8) or b0`). `DataInputStream.readShortLe()` есть, но на разных JVM-платформах ведёт себя по-разному в unit-тестах.
- **Room**: `exportSchema = false` обязательно, иначе KSP падает в CI.
- **ModelsScreen** (`ui/screens/models/ModelsScreen.kt`): показывает каталог TTS-моделей с HuggingFace через engine-host. Kokoro скачивается на устройство, тяжёлые модели — на сервер.
- **engine-host** (`server-host/engine_host.py`): эндпоинты `/models` (каталог), `/local-models`, `/models/{id}/download`. Требует `huggingface_hub`. Приватные репо — `HF_TOKEN=...` env var.
