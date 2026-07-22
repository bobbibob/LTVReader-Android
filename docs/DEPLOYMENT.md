# Деплоймент

## Google Play

### Подготовка

1. Зарегистрировать аккаунт разработчика Google Play (25 USD, единоразово).
2. Создать приложение в Google Play Console.
3. Сгенерировать ключ подписи:
   ```bash
   keytool -genkey -v -keystore ltvreader.keystore \
     -alias ltvreader -keyalg RSA -keysize 2048 -validity 10000
   ```
4. Положить keystore в безопасное место (НЕ коммитить).
5. Настроить `~/.gradle/gradle.properties`:
   ```properties
   LTVREADER_UPLOAD_STORE_FILE=path/to/ltvreader.keystore
   LTVREADER_UPLOAD_KEY_ALIAS=ltvreader
   LTVREADER_UPLOAD_STORE_PASSWORD=****
   LTVREADER_UPLOAD_KEY_PASSWORD=****
   ```

### Сборка release-AAB

```bash
./gradlew :app:bundleRelease
# → app/build/outputs/bundle/release/app-release.aab
```

### Загрузка в Google Play

1. Google Play Console → Release → Production → Create new release.
2. Загрузить AAB.
3. Заполнить release notes, version code, target API.
4. Submit for review.

## F-Droid

F-Droid принимает обычные APK, подписанные F-Droid-ключом. См.
[f-droid.org](https://f-droid.org) для требований.

## Прямой APK

```bash
./gradlew :app:assembleRelease
# → app/build/outputs/apk/release/app-release.apk
```

## CI/CD (GitHub Actions пример)

`.github/workflows/android.yml`:
```yaml
name: Android CI
on: [push, pull_request]
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 17
      - uses: android-actions/setup-android@v3
      - run: ./gradlew :app:assembleDebug :app:testDebugUnitTest
      - uses: actions/upload-artifact@v4
        with:
          name: apk
          path: app/build/outputs/apk/debug/app-debug.apk
```

## Размер APK

- **Debug**: ~70 MB (с Kokoro-моделью внутри)
- **Release без Kokoro**: ~10 MB
- **Release с Kokoro**: ~160 MB (модель скачивается отдельно)

Рекомендация: Kokoro-модель **не включать** в APK, а скачивать при
первом запуске (это делается в `KokoroTtsEngine.preload()`).

