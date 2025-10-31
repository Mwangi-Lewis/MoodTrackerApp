package com.example.moodtrackerapp

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
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
import kotlinx.coroutines.launch
import com.google.firebase.Timestamp

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
    var saving by remember { mutableStateOf(false) }

    var selfieMood by remember { mutableStateOf(moodFromCamera) }
    var selfieConf by remember { mutableStateOf(confFromCamera) }
    var showCamera by remember { mutableStateOf(false) }

    val auth = remember { com.google.firebase.auth.FirebaseAuth.getInstance() }
    val firestore = remember { com.google.firebase.firestore.FirebaseFirestore.getInstance() }
    var username by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { latest = repo.latestMood()
        val email = auth.currentUser?.email
        if (email.isNullOrBlank()) {
            username = "there"
            return@LaunchedEffect
        }
        // Look up user doc by email and read "username"
        firestore.collection("users")
            .whereEqualTo("email", email)
            .limit(1)
            .get()
            .addOnSuccessListener { qs ->
                val u = qs.firstOrNull()?.getString("username")
                username = when {
                    !u.isNullOrBlank() -> u
                    else -> email.substringBefore('@')   // fallback
                }
            }
            .addOnFailureListener {
                username = email.substringBefore('@')     // fallback on error
            }
    }

    fun addMood(m: String, s: Int) = scope.launch {
        saving = true
        repo.addMood(MoodEntry(mood = m, score = s, createdAt = Timestamp.now()))
        latest = repo.latestMood()
        saving = false
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onLogMood) {
                Icon(Icons.Filled.Add, contentDescription = "Log mood")
            }
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = true, onClick = { },
                    icon = { Text("🏠") }, label = { Text("Home") }
                )
                NavigationBarItem(
                    selected = false, onClick = onCalendar,
                    icon = { Text("📅") }, label = { Text("Calendar") }
                )
                NavigationBarItem(
                    selected = false, onClick = onTips,
                    icon = { Text("💡") }, label = { Text("Tips") }
                )
                NavigationBarItem(
                    selected = false, onClick = onProfile,
                    icon = { Text("👤") }, label = { Text("Profile") }
                )
            }
        }
    ) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(bottom = 72.dp) // keep room above bottom nav
        ) {

            // ───────────────── Hero / Daily reflection ─────────────────
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFFFFF176) // warm yellow
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

                    // “Your reflection  →”
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

            // Optional: show last detected mood (compact)
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
                            "${mood.replaceFirstChar { it.uppercase() }} " +
                                    (selfieConf?.let { "(${(it * 100).toInt()}%)" } ?: ""),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            // Keep: Take a selfie to know your mood
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

            // ───────────────── “How are you feeling?” chips ─────────────────
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
                MoodChip("Amazing", "🤩") { onLogMood() }
                MoodChip("Happy", "😊") { onLogMood() }
                MoodChip("Okay", "😐") { onLogMood() }
                MoodChip("Sad", "😴") { onLogMood() }
                MoodChip("Angry", "😠") { onLogMood() }
            }

            Spacer(Modifier.height(20.dp))

            // ───────────────── Progress card ─────────────────
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Your progress", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                "58%",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "of the weekly plan\ncompleted",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // ───────────────── “Prefer to log it yourself?” (quick add) ─────────────────
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
                        listOf("happy" to 85, "sad" to 30, "angry" to 25, "tired" to 45, "neutral" to 62)
                            .forEach { (m, s) ->
                                AssistChip(
                                    onClick = { if (!saving) addMood(m, s) },
                                    label = { Text(m) }
                                )
                            }
                    }
                }
            }
        }
    }

    // ───────────────── Full-screen camera dialog (unchanged) ─────────────────
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
                        selfieMood = mood
                        selfieConf = conf
                        addMood(mood, (conf * 100).toInt().coerceIn(0, 100))
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
        color = Color(0xFFFFF9C4), // pale yellow pill
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