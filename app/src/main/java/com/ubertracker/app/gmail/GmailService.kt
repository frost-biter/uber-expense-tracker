package com.ubertracker.app.gmail

import android.content.Context
import android.content.Intent
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
import com.google.api.services.gmail.model.Message
import com.ubertracker.app.data.Ride
import com.ubertracker.app.data.SecurePreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.text.SimpleDateFormat
import java.util.*

class GmailService(private val context: Context) {

    private val prefs = SecurePreferences(context)
    private var gmailService: Gmail? = null

    companion object {
        private const val BUSINESS_KEYWORD = "business"
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
                Log.d("GmailService", "✅ Restored Gmail service from existing sign-in")
            } else {
                Log.w("GmailService", "⚠️ No signed-in account found, but preference says connected")
                prefs.isGmailConnected = false
            }
        } catch (e: Exception) {
            Log.e("GmailService", "❌ Failed to restore Gmail service", e)
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
            e.printStackTrace()
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
        val TAG = "GmailDebug"
        try {
            // Try to restore service if null
            if (gmailService == null) {
                restoreGmailService()
            }
            
            val gmail = gmailService
            if (gmail == null) {
                Log.e(TAG, "❌ FAILURE: Gmail Service is NULL. User not signed in properly.")
                Log.e(TAG, "   -> Try clicking 'Connect' button to sign in again.")
                return@withContext rides
            }
            Log.d(TAG, "✅ SUCCESS: Gmail Service is initialized. Starting search...")
            
            // Query for emails from configured sender address from last 30 days
            val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
            val dateStr = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(Date(thirtyDaysAgo))

            // Build query using configured sender emails (supports multiple)
            val senderEmails = prefs.senderEmails
            val query = if (senderEmails.size == 1) {
                // Single email - simple query
                "from:${senderEmails.first()} after:$dateStr subject:(receipt OR trip OR business)"
            } else {
                // Multiple emails - use OR syntax: from:(email1 OR email2 OR email3)
                val emailList = senderEmails.joinToString(" OR ")
                "from:($emailList) after:$dateStr subject:(receipt OR trip OR business)"
            }

            Log.d(TAG, "🔍 EXECUTING QUERY: $query")
            Log.d(TAG, "📧 Searching for emails from: ${senderEmails.joinToString(", ")}")

            // Execute search
            val listRequest = gmail.users().messages().list("me")
            listRequest.q = query
            listRequest.maxResults = 50 // Increased for production use

            val response = listRequest.execute()
            val messages = response.messages

            // Process found messages
            if (messages.isNullOrEmpty()) {
                Log.w(TAG, "⚠️ RESULT: 0 Emails found matching the query.")
                Log.w(TAG, "   -> Query: $query")
                Log.w(TAG, "   -> Check if you have emails from '${senderEmails.joinToString(", ")}' in the last 30 days.")
            } else {
                Log.d(TAG, "🎉 RESULT: Found ${messages.size} emails! Processing...")

                // Parse each email and extract ride information
                for (msg in messages) {
                    try {
                        // Fetch full message details for parsing
                        val fullMessage = gmail.users().messages()
                            .get("me", msg.id)
                            .setFormat("full")
                            .execute()

                        val parts = fullMessage.payload.parts
                        if (parts != null) {
                            Log.d(TAG, "📦 EMAIL IS MULTIPART with ${parts.size} parts.")

                            parts.forEachIndexed { index, part ->
                                Log.d(TAG, "   Part $index MimeType: ${part.mimeType}")

                                // If the part has body data, log a snippet of the ENCODED string
                                if (part.body?.data != null) {
                                    val encodedData = part.body.data
                                    Log.d(TAG, "   -> Data found (Length: ${encodedData.length})")
                                    Log.d(TAG, "   -> Snippet: ${encodedData.take(50)}...") // Show first 50 chars
                                } else {
                                    Log.d(TAG, "   -> No data in this part (might be nested further)")
                                }
                            }
                        }
                        // 2. Check if it is Single Part (Rare for receipts)
                        else {
                            val bodyData = fullMessage.payload.body?.data
                            if (bodyData != null) {
                                Log.d(TAG, "📄 EMAIL IS SINGLE PART. Data Length: ${bodyData.length}")
                            } else {
                                Log.e(TAG, "❌ EMAIL HAS NO BODY DATA FOUND.")
                            }
                        }
                        // Parse the email to extract ride details
                        val ride = parseUberEmail(fullMessage)
                        if (ride != null) {
                            rides.add(ride)
                            Log.d(TAG, "✅ Parsed ride: ${ride.tripId} - ₹${ride.fare} from ${ride.fromAddress} to ${ride.toAddress}")
                        } else {
                            val subject = fullMessage.payload.headers
                                ?.firstOrNull { it.name.equals("Subject", ignoreCase = true) }
                                ?.value ?: "No Subject"
                            Log.d(TAG, "⚠️ Skipped email (doesn't match criteria): $subject")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Error parsing email ${msg.id}", e)
                    }
                }

                Log.d(TAG, "✅ Successfully parsed ${rides.size} rides from ${messages.size} emails")
            }

            // Update last sync timestamp
            prefs.lastSyncTimestamp = System.currentTimeMillis()

        } catch (e: UserRecoverableAuthIOException) {
            // Handle user recoverable auth errors (NeedRemoteConsent)
            // This means user needs to grant consent again
            Log.w(TAG, "⚠️ User recoverable auth error - NeedRemoteConsent", e)
            prefs.isGmailConnected = false
            gmailService = null
            // Extract the Intent to get user consent
            val intent = e.intent
            throw AuthenticationException(
                "Gmail access requires your consent. Please grant permission again.",
                e,
                intent
            )
        } catch (e: GoogleJsonResponseException) {
            // Handle Google API authentication errors
            val statusCode = e.statusCode
            Log.e(TAG, "❌ Google API Error: Status $statusCode", e)
            
            // Check for authentication/authorization errors
            if (statusCode == 401 || statusCode == 403) {
                Log.w(TAG, "⚠️ Authentication failed - Token expired or revoked")
                // Mark as disconnected and clear service
                prefs.isGmailConnected = false
                gmailService = null
                // Throw a specific exception to be handled by caller
                throw AuthenticationException("Gmail authentication failed. Please sign in again.", e)
            }
            e.printStackTrace()
        } catch (e: HttpResponseException) {
            // Handle other HTTP errors
            val statusCode = e.statusCode
            Log.e(TAG, "❌ HTTP Error: Status $statusCode", e)
            
            if (statusCode == 401 || statusCode == 403) {
                Log.w(TAG, "⚠️ Authentication failed - Token expired or revoked")
                prefs.isGmailConnected = false
                gmailService = null
                throw AuthenticationException("Gmail authentication failed. Please sign in again.", e)
            }
            e.printStackTrace()
        } catch (e: Exception) {
            Log.e(TAG, "❌ CRITICAL ERROR: API Call Failed", e)
            e.printStackTrace()
        }

        return@withContext rides
    }
    
    /**
     * Custom exception for authentication failures
     * @param intent Optional Intent to get user consent (for UserRecoverableAuthIOException)
     */
    class AuthenticationException(
        message: String,
        cause: Throwable? = null,
        val intent: Intent? = null
    ) : Exception(message, cause)

    // ... imports ...

    private fun parseUberEmail(message: Message): Ride? {
        try {
            val headers = message.payload.headers
            val subject = headers?.firstOrNull { it.name.equals("Subject", ignoreCase = true) }?.value ?: ""

            // 1. Extract Body (Handles the Multipart structure)
            val bodyHtml = extractEmailBody(message)
            if (bodyHtml.isEmpty()) return null

            // 2. Parse HTML using Jsoup
            val doc = Jsoup.parse(bodyHtml)

            // 3. Extract Specific Details
            val fare = extractTotalFare(doc)
            val receiptUrl = extractPdfLink(doc)

            // 4. Extract Route Info (Pickup/Drop)
            // This function returns a Pair(Source, Destination)
            val route = extractRouteInfo(doc)

            // 5. Extract Date/Time
            // Prefer internal date for the main date, but try to find trip time in body
            val date = extractDate(message)
            val time = extractTripTime(doc) ?: "00:00"

            val tripId = message.id.takeLast(10).uppercase()

            // VALIDATION: We need at least a Fare to consider it a ride
            if (fare != null) {
                return Ride(
                    date = date,
                    time = time,
                    fromAddress = route.first, // Source
                    toAddress = route.second,  // Destination
                    fare = fare,
                    payment = extractPaymentMethod(doc) ?: "Unknown",
                    tripId = tripId,
                    source = "gmail_auto",
                    gmailMessageId = message.id,
                    isBusiness = subject.contains("business", ignoreCase = true),
                    receiptUrl = receiptUrl
                )
            } else {
                Log.w("GmailService", "Could not find Fare in email: $subject")
            }

        } catch (e: Exception) {
            Log.e("GmailService", "Error parsing Uber email", e)
        }
        return null
    }
    private fun extractEmailBody(message: Message): String {
        try {
            val parts = message.payload.parts
            var data: String? = null

            // 1. Try to find HTML part in multipart emails
            if (parts != null) {
                data = parts.firstOrNull { it.mimeType == "text/html" }?.body?.data
                // If no HTML, try Plain Text
                if (data == null) {
                    data = parts.firstOrNull { it.mimeType == "text/plain" }?.body?.data
                }
            }

            // 2. If no parts (single-part email), check the main body
            if (data == null) {
                data = message.payload.body?.data
            }

            // 3. Decode using Android's URL_SAFE mode
            if (data != null) {
                // This flag is CRITICAL for Gmail API
                return String(Base64.decode(data, Base64.URL_SAFE))
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }

        return ""
    }
    private fun extractDate(message: Message): String {
        val timestamp = message.internalDate ?: System.currentTimeMillis()
        val date = Date(timestamp)
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(date)
    }

    private fun extractTotalFare(doc: org.jsoup.nodes.Document): Double? {
        // Strategy: Look for "Total" text, then find the price in the sibling/next element
        // In your HTML: <td>Total</td><td>₹351.85</td>

        val totalElements = doc.getElementsContainingOwnText("Total")
        for (el in totalElements) {
            // Check the next sibling or the next cell in the row
            var value = el.nextElementSibling()?.text()

            // Clean up string (remove ₹, commas, spaces)
            if (value != null) {
                val cleanPrice = value.replace(Regex("[^0-9.]"), "")
                return cleanPrice.toDoubleOrNull()
            }
        }
        return null
    }

    private fun extractPdfLink(doc: org.jsoup.nodes.Document): String? {
        // Strategy: Find link with text "Download PDF"
        val links = doc.select("a")
        for (link in links) {
            if (link.text().contains("Download PDF", ignoreCase = true)) {
                return link.attr("href")
            }
        }
        return null
    }

    private fun extractRouteInfo(doc: org.jsoup.nodes.Document): Pair<String, String> {
        // Strategy: Uber emails list 2 times (Start/End) followed by Addresses.
        // Pattern: [Time] -> [Address] -> [Time] -> [Address]

        var source = "Unknown Pickup"
        var dest = "Unknown Drop"

        // Find elements that look like times (e.g., "6:09 pm")
        // Your HTML puts these in <td> tags with specific styling or just text.
        val timeRegex = Regex("\\d{1,2}:\\d{2}\\s*(am|pm)", RegexOption.IGNORE_CASE)

        // Get all table cells
        val cells = doc.select("td")
        val addresses = mutableListOf<String>()

        for (i in 0 until cells.size) {
            val text = cells[i].text().trim()

            if (text.matches(timeRegex)) {
                // The NEXT cell usually contains the address
                // We check i+1. Sometimes there's an empty cell, so we check i+2 too if needed.
                if (i + 1 < cells.size) {
                    val potentialAddress = cells[i+1].text().trim()
                    if (potentialAddress.length > 5 && !potentialAddress.matches(timeRegex)) {
                        addresses.add(potentialAddress)
                    }
                }
            }
        }

        if (addresses.isNotEmpty()) source = addresses.first()
        if (addresses.size > 1) dest = addresses.last() // Or the second one found

        return Pair(source, dest)
    }

    private fun extractTripTime(doc: org.jsoup.nodes.Document): String? {
        val timeRegex = Regex("(\\d{1,2}:\\d{2}\\s*(am|pm))", RegexOption.IGNORE_CASE)
        val text = doc.text()
        return timeRegex.find(text)?.value
    }

    private fun extractPaymentMethod(doc: org.jsoup.nodes.Document): String? {
        val text = doc.text().lowercase()
        return when {
            text.contains("cash") -> "Cash"
            text.contains("upi") -> "UPI"
            text.contains("goaxb") || text.contains("visa") || text.contains("mastercard") -> "Card"
            text.contains("paytm") -> "Paytm"
            else -> "Unknown"
        }
    }
}