package com.yadavard.app

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Every 15 minutes or so, when there is internet: the cloud copy and the groups catch up, so a reminder a friend
 * added rings here even if the app was not opened. Runs only while one of them is turned on.
 */
class SyncWork(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        runCatching { CloudSync.now(applicationContext) }
        runCatching { Groups.syncNow(applicationContext) }
        Result.success()
    }

    companion object {
        private const val NAME = "yadimo_sync"

        /** Starts or stops the background catch-up to match the settings. */
        fun ensure(context: Context) {
            val wm = WorkManager.getInstance(context.applicationContext)
            if (!CloudSync.on(context) && !(Groups.enabled(context) && Account.isLoggedIn(context))) { wm.cancelUniqueWork(NAME); return }
            wm.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<SyncWork>(15, TimeUnit.MINUTES)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build())
        }
    }
}
