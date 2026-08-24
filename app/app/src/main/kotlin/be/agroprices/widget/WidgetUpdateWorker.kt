package be.agroprices.widget

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import be.agroprices.widget.cache.WidgetPreferences
import be.agroprices.widget.data.BeefClient
import be.agroprices.widget.data.FegraClient
import be.agroprices.widget.data.PriceRepository
import be.agroprices.widget.ui.WidgetRenderer
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

private const val TAG = "AgroWidget"
private const val PERIODIC_WORK_NAME = "agro_widget_periodic_update"
private const val MANUAL_WORK_NAME = "agro_widget_manual_refresh"

/** fegra.be publishes at ~9:30; 10:00 leaves a safety margin. */
private const val DAILY_REFRESH_HOUR = 10

class WidgetUpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val repository = PriceRepository(
                fegraClient = FegraClient(),
                beefClient = BeefClient(Config.BEEF_JSON_URL),
                preferences = WidgetPreferences(applicationContext),
            )
            val snapshot = repository.refresh()
            WidgetRenderer.updateAllWidgets(applicationContext, snapshot)
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Widget-update volledig mislukt", e)
            Result.retry()
        }
    }

    companion object {

        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<WidgetUpdateWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(computeInitialDelayMillis(), TimeUnit.MILLISECONDS)
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(PERIODIC_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun requestManualRefresh(context: Context) {
            val request = OneTimeWorkRequestBuilder<WidgetUpdateWorker>()
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(MANUAL_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }

        private fun computeInitialDelayMillis(): Long {
            val now = LocalDateTime.now()
            var target = now.withHour(DAILY_REFRESH_HOUR).withMinute(0).withSecond(0).withNano(0)
            if (!target.isAfter(now)) {
                target = target.plusDays(1)
            }
            return Duration.between(now, target).toMillis()
        }
    }
}
