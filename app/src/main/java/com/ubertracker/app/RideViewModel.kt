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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
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

    private val _gmailConnected = MutableStateFlow(false)
    val gmailConnected: StateFlow<Boolean> = _gmailConnected.asStateFlow()

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    private val _oneTimeEvent = MutableSharedFlow<OneTimeEvent>()
    val oneTimeEvent = _oneTimeEvent.asSharedFlow()

    val unclaimedRides: StateFlow<List<Ride>> = rideDao.getUnclaimedRides()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val claimedRides: StateFlow<List<Ride>> = rideDao.getClaimedRides()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedRideIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedRideIds = _selectedRideIds.asStateFlow()

    fun toggleSelection(rideId: Long) {
        val current = _selectedRideIds.value
        if (current.contains(rideId)) {
            _selectedRideIds.value = current - rideId
        } else {
            _selectedRideIds.value = current + rideId
        }
    }
    fun clearSelection() {
        _selectedRideIds.value = emptySet()
    }

    fun exportSelectedRides() {
        viewModelScope.launch {
            val ids = _selectedRideIds.value
            if (ids.isEmpty()) return@launch

            // 1. Get the actual ride objects
            val allRides = unclaimedRides.value
            val ridesToExport = allRides.filter { ids.contains(it.id) }

            if (ridesToExport.isNotEmpty()) {
                try {
                    // 2. Generate Excel
                    val file = excelExporter.generateExcel(ridesToExport)
                    excelExporter.shareFile(file)

                    // 3. Mark as Claimed in DB
                    rideDao.updateClaimStatus(ids.toList(), true)

                    // 4. Clear Selection
                    clearSelection()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun unclaimRides(rideIds: List<Long>) {
        viewModelScope.launch {
            rideDao.updateClaimStatus(rideIds, false)
        }
    }

    init {

        checkGmailConnection()
    }
    val stats: StateFlow<RideStats> = rideDao.getAllRides()
        .map { rides ->
            // This runs every time the database changes
            calculateStatsValues(rides)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = RideStats()
        )

    private fun calculateStatsValues(rideList: List<Ride>): RideStats {
        val calendar = Calendar.getInstance()
        val currentMonth = calendar.get(Calendar.MONTH)
        val currentYear = calendar.get(Calendar.YEAR)

        val monthRides = rideList.filter { ride ->
            try {
                val rideDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(ride.date)
                val rideCal = Calendar.getInstance().apply { time = rideDate ?: Date() }
                rideCal.get(Calendar.MONTH) == currentMonth &&
                        rideCal.get(Calendar.YEAR) == currentYear
            } catch (e: Exception) {
                false
            }
        }

        return RideStats(
            totalRides = monthRides.size,
            totalAmount = monthRides.sumOf { it.fare },
            manualEntries = monthRides.count { it.source == "manual" },
            claimedAmount = monthRides.filter { it.isClaimed }.sumOf { it.fare },
            unclaimedAmount = monthRides.filter { !it.isClaimed }.sumOf { it.fare }
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
        // Force this to run on background thread
        viewModelScope.launch(Dispatchers.IO) {
            prefs.senderEmail = email.trim()
        }
    }
}

sealed class OneTimeEvent {
    data class StartSignIn(val signInIntent: Intent) : OneTimeEvent()
}