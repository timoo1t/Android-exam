# Currency Converter

Учебное Android-приложение для конвертации валют. Курсы загружаются из
[exchangerate.host](https://exchangerate.host) через Retrofit 2 и кэшируются в
SharedPreferences на 30 минут.

## Что сделано

- Два поля ввода суммы (исходная / целевая) и два `Spinner` со списком валют
  (флаг-emoji + ISO-код + название).
- Любое поле можно править — второе пересчитывается автоматически.
- Поля принимают только цифры и один десятичный разделитель (точка или запятая):
  буквы блокируются `InputFilter`-ом (`DecimalInputFilter`).
- Сетевой запрос — Retrofit 2 + Gson + OkHttp logging.
- Бизнес-логика (запрос, парсинг JSON, кэш, конвертация) вынесена в
  синглтон-репозиторий `CurrencyRepository` (`object` в Kotlin).
- При старте и по кнопке «Обновить курсы» — запрос к API, результат сохраняется
  в `SharedPreferences` с меткой времени.
- Если кэш свежее 30 мин — он используется без сетевого запроса.
- Если интернета нет / API упал — берётся последний кэш и в UI появляется
  пометка **«Автономный режим • данные от HH:mm»**.
- Ошибки сети показываются через `Snackbar` с понятным сообщением.

## Структура

```
app/src/main/java/com/example/currencyconverter/
├── MainActivity.kt
├── data/
│   ├── ApiService.kt              # Retrofit интерфейс
│   ├── ExchangeRateResponse.kt    # DTO для JSON
│   └── CurrencyRepository.kt      # singleton: сеть + кэш + математика
├── model/
│   └── Currency.kt                # список валют с emoji-флагами
├── ui/
│   └── CurrencyAdapter.kt         # адаптер Spinner
└── util/
    └── DecimalInputFilter.kt      # фильтр ввода
```

## Как запустить

1. Открыть папку в Android Studio (Hedgehog/Iguana или новее).
2. Получить бесплатный API-ключ на <https://exchangerate.host>.
3. В корне проекта в файле `local.properties` указать:
   ```
   EXCHANGERATE_API_KEY=ВАШ_КЛЮЧ
   ```
4. Sync Gradle → Run app.

Без ключа приложение не упадёт: оно покажет Snackbar с просьбой добавить ключ
и (если кэш есть) последние сохранённые курсы.
