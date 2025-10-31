@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.moodtrackerapp

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    currentTheme: ThemeMode,
    onChangeTheme: (ThemeMode) -> Unit
) {
    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }
    val uid = auth.currentUser?.uid

    // Name/handle pulled from Auth, then Firestore fallback
    var name by remember { mutableStateOf(auth.currentUser?.displayName ?: "") }
    var handle by remember { mutableStateOf<String?>(null) }

    // Try Firestore username if displayName is blank
    LaunchedEffect(uid) {
        if (uid != null) {
            try {
                val snap = db.collection("users").document(uid).get().await()
                val u = (snap.get("username") as? String)?.trim().orEmpty()
                if (u.isNotEmpty()) {
                    if (name.isBlank()) name = u
                    handle = "@$u"
                } else if (name.isBlank()) {
                    // final fallback: email prefix
                    val emailName = auth.currentUser?.email?.substringBefore('@').orEmpty()
                    if (emailName.isNotBlank()) {
                        name = emailName.replaceFirstChar { it.uppercase() }
                        handle = "@$emailName"
                    }
                }
            } catch (_: Exception) {
                // ignore fetch errors; keep whatever we have
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text("👤", style = MaterialTheme.typography.headlineLarge)
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = if (name.isNotBlank()) name else " ",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
            )
            handle?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }

            Spacer(Modifier.height(28.dp))

            // Theme section
            Text("Theme Style", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ThemeCard(
                    title = "Light",
                    emoji = "🌞",
                    selected = currentTheme == ThemeMode.Light,
                    onClick = { onChangeTheme(ThemeMode.Light) },
                    modifier = Modifier.weight(1f)
                )
                ThemeCard(
                    title = "Dark",
                    emoji = "🌙",
                    selected = currentTheme == ThemeMode.Dark,
                    onClick = { onChangeTheme(ThemeMode.Dark) },
                    modifier = Modifier.weight(1f)
                )
                ThemeCard(
                    title = "Custom",
                    emoji = "🎨",
                    selected = currentTheme == ThemeMode.Custom,
                    onClick = { onChangeTheme(ThemeMode.Custom) },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(28.dp))

            Text("Preferences", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))

            PrefRow("🔔 Notifications")
            PrefRow("🔒 Privacy Settings")
            PrefRow("🌐 Language")

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = {
                    // Optional: persist theme choice to Firestore for this user
                    if (uid != null) {
                        val data = mapOf("theme" to currentTheme.name)
                        db.collection("users").document(uid)
                            .set(data, SetOptions.merge())
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(22.dp)
            ) {
                Text("Save Changes")
            }
        }
    }
}

@Composable
private fun ThemeCard(
    title: String,
    emoji: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg = if (selected) Color(0xFFFFF3B0) else MaterialTheme.colorScheme.surfaceVariant
    val stroke = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent

    Surface(
        onClick = onClick,
        color = bg,
        shape = RoundedCornerShape(20.dp),
        modifier = modifier
            .height(120.dp)
            .border(2.dp, stroke, RoundedCornerShape(20.dp))
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(emoji, style = MaterialTheme.typography.headlineMedium)
            Text(title, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun PrefRow(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text, style = MaterialTheme.typography.bodyLarge)
        }
    }
    Spacer(Modifier.height(12.dp))
}