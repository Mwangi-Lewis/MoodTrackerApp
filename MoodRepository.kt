package com.example.moodtrackerapp


import com.example.moodtrackerapp.MoodEntry
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

class MoodRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private fun col() =
        db.collection("users").document(auth.currentUser!!.uid).collection("moods")

    suspend fun addMood(entry: MoodEntry) {
        col().add(entry).await()
    }

    suspend fun latestMood(limit: Long = 1): MoodEntry? {
        val snap = col()
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(limit)
            .get().await()
        return snap.documents.firstOrNull()?.toObject(MoodEntry::class.java)
    }

    suspend fun last7(): List<MoodEntry> {
        val snap = col()
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(7)
            .get().await()
        return snap.toObjects(MoodEntry::class.java)
    }
}