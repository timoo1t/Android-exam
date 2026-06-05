package com.example.currencyconverter.model

/**
 * Описание валюты для отображения в Spinner.
 *
 * @param code  ISO-код, например "USD".
 * @param name  человекочитаемое название.
 * @param flag  emoji-флаг (никаких ресурсов-картинок не требуется).
 */
data class Currency(
    val code: String,
    val name: String,
    val flag: String
) {
    override fun toString(): String = "$flag  $code"
}

/**
 * Список валют, которые показываем в спиннерах. Можно расширить — главное,
 * чтобы коды совпадали с тем, что возвращает API (ISO 4217).
 */
object Currencies {
    val ALL: List<Currency> = listOf(
        Currency("USD", "Доллар США",        "🇺🇸"),
        Currency("EUR", "Евро",              "🇪🇺"),
        Currency("RUB", "Российский рубль",  "🇷🇺"),
        Currency("GBP", "Фунт стерлингов",   "🇬🇧"),
        Currency("JPY", "Японская иена",     "🇯🇵"),
        Currency("CNY", "Китайский юань",    "🇨🇳"),
        Currency("CHF", "Швейцарский франк", "🇨🇭"),
        Currency("CAD", "Канадский доллар",  "🇨🇦"),
        Currency("AUD", "Австралийский доллар", "🇦🇺"),
        Currency("KZT", "Казахстанский тенге", "🇰🇿"),
        Currency("BYN", "Белорусский рубль",   "🇧🇾"),
        Currency("UAH", "Украинская гривна",   "🇺🇦"),
        Currency("TRY", "Турецкая лира",       "🇹🇷"),
        Currency("INR", "Индийская рупия",     "🇮🇳"),
        Currency("BRL", "Бразильский реал",    "🇧🇷"),
    )

    fun indexOf(code: String): Int = ALL.indexOfFirst { it.code == code }.let { if (it < 0) 0 else it }
}
