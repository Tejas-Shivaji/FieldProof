package com.fieldproof.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fieldproof.app.domain.model.IntegrityResult
import com.fieldproof.app.domain.model.SyncStatus
import com.fieldproof.app.ui.theme.Amber
import com.fieldproof.app.ui.theme.Clay
import com.fieldproof.app.ui.theme.Danger
import com.fieldproof.app.ui.theme.Moss
import com.fieldproof.app.ui.theme.Sand
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Composable
fun StatusChip(status: SyncStatus) {
    val color = when (status) {
        SyncStatus.SYNCED -> Moss
        SyncStatus.CONFLICT -> Danger
        SyncStatus.FAILED -> Clay
        SyncStatus.SYNCING -> Amber
        SyncStatus.PENDING_UPLOAD, SyncStatus.PENDING_UPDATE -> Amber
    }
    Surface(color = color.copy(alpha = 0.22f), shape = RoundedCornerShape(20.dp)) {
        Text(
            text = status.name.replace('_', ' '),
            color = color,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
fun IntegrityChip(result: IntegrityResult) {
    val color = when (result) {
        IntegrityResult.MATCH -> Moss
        IntegrityResult.MISMATCH, IntegrityResult.MISSING_FILE -> Danger
        IntegrityResult.NOT_HASHED -> Amber
    }
    Surface(color = color.copy(alpha = 0.22f), shape = RoundedCornerShape(20.dp)) {
        Text(
            text = result.name.replace('_', ' '),
            color = color,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
fun DisclaimerCard() {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Integrity is relative, not absolute", style = MaterialTheme.typography.titleSmall, color = Clay)
            Text(
                "SHA-256 only shows whether a file still matches the digest stored at capture. " +
                    "GPS and timestamps are device-reported. FieldProof never silently overwrites a conflicting version.",
                style = MaterialTheme.typography.bodySmall,
                color = Sand.copy(alpha = 0.86f),
            )
        }
    }
}

@Composable
fun MetaRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = Sand.copy(alpha = 0.7f))
        Text(value, style = MaterialTheme.typography.bodySmall, color = Sand)
    }
}

fun formatDeviceTime(epochMs: Long): String {
    val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    format.timeZone = TimeZone.getDefault()
    return format.format(Date(epochMs))
}
