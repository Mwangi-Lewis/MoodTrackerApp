@file:OptIn(ExperimentalMaterial3Api::class)
package com.example.moodtrackerapp

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun TipsScreen(onBack: () -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text("Tips") }, navigationIcon = { IconButton(onClick = onBack){ Text("←") } }) }) {
        Box(Modifier.fillMaxSize().padding(it), contentAlignment = Alignment.Center) {
            Text("Breathing • Wind-Down • 10-min Walk")
        }
    }
}