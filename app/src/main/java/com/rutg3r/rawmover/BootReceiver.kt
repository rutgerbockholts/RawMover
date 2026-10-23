package com.rutg3r.rawmover

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Content-trigger jobs can't be persisted, so re-arm after a reboot or app update. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> Scheduler.applyAll(context)
        }
    }
}
