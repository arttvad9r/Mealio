package com.arttvad9r.mealio.data.remote

import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Builds Retrofit clients. The bearer token is injected by an interceptor so it
 * never appears in call sites, logs or exception messages.
 */
class MealieApiFactory(
    private val okHttpClient: OkHttpClient,
    private val tokenProvider: () -> String?,
) {

    val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    fun create(baseUrl: String): MealieApi {
        val client = okHttpClient.newBuilder()
            .addInterceptor(authInterceptor())
            .build()
        return build(baseUrl, client)
    }

    /**
     * Builds a one-off API instance with an explicit token, used to verify a
     * token BEFORE it is persisted to secure storage.
     */
    fun createWithToken(baseUrl: String, token: String): MealieApi {
        val client = okHttpClient.newBuilder()
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("Authorization", "Bearer $token")
                        .build(),
                )
            }
            .build()
        return build(baseUrl, client)
    }

    private fun build(baseUrl: String, client: OkHttpClient): MealieApi {
        val normalized = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        return Retrofit.Builder()
            .baseUrl(normalized)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(MealieApi::class.java)
    }

    private fun authInterceptor(): Interceptor = Interceptor { chain ->
        val token = tokenProvider()
        val request = if (token.isNullOrBlank()) {
            chain.request()
        } else {
            chain.request().newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        }
        chain.proceed(request)
    }

    companion object {
        fun defaultOkHttp(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
