package com.fieldproof.app.ui.observations

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fieldproof.app.domain.model.Observation
import com.fieldproof.app.ui.ListViewModel
import com.fieldproof.app.ui.common.DisclaimerCard
import com.fieldproof.app.ui.common.StatusChip
import com.fieldproof.app.ui.common.formatDeviceTime
import com.fieldproof.app.ui.theme.Clay
import com.fieldproof.app.ui.theme.Ink
import com.fieldproof.app.ui.theme.Sand

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListScreen(
    viewModel: ListViewModel,
    onCapture: () -> Unit,
    onOpen: (String) -> Unit,
    onConflicts: () -> Unit,
    onSettings: () -> Unit,
) {
    val items by viewModel.observations.collectAsStateWithLifecycle()
    val conflicts by viewModel.conflictCount.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = Ink,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("FieldProof", color = Sand)
                        Text(
                            "Offline-first evidence recorder",
                            style = MaterialTheme.typography.labelSmall,
                            color = Sand.copy(alpha = 0.7f),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.syncNow() }, modifier = Modifier.semantics { contentDescription = "syncNow" }) {
                        Icon(Icons.Outlined.CloudSync, contentDescription = "Sync now", tint = Sand)
                    }
                    IconButton(onClick = onConflicts, modifier = Modifier.semantics { contentDescription = "openConflicts" }) {
                        BadgedBox(badge = { if (conflicts > 0) Badge { Text("$conflicts") } }) {
                            Icon(Icons.Outlined.WarningAmber, contentDescription = "Conflicts", tint = if (conflicts > 0) Clay else Sand)
                        }
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings", tint = Sand)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Ink),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCapture,
                containerColor = Clay,
                contentColor = Sand,
                modifier = Modifier.semantics { contentDescription = "captureObservation" },
            ) {
                Icon(Icons.Outlined.Add, contentDescription = "New observation")
            }
        },
    ) { padding ->
        if (items.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                DisclaimerCard()
                Text(
                    "No observations yet. Capture one without a network connection. Room is the source of truth; WorkManager uploads later.",
                    color = Sand.copy(alpha = 0.86f),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { DisclaimerCard() }
                items(items, key = { it.id }) { observation ->
                    ObservationCard(observation, onClick = { onOpen(observation.id) })
                }
                item { Spacer(Modifier.height(72.dp)) }
            }
        }
    }
}

@Composable
private fun ObservationCard(observation: Observation, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics { contentDescription = "observation-${observation.id}" },
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    observation.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Sand,
                    modifier = Modifier.weight(1f),
                )
                StatusChip(observation.syncStatus)
            }
            Text(
                observation.notes.ifBlank { "No notes" },
                style = MaterialTheme.typography.bodyMedium,
                color = Sand.copy(alpha = 0.78f),
                maxLines = 2,
            )
            Text(
                "Captured ${formatDeviceTime(observation.capturedAtEpochMs)}  ·  rev ${observation.localRevision}",
                style = MaterialTheme.typography.labelSmall,
                color = Sand.copy(alpha = 0.55f),
            )
        }
    }
}
