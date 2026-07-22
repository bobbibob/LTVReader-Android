# Engine Host — how to use

`server-host/` — это **опциональный** Python-бэкенд, который запускается
на ПК/сервере. Android-клиент подключается к нему по HTTP (Wi-Fi) и
использует движки, которые не могут работать на телефоне.

## Когда нужен

- Хотите **Piper**, **Chatterbox**, **Qwen3 TTS**, **OmniVoice** на Android
  (без него эти движки недоступны).
- Хотите **Faster Whisper**-верификацию.
- Хотите использовать **тяжёлые модели Kokoro v1+** (более качественные,
  но > 500 МБ).

## Установка

```bash
cd server-host
pip install -r requirements.txt

# Скопировать код оригинала для app/ и engines/
cp -r ../Text2Voice/app .
cp -r ../Text2Voice/engines .

# Установить зависимости оригинала
pip install -r ../Text2Voice/requirements.txt
pip install -r ../Text2Voice/requirements-kokoro-engine.txt

# Запустить
python engine_host.py --port 8765 --allow-lan
```

## Использование из Android

Settings → Remote host:
- URL: `http://192.168.1.10:8765`
- Enable: ✓

После этого в выпадающем списке движков появятся:
- Piper (via remote host)
- Chatterbox (via remote host)
- Qwen3 TTS (via remote host)
- OmniVoice (via remote host)

## Эндпоинты

- `GET  /info` — метаданные сервера
- `GET  /engines` — список движков
- `GET  /engines/{id}/voices` — голоса
- `POST /engines/{id}/preload` — прогрев модели
- `POST /engines/{id}/unload` — выгрузка
- `POST /synthesize` — **упрощённый** синтез (отдаёт WAV сразу)
- `POST /jobs` — очередь задач
- `GET  /jobs/{id}` — статус
- `POST /jobs/{id}/cancel` — отмена

## Безопасность

`engine_host.py` по умолчанию слушает `127.0.0.1`. Чтобы Android
мог подключиться, используйте `--allow-lan` или `--host 0.0.0.0`.

`network_security_config.xml` в Android разрешает cleartext только
к локальной сети (192.168/16, 10/8, 172.16/12, localhost). К
публичным серверам cleartext запрещён.

## Troubleshooting

- **Connection refused**: проверьте, что хост слушает `0.0.0.0:8765`,
  а не `127.0.0.1`.
- **Time out**: проверьте, что телефон и ПК в одной Wi-Fi сети.
- **Engine not found**: установите зависимости движка в `server-host/`.
- **API key required**: укажите API-ключ в `config.json` или через
  переменные окружения.

