package com.fieldproof.app.data.local

import androidx.room.TypeConverter
import com.fieldproof.app.domain.model.SyncStatus
import com.fieldproof.app.domain.model.VersionSource

class Converters {
    @TypeConverter
    fun toSyncStatus(value: String): SyncStatus = SyncStatus.valueOf(value)

    @TypeConverter
    fun fromSyncStatus(value: SyncStatus): String = value.name

    @TypeConverter
    fun toVersionSource(value: String): VersionSource = VersionSource.valueOf(value)

    @TypeConverter
    fun fromVersionSource(value: VersionSource): String = value.name
}
