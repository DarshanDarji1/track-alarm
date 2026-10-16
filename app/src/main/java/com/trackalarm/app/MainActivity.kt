package com.trackalarm.app

import android.Manifest
import android.content.Context
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.trackalarm.app.service.LocationTrackingService
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker

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
    var destName by remember { mutableStateOf("Map Destination") }
    var latText by remember { mutableStateOf("22.3072") }
    var lonText by remember { mutableStateOf("73.1812") }
    var selectedRadius by remember { mutableStateOf(1000f) }
    var isRunning by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Track Alarm",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))

        // OSMDroid Map Integration
        AndroidView(
            factory = { ctx ->
                Configuration.getInstance().load(ctx, ctx.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    controller.setZoom(12.0)
                    controller.setCenter(GeoPoint(22.3072, 73.1812))

                    var currentMarker: Marker? = null

                    val mapEventsReceiver = object : MapEventsReceiver {
                        override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                            if (!isRunning) {
                                latText = p.latitude.toString()
                                lonText = p.longitude.toString()
                                
                                if (currentMarker == null) {
                                    currentMarker = Marker(this@apply).apply {
                                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                    }
                                    overlays.add(currentMarker)
                                }
                                currentMarker?.position = p
                                invalidate()
                            }
                            return true
                        }
                        override fun longPressHelper(p: GeoPoint): Boolean = false
                    }
                    overlays.add(MapEventsOverlay(mapEventsReceiver))
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = latText,
                onValueChange = { latText = it },
                label = { Text("Lat") },
                modifier = Modifier.weight(1f),
                enabled = !isRunning,
                singleLine = true
            )
            OutlinedTextField(
                value = lonText,
                onValueChange = { lonText = it },
                label = { Text("Lon") },
                modifier = Modifier.weight(1f),
                enabled = !isRunning,
                singleLine = true
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
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

        Spacer(modifier = Modifier.height(16.dp))

        if (!isRunning) {
            Button(
                onClick = {
                    val lat = latText.toDoubleOrNull()
                    val lon = lonText.toDoubleOrNull()
                    
                    if (lat == null || lon == null) {
                        Toast.makeText(context, "Please select a point on the map.", Toast.LENGTH_SHORT).show()
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
