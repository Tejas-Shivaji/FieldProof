package com.fieldproof.app.data.sync

import com.fieldproof.app.data.local.EvidenceFileStore
import com.fieldproof.app.data.local.FieldProofDatabase
import com.fieldproof.app.data.local.SyncMetaEntity
import com.fieldproof.app.data.remote.ApiFactory
import com.fieldproof.app.data.remote.ConflictResponse
import com.fieldproof.app.data.remote.EvidencePushDto
import com.fieldproof.app.data.remote.ObservationPushRequest
import com.fieldproof.app.data.remote.ObservationResponse
import com.fieldproof.app.data.repo.ObservationRepositoryImpl
import com.fieldproof.app.data.settings.SettingsRepositoryImpl
import com.fieldproof.app.domain.model.Observation
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class SyncManager(
    private val db: FieldProofDatabase,
    private val files: EvidenceFileStore,
    private val repository: ObservationRepositoryImpl,
    private val settings: SettingsRepositoryImpl,
) {
    suspend fun synchronize() {
        val api = ApiFactory.create(settings.apiBaseUrl())
        try {
            api.health()
        } catch (error: Exception) {
            throw IOException("backend unreachable", error)
        }

        val pending = db.observationDao().pending()
        for (entity in pending) {
            repository.markSyncing(entity.id)
            val evidence = db.evidenceDao().getForObservation(entity.id)
            val body = ObservationPushRequest(
                id = entity.id,
                title = entity.title,
                notes = entity.notes,
                capturedAtEpochMs = entity.capturedAtEpochMs,
                latitude = entity.latitude,
                longitude = entity.longitude,
                locationAccuracyMeters = entity.locationAccuracyMeters,
                locationProvider = entity.locationProvider,
                localRevision = entity.localRevision,
                lastSyncedRevision = entity.lastSyncedRevision,
                deleted = entity.deleted,
                evidence = evidence?.let {
                    EvidencePushDto(
                        id = it.id,
                        sha256Hex = it.sha256Hex,
                        byteLength = it.byteLength,
                        mimeType = it.mimeType,
                    )
                },
            )
            val response = try {
                api.pushObservation(body)
            } catch (error: Exception) {
                repository.markFailed(entity.id, "network error")
                throw IOException("push failed", error)
            }
            when {
                response.isSuccessful -> {
                    val payload = response.body() ?: continue
                    if (evidence != null) {
                        val file = files.fileFor(evidence.relativePath)
                        if (file.exists()) {
                            val shaBody = evidence.sha256Hex.toRequestBody("text/plain".toMediaType())
                            val part = MultipartBody.Part.createFormData(
                                "file",
                                file.name,
                                file.asRequestBody(evidence.mimeType.toMediaType()),
                            )
                            val upload = api.uploadEvidence(entity.id, shaBody, part)
                            if (!upload.isSuccessful && upload.code() != 409) {
                                repository.markFailed(entity.id, "evidence upload HTTP ${upload.code()}")
                                continue
                            }
                        }
                    }
                    repository.markSynced(entity.id, payload.revision)
                }
                response.code() == 409 -> {
                    val conflict = parseConflict(response.errorBody()?.string())
                    if (conflict != null) {
                        repository.applyRemote(conflict.server.toRemote(), asConflict = true)
                    } else {
                        repository.markFailed(entity.id, "conflict without payload")
                    }
                }
                else -> {
                    repository.markFailed(entity.id, "HTTP ${response.code()}")
                    if (response.code() in 500..599) throw IOException("server error ${response.code()}")
                }
            }
        }

        val cursor = db.syncMetaDao().get(CURSOR_KEY)?.toLongOrNull() ?: 0L
        val pull = try {
            api.pullObservations(cursor)
        } catch (error: Exception) {
            throw IOException("pull failed", error)
        }
        val locals = db.observationDao().getAll().associateBy { it.id }
        for (remoteDto in pull.observations) {
            val remote = remoteDto.toRemote()
            val local = locals[remote.id]?.let {
                Observation(
                    id = it.id,
                    title = it.title,
                    notes = it.notes,
                    capturedAtEpochMs = it.capturedAtEpochMs,
                    latitude = it.latitude,
                    longitude = it.longitude,
                    locationAccuracyMeters = it.locationAccuracyMeters,
                    locationProvider = it.locationProvider,
                    syncStatus = it.syncStatus,
                    localRevision = it.localRevision,
                    serverRevision = it.serverRevision,
                    lastSyncedRevision = it.lastSyncedRevision,
                    createdAtEpochMs = it.createdAtEpochMs,
                    updatedAtEpochMs = it.updatedAtEpochMs,
                    lastError = it.lastError,
                    deleted = it.deleted,
                )
            }
            when (val decision = ConflictMerger.decide(local, remote)) {
                MergeDecision.InSync -> Unit
                is MergeDecision.PushLocal -> Unit
                is MergeDecision.InsertRemote -> repository.applyRemote(decision.remote, asConflict = false)
                is MergeDecision.FastForwardRemote -> repository.applyRemote(decision.remote, asConflict = false)
                is MergeDecision.Conflict -> repository.applyRemote(decision.remote, asConflict = true)
            }
        }
        db.syncMetaDao().put(SyncMetaEntity(CURSOR_KEY, pull.serverTimeEpochMs.toString()))
    }

    private fun parseConflict(raw: String?): ConflictResponse? {
        if (raw.isNullOrBlank()) return null
        return runCatching { ApiFactory.gson.fromJson(raw, ConflictResponse::class.java) }.getOrNull()
    }

    companion object {
        const val CURSOR_KEY = "pull_cursor_epoch_ms"
    }
}

fun ObservationResponse.toRemote() = RemoteObservation(
    id = id,
    title = title,
    notes = notes,
    capturedAtEpochMs = capturedAtEpochMs,
    latitude = latitude,
    longitude = longitude,
    locationAccuracyMeters = locationAccuracyMeters,
    locationProvider = locationProvider,
    revision = revision,
    updatedAtEpochMs = updatedAtEpochMs,
    evidenceId = evidence?.id,
    evidenceSha256Hex = evidence?.sha256Hex,
    evidenceByteLength = evidence?.byteLength,
    evidenceMimeType = evidence?.mimeType,
    deleted = deleted,
)
