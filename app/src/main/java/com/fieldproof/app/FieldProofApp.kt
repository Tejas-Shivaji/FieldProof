package com.fieldproof.app

import android.app.Application
import com.fieldproof.app.di.AppContainer

class FieldProofApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
