@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.moodtrackerapp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import androidx.compose.material.icons.automirrored.filled.ArrowBack

@Composable
fun EntryRecordsScreen(
    onBack: () -> Unit
) {
    val repo = remember { MoodRepository() }
    val scope = rememberCoroutineScope()

    var last7 by remember { mutableStateOf<List<MoodEntry>>(emptyList()) }
    var monthly by remember { mutableStateOf<Map<Int, MoodEntry?>>(emptyMap()) }

    // Load data once
    LaunchedEffect(Unit) {
        scope.launch {
            val entries7 = repo.last7()
            last7 = entries7

            // Build map: day-of-month -> latest entry that day
            val cal = Calendar.getInstance()
            val byDay = mutableMapOf<Int, MoodEntry>()
            entries7.forEach { e ->
                cal.time = e.createdAt.toDate()
                val day = cal.get(Calendar.DAY_OF_MONTH)
                val existing = byDay[day]
                if (existing == null || e.createdAt > existing.createdAt) {
                    byDay[day] = e
                }
            }
            monthly = byDay
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {

            // Top bar row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                Spacer(Modifier.width(4.dp))
                Column {
                    Text(
                        text = "Entry Records",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = "Your recent moods at a glance",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // ----------- LAST 7 DAYS CARD -----------
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(
                        horizontal = 16.dp,
                        vertical = 14.dp
                    )
                ) {
                    Text(
                        text = "LAST 7 DAYS",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )

                    Spacer(Modifier.height(8.dp))

                    if (last7.isEmpty()) {
                        Text(
                            "No moods logged yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(last7) { entry ->
                                Last7Row(entry = entry)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ----------- CALENDAR TRACKER CARD -----------
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(
                        horizontal = 16.dp,
                        vertical = 14.dp
                    )
                ) {
                    Text(
                        text = "Calendar Tracker",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    Text(
                        text = "Tap a day to view details",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(12.dp))

                    CalendarGrid(monthly = monthly)

                    Spacer(Modifier.height(16.dp))

                    LegendRow()
                }
            }
        }
    }
}

/* ---------- Last 7 days row ---------- */

@Composable
private fun Last7Row(entry: MoodEntry) {
    val dateStr = entry.createdAt.toDate().fmt("MMM d, yyyy")
    val emoji = moodEmoji(entry.mood)
    val bg = moodTint(entry.mood)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = dateStr,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold
                )
            )
            Text(
                text = entry.mood.replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = emoji,
            style = MaterialTheme.typography.headlineSmall
        )
    }
}

/* ---------- Calendar grid ---------- */

@Composable
private fun CalendarGrid(
    monthly: Map<Int, MoodEntry?>
) {
    val cal = remember { Calendar.getInstance() }
    val year = cal.get(Calendar.YEAR)
    val month = cal.get(Calendar.MONTH) // 0-based
    cal.set(year, month, 1)

    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val startDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) // 1 = Sunday

    // Weekday labels
    val weekdays = listOf("S", "M", "T", "W", "T", "F", "S")

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        weekdays.forEach {
            Text(
                text = it,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    Spacer(Modifier.height(8.dp))

    var day = 1
    val firstColumnIndex = (startDayOfWeek - Calendar.SUNDAY) // 0..6

    Column {
        while (day <= daysInMonth) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                for (col in 0..6) {
                    val cellDay = when {
                        // first row offset
                        day == 1 && col < firstColumnIndex -> null
                        day > daysInMonth -> null
                        else -> day++
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (cellDay != null) {
                            val entry = monthly[cellDay]
                            val emoji = entry?.mood?.let { moodEmoji(it) }
                            val tint = entry?.mood?.let { moodTint(it) } ?: Color.Transparent

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(tint),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = emoji ?: "",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                Text(
                                    text = cellDay.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/* ---------- Legend ---------- */

@Composable
private fun LegendRow() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        LegendChip(color = PositiveTint, label = "Positive")
        LegendChip(color = NeutralTint, label = "Neutral")
        LegendChip(color = LowTint, label = "Low")
        LegendChip(color = AngryTint, label = "Angry")
    }
}

@Composable
private fun LegendChip(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/* ---------- Mood helpers ---------- */

private val PositiveTint = Color(0xFFE6F7E9)
private val NeutralTint  = Color(0xFFFDF7E5)
private val LowTint      = Color(0xFFE6F0FF)
private val AngryTint    = Color(0xFFFFE5E5)

private fun moodEmoji(mood: String): String = when (mood.lowercase()) {
    "happy", "amazing", "good" -> "😊"
    "sad", "low"               -> "😢"
    "angry", "mad"             -> "😠"
    "tired"                    -> "🥱"
    else                       -> "😐"
}

private fun moodTint(mood: String): Color = when (mood.lowercase()) {
    "happy", "amazing", "good" -> PositiveTint
    "sad", "low"               -> LowTint
    "angry", "mad"             -> AngryTint
    "tired"                    -> NeutralTint
    else                       -> NeutralTint
}

private fun Date.fmt(pattern: String): String =
    SimpleDateFormat(pattern, Locale.getDefault()).format(this)

private fun Date.fmt(p: String) = SimpleDateFormat(p, Locale.getDefault()).format(this)
private fun emoji(mood:String) = when(mood) {
    "happy"->"😊"; "sad"->"😢"; "angry"->"😠"; "tired"->"🥱"; else->"😐"
}
