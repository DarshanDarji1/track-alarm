package com.trackalarm.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.trackalarm.app.service.LocationTrackingService

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Ask for permissions immediately on app launch
        askForPermissions()

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(
                        onStart = { lat, lon, radius -> 
                            askForPermissions() // Double-check when they press Start
                            startJourney(lat, lon, radius) 
                        },
                        onStop = { stopJourney() }
                    )
                }
            }
        }
    }

    private fun askForPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        // Only ask for notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        
        // Filter out permissions that are already granted
        val notGranted = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (notGranted.isNotEmpty()) {
            permissionLauncher.launch(notGranted.toTypedArray())
        }
    }

    private fun startJourney(lat: Double, lon: Double, radius: Float) {
        val intent = Intent(this, LocationTrackingService::class.java).apply {
            putExtra("DEST_LAT", lat)
            putExtra("DEST_LON", lon)
            putExtra("RADIUS", radius)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopJourney() {
        stopService(Intent(this, LocationTrackingService::class.java))
    }
}

@Composable
fun MainScreen(onStart: (Double, Double, Float) -> Unit, onStop: () -> Unit) {
    var destName by remember { mutableStateOf("Vadodara Station") }
    var latText by remember { mutableStateOf("22.3072") }
    var lonText by remember { mutableStateOf("73.1812") }
    var selectedRadius by remember { mutableStateOf(1000f) }
    var isRunning by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Track Alarm",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = destName,
            onValueChange = { destName = it },
            label = { Text("Destination Name") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isRunning
        )
        Spacer(modifier = Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = latText,
                onValueChange = { latText = it },
                label = { Text("Latitude") },
                modifier = Modifier.weight(1f),
                enabled = !isRunning
            )
            OutlinedTextField(
                value = lonText,
                onValueChange = { lonText = it },
                label = { Text("Longitude") },
                modifier = Modifier.weight(1f),
                enabled = !isRunning
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("Alert Radius: ${selectedRadius.toInt()} meters", fontWeight = FontWeight.SemiBold)
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            listOf(500f, 1000f, 2000f, 5000f).forEach { r ->
                FilterChip(
                    selected = selectedRadius == r,
                    onClick = { if (!isRunning) selectedRadius = r },
                    label = { Text("${r.toInt()}m") }
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        if (!isRunning) {
            Button(
                onClick = {
                    val lat = latText.toDoubleOrNull() ?: 22.3072
                    val lon = lonText.toDoubleOrNull() ?: 73.1812
                    onStart(lat, lon, selectedRadius)
                    isRunning = true
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Start Journey", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        } else {
            Button(
                onClick = {
                    onStop()
                    isRunning = false
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Stop Journey & Alarm", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
