package com.example.moodtrackerapp

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

data class MoodEntry(
    @DocumentId val id: String? = null,
    val mood: String = "neutral",        // "happy", "sad", "angry", etc.
    val score: Int = 62,                 // 0..100
    val note: String = "",
    val createdAt: Timestamp = Timestamp.now()
)