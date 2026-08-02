package com.example.umbrellaautolock

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.umbrellaautolock.ui.theme.UmbrellaAutoLockTheme

class MainActivity : ComponentActivity() {

    private val REQUEST_CODE_PERMISSIONS = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        checkAndRequestPermissions()
        requestIgnoreBatteryOptimizations() // バッテリー最適化の解除を要求

        setContent {
            UmbrellaAutoLockTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(
                        onStartService = { startBleService() },
                        onStopService = { stopBleService() }
                    )
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissionsList = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionsList.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            permissionsList.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsList.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val needed = permissionsList.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), REQUEST_CODE_PERMISSIONS)
        }
    }

    // Androidのバッテリー最適化からこのアプリを除外させる処理
    private fun requestIgnoreBatteryOptimizations() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        }
    }

    private fun startBleService() {
        val intent = Intent(this, BluetoothAdvertiseService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopBleService() {
        val intent = Intent(this, BluetoothAdvertiseService::class.java)
        stopService(intent)
    }
}

@Composable
fun MainScreen(onStartService: () -> Unit, onStopService: () -> Unit) {
    var isRunning by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = if (isRunning) "🛡️ 発信中（スリープ回避モード）" else "🛑 停止中",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = {
                    if (isRunning) {
                        onStopService()
                        isRunning = false
                    } else {
                        onStartService()
                        isRunning = true
                    }
                },
                modifier = Modifier.width(200.dp).height(60.dp)
            ) {
                Text(text = if (isRunning) "サービス停止" else "発信スタート")
            }
        }
    }
}