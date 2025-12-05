package com.ubertracker.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.work.Configuration
import androidx.work.DelegatingWorkerFactory
import androidx.work.WorkManager
import net.sqlcipher.database.SQLiteDatabase

class UberTrackerApp : Application(), Configuration.Provider {

    override fun onCreate() {
        super.onCreate()

        // Load SQLCipher native libraries (if not already loaded)
        try {
            SQLiteDatabase.loadLibs(this)
        } catch (e: Exception) {
            // Libraries might already be loaded or loading failed
            // SQLCipher 4.x should auto-load, so this is just a safety measure
            android.util.Log.w("UberTrackerApp", "SQLCipher library loading: ${e.message}")
        }

        // Initialize WorkManager
        WorkManager.initialize(this, workManagerConfiguration)

        // Create notification channels
        createNotificationChannels()

        // Schedule background tasks
        scheduleBackgroundTasks()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .setWorkerFactory(DelegatingWorkerFactory())
            .build()

    private fun createNotificationChannels() {
        val notificationManager = getSystemService(NotificationManager::class.java)

        // Reminders Channel
        val reminderChannel = NotificationChannel(
            "uber_tracker_reminders",
            "Daily Reminders",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Reminders to track your Uber rides"
            enableVibration(true)
        }

        // Sync Updates Channel
        val syncChannel = NotificationChannel(
            "uber_tracker_sync",
            "Sync Updates",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Notifications about Gmail sync status"
        }

        notificationManager.createNotificationChannel(reminderChannel)
        notificationManager.createNotificationChannel(syncChannel)
    }

    private fun scheduleBackgroundTasks() {
        // Schedule daily reminders
        //ReminderWorker.scheduleReminders(this)

        // Schedule periodic Gmail sync
        //GmailSyncWorker.schedulePeriodicSync(this)
    }
}