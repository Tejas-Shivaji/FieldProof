package com.fieldproof.app.di

import android.content.Context
import androidx.room.Room
import com.fieldproof.app.data.integrity.KeystoreSigner
import com.fieldproof.app.data.local.EvidenceFileStore
import com.fieldproof.app.data.local.FieldProofDatabase
import com.fieldproof.app.data.location.DeviceLocationReader
import com.fieldproof.app.data.repo.ObservationRepositoryImpl
import com.fieldproof.app.data.settings.SettingsRepositoryImpl
import com.fieldproof.app.data.sync.SyncManager
import com.fieldproof.app.data.sync.SyncScheduler

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database: FieldProofDatabase = Room.databaseBuilder(
        appContext,
        FieldProofDatabase::class.java,
        "fieldproof.db",
    ).build()

    val fileStore = EvidenceFileStore(appContext)
    val signer = KeystoreSigner()
    val locationReader = DeviceLocationReader(appContext)
    val settings = SettingsRepositoryImpl(appContext)
    val syncScheduler = SyncScheduler(appContext)

    val observations: ObservationRepositoryImpl = ObservationRepositoryImpl(
        db = database,
        files = fileStore,
        signer = signer,
        syncScheduler = syncScheduler,
    )

    val syncManager: SyncManager = SyncManager(
        db = database,
        files = fileStore,
        repository = observations,
        settings = settings,
    )
}
