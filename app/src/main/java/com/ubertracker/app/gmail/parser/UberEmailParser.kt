package com.ubertracker.app.gmail.parser

import android.util.Base64
import com.google.api.services.gmail.model.Message
import com.ubertracker.app.data.Ride
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.*

/**
 * Parser for Uber ride receipt emails
 */
class UberEmailParser : EmailParser {

    companion object {
        private val UBER_EMAIL_DOMAINS = listOf("uber.com", "uber.us", "uber.in")
    }

    override fun canHandle(
        senderEmail: String,
        subject: String,
        trustedSenders: List<String>
    ): Boolean {
        val lowerSender = senderEmail.lowercase()
        val lowerSubject = subject.lowercase()

        if (UBER_EMAIL_DOMAINS.any { lowerSender.contains(it) }) return true

        val isTrusted = trustedSenders.any { lowerSender.contains(it.lowercase()) }
        if (isTrusted && lowerSubject.contains("uber")) {
            return true
        }

        return false
    }

    override fun parseEmail(message: Message): Ride? {
        try {
            val headers = message.payload.headers
            val subject = headers?.firstOrNull { it.name.equals("Subject", ignoreCase = true) }?.value ?: ""

            val bodyHtml = extractEmailBody(message)
            if (bodyHtml.isEmpty()) return null

            val doc = Jsoup.parse(bodyHtml)

            val fare = extractTotalFare(doc)
            val receiptUrl = extractPdfLink(doc)
            val route = extractRouteInfo(doc)

            val date = extractDate(message)
            val time = extractTripTime(doc) ?: "00:00"

            val tripId = message.id.takeLast(10).uppercase()

            if (fare != null) {
                return Ride(
                    date = date,
                    time = time,
                    fromAddress = route.first,
                    toAddress = route.second,
                    fare = fare,
                    payment = extractPaymentMethod(doc) ?: "Unknown",
                    tripId = tripId,
                    source = "gmail_auto",
                    gmailMessageId = message.id,
                    isBusiness = subject.contains("business", ignoreCase = true),
                    receiptUrl = receiptUrl
                )
            }
        } catch (e: Exception) {
            // ignore
        }
        return null
    }

    override fun extractEmailBody(message: Message): String {
        try {
            val parts = message.payload.parts
            var data: String? = null

            if (parts != null) {
                data = parts.firstOrNull { it.mimeType == "text/html" }?.body?.data
                if (data == null) {
                    data = parts.firstOrNull { it.mimeType == "text/plain" }?.body?.data
                }
            }

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

    override fun extractTotalFare(doc: Document): Double? {
        val totalElements = doc.getElementsContainingOwnText("Total")
        for (el in totalElements) {
            var value = el.nextElementSibling()?.text()

            if (value != null) {
                val cleanPrice = value.replace(Regex("[^0-9.]"), "")
                return cleanPrice.toDoubleOrNull()
            }
        }
        return null
    }

    override fun extractPdfLink(doc: Document): String? {
        val links = doc.select("a")
        for (link in links) {
            if (link.text().contains("Download PDF", ignoreCase = true)) {
                return link.attr("href")
            }
        }
        return null
    }

    override fun extractRouteInfo(doc: Document): Pair<String, String> {
        var source = "Unknown Pickup"
        var dest = "Unknown Drop"

        val timeRegex = Regex("\\d{1,2}:\\d{2}\\s*(am|pm)", RegexOption.IGNORE_CASE)

        val cells = doc.select("td")
        val addresses = mutableListOf<String>()

        for (i in 0 until cells.size) {
            val text = cells[i].text().trim()

            if (text.matches(timeRegex)) {
                if (i + 1 < cells.size) {
                    val potentialAddress = cells[i+1].text().trim()
                    if (potentialAddress.length > 5 && !potentialAddress.matches(timeRegex)) {
                        addresses.add(potentialAddress)
                    }
                }
            }
        }

        if (addresses.isNotEmpty()) source = addresses.first()
        if (addresses.size > 1) dest = addresses.last()

        return Pair(source, dest)
    }

    override fun extractTripTime(doc: Document): String? {
        val timeRegex = Regex("(\\d{1,2}:\\d{2}\\s*(am|pm))", RegexOption.IGNORE_CASE)
        val text = doc.text()
        return timeRegex.find(text)?.value
    }

    override fun extractPaymentMethod(doc: Document): String? {
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
