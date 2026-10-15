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
import androidx.compose.ui.platform.ContextCompat
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trackalarm.app.service.LocationTrackingService

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Handle permissions granted/denied if needed
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestAllPermissions()

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(
                        onStartJourney = { lat, lon, radius ->
                            startJourneyService(lat, lon, radius)
                        },
                        onStopJourney = {
                            stopJourneyService()
                        }
                    )
                }
            }
        }
    }

    private fun requestAllPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.VIBRATE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissions.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
        requestPermissionLauncher.launch(permissions.toTypedArray())
    }

    private fun startJourneyService(lat: Double, lon: Double, radius: Float) {
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

    private fun stopJourneyService() {
        val intent = Intent(this, LocationTrackingService::class.java)
        stopService(intent)
    }
}

@Composable
fun MainScreen(onStartJourney: (Double, Double, Float) -> Unit, onStopJourney: () -> Unit) {
    var destName by remember { mutableStateOf("Vadodara Station") }
    var latText by remember { mutableStateOf("22.3072") }
    var lonText by remember { mutableStateOf("73.1812") }
    var selectedRadius by remember { mutableStateOf(1000f) } // Default 1 km
    var isJourneyActive by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Travel Alarm",
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = destName,
            onValueChange = { destName = it },
            label = { Text("Destination Name") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isJourneyActive
        )
        Spacer(modifier = Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = latText,
                onValueChange = { latText = it },
                label = { Text("Latitude") },
                modifier = Modifier.weight(1f),
                enabled = !isJourneyActive
            )
            OutlinedTextField(
                value = lonText,
                onValueChange = { lonText = it },
                label = { Text("Longitude") },
                modifier = Modifier.weight(1f),
                enabled = !isJourneyActive
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("Alert Radius: ${selectedRadius.toInt()} meters", fontWeight = FontWeight.Medium)
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            listOf(500f, 1000f, 2000f, 5000f).forEach { radius ->
                FilterChip(
                    selected = selectedRadius == radius,
                    onClick = { if (!isJourneyActive) selectedRadius = radius },
                    label = { Text("${radius.toInt()}m") }
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        if (!isJourneyActive) {
            Button(
                onClick = {
                    val lat = latText.toDoubleOrNull() ?: 22.3072
                    val lon = lonText.toDoubleOrNull() ?: 73.1812
                    onStartJourney(lat, lon, selectedRadius)
                    isJourneyActive = true
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Start Journey", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        } else {
            Button(
                onClick = {
                    onStopJourney()
                    isJourneyActive = false
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
