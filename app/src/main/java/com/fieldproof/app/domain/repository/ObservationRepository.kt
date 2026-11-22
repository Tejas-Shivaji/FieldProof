package com.fieldproof.app.domain.repository

import com.fieldproof.app.domain.model.CreateObservationInput
import com.fieldproof.app.domain.model.IntegrityResult
import com.fieldproof.app.domain.model.Observation
import com.fieldproof.app.domain.model.ObservationDetail
import com.fieldproof.app.domain.model.VerificationReport
import kotlinx.coroutines.flow.Flow

interface ObservationRepository {
    fun observeAll(): Flow<List<Observation>>
    fun observeConflicts(): Flow<List<Observation>>
    fun observeConflictCount(): Flow<Int>
    fun observeDetail(id: String): Flow<ObservationDetail?>

    suspend fun create(input: CreateObservationInput): Observation
    suspend fun updateNotes(id: String, title: String, notes: String)
    suspend fun verify(id: String): IntegrityResult
    suspend fun exportReport(id: String): VerificationReport
    suspend fun resolveKeepLocal(id: String)
    suspend fun resolveKeepRemote(id: String)
    suspend fun enqueueSync()
}

interface SettingsRepository {
    fun observeApiBaseUrl(): Flow<String>
    suspend fun apiBaseUrl(): String
    suspend fun setApiBaseUrl(url: String)
}
