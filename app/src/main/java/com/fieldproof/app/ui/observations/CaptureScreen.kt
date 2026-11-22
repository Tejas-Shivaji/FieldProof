package com.fieldproof.app.ui.observations

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.fieldproof.app.ui.CaptureViewModel
import com.fieldproof.app.ui.common.DisclaimerCard
import com.fieldproof.app.ui.theme.Ink
import com.fieldproof.app.ui.theme.Sand
import java.io.File
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureScreen(
    viewModel: CaptureViewModel,
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
) {
    val context = LocalContext.current
    val saving by viewModel.saving.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val savedId by viewModel.savedId.collectAsStateWithLifecycle()
    var title by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var includeLocation by remember { mutableStateOf(false) }
    var photoFile by remember { mutableStateOf<File?>(null) }
    var previewUri by remember { mutableStateOf<Uri?>(null) }
    var pendingCameraFile by remember { mutableStateOf<File?>(null) }
    var locationLabel by remember { mutableStateOf("Location not requested") }

    LaunchedEffect(savedId) {
        savedId?.let(onSaved)
    }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) {
            photoFile = pendingCameraFile
            previewUri = pendingCameraFile?.let { Uri.fromFile(it) }
        }
    }
    val pickVisual = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val dest = File(context.cacheDir, "capture/${UUID.randomUUID()}.jpg")
        dest.parentFile?.mkdirs()
        context.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        }
        photoFile = dest
        previewUri = Uri.fromFile(dest)
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            pendingCameraFile = startCameraCapture(context, takePicture)
        }
    }
    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        includeLocation = result.values.any { it }
        locationLabel = if (includeLocation) {
            viewModel.lastKnownLocation()?.let { "${it.latitude}, ${it.longitude} (device-reported)" }
                ?: "Permission granted, no last-known fix yet"
        } else {
            "Location permission denied"
        }
    }

    Scaffold(
        containerColor = Ink,
        topBar = {
            TopAppBar(
                title = { Text("New observation", color = Sand) },
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DisclaimerCard()
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "titleField" },
                label = { Text("Title") },
                singleLine = true,
            )
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .semantics { contentDescription = "notesField" },
                label = { Text("Notes") },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                            PackageManager.PERMISSION_GRANTED
                        if (granted) {
                            pendingCameraFile = startCameraCapture(context, takePicture)
                        } else {
                            cameraPermission.launch(Manifest.permission.CAMERA)
                        }
                    },
                    modifier = Modifier.semantics { contentDescription = "takePhoto" },
                ) { Text("Take photo") }
                OutlinedButton(
                    onClick = { pickVisual.launch("image/*") },
                    modifier = Modifier.semantics { contentDescription = "pickPhoto" },
                ) { Text("Choose photo") }
            }
            previewUri?.let {
                AsyncImage(
                    model = it,
                    contentDescription = "Attached evidence",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Crop,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = includeLocation,
                    onCheckedChange = { checked ->
                        if (checked) {
                            locationPermission.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION,
                                ),
                            )
                        } else {
                            includeLocation = false
                            locationLabel = "Location not requested"
                        }
                    },
                )
                Column {
                    Text("Include device GPS", color = Sand)
                    Text(locationLabel, style = MaterialTheme.typography.labelSmall, color = Sand.copy(alpha = 0.65f))
                }
            }
            Text(
                "Saving writes Room + the evidence file + SHA-256 before any network call. The app can be killed immediately after save.",
                style = MaterialTheme.typography.bodySmall,
                color = Sand.copy(alpha = 0.7f),
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = {
                    val file = photoFile ?: return@Button
                    viewModel.save(title, notes, file, "image/jpeg", includeLocation)
                },
                enabled = !saving && photoFile != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "saveObservation" },
            ) {
                Text(if (saving) "Saving locally…" else "Save offline")
            }
        }
    }
}

private fun startCameraCapture(
    context: Context,
    launcher: androidx.activity.result.ActivityResultLauncher<Uri>,
): File {
    val dir = File(context.cacheDir, "capture").apply { mkdirs() }
    val file = File(dir, "${UUID.randomUUID()}.jpg")
    file.createNewFile()
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    launcher.launch(uri)
    return file
}
