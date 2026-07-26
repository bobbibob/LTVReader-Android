# Архитектура

T2V — самостоятельное Android-приложение. Допустимы только два типа TTS:

1. `Local` — модель и runtime выполняют синтез непосредственно на телефоне.
2. `Cloud` — приложение обращается к публичному API-провайдеру.

Пользовательские вычислительные серверы и engine-host не поддерживаются.

```text
Compose UI
    ↓
ViewModel / StateFlow
    ↓
GenerationPipeline
    ↓
EngineRegistry
    ├── Local Android runtime
    └── Cloud API (HTTPS)
    ↓
WAV segments
    ↓
FFmpegBridge → M4A / audio mix
```

## Уровни

| Уровень | Каталог | Назначение |
|---|---|---|
| Core | `core/` | текст, разметка, аудио, нормализация, субтитры |
| Data | `data/` | Room и DataStore |
| TTS | `tts/` | локальные Android и облачные движки |
| Model catalog | `server/` | проверка и загрузка файлов с Hugging Face |
| Worker | `worker/` | пайплайн генерации и foreground service |
| UI | `ui/` | Compose-экраны и ViewModel |

Зависимости направлены от UI к бизнес-логике. `core/` не зависит от Android UI,
TTS или хранилища.

## Политика локальных моделей

Расширение `.onnx`, `.gguf` или `.safetensors` само по себе не подтверждает
совместимость. В каталог допускается только точная ревизия модели, для которой:

- встроен Android-runtime;
- известен полный набор обязательных файлов;
- проверены ABI и требования к памяти;
- выполнен реальный синтез на Android-устройстве.
