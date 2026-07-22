# Глоссарий

## TTS (Text-to-Speech)
Преобразование текста в речь. Иногда называют «синтез речи».

## ASR (Automatic Speech Recognition)
Распознавание речи. Используется в Faster Whisper для верификации.

## G2P (Grapheme-to-Phoneme)
Преобразование букв в фонемы. Необходимо для качественного TTS.

## NNAPI (Android Neural Networks API)
Android API для запуска нейросетей на NPU/GPU. Используется
onnxruntime-android для ускорения Kokoro.

## XNNPACK
Библиотека ускорения CPU-инференса для float-моделей. Используется
onnxruntime как fallback.

## ONNX (Open Neural Network Exchange)
Открытый формат моделей. Поддерживается ORT, PyTorch, TensorFlow.

## ORT (ONNX Runtime)
Microsoft-овский движок инференса ONNX-моделей.

## WAV
Несжатый аудиоформат. Используется как промежуточный в LTV Reader.

## MP3
Сжатый аудиоформат. Финальный формат экспорта.

## PCM (Pulse-Code Modulation)
Способ хранения аудио в виде последовательности значений амплитуды.
16-bit PCM = 2 байта на сэмпл.

## Sample rate
Частота дискретизации. 24 kHz = 24 000 сэмплов в секунду. Kokoro использует 24 kHz.

## Bitrate
Битрейт MP3. 192 kbps — стандарт для подкастов.

## Ducking
Автоматическое приглушение фоновой музыки, когда говорит голос.
Реализовано через ffmpeg `sidechaincompress`.

## Sidechain compression
Техника ducking'а: компрессор на music-канале управляется уровнем voice-канала.

## FGS (Foreground Service)
Android-сервис с уведомлением. Защищает задачу от убийства системой.

## Room
Android-библиотека для работы с SQLite.

## DataStore
Замена SharedPreferences. Поддерживает Flow, async, типизацию.

## Compose
Декларативный UI-фреймворк от Google. Заменил XML-разметку + View.

## Material 3
Третья версия Material Design. Используется в Compose.

## ADB (Android Debug Bridge)
Утилита для отладки Android-устройств.

## APK / AAB
APK = Android Package. AAB = Android App Bundle (для Google Play).

## Gradle
Система сборки Android-проектов.

## KSP (Kotlin Symbol Processing)
Аналог annotation processor для Kotlin. Используется Room.

## Hilt
DI-фреймворк от Google. Не используется в LTV Reader (DI вручную через AppContainer).

## WorkManager
API для отложенных/периодических фоновых задач.

## Coroutine
Kotlin-аналог горутин. Легковесные потоки.

## StateFlow
Типобезопасный Flow с одним текущим значением. Используется для UI-стейта.

## Sealed class
Kotlin-конструкция для иерархий типов. Используется для MarkupCommand, EngineInfo.EngineKind.

## LTV (Local Text-to-Voice)
Внутреннее название разметки LocalText2Voice. Синтаксис `{{...}}`.

## Engine host
Python-бэкенд, запускаемый на ПК. Позволяет Android использовать
движки, которые не работают на устройстве (Chatterbox, Qwen3, OmniVoice, Piper).

## Custom HTTP
TTS-движок, который обращается к произвольному HTTP-эндпоинту.
Шаблон тела и формат ответа настраиваются.

## Engine registry
Реестр всех доступных TTS-движков. Создаёт экземпляры по id.

## Project
Импортированный документ, который пользователь редактирует. Хранится в Room.

## Audiobook
Результат генерации проекта. Содержит сегменты и финальный MP3.

## Segment
Один чанк текста + сгенерированный WAV. Хранится в Room.

## Markup state
Набор параметров (voice, lang, speed, volume), прикреплённый к чанку.

## Chunk
Безопасный фрагмент текста ≤ chunkSize символов. Генерируется TextProcessor.

## Section
Часть текста с заголовком (глава, урок, модуль). Определяется по regex.

