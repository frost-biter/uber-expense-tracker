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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.lifecycleScope
import com.ubertracker.app.OneTimeEvent
import com.ubertracker.app.R
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
    
    private val consentLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // After user grants consent, recheck connection and try syncing again
        viewModel.recheckGmailConnection()
        // Note: syncGmail() and scheduleDailyGmailSyncs() will be called automatically
        // when connection is restored, but we can trigger a manual sync here
        viewModel.syncGmail()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            viewModel.oneTimeEvent.collectLatest { event ->
                when (event) {
                    is OneTimeEvent.StartSignIn -> {
                        signInLauncher.launch(event.signInIntent)
                    }
                    is OneTimeEvent.RequestGmailConsent -> {
                        // User needs to grant consent - launch the consent Intent
                        consentLauncher.launch(event.consentIntent)
                    }
                    is OneTimeEvent.GmailAuthFailed -> {
                        // Auth failure will be handled in the UI via state change
                        // The connection state will be updated, triggering UI refresh
                    }
                }
            }
        }

        setContent {
            UberTrackerTheme {
                // Remove Surface wrapper to allow background to show through
                MainScreen(viewModel)
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
    var rideToEdit by remember { mutableStateOf<Ride?>(null) }
    val pagerState = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()
    val titles = listOf("Pending", "History")
    val gmailConnected by viewModel.gmailConnected.collectAsState()
    val syncing by viewModel.syncing.collectAsState()
    val stats by viewModel.stats.collectAsState()
    var currentScreen by remember { mutableStateOf("home") }


    when (currentScreen) {
        "trash" -> {
            // Show the Trash Screen file you created
            TrashScreen(
                viewModel = viewModel,
                onBack = { currentScreen = "home" }
            )
        }
        "home" -> {
            // YOUR EXISTING DASHBOARD UI
            Box(modifier = Modifier.fillMaxSize()) {
                // --- LAYER 1: THE BACKGROUND IMAGE ---
                Image(
                    painter = painterResource(id = R.drawable.bg_image),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                // --- LAYER 3: SCAFFOLD (Transparent to show background) ---
                Scaffold(
                    containerColor = Color.Transparent, // Make transparent to show background
                    topBar = {
                        CenterAlignedTopAppBar(
                            title = {
                                Text(
                                    "CYBERPUNK TRACKER",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = CyberPink
                                )
                            },
                            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                containerColor = Color.Transparent // Transparent to show background
                            ),
                            actions = {
                                // --- NEW TRASH BUTTON ---
                                IconButton(onClick = { currentScreen = "trash" }) {
                                    Icon(Icons.Default.Delete, "Trash", tint = CyberGray)
                                }

                                // Existing Settings Button
                                IconButton(onClick = { showSettingsDialog = true }) {
                                    Icon(Icons.Default.Settings, "Settings", tint = CyberBlue)
                                }
                            }
                        )
                    },
                    floatingActionButton = {
                        FloatingActionButton(
                            onClick = { showAddDialog = true },
                            containerColor = CyberPink,
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
            }
            // --- DIALOGS (Only show these when on Home screen) ---
            if (showAddDialog) {
                AddRideDialog(
                    onDismiss = { showAddDialog = false },
                    onAdd = { ride -> viewModel.addManualRide(ride) }
                )
            }

            if (showSettingsDialog) {
                SettingsDialog(
                    viewModel = viewModel,
                    onDismiss = { showSettingsDialog = false }
                )
            }

            if (rideDetailToShow != null) {
                val currentRide = rideDetailToShow!!
                RideDetailsDialog(
                    ride = currentRide,
                    onDismiss = { rideDetailToShow = null },
                    onEdit = {
                        rideToEdit = currentRide
                        rideDetailToShow = null
                    }
                )
            }

            if (rideToEdit != null) {
                AddRideDialog(
                    rideToEdit = rideToEdit,
                    onDismiss = { rideToEdit = null },
                    onAdd = { ride ->
                        viewModel.updateRide(ride)
                        rideToEdit = null
                    }
                )
            }
        }
    }
}
@Composable
fun NeonCard(
    glowColor: Color,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 16.dp,
    paddingInner: Dp = 12.dp,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)

    // 1. The Parent Container (Holds the shape, border, and shadow)
    Box(
        modifier = modifier
            .shadow(
                elevation = 10.dp, // Reduced slightly for cleaner look
                shape = shape,
                ambientColor = glowColor.copy(alpha = 0.35f),
                spotColor = glowColor.copy(alpha = 0.55f)
            )
            .border(
                width = 2.dp,
                brush = Brush.verticalGradient(
                    listOf(
                        glowColor.copy(alpha = 1f),
                        glowColor.copy(alpha = 0.5f)
                    )
                ),
                shape = shape
            )
            .clip(shape) // Clip everything inside to the card shape
    ) {
        // 2. LAYER A: The "Glass" Background
        // We use matchParentSize() so it fills the card behind the content
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Color.Black.copy(alpha = 0.65f)) // Translucent fill
                .blur(20.dp) // Apply blur ONLY to this background layer
        )

        // 3. LAYER B: The Content (Text, Buttons)
        // This is a sibling to Layer A, so it does NOT get blurred
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(paddingInner)
        ) {
            content()
        }
    }
}



@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class) // Added FoundationApi for combinedClickable
@Composable
fun PendingScreen(
    viewModel: RideViewModel,
    onShowDetails: (Ride) -> Unit
) {
    val rides by viewModel.unclaimedRides.collectAsState()
    val selectedIds by viewModel.selectedRideIds.collectAsState()
    val isSyncing by viewModel.syncing.collectAsState()
    val state = rememberPullToRefreshState()

    Scaffold(
        containerColor = Color.Transparent, // Transparent so background shows through glass
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
        PullToRefreshBox(
            isRefreshing = isSyncing,
            onRefresh = { viewModel.syncGmail() },
            state = state,
            modifier = Modifier.padding(padding),
            indicator = {
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
                    // Logic: Green if selected, Pink if not
                    val glowColor = if (selectedIds.contains(ride.id)) NeonGreen else NeonPink

                    // --- THE NEW NEON CARD IMPLEMENTATION ---
                    NeonCard(
                        glowColor = glowColor,
                        modifier = Modifier
                            .fillMaxWidth()
                            // Handle Clicks here on the wrapper
                            .combinedClickable(
                                onClick = { viewModel.toggleSelection(ride.id) },
                                onLongClick = { onShowDetails(ride) }
                            )
                    ) {
                        // --- CONTENT INSIDE THE CARD ---
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Checkbox
                            Checkbox(
                                checked = selectedIds.contains(ride.id),
                                onCheckedChange = null, // Handled by card click above
                                colors = CheckboxDefaults.colors(
                                    checkedColor = NeonGreen,
                                    uncheckedColor = glowColor,
                                    checkmarkColor = Color.Black,
                                    // Make the border match the neon theme
                                    disabledCheckedColor = NeonGreen,
                                    disabledUncheckedColor = glowColor
                                )
                            )

                            // Date & Address
                            Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                                Text(ride.date, color = Color.White, fontWeight = FontWeight.Bold)
                                Text(
                                    ride.fromAddress,
                                    color = Color.Gray  ,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1
                                )
                            }

                            // Price
                            Text(
                                "₹${ride.fare}",
                                color = glowColor,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )

                            // Action Buttons Row
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Download Receipt Button (if receiptUrl exists)
                                val context = LocalContext.current
                                if (!ride.receiptUrl.isNullOrEmpty()) {
                                    IconButton(
                                        onClick = {
                                            try {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(ride.receiptUrl))
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Download,
                                            contentDescription = "Download Receipt",
                                            tint = NeonGreen // Green for download
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                }

                                // Delete Icon
                                IconButton(onClick = { viewModel.deleteRide(ride.id) }) {
                                    Icon(Icons.Default.Delete, "Delete", tint = Color.Red.copy(alpha = 0.7f))
                                }
                            }
                        }
                    }
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
    onLongClick: () -> Unit,
    onDelete: () -> Unit
) {
    // Define the Green glow for "Claimed" items
    val claimColor = Color(0xFF00FF99)
    val shape = RoundedCornerShape(12.dp)

    // 1. Parent Container: Handles Shape, Border, Glow, and Click Logic
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            // SHADOW / GLOW
            .shadow(
                elevation = 10.dp,
                shape = shape,
                ambientColor = claimColor.copy(alpha = 0.3f),
                spotColor = claimColor.copy(alpha = 0.5f)
            )
            // NEON BORDER
            .border(
                width = 2.dp,
                brush = Brush.verticalGradient(
                    listOf(claimColor.copy(alpha = 1f), claimColor.copy(alpha = 0.3f))
                ),
                shape = shape
            )
            .clip(shape)
            // CLICK LISTENER (Applied to the wrapper)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null, // Disable ripple to prevent crash/visual glitch on glass
                onClick = {},      // Empty click (consumes touch)
                onLongClick = onLongClick
            )
    ) {
        // 2. LAYER A: The "Black Glass" Background (Blurred)
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Color.Black.copy(alpha = 0.5f)) // Dark semi-transparent background
                .blur(12.dp) // The frost effect
        )

        // 3. LAYER B: The Content (Sharp Text & Buttons)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp), // Padding inside the glass card
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Text Details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = ride.date,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = ride.fromAddress,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    maxLines = 1
                )
                Text(
                    text = "₹${ride.fare}",
                    style = MaterialTheme.typography.titleLarge,
                    color = claimColor, // Green Text for the Amount
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            // Action Buttons
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onUnclaim) {
                    Icon(
                        Icons.AutoMirrored.Filled.Undo,
                        "Unclaim",
                        tint = Color(0xFFFFB74D) // Orange for "Undo/Warning"
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        "Delete",
                        tint = Color(0xFFEF5350) // Red for Delete
                    )
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
fun StatCard(
    title: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(12.dp)

    // 1. Parent Container: Handles Shape, Border, and Glow Shadow
    Box(
        modifier = modifier
            .shadow(
                elevation = 16.dp,
                shape = shape,
                ambientColor = color.copy(alpha = 0.4f),
                spotColor = color.copy(alpha = 0.6f)
            )
            .border(
                width = 2.dp,
                brush = Brush.verticalGradient(
                    listOf(color.copy(alpha = 1f), color.copy(alpha = 0.5f))
                ),
                shape = shape
            )
            .clip(shape) // Clips the inner glass layer to the rounded corners
    ) {
        // 2. LAYER A: The Glass Background (Blurred)
        Box(
            modifier = Modifier
                .matchParentSize() // Fills the available space behind content
                .background(Color.Black.copy(alpha = 0.35f)) // Subtle translucent fill
                .blur(12.dp) // The frosted effect
        )

        // 3. LAYER B: The Content (Sharp Text)
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = color.copy(alpha = 0.8f), // Slightly dimmer than the value
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = color, // Bright neon color
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
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

@Composable
fun SettingsDialog(viewModel: RideViewModel, onDismiss: () -> Unit) {
    var senderEmail by remember { mutableStateOf("") }

    // Load current email when dialog opens
    LaunchedEffect(Unit) {
        senderEmail = viewModel.getSenderEmail()
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CyberBg),
            border = BorderStroke(1.dp, CyberPink)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    "SYSTEM_CONFIG",
                    style = MaterialTheme.typography.titleMedium,
                    color = CyberPink,
                    modifier = Modifier.padding(bottom = 24.dp),
                    fontFamily = FontFamily.Monospace
                )

                // Input Field
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
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace),
                    singleLine = true,
                    // FEATURE: Pressing 'Done' on keyboard saves & closes
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            viewModel.setSenderEmail(senderEmail)
                            onDismiss()
                        }
                    )
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Save Button
                Button(
                    onClick = {
                        viewModel.setSenderEmail(senderEmail)
                        onDismiss() // <--- CRITICAL: This closes the dialog
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = CyberPink),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        "SAVE CONFIG",
                        color = CyberBg,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
fun RideDetailsDialog(ride: Ride, onDismiss: () -> Unit, onEdit: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CyberBg),
            border = BorderStroke(1.dp, CyberBlue) // Blue Border for Info
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                // Edit button in top-right corner
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                ) {
                    Icon(
                        Icons.Default.Edit,
                        "Edit",
                        tint = CyberPink,
                        modifier = Modifier.size(24.dp)
                    )
                }
                
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .padding(top = 8.dp), // Extra top padding to avoid overlap with Edit button
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