package com.ubertracker.app

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ubertracker.app.data.Ride
import com.ubertracker.app.data.RideDatabase
import com.ubertracker.app.data.RideStats
import com.ubertracker.app.data.SecurePreferences
import com.ubertracker.app.gmail.GmailService
import com.ubertracker.app.excel.ExcelExporter
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import android.util.Log
import java.text.SimpleDateFormat
import java.util.*

class RideViewModel(application: Application) : AndroidViewModel(application) {

    private val database: RideDatabase by lazy { RideDatabase.getDatabase(application) }
    private val rideDao: com.ubertracker.app.data.RideDao by lazy { database.rideDao() }
    private val gmailService = GmailService(application)
    private val excelExporter = ExcelExporter(application)
    private val prefs = SecurePreferences(application)

    private val _rides = MutableStateFlow<List<Ride>>(emptyList())
    val rides: StateFlow<List<Ride>> = _rides.asStateFlow()

    private val _stats = MutableStateFlow(RideStats())
    val stats: StateFlow<RideStats> = _stats.asStateFlow()

    private val _gmailConnected = MutableStateFlow(false)
    val gmailConnected: StateFlow<Boolean> = _gmailConnected.asStateFlow()

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    private val _oneTimeEvent = MutableSharedFlow<OneTimeEvent>()
    val oneTimeEvent = _oneTimeEvent.asSharedFlow()

    init {
        // Load rides asynchronously to avoid blocking initialization
        loadRides()
        checkGmailConnection()
    }

    private fun loadRides() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                // Ensure database is initialized
                val dao = rideDao

                dao.getAllRides()
                    .catch { e ->
                        Log.e("RideViewModel", "Error in Flow collection", e)
                        e.printStackTrace()
                        _rides.value = emptyList()
                        _stats.value = RideStats()
                    }
                    .collect { rideList ->
                        try {
                            _rides.value = rideList
                            calculateStats(rideList)
                            Log.d("RideViewModel", "Loaded ${rideList.size} rides")
                        } catch (e: Exception) {
                            Log.e("RideViewModel", "Error processing ride list", e)
                            e.printStackTrace()
                        }
                    }
            } catch (e: Exception) {
                Log.e("RideViewModel", "Error initializing database or loading rides", e)
                e.printStackTrace()
                // Set empty state to prevent crash
                _rides.value = emptyList()
                _stats.value = RideStats()
            }
        }
    }

    private fun calculateStats(rideList: List<Ride>) {
        val calendar = Calendar.getInstance()
        val currentMonth = calendar.get(Calendar.MONTH)
        val currentYear = calendar.get(Calendar.YEAR)

        val monthRides = rideList.filter { ride ->
            val rideDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(ride.date)
            val rideCal = Calendar.getInstance().apply { time = rideDate ?: Date() }
            rideCal.get(Calendar.MONTH) == currentMonth &&
                    rideCal.get(Calendar.YEAR) == currentYear
        }

        _stats.value = RideStats(
            totalRides = monthRides.size,
            totalAmount = monthRides.sumOf { it.fare },
            manualEntries = monthRides.count { it.source == "manual" }
        )
    }

    private fun checkGmailConnection() {
        viewModelScope.launch {
            // This will also restore the service if needed
            _gmailConnected.value = gmailService.isConnected()
        }
    }

    fun connectGmail() {
        viewModelScope.launch {
            if (!gmailService.isConnected()) {
                val signInIntent = gmailService.getSignInIntent()
                _oneTimeEvent.emit(OneTimeEvent.StartSignIn(signInIntent))
            }
        }
    }

    fun handleSignInResult(data: Intent?) {
        viewModelScope.launch {
            val success = gmailService.handleSignInResult(data)
            if (success) {
                _gmailConnected.value = true
                syncGmail()
            }
        }
    }

    fun syncGmail() {
        viewModelScope.launch {
            _syncing.value = true
            try {
                val newRides = gmailService.fetchUberReceipts()
                newRides.forEach { ride ->
                    // Check for duplicates based on tripId
                    val existing = rideDao.getRideByTripId(ride.tripId)
                    if (existing == null) {
                        rideDao.insertRide(ride)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _syncing.value = false
            }
        }
    }

    fun addManualRide(ride: Ride) {
        viewModelScope.launch {
            try {
                // Check for potential duplicates
                val duplicate = rideDao.checkDuplicate(ride.date, ride.fare, 5.0)
                if (duplicate == null) {
                    rideDao.insertRide(ride)
                } else {
                    // Handle duplicate (show dialog in UI)
                    // For now, just insert anyway with different ID
                    rideDao.insertRide(ride)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun deleteRide(rideId: Long) {
        viewModelScope.launch {
            try {
                rideDao.deleteRideById(rideId)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun exportToExcel() {
        viewModelScope.launch {
            try {
                val calendar = Calendar.getInstance()
                val currentMonth = calendar.get(Calendar.MONTH)
                val currentYear = calendar.get(Calendar.YEAR)

                val monthRides = _rides.value.filter { ride ->
                    val rideDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(ride.date)
                    val rideCal = Calendar.getInstance().apply { time = rideDate ?: Date() }
                    rideCal.get(Calendar.MONTH) == currentMonth &&
                            rideCal.get(Calendar.YEAR) == currentYear
                }

                if (monthRides.isNotEmpty()) {
                    val file = excelExporter.generateExcel(monthRides)
                    // Share or open the file
                    excelExporter.shareFile(file)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun scheduleDailyReminders() {
        // This will be called from MainActivity onCreate
        // WorkManager setup for daily reminders at 9 AM and 6 PM
        viewModelScope.launch {
            // Implementation in WorkManager section
        }
    }

    // Sender email management
    fun getSenderEmail(): String {
        return prefs.senderEmail
    }

    fun setSenderEmail(email: String) {
        prefs.senderEmail = email.trim()
    }
}

sealed class OneTimeEvent {
    data class StartSignIn(val signInIntent: Intent) : OneTimeEvent()
}