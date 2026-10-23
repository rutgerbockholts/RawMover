package com.rutg3r.rawmover

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.provider.MediaStore
import java.util.concurrent.TimeUnit

object Scheduler {
    private const val WATCH_JOB_ID = 1001
    private const val SWEEP_JOB_ID = 1002
    private val SWEEP_INTERVAL_MS = TimeUnit.HOURS.toMillis(3)

    /** Arms (or disarms) everything according to the enabled setting. */
    fun applyAll(context: Context) {
        if (Settings(context).enabled) {
            scheduleWatch(context)
            scheduleSweep(context)
        } else {
            cancelAll(context)
        }
    }

    /** One-shot job that fires on the next change in the video collection. */
    fun scheduleWatch(context: Context) {
        if (!Settings(context).enabled) return
        val job = JobInfo.Builder(WATCH_JOB_ID, ComponentName(context, MediaWatchJob::class.java))
            .addTriggerContentUri(
                JobInfo.TriggerContentUri(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS
                )
            )
            .setTriggerContentUpdateDelay(5_000)   // let the burst of index updates settle
            .setTriggerContentMaxDelay(60_000)
            .build()
        scheduler(context).schedule(job)
    }

    /** Periodic sweep; survives reboots. */
    fun scheduleSweep(context: Context) {
        val js = scheduler(context)
        if (js.getPendingJob(SWEEP_JOB_ID) != null) return
        val job = JobInfo.Builder(SWEEP_JOB_ID, ComponentName(context, SweepJob::class.java))
            .setPeriodic(SWEEP_INTERVAL_MS)
            .setPersisted(true)
            .build()
        js.schedule(job)
    }

    fun cancelAll(context: Context) {
        scheduler(context).cancel(WATCH_JOB_ID)
        scheduler(context).cancel(SWEEP_JOB_ID)
    }

    fun isWatchArmed(context: Context): Boolean =
        scheduler(context).getPendingJob(WATCH_JOB_ID) != null

    private fun scheduler(context: Context) =
        context.getSystemService(JobScheduler::class.java)
}
