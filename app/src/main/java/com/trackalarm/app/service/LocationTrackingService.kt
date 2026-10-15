package com.trackalarm.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.location.Location
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import com.trackalarm.app.alarm.AlarmPlayer

class LocationTrackingService : Service() {

    private lateinit import: FusedLocationProviderClient // Note: standard client below
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var alarmPlayer: AlarmPlayer

    companion object {
        var currentDistanceMeters: Float = -1f
        var isDestinationReached: Boolean = false
        const val CHANNEL_ID = "TravelAlarmChannel"
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        alarmPlayer = AlarmPlayer(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val destLat = intent?.getDoubleExtra("DEST_LAT", 0.0) ?: 0.0
        val destLon = intent?.getDoubleExtra("DEST_LON", 0.0) ?: 0.0
        val radiusMeters = intent?.getFloatExtra("RADIUS", 1000f) ?: 1000f

        val notification = createNotification("Journey Active. Monitoring distance...")
        startForeground(1, notification)

        startLocationTracking(destLat, destLon, radiusMeters)

        return START_STICKY
    }

    private fun startLocationTracking(destLat: Double, destLon: Double, radiusMeters: Float) {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
            .setMinUpdateIntervalMillis(3000)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                for (location in result.locations) {
                    val targetLocation = Location("Target").apply {
                        latitude = destLat
                        longitude = destLon
                    }
                    val distance = location.distanceTo(targetLocation)
                    currentDistanceMeters = distance

                    if (distance <= radiusMeters) {
                        isDestinationReached = true
                        alarmPlayer.startAlarm()
                        updateNotification("DESTINATION REACHED! Distance: ${distance.toInt()}m")
                    } else {
                        updateNotification("Distance to destination: ${distance.toInt()}m")
                    }
                }
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    private fun createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Travel Alarm Background Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(message: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Travel Alarm Running")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(message: String) {
        val notification = createNotification(message)
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(1, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        alarmPlayer.stopAlarm()
        currentDistanceMeters = -1f
        isDestinationReached = false
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
