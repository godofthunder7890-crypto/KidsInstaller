package com.system.services

import android.app.Application

class KidsInstallerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashHandler.install(this)
    }
}
