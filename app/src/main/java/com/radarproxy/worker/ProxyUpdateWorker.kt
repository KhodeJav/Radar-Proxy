package com.radarproxy.worker

import android.content.Context
import androidx.room.Room
import androidx.work.*
import com.radarproxy.data.local.ProxyDatabase
import com.radarproxy.data.repository.ProxyRepository
import java.util.Calendar
import java.util.concurrent.TimeUnit

class ProxyUpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val db = Room.databaseBuilder(applicationContext, ProxyDatabase::class.java, "radar_proxy.db").build()
        return try {
            ProxyRepository(db).refreshAll().fold({ Result.success() }, { Result.retry() })
        } finally {
            db.close()
        }
    }

    companion object {
        private const val WORK_NAME = "radar_proxy_refresh"

        fun schedule(context: Context, minutes: Long, wifiOnly: Boolean, enabled: Boolean = true) {
            val manager = WorkManager.getInstance(context)
            if (!enabled) {
                manager.cancelUniqueWork(WORK_NAME)
                return
            }
            val network = if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
            val constraints = Constraints.Builder().setRequiredNetworkType(network).build()
            val request = PeriodicWorkRequestBuilder<ProxyUpdateWorker>(minutes.coerceIn(15L, 60L), TimeUnit.MINUTES)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            manager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}

class ProxyCleanupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val db = Room.databaseBuilder(applicationContext, ProxyDatabase::class.java, "radar_proxy.db").build()
        return try {
            val hours = inputData.getLong(KEY_HOURS, 24L).let { if (it in VALID_HOURS) it else 24L }
            val cutoff = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(hours)
            db.proxies().deleteOlderThan(cutoff)
            Result.success()
        } finally {
            db.close()
        }
    }

    companion object {
        private const val WORK_NAME = "radar_proxy_cleanup"
        private const val KEY_HOURS = "hours"
        private val VALID_HOURS = setOf(4L, 8L, 12L, 24L)

        fun schedule(context: Context, enabled: Boolean, hours: Long) {
            val manager = WorkManager.getInstance(context)
            if (!enabled) {
                manager.cancelUniqueWork(WORK_NAME)
                return
            }
            val safeHours = hours.takeIf { it in VALID_HOURS } ?: 24L
            val request = PeriodicWorkRequestBuilder<ProxyCleanupWorker>(safeHours, TimeUnit.HOURS)
                .setInputData(workDataOf(KEY_HOURS to safeHours))
                .setInitialDelay(delayToNextLocalBoundary(safeHours), TimeUnit.MILLISECONDS)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            manager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        private fun delayToNextLocalBoundary(hours: Long): Long {
            val now = Calendar.getInstance()
            val next = (now.get(Calendar.HOUR_OF_DAY) / hours * hours + hours).toInt()
            val target = (now.clone() as Calendar).apply {
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (next >= 24) {
                    add(Calendar.DAY_OF_YEAR, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                } else {
                    set(Calendar.HOUR_OF_DAY, next)
                }
            }
            return (target.timeInMillis - now.timeInMillis).coerceAtLeast(1_000L)
        }
    }
}
