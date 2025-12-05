package com.ubertracker.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecurePreferences(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "uber_tracker_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    companion object {
        private const val KEY_GMAIL_TOKEN = "gmail_token"
        private const val KEY_GMAIL_REFRESH_TOKEN = "gmail_refresh_token"
        private const val KEY_GMAIL_CONNECTED = "gmail_connected"
        private const val KEY_LAST_SYNC = "last_sync"
        private const val KEY_REMINDER_ENABLED = "reminder_enabled"
        private const val KEY_REMINDER_MORNING = "reminder_morning_time"
        private const val KEY_REMINDER_EVENING = "reminder_evening_time"
        private const val KEY_SENDER_EMAIL = "sender_email"
    }

    var gmailToken: String?
        get() = sharedPreferences.getString(KEY_GMAIL_TOKEN, null)
        set(value) = sharedPreferences.edit().putString(KEY_GMAIL_TOKEN, value).apply()

    var gmailRefreshToken: String?
        get() = sharedPreferences.getString(KEY_GMAIL_REFRESH_TOKEN, null)
        set(value) = sharedPreferences.edit().putString(KEY_GMAIL_REFRESH_TOKEN, value).apply()

    var isGmailConnected: Boolean
        get() = sharedPreferences.getBoolean(KEY_GMAIL_CONNECTED, false)
        set(value) = sharedPreferences.edit().putBoolean(KEY_GMAIL_CONNECTED, value).apply()

    var lastSyncTimestamp: Long
        get() = sharedPreferences.getLong(KEY_LAST_SYNC, 0L)
        set(value) = sharedPreferences.edit().putLong(KEY_LAST_SYNC, value).apply()

    var reminderEnabled: Boolean
        get() = sharedPreferences.getBoolean(KEY_REMINDER_ENABLED, true)
        set(value) = sharedPreferences.edit().putBoolean(KEY_REMINDER_ENABLED, value).apply()

    var reminderMorningTime: String
        get() = sharedPreferences.getString(KEY_REMINDER_MORNING, "09:00") ?: "09:00"
        set(value) = sharedPreferences.edit().putString(KEY_REMINDER_MORNING, value).apply()

    var reminderEveningTime: String
        get() = sharedPreferences.getString(KEY_REMINDER_EVENING, "18:00") ?: "18:00"
        set(value) = sharedPreferences.edit().putString(KEY_REMINDER_EVENING, value).apply()

    var senderEmail: String
        get() = sharedPreferences.getString(KEY_SENDER_EMAIL, "no-reply@uber.com") ?: "no-reply@uber.com"
        set(value) = sharedPreferences.edit().putString(KEY_SENDER_EMAIL, value).apply()

    fun clearGmailCredentials() {
        sharedPreferences.edit()
            .remove(KEY_GMAIL_TOKEN)
            .remove(KEY_GMAIL_REFRESH_TOKEN)
            .putBoolean(KEY_GMAIL_CONNECTED, false)
            .apply()
    }

    fun clearAll() {
        sharedPreferences.edit().clear().apply()
    }
}