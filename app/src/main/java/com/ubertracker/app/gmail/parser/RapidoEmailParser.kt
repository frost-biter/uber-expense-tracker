package com.ubertracker.app.gmail.parser

import android.util.Base64
import com.google.api.services.gmail.model.Message
import com.ubertracker.app.data.Ride
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.*

/**
 * Parser specifically for Rapido ride receipt emails.
 * Handles HTML structure defined in standard Rapido receipts.
 */
class RapidoEmailParser : EmailParser {

    companion object {
        private val RAPIDO_EMAIL_DOMAINS = listOf(
            "shoutout@rapido.bike",
            "rapido.bike",
            "pranav61002@gmail.com"
        )
    }

    override fun canHandle(
        senderEmail: String,
        subject: String,
        trustedSenders: List<String>
    ): Boolean {
        val lowerSender = senderEmail.lowercase()
        val lowerSubject = subject.lowercase()

        if (RAPIDO_EMAIL_DOMAINS.any { lowerSender.contains(it) }) return true

        val isTrusted = trustedSenders.any { lowerSender.contains(it.lowercase()) }
        return isTrusted && lowerSubject.contains("rapido")
    }

    override fun parseEmail(message: Message): Ride? {
        try {
            val headers = message.payload.headers
            val subject = headers?.firstOrNull { it.name.equals("Subject", ignoreCase = true) }?.value ?: ""

            val bodyHtml = extractEmailBody(message)
            if (bodyHtml.isEmpty()) return null

            val doc = Jsoup.parse(bodyHtml)

            val fare = extractTotalFare(doc)
            val receiptUrl = extractPdfLinkFromAttachments(message)
            val route = extractRouteInfo(doc)

            val dateTimePair = extractDateTimeFromBody(doc)
            val date = dateTimePair?.first ?: extractDate(message)
            val time = dateTimePair?.second ?: "00:00"

            val tripId = extractTripId(doc) ?: message.id.takeLast(10).uppercase()

            if (fare != null) {
                return Ride(
                    date = date,
                    time = time,
                    fromAddress = route.first,
                    toAddress = route.second,
                    fare = fare,
                    payment = extractPaymentMethod(doc) ?: "Online",
                    tripId = tripId,
                    source = "gmail_auto",
                    gmailMessageId = message.id,
                    isBusiness = subject.contains("business", ignoreCase = true),
                    receiptUrl = receiptUrl
                )
            }
        } catch (e: Exception) {
            // Error parsing, return null
        }
        return null
    }

    override fun extractEmailBody(message: Message): String {
        try {
            fun findHtmlPart(parts: List<com.google.api.services.gmail.model.MessagePart>?): String? {
                if (parts == null) return null
                for (part in parts) {
                    if (part.mimeType == "text/html" && part.body.data != null) {
                        return part.body.data
                    }
                    if (part.parts != null) {
                        val found = findHtmlPart(part.parts)
                        if (found != null) return found
                    }
                }
                return null
            }

            var data = findHtmlPart(message.payload.parts)
            if (data == null) {
                data = message.payload.body?.data
            }

            if (data != null) {
                return String(Base64.decode(data, Base64.URL_SAFE))
            }
        } catch (e: Exception) {
            // ignore
        }
        return ""
    }

    override fun extractDate(message: Message): String {
        val timestamp = message.internalDate ?: System.currentTimeMillis()
        val date = Date(timestamp)
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(date)
    }

    private fun extractDateTimeFromBody(doc: Document): Pair<String, String>? {
        try {
            val labelDiv = doc.getElementsContainingOwnText("Time of Ride").firstOrNull()
            val timeString = labelDiv?.nextElementSibling()?.text()?.trim()

            if (timeString != null) {
                val cleanString = timeString.replace(Regex("(\\d+)(st|nd|rd|th)"), "$1")
                val parser = SimpleDateFormat("MMM d yyyy, h:mm a", Locale.ENGLISH)
                val dateObj = parser.parse(cleanString)

                if (dateObj != null) {
                    val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(dateObj)
                    val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(dateObj)
                    return Pair(date, time)
                }
            }
        } catch (e: Exception) {
            // ignore
        }
        return null
    }

    override fun extractTotalFare(doc: Document): Double? {
        val costElement = doc.select("div.ride-cost").first()
        if (costElement != null) {
            val text = costElement.text()
            val cleanPrice = text.replace(Regex("[^0-9.]"), "")
            return cleanPrice.toDoubleOrNull()
        }
        return null
    }

    private fun extractPdfLinkFromAttachments(message: Message): String? {
        val parts = message.payload.parts ?: return null
        for (part in parts) {
            if (part.mimeType == "application/pdf" && !part.filename.isNullOrEmpty()) {
                val attachmentId = part.body.attachmentId
                if (attachmentId != null) {
                    return "attachment://${message.id}/$attachmentId/${part.filename}"
                }
            }
        }
        return null
    }

    override fun extractPdfLink(doc: Document): String? = null

    override fun extractRouteInfo(doc: Document): Pair<String, String> {
        var source = "Unknown Pickup"
        var dest = "Unknown Drop"

        val pickupEl = doc.select(".pickup-point .content.location").first()
        if (pickupEl != null) {
            source = pickupEl.text().trim()
        }

        val dropEl = doc.select(".drop-point .content.location").first()
        if (dropEl != null) {
            dest = dropEl.text().trim()
        }

        return Pair(source, dest)
    }

    override fun extractTripTime(doc: Document): String? {
        return extractDateTimeFromBody(doc)?.second
    }

    private fun extractTripId(doc: Document): String? {
        val label = doc.getElementsContainingOwnText("Ride ID").firstOrNull()
        return label?.nextElementSibling()?.text()?.trim()
    }

    override fun extractPaymentMethod(doc: Document): String? {
        val text = doc.text().lowercase()
        return when {
            text.contains("cash") -> "Cash"
            text.contains("upi") || text.contains("phonepe") || text.contains("gpay") -> "UPI"
            text.contains("wallet") || text.contains("rapido wallet") -> "Wallet"
            else -> null
        }
    }
}