package com.ubertracker.app.gmail.parser

import com.google.api.services.gmail.model.Message

/**
 * Factory class to create appropriate EmailParser based on sender email address
 */
object EmailParserFactory {
    // Define all available parsers
    private val parsers = listOf(
        UberEmailParser(),
        RapidoEmailParser()
    )

    fun getParser(
        message: Message,
        trustedSenderEmails: List<String> // <--- Must accept this list
    ): EmailParser? {

        // 1. Extract & Clean Headers
        val headers = message.payload.headers
        val rawSender = headers?.find { it.name.equals("From", ignoreCase = true) }?.value ?: ""
        val subject = headers?.find { it.name.equals("Subject", ignoreCase = true) }?.value ?: ""

        // Clean: "Name <email@domain.com>" -> "email@domain.com"
        val senderEmail = extractSenderEmail(rawSender)

        // 2. Find matching parser
        // Pass the trusted list so parsers can check if it's a forward
        return parsers.find {
            it.canHandle(senderEmail, subject, trustedSenderEmails)
        }
    }

    private fun extractSenderEmail(raw: String): String {
        val start = raw.indexOf('<')
        val end = raw.indexOf('>')
        return if (start != -1 && end != -1) {
            raw.substring(start + 1, end)
        } else {
            raw
        }
    }
}