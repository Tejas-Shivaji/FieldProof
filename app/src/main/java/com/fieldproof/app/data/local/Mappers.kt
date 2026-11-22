package com.fieldproof.app.data.local

import com.fieldproof.app.domain.model.Evidence
import com.fieldproof.app.domain.model.Observation
import com.fieldproof.app.domain.model.ObservationVersion

fun ObservationEntity.toDomain() = Observation(
    id = id,
    title = title,
    notes = notes,
    capturedAtEpochMs = capturedAtEpochMs,
    latitude = latitude,
    longitude = longitude,
    locationAccuracyMeters = locationAccuracyMeters,
    locationProvider = locationProvider,
    syncStatus = syncStatus,
    localRevision = localRevision,
    serverRevision = serverRevision,
    lastSyncedRevision = lastSyncedRevision,
    createdAtEpochMs = createdAtEpochMs,
    updatedAtEpochMs = updatedAtEpochMs,
    lastError = lastError,
    deleted = deleted,
)

fun EvidenceEntity.toDomain() = Evidence(
    id = id,
    observationId = observationId,
    relativePath = relativePath,
    sha256Hex = sha256Hex,
    byteLength = byteLength,
    mimeType = mimeType,
    hashedAtEpochMs = hashedAtEpochMs,
    keystoreSignatureBase64 = keystoreSignatureBase64,
)

fun ObservationVersionEntity.toDomain() = ObservationVersion(
    id = id,
    observationId = observationId,
    source = source,
    revision = revision,
    title = title,
    notes = notes,
    capturedAtEpochMs = capturedAtEpochMs,
    latitude = latitude,
    longitude = longitude,
    evidenceSha256Hex = evidenceSha256Hex,
    recordedAtEpochMs = recordedAtEpochMs,
)
