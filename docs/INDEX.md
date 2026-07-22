# Документация LTV Reader

Полный индекс документации.

## Быстрый старт
- [README](../README.md) — обзор проекта
- [QUICKSTART](QUICKSTART.md) — собрать и запустить за 5 минут

## Архитектура и портирование
- [PORTING](PORTING.md) — что портировано из оригинала
- [ROADMAP](ROADMAP.md) — текущий статус и планы
- [ARCHITECTURE](ARCHITECTURE.md) — диаграммы компонентов
- [INTERNALS](INTERNALS.md) — как устроено внутри
- [LTV_MARKUP](LTV_MARKUP.md) — поведение разметки

## Разработка
- [EXTENDING](EXTENDING.md) — как добавить новый движок/экран/локализацию
- [TESTING](TESTING.md) — тестирование
- [PERFORMANCE](PERFORMANCE.md) — производительность и оптимизации
- [TROUBLESHOOTING](TROUBLESHOOTING.md) — решение типичных проблем
- [VERSIONING](VERSIONING.md) — политика версий
- [CHANGELOG](CHANGELOG.md) — история изменений

## Деплоймент
- [DEPLOYMENT](DEPLOYMENT.md) — публикация в Google Play и F-Droid
- [SECURITY](SECURITY.md) — безопасность
- [EN_ENGINE_HOST](EN_ENGINE_HOST.md) — настройка server-host

## Продукт
- [FAQ](FAQ.md) — частые вопросы
- [COMPARISON](COMPARISON.md) — сравнение с конкурентами
- [MODELS](MODELS.md) — какой движок выбрать
- [MARKETING](MARKETING.md) — план продвижения

## Ссылки
- [REFERENCES](REFERENCES.md) — внешние ссылки и ресурсы

## Структура проекта

```
t2v/
├── app/                  Android-приложение
│   ├── src/main/java/com/ltvreader/
│   │   ├── core/         бизнес-логика (text, markup, audio)
│   │   ├── tts/          TTS-движки
│   │   ├── data/         Room + DataStore
│   │   ├── ui/           Compose-экраны
│   │   ├── worker/       пайплайн
│   │   ├── server/       HTTP-клиент к engine-host
│   │   ├── util/         утилиты
│   │   ├── llm/          LLM stub
│   │   └── app/          Application + DI
│   └── src/test/         тесты
├── server-host/          Python-бэкенд
├── docs/                 эта документация
└── tools/                вспомогательные скрипты
```

