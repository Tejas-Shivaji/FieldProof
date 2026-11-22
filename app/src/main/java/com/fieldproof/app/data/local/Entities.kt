package com.fieldproof.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.fieldproof.app.domain.model.SyncStatus
import com.fieldproof.app.domain.model.VersionSource

@Entity(tableName = "observations")
data class ObservationEntity(
    @PrimaryKey val id: String,
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

@Entity(
    tableName = "evidence",
    foreignKeys = [
        ForeignKey(
            entity = ObservationEntity::class,
            parentColumns = ["id"],
            childColumns = ["observationId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("observationId")],
)
data class EvidenceEntity(
    @PrimaryKey val id: String,
    val observationId: String,
    val relativePath: String,
    val sha256Hex: String,
    val byteLength: Long,
    val mimeType: String,
    val hashedAtEpochMs: Long,
    val keystoreSignatureBase64: String?,
)

@Entity(
    tableName = "observation_versions",
    foreignKeys = [
        ForeignKey(
            entity = ObservationEntity::class,
            parentColumns = ["id"],
            childColumns = ["observationId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("observationId")],
)
data class ObservationVersionEntity(
    @PrimaryKey val id: String,
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

@Entity(tableName = "sync_meta")
data class SyncMetaEntity(
    @PrimaryKey val key: String,
    val value: String,
)
