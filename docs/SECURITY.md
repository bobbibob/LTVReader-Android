# Безопасность

## Что мы делаем

- API-ключи хранятся в `EncryptedSharedPreferences` (Tink).
  См. `data/SettingsRepository.kt` — будет переведено на encryption
  в v0.2.0.
- Трафик к публичным API — только HTTPS.
- HTTPS используется для встроенных облачных API и Hugging Face.
- HTTP может использоваться только явно настроенным Custom HTTP API.

## Что нужно сделать вам

### Защита API-ключей

API-ключи хранятся в `SharedPreferences`. **Не включайте их в git**.

`.gitignore` уже исключает `local.properties` и keystore.

## Reporting vulnerabilities

Нашли уязвимость? Пишите на security@t2v.example.com
(замените на реальный адрес при публикации).
