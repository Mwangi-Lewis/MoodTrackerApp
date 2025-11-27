package com.example.moodtrackerapp

import retrofit2.http.Body
import retrofit2.http.POST

data class ChatRequestDto(
    val mood: String,
    val message: String? = null
)

data class ChatResponseDto(
    val reply: String
)

interface ChatApi {
    @POST("chat")
    suspend fun chat(@Body body: ChatRequestDto): ChatResponseDto
}
