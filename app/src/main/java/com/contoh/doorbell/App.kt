package com.contoh.doorbell

import android.app.Application

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLogger.pasang(this)
    }
}
