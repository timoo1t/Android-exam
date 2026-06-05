package com.example.currencyconverter.data

import android.content.Context
import android.content.SharedPreferences
import com.example.currencyconverter.BuildConfig
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Синглтон-репозиторий. Отвечает за:
 *  - сетевой запрос курсов через Retrofit,
 *  - парсинг JSON (через Gson + ApiService),
 *  - кэширование в SharedPreferences на 30 минут,
 *  - математический расчёт конвертации между двумя валютами.
 *
 * Все публичные API — потокобезопасны. Сетевые вызовы — suspend.
 */
object CurrencyRepository {

    private const val PREFS_NAME = "currency_cache"
    private const val KEY_RATES_JSON = "rates_json"
    private const val KEY_BASE = "rates_base"
    private const val KEY_TIMESTAMP = "rates_saved_at"
    private const val CACHE_TTL_MS = 30L * 60L * 1000L  // 30 минут

    private const val BASE_URL = "https://api.exchangerate.host/"
    private const val DEFAULT_BASE = "USD"

    private lateinit var prefs: SharedPreferences
    private val gson = Gson()

    private val api: ApiService by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
                    else HttpLoggingInterceptor.Level.NONE
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(ApiService::class.java)
    }

    /** Должен быть вызван один раз в Application/Activity до первого обращения к курсам. */
    fun init(context: Context) {
        if (!::prefs.isInitialized) {
            prefs = context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    // -------------------- Public API --------------------

    /**
     * Делает сетевой запрос и обновляет кэш. Если сеть упала или API вернул ошибку —
     * возвращает [RatesResult.Error] и при наличии прикладывает последний кэш.
     */
    suspend fun refreshRates(): RatesResult = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.EXCHANGERATE_API_KEY
        if (apiKey.isBlank()) {
            return@withContext RatesResult.Error(
                message = "API-ключ не задан. Добавьте EXCHANGERATE_API_KEY в local.properties.",
                cached = readCache()
            )
        }
        try {
            val response = api.getLiveRates(accessKey = apiKey, source = DEFAULT_BASE)
            if (!response.success || response.quotes.isNullOrEmpty()) {
                val msg = response.error?.info ?: "Сервер вернул пустой ответ"
                return@withContext RatesResult.Error(message = msg, cached = readCache())
            }
            val rates = parseQuotes(response.source ?: DEFAULT_BASE, response.quotes)
            val snapshot = RatesSnapshot(
                base = response.source ?: DEFAULT_BASE,
                rates = rates,
                savedAtMs = System.currentTimeMillis()
            )
            writeCache(snapshot)
            RatesResult.Success(snapshot, fromCache = false)
        } catch (e: IOException) {
            RatesResult.Error(
                message = "Нет соединения с сервером",
                cached = readCache()
            )
        } catch (e: Exception) {
            RatesResult.Error(
                message = e.localizedMessage ?: "Неизвестная ошибка",
                cached = readCache()
            )
        }
    }

    /**
     * Возвращает данные либо из свежего кэша (моложе 30 мин), либо делает запрос.
     * Используется при старте приложения.
     */
    suspend fun getRates(): RatesResult {
        val cached = readCache()
        if (cached != null && !cached.isExpired()) {
            return RatesResult.Success(cached, fromCache = true)
        }
        return refreshRates()
    }

    /** Чисто синхронное чтение последнего кэша без обращения к сети. */
    fun peekCache(): RatesSnapshot? = readCache()

    /**
     * Конвертация суммы из одной валюты в другую через общую базу [snapshot.base].
     *
     * rate[X] — сколько единиц X за 1 единицу базы.
     * Тогда: amount(from) * rate[to] / rate[from] = amount(to).
     */
    fun convert(amount: Double, from: String, to: String, snapshot: RatesSnapshot): Double {
        if (amount == 0.0) return 0.0
        if (from == to) return amount
        val rFrom = snapshot.rateOf(from) ?: return Double.NaN
        val rTo = snapshot.rateOf(to) ?: return Double.NaN
        if (rFrom == 0.0) return Double.NaN
        return amount * rTo / rFrom
    }

    // -------------------- Internals --------------------

    /**
     * Превращает {"USDEUR":0.92,"USDRUB":90.5} в {"EUR":0.92,"RUB":90.5,"USD":1.0}.
     */
    private fun parseQuotes(base: String, quotes: Map<String, Double>): Map<String, Double> {
        val out = HashMap<String, Double>(quotes.size + 1)
        out[base] = 1.0
        for ((key, value) in quotes) {
            val code = when {
                key.length == 6 && key.startsWith(base) -> key.substring(3)
                key.length == 3 -> key
                else -> continue
            }
            out[code] = value
        }
        return out
    }

    private fun writeCache(snapshot: RatesSnapshot) {
        val json = gson.toJson(snapshot.rates)
        prefs.edit()
            .putString(KEY_RATES_JSON, json)
            .putString(KEY_BASE, snapshot.base)
            .putLong(KEY_TIMESTAMP, snapshot.savedAtMs)
            .apply()
    }

    private fun readCache(): RatesSnapshot? {
        if (!::prefs.isInitialized) return null
        val json = prefs.getString(KEY_RATES_JSON, null) ?: return null
        val base = prefs.getString(KEY_BASE, DEFAULT_BASE) ?: DEFAULT_BASE
        val ts = prefs.getLong(KEY_TIMESTAMP, 0L)
        if (ts == 0L) return null
        val type = object : TypeToken<Map<String, Double>>() {}.type
        val rates: Map<String, Double> = try {
            gson.fromJson(json, type) ?: return null
        } catch (_: Exception) {
            return null
        }
        return RatesSnapshot(base = base, rates = rates, savedAtMs = ts)
    }
}

/** Снимок курсов с временной меткой. */
data class RatesSnapshot(
    val base: String,
    val rates: Map<String, Double>,
    val savedAtMs: Long
) {
    fun rateOf(code: String): Double? = rates[code]
    fun ageMs(now: Long = System.currentTimeMillis()): Long = now - savedAtMs
    fun isExpired(now: Long = System.currentTimeMillis()): Boolean =
        ageMs(now) > 30L * 60L * 1000L
}

sealed class RatesResult {
    /** Успешное получение курсов. [fromCache] = true, если данные взяты из локального кэша. */
    data class Success(val snapshot: RatesSnapshot, val fromCache: Boolean) : RatesResult()

    /** Ошибка сети/API. [cached] — последнее доступное состояние, может быть null. */
    data class Error(val message: String, val cached: RatesSnapshot?) : RatesResult()
}
