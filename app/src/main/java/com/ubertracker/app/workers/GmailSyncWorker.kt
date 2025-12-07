package com.ubertracker.app.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.ubertracker.app.data.RideDatabase
import com.ubertracker.app.data.SecurePreferences
import com.ubertracker.app.gmail.GmailService
import com.ubertracker.app.ui.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.concurrent.TimeUnit

class GmailSyncWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {

    private val workerInputData = workerParams.inputData

    companion object {
        private const val WORK_NAME_11_02 = "gmail_sync_11_02"
        private const val WORK_NAME_15_02 = "gmail_sync_15_02"
        private const val WORK_NAME_20_00 = "gmail_sync_20_00"

        const val WORK_NAME_11_02_PUBLIC = WORK_NAME_11_02
        const val WORK_NAME_15_02_PUBLIC = WORK_NAME_15_02
        const val WORK_NAME_20_00_PUBLIC = WORK_NAME_20_00

        fun scheduleDailySyncs(context: Context) {
            val workManager = WorkManager.getInstance(context)

            workManager.cancelUniqueWork(WORK_NAME_11_02)
            workManager.cancelUniqueWork(WORK_NAME_15_02)
            workManager.cancelUniqueWork(WORK_NAME_20_00)

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .setRequiresCharging(false)
                .build()

            scheduleSyncAtTime(context, workManager, WORK_NAME_11_02, 11, 1, constraints)
            scheduleSyncAtTime(context, workManager, WORK_NAME_15_02, 15, 5, constraints)
            scheduleSyncAtTime(context, workManager, WORK_NAME_20_00, 20, 0, constraints)
        }

        private fun scheduleSyncAtTime(
            context: Context,
            workManager: WorkManager,
            workName: String,
            hour: Int,
            minute: Int,
            constraints: Constraints
        ) {
            val calendar = Calendar.getInstance()
            val now = Calendar.getInstance()

            calendar.set(Calendar.HOUR_OF_DAY, hour)
            calendar.set(Calendar.MINUTE, minute)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)

            if (calendar.before(now) || calendar == now) {
                calendar.add(Calendar.DAY_OF_MONTH, 1)
            }

            val delayMillis = calendar.timeInMillis - now.timeInMillis
            val delayMinutes = TimeUnit.MILLISECONDS.toMinutes(delayMillis)

            val inputData = Data.Builder()
                .putString("work_name", workName)
                .putInt("hour", hour)
                .putInt("minute", minute)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<GmailSyncWorker>()
                .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .setInputData(inputData)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .addTag("gmail_sync")
                .build()

            workManager.enqueueUniqueWork(
                workName,
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
        }

        fun rescheduleNextDay(context: Context, workName: String, hour: Int, minute: Int) {
            val workManager = WorkManager.getInstance(context)
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .setRequiresCharging(false)
                .build()

            scheduleSyncAtTime(context, workManager, workName, hour, minute, constraints)
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val gmailService = GmailService(applicationContext)
            val database = RideDatabase.getDatabase(applicationContext)
            val rideDao = database.rideDao()

            if (!gmailService.isConnected()) {
                return@withContext Result.success()
            }

            val newRides = gmailService.fetchUberReceipts()

            var insertedCount = 0
            newRides.forEach { ride ->
                val existing = rideDao.getRideByTripId(ride.tripId)
                if (existing == null) {
                    rideDao.insertRide(ride)
                    insertedCount++
                }
            }

            val workName = workerInputData.getString("work_name") ?: ""
            if (workName.isNotEmpty()) {
                val hour = workerInputData.getInt("hour", -1)
                val minute = workerInputData.getInt("minute", -1)
                if (hour != -1 && minute != -1) {
                    rescheduleNextDay(applicationContext, workName, hour, minute)
                }
            }

            Result.success()

        } catch (e: GmailService.AuthenticationException) {
            val prefs = SecurePreferences(applicationContext)
            prefs.isGmailConnected = false

            val message = if (e.intent != null) {
                "Gmail access requires your consent. Tap to grant permission."
            } else {
                e.message ?: "Gmail authentication failed"
            }
            showAuthFailureNotification(applicationContext, message)

            WorkManager.getInstance(applicationContext).cancelUniqueWork(WORK_NAME_11_02)
            WorkManager.getInstance(applicationContext).cancelUniqueWork(WORK_NAME_15_02)
            WorkManager.getInstance(applicationContext).cancelUniqueWork(WORK_NAME_20_00)

            Result.success()

        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun showAuthFailureNotification(context: Context, message: String) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val channelId = "uber_tracker_sync"
            val channel = NotificationChannel(
                channelId,
                "Sync Updates",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications about Gmail sync status"
            }
            notificationManager.createNotificationChannel(channel)

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val notification = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("Gmail Sync Failed")
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText("Please reconnect your Gmail account to continue syncing."))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

            notificationManager.notify(1001, notification)
        } catch (e: Exception) {
            // Failed to show notification, but we can't do much here
        }
    }
}