package com.fieldproof.app.data.sync

import com.fieldproof.app.domain.model.Observation

/**
 * Pure conflict classifier. Device clocks are never used as the conflict key.
 *
 * localDirty  = localRevision > (lastSyncedRevision ?: 0)
 * remoteChanged = remote.revision > (lastSyncedRevision ?: 0)
 *
 * Both true → CONFLICT. Neither version is discarded here.
 */
sealed class MergeDecision {
    data object InSync : MergeDecision()
    data class PushLocal(val local: Observation) : MergeDecision()
    data class FastForwardRemote(val remote: RemoteObservation) : MergeDecision()
    data class InsertRemote(val remote: RemoteObservation) : MergeDecision()
    data class Conflict(val local: Observation, val remote: RemoteObservation) : MergeDecision()
}

data class RemoteObservation(
    val id: String,
    val title: String,
    val notes: String,
    val capturedAtEpochMs: Long,
    val latitude: Double?,
    val longitude: Double?,
    val locationAccuracyMeters: Float?,
    val locationProvider: String?,
    val revision: Long,
    val updatedAtEpochMs: Long,
    val evidenceId: String?,
    val evidenceSha256Hex: String?,
    val evidenceByteLength: Long?,
    val evidenceMimeType: String?,
    val deleted: Boolean,
)

object ConflictMerger {
    fun decide(local: Observation?, remote: RemoteObservation?): MergeDecision {
        require(local != null || remote != null) { "both sides missing" }
        if (local == null && remote != null) return MergeDecision.InsertRemote(remote)
        if (local != null && remote == null) return MergeDecision.PushLocal(local)

        checkNotNull(local)
        checkNotNull(remote)
        val base = local.lastSyncedRevision ?: 0L
        val localDirty = local.localRevision > base
        val remoteChanged = remote.revision > base
        return when {
            !localDirty && !remoteChanged -> MergeDecision.InSync
            localDirty && !remoteChanged -> MergeDecision.PushLocal(local)
            !localDirty && remoteChanged -> MergeDecision.FastForwardRemote(remote)
            else -> MergeDecision.Conflict(local, remote)
        }
    }
}
