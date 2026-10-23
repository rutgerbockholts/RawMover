package com.rutg3r.rawmover

import android.app.Application

class RawMoverApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Settings(this).initIfNeeded()   // first launch: only videos recorded from now on
        Notifier.createChannels(this)
        Scheduler.applyAll(this)
    }
}
