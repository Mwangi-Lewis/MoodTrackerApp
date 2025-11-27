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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.CircularProgressIndicator
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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date
@Composable
fun MoodLogScreen(
    onBack: () -> Unit,
    // Called after a successful save so the parent (NavHost) can
    // navigate back to Home and refresh the mood log section.
    onSave: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val repo = remember { MoodRepository() }
    val scope = rememberCoroutineScope()
    val auth = remember { FirebaseAuth.getInstance() }
    val firestore = remember { FirebaseFirestore.getInstance() }

    // ---- state ----
    var title by remember { mutableStateOf(TextFieldValue("")) }
    var subtitle by remember { mutableStateOf(TextFieldValue("")) }
    var note by remember { mutableStateOf(TextFieldValue("")) }

    // Derived from speech-to-text / typed keywords
    var detectedMood by remember { mutableStateOf<String?>(null) }
    var supportMessage by remember { mutableStateOf<String?>(null) }

    // Debounced analysis state for typed input
    var isAnalyzing by remember { mutableStateOf(false) }
    var lastAnalyzedText by remember { mutableStateOf("") }

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
                onResult = { text ->
                    // Keep full transcript as the note content for logging
                    note = TextFieldValue(text)

                    // Analyze spoken text for emotional keywords
                    val (mood, message) = analyzeSpeechForMoodAndSupport(text)
                    detectedMood = mood
                    supportMessage = message

                    // Optionally, surface the mood in the title for quick glance
                    if (!mood.isNullOrBlank() && title.text.isBlank()) {
                        title = TextFieldValue("Feeling ${mood.replaceFirstChar { it.uppercase() }}")
                    }
                },
                onEnd = { isListening = false }
            )
        } else {
            isListening = false
        }
    }

    // Debounced analysis of whatever is in the note field (typed or dictated).
    LaunchedEffect(note.text) {
        val current = note.text.trim()

        // If user clears the text, clear any previous guidance.
        if (current.isEmpty()) {
            detectedMood = null
            supportMessage = null
            isAnalyzing = false
            lastAnalyzedText = ""
            return@LaunchedEffect
        }

        isAnalyzing = true
        val startSnapshot = note.text
        delay(1500L) // wait to see if user keeps typing

        // Only continue if the text has not changed during the debounce window
        if (startSnapshot == note.text && current != lastAnalyzedText) {
            val (mood, message) = analyzeSpeechForMoodAndSupport(current)
            detectedMood = mood
            supportMessage = message
            lastAnalyzedText = current
        }
        isAnalyzing = false
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
                                    // Use a single timestamp for both the mood entry and the saved log
                                    val ts = Timestamp.now()

                                    // Store as a MoodEntry. If your MoodEntry has more fields,
                                    // add them here. Title becomes the "mood label", note is
                                    // what you actually typed.
                                    val entry = MoodEntry(
                                        mood = if (title.text.isNotBlank())
                                            title.text
                                        else
                                            "Mood log",
                                        score = 0,
                                        note = note.text,
                                        createdAt = ts
                                    )
                                    repo.addMood(entry)

                                    // Save a summary card so it shows in HomeScreen "Mood log" list
                                    val uid = auth.currentUser?.uid
                                    if (uid != null && note.text.isNotBlank()) {
                                        val moodLabel = (detectedMood ?: title.text).ifBlank { "reflection" }
                                        val summary = if (note.text.length > 60)
                                            note.text.take(60) + "..."
                                        else
                                            note.text

                                        val detail = buildString {
                                            append("Mood: ")
                                            append(moodLabel.replaceFirstChar { it.uppercase() })
                                            append("\n\nWhat you wrote:\n")
                                            append(note.text)
                                            if (!supportMessage.isNullOrBlank()) {
                                                append("\n\nSupport message:\n")
                                                append(supportMessage)
                                            }
                                        }

                                        val docRef = firestore.collection("chatLogs").document()
                                        val data = mapOf(
                                            "userId" to uid,
                                            "mood" to moodLabel.lowercase(),
                                            "createdAt" to ts,
                                            "summary" to summary,
                                            "detail" to detail
                                        )
                                        // Wait for Firestore write so that HomeScreen, which
                                        // reloads chatLogs on navigation, can immediately see
                                        // this new document.
                                        docRef.set(data).await()
                                    }

                                    // Now tell the parent that we saved successfully. The
                                    // parent (NavHost) will typically navigate back to Home
                                    // so the new mood log card appears immediately.
                                    onSave(note.text)
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
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
                .imePadding(),
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
                    .fillMaxWidth()
                    .heightIn(min = 160.dp)
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp)
                )
            }

            // Small "processing" indicator when analyzing typed / spoken text
            if (isAnalyzing) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Processing what you wrote…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ---- Optional helper bottom-right / rule-based support ----
            if (!supportMessage.isNullOrBlank()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = detectedMood?.let { "Based on what you said, you may be feeling ${it.lowercase()}." }
                            ?: "Here's a little support based on what you shared:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = supportMessage!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (note.text.isNotBlank()) {
                                val (mood, message) = analyzeSpeechForMoodAndSupport(note.text)
                                detectedMood = mood
                                supportMessage = message
                            }
                        },
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

// Simple rule-based keyword detector + support generator for speech-to-text input.
// This runs entirely on-device and does not require network, to keep things
// fast and inclusive for students who prefer (or need) to use their voice.
private fun analyzeSpeechForMoodAndSupport(text: String): Pair<String?, String?> {
    val normalized = text.lowercase()

    // You can expand this list with more university/life-related words.
    val keywordBuckets: Map<String, List<String>> = mapOf(
        "sad" to listOf("sad", "down", "lonely", "depressed", "cry", "crying", "unhappy", "empty"),
        "stressed" to listOf("stressed", "stress", "overwhelmed", "pressure", "burnt out", "burnout", "too much work"),
        "anxious" to listOf("anxious", "anxiety", "worried", "worry", "nervous", "scared"),
        "tired" to listOf("tired", "exhausted", "sleepy", "drained", "fatigued", "no energy"),
        "angry" to listOf("angry", "mad", "frustrated", "annoyed", "irritated"),
        "happy" to listOf("happy", "good", "great", "excited", "grateful", "proud", "motivated"),
        "lonely" to listOf("lonely", "alone", "isolated"),
        "unmotivated" to listOf("unmotivated", "can’t focus", "cant focus", "don’t feel like", "dont feel like")
    )

    // Count how many keywords from each bucket appear in the text
    val scores = mutableMapOf<String, Int>()
    for ((mood, words) in keywordBuckets) {
        var count = 0
        for (w in words) {
            if (normalized.contains(w)) count++
        }
        if (count > 0) scores[mood] = count
    }

    if (scores.isEmpty()) {
        // No clear emotional keywords found – keep the log but don't force a label.
        return null to null
    }

    val (bestMood, _) = scores.maxBy { it.value }

    val support = when (bestMood) {
        "sad", "lonely" ->
            "It sounds like things feel heavy right now. You deserve kindness and rest — even small steps like talking to a friend, taking a short walk, or just breathing for a moment can help. You’re not alone in feeling this way."

        "stressed" ->
            "You’re carrying a lot. Try breaking what’s on your mind into one or two small next steps. It’s okay to pause, say no to extra pressure, and look after your wellbeing first."

        "anxious" ->
            "Feeling anxious doesn’t mean you’re failing — it means your mind is trying to protect you. Notice one thing you can control right now, and remember you don’t have to solve everything at once."

        "tired" ->
            "Your body and mind sound tired. Rest is not a reward, it’s a basic need. Even a short break, a glass of water, or logging off for a bit is a valid step toward taking care of yourself."

        "angry" ->
            "It makes sense to feel angry when things feel unfair or out of control. Try to give yourself a safe outlet — journaling, movement, or deep breaths — before you decide what to do next."

        "unmotivated" ->
            "Motivation naturally goes up and down. Instead of waiting to ‘feel ready’, try picking one very small, doable action. Finishing that can gently restart your momentum."

        "happy" ->
            "It’s great that you’re feeling some positive energy. Noticing what went well today — even small wins — can help you come back to this feeling on harder days."

        else ->
            "Thank you for putting your feelings into words. Whatever you’re experiencing is valid, and taking a moment to reflect like this is already a strong step toward caring for yourself."
    }

    return bestMood to support
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
