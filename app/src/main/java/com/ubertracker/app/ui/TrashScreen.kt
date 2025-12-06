package com.ubertracker.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ubertracker.app.RideViewModel
import com.ubertracker.app.data.Ride
import com.ubertracker.app.ui.theme.CyberBg
import com.ubertracker.app.ui.theme.CyberBlue

// --- ERROR FIX: Removed the "class TrashScreen" wrapper ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(
    viewModel: RideViewModel,
    onBack: () -> Unit
) {
    val trashedRides by viewModel.trashedRides.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recycle Bin", fontFamily = FontFamily.Monospace) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        // Use AutoMirrored for back arrows (good practice)
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (trashedRides.isNotEmpty()) {
                        TextButton(onClick = { viewModel.emptyTrash() }) {
                            Text("EMPTY ALL", color = Color.Red, fontWeight = FontWeight.Bold)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = CyberBg,
                    titleContentColor = CyberBlue
                )
            )
        },
        containerColor = CyberBg
    ) { padding ->
        if (trashedRides.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("Trash is empty", color = Color.Gray, fontFamily = FontFamily.Monospace)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(trashedRides) { ride ->
                    TrashItemRow(
                        ride = ride,
                        onRestore = { viewModel.restoreRide(ride.id) },
                        onDeleteForever = { viewModel.deleteForever(ride.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun TrashItemRow(ride: Ride, onRestore: () -> Unit, onDeleteForever: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A)), // Darker bg for trash
        border = BorderStroke(1.dp, Color.Gray)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Ride Info
            Column(modifier = Modifier.weight(1f)) {
                Text(ride.date, color = Color.White, fontWeight = FontWeight.Bold)
                Text("₹${ride.fare}", color = CyberBlue)
            }

            // Actions
            Row {
                // Restore Button
                IconButton(onClick = onRestore) {
                    Icon(Icons.Default.Refresh, "Restore", tint = Color.Green)
                }
                // Permanent Delete Button
                IconButton(onClick = onDeleteForever) {
                    Icon(Icons.Default.Delete, "Delete Forever", tint = Color.Red)
                }
            }
        }
    }
}