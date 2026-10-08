package com.arttvad9r.mealio

import android.app.Application
import coil.ImageLoader
import coil.Coil
import com.arttvad9r.mealio.data.AppContainer
import okhttp3.OkHttpClient

class MealioApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // Mealie recipe images live behind the same Bearer auth as the API, so
        // Coil must attach the token header. A bare image URL renders nothing.
        Coil.setImageLoader(
            ImageLoader.Builder(this)
                .okHttpClient {
                    OkHttpClient.Builder()
                        .addInterceptor { chain ->
                            val token = container.tokenStorage.getToken()
                            val request = if (token.isNullOrBlank()) {
                                chain.request()
                            } else {
                                chain.request().newBuilder()
                                    .header("Authorization", "Bearer $token")
                                    .build()
                            }
                            chain.proceed(request)
                        }
                        .build()
                }
                .crossfade(true)
                .build(),
        )
    }
}
