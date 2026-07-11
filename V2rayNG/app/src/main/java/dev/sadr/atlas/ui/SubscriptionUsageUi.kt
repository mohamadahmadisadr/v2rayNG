package dev.sadr.atlas.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.sadr.atlas.dto.entities.SubscriptionItem
import dev.sadr.atlas.extension.toTrafficString
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * True when the server reported any data-usage or expiry info for this
 * subscription (via the `subscription-userinfo` response header).
 */
internal fun SubscriptionItem.hasUsageInfo(): Boolean =
    download >= 0 || upload >= 0 || total >= 0 || expire >= 0

/**
 * Renders the data used (with a quota progress bar when a total is set) and the
 * expiry reported by the server's `subscription-userinfo` header. Renders nothing
 * when no usage info is available.
 */
@Composable
internal fun SubscriptionUsageContent(item: SubscriptionItem, modifier: Modifier = Modifier) {
    val hasUsage = item.download >= 0 || item.upload >= 0 || item.total >= 0
    val hasExpiry = item.expire >= 0
    if (!hasUsage && !hasExpiry) return

    val used = item.upload.coerceAtLeast(0) + item.download.coerceAtLeast(0)
    val total = item.total
    val usedText = when {
        total > 0 -> "${used.toTrafficString()} / ${total.toTrafficString()}"
        hasUsage -> "${used.toTrafficString()} used"
        else -> null
    }
    val progress = if (total > 0) (used.toFloat() / total.toFloat()).coerceIn(0f, 1f) else null

    val expired = hasExpiry && item.expire > 0L && item.expire <= System.currentTimeMillis() / 1000L
    val expiryText = when {
        !hasExpiry -> null
        item.expire == 0L -> "Never expires"
        expired -> "Expired"
        else -> {
            val date = remember(item.expire) {
                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(item.expire * 1000L))
            }
            "Expires $date"
        }
    }

    Column(modifier = modifier) {
        if (progress != null) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape),
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
            Spacer(modifier = Modifier.height(6.dp))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (usedText != null) {
                Text(
                    text = usedText,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (expiryText != null) {
                Text(
                    text = expiryText,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (expired) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
