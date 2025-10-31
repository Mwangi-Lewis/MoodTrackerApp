package com.example.moodtrackerapp

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FormatItalic
import androidx.compose.material.icons.outlined.FormatUnderlined
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MoodLogScreen(
    onBack: () -> Unit,
    onSave: (String) -> Unit = {}
) {
    val context = LocalContext.current

    // UI state
    var title by remember { mutableStateOf(TextFieldValue("Title")) }
    var subtitle by remember { mutableStateOf(TextFieldValue("Subtitle")) }
    var note by remember { mutableStateOf(TextFieldValue("")) }

    // Get current date and time when screen loads
    val currentDate = remember {
        SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date())
    }
    val currentTime = remember {
        SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
    }

// Initialize with current values
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

    Scaffold(
        bottomBar = {
            BottomAppBar(
                tonalElevation = 0.dp,
                containerColor = Color.Transparent
            ) {
                // I, U, attach, delete, mic — labels hidden (just like your mock)
                NavigationBarItem(
                    selected = false,
                    onClick = { /* italic behavior (optional) */ },
                    icon = { Icon(Icons.Outlined.FormatItalic, contentDescription = "Italic") },
                    label = { Text("I") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { /* underline behavior (optional) */ },
                    icon = { Icon(Icons.Outlined.FormatUnderlined, contentDescription = "Underline") },
                    label = { Text("U") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { /* attach behavior (optional) */ },
                    icon = { Icon(Icons.Outlined.AttachFile, contentDescription = "Attach") },
                    label = { Text("") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { /* delete behavior (optional) */ },
                    icon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete") },
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
                            tint = if (isListening) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
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
                .padding(horizontal = 16.dp)
        ) {

            // Top bar row (back, placeholders, title/subtitle, quick icons)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Outlined.ArrowBack, contentDescription = "Back")
                }

                // Two “square” placeholders
                PlaceholderIcon()
                PlaceholderIcon()

                // Title & subtitle stacked
                Column(modifier = Modifier.weight(1f)) {
                    BasicTextFieldM3(
                        value = title,
                        onValueChange = { title = it },
                        textStyle = MaterialTheme.typography.titleMedium
                    )
                    BasicTextFieldM3(
                        value = subtitle,
                        onValueChange = { subtitle = it },
                        textStyle = MaterialTheme.typography.labelMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }

                // Two small round icons on the right (as in the mock)
                SmallHollowCircle()
                SmallHollowCircle()

                // Star icon at far right
                Icon(
                    imageVector = Icons.Outlined.StarBorder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(Modifier.height(16.dp))

            // Date & Time chips
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AssistChip(
                    onClick = { /* show date picker if desired */ },
                    label = { Text(date) }
                )
                AssistChip(
                    onClick = { /* show time picker if desired */ },
                    label = { Text(time) }
                )
            }

            // Divider line
            Spacer(Modifier.height(16.dp))
            Divider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

            // Big note area with “Log your Mood” placeholder
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(top = 24.dp)
            ) {
                if (note.text.isBlank()) {
                    Text(
                        "Log your Mood",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 18.sp
                    )
                }
                BasicTextFieldM3(
                    value = note,
                    onValueChange = { note = it },
                    textStyle = TextStyle(fontSize = 18.sp),
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Bottom-right “Need some help?” text and small star
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
        }
    }
}

/* ----------------------------- helpers ------------------------------ */

@Composable
private fun PlaceholderIcon() {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(6.dp))
            .border(
                BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                RoundedCornerShape(6.dp)
            )
    )
}

@Composable
private fun SmallHollowCircle() {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .border(
                BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                CircleShape
            )
    )
}

/**
 * Minimal “no decoration” text field using Material 3 colors/typography.
 */
@Composable
private fun BasicTextFieldM3(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    textStyle: TextStyle,
    modifier: Modifier = Modifier
) {
    TextField(
        modifier = modifier.fillMaxWidth(),
        value = value,
        onValueChange = onValueChange,
        textStyle = textStyle,
        singleLine = false,
        colors = TextFieldDefaults.colors(
            unfocusedContainerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent
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
            list?.firstOrNull()?.takeIf { it.isNotBlank() }?.let(onResult)
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    })

    recognizer.startListening(intent)
}