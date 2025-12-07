package com.ubertracker.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.work.Configuration
import androidx.work.DelegatingWorkerFactory
import net.zetetic.database.sqlcipher.SQLiteDatabase

class UberTrackerApp : Application(), Configuration.Provider {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        scheduleBackgroundTasks()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(DelegatingWorkerFactory())
            .build()

    private fun createNotificationChannels() {
        val notificationManager = getSystemService(NotificationManager::class.java)

        val reminderChannel = NotificationChannel(
            "uber_tracker_reminders",
            "Daily Reminders",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Reminders to track your Uber rides"
            enableVibration(true)
        }

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