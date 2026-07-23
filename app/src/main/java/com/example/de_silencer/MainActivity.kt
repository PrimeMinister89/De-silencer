package com.example.de_silencer

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.widget.Button
import androidx.core.app.NotificationManagerCompat
import androidx.appcompat.app.AlertDialog
import android.os.PowerManager
import android.net.Uri

class MainActivity : AppCompatActivity() {
    private val REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.POST_NOTIFICATIONS
    )
    private val PERMISSION_REQUEST_CODE = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        checkAndRequestPermissions()

        requestDNDPermission()

        checkNotificationPermission()

        checkBatteryOptimization()

        val serviceIntent = Intent(this, CallService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        val btnWhitelist = findViewById<Button>(R.id.button_list)
        btnWhitelist.setOnClickListener {
            val intent = Intent(this, ListPage::class.java)

            startActivity(intent)
        }

        val btnLog = findViewById<Button>(R.id.button_log) // 假设你的按钮 ID 叫这个
        btnLog.setOnClickListener {
            val intent = Intent(this, LogActivity::class.java)
            startActivity(intent)
        }
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()
        for (permission in REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(permission)
            }
        }
        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), PERMISSION_REQUEST_CODE)
        }
    }

    private fun requestDNDPermission() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!notificationManager.isNotificationPolicyAccessGranted) {
            Toast.makeText(this, "De-silencer 需要能够解除静音的权限，请在设置中允许", Toast.LENGTH_LONG).show()
            val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            startActivity(intent)
        }
    }

    private fun checkNotificationPermission() {
        val packageNames = NotificationManagerCompat.getEnabledListenerPackages(this)
        if (!packageNames.contains(packageName)) {
            // 如果发现用户没给权限，就弹窗引导他去设置里开
            AlertDialog.Builder(this)
                .setTitle("核心权限缺失")
                .setMessage("为了能够拦截微信电话，De-silencer 需要获取「通知读取」权限。请在接下来的系统设置页面中找到 De-silencer 并开启。")
                .setPositiveButton("去开启") { _, _ ->
                    // 使用这个神奇的 Intent，可以直接把用户传送到系统的通知权限设置页！
                    startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }
                .setNegativeButton("取消", null)
                .show()
        }
    }

    private fun checkBatteryOptimization() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        // 检查应用是否已经在白名单中了
        val isIgnoring = powerManager.isIgnoringBatteryOptimizations(packageName)

        if (!isIgnoring) {
            AlertDialog.Builder(this)
                .setTitle("保持后台运行必需权限")
                .setMessage("为了确保在【手机息屏】状态下依然能成功拦截并响铃，请允许 De-silencer 忽略电池优化。")
                .setPositiveButton("去允许") { _, _ ->
                    try {
                        // 唤起系统官方的白名单添加页面
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                        intent.data = Uri.parse("package:$packageName")
                        startActivity(intent)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                .setNegativeButton("稍后", null)
                .show()
        }
    }
}