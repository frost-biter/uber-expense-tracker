package com.ubertracker.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import com.ubertracker.app.OneTimeEvent
import com.ubertracker.app.RideViewModel
import com.ubertracker.app.data.Ride
import com.ubertracker.app.data.RideStats
import com.ubertracker.app.ui.theme.UberTrackerTheme
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: RideViewModel by viewModels()

    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.handleSignInResult(result.data)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            viewModel.oneTimeEvent.collectLatest { event ->
                when (event) {
                    is OneTimeEvent.StartSignIn -> {
                        signInLauncher.launch(event.signInIntent)
                    }
                }
            }
        }

        setContent {
            UberTrackerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(viewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: RideViewModel) {
    var showAddDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    val pagerState = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()
    val titles = listOf("Pending", "History")
    val gmailConnected by viewModel.gmailConnected.collectAsState()
    val syncing by viewModel.syncing.collectAsState()
    val stats by viewModel.stats.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Uber Expense Tracker") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1A1A2E)
                ),
                actions = {
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(Icons.Default.Settings, "Settings", tint = Color.White)
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, "Add Ride")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                GmailConnectionCard(
                    connected = gmailConnected,
                    syncing = syncing,
                    onConnect = { viewModel.connectGmail() },
                    onSync = { viewModel.syncGmail() }
                )
                StatsRow(stats)
            }
            TabRow(selectedTabIndex = pagerState.currentPage) {
                titles.forEachIndexed { index, title ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = { Text(title) }
                    )
                }
            }
            HorizontalPager(state = pagerState) {
                when (it) {
                    0 -> PendingScreen(viewModel)
                    1 -> HistoryScreen(viewModel)
                }
            }
        }
    }

    if (showAddDialog) {
        AddRideDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { ride ->
                viewModel.addManualRide(ride)
                showAddDialog = false
            }
        )
    }

    if (showSettingsDialog) {
        SettingsDialog(
            viewModel = viewModel,
            onDismiss = { showSettingsDialog = false }
        )
    }
}

@Composable
fun PendingScreen(viewModel: RideViewModel) {
    val rides by viewModel.unclaimedRides.collectAsState()
    val selectedIds by viewModel.selectedRideIds.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(bottom = 80.dp)) {
            items(rides) { ride ->
                RideItem(
                    ride = ride,
                    isSelected = selectedIds.contains(ride.id),
                    onClick = { viewModel.toggleSelection(ride.id) }
                )
            }
        }

        // Export Button
        if (selectedIds.isNotEmpty()) {
            Button(
                onClick = { viewModel.exportSelectedRides() },
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4CAF50)
                )
            ) {
                Row(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Icon(Icons.Default.Download, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Claim (${selectedIds.size})")
                }
            }
        }
    }
}

@Composable
fun HistoryScreen(viewModel: RideViewModel) {
    val rides by viewModel.claimedRides.collectAsState()

    LazyColumn {
        items(rides) { ride ->
            ClaimedRideItem(ride = ride, onUnclaim = { viewModel.unclaimRides(listOf(ride.id)) })
        }
    }
}

@Composable
fun RideItem(ride: Ride, isSelected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = rememberRipple(bounded = true),
                onClick = onClick
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFF303F9F) else Color(0xFF263238)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onClick() }
            )
            Column(modifier = Modifier.padding(start = 8.dp)) {
                Text(ride.date, style = MaterialTheme.typography.titleMedium, color = Color.White)
                Text(ride.fromAddress, style = MaterialTheme.typography.bodySmall, color = Color.Gray, maxLines = 1)
                Text("₹${ride.fare}", style = MaterialTheme.typography.titleLarge, color = Color(0xFF4CAF50))
            }
        }
    }
}

@Composable
fun ClaimedRideItem(ride: Ride, onUnclaim: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF37474F)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(ride.date, style = MaterialTheme.typography.titleMedium, color = Color.White)
                Text(ride.fromAddress, style = MaterialTheme.typography.bodySmall, color = Color.Gray, maxLines = 1)
                Text("₹${ride.fare}", style = MaterialTheme.typography.titleLarge, color = Color.White)
            }
            IconButton(onClick = onUnclaim) {
                Icon(Icons.Default.Undo, "Unclaim")
            }
        }
    }
}


@Composable
fun GmailConnectionCard(
    connected: Boolean,
    syncing: Boolean,
    onConnect: () -> Unit,
    onSync: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (connected) Color(0xFF1B5E20) else Color(0xFF263238)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    if (connected) Icons.Default.CheckCircle else Icons.Default.Email,
                    contentDescription = null,
                    tint = if (connected) Color(0xFF4CAF50) else Color(0xFF64B5F6),
                    modifier = Modifier.size(32.dp)
                )
                Column {
                    Text(
                        if (connected) "Gmail Connected" else "Connect Gmail",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                    if (!connected) {
                        Text(
                            "Auto-fetch receipts",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                }
            }

            if (connected) {
                Button(
                    onClick = onSync,
                    enabled = !syncing,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2E7D32)
                    )
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(if (syncing) "Syncing..." else "Sync")
                }
            } else {
                Button(onClick = onConnect) {
                    Text("Connect")
                }
            }
        }
    }
}

@Composable
fun StatsRow(stats: RideStats) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatCard(
            title = "Unclaimed",
            value = "₹${String.format("%.2f", stats.unclaimedAmount)}",
            color = Color(0xFFFF9800),
            modifier = Modifier.weight(1f)
        )
        StatCard(
            title = "Claimed",
            value = "₹${String.format("%.2f", stats.claimedAmount)}",
            color = Color(0xFF4CAF50),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun StatCard(title: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = color)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                value,
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White
            )
            Text(
                title,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
fun RideCard(ride: Ride, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF263238))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                if (ride.source == "auto") Color(0xFF4CAF50) else Color(0xFFFF9800),
                                shape = MaterialTheme.shapes.small
                            )
                    )
                    Column {
                        Text(
                            "₹${String.format("%.2f", ride.fare)}",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White
                        )
                        Text(
                            "${ride.date} • ${ride.time ?: "N/A"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                }

                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Close, "Delete", tint = Color.Gray)
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.LocationOn, null, tint = Color(0xFF64B5F6), modifier = Modifier.size(16.dp))
                Text(ride.fromAddress, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.9f))
            }

            Spacer(Modifier.height(4.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Flag, null, tint = Color(0xFF64B5F6), modifier = Modifier.size(16.dp))
                Text(ride.toAddress, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.9f))
            }

            ride.notes?.let {
                Spacer(Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Notes, null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
            }

            // Download Receipt Button (if receiptUrl is available)
            ride.receiptUrl?.let { url ->
                Spacer(Modifier.height(12.dp))
                val context = LocalContext.current
                Button(
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            // Handle case where no browser is available
                            e.printStackTrace()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2196F3)
                    )
                ) {
                    Icon(Icons.Default.Download, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Download Receipt")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(
    viewModel: RideViewModel,
    onDismiss: () -> Unit
) {
    var senderEmail by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }

    // Load current email on first composition
    LaunchedEffect(Unit) {
        senderEmail = viewModel.getSenderEmail()
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF263238)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Settings",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.Gray
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Sender Email Field
                Text(
                    "Sender Email Address",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Text(
                    "Email address to search for receipts (e.g., no-reply@uber.com)",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                OutlinedTextField(
                    value = senderEmail,
                    onValueChange = {
                        senderEmail = it
                        showError = false
                    },
                    label = { Text("Email Address") },
                    placeholder = { Text("no-reply@uber.com") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF64B5F6),
                        unfocusedBorderColor = Color.Gray,
                        focusedLabelColor = Color(0xFF64B5F6),
                        unfocusedLabelColor = Color.Gray,
                        focusedPlaceholderColor = Color.Gray,
                        unfocusedPlaceholderColor = Color.Gray
                    )
                )

                // Error Message
                if (showError) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Please enter a valid email address",
                        color = Color(0xFFEF5350),
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Save Button
                Button(
                    onClick = {
                        val emailPattern = android.util.Patterns.EMAIL_ADDRESS
                        if (senderEmail.isBlank() || !emailPattern.matcher(senderEmail).matches()) {
                            showError = true
                        } else {
                            viewModel.setSenderEmail(senderEmail)
                            onDismiss()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF9C27B0)
                    )
                ) {
                    Text(
                        "Save",
                        modifier = Modifier.padding(8.dp),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}