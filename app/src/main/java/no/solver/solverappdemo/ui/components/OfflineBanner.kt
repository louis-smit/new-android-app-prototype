package no.solver.solverappdemo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import no.solver.solverappdemo.ui.theme.SolverAppTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@Composable
fun OfflineBanner(
    lastSyncedAt: Long?,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = "Offline",
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onErrorContainer
        )
        Text(
            text = buildOfflineMessage(lastSyncedAt),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer
        )
    }
}

private fun buildOfflineMessage(lastSyncedAt: Long?): String {
    val baseMessage = "You're offline"
    if (lastSyncedAt == null) return baseMessage
    
    val lastUpdatedText = formatRelativeTime(lastSyncedAt)
    return "$baseMessage • Last updated $lastUpdatedText"
}

private fun formatRelativeTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    
    return when {
        diff < TimeUnit.MINUTES.toMillis(1) -> "just now"
        diff < TimeUnit.HOURS.toMillis(1) -> {
            val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
            "$minutes ${if (minutes == 1L) "minute" else "minutes"} ago"
        }
        diff < TimeUnit.DAYS.toMillis(1) -> {
            val hours = TimeUnit.MILLISECONDS.toHours(diff)
            "$hours ${if (hours == 1L) "hour" else "hours"} ago"
        }
        diff < TimeUnit.DAYS.toMillis(7) -> {
            val days = TimeUnit.MILLISECONDS.toDays(diff)
            "$days ${if (days == 1L) "day" else "days"} ago"
        }
        else -> {
            val dateFormat = SimpleDateFormat("MMM d", Locale.getDefault())
            dateFormat.format(Date(timestamp))
        }
    }
}

@Preview
@Composable
private fun OfflineBannerPreview() {
    SolverAppTheme {
        OfflineBanner(
            lastSyncedAt = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(2)
        )
    }
}

@Preview
@Composable
private fun OfflineBannerNoSyncPreview() {
    SolverAppTheme {
        OfflineBanner(lastSyncedAt = null)
    }
}
