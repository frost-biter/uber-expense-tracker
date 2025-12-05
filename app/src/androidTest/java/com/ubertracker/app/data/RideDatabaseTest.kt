package com.ubertracker.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class RideDatabaseTest {

    private lateinit var rideDao: RideDao
    private lateinit var db: RideDatabase

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Using an in-memory database because the information stored here disappears when the
        // process is killed.
        db = Room.inMemoryDatabaseBuilder(context, RideDatabase::class.java)
            // Allowing main thread queries, just for testing.
            .allowMainThreadQueries()
            .build()
        rideDao = db.rideDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    @Throws(Exception::class)
    fun insertAndGetRide() = runBlocking {
        val ride = Ride(
            id = 1,
            date = "2024-01-01",
            time = "12:00",
            fromAddress = "Home",
            toAddress = "Work",
            fare = 150.0,
            payment = "UPI",
            tripId = "test-trip-123",
            source = "manual"
        )
        rideDao.insertRide(ride)
        val retrievedRide = rideDao.getRideById(1)
        assertEquals(retrievedRide?.tripId, "test-trip-123")
    }
}