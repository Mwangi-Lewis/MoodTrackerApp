@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.moodtrackerapp

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FormatItalic
import androidx.compose.material.icons.outlined.FormatUnderlined
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AssistChip
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.Timestamp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date
@Composable
fun MoodLogScreen(
    onBack: () -> Unit,
    // still expose callback if parent wants it, but we also save internally
    onSave: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val repo = remember { MoodRepository() }
    val scope = rememberCoroutineScope()

    // ---- state ----
    var title by remember { mutableStateOf(TextFieldValue("")) }
    var subtitle by remember { mutableStateOf(TextFieldValue("")) }
    var note by remember { mutableStateOf(TextFieldValue("")) }

    // current date/time at open
    val currentDate = remember {
        SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date())
    }
    val currentTime = remember {
        SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
    }
    var date by remember { mutableStateOf(currentDate) }
    var time by remember { mutableStateOf(currentTime) }

    // mic
    var isListening by remember { mutableStateOf(false) }
    val audioPerm = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startListening(
                context = context,
                onStart = { isListening = true },
                onResult = { text -> note = TextFieldValue(text) },
                onEnd = { isListening = false }
            )
        } else {
            isListening = false
        }
    }

    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = if (title.text.isBlank()) "Title" else title.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Outlined.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            if (saving) return@TextButton
                            saving = true
                            error = null

                            scope.launch {
                                try {
                                    // Store as a MoodEntry. If your MoodEntry has more fields,
                                    // add them here. Title becomes the "mood label", note is
                                    // what you actually typed.
                                    val entry = MoodEntry(
                                        mood = if (title.text.isNotBlank())
                                            title.text
                                        else
                                            "Mood log",
                                        score = 0,
                                        createdAt = Timestamp.now()
                                    )
                                    repo.addMood(entry)

                                    // Also forward raw text up if parent cares
                                    onSave(note.text)

                                    // Go back after successful save
                                    onBack()
                                } catch (e: Exception) {
                                    error = e.message ?: "Failed to save mood."
                                } finally {
                                    saving = false
                                }
                            }
                        }
                    ) {
                        Text(if (saving) "Saving..." else "Save")
                    }
                }
            )
        },
        bottomBar = {
            BottomAppBar(
                tonalElevation = 0.dp,
                containerColor = Color.Transparent
            ) {
                NavigationBarItem(
                    selected = false,
                    onClick = { /* TODO italic styling */ },
                    icon = { Icon(Icons.Outlined.FormatItalic, contentDescription = "Italic") },
                    label = { Text("I") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { /* TODO underline styling */ },
                    icon = { Icon(Icons.Outlined.FormatUnderlined, contentDescription = "Underline") },
                    label = { Text("U") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { /* TODO attach file */ },
                    icon = { Icon(Icons.Outlined.AttachFile, contentDescription = "Attach") },
                    label = { Text("") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { note = TextFieldValue("") },
                    icon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = "Clear") },
                    label = { Text("") }
                )

                Spacer(Modifier.weight(1f))

                NavigationBarItem(
                    selected = isListening,
                    onClick = {
                        if (!isListening) {
                            audioPerm.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Outlined.Mic,
                            contentDescription = "Voice to text",
                            tint = if (isListening)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurface
                        )
                    },
                    label = { Text("") }
                )
            }
        }
    ) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.Top
        ) {
            Spacer(Modifier.height(16.dp))

            // ---- Title field (drives top bar title) ----
            ClearTextField(
                value = title,
                onValueChange = { title = it },
                placeholder = "Title",
                textStyle = MaterialTheme.typography.titleMedium,
                singleLine = true
            )

            Spacer(Modifier.height(8.dp))

            // ---- Subtitle ----
            ClearTextField(
                value = subtitle,
                onValueChange = { subtitle = it },
                placeholder = "Subtitle",
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                singleLine = true
            )

            Spacer(Modifier.height(16.dp))

            // ---- Date & Time chips ----
            // ---- Date & Time chips ----
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AssistChip(
                    onClick = { /* TODO: hook up date picker later */ },
                    label = { Text(date) }
                )

                AssistChip(
                    onClick = { /* TODO: hook up time picker later */ },
                    label = { Text(time) }
                )
            }

            Spacer(Modifier.height(16.dp))
            Divider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
            )

            // ---- Note body ----
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(top = 20.dp)
            ) {
                if (note.text.isBlank()) {
                    Text(
                        "Log your Mood",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 18.sp
                    )
                }
                ClearTextField(
                    value = note,
                    onValueChange = { note = it },
                    placeholder = "",
                    textStyle = TextStyle(fontSize = 18.sp),
                    singleLine = false,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // ---- Optional helper bottom-right ----
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.StarBorder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text("Need some", style = MaterialTheme.typography.bodySmall)
                    Text("help?", style = MaterialTheme.typography.bodySmall)
                }
            }

            Spacer(Modifier.height(8.dp))

            if (error != null) {
                Text(
                    text = error!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

/* ----------------------------- helpers ------------------------------ */

@Composable
private fun ClearTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    placeholder: String,
    textStyle: TextStyle,
    singleLine: Boolean,
    modifier: Modifier = Modifier
) {
    TextField(
        modifier = modifier.fillMaxWidth(),
        value = value,
        onValueChange = onValueChange,
        textStyle = textStyle,
        singleLine = singleLine,
        placeholder = {
            if (placeholder.isNotEmpty()) Text(placeholder)
        },
        colors = TextFieldDefaults.colors(
            unfocusedContainerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            cursorColor = MaterialTheme.colorScheme.primary
        )
    )
}

/**
 * Starts Android SpeechRecognizer and streams results back via callbacks.
 * NOTE: Call this only after RECORD_AUDIO permission is granted.
 */
private fun startListening(
    context: Context,
    onStart: () -> Unit,
    onResult: (String) -> Unit,
    onEnd: () -> Unit
) {
    val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        )
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
    }

    recognizer.setRecognitionListener(object : android.speech.RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) { onStart() }
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() { onEnd() }
        override fun onError(error: Int) { onEnd(); recognizer.destroy() }

        override fun onResults(results: Bundle) {
            val list = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            onResult(list?.firstOrNull().orEmpty())
            onEnd()
            recognizer.destroy()
        }

        override fun onPartialResults(partialResults: Bundle) {
            val list = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            list?.firstOrNull()
                ?.takeIf { it.isNotBlank() }
                ?.let(onResult)
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    })

    recognizer.startListening(intent)
}
