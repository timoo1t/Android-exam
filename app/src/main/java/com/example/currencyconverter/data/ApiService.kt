package com.example.currencyconverter.data

import retrofit2.http.GET
import retrofit2.http.Query

interface ApiService {
    /**
     * exchangerate.host /live — возвращает курсы относительно [source] валюты.
     * Документация: https://exchangerate.host/documentation
     */
    @GET("live")
    suspend fun getLiveRates(
        @Query("access_key") accessKey: String,
        @Query("source") source: String = "USD",
        @Query("currencies") currencies: String? = null
    ): ExchangeRateResponse
}
