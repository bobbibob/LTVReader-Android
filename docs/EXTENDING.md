# Расширение LTV Reader

## Добавить новый TTS-движок

1. Создайте `app/src/main/java/com/ltvreader/tts/engines/MyEngine.kt`:

```kotlin
class MyTtsEngine(
    private val apiKey: String,
) : AbstractHttpEngine(ENGINE_INFO) {
    override fun endpoint() = "https://api.example.com/tts"
    override fun headers() = mapOf("Authorization" to "Bearer $apiKey")
    override fun buildBody(req: TtsRequest) = buildJsonObject {
        put("text", req.text)
        put("voice", req.voice.voice)
    }
    override suspend fun listVoices() = listOf(
        VoiceInfo("default", "Default", "en", engineId = ENGINE_INFO.id),
    )
    companion object {
        val ENGINE_INFO = EngineInfo(
            id = "my_engine",
            displayName = "My Engine",
            kind = EngineInfo.EngineKind.Cloud,
            requiresApiKey = true,
        )
    }
}
```

2. Зарегистрируйте в `EngineRegistry`:

```kotlin
"my_engine" -> MyTtsEngine(
    apiKey = cfg["apiKey"] ?: return null,
),
```

3. Добавьте в `allEngineInfos()`:

```kotlin
add(MyTtsEngine.ENGINE_INFO)
```

4. Добавьте API-ключ в `SettingsRepository.Keys` и UI.

## Добавить локаль

1. Создайте `app/src/main/res/values-XX/strings.xml`.
2. Добавьте язык в `app/build.gradle.kts`:
   ```kotlin
   resourceConfigurations += listOf("en", "ru", ..., "xx")
   ```
3. Переведите строки.

## Добавить новый тип разметки

1. Добавьте `MarkupCommand.*` в `core/markup/LTVMarkupParser.kt`.
2. Добавьте правило в `parseCommand()`.
3. Добавьте состояние в `MarkupState`.
4. Добавьте подсветку в `ui/markup/MarkupHighlighter.kt`.
5. Добавьте кнопку в `ui/components/MarkupToolbar.kt`.
6. Напишите тест в `app/src/test/.../markup/`.

## Добавить новый шаг в пайплайн

1. Создайте класс в `worker/` (например, `PostProcessStep.kt`).
2. Вызовите его в `GenerationPipeline.generate()` после `Encoding`.
3. Сохраните результат в БД.

## Добавить новый экран

1. Создайте `ui/screens/myscreen/MyScreen.kt` + `MyViewModel.kt`.
2. Добавьте `composable(Routes.MyScreen)` в `LTVNavHost.kt`.
3. Добавьте маршрут в `Routes`.
4. Добавьте иконку в `LTVScaffold` (если нужен bottom nav).

## Добавить новый источник текста

1. Создайте класс в `core/text/MySource.kt` (например, RSS, EPUB).
2. Реализуйте `Source` интерфейс с методом `import()`.
3. Добавьте UI-кнопку в `EditorScreen`.

