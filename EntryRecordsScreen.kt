@file:OptIn(ExperimentalMaterial3Api::class)
package com.example.moodtrackerapp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun EntryRecordsScreen(onBack: () -> Unit) {
    val repo = remember { MoodRepository() }
    var items by remember { mutableStateOf<List<MoodEntry>>(emptyList()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { scope.launch { items = repo.last7() } }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Entry Records") },
            navigationIcon = { IconButton(onClick = onBack) { Text("←") } }
        )
    }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(16.dp)) {

            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("Last 7 days", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(items) { entry ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column {
                                    Text(entry.createdAt.toDate().fmt("EEE, MMM d"))
                                    Text(entry.mood, style = MaterialTheme.typography.bodySmall)
                                }
                                Text(emoji(entry.mood), style = MaterialTheme.typography.titleLarge)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun Date.fmt(p: String) = SimpleDateFormat(p, Locale.getDefault()).format(this)
private fun emoji(mood:String) = when(mood) {
    "happy"->"😊"; "sad"->"😢"; "angry"->"😠"; "tired"->"🥱"; else->"😐"
}