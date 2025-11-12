package com.example.moodtrackerapp

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.firebase.Timestamp
import kotlinx.coroutines.launch


@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun HomeScreen(
    moodFromCamera: String? = null,
    confFromCamera: Float? = null,
    onCalendar: () -> Unit,
    onTips: () -> Unit,
    onProfile: () -> Unit,
    onLogMood: () -> Unit
) {
    val repo = remember { MoodRepository() }
    val scope = rememberCoroutineScope()

    var latest by remember { mutableStateOf<MoodEntry?>(null) }
    var weekEntries by remember { mutableStateOf<List<MoodEntry>>(emptyList()) }
    var saving by remember { mutableStateOf(false) }

    var selfieMood by remember { mutableStateOf(moodFromCamera) }
    var selfieConf by remember { mutableStateOf(confFromCamera) }
    var showCamera by remember { mutableStateOf(false) }

    val auth = remember { com.google.firebase.auth.FirebaseAuth.getInstance() }
    val firestore = remember { com.google.firebase.firestore.FirebaseFirestore.getInstance() }
    var username by remember { mutableStateOf<String?>(null) }

    // ───────── Load latest mood, last 7 days & username ─────────
    LaunchedEffect(Unit) {
        latest = repo.latestMood()
        weekEntries = repo.last7()

        val email = auth.currentUser?.email
        if (email.isNullOrBlank()) {
            username = "there"
        } else {
            firestore.collection("users")
                .whereEqualTo("email", email)
                .limit(1)
                .get()
                .addOnSuccessListener { qs ->
                    val u = qs.firstOrNull()?.getString("username")
                    username = if (!u.isNullOrBlank()) u else email.substringBefore('@')
                }
                .addOnFailureListener {
                    username = email.substringBefore('@')
                }
        }
    }

    // canonical mood saver
    fun addMood(mood: String, score: Int) = scope.launch {
        if (saving) return@launch
        saving = true

        val canonical = when (mood.lowercase()) {
            "amazing", "great", "good", "happy", "🤩", "😊" -> "happy"
            "ok", "okay", "fine", "😐"                      -> "neutral"
            "sad", "😢", "😭"                               -> "sad"
            "angry", "mad", "😠", "😡"                       -> "angry"
            "tired", "exhausted", "🥱", "😴"                 -> "tired"
            else                                            -> mood.lowercase()
        }

        repo.addMood(
            MoodEntry(
                mood = canonical,
                score = score.coerceIn(0, 100),
                createdAt = Timestamp.now()
            )
        )

        // refresh data used on home
        latest = repo.latestMood()
        weekEntries = repo.last7()
        saving = false
    }

    // ───────── Derived mini-insights ─────────
    val weeklyLogs = weekEntries
        .map { it.createdAt.toDate().toInstant().toEpochMilli() }
        .distinctBy { dayKey(it) }
        .size

    val weeklyProgress = (weeklyLogs.coerceIn(0, 7) / 7f)
    val avgScore: Int? =
        if (weekEntries.isNotEmpty()) weekEntries.map { it.score }.average().toInt() else null

    val avgMoodLabel = avgScore?.let { scoreToMoodLabel(it) }
    val latestMoodEmoji = latest?.mood?.let { moodEmoji(it) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onLogMood) {
                Icon(Icons.Filled.Add, contentDescription = "Log mood")
            }
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = true,
                    onClick = { },
                    icon = { Text("🏠") },
                    label = { Text("Home") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onCalendar,
                    icon = { Text("📅") },
                    label = { Text("Calendar") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onTips,
                    icon = { Text("💡") },
                    label = { Text("Tips") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onProfile,
                    icon = { Text("👤") },
                    label = { Text("Profile") }
                )
            }
        }
    ) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(bottom = 72.dp)
        ) {

            // ───────── Hero card ─────────
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFFFFF176)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        "Daily reflection",
                        style = MaterialTheme.typography.titleSmall.copy(color = Color(0xFF5C5C5C))
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Hello, ${username ?: "there"}",
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF1D1B20)
                            )
                        )
                        Text(
                            "How do you feel about your\ncurrent emotions?",
                            style = MaterialTheme.typography.headlineSmall.copy(
                                color = Color(0xFF1D1B20),
                                lineHeight = MaterialTheme.typography.headlineSmall.lineHeight
                            )
                        )
                    }
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onLogMood() },
                        color = Color(0xFFEFEFEF)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Your reflection",
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    color = Color(0xFF555555)
                                ),
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                                contentDescription = "Reflect",
                                tint = Color(0xFF555555)
                            )
                        }
                    }
                }
            }

            // ───────── Today at a glance / last mood ─────────
            latest?.let { last ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFFF9C4)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(latestMoodEmoji ?: moodEmoji(last.mood))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Today at a glance", style = MaterialTheme.typography.labelLarge)
                            Text(
                                text = "You last logged feeling ${last.mood}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Text(
                            "View",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.clickable { onCalendar() }
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // ───────── Last detected selfie mood (if any) ─────────
            selfieMood?.let { mood ->
                Row(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Face,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Last detected mood", style = MaterialTheme.typography.labelLarge)
                        Text(
                            mood.replaceFirstChar { it.uppercase() } +
                                    (selfieConf?.let { " (${(it * 100).toInt()}%)" } ?: ""),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            // ───────── Selfie button ─────────
            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                onClick = { showCamera = true },
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Take a selfie to know your mood")
            }

            Spacer(Modifier.height(20.dp))

            // ───────── Quick emoji chips ─────────
            Text(
                "How are you feeling?",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MoodChip("Amazing", "🤩") {
                    addMood("happy", 95)
                    onLogMood()
                }
                MoodChip("Happy", "😊") {
                    addMood("happy", 80)
                    onLogMood()
                }
                MoodChip("Okay", "😐") {
                    addMood("neutral", 60)
                    onLogMood()
                }
                MoodChip("Sad", "😢") {
                    addMood("sad", 30)
                    onLogMood()
                }
                MoodChip("Angry", "😠") {
                    addMood("angry", 20)
                    onLogMood()
                }
            }

            Spacer(Modifier.height(20.dp))

            // ───────── Weekly progress / insights (dynamic) ─────────
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text("Your week so far", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))

                    // progress bar based on how many days logged in last 7
                    LinearProgressIndicator(
                        progress = { weeklyProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                        trackColor = Color(0xFFE0E0E0),
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(Modifier.height(8.dp))
                    Text(
                        "$weeklyLogs of 7 days logged",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    avgMoodLabel?.let { label ->
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Overall mood: $label",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // ───────── Prefer to log it yourself? ─────────
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Prefer to log it yourself?")
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            "happy" to 85,
                            "sad" to 30,
                            "angry" to 25,
                            "tired" to 45,
                            "neutral" to 62
                        ).forEach { (m, s) ->
                            AssistChip(
                                onClick = { addMood(m, s) },
                                label = { Text(m) }
                            )
                        }
                    }
                }
            }
        }
    }

    // ───────── Full-screen camera dialog ─────────
    if (showCamera) {
        Dialog(
            onDismissRequest = { showCamera = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = false
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                InlineSelfieCamera(
                    onResult = { mood, conf ->
                        addMood(mood, (conf * 100).toInt())
                        selfieMood = mood
                        selfieConf = conf
                        showCamera = false
                    },
                    onClose = { showCamera = false }
                )
            }
        }
    }
}

@Composable
private fun MoodChip(
    label: String,
    emoji: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFFFFF9C4),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = 84.dp)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFFF176)),
                contentAlignment = Alignment.Center
            ) {
                Text(emoji)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/* ───────── Helpers ───────── */

private fun dayKey(epochMillis: Long): Long {
    // collapse timestamp to YYYY-MM-DD bucket
    return epochMillis / (24L * 60L * 60L * 1000L)
}

private fun moodEmoji(mood: String): String = when (mood.lowercase()) {
    "happy" -> "😊"
    "sad" -> "😢"
    "angry" -> "😠"
    "tired" -> "😴"
    "neutral" -> "😐"
    else -> "🙂"
}

private fun scoreToMoodLabel(score: Int): String = when {
    score >= 75 -> "mostly positive 😊"
    score >= 50 -> "balanced 😐"
    score >= 25 -> "a bit low 😕"
    else        -> "quite low 💜 take it easy"
}
