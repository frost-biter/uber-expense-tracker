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
import com.ubertracker.app.workers.GmailSyncWorker
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.*
import androidx.core.net.toUri
import androidx.lifecycle.application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.ubertracker.app.workers.ReminderWorker
import java.util.concurrent.TimeUnit

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

            val allRides = unclaimedRides.value
            val ridesToExport = allRides.filter { ids.contains(it.id) }

            if (ridesToExport.isNotEmpty()) {
                try {
                    ridesToExport.forEach { ride ->
                        val url = ride.receiptUrl
                        if (gmailService.isAttachmentUrl(url)) {
                            val parsed = gmailService.parseAttachmentUrl(url ?: "")
                            if (parsed != null) {
                                val (msgId, attId, filename) = parsed
                                gmailService.downloadAttachment(msgId, attId, filename)
                            }
                        }
                    }

                    val file = excelExporter.generateExcel(ridesToExport)
                    excelExporter.shareFile(file)

                    rideDao.updateClaimStatus(ids.toList(), true)
                    clearSelection()

                } catch (_: Exception) {
                    // ignore
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
        scheduleReminders()
    }
    val stats: StateFlow<RideStats> = rideDao.getAllRides()
        .map { rides ->
            calculateStatsValues(rides)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = RideStats()
        )

    private fun calculateStatsValues(rideList: List<Ride>): RideStats {
        return RideStats(
            totalRides = rideList.size,
            totalAmount = rideList.sumOf { it.fare },
            manualEntries = rideList.count { it.source == "manual" },
            claimedAmount = rideList.filter { it.isClaimed }.sumOf { it.fare },
            unclaimedAmount = rideList.filter { !it.isClaimed }.sumOf { it.fare }
        )
    }

    private fun checkGmailConnection() {
        viewModelScope.launch {
            val isConnected = gmailService.isConnected()
            _gmailConnected.value = isConnected
            if (isConnected) {
                scheduleDailyGmailSyncs()
            } else {
                try {
                    val workManager = androidx.work.WorkManager.getInstance(getApplication())
                    workManager.cancelUniqueWork("gmail_sync_11_02")
                    workManager.cancelUniqueWork("gmail_sync_15_02")
                    workManager.cancelUniqueWork("gmail_sync_20_00")
                } catch (e: Exception) {
                    // ignore
                }
            }
        }
    }

    fun recheckGmailConnection() {
        checkGmailConnection()
    }

    private fun scheduleDailyGmailSyncs() {
        viewModelScope.launch {
            try {
                GmailSyncWorker.scheduleDailySyncs(getApplication())
            } catch (e: Exception) {
                // ignore
            }
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
                scheduleDailyGmailSyncs()
            }
        }
    }

    fun syncGmail() {
        viewModelScope.launch {
            _syncing.value = true
            try {
                val newRides = gmailService.fetchUberReceipts()
                newRides.forEach { ride ->
                    val existing = rideDao.getRideByTripId(ride.tripId)
                    if (existing == null) {
                        rideDao.insertRide(ride)
                    }
                }
            } catch (e: GmailService.AuthenticationException) {
                _gmailConnected.value = false

                if (e.intent != null) {
                    _oneTimeEvent.emit(OneTimeEvent.RequestGmailConsent(e.intent))
                } else {
                    _oneTimeEvent.emit(OneTimeEvent.GmailAuthFailed(e.message ?: "Gmail authentication failed. Please sign in again."))
                }
            } catch (e: Exception) {
                // ignore
            } finally {
                _syncing.value = false
            }
        }
    }

    fun addManualRide(ride: Ride) {
        viewModelScope.launch {
            try {
                val duplicate = rideDao.checkDuplicate(ride.date, ride.fare, 5.0)
                if (duplicate == null) {
                    rideDao.insertRide(ride)
                } else {
                    rideDao.insertRide(ride)
                }
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    fun updateRide(ride: Ride) {
        viewModelScope.launch {
            try {
                rideDao.updateRide(ride)
            } catch (_: Exception) {
                // ignore
            }
        }
    }

    fun deleteRide(rideId: Long) {
        viewModelScope.launch {
            try {
                rideDao.softDeleteRide(rideId)
            } catch (e: Exception) {
                // ignore
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
                    excelExporter.shareFile(file)
                }
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    fun scheduleReminders() {
        val workRequest = PeriodicWorkRequestBuilder<ReminderWorker>(12, TimeUnit.HOURS)
            .build()

        WorkManager.getInstance(application).enqueueUniquePeriodicWork(
            "ExpenseReminderWork",
            ExistingPeriodicWorkPolicy.KEEP, // KEEP ensures we don't reset the timer if it's already running
            workRequest
        )
    }

    fun getSenderEmails(): List<String> {
        return prefs.senderEmails
    }

    fun setSenderEmails(emails: List<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            prefs.senderEmails = emails.map { it.trim() }.filter { it.isNotBlank() }
        }
    }

    fun removeSenderEmail(email: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentEmails = prefs.senderEmails.toMutableList()
            currentEmails.remove(email.trim())
            if (currentEmails.isEmpty()) {
                currentEmails.add("noreply@uber.com")
            }
            prefs.senderEmails = currentEmails
        }
    }
    val trashedRides: StateFlow<List<Ride>> = rideDao.getTrashedRides()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun restoreRide(rideId: Long) {
        viewModelScope.launch {
            rideDao.restoreRide(rideId)
        }
    }

    // In RideViewModel.kt

    fun testNotificationNow() {
        val request = OneTimeWorkRequest.Builder(ReminderWorker::class.java)
            .build()
        WorkManager.getInstance(application).enqueue(request)
    }

    fun deleteForever(rideId: Long) {
        viewModelScope.launch {
            rideDao.deleteForever(rideId)
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            rideDao.emptyTrash()
        }
    }

    suspend fun downloadAndOpenReceipt(ride: Ride): Boolean {
        return try {
            val url = ride.receiptUrl ?: return false

            if (gmailService.isAttachmentUrl(url)) {
                val parsed = gmailService.parseAttachmentUrl(url)
                if (parsed != null) {
                    val (msgId, attId, filename) = parsed
                    val file = gmailService.downloadAttachment(msgId, attId, filename)
                    if (file != null && file.exists()) {
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                            val uri = androidx.core.content.FileProvider.getUriForFile(
                                getApplication(),
                                "${getApplication<Application>().packageName}.fileprovider",
                                file
                            )
                            setDataAndType(uri, "application/pdf")
                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        getApplication<Application>().startActivity(intent)
                        true
                    } else {
                        false
                    }
                } else {
                    false
                }
            } else {
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, url.toUri())
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                getApplication<Application>().startActivity(intent)
                true
            }
        } catch (e: Exception) {
            false
        }
    }
}

sealed class OneTimeEvent {
    data class StartSignIn(val signInIntent: Intent) : OneTimeEvent()
    data class RequestGmailConsent(val consentIntent: Intent) : OneTimeEvent()
    data class GmailAuthFailed(val message: String) : OneTimeEvent()
}