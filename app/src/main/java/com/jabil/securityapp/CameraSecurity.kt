package com.jabil.securityapp

import android.app.Application

class CameraSecurity: Application() {
    override fun onCreate() {
        super.onCreate()
//        ProcessLifecycleOwner.get().lifecycle.addObserver(AppLifecycleObserver(this))
    }
}