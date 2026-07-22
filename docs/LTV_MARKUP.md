# LTV-разметка

Документ описывает поведение разметки в **LTV Reader**, которое полностью
совместимо с десктопной версией [LocalText2Voice](https://github.com/estebanstifli/LocalText2Voice).
См. оригинальный `docs/LTV_MARKUP.md` для углублённых примеров.

## Синтаксис

Команды записываются в `{{ ... }}` внутри текста. Можно использовать
кавычки для строк с пробелами.

```
{{chapter "Lesson 1"}}
{{voice "Serena - Spanish"}}
Bienvenido a esta lección.

{{pause 900ms}}
{{voice "Sohee - English"}}
Now listen to the same idea in English.

{{speed 0.92}}
{{volume 80%}}
This part is slower and softer.
```

## Команды

| Команда | Аргументы | Поведение |
|---|---|---|
| `{{chapter "..."}}` | строка | Установить текущую главу/раздел |
| `{{voice "..."}}` | строка | Сменить голос (поиск по началу имени) |
| `{{lang es}}` | BCP-47 | Сменить язык для движка |
| `{{pause 700ms}}` | число + ms\|s | Вставить тишину в аудио |
| `{{pause 0.7s}}` | то же | В секундах |
| `{{pause.long}}` | — | 1500 мс |
| `{{pause.short}}` | — | 300 мс |
| `{{pause random 500 1200}}` | min, max | Случайная пауза в диапазоне |
| `{{speed 0.92}}` | 0.5..2.0 | Множитель скорости |
| `{{volume 80%}}` | процент или множитель | Громкость (1.0 = 100%) |
| `{{pitch 1.1}}` | множитель | Pitch |
| `{{emotion happy}}` | строка | Эмоциональная окраска |
| `{{sfx "name" 200ms}}` | имя, смещение | Вставить звуковой эффект |
| `{{music "calm" -2dB}}` | имя, громкость | Фоновая музыка |
| `{{cmd key=value}}` | пары | Кастомные параметры |

## Реализация

- Парсер: `app/src/main/java/com/ltvreader/core/markup/LTVMarkupParser.kt`
- Состояние: `MarkupState` (прикрепляется к каждому `TextChunk`)
- Подсветка в редакторе: `app/src/main/java/com/ltvreader/ui/markup/MarkupHighlighter.kt`

