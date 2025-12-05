package com.ubertracker.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RideDao {

    @Query("SELECT * FROM rides WHERE isClaimed = 0 ORDER BY date DESC")
    fun getUnclaimedRides(): Flow<List<Ride>>

    @Query("SELECT * FROM rides WHERE isClaimed = 1 ORDER BY date DESC")
    fun getClaimedRides(): Flow<List<Ride>>

    @Query("SELECT * FROM rides WHERE date >= :startDate AND date <= :endDate ORDER BY date DESC")
    fun getRidesByDateRange(startDate: String, endDate: String): Flow<List<Ride>>

    @Query("SELECT * FROM rides WHERE tripId = :tripId LIMIT 1")
    suspend fun getRideByTripId(tripId: String): Ride?

    @Query("SELECT * FROM rides WHERE id = :rideId")
    suspend fun getRideById(rideId: Long): Ride?

    @Query("SELECT * FROM rides WHERE date = :date AND ABS(fare - :fare) <= :tolerance LIMIT 1")
    suspend fun checkDuplicate(date: String, fare: Double, tolerance: Double): Ride?

    @Query("SELECT * FROM rides WHERE source = 'manual' ORDER BY date DESC")
    fun getManualRides(): Flow<List<Ride>>

    @Query("UPDATE rides SET isClaimed = :status WHERE id IN (:rideIds)")
    suspend fun updateClaimStatus(rideIds: List<Long>, status: Boolean)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRide(ride: Ride): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRides(rides: List<Ride>): List<Long>

    @Update
    suspend fun updateRide(ride: Ride)

    @Delete
    suspend fun deleteRide(ride: Ride)

    @Query("DELETE FROM rides WHERE id = :rideId")
    suspend fun deleteRideById(rideId: Long)

    @Query("DELETE FROM rides")
    suspend fun deleteAllRides()

    @Query("SELECT COUNT(*) FROM rides")
    suspend fun getRideCount(): Int

    @Query("SELECT SUM(fare) FROM rides WHERE date >= :startDate AND date <= :endDate")
    suspend fun getTotalFare(startDate: String, endDate: String): Double?

    @Query("""
        SELECT * FROM rides 
        WHERE date >= date('now', 'start of month') 
        AND date <= date('now', 'start of month', '+1 month', '-1 day')
        ORDER BY date DESC
    """)
    fun getCurrentMonthRides(): Flow<List<Ride>>


    @Query("SELECT * FROM rides ORDER BY date DESC")
    fun getAllRides(): Flow<List<Ride>>

}