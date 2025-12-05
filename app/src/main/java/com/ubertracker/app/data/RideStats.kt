package com.ubertracker.app.data

data class RideStats(
    val totalRides: Int = 0,
    val totalAmount: Double = 0.0,
    val manualEntries: Int = 0,
    val claimedAmount: Double = 0.0,
    val unclaimedAmount: Double = 0.0
)