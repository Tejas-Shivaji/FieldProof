package com.fieldproof.app.data.remote

import com.google.gson.annotations.SerializedName

data class ObservationPushRequest(
    val id: String,
    val title: String,
    val notes: String,
    @SerializedName("captured_at_epoch_ms") val capturedAtEpochMs: Long,
    val latitude: Double?,
    val longitude: Double?,
    @SerializedName("location_accuracy_meters") val locationAccuracyMeters: Float?,
    @SerializedName("location_provider") val locationProvider: String?,
    @SerializedName("local_revision") val localRevision: Long,
    @SerializedName("last_synced_revision") val lastSyncedRevision: Long?,
    val deleted: Boolean,
    val evidence: EvidencePushDto?,
)

data class EvidencePushDto(
    val id: String,
    @SerializedName("sha256_hex") val sha256Hex: String,
    @SerializedName("byte_length") val byteLength: Long,
    @SerializedName("mime_type") val mimeType: String,
)

data class ObservationResponse(
    val id: String,
    val title: String,
    val notes: String,
    @SerializedName("captured_at_epoch_ms") val capturedAtEpochMs: Long,
    val latitude: Double?,
    val longitude: Double?,
    @SerializedName("location_accuracy_meters") val locationAccuracyMeters: Float?,
    @SerializedName("location_provider") val locationProvider: String?,
    val revision: Long,
    @SerializedName("updated_at_epoch_ms") val updatedAtEpochMs: Long,
    val deleted: Boolean,
    val evidence: EvidenceResponse?,
)

data class EvidenceResponse(
    val id: String,
    @SerializedName("sha256_hex") val sha256Hex: String,
    @SerializedName("byte_length") val byteLength: Long,
    @SerializedName("mime_type") val mimeType: String,
)

data class ConflictResponse(
    val code: String,
    val server: ObservationResponse,
)

data class PullResponse(
    val observations: List<ObservationResponse>,
    @SerializedName("server_time_epoch_ms") val serverTimeEpochMs: Long,
)

data class HealthResponse(
    val status: String,
)
