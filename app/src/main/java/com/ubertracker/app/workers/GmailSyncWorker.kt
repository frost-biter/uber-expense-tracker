package com.ubertracker.app.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
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
    
    // Store inputData for access in doWork()
    private val workerInputData = workerParams.inputData

    companion object {
        private const val WORK_NAME_11_02 = "gmail_sync_11_02"
        private const val WORK_NAME_15_02 = "gmail_sync_15_02"
        private const val WORK_NAME_20_00 = "gmail_sync_20_00"
        
        // Make these accessible for cancellation
        const val WORK_NAME_11_02_PUBLIC = WORK_NAME_11_02
        const val WORK_NAME_15_02_PUBLIC = WORK_NAME_15_02
        const val WORK_NAME_20_00_PUBLIC = WORK_NAME_20_00
        
        /**
         * Schedule Gmail sync at 11:02 AM, 3:02 PM, and 8:00 PM daily
         * Uses battery-efficient constraints and OneTimeWorkRequest for exact timing
         */
        fun scheduleDailySyncs(context: Context) {
            val workManager = WorkManager.getInstance(context)
            
            // Cancel existing work to avoid duplicates
            workManager.cancelUniqueWork(WORK_NAME_11_02)
            workManager.cancelUniqueWork(WORK_NAME_15_02)
            workManager.cancelUniqueWork(WORK_NAME_20_00)
            
            // Battery-efficient constraints
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED) // Only sync when connected
                .setRequiresBatteryNotLow(true) // Don't sync if battery is low
                .setRequiresCharging(false) // Can run without charging (more flexible)
                .build()
            
            // Schedule 11:02 AM sync
            scheduleSyncAtTime(context, workManager, WORK_NAME_11_02, 11, 1, constraints)
            
            // Schedule 3:02 PM (15:02) sync
            scheduleSyncAtTime(context, workManager, WORK_NAME_15_02, 15, 5, constraints)
            
            // Schedule 8:00 PM (20:00) sync
            scheduleSyncAtTime(context, workManager, WORK_NAME_20_00, 20, 0, constraints)
            
            Log.d("GmailSyncWorker", "✅ Scheduled daily Gmail syncs at 11:02 AM, 3:02 PM, and 8:00 PM")
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
            
            // Set target time
            calendar.set(Calendar.HOUR_OF_DAY, hour)
            calendar.set(Calendar.MINUTE, minute)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            
            // If the time has passed today, schedule for tomorrow
            if (calendar.before(now) || calendar == now) {
                calendar.add(Calendar.DAY_OF_MONTH, 1)
            }
            
            // Calculate delay in milliseconds
            val delayMillis = calendar.timeInMillis - now.timeInMillis
            val delayMinutes = TimeUnit.MILLISECONDS.toMinutes(delayMillis)
            
            // Create work request with exponential backoff for retries
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
            
            // Enqueue as unique work (replaces existing work with same name)
            workManager.enqueueUniqueWork(
                workName,
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
            
            val minuteStr = minute.toString().padStart(2, '0')
            Log.d("GmailSyncWorker", "📅 Scheduled sync at $hour:$minuteStr (in $delayMinutes minutes)")
        }
        
        /**
         * Reschedule this worker for the next day at the same time
         * Called after successful sync to maintain daily schedule
         */
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
            Log.d("GmailSyncWorker", "🔄 Starting scheduled Gmail sync...")
            
            // Initialize services
            val gmailService = GmailService(applicationContext)
            val database = RideDatabase.getDatabase(applicationContext)
            val rideDao = database.rideDao()
            
            // Check if Gmail is connected
            if (!gmailService.isConnected()) {
                Log.w("GmailSyncWorker", "⚠️ Gmail not connected. Skipping sync.")
                return@withContext Result.success() // Return success to avoid retries
            }
            
            // Fetch new rides from Gmail
            val newRides = gmailService.fetchUberReceipts()
            
            // Insert new rides (skip duplicates)
            var insertedCount = 0
            newRides.forEach { ride ->
                val existing = rideDao.getRideByTripId(ride.tripId)
                if (existing == null) {
                    rideDao.insertRide(ride)
                    insertedCount++
                }
            }
            
            Log.d("GmailSyncWorker", "✅ Sync completed. Inserted $insertedCount new rides.")
            
            // Reschedule for next day at the same time
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
            // Authentication failed - notify user and don't retry
            Log.w("GmailSyncWorker", "⚠️ Gmail authentication failed", e)
            
            // Update connection state
            val prefs = SecurePreferences(applicationContext)
            prefs.isGmailConnected = false
            
            // Show notification to user
            val message = if (e.intent != null) {
                "Gmail access requires your consent. Tap to grant permission."
            } else {
                e.message ?: "Gmail authentication failed"
            }
            showAuthFailureNotification(applicationContext, message)
            
            // Cancel scheduled syncs since auth is required
            WorkManager.getInstance(applicationContext).cancelUniqueWork(WORK_NAME_11_02)
            WorkManager.getInstance(applicationContext).cancelUniqueWork(WORK_NAME_15_02)
            WorkManager.getInstance(applicationContext).cancelUniqueWork(WORK_NAME_20_00)
            
            // Return success to avoid retries (user needs to manually reconnect)
            Result.success()
            
        } catch (e: Exception) {
            Log.e("GmailSyncWorker", "❌ Error during Gmail sync", e)
            // Use exponential backoff for retries (WorkManager handles this)
            Result.retry()
        }
    }
    
    private fun showAuthFailureNotification(context: Context, message: String) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // Create notification channel if it doesn't exist
            val channelId = "uber_tracker_sync"
            val channel = NotificationChannel(
                channelId,
                "Sync Updates",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications about Gmail sync status"
            }
            notificationManager.createNotificationChannel(channel)

            // Create intent to open app
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            
            // Build notification
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
            Log.e("GmailSyncWorker", "Failed to show notification", e)
        }
    }
}