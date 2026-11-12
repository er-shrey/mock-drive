package com.shrey.mockdrive

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.min

/**
 * Drives the fake vehicle along [DriveState.path], pushing one fix per second. The loop
 * runs on the main thread (a few cheap calls a second) so Stop can never race a tick.
 * Paused or arrived, it keeps pushing the same spot at speed 0 so the phone stays
 * "parked" there until Stop.
 */
class DriveService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private var pusher: MockLocationPusher? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopDrive()
            ACTION_TOGGLE_PAUSE -> DriveState.paused.value = !DriveState.paused.value
            else -> startDrive()
        }
        return START_NOT_STICKY
    }

    private fun startDrive() {
        // Must go foreground before anything can fail, or Android kills the app.
        ServiceCompat.startForeground(
            this, NOTIF_ID, notification("Starting…"), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
        )
        val path = DriveState.path.value ?: return stopDrive()
        job?.cancel()
        pusher?.stop()
        val p = MockLocationPusher(this)
        try {
            p.start()
        } catch (e: SecurityException) {
            DriveState.error.value = "MockDrive is not the selected mock location app."
            return stopDrive()
        }
        pusher = p
        DriveState.paused.value = false
        DriveState.error.value = null
        if (wakeLock == null) {
            wakeLock = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MockDrive:drive")
                .apply { acquire() }
        }

        job = scope.launch {
            var traveled = 0.0
            var last = SystemClock.elapsedRealtime()
            var lastBearing = path.at(0.0).bearing
            var lastNotif = 0L
            while (isActive) {
                val now = SystemClock.elapsedRealtime()
                val dt = (now - last) / 1000.0
                last = now
                val paused = DriveState.paused.value
                val speed = DriveState.speedMps.value
                if (!paused) traveled = min(path.totalM, traveled + speed * DriveState.timeWarp.value * dt)
                val arrived = traveled >= path.totalM
                val moving = !paused && !arrived && speed > 0
                val fix = path.at(traveled)
                val bearing = if (moving) fix.bearing else lastBearing
                lastBearing = bearing
                val reported = if (moving) speed else 0f
                p.push(fix.lat, fix.lng, reported, bearing)
                val status = DriveStatus(fix.lat, fix.lng, bearing, reported, traveled, path.totalM, paused, arrived)
                DriveState.status.value = status
                if (now - lastNotif >= 3000) {
                    lastNotif = now
                    getSystemService(NotificationManager::class.java).notify(NOTIF_ID, notification(describe(status)))
                }
                delay(1000)
            }
        }
    }

    private fun describe(s: DriveStatus): String {
        val doneMi = s.traveledM / 1609.344
        val totalMi = s.totalM / 1609.344
        val state = when {
            s.totalM == 0.0 -> "Holding position"
            s.arrived -> "Arrived — holding at destination"
            s.paused -> "Paused"
            else -> "Driving %.0f mph".format(s.speedMps / DriveState.MPH)
        }
        return if (s.totalM == 0.0) state else "$state · %.1f / %.1f mi".format(doneMi, totalMi)
    }

    private fun stopDrive() {
        job?.cancel()
        job = null
        pusher?.stop()
        pusher = null
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        DriveState.status.value = null
        DriveState.paused.value = false
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        pusher?.stop()
        wakeLock?.let { if (it.isHeld) it.release() }
        DriveState.status.value = null
        scope.cancel()
        super.onDestroy()
    }

    private fun notification(text: String): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Mock drive", NotificationManager.IMPORTANCE_LOW),
            )
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("MockDrive is faking GPS")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, if (DriveState.paused.value) "Resume" else "Pause", action(ACTION_TOGGLE_PAUSE, 1))
            .addAction(0, "Stop", action(ACTION_STOP, 2))
            .build()
    }

    private fun action(name: String, code: Int) = PendingIntent.getService(
        this, code, Intent(this, DriveService::class.java).setAction(name), PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val ACTION_START = "com.shrey.mockdrive.START"
        const val ACTION_STOP = "com.shrey.mockdrive.STOP"
        const val ACTION_TOGGLE_PAUSE = "com.shrey.mockdrive.TOGGLE_PAUSE"
        private const val CHANNEL = "drive"
        private const val NOTIF_ID = 7

        fun start(context: Context) = ContextCompat.startForegroundService(
            context, Intent(context, DriveService::class.java).setAction(ACTION_START),
        )

        fun send(context: Context, action: String) {
            if (DriveState.status.value != null) {
                context.startService(Intent(context, DriveService::class.java).setAction(action))
            }
        }
    }
}
