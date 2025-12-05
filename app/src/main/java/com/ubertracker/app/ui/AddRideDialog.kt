package com.ubertracker.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.ubertracker.app.data.Ride
import java.text.SimpleDateFormat
import com.ubertracker.app.ui.theme.*
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRideDialog(
    onDismiss: () -> Unit,
    onAdd: (Ride) -> Unit
) {
    var date by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())) }
    var fromAddress by remember { mutableStateOf("") }
    var toAddress by remember { mutableStateOf("") }
    var fare by remember { mutableStateOf("") }
    var payment by remember { mutableStateOf("UPI") }
    var notes by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }


    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CyberBg), // Black Background
            border = BorderStroke(1.dp, CyberPink) // Pink Border

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
                        "MANUAL_OVERRIDE",
                        style = MaterialTheme.typography.titleMedium,
                        color = CyberPink,
                        fontFamily = FontFamily.Monospace
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "Close", tint = CyberGray)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                val cyberTextFieldColors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CyberPink,
                    unfocusedBorderColor = CyberGray,
                    focusedLabelColor = CyberPink,
                    unfocusedLabelColor = CyberGray,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = CyberGray,
                    cursorColor = CyberPink
                )

                val cyberTextStyle = TextStyle(fontFamily = FontFamily.Monospace)

                // Date Field
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text("DATE_LOG") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = cyberTextFieldColors,
                    textStyle = cyberTextStyle
                )

                Spacer(modifier = Modifier.height(16.dp))

                // From Address
                OutlinedTextField(
                    value = fromAddress,
                    onValueChange = { fromAddress = it },
                    label = { Text("ORIGIN_POINT") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = cyberTextFieldColors,
                    textStyle = cyberTextStyle
                )

                Spacer(modifier = Modifier.height(16.dp))

                // To Address
                OutlinedTextField(
                    value = toAddress,
                    onValueChange = { toAddress = it },
                    label = { Text("DESTINATION_POINT") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = cyberTextFieldColors,
                    textStyle = cyberTextStyle
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Fare
                OutlinedTextField(
                    value = fare,
                    onValueChange = { if (it.matches(Regex("^\\d*\\.?\\d*$"))) fare = it },
                    label = { Text("COST_VALUE (INR)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = cyberTextFieldColors,
                    textStyle = cyberTextStyle
                )

                Spacer(modifier = Modifier.height(16.dp))

                var expanded by remember { mutableStateOf(false) }
                val paymentMethods = listOf("UPI", "Card", "Cash", "Wallet")

                // Payment Method Dropdown
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = payment,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("PAYMENT_PROTOCOL") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(
                                expanded = expanded
                            )
                        },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        colors = cyberTextFieldColors,
                        textStyle = cyberTextStyle
                    )

                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.background(CyberBg).border(1.dp, CyberPink)
                    ) {
                        paymentMethods.forEach { method ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        method,
                                        fontFamily = FontFamily.Monospace,
                                        color = Color.White
                                    )
                                },
                                onClick = {
                                    payment = method
                                    expanded = false
                                },
                                colors = MenuDefaults.itemColors(
                                    textColor = Color.White,
                                    leadingIconColor = CyberPink
                                ),
                                modifier = Modifier.background(CyberBg)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("ADDITIONAL_DATA") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = cyberTextFieldColors,
                    textStyle = cyberTextStyle
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Error Message
                if (showError) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Please fill all required fields",
                        color = Color(0xFFEF5350),
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Add Button
                Button(
                    onClick = {
                        val fareValue = fare.toDoubleOrNull()
                        if (fareValue != null && fromAddress.isNotBlank()) {
                            val ride = Ride(
                                date = date,
                                time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()),
                                fromAddress = fromAddress,
                                toAddress = toAddress,
                                fare = fareValue,
                                payment = payment,
                                tripId = "MAN${System.currentTimeMillis()}",
                                source = "manual",
                                notes = notes.ifEmpty { null }
                            )
                            onAdd(ride)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = CyberPink),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text("INJECT DATA", color = CyberBg, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}