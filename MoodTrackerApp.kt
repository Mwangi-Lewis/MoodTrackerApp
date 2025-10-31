package com.example.moodtrackerapp

import android.app.Application
import com.google.firebase.FirebaseApp

class MoodTrackerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
    }
}