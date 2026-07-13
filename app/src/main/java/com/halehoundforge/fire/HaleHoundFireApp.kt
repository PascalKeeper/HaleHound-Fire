package com.halehoundforge.fire

import android.app.Application

class HaleHoundFireApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: HaleHoundFireApp
            private set
    }
}
