package com.example.moodtrackerapp

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// ← single, canonical enum used everywhere
enum class AppThemeMode { Light, Dark, Custom }
@Composable
fun MoodTrackerTheme(
    mode: ThemeMode,
    content: @Composable () -> Unit
) {
    val scheme = when (mode) {
        ThemeMode.Light  -> lightColorScheme()
        ThemeMode.Dark   -> darkColorScheme()
        ThemeMode.Custom -> lightColorScheme() // or a custom scheme if you want
    }
    MaterialTheme(colorScheme = scheme, content = content)
}