package com.fieldproof.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.fieldproof.app.di.AppContainer
import com.fieldproof.app.domain.model.CreateObservationInput
import com.fieldproof.app.domain.model.IntegrityResult
import com.fieldproof.app.domain.model.LocationFix
import com.fieldproof.app.domain.model.Observation
import com.fieldproof.app.domain.model.ObservationDetail
import com.fieldproof.app.domain.model.VerificationReport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class ListViewModel(private val container: AppContainer) : ViewModel() {
    val observations: StateFlow<List<Observation>> = container.observations.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val conflictCount: StateFlow<Int> = container.observations.observeConflictCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun syncNow() {
        viewModelScope.launch { container.observations.enqueueSync() }
    }
}

class CaptureViewModel(private val container: AppContainer) : ViewModel() {
    private val _saving = MutableStateFlow(false)
    val saving = _saving.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private val _savedId = MutableStateFlow<String?>(null)
    val savedId = _savedId.asStateFlow()

    fun lastKnownLocation(): LocationFix? = container.locationReader.lastKnown()

    fun save(
        title: String,
        notes: String,
        photo: File,
        mimeType: String,
        includeLocation: Boolean,
    ) {
        if (_saving.value) return
        viewModelScope.launch {
            _saving.value = true
            _error.value = null
            try {
                val created = container.observations.create(
                    CreateObservationInput(
                        title = title,
                        notes = notes,
                        photoBytesSource = photo,
                        mimeType = mimeType,
                        location = if (includeLocation) container.locationReader.lastKnown() else null,
                    ),
                )
                _savedId.value = created.id
            } catch (error: Exception) {
                _error.value = error.message ?: "Could not save observation"
            } finally {
                _saving.value = false
            }
        }
    }
}

class DetailViewModel(
    private val container: AppContainer,
    private val observationId: String,
) : ViewModel() {
    val detail: StateFlow<ObservationDetail?> = container.observations.observeDetail(observationId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _integrity = MutableStateFlow<IntegrityResult?>(null)
    val integrity = _integrity.asStateFlow()
    private val _signatureValid = MutableStateFlow<Boolean?>(null)
    val signatureValid = _signatureValid.asStateFlow()
    private val _report = MutableStateFlow<VerificationReport?>(null)
    val report = _report.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()

    fun verify() {
        viewModelScope.launch {
            _busy.value = true
            _integrity.value = container.observations.verify(observationId)
            _signatureValid.value = container.observations.signatureValid(observationId)
            _busy.value = false
        }
    }

    fun export() {
        viewModelScope.launch {
            _busy.value = true
            _report.value = container.observations.exportReport(observationId)
            _busy.value = false
        }
    }

    fun keepLocal() {
        viewModelScope.launch { container.observations.resolveKeepLocal(observationId) }
    }

    fun keepRemote() {
        viewModelScope.launch { container.observations.resolveKeepRemote(observationId) }
    }

    fun saveEdits(title: String, notes: String) {
        viewModelScope.launch { container.observations.updateNotes(observationId, title, notes) }
    }
}

class ConflictsViewModel(container: AppContainer) : ViewModel() {
    val conflicts: StateFlow<List<Observation>> = container.observations.observeConflicts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    val apiUrl: StateFlow<String> = container.settings.observeApiBaseUrl()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    fun saveUrl(url: String) {
        viewModelScope.launch { container.settings.setApiBaseUrl(url) }
    }

    fun syncNow() {
        viewModelScope.launch { container.observations.enqueueSync() }
    }
}

fun <T : ViewModel> viewModelFactory(create: () -> T): ViewModelProvider.Factory {
    return object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <VM : ViewModel> create(modelClass: Class<VM>): VM = create() as VM
    }
}
