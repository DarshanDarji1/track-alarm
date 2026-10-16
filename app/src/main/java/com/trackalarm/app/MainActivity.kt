package com.trackalarm.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.trackalarm.app.service.LocationTrackingService

class MainActivity : ComponentActivity() {

    private var pendingLat: Double = 0.0
    private var pendingLon: Double = 0.0
    private var pendingRadius: Float = 0f
    private var isWaitingForPermission = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val locationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                              permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        
        if (locationGranted && isWaitingForPermission) {
            isWaitingForPermission = false
            startJourney(pendingLat, pendingLon, pendingRadius)
        } else if (!locationGranted && isWaitingForPermission) {
            isWaitingForPermission = false
            Toast.makeText(this, "Location permission is required to track the alarm!", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        askForPermissions()

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(
                        onStart = { lat, lon, radius -> 
                            if (hasLocationPermission()) {
                                startJourney(lat, lon, radius)
                            } else {
                                // Store data and wait for permission result
                                pendingLat = lat
                                pendingLon = lon
                                pendingRadius = radius
                                isWaitingForPermission = true
                                askForPermissions()
                            }
                        },
                        onStop = { stopJourney() }
                    )
                }
            }
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
               ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun askForPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        
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
    val context = LocalContext.current
    var destName by remember { mutableStateOf("") }
    var latText by remember { mutableStateOf("") }
    var lonText by remember { mutableStateOf("") }
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
                    val lat = latText.toDoubleOrNull()
                    val lon = lonText.toDoubleOrNull()
                    
                    if (destName.isBlank() || lat == null || lon == null) {
                        Toast.makeText(context, "Please enter a valid destination, latitude, and longitude.", Toast.LENGTH_SHORT).show()
                    } else {
                        onStart(lat, lon, selectedRadius)
                        isRunning = true
                    }
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
