package com.rutg3r.rawmover

import android.app.job.JobParameters
import android.app.job.JobService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Periodic safety net: catches anything a missed content trigger let through
 * (e.g. when the battery manager deferred the watch job), well before the nightly sync.
 * Also re-arms the watch job in case it got lost.
 */
class SweepJob : JobService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var work: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        Scheduler.scheduleWatch(applicationContext)
        work = scope.launch {
            try {
                RawMover(applicationContext).processNew()
            } finally {
                jobFinished(params, false)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        work?.cancel()
        return true
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
