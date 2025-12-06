package com.ubertracker.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.core.content.edit

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
        set(value) = sharedPreferences.edit { putString(KEY_GMAIL_TOKEN, value) }

    var gmailRefreshToken: String?
        get() = sharedPreferences.getString(KEY_GMAIL_REFRESH_TOKEN, null)
        set(value) = sharedPreferences.edit { putString(KEY_GMAIL_REFRESH_TOKEN, value) }

    var isGmailConnected: Boolean
        get() = sharedPreferences.getBoolean(KEY_GMAIL_CONNECTED, false)
        set(value) = sharedPreferences.edit { putBoolean(KEY_GMAIL_CONNECTED, value) }

    var lastSyncTimestamp: Long
        get() = sharedPreferences.getLong(KEY_LAST_SYNC, 0L)
        set(value) = sharedPreferences.edit { putLong(KEY_LAST_SYNC, value) }

    var reminderEnabled: Boolean
        get() = sharedPreferences.getBoolean(KEY_REMINDER_ENABLED, true)
        set(value) = sharedPreferences.edit { putBoolean(KEY_REMINDER_ENABLED, value) }

    var reminderMorningTime: String
        get() = sharedPreferences.getString(KEY_REMINDER_MORNING, "09:00") ?: "09:00"
        set(value) = sharedPreferences.edit { putString(KEY_REMINDER_MORNING, value) }

    var reminderEveningTime: String
        get() = sharedPreferences.getString(KEY_REMINDER_EVENING, "18:00") ?: "18:00"
        set(value) = sharedPreferences.edit { putString(KEY_REMINDER_EVENING, value) }

    // Legacy single email support - kept for backward compatibility
    var senderEmail: String
        get() = senderEmails.firstOrNull() ?: "noreply@uber.com"
        set(value) {
            // If setting a single email, replace the list with just this one
            senderEmails = if (value.isNotBlank()) listOf(value.trim()) else listOf("noreply@uber.com")
        }
    
    // New: List of sender emails
    var senderEmails: List<String>
        get() {
            val emailsString = sharedPreferences.getString(KEY_SENDER_EMAIL, null)
            return if (emailsString.isNullOrBlank()) {
                // Default to single email for backward compatibility
                listOf("noreply@uber.com")
            } else {
                // Split by comma and filter out empty strings
                emailsString.split(",")
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .ifEmpty { listOf("noreply@uber.com") }
            }
        }
        set(value) {
            val emailsString = value
                .filter { it.isNotBlank() }
                .map { it.trim() }
                .distinct()
                .joinToString(",")
            sharedPreferences.edit { 
                putString(KEY_SENDER_EMAIL, if (emailsString.isBlank()) "noreply@uber.com" else emailsString)
            }
        }

    fun clearGmailCredentials() {
        sharedPreferences.edit {
            remove(KEY_GMAIL_TOKEN)
                .remove(KEY_GMAIL_REFRESH_TOKEN)
                .putBoolean(KEY_GMAIL_CONNECTED, false)
        }
    }

    fun clearAll() {
        sharedPreferences.edit { clear() }
    }
}