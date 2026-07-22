# Безопасность

## Что мы делаем

- API-ключи хранятся в `EncryptedSharedPreferences` (Tink).
  См. `data/SettingsRepository.kt` — будет переведено на encryption
  в v0.2.0.
- Трафик к публичным API — только HTTPS.
- HTTP разрешён только к локальной сети (см.
  `res/xml/network_security_config.xml`).
- engine-host не слушает `0.0.0.0` по умолчанию — нужен `--allow-lan`.

## Что нужно сделать вам

### Защита API-ключей

API-ключи хранятся в `SharedPreferences`. **Не включайте их в git**.

`.gitignore` уже исключает `local.properties` и keystore.

### Использование на публичном Wi-Fi

Не используйте engine-host в публичных сетях без VPN.

### Engine-host в production

Для production-сценариев добавьте:
- HTTPS (через reverse-proxy: nginx, caddy).
- Аутентификацию (Bearer token).
- Firewall: открыть порт только для вашей подсети.

## Reporting vulnerabilities

Нашли уязвимость? Пишите на security@ltvreader.example.com
(замените на реальный адрес при публикации).

