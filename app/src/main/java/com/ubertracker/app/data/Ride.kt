package com.ubertracker.app.data

import androidx.room.*

@Entity(
    tableName = "rides",
    indices = [
        Index(value = ["date"]),
        Index(value = ["tripId"], unique = true),
        Index(value = ["isClaimed"])
    ]
)
data class Ride(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "date")
    val date: String, // yyyy-MM-dd format

    @ColumnInfo(name = "time")
    val time: String? = null, // HH:mm format

    @ColumnInfo(name = "fromAddress")
    val fromAddress: String,

    @ColumnInfo(name = "toAddress")
    val toAddress: String,

    @ColumnInfo(name = "fare")
    val fare: Double,

    @ColumnInfo(name = "currency")
    val currency: String = "INR",

    @ColumnInfo(name = "payment")
    val payment: String, // UPI, Card, Cash, Wallet

    @ColumnInfo(name = "tripId")
    val tripId: String, // Unique identifier

    @ColumnInfo(name = "source")
    val source: String, // "auto" or "manual"

    @ColumnInfo(name = "gmailMessageId")
    val gmailMessageId: String? = null,

    @ColumnInfo(name = "isBusiness")
    val isBusiness: Boolean = true,

    @ColumnInfo(name = "isClaimed")
    val isClaimed: Boolean = false,

    @ColumnInfo(name = "notes")
    val notes: String? = null,

    @ColumnInfo(name = "createdAt")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "receiptUrl")
    val receiptUrl: String? = null,

    @ColumnInfo(name = "is_deleted")
    val isDeleted: Boolean = false

)