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
 * Woken by Android when the video collection in the media index changes (a recording finished).
 * Content-URI triggers fire only once, so the job re-arms itself after every run.
 */
class MediaWatchJob : JobService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var work: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        work = scope.launch {
            try {
                RawMover(applicationContext).processNew()
            } finally {
                jobFinished(params, false)
                Scheduler.scheduleWatch(applicationContext)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        work?.cancel()
        Scheduler.scheduleWatch(applicationContext)
        return false
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
