package com.fieldproof.app.data.sync

import com.fieldproof.app.domain.model.Observation
import com.fieldproof.app.domain.model.SyncStatus
import org.junit.Assert.assertTrue
import org.junit.Test

class ConflictMergerTest {
    @Test
    fun neverSyncedLocal_pushes() {
        val local = sample(localRevision = 1, lastSynced = null)
        val decision = ConflictMerger.decide(local, remote = null)
        assertTrue(decision is MergeDecision.PushLocal)
    }

    @Test
    fun remoteOnly_inserts() {
        val decision = ConflictMerger.decide(local = null, remote = remote(revision = 1))
        assertTrue(decision is MergeDecision.InsertRemote)
    }

    @Test
    fun localDirtyRemoteUnchanged_pushes() {
        val local = sample(localRevision = 2, lastSynced = 1, serverRevision = 1)
        val decision = ConflictMerger.decide(local, remote(revision = 1))
        assertTrue(decision is MergeDecision.PushLocal)
    }

    @Test
    fun localCleanRemoteAdvanced_fastForwards() {
        val local = sample(localRevision = 1, lastSynced = 1, serverRevision = 1, status = SyncStatus.SYNCED)
        val decision = ConflictMerger.decide(local, remote(revision = 2, notes = "bridge OK"))
        assertTrue(decision is MergeDecision.FastForwardRemote)
    }

    @Test
    fun bothChanged_preservesConflict_doesNotPickAWinner() {
        val local = sample(
            localRevision = 2,
            lastSynced = 1,
            serverRevision = 1,
            notes = "bridge damaged",
        )
        val decision = ConflictMerger.decide(local, remote(revision = 2, notes = "bridge OK"))
        assertTrue(decision is MergeDecision.Conflict)
        val conflict = decision as MergeDecision.Conflict
        assertTrue(conflict.local.notes == "bridge damaged")
        assertTrue(conflict.remote.notes == "bridge OK")
    }

    @Test
    fun identicalRevisions_inSync() {
        val local = sample(localRevision = 3, lastSynced = 3, serverRevision = 3, status = SyncStatus.SYNCED)
        val decision = ConflictMerger.decide(local, remote(revision = 3))
        assertTrue(decision is MergeDecision.InSync)
    }

    private fun sample(
        localRevision: Long,
        lastSynced: Long?,
        serverRevision: Long? = lastSynced,
        notes: String = "bridge damaged",
        status: SyncStatus = SyncStatus.PENDING_UPDATE,
    ) = Observation(
        id = "obs-1",
        title = "Bridge",
        notes = notes,
        capturedAtEpochMs = 1_000L,
        latitude = 1.0,
        longitude = 2.0,
        locationAccuracyMeters = 8f,
        locationProvider = "gps",
        syncStatus = status,
        localRevision = localRevision,
        serverRevision = serverRevision,
        lastSyncedRevision = lastSynced,
        createdAtEpochMs = 1_000L,
        updatedAtEpochMs = 2_000L,
        lastError = null,
        deleted = false,
    )

    private fun remote(revision: Long, notes: String = "bridge OK") = RemoteObservation(
        id = "obs-1",
        title = "Bridge",
        notes = notes,
        capturedAtEpochMs = 1_000L,
        latitude = 1.0,
        longitude = 2.0,
        locationAccuracyMeters = 8f,
        locationProvider = "gps",
        revision = revision,
        updatedAtEpochMs = 3_000L,
        evidenceId = "ev-1",
        evidenceSha256Hex = "abc",
        evidenceByteLength = 12,
        evidenceMimeType = "image/jpeg",
        deleted = false,
    )
}
