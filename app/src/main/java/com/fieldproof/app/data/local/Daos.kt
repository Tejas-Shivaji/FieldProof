package com.fieldproof.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ObservationDao {
    @Query("SELECT * FROM observations WHERE deleted = 0 ORDER BY capturedAtEpochMs DESC")
    fun observeAll(): Flow<List<ObservationEntity>>

    @Query("SELECT * FROM observations WHERE id = :id")
    fun observeById(id: String): Flow<ObservationEntity?>

    @Query("SELECT * FROM observations WHERE id = :id")
    suspend fun getById(id: String): ObservationEntity?

    @Query("SELECT * FROM observations WHERE deleted = 0")
    suspend fun getAll(): List<ObservationEntity>

    @Query(
        "SELECT * FROM observations WHERE syncStatus IN ('PENDING_UPLOAD','PENDING_UPDATE','FAILED') AND deleted = 0",
    )
    suspend fun pending(): List<ObservationEntity>

    @Query("SELECT * FROM observations WHERE syncStatus = 'CONFLICT' AND deleted = 0 ORDER BY updatedAtEpochMs DESC")
    fun observeConflicts(): Flow<List<ObservationEntity>>

    @Query("SELECT COUNT(*) FROM observations WHERE syncStatus = 'CONFLICT' AND deleted = 0")
    fun observeConflictCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: ObservationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ObservationEntity)

    @Update
    suspend fun update(entity: ObservationEntity)
}

@Dao
interface EvidenceDao {
    @Query("SELECT * FROM evidence WHERE observationId = :observationId LIMIT 1")
    fun observeForObservation(observationId: String): Flow<EvidenceEntity?>

    @Query("SELECT * FROM evidence WHERE observationId = :observationId LIMIT 1")
    suspend fun getForObservation(observationId: String): EvidenceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: EvidenceEntity)
}

@Dao
interface VersionDao {
    @Query("SELECT * FROM observation_versions WHERE observationId = :observationId ORDER BY recordedAtEpochMs DESC")
    fun observeForObservation(observationId: String): Flow<List<ObservationVersionEntity>>

    @Query("SELECT * FROM observation_versions WHERE observationId = :observationId ORDER BY recordedAtEpochMs DESC")
    suspend fun getForObservation(observationId: String): List<ObservationVersionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ObservationVersionEntity)
}

@Dao
interface SyncMetaDao {
    @Query("SELECT value FROM sync_meta WHERE key = :key")
    suspend fun get(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entity: SyncMetaEntity)
}
