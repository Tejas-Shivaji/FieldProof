package com.fieldproof.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        ObservationEntity::class,
        EvidenceEntity::class,
        ObservationVersionEntity::class,
        SyncMetaEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class FieldProofDatabase : RoomDatabase() {
    abstract fun observationDao(): ObservationDao
    abstract fun evidenceDao(): EvidenceDao
    abstract fun versionDao(): VersionDao
    abstract fun syncMetaDao(): SyncMetaDao
}
