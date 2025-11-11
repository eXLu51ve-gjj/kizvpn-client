package com.kizvpn.client

import android.app.Application

class KizVpnApplication : Application() {
    
    companion object {
        lateinit var instance: KizVpnApplication
            private set
    }
    
    override fun onCreate() {
        super.onCreate()
        instance = this
    }
}

