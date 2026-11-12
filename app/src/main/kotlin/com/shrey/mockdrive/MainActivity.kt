package com.shrey.mockdrive

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.core.content.ContextCompat
import com.shrey.mockdrive.ui.MainScreen
import org.osmdroid.config.Configuration
import java.io.File

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (hasLocationPermission()) startDrive()
        else Toast.makeText(this, "Location permission is needed to run the fake-GPS service", Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().apply {
            userAgentValue = "MockDrive/0.1 (Android location test tool)"
            osmdroidBasePath = File(cacheDir, "osmdroid")
            osmdroidTileCache = File(cacheDir, "osmdroid/tiles")
        }
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                MainScreen(
                    vm = vm,
                    onStart = ::onStartClicked,
                    onPauseToggle = { DriveService.send(this, DriveService.ACTION_TOGGLE_PAUSE) },
                    onStop = { DriveService.send(this, DriveService.ACTION_STOP) },
                    onOpenDevOptions = ::openDeveloperOptions,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.refreshMockAllowed()
    }

    private fun onStartClicked() {
        val needed = buildList {
            if (!hasLocationPermission()) add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (!hasLocationPermission()) add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= 33 && !granted(Manifest.permission.POST_NOTIFICATIONS)) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (needed.isNotEmpty()) permissions.launch(needed.toTypedArray()) else startDrive()
    }

    private fun startDrive() {
        vm.refreshMockAllowed()
        if (!MockLocationPusher.isSelectedMockApp(this)) {
            vm.setMockHelp(true)
            return
        }
        val path = vm.drivePath() ?: return
        DriveState.path.value = path
        DriveService.start(this)
    }

    private fun openDeveloperOptions() {
        try {
            startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
        } catch (e: ActivityNotFoundException) {
            startActivity(Intent(Settings.ACTION_DEVICE_INFO_SETTINGS))
        }
    }

    private fun hasLocationPermission() = granted(Manifest.permission.ACCESS_FINE_LOCATION)

    private fun granted(p: String) =
        ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED
}
