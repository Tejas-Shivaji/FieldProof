package com.fieldproof.app.ui.observations

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fieldproof.app.ui.ConflictsViewModel
import com.fieldproof.app.ui.common.StatusChip
import com.fieldproof.app.ui.theme.Ink
import com.fieldproof.app.ui.theme.Sand

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConflictsScreen(
    viewModel: ConflictsViewModel,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
) {
    val conflicts by viewModel.conflicts.collectAsStateWithLifecycle()
    Scaffold(
        containerColor = Ink,
        topBar = {
            TopAppBar(
                title = { Text("Preserved conflicts", color = Sand) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = Sand)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Ink),
            )
        },
    ) { padding ->
        if (conflicts.isEmpty()) {
            Text(
                "No conflicts. When local and remote both change after the last synced revision, FieldProof keeps both versions.",
                modifier = Modifier.padding(padding).padding(24.dp),
                color = Sand,
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(conflicts, key = { it.id }) { item ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpen(item.id) },
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(item.title, color = Sand, style = MaterialTheme.typography.titleMedium)
                            StatusChip(item.syncStatus)
                            Text(
                                item.lastError ?: "Local and server both changed.",
                                color = Sand.copy(alpha = 0.75f),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
}
