package com.example.currencyconverter.data

import com.google.gson.annotations.SerializedName

data class ExchangeRateResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("timestamp") val timestamp: Long = 0L,
    @SerializedName("source") val source: String? = null,
    @SerializedName("quotes") val quotes: Map<String, Double>? = null,
    @SerializedName("error") val error: ApiError? = null
)

data class ApiError(
    @SerializedName("code") val code: Int = 0,
    @SerializedName("type") val type: String? = null,
    @SerializedName("info") val info: String? = null
)
