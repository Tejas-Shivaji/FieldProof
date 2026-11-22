package com.fieldproof.app.domain.model

/**
 * Device-reported location. Not an independently verified fact.
 */
data class LocationFix(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float?,
    val recordedAtEpochMs: Long,
    val provider: String?,
)

enum class SyncStatus {
    PENDING_UPLOAD,
    PENDING_UPDATE,
    SYNCING,
    SYNCED,
    CONFLICT,
    FAILED,
}

enum class VersionSource {
    LOCAL,
    SERVER,
}

enum class IntegrityResult {
    MATCH,
    MISMATCH,
    MISSING_FILE,
    NOT_HASHED,
}

data class Observation(
    val id: String,
    val title: String,
    val notes: String,
    val capturedAtEpochMs: Long,
    val latitude: Double?,
    val longitude: Double?,
    val locationAccuracyMeters: Float?,
    val locationProvider: String?,
    val syncStatus: SyncStatus,
    val localRevision: Long,
    val serverRevision: Long?,
    val lastSyncedRevision: Long?,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val lastError: String?,
    val deleted: Boolean,
)

data class Evidence(
    val id: String,
    val observationId: String,
    val relativePath: String,
    val sha256Hex: String,
    val byteLength: Long,
    val mimeType: String,
    val hashedAtEpochMs: Long,
    val keystoreSignatureBase64: String?,
)

data class ObservationVersion(
    val id: String,
    val observationId: String,
    val source: VersionSource,
    val revision: Long,
    val title: String,
    val notes: String,
    val capturedAtEpochMs: Long,
    val latitude: Double?,
    val longitude: Double?,
    val evidenceSha256Hex: String?,
    val recordedAtEpochMs: Long,
)

data class ObservationDetail(
    val observation: Observation,
    val evidence: Evidence?,
    val versions: List<ObservationVersion>,
    val integrity: IntegrityResult?,
    val signatureValid: Boolean?,
)

data class VerificationReport(
    val generatedAtEpochMs: Long,
    val text: String,
)

data class CreateObservationInput(
    val title: String,
    val notes: String,
    val photoBytesSource: java.io.File,
    val mimeType: String,
    val location: LocationFix?,
)
