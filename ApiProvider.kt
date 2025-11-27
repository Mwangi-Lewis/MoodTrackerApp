// ApiProvider.kt
package com.example.moodtrackerapp

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ApiProvider {

    // SAME IP as your FER backend (change if your IP changes)
    private const val BASE_URL = "YOUR LAPTOP'S OR COMPUTER'S URL"

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    // You already have FerApi defined in InlineSelfieCamera file;
    // if it's in another file, just make sure it's in same package.
    val ferApi: FerApi by lazy { retrofit.create(FerApi::class.java) }

    // Chat API for the chatbot
    val chatApi: ChatApi by lazy { retrofit.create(ChatApi::class.java) }
}
