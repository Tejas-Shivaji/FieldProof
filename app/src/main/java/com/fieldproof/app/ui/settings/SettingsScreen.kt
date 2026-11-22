package com.fieldproof.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fieldproof.app.ui.SettingsViewModel
import com.fieldproof.app.ui.common.DisclaimerCard
import com.fieldproof.app.ui.theme.Ink
import com.fieldproof.app.ui.theme.Sand

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    val stored by viewModel.apiUrl.collectAsStateWithLifecycle()
    var url by remember(stored) { mutableStateOf(stored) }
    Scaffold(
        containerColor = Ink,
        topBar = {
            TopAppBar(
                title = { Text("Settings", color = Sand) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = Sand)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Ink),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            DisclaimerCard()
            Text(
                "The emulator reaches the host machine at 10.0.2.2. A physical device should use your computer's LAN IP.",
                style = MaterialTheme.typography.bodySmall,
                color = Sand.copy(alpha = 0.75f),
            )
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("API base URL") },
                singleLine = true,
            )
            Button(onClick = { viewModel.saveUrl(url) }, modifier = Modifier.fillMaxWidth()) {
                Text("Save URL")
            }
            OutlinedButton(onClick = { viewModel.syncNow() }, modifier = Modifier.fillMaxWidth()) {
                Text("Enqueue sync (WorkManager)")
            }
        }
    }
}
