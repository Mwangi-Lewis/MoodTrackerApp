@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.moodtrackerapp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TipsScreen(
    onBack: () -> Unit,
    onWriteOut: () -> Unit,
    onSchoolCounsellor: () -> Unit = {},
    onSafeSpace: () -> Unit = {}
) {
    val bg = Color(0xFFF9F7FB)

    Scaffold(
        containerColor = bg,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()               // <-- keeps out of system bar
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // Back row (now safely below status bar)
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFF151515)
                        )
                    }
                    Spacer(Modifier.width(2.dp))
                    Text(
                        text = "Back",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = Color(0xFF151515)
                        )
                    )
                }

                Spacer(Modifier.height(8.dp))

                // Gradient header "Tips"
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFF63A4FF), Color(0xFF83EAF1))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Tips",
                        color = Color.White,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {

            Spacer(Modifier.height(16.dp))

            // Card 1 - Quick grounding
            TipCard(
                emoji = "💡",
                title = "Quick grounding",
                body = "Try 4-7-8 breathing for one minute to reset."
            )

            Spacer(Modifier.height(12.dp))

            // Card 2 - Write it out (navigates to LogMoodScreen)
            TipCard(
                emoji = "❤️",
                title = "Write it out",
                body = "Jot a few lines about what you're feeling—no filter.",
                onClick = onWriteOut
            )

            Spacer(Modifier.height(12.dp))

            // Card 3 - Need to talk to someone now?
            HelpCard(
                onSchoolCounsellor = onSchoolCounsellor,
                onSafeSpace = onSafeSpace
            )
        }
    }
}

/* ------------ Reusable UI bits ------------ */

@Composable
private fun TipCard(
    emoji: String,
    title: String,
    body: String,
    onClick: (() -> Unit)? = null
) {
    val shape = RoundedCornerShape(24.dp)
    Surface(
        onClick = { onClick?.invoke() },
        enabled = onClick != null,
        shape = shape,
        color = Color.White,
        shadowElevation = 6.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = emoji,
                fontSize = 26.sp,
                modifier = Modifier.padding(end = 16.dp)
            )
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF222222)
                    )
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color(0xFF6B6B6B)
                    )
                )
            }
        }
    }
}

@Composable
private fun HelpCard(
    onSchoolCounsellor: () -> Unit,
    onSafeSpace: () -> Unit
) {
    val shape = RoundedCornerShape(24.dp)
    Surface(
        shape = shape,
        color = Color.White,
        shadowElevation = 6.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Need to talk to someone now?",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF222222)
                )
            )
            Text(
                text = "Add a direct link to your school counsellor or a trusted person.",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = Color(0xFF6B6B6B)
                )
            )

            Spacer(Modifier.height(4.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onSchoolCounsellor,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF506BFF),
                        contentColor = Color.White
                    )
                ) {
                    Text("School Counsellor")
                }
                Button(
                    onClick = onSafeSpace,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF28A876),
                        contentColor = Color.White
                    )
                ) {
                    Text("Safe Space")
                }
            }
        }
    }
}
