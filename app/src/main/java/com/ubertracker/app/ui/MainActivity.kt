package com.ubertracker.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.PowerOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.lifecycleScope
import com.ubertracker.app.OneTimeEvent
import com.ubertracker.app.RideViewModel
import com.ubertracker.app.data.Ride
import com.ubertracker.app.data.RideStats
import com.ubertracker.app.ui.theme.CyberBg
import com.ubertracker.app.ui.theme.CyberBlue
import com.ubertracker.app.ui.theme.CyberGray
import com.ubertracker.app.ui.theme.CyberGreen
import com.ubertracker.app.ui.theme.CyberPink
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
    var rideDetailToShow by remember { mutableStateOf<Ride?>(null) }
    val pagerState = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()
    val titles = listOf("Pending", "History")
    val gmailConnected by viewModel.gmailConnected.collectAsState()
    val syncing by viewModel.syncing.collectAsState()
    val stats by viewModel.stats.collectAsState()


    Scaffold(
        containerColor = CyberBg,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "CYBERPUNK TRACKER", // Caps like HTML
                        style = MaterialTheme.typography.titleLarge,
                        color = CyberPink
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = CyberBg
                ),
                actions = {
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(Icons.Default.Settings, "Settings", tint = CyberBlue)
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = CyberPink, // Pink Button
                contentColor = CyberBg
            ) {
                Icon(Icons.Default.Add, "Add")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                GmailStatusStrip(
                    isConnected = gmailConnected,
                    isSyncing = syncing,
                    onConnect = { viewModel.connectGmail() }
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
                    0 -> PendingScreen(
                        viewModel = viewModel,
                        onShowDetails = { ride -> rideDetailToShow = ride }
                    )
                    1 -> HistoryScreen(
                        viewModel = viewModel,
                        onShowDetails = { ride -> rideDetailToShow = ride }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddRideDialog(
            onDismiss = { },
            onAdd = { ride ->
                viewModel.addManualRide(ride)
            }
        )
    }

    if (showSettingsDialog) {
        SettingsDialog(
            viewModel = viewModel,
            onDismiss = { }
        )
    }
    if (rideDetailToShow != null) {
        RideDetailsDialog(
            ride = rideDetailToShow!!,
            onDismiss = { }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PendingScreen(
    viewModel: RideViewModel,
    onShowDetails: (Ride) -> Unit
) {
    val rides by viewModel.unclaimedRides.collectAsState()
    val selectedIds by viewModel.selectedRideIds.collectAsState()
    // 1. Correctly observe the 'syncing' state from your ViewModel
    val isSyncing by viewModel.syncing.collectAsState()

    // 2. Just create the state, don't manipulate it manually
    val state = rememberPullToRefreshState()

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            if (selectedIds.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ){
                    Button(
                        onClick = { viewModel.exportSelectedRides() },
                        modifier = Modifier
                            .fillMaxWidth(0.65f)
                            .padding (16.dp)
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CyberGreen),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Icon(Icons.Default.Download, null, tint = CyberBg)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "CLAIM (${selectedIds.size})",
                            color = CyberBg,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    ) { padding ->
        // 3. The Box handles the logic. No extra code needed.
        PullToRefreshBox(
            isRefreshing = isSyncing,
            onRefresh = { viewModel.syncGmail() }, // This triggers when you pull down
            state = state,
            modifier = Modifier.padding(padding),
            indicator = {
                // Custom Cyberpunk Spinner
                PullToRefreshDefaults.Indicator(
                    state = state,
                    isRefreshing = isSyncing,
                    containerColor = CyberBg,
                    color = CyberGreen,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                if (rides.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 64.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "NO_PENDING_DATA",
                                color = CyberGray,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                items(rides) { ride ->
                    RideItem(
                        ride = ride,
                        isSelected = selectedIds.contains(ride.id),
                        onClick = { viewModel.toggleSelection(ride.id) },
                        onLongClick = { onShowDetails(ride) },
                        onDelete = { viewModel.deleteRide(ride.id) }
                    )
                }
            }
        }
    }
}
@Composable
fun HistoryScreen(
    viewModel: RideViewModel,
    onShowDetails: (Ride) -> Unit // New Parameter
) {
    val rides by viewModel.claimedRides.collectAsState()

    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
        items(rides) { ride ->
            ClaimedRideItem(
                ride = ride,
                onUnclaim = { viewModel.unclaimRides(listOf(ride.id)) },
                onLongClick = { onShowDetails(ride) },       // Trigger Dialog
                onDelete = { viewModel.deleteRide(ride.id) } // Trigger Delete
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RideItem(
    ride: Ride,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDelete: () -> Unit
) {
    val borderColor = if (isSelected) CyberBlue else CyberPink
    val context = LocalContext.current // Context needed to open the browser

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            ),
        colors = CardDefaults.cardColors(
            containerColor = Color.Black.copy(alpha = 0.3f)
        ),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onClick() },
                colors = CheckboxDefaults.colors(
                    checkedColor = CyberBlue,
                    uncheckedColor = CyberPink,
                    checkmarkColor = CyberBg
                )
            )
            Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                Text(ride.date, style = MaterialTheme.typography.titleMedium, color = Color.White)
                Text(ride.fromAddress, style = MaterialTheme.typography.bodySmall, color = CyberGray, maxLines = 1)
                Text("₹${ride.fare}", style = MaterialTheme.typography.titleLarge, color = CyberPink)
            }

            // --- ACTION BUTTONS ROW ---
            Row(verticalAlignment = Alignment.CenterVertically) {

                // 1. RECEIPT DOWNLOAD BUTTON (Visible only if URL exists)
                if (!ride.receiptUrl.isNullOrEmpty()) {
                    IconButton(
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(ride.receiptUrl))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                e.printStackTrace() // Log error if no browser found
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Download Receipt",
                            tint = CyberGreen // Green for "Get/Download"
                        )
                    }

                    // The "gap" you requested
                    Spacer(modifier = Modifier.width(8.dp))
                }

                // 2. DELETE BUTTON
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, "Delete", tint = CyberBlue)
                }
            }
        }
    }
}
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ClaimedRideItem(
    ride: Ride,
    onUnclaim: () -> Unit,
    onLongClick: () -> Unit, // New
    onDelete: () -> Unit     // New
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            // Added Long Press capability
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null, // Fixes the crash by disabling the incompatible ripple
                onClick = {},
                onLongClick = onLongClick
            ),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF37474F))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(ride.date, style = MaterialTheme.typography.titleMedium, color = Color.White)
                Text(ride.fromAddress, style = MaterialTheme.typography.bodySmall, color = Color.Gray, maxLines = 1)
                Text("₹${ride.fare}", style = MaterialTheme.typography.titleLarge, color = Color.White)
            }

            // Action Buttons
            Row {
                IconButton(onClick = onUnclaim) {
                    Icon(Icons.AutoMirrored.Filled.Undo, "Unclaim", tint = Color(0xFFFFB74D))
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, "Delete", tint = Color(0xFFEF5350))
                }
            }
        }
    }
}

@Composable
fun GmailStatusStrip(
    isConnected: Boolean,
    isSyncing: Boolean,
    onConnect: () -> Unit
) {
    val statusColor = if (isConnected) CyberGreen else CyberBlue
    val statusText = if (isConnected) "SYSTEM::ONLINE" else "SYSTEM::OFFLINE // TAP TO CONNECT"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .height(48.dp) // Fixed height strip
            .clickable(enabled = !isConnected) { onConnect() }, // Only clickable if disconnected
        colors = CardDefaults.cardColors(containerColor = CyberBg),
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.8f))
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            // Background Progress Bar (Visible only when syncing)
            if (isSyncing) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxSize().alpha(0.2f),
                    color = statusColor,
                    trackColor = Color.Transparent
                )
            }

            // Content
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = if (isConnected) Icons.Default.CheckCircle else Icons.Default.PowerOff,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (isSyncing) "SYNC_PROTOCOL_INITIATED..." else statusText,
                    color = statusColor,
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
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
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(1.dp, color)
    ) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, color = color)
            Text(value, style = MaterialTheme.typography.titleLarge, color = color)
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

@Composable
fun SettingsDialog(viewModel: RideViewModel, onDismiss: () -> Unit) {
    var senderEmail by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { senderEmail = viewModel.getSenderEmail() }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CyberBg),
            border = BorderStroke(1.dp, CyberPink) // Pink Border for Settings
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    "SYSTEM_CONFIG",
                    style = MaterialTheme.typography.titleMedium,
                    color = CyberPink,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                // Cyberpunk Text Field
                OutlinedTextField(
                    value = senderEmail,
                    onValueChange = { senderEmail = it },
                    label = { Text("TARGET_EMAIL_ADDRESS") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberPink,
                        unfocusedBorderColor = CyberGray,
                        focusedLabelColor = CyberPink,
                        unfocusedLabelColor = CyberGray,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = CyberGray,
                        cursorColor = CyberPink
                    ),
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace)
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        viewModel.setSenderEmail(senderEmail)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = CyberPink),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text("SAVE CONFIG", color = CyberBg, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
@Composable
fun RideDetailsDialog(ride: Ride, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CyberBg),
            border = BorderStroke(1.dp, CyberBlue) // Blue Border for Info
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "RIDE_DETAILS_LOG",
                    style = MaterialTheme.typography.titleMedium,
                    color = CyberBlue,
                    fontFamily = FontFamily.Monospace
                )

                // Specific Fields Requested
                DetailRow("DATE", "${ride.date} at ${ride.time ?: "N/A"}")
                DetailRow("AMOUNT", "₹${ride.fare}")
                DetailRow("PICKUP", ride.fromAddress)
                DetailRow("DROP", ride.toAddress)
                DetailRow("PAYMENT", ride.payment)

                // Source Logic
                val sourceText = if (ride.source == "gmail_auto") "GMAIL AUTO-SYNC" else "MANUAL ENTRY"
                DetailRow("SOURCE", sourceText)

                // Extra field often useful for debugging
                DetailRow("TRIP_ID", ride.tripId)

                Spacer(modifier = Modifier.height(8.dp))

                // Close Button
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = CyberBlue),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text("CLOSE TERMINAL", color = CyberBg, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}
@Composable
fun DetailRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.Gray
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White
        )
    }
}