# LTV Reader — server-host (Python)

Это **опциональный** сервер, который запускается на ПК или домашнем сервере.
Он поднимает тот же `engine_host.py` + `http_app.py`, что и в исходном
[LocalText2Voice](https://github.com/estebanstifli/LocalText2Voice), и
добавляет упрощённый эндпоинт `/synthesize` для прямого вызова из
Android-клиента без очереди задач.

## Зачем

Qwen3-TTS запускается на engine-host, а Android получает готовый WAV. Формат
GGUF сам по себе не означает совместимость с Android: нужен runtime, который
реализует именно аудиодекодер Qwen3-TTS. Ollama аудиовыход не реализует и
поэтому намеренно не регистрируется как TTS-движок.

## Установка

```bash
# 1. Установить host и официальный Qwen runtime
pip install -r requirements-qwen.txt

# 2. Запустить
python engine_host.py --port 8765 --allow-lan
```

## Использование из Android

В LTV Reader → Settings → Remote host:
- URL: `http://192.168.1.10:8765` (IP вашего ПК)
- Enable: ✓

После этого доступен `Qwen3 TTS (via remote host)`. По умолчанию используется
официальная модель `Qwen/Qwen3-TTS-12Hz-0.6B-CustomVoice`.

Каталог закрытый: сервер разрешает скачивать только официальные модели
CustomVoice 0.6B и 1.7B, для которых реализован текущий контракт синтеза.
Base/VoiceDesign, произвольные GGUF и другие репозитории отклоняются.

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
