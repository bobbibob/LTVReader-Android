# LTV Reader — server-host (Python)

Это **опциональный** сервер, который запускается на ПК или домашнем сервере.
Он поднимает тот же `engine_host.py` + `http_app.py`, что и в исходном
[LocalText2Voice](https://github.com/estebanstifli/LocalText2Voice), и
добавляет упрощённый эндпоинт `/synthesize` для прямого вызова из
Android-клиента без очереди задач.

## Зачем

На Android-устройстве **нельзя** запустить Chatterbox / Qwen3 / OmniVoice /
Piper (Windows-EXE / PyTorch 2 ГБ). Чтобы использовать эти движки с
телефона, нужно поднять engine-host на машине, где они есть.

## Установка

```bash
# 1. Скопировать сюда код оригинала для app/ и engines/
#    (или поставить через pip editable install из исходного репо).
cp -r ../Text2Voice/app .
cp -r ../Text2Voice/engines .

# 2. Установить зависимости
pip install -r requirements.txt

# 3. Установить голоса (через UI оригинала, либо вручную в engines/piper/)

# 4. Запустить
python engine_host.py --port 8765 --allow-lan
```

## Использование из Android

В LTV Reader → Settings → Remote host:
- URL: `http://192.168.1.10:8765` (IP вашего ПК)
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
- `POST /engines/{id}/preload` — прогрев
- `POST /engines/{id}/unload` — выгрузка
- `POST /synthesize` — **упрощённый** синтез, отдаёт WAV сразу
- `POST /jobs` — обычная очередь задач (как в оригинале)
- `GET  /jobs/{id}` — статус задачи
- `POST /jobs/{id}/cancel` — отмена
