package com.sierra.camblock

import android.app.Application

class CamShield: Application() {
    override fun onCreate() {
        super.onCreate()
//        ProcessLifecycleOwner.get().lifecycle.addObserver(AppLifecycleObserver(this))
    }
}