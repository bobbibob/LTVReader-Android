# Troubleshooting

## Сборка

### Gradle sync failed
- Проверьте `~/.gradle/gradle.properties` (нет ли битых символов).
- В Android Studio: File → Invalidate Caches / Restart.
- Удалите `.gradle/` и `app/build/` и попробуйте снова.

### `com.arthenica.ffmpegkit` not found
- Зависимость скачивается с `https://github.com/Arthenica/ffmpeg-kit`.
- Если корпоративный firewall блокирует — добавьте прокси в `gradle.properties`:
  ```properties
  systemProp.http.proxyHost=...
  systemProp.http.proxyPort=...
  ```

### `com.microsoft.onnxruntime` not found
- Используется `mavenCentral()`. Если не скачивается — включите
  `google()` (для AndroidX).

## Запуск

### Приложение крашится при старте
- Проверьте `adb logcat | grep ltvreader` — там будет stacktrace.
- Убедитесь, что `LTVApplication` зарегистрирован в `AndroidManifest.xml`
  (`android:name=".app.LTVApplication"`).

### "Engine not found" при выборе
- Установите API-ключ в Settings → TTS engines.
- Или включите remote host и убедитесь, что engine-host запущен.

### "Network error" при генерации через облачный движок
- Проверьте интернет.
- Убедитесь, что API-ключ валидный.
- OpenAI / ElevenLabs / Gemini / Azure могут возвращать 401/403 при
  неверном ключе.

### Kokoro не работает
- Проверьте наличие файлов `kokoro.onnx` и `voices.bin` в
  `app/src/main/assets/voices/kokoro/`.
- См. `app/src/main/assets/voices/kokoro/README.md`.

## Server host

### Connection refused
- Запустите с `--allow-lan`.
- Проверьте firewall: `sudo ufw allow 8765/tcp`.

### Engine not found
- Установите зависимости нужного движка:
  ```bash
  pip install kokoro-onnx      # для Kokoro
  pip install piper-tts        # для Piper
  pip install chatterbox-tts   # для Chatterbox
  ```

### Долгая генерация
- Chatterbox и Qwen3 — тяжёлые модели. Первый запуск может занять
  1-2 минуты (скачивание + инициализация).
- Используйте GPU (CUDA) для ускорения: Chatterbox / Qwen3 / OmniVoice
  поддерживают CUDA.

## Производительность

### Лаги UI на длинных текстах
- TextProcessor чанкование: 2500 символов по умолчанию.
- Уменьшите в Settings → General → Chunk size.

### Out of memory при генерации
- Уменьшите chunk size.
- Отключите превью waveform во время генерации.
- Используйте менее тяжёлый движок (Piper или Kokoro вместо Qwen3).

