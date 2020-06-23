package com.meicm.cas.digitalwellbeing.remote

import okhttp3.Interceptor
import pl.droidsonroids.jspoon.annotation.Selector
import pl.droidsonroids.retrofit2.JspoonConverterFactory
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

const val BASE_URL = "https://play.google.com/store/"

class GooglePlayCategory {
    @Selector("a[itemprop='genre']") var category: String? = null
}

interface GooglePlayService {
    @GET("apps/details")
    suspend fun getAppPage(@Query("id") packageName: String): GooglePlayCategory

    companion object {
        fun create(): GooglePlayService {
            val retrofit = Retrofit.Builder()
                .addConverterFactory(JspoonConverterFactory.create())
                .baseUrl(BASE_URL)
                .build()

            return retrofit.create(GooglePlayService::class.java)
        }
    }
}