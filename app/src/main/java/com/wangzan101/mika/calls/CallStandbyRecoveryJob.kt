package com.wangzan101.mika.calls

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.PowerManager
import android.util.Log
import com.wangzan101.mika.MikaApp

/**
 * Periodic backup for incoming standby when Android drops the sticky foreground-service restart
 * (ported from FlashPhone v1.1.14). It only re-requests the standby the owner already started;
 * it never asks for a permission, never replays outgoing work, and stays off after an explicit
 * stop or a full exit. The 15-minute interval is a request; Android may defer it (Doze, standby buckets).
 */
class CallStandbyRecoveryJob : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        // Runs on the main thread like the runtime. The service request is asynchronous, so
        // nothing is left for this job to hold on to.
        (application as MikaApp).calls.restoreAuthorizedStandbyFromSystem()
        return false
    }

    override fun onStopJob(params: JobParameters): Boolean = false

    companion object {
        private const val JOB_ID = 0x43414C52
        private const val INTERVAL_MS = 15 * 60 * 1_000L

        /**
         * Keeps the job scheduled while it can be useful: the owner's grant stands and they have
         * not stopped Mika. Battery and notification permission are checked when it runs, so a
         * permission that comes back re-arms standby without the app being opened.
         */
        internal fun reconcile(context: Context, authorized: Boolean) {
            runCatching {
                val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
                val wanted = CallStandbyPolicy.keepsRecoveryScheduled(
                    Build.VERSION.SDK_INT, authorized, ownerStopped = authorized && CallStandbyOwnerStop.stopped(context),
                )
                if (!wanted) scheduler.cancel(JOB_ID)
                else if (scheduler.getPendingJob(JOB_ID) == null) {
                    val info = JobInfo.Builder(JOB_ID, ComponentName(context, CallStandbyRecoveryJob::class.java))
                        .setPeriodic(INTERVAL_MS)
                        .setPersisted(true)
                        .build()
                    check(scheduler.schedule(info) == JobScheduler.RESULT_SUCCESS)
                }
            }.onFailure { Log.w("CallStandby", "Could not configure the standby recovery job", it) }
        }

        /** The owner opened Mika: an earlier stop no longer blocks recovery, and the schedule follows the grant. */
        internal fun onOwnerOpenedApp(app: MikaApp) {
            CallStandbyOwnerStop.of(app).opened(System.currentTimeMillis())
            // Only bring the call runtime up for owners who granted standby at some point.
            if (CallSettings.hasStoredStandbyAuthorization(app)) app.calls.refreshStandbyRecovery()
            else reconcile(app, authorized = false)
        }

        /** Battery-optimisation exemption with unrestricted background use is what lets a job start a foreground service. */
        internal fun systemAllowsScheduledStart(context: Context): Boolean = runCatching {
            context.getSystemService(ActivityManager::class.java)?.isBackgroundRestricted == false &&
                context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true
        }.getOrDefault(false)
    }
}

/**
 * Remembers that the owner stopped Mika from the system since they last opened it: a force stop,
 * or a task manager that reports its kill as user-requested and leaves jobs scheduled. Background
 * recovery stays off until the next open; the record outlives Android's rotating exit history.
 */
internal class CallStandbyOwnerStop(private val prefs: SharedPreferences) {
    fun opened(now: Long) {
        prefs.edit().putLong(LAST_OPENED, now).remove(STOPPED).apply()
    }

    /**
     * [exits] lists recent process exits as (reason, timestamp); with `newestOnly` it may return just
     * the latest. It returns null when Android cannot tell, which counts as stopped: unsure means no revival.
     */
    fun stopped(exits: (newestOnly: Boolean) -> List<Pair<Int, Long>>?): Boolean {
        if (prefs.getBoolean(STOPPED, false)) return true
        val lastOpened = prefs.getLong(LAST_OPENED, 0L)
        // Before the first recorded open, only the newest exit says whether this install was stopped.
        val recent = exits(lastOpened == 0L) ?: return true
        if (recent.none { (reason, at) -> explicitStopSinceOpen(reason, at, lastOpened) }) return false
        prefs.edit().putBoolean(STOPPED, true).apply()
        return true
    }

    companion object {
        private const val PREFS = "call_standby_recovery"
        private const val LAST_OPENED = "last_opened"
        private const val STOPPED = "user_stopped"

        fun of(context: Context) = CallStandbyOwnerStop(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE))

        /** Android 11+ only: older versions have no exit history, and Mika does not schedule recovery there. */
        fun stopped(context: Context): Boolean = Build.VERSION.SDK_INT >= 30 && of(context).stopped { newestOnly ->
            runCatching {
                checkNotNull(context.getSystemService(ActivityManager::class.java))
                    .getHistoricalProcessExitReasons(context.packageName, 0, if (newestOnly) 1 else 0)
                    .map { it.reason to it.timestamp }
            }.getOrNull()
        }

        fun explicitStopSinceOpen(reason: Int, exitedAt: Long, lastOpened: Long): Boolean =
            reason == ApplicationExitInfo.REASON_USER_REQUESTED && exitedAt >= lastOpened
    }
}
