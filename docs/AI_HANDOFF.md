# T2V: инструкция и журнал передачи для ИИ

Последнее обновление: 2026-07-27
Рабочая ветка большого аудиомодуля: `codex/audio-production`
Текущий коммит ветки на момент записи: `e627dd2` (CI run 30273098517 green, APK на R5CN30LJS4W)
Прошлый зелёный эталон до этого: `9ed86ab` (CI run 30270795662, APK на R5CN30LJS4W)
Ещё раньше: `bb9b374` (CI run 30248614315, APK на R5CN30LJS4W)

Этот файл — главный оперативный контекст для следующего ИИ-агента. Его нужно
обновлять после каждого существенного изменения архитектуры, поведения,
ограничений, CI, состава моделей или плана. Не заменять фактические результаты
предположениями: «реализовано» означает, что код существует, «проверено» —
что соответствующая проверка действительно прошла.

## Обязательные требования владельца

- **Локальной сборки нет.** Никакого `./gradlew`, `gradle assembleDebug`,
  прямого `kotlinc` или `java -jar` для KSP на хосте разработчика. Все
  правки Kotlin/Java/XML компилируются **только** внутри GitHub Actions
  workflow `android.yml` на ветке `codex/audio-production`. Запускать
  `gh workflow run` после пуша можно только с явного согласия владельца.
  Если нет сети к GitHub — код коммитится и пушится; CI должен стартовать
  автоматически благодаря push-trigger. Проверка синтаксиса на хосте не
  считается валидной верификацией: на машине нет Android SDK, нет Java
  toolchain с правильными Android-зависимостями, и `kotlinc` локально
  даёт ложноположительные результаты.
- Приложение называется **T2V**, package/application id — `com.t2v`.
- Никаких server-host, Ollama-host или других промежуточных серверных движков.
  Допустимы только:
  - модели, полностью исполняемые на Android-устройстве;
  - облачные API, к которым приложение обращается напрямую.
- Модели не включать в Git и APK. Пользователь скачивает их явно из приложения.
- Runtime можно установить автоматически при выборе модели, но приложение
  обязано объяснять размер и состав загрузки.
- В каталог допускаются только модели, чьи точные файлы и runtime совместимы
  с Android ARM64. Не показывать серверную/PyTorch-модель как рабочую локальную.
- Kokoro — первый вариант TTS по умолчанию.
- Сборка Gradle выполняется **только в GitHub Actions**. Не запускать локальную
  Gradle-сборку **никогда**: ни `./gradlew`, ни `gradle`, ни прямой `kotlinc`, ни
  `java -jar ...` для KSP. Проверка синтаксиса делается только через `gh workflow run`
  + `gh run view --log`. На машине разработчика нет Android SDK и Gradle wrapper
  считается работоспособным, поэтому любая локальная попытка сборки считается
  ошибкой процесса, а не валидным способом верификации.
- APK с успешного CI можно скачивать и устанавливать через ADB:
  `/Users/robertbiktimirov/Downloads/platform-tools/adb`.
- После каждой зелёной промежуточной CI-сборки устанавливать APK на
  подключённое устройство через ADB, чтобы владелец мог тестировать текущий
  этап. Перед установкой проверить serial через `adb devices`.
- Текущее USB-устройство при последней проверке: `R5CN30LJS4W`.
- Не трогать пользовательский untracked-файл `test_text_processor.kt`.

## Репозиторий и Git

- Локальный путь: `/Users/robertbiktimirov/Downloads/books/t2v`
- GitHub: `bobbibob/LTVReader-Android`
- Предыдущая ветка model manager: `agent/huggingface-model-manager`
- Большой трёхдорожечный модуль разрабатывается отдельно:
  `codex/audio-production`.
- Базовый проверенный коммит до нового аудиомодуля:
  `e052f1c feat: add Russian voices and voice cloning`.
- Draft PR предыдущей ветки:
  `https://github.com/bobbibob/LTVReader-Android/pull/1`.

## Что реально работало на устройстве до новой ветки

- Локальный Kokoro 82M через sherpa-onnx.
- Загрузка Kokoro из приложения с размером, процентом и количеством байт.
- Локальные русские Piper/VITS: Ирина, Денис, Дмитрий, Руслан.
- Голос Ирина был скачан на телефоне (около 67,2 MB).
- Русское предложение было успешно синтезировано в WAV на самом телефоне.
- ElevenLabs cloud voice cloning имеет UI и конфигурацию API.
- CI `30205633140` для коммита `e052f1c` был зелёным.

## Реализовано в `codex/audio-production`

### Проекты и главы

- У проекта появились `author` и persisted SAF URI выбранной выходной папки.
- При создании проекта папка обязательна.
- Одна генерация хранится как именованная глава (`AudiobookEntity.title`).
- Добавлен порядок глав внутри проекта (`orderIndex`).

### Трёхдорожечный редактор

- Типы дорожек: `VOICE`, `MUSIC`, `SOUND`.
- Room-сущности:
  - `AudioTrackEntity`;
  - `AudioClipEntity`;
  - `ChapterExportEntity`.
- Миграция Room `1 -> 2` сохраняет существующие проекты.
- Для клипа сохраняются:
  - исходный файл;
  - позиция на общей шкале времени;
  - границы обрезки;
  - скорость;
  - gain;
  - fade in/out;
  - loop/lock;
  - связь с тегом разметки.
- Экран редактора умеет импортировать отдельные клипы музыки и эффектов,
  менять позицию/обрезку/скорость/gain, делить, удалять и переставлять клипы.
- Кнопка «Сохранить» записывает все три дорожки и позиции в Room.
- Кнопка «Экспорт MP3» рендерит дорожки, приглушает музыку под речью,
  микширует эффекты и сохраняет новый файл без перезаписи в `exports/`
  выбранной папки проекта.

### FFmpeg и MP3

- В `FFmpegBridge` добавлен timeline-render и трёхдорожечный production mix.
- Для настоящего MP3 используется `libmp3lame`, а не файл AAC с расширением
  `.mp3`.
- CI-скрипт собирает LAME 3.100 из исходников с проверкой SHA-256 и линкует
  его в Android FFmpeg.
- В CI `30207955605` сборка и проверка FFmpeg+LAME прошли.
- Первый CI упал только из-за неправильного пути копирования артефакта;
  это исправлено коммитом `da815ce`.

### Экран моделей

- Добавлены вкладки:
  - «Голос»;
  - «Музыка»;
  - «Звуки».
- Выбор модели хранится независимо для каждой категории в DataStore.
- Реальные локальные модели голоса:
  - Kokoro 82M;
  - Piper/VITS Ирина;
  - Piper/VITS Денис;
  - Piper/VITS Дмитрий;
  - Piper/VITS Руслан;
  - Piper/VITS Amy (`en-US`);
  - Piper/VITS Cori (`en-GB`).
- Все Piper-модели используют уже встроенный sherpa-onnx runtime и
  скачиваются отдельно.
- Stable Audio Open Small и Stable Audio 3 Small отображаются в правильных
  вкладках только как будущие варианты. Выбор заблокирован, пока нет
  рабочего Android runtime и device smoke-test. Не снимать блокировку
  простым добавлением URL модели.

### Универсальный registry моделей

- Добавлен `core/model/GenerationModelCatalog.kt` — единый типизированный
  источник категорий Voice/Music/Sound, capabilities, runtime, ABI, минимальной
  RAM, лицензии, repository/revision, размера и статуса поддержки.
- `Verified` — единственный статус, разрешающий установку. Модели со статусами
  `RuntimeInDevelopment` и `Experimental` нельзя случайно выдать за рабочие.
- Stable Audio Open Small зарегистрирован один раз для Music и Sound и требует
  общий LiteRT runtime; установка пока запрещена.
- PocketTTS и ZipVoice внесены как будущие voice-cloning варианты, но не
  разрешены к загрузке до обновления runtime и device smoke-test.
- Добавлены unit-тесты уникальности каталога, обязательного размера проверенных
  моделей и совместного LiteRT runtime для Music/Sound.
- Registry и тесты отправлены коммитом `e34d1c4`; запущен CI
  `30209963092`.

## Текущее состояние CI

- Run `30207678998`: FFmpeg собрался, но артефакт копировался не в workspace.
- Run `30207955605`: FFmpeg+LAME прошёл; Kotlin остановился из-за импортов
  внизу `EditorScreen.kt`.
- Импорты исправлены коммитом `91e38d4`.
- Run `30208149675` запущен и на момент последнего обновления выполняется.
  Он упал до Kotlin на сетевом timeout `git.ffmpeg.org`; источник переключён
  на официальный GitHub mirror с сохранением проверки commit SHA.
- Run `30208347664` запущен после переключения mirror.
- Run `30208347664`: FFmpeg+LAME, Room и Kotlin-компиляция прошли. Из 52
  unit-тестов упал только `PiperRussianCatalogTest`, потому что он ожидал
  размер общего каталога 4 после добавления двух английских моделей.
- Тест изменён: отдельно требует ровно четыре русские модели, наличие `en-US`
  и `en-GB`, уникальность и корректность архивов всего каталога.
- Исправление теста отправлено коммитом `5dc1e13`; запущен CI
  `30209325061`.
- CI `30209325061` полностью зелёный: 52 unit-теста, debug APK, подпись,
  FFmpeg+LAME и sherpa-onnx packaging прошли.
- Artifact `app-debug` (ID `8634095371`) скачан и ZIP проверен без ошибок.
- Устройство `R5CN30LJS4W` обнаружено. `adb install -r` не выполнился:
  `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, потому что уже установленный
  `com.t2v.debug` подписан другим debug-ключом. Не удалять приложение без
  разрешения владельца: uninstall может удалить настройки и внутренние модели.
- Владелец разрешил удаление. `com.t2v.debug` удалён с устройства вместе со
  старыми debug-данными, затем APK из зелёного CI `30209325061` установлен
  успешно.
- T2V запущен launcher-событием: `com.t2v.ui.MainActivity`, процесс
  `com.t2v.debug` появился (PID 19147). Установленная промежуточная версия
  соответствует коммиту `5dc1e13`; следующий registry-коммит `e34d1c4`
  проверяется CI отдельно.
- Следующему агенту сначала проверить самый новый CI после этого исправления:

```bash
gh run list --workflow android.yml --branch codex/audio-production --limit 3
```

## Что ещё не готово

- Нет локального исполняемого генератора музыки.
- Нет локального исполняемого генератора звуков.
- Нет прямых cloud-провайдеров музыки/эффектов в новом registry.
- Теги `{{music ...}}` и `{{sfx ...}}` парсятся, но ещё не запускают
  генерацию и не создают клипы на точной временной позиции.
- Нет отдельного экрана списка глав проекта и открытия старой главы.
- UI аудиоредактора пока функциональный, но это карточки с числовыми полями,
  а не полноценная визуальная waveform timeline.
- Нет проверки Room migration на реальном устройстве.
- Нет end-to-end теста экспорта MP3 на телефоне.
- Новая ветка ещё не установлена на устройство.

## Следующий план действий

> ПРИОРИТЕТ ИЗМЕНЁН владельцем 2026-07-26: работы над model registry,
> LiteRT, музыкой и звуками временно приостановлены после коммита `e34d1c4`.
> Текущая активная задача — выразительная TTS-разметка для эмоций, дыхания,
> шёпота, смеха и других голосовых реакций.

1. Исправить фактический разрыв: `LTVMarkupParser` распознаёт команды, но
   `TextProcessor.process()` их не применяет, а pipeline не передаёт emotion.
2. Добавить единый семантический слой выразительной речи:
   emotion, delivery, одноразовые vocal cues и reset.
3. Адаптировать семантику по движкам:
   OpenAI instructions, Gemini natural-language prompt, Eleven v3 audio tags,
   Azure SSML `mstts:express-as`, Kokoro/Piper — только честные акустические
   приближения через speed/pitch/volume.
4. Обновить parser, highlighter, toolbar, тесты и `docs/LTV_MARKUP.md`
   одновременно, как требует AGENTS.md.
5. Проверить только через GitHub Actions; каждый зелёный промежуточный APK
   устанавливать через ADB.
6. После завершения этой задачи вернуться к приостановленному плану:
7. Довести GitHub Actions до зелёного APK. Исправлять ошибки по одной,
   не запускать Gradle локально.
8. Добавить экран проекта со списком множества именованных глав:
   создать, открыть, продолжить, экспортировать.
9. Ввести общий registry генераторов для `VOICE`, `MUSIC`, `SOUND`:
   capabilities, runtime, точная ревизия, файлы, размеры, лицензия,
   требования к RAM/ABI, install/verify/generate.
10. Подключить Stable Audio Open Small только после реализации LiteRT-пути:
   text encoder + DiT + decoder, проверка manifest/хешей и тест на ARM64.
11. Добавить прямые облачные music/SFX API как отдельные варианты без
   промежуточного сервера.
12. Расширить разметку музыки/эффектов, одновременно обновив:
   `LTVMarkupParser`, `MarkupHighlighter`, `MarkupToolbar`, тесты и
   `docs/LTV_MARKUP.md`.
13. Во время TTS вычислять соответствие текстового offset времени речи;
   по нему создавать music/sound clips ровно в позиции тега.
14. Сделать визуальную трёхдорожечную timeline: масштаб, playhead,
   waveform, drag/trim/split, независимые mute/solo/gain/fades.
15. Провести device smoke-test: сохранение, перезапуск, восстановление
   таймлайна, экспорт MP3, воспроизведение и проверка длительности.
16. Обновить CHANGELOG/ROADMAP/этот файл, затем подготовить отдельный PR.

### Журнал активной задачи: выразительная речь

- Подтверждён дефект: `emotion` и custom-параметры существовали в
  `MarkupState`, но не копировались в `VoiceConfig` при создании `TtsRequest`.
- `GenerationPipeline` исправлен: передаёт `emotion` и объединяет
  `markupState.custom` с `voice.extras`.
- Следующий шаг: разрезать исходный текст на последовательные размеченные
  spans, чтобы состояние применялось не ко всему документу, а ровно после
  позиции каждого тега.
- Создан корневой `tags.md`: канонический список emotion/delivery, одноразовых
  vocal reactions, prosody, reset, pronunciation и project/audio tags,
  семантика области действия и таблица поддержки движков. В отличие от этого
  handoff-файла, `tags.md` является пользовательской документацией и должен
  войти в Git.
- Парсер расширен командами `delivery/style`, `whisper`, `shout`, `emphasis`,
  15 vocal reaction tags и `reset emotion|delivery|prosody|voice|all`.
- `MarkupState` хранит delivery/emphasis и очередь одноразовых vocal cues;
  highlighter и parser unit-test обновлены одновременно.
- Добавлен `LTVMarkupParser.parseSpans()`: состояние применяется строго после
  позиции тега, pause накапливается перед следующим span, vocal cues
  потребляются ровно один раз.
- `TextProcessor.process()` теперь использует spans при наличии `{{...}}`,
  удаляет команды из произносимого текста и переносит state/pause в TextChunk.
- Добавлен `ExpressiveSpeech`: единый renderer инструкций, Eleven v3 audio
  tags и локальный fallback-профиль speed/pitch/volume.
- Pipeline переносит delivery/emphasis/vocal cues структурировано. OpenAI
  получает `instructions`, Gemini — direction prompt, Eleven v3 — native
  square tags. Kokoro/Piper никогда не получают reaction tag как текст.
- Azure получает allowlist-ограниченный `mstts:express-as`; неизвестное значение
  не вставляется в SSML. Toolbar получил быстрые кнопки Emotion/Whisper/Breath.
- `docs/LTV_MARKUP.md` связан с полным корневым `tags.md` и содержит пример.
- Добавлены unit-тесты end-to-end parser→TextProcessor, Eleven v3 mapping и
  локального prosody fallback без утечки reaction tags в произносимый текст.
- `ExpressiveSpeech.localFallback` теперь вырезает `t2v.emphasis` и
  `t2v.vocalCues` из `extras` — локальные движки не должны получать
  cloud-only семантические ключи ни в каких логах и UI.
- `GenerationPipeline` переключён с хрупкой строковой проверки
  `engineId == "kokoro" || engineId == "piper_ru"` на
  `engine.info.kind == EngineKind.Local`. Будущие on-device движки
  автоматически получат честную просодию вместо raw-тегов.
- `ExpressiveSpeechTest` расширен: тесты на cloud-only extras stripping,
  на instruction-строку для OpenAI/Gemini и на пустой `elevenV3Prefix`.
- Коммит `efcfec1` отправлен в `codex/audio-production`; CI 30227504417
  ожидается. После зелёного CI — `adb install -r` на `R5CN30LJS4W`.

### Журнал активной задачи: music/sound generators + 3-track editor

- Добавлен `com.t2v.generators.Generator` контракт (Music / Sound) и параллельный
  `GeneratorRegistry`, прокинутый через `AppContainer.generatorRegistry`.
- Реализованы `BundledMusicGenerator` / `BundledSoundGenerator` поверх 6
  плейсхолдеров в `app/src/main/assets/{music,sound}` (whitelisted в
  `.gitignore`). Это даёт рабочий smoke-test дорожек Music и Sound на любом
  устройстве без модели или API-ключа.
- Реализован `ElevenLabsSoundEffectsGenerator` (cloud, public
  `POST /v1/sound-generation`).
- `SettingsRepository` получил два новых ключа:
  `selected_music_generator`, `selected_sound_generator`.
- `AudioEditorScreen` показывает два `GeneratorPanel` (Music и Sound) с
  prompt, чипами выбора генератора и кнопкой Run. После генерации клип
  автоматически добавляется в нужную дорожку (VOICE / MUSIC / SOUND).
  Существующие `TrackEditor` карточки и Save / Export MP3 не тронуты.
- 5 unit-тестов в `GeneratorRegistryTest`.
- Коммиты `fecc56e`, `6f2f0c3`, `bb9b374` отправлены в
  `codex/audio-production`. CI 30248614315 — зелёный. APK 42.6 МБ
  установлен на `R5CN30LJS4W` через `adb install -r`, проверен запуск
  (`pidof com.t2v.debug = 5867`, `dumpsys window` показывает MainActivity
  в foreground, в logcat нет FATAL/Exception).
- Что осталось: визуальный waveform timeline, реальные on-device генераторы
  музыки/звуков (LiteRT), Music cloud-провайдеры, привязка
  `{{music ...}}`/`{{sfx ...}}` к TTS-времени.

## Правила безопасной реализации моделей

- Не доверять имени репозитория или расширению файла.
- Каталожная запись должна фиксировать repository, revision и перечень файлов.
- До установки показывать суммарный размер; во время загрузки —
  проценты и `скачано / всего` в KB/MB/GB.
- Скачивать во staging, проверять файлы/размеры/хеши, затем атомарно
  активировать модель.
- При выборе модели автоматически проверить runtime. Если runtime отсутствует,
  показать состав и размер дополнительной загрузки и установить после
  подтверждения пользователя.
- Модель становится выбираемой только после runtime probe и минимального
  синтеза на поддерживаемом устройстве.
- Удаление общей runtime-зависимости не должно ломать другие установленные
  модели; нужен reference count или вычисление потребителей.

### Журнал активной задачи: TagDocs Info dialog

- Каждый движок/модель/генератор в `GenerationModelCatalog` теперь имеет
  блок `TagDocs` с tagline, supported/partial/ignored, примерами и promptHelp.
- Новый компонент `ui/components/TagInfoDialog.kt` показывает всю
  информацию в `AlertDialog` с прокруткой; локализован в 11 локалях
  (`info_*` строковые ключи).
- В `ModelsScreen` каждая `ModelDetailCard` получила кнопку `Info`
  (`Icons.Default.Info`) в правом верхнем углу; нажатие открывает
  диалог с реальными данными из каталога.
- `EngineInfo` kokoro/piper_ru получили `ENGINE_TAGS` записи, чтобы
  движки в `Voices` тоже могли резолвиться в `tagDocsForEngine`.
- `Catalog.repositoryFor()` / `licenseFor()` помогают UI не дублировать
  метаданные.
- Коммиты: `98e7a8b` (функциональность), `414e2cf` (фикс кавычек в
  `TagDocs`). CI 30270164145 на ветке `codex/audio-production`
  запущен и ожидается зелёным.

### Журнал активной задачи: TagDocs Info dialog + русский текст по умолчанию

- Каждый движок/модель/генератор в `GenerationModelCatalog` теперь имеет
  блок `TagDocs` с tagline, supported/partial/ignored, примерами и promptHelp.
- Все TagDocs-строки и пользовательский текст в `ModelsScreen.kt` переведены
  на русский язык (UI приложения - для русскоязычного владельца). LTV-разметка
  `{{...}}` и имена движков/голосов остаются латиницей.
- Новый компонент `ui/components/TagInfoDialog.kt` показывает всю
  информацию в `AlertDialog` с прокруткой; локализован в 11 локалях
  (`info_*` строковые ключи).
- В `ModelsScreen` каждая `ModelDetailCard` получила кнопку `Info`
  (`Icons.Default.Info`) в правом верхнем углу; нажатие открывает
  диалог с реальными данными из каталога.
- `EngineInfo` kokoro/piper_ru получили `ENGINE_TAGS` записи, чтобы
  движки в `Voices` тоже могли резолвиться в `tagDocsForEngine`.
- `Catalog.repositoryFor()` / `licenseFor()` помогают UI не дублировать
  метаданные.
- CI-проверка: run `30270795662` для коммита `9ed86ab` зелёный (test + build),
  APK скачан, готов к `adb install -r` на `R5CN30LJS4W`. После правки русского
  текста будет ещё один коммит + CI.

## Журнал активной задачи: XML-теги <music>/<sfx> с привязкой к TTS

- `LTVMarkupParser` распознаёт два новых тега: `<music>промпт</music>` и
  `<sfx>промпт</sfx>`. Содержимое тега — это промпт; атрибутов нет.
  Громкость и скорость не задаются — они берутся из настроек выбранной
  модели и редактируются в AudioEditorScreen как у любого другого клипа.
- `parseSpans()` теперь рвёт голосовой поток в каждом теге: всё до
  открывающего `<` становится концом предыдущего voice-чанка, всё после
  закрывающего `>` — началом нового. Это значит, что границы речи и
  позиция клипа совпадают до символа.
- `parseSpans()` помечает каждый разрыв тегом через
  `MarkupSpan.trailingAudioTag`; `TextProcessor.process()` теперь возвращает
  `ProcessResult` (sections, chunks, audioTags).
- `GenerationPipeline` после синтеза речи вызывает `AudioTagInserter`,
  который для каждого тега генерирует WAV через `GeneratorRegistry` и
  сохраняет `AudioClipEntity` в Room на нужной дорожке.
  `timelineStartMs` = сумма `pauseBeforeMs + durationMs` уже сохранённых
  сегментов.
- `MarkupHighlighter` красит теги accent-цветом; `MarkupToolbar` получил
  две новые кнопки «Музыка» и «Звук».
- Новые строки `markup_music` и `markup_sfx` добавлены во все 11 локалей.
- Тесты: новый `LTVMarkupAudioTagsTest` (4 кейса), `TextProcessorTest`
  обновлён под `ProcessResult`.

## Журнал активной задачи: вернуть скачивание моделей и добавить новые

- В `ModelsScreen` восстановлена карточка скачивания Kokoro, которую раньше
  скрывал `if (false)`. Прогресс в байтах/процентах, отмена, выбор после
  установки — всё работает поверх существующего `downloadKokoro()` и
  состояния `ModelsState.{kokoroInstalled,loadingCatalog,downloading,
  downloadProgress,downloadedBytes,downloadTotalBytes}`.
- Раздел Piper/VITS теперь группирует голоса по языку (`PiperVoiceGroup`) и
  показывает уже скачанные Amy/Cori рядом с русскими, а также добавленные
  немецкие, французские, испанские, итальянские, китайский и японский голоса
  из каталога `k2-fsa/sherpa-onnx tts-models`.
- Все новые пункты живут в `PiperRussianTtsEngine.RUSSIAN_VOICES`; их
  отдаёт тот же `RussianVoiceInstaller` и тот же `PiperRussianTtsEngine`.
  Никаких новых runtimes не требуется — все они совместимы со встроенным
  SherpaOnnx.
- В `GenerationModelCatalog` зарезервированы два облачных пункта —
  `openai-music` и `elevenlabs-sound-clip` — со статусом
  `RuntimeInDevelopment` и `canInstall = false`. Это нужно, чтобы Info-карточки
  в будущем могли ссылаться на них, не показывая их как готовые кнопки.
- `PiperRussianCatalogTest` переписан под три кейса: четыре русские голоса,
  десять языков, единый источник `sherpa-onnx/releases`.
- `GenerationModelCatalogTest` теперь фиксирует, что `openai-music` и
  `elevenlabs-sound-clip` остаются `RuntimeInDevelopment` (защита от
  регрессии, если кто-то случайно повысит их статус).

## Как обновлять этот файл

После каждого этапа:

1. Обновить дату и текущий коммит.
2. Перенести пункт из «не готово» в «реализовано» только после появления кода.
3. Дописать отдельную строку о фактической проверке: CI run/device/model.
4. Зафиксировать новые ограничения пользователя дословно по смыслу.
5. Указать известный дефект и ближайшее конкретное действие.
6. Не удалять важную историю решений, пока ветка не слита и не выпущена.
