package com.fieldproof.app.data.repo

import androidx.room.withTransaction
import com.fieldproof.app.data.integrity.KeystoreSigner
import com.fieldproof.app.data.integrity.Sha256Hasher
import com.fieldproof.app.data.local.EvidenceEntity
import com.fieldproof.app.data.local.EvidenceFileStore
import com.fieldproof.app.data.local.FieldProofDatabase
import com.fieldproof.app.data.local.ObservationEntity
import com.fieldproof.app.data.local.ObservationVersionEntity
import com.fieldproof.app.data.local.toDomain
import com.fieldproof.app.data.sync.RemoteObservation
import com.fieldproof.app.data.sync.SyncScheduler
import com.fieldproof.app.domain.model.CreateObservationInput
import com.fieldproof.app.domain.model.IntegrityResult
import com.fieldproof.app.domain.model.Observation
import com.fieldproof.app.domain.model.ObservationDetail
import com.fieldproof.app.domain.model.SyncStatus
import com.fieldproof.app.domain.model.VerificationReport
import com.fieldproof.app.domain.model.VersionSource
import com.fieldproof.app.domain.repository.ObservationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

class ObservationRepositoryImpl(
    private val db: FieldProofDatabase,
    private val files: EvidenceFileStore,
    private val signer: KeystoreSigner,
    private val syncScheduler: SyncScheduler,
) : ObservationRepository {

    private val observations = db.observationDao()
    private val evidenceDao = db.evidenceDao()
    private val versions = db.versionDao()

    override fun observeAll(): Flow<List<Observation>> =
        observations.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeConflicts(): Flow<List<Observation>> =
        observations.observeConflicts().map { list -> list.map { it.toDomain() } }

    override fun observeConflictCount(): Flow<Int> = observations.observeConflictCount()

    override fun observeDetail(id: String): Flow<ObservationDetail?> {
        return combine(
            observations.observeById(id),
            evidenceDao.observeForObservation(id),
            versions.observeForObservation(id),
        ) { obs, evidence, versionList ->
            if (obs == null) {
                null
            } else {
                ObservationDetail(
                    observation = obs.toDomain(),
                    evidence = evidence?.toDomain(),
                    versions = versionList.map { it.toDomain() },
                    integrity = null,
                    signatureValid = null,
                )
            }
        }
    }

    override suspend fun create(input: CreateObservationInput): Observation {
        val observationId = UUID.randomUUID().toString()
        val evidenceId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val extension = extensionFor(input.mimeType, input.photoBytesSource.name)
        val dest = files.persist(
            observationId,
            evidenceId,
            input.photoBytesSource.inputStream(),
            extension,
        )
        val hash = dest.inputStream().use { Sha256Hasher.hash(it) }
        val signature = runCatching { signer.signDigest(hash.digest) }.getOrNull()
        val relative = files.relativePath(observationId, dest)
        val entity = ObservationEntity(
            id = observationId,
            title = input.title.trim().ifBlank { "Untitled observation" },
            notes = input.notes.trim(),
            capturedAtEpochMs = now,
            latitude = input.location?.latitude,
            longitude = input.location?.longitude,
            locationAccuracyMeters = input.location?.accuracyMeters,
            locationProvider = input.location?.provider,
            syncStatus = SyncStatus.PENDING_UPLOAD,
            localRevision = 1L,
            serverRevision = null,
            lastSyncedRevision = null,
            createdAtEpochMs = now,
            updatedAtEpochMs = now,
            lastError = null,
            deleted = false,
        )
        val evidence = EvidenceEntity(
            id = evidenceId,
            observationId = observationId,
            relativePath = relative,
            sha256Hex = hash.hex,
            byteLength = hash.byteLength,
            mimeType = input.mimeType,
            hashedAtEpochMs = now,
            keystoreSignatureBase64 = signature,
        )
        db.withTransaction {
            observations.insert(entity)
            evidenceDao.upsert(evidence)
            versions.insert(snapshot(entity, evidence.sha256Hex, VersionSource.LOCAL, now))
        }
        runCatching { input.photoBytesSource.delete() }
        syncScheduler.enqueue()
        return entity.toDomain()
    }

    override suspend fun updateNotes(id: String, title: String, notes: String) {
        val current = observations.getById(id) ?: return
        val now = System.currentTimeMillis()
        val nextStatus = when (current.syncStatus) {
            SyncStatus.PENDING_UPLOAD, SyncStatus.FAILED -> current.syncStatus
            SyncStatus.CONFLICT -> SyncStatus.CONFLICT
            else -> if (current.lastSyncedRevision == null) SyncStatus.PENDING_UPLOAD else SyncStatus.PENDING_UPDATE
        }
        val updated = current.copy(
            title = title.trim().ifBlank { current.title },
            notes = notes.trim(),
            localRevision = current.localRevision + 1,
            updatedAtEpochMs = now,
            syncStatus = nextStatus,
            lastError = null,
        )
        val evidence = evidenceDao.getForObservation(id)
        db.withTransaction {
            observations.update(updated)
            versions.insert(snapshot(updated, evidence?.sha256Hex, VersionSource.LOCAL, now))
        }
        syncScheduler.enqueue()
    }

    override suspend fun verify(id: String): IntegrityResult {
        val evidence = evidenceDao.getForObservation(id) ?: return IntegrityResult.NOT_HASHED
        val file = files.fileFor(evidence.relativePath)
        if (!file.exists()) return IntegrityResult.MISSING_FILE
        val recomputed = file.inputStream().use { Sha256Hasher.hash(it) }
        return if (Sha256Hasher.equalHex(recomputed.hex, evidence.sha256Hex)) {
            IntegrityResult.MATCH
        } else {
            IntegrityResult.MISMATCH
        }
    }

    suspend fun signatureValid(id: String): Boolean? {
        val evidence = evidenceDao.getForObservation(id) ?: return null
        val signature = evidence.keystoreSignatureBase64 ?: return null
        val digest = Sha256Hasher.fromHex(evidence.sha256Hex)
        return signer.verifyDigest(digest, signature)
    }

    override suspend fun exportReport(id: String): VerificationReport {
        val observation = observations.getById(id) ?: error("missing observation")
        val evidence = evidenceDao.getForObservation(id)
        val integrity = verify(id)
        val signature = evidence?.let { signatureValid(id) }
        val versionList = versions.getForObservation(id)
        val generatedAt = System.currentTimeMillis()
        val text = buildString {
            appendLine("FieldProof verification report")
            appendLine("Generated (device clock): ${formatTime(generatedAt)}")
            appendLine()
            appendLine("DISCLAIMER")
            appendLine("SHA-256 establishes file integrity relative to the digest recorded at capture.")
            appendLine("It does not prove authorship, authenticity, GPS truth, or correct time.")
            appendLine("Coordinates and timestamps below are device-reported claims.")
            appendLine()
            appendLine("Observation id: ${observation.id}")
            appendLine("Title: ${observation.title}")
            appendLine("Notes: ${observation.notes}")
            appendLine("Captured at (device clock): ${formatTime(observation.capturedAtEpochMs)}")
            appendLine("Location: ${formatLocation(observation)}")
            appendLine("Sync status: ${observation.syncStatus}")
            appendLine("Local revision: ${observation.localRevision}")
            appendLine("Server revision: ${observation.serverRevision ?: "-"}")
            appendLine("Last synced revision: ${observation.lastSyncedRevision ?: "-"}")
            appendLine()
            if (evidence == null) {
                appendLine("No evidence file attached.")
            } else {
                appendLine("Evidence id: ${evidence.id}")
                appendLine("Recorded SHA-256: ${evidence.sha256Hex}")
                appendLine("Byte length: ${evidence.byteLength}")
                appendLine("MIME: ${evidence.mimeType}")
                appendLine("Hashed at (device clock): ${formatTime(evidence.hashedAtEpochMs)}")
                appendLine("Integrity result: $integrity")
                appendLine("Keystore signature present: ${evidence.keystoreSignatureBase64 != null}")
                appendLine("Keystore signature valid: ${signature ?: "n/a"}")
            }
            appendLine()
            appendLine("Version history (${versionList.size})")
            versionList.forEach { version ->
                appendLine(
                    "- ${version.source} rev=${version.revision} at ${formatTime(version.recordedAtEpochMs)} " +
                        "title=${version.title} sha=${version.evidenceSha256Hex ?: "-"}",
                )
            }
        }
        files.writeReport("report-${observation.id.take(8)}-$generatedAt.txt", text)
        return VerificationReport(generatedAtEpochMs = generatedAt, text = text)
    }

    override suspend fun resolveKeepLocal(id: String) {
        val current = observations.getById(id) ?: return
        val now = System.currentTimeMillis()
        val acknowledged = current.serverRevision ?: current.lastSyncedRevision
        val updated = current.copy(
            syncStatus = if (acknowledged == null) SyncStatus.PENDING_UPLOAD else SyncStatus.PENDING_UPDATE,
            lastSyncedRevision = acknowledged,
            updatedAtEpochMs = now,
            lastError = null,
        )
        observations.update(updated)
        syncScheduler.enqueue()
    }

    override suspend fun resolveKeepRemote(id: String) {
        val current = observations.getById(id) ?: return
        val remoteVersion = versions.getForObservation(id)
            .firstOrNull { it.source == VersionSource.SERVER }
            ?: return
        val now = System.currentTimeMillis()
        val updated = current.copy(
            title = remoteVersion.title,
            notes = remoteVersion.notes,
            capturedAtEpochMs = remoteVersion.capturedAtEpochMs,
            latitude = remoteVersion.latitude,
            longitude = remoteVersion.longitude,
            localRevision = remoteVersion.revision,
            serverRevision = remoteVersion.revision,
            lastSyncedRevision = remoteVersion.revision,
            syncStatus = SyncStatus.SYNCED,
            updatedAtEpochMs = now,
            lastError = null,
        )
        db.withTransaction {
            observations.update(updated)
            versions.insert(snapshot(updated, remoteVersion.evidenceSha256Hex, VersionSource.LOCAL, now))
        }
    }

    override suspend fun enqueueSync() {
        syncScheduler.enqueue()
    }

    suspend fun applyRemote(remote: RemoteObservation, asConflict: Boolean) {
        val now = System.currentTimeMillis()
        val existing = observations.getById(remote.id)
        if (existing == null) {
            val entity = ObservationEntity(
                id = remote.id,
                title = remote.title,
                notes = remote.notes,
                capturedAtEpochMs = remote.capturedAtEpochMs,
                latitude = remote.latitude,
                longitude = remote.longitude,
                locationAccuracyMeters = remote.locationAccuracyMeters,
                locationProvider = remote.locationProvider,
                syncStatus = SyncStatus.SYNCED,
                localRevision = remote.revision,
                serverRevision = remote.revision,
                lastSyncedRevision = remote.revision,
                createdAtEpochMs = now,
                updatedAtEpochMs = remote.updatedAtEpochMs,
                lastError = null,
                deleted = remote.deleted,
            )
            db.withTransaction {
                observations.upsert(entity)
                versions.insert(snapshot(entity, remote.evidenceSha256Hex, VersionSource.SERVER, now))
            }
            return
        }
        if (asConflict) {
            val updated = existing.copy(
                syncStatus = SyncStatus.CONFLICT,
                serverRevision = remote.revision,
                updatedAtEpochMs = now,
                lastError = "Remote revision ${remote.revision} differs from local revision ${existing.localRevision}",
            )
            db.withTransaction {
                observations.update(updated)
                versions.insert(
                    ObservationVersionEntity(
                        id = UUID.randomUUID().toString(),
                        observationId = remote.id,
                        source = VersionSource.SERVER,
                        revision = remote.revision,
                        title = remote.title,
                        notes = remote.notes,
                        capturedAtEpochMs = remote.capturedAtEpochMs,
                        latitude = remote.latitude,
                        longitude = remote.longitude,
                        evidenceSha256Hex = remote.evidenceSha256Hex,
                        recordedAtEpochMs = now,
                    ),
                )
            }
            return
        }
        val updated = existing.copy(
            title = remote.title,
            notes = remote.notes,
            capturedAtEpochMs = remote.capturedAtEpochMs,
            latitude = remote.latitude,
            longitude = remote.longitude,
            locationAccuracyMeters = remote.locationAccuracyMeters,
            locationProvider = remote.locationProvider,
            syncStatus = SyncStatus.SYNCED,
            localRevision = remote.revision,
            serverRevision = remote.revision,
            lastSyncedRevision = remote.revision,
            updatedAtEpochMs = remote.updatedAtEpochMs,
            lastError = null,
            deleted = remote.deleted,
        )
        db.withTransaction {
            observations.update(updated)
            versions.insert(snapshot(updated, remote.evidenceSha256Hex, VersionSource.SERVER, now))
        }
    }

    suspend fun markSynced(id: String, serverRevision: Long) {
        val current = observations.getById(id) ?: return
        observations.update(
            current.copy(
                syncStatus = SyncStatus.SYNCED,
                serverRevision = serverRevision,
                lastSyncedRevision = serverRevision,
                localRevision = maxOf(current.localRevision, serverRevision),
                lastError = null,
                updatedAtEpochMs = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun markFailed(id: String, message: String) {
        val current = observations.getById(id) ?: return
        val status = if (current.syncStatus == SyncStatus.CONFLICT) {
            SyncStatus.CONFLICT
        } else {
            SyncStatus.FAILED
        }
        observations.update(
            current.copy(
                syncStatus = status,
                lastError = message.take(180),
                updatedAtEpochMs = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun markSyncing(id: String) {
        val current = observations.getById(id) ?: return
        if (current.syncStatus == SyncStatus.CONFLICT) return
        observations.update(current.copy(syncStatus = SyncStatus.SYNCING))
    }

    private fun snapshot(
        entity: ObservationEntity,
        evidenceSha: String?,
        source: VersionSource,
        now: Long,
    ) = ObservationVersionEntity(
        id = UUID.randomUUID().toString(),
        observationId = entity.id,
        source = source,
        revision = if (source == VersionSource.SERVER) entity.serverRevision ?: entity.localRevision else entity.localRevision,
        title = entity.title,
        notes = entity.notes,
        capturedAtEpochMs = entity.capturedAtEpochMs,
        latitude = entity.latitude,
        longitude = entity.longitude,
        evidenceSha256Hex = evidenceSha,
        recordedAtEpochMs = now,
    )

    private fun extensionFor(mime: String, filename: String): String {
        val fromName = filename.substringAfterLast('.', missingDelimiterValue = "")
        if (fromName.length in 1..4) return fromName.lowercase()
        return when (mime.lowercase()) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/jpeg", "image/jpg" -> "jpg"
            else -> "bin"
        }
    }

    private fun formatTime(epochMs: Long): String {
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss 'UTC'", Locale.US)
        format.timeZone = TimeZone.getTimeZone("UTC")
        return format.format(Date(epochMs))
    }

    private fun formatLocation(observation: ObservationEntity): String {
        val lat = observation.latitude
        val lng = observation.longitude
        if (lat == null || lng == null) return "not recorded"
        val accuracy = observation.locationAccuracyMeters?.let { " ±${it}m" } ?: ""
        val provider = observation.locationProvider?.let { " via $it" } ?: ""
        return "$lat, $lng$accuracy$provider (device-reported)"
    }
}
