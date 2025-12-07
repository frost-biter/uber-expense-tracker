package com.ubertracker.app.gmail.parser

import com.google.api.services.gmail.model.Message
import com.ubertracker.app.data.Ride
import org.jsoup.nodes.Document

/**
 * Interface for parsing ride receipt emails from different providers (Uber, Rapido, etc.)
 */
interface EmailParser {
    
    /**
     * Main method to parse an email message and extract ride information
     */
    fun parseEmail(message: Message): Ride?
    
    /**
     * Extract the email body HTML/text from the message
     */
    fun extractEmailBody(message: Message): String
    
    /**
     * Extract the date from the email message
     */
    fun extractDate(message: Message): String
    
    /**
     * Extract the total fare from the parsed HTML document
     */
    fun extractTotalFare(doc: Document): Double?
    
    /**
     * Extract the PDF receipt link from the parsed HTML document
     */
    fun extractPdfLink(doc: Document): String?
    
    /**
     * Extract route information (pickup and drop addresses) from the parsed HTML document
     * @return Pair of (fromAddress, toAddress)
     */
    fun extractRouteInfo(doc: Document): Pair<String, String>
    
    /**
     * Extract the trip time from the parsed HTML document
     */
    fun extractTripTime(doc: Document): String?
    
    /**
     * Extract the payment method from the parsed HTML document
     */
    fun extractPaymentMethod(doc: Document): String?
    
    /**
     * Check if this parser can handle emails from the given sender email address
     */
    fun canHandle(senderEmail: String,subject: String, trustedSenders: List<String>): Boolean
}

