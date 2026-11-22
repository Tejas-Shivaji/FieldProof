package com.fieldproof.app.ui.observations

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.fieldproof.app.domain.model.SyncStatus
import com.fieldproof.app.ui.DetailViewModel
import com.fieldproof.app.ui.common.DisclaimerCard
import com.fieldproof.app.ui.common.IntegrityChip
import com.fieldproof.app.ui.common.MetaRow
import com.fieldproof.app.ui.common.StatusChip
import com.fieldproof.app.ui.common.formatDeviceTime
import com.fieldproof.app.ui.theme.Ink
import com.fieldproof.app.ui.theme.Sand
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    viewModel: DetailViewModel,
    filesDir: File,
    onBack: () -> Unit,
) {
    val detail by viewModel.detail.collectAsStateWithLifecycle()
    val integrity by viewModel.integrity.collectAsStateWithLifecycle()
    val signatureValid by viewModel.signatureValid.collectAsStateWithLifecycle()
    val report by viewModel.report.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val observation = detail?.observation
    var title by remember(observation?.id, observation?.updatedAtEpochMs) { mutableStateOf(observation?.title.orEmpty()) }
    var notes by remember(observation?.id, observation?.updatedAtEpochMs) { mutableStateOf(observation?.notes.orEmpty()) }

    Scaffold(
        containerColor = Ink,
        topBar = {
            TopAppBar(
                title = { Text(observation?.title ?: "Observation", color = Sand) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = Sand)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Ink),
            )
        },
    ) { padding ->
        if (observation == null) {
            Text("Loading…", modifier = Modifier.padding(padding).padding(24.dp), color = Sand)
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusChip(observation.syncStatus)
                integrity?.let { IntegrityChip(it) }
            }
            DisclaimerCard()
            detail?.evidence?.let { evidence ->
                val file = File(File(filesDir, "evidence"), evidence.relativePath)
                if (file.exists()) {
                    AsyncImage(
                        model = file,
                        contentDescription = "Evidence photo",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                            .clip(RoundedCornerShape(16.dp)),
                        contentScale = ContentScale.Crop,
                    )
                }
                MetaRow("SHA-256", evidence.sha256Hex)
                MetaRow("Bytes", evidence.byteLength.toString())
                MetaRow("Hashed at", formatDeviceTime(evidence.hashedAtEpochMs))
                MetaRow("Keystore signature", if (evidence.keystoreSignatureBase64 != null) "present" else "missing")
                signatureValid?.let { MetaRow("Signature valid", it.toString()) }
            }
            MetaRow("Captured (device clock)", formatDeviceTime(observation.capturedAtEpochMs))
            MetaRow(
                "GPS",
                if (observation.latitude != null && observation.longitude != null) {
                    "${observation.latitude}, ${observation.longitude} (device-reported)"
                } else {
                    "not recorded"
                },
            )
            MetaRow("Local revision", observation.localRevision.toString())
            MetaRow("Server revision", observation.serverRevision?.toString() ?: "-")
            observation.lastError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Title") },
            )
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Notes") },
            )
            OutlinedButton(onClick = { viewModel.saveEdits(title, notes) }, modifier = Modifier.fillMaxWidth()) {
                Text("Save local edit (creates a new revision)")
            }
            Button(
                onClick = { viewModel.verify() },
                enabled = !busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "verifyIntegrity" },
            ) { Text(if (busy) "Working…" else "Recompute SHA-256") }
            Button(
                onClick = { viewModel.export() },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Build verification report") }

            if (observation.syncStatus == SyncStatus.CONFLICT) {
                Text("Two versions exist. Neither was discarded.", color = Sand)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = { viewModel.keepLocal() }, modifier = Modifier.weight(1f)) { Text("Keep local") }
                    OutlinedButton(onClick = { viewModel.keepRemote() }, modifier = Modifier.weight(1f)) { Text("Keep remote") }
                }
            }

            Text("Evidence history", style = MaterialTheme.typography.titleMedium, color = Sand)
            detail?.versions.orEmpty().forEach { version ->
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${version.source} · revision ${version.revision}", color = Sand)
                        Text(version.title, style = MaterialTheme.typography.bodyMedium, color = Sand)
                        Text(version.notes, style = MaterialTheme.typography.bodySmall, color = Sand.copy(alpha = 0.75f))
                        Text(
                            formatDeviceTime(version.recordedAtEpochMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = Sand.copy(alpha = 0.55f),
                        )
                    }
                }
            }

            report?.let { built ->
                Text("Report", style = MaterialTheme.typography.titleMedium, color = Sand)
                Text(built.text, style = MaterialTheme.typography.bodySmall, color = Sand.copy(alpha = 0.85f))
                OutlinedButton(
                    onClick = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "FieldProof verification report")
                            putExtra(Intent.EXTRA_TEXT, built.text)
                        }
                        context.startActivity(Intent.createChooser(send, "Export report"))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Share report") }
            }
        }
    }
}
