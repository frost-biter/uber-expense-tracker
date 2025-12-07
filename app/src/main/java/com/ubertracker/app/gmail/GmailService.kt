package com.ubertracker.app.gmail

import android.content.Context
import android.content.Intent
import android.os.Environment
import android.util.Base64
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.HttpResponseException
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.gmail.Gmail
import com.google.api.services.gmail.GmailScopes
import com.ubertracker.app.data.Ride
import com.ubertracker.app.data.SecurePreferences
import com.ubertracker.app.gmail.parser.EmailParserFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class GmailService(private val context: Context) {

    private val prefs = SecurePreferences(context)
    private var gmailService: Gmail? = null

    companion object {
        private const val TAG = "GmailService"
    }

    fun isConnected(): Boolean {
        // Check if we have a signed-in account AND the service is initialized
        if (!prefs.isGmailConnected) {
            return false
        }

        // If preference says connected but service is null, try to restore it
        if (gmailService == null) {
            restoreGmailService()
        }

        return gmailService != null
    }

    private fun restoreGmailService() {
        try {
            // Check if there's already a signed-in account
            val account = GoogleSignIn.getLastSignedInAccount(context)
            if (account != null) {
                initializeGmailService(account)
            } else {
                prefs.isGmailConnected = false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore Gmail service", e)
            prefs.isGmailConnected = false
        }
    }

    fun getSignInIntent(): Intent {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(GmailScopes.GMAIL_READONLY))
            .build()
        val signInClient = GoogleSignIn.getClient(context, gso)
        return signInClient.signInIntent
    }

    suspend fun handleSignInResult(data: Intent?): Boolean = withContext(Dispatchers.IO) {
        try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.result
            if (account != null) {
                initializeGmailService(account)
                prefs.isGmailConnected = true
                return@withContext true
            }
            return@withContext false
        } catch (e: Exception) {
            Log.e(TAG, "Error handling sign-in result", e)
            return@withContext false
        }
    }

    private fun initializeGmailService(account: GoogleSignInAccount) {
        val credential = GoogleAccountCredential.usingOAuth2(
            context,
            listOf(GmailScopes.GMAIL_READONLY)
        )
        credential.selectedAccount = account.account

        gmailService = Gmail.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        )
            .setApplicationName("Uber Expense Tracker")
            .build()
    }

    suspend fun fetchUberReceipts(): List<Ride> = withContext(Dispatchers.IO) {
        val rides = mutableListOf<Ride>()

        try {
            if (gmailService == null) {
                restoreGmailService()
            }

            val gmail = gmailService ?: return@withContext rides

            val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
            val dateStr = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(Date(thirtyDaysAgo))

            val trustedSenderEmails = prefs.senderEmails
            val query = if (trustedSenderEmails.size == 1) {
                "from:${trustedSenderEmails.first()} after:$dateStr subject:(receipt OR trip OR business)"
            } else {
                val emailList = trustedSenderEmails.joinToString(" OR ")
                "from:($emailList) after:$dateStr subject:(receipt OR trip OR business)"
            }

            val listRequest = gmail.users().messages().list("me")
            listRequest.q = query
            listRequest.maxResults = 50

            val response = listRequest.execute()
            val messages = response.messages

            if (!messages.isNullOrEmpty()) {
                for (msg in messages) {
                    try {
                        val fullMessage = gmail.users().messages().get("me", msg.id).setFormat("full").execute()
                        val parser = EmailParserFactory.getParser(fullMessage, trustedSenderEmails.toList())

                        if (parser != null) {
                            val ride = parser.parseEmail(fullMessage)
                            if (ride != null) {
                                rides.add(ride)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing email ${msg.id}", e)
                    }
                }
            }

            prefs.lastSyncTimestamp = System.currentTimeMillis()

        } catch (e: UserRecoverableAuthIOException) {
            prefs.isGmailConnected = false
            gmailService = null
            throw AuthenticationException("Gmail access requires your consent. Please grant permission again.", e, e.intent)
        } catch (e: GoogleJsonResponseException) {
            if (e.statusCode == 401 || e.statusCode == 403) {
                prefs.isGmailConnected = false
                gmailService = null
                throw AuthenticationException("Gmail authentication failed. Please sign in again.", e)
            }
            Log.e(TAG, "Google API Error", e)
        } catch (e: HttpResponseException) {
            if (e.statusCode == 401 || e.statusCode == 403) {
                prefs.isGmailConnected = false
                gmailService = null
                throw AuthenticationException("Gmail authentication failed. Please sign in again.", e)
            }
            Log.e(TAG, "HTTP Error", e)
        } catch (e: Exception) {
            Log.e(TAG, "API Call Failed", e)
        }

        return@withContext rides
    }

    suspend fun downloadAttachment(messageId: String, attachmentId: String, filename: String): File? {
        return withContext(Dispatchers.IO) {
            try {
                if (gmailService == null) {
                    restoreGmailService()
                }

                val gmail = gmailService ?: return@withContext null

                val attachment = gmail.users().messages().attachments().get("me", messageId, attachmentId).execute()
                val fileBytes = Base64.decode(attachment.data, Base64.URL_SAFE)

                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val receiptsDir = File(downloadsDir, "UberTracker/Receipts")
                if (!receiptsDir.exists()) {
                    receiptsDir.mkdirs()
                }

                val file = File(receiptsDir, filename)

                FileOutputStream(file).use { it.write(fileBytes) }

                return@withContext file
            } catch (e: Exception) {
                Log.e(TAG, "Error downloading attachment", e)
                return@withContext null
            }
        }
    }

    fun isAttachmentUrl(url: String?): Boolean {
        return url != null && url.startsWith("attachment://")
    }

    fun parseAttachmentUrl(url: String): Triple<String, String, String>? {
        if (!isAttachmentUrl(url)) return null

        val parts = url.replace("attachment://", "").split("/")
        if (parts.size >= 3) {
            return Triple(parts[0], parts[1], parts[2])
        }
        return null
    }

    class AuthenticationException(
        message: String,
        cause: Throwable? = null,
        val intent: Intent? = null
    ) : Exception(message, cause)
}