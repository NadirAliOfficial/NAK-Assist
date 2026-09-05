package com.teamnak.nakassist

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.color.DynamicColors
import com.google.android.material.switchmaterial.SwitchMaterial

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        DynamicColors.applyToActivityIfAvailable(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
            android.view.WindowManager.LayoutParams.FLAG_SECURE
        )

        findViewById<View>(R.id.rowOverlay).setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")))
            } else {
                Toast.makeText(this, "Already granted", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<View>(R.id.rowAccessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        findViewById<View>(R.id.rowNotification).setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
        bottomNav.selectedItemId = R.id.navSettings
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navSettings -> true
                R.id.navMessages -> {
                    startActivity(Intent(this, ClientsActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
                    overridePendingTransition(0, 0)
                    true
                }
                else -> false
            }
        }

        val switchAway = findViewById<SwitchMaterial>(R.id.switchAwayMode)
        MessageNotificationService.awayMode = PersistenceHelper.loadAwayMode(this)
        switchAway.isChecked = MessageNotificationService.awayMode

        switchAway.setOnCheckedChangeListener { _, isChecked ->
            MessageNotificationService.awayMode = isChecked
            PersistenceHelper.saveAwayMode(this, isChecked)
            FloatingButtonManager.setAwayMode(isChecked)
            Toast.makeText(this,
                if (isChecked) "Away Mode ON — drafts replies as notifications for you to review & send"
                else "Away Mode OFF",
                Toast.LENGTH_SHORT).show()
        }

        // API Keys
        val etKeys = findViewById<EditText>(R.id.etApiKeys)
        etKeys.setText(GroqApiHelper.getSavedKeys(this).replace(",", "\n"))
        findViewById<Button>(R.id.btnSaveKeys).setOnClickListener {
            val keys = etKeys.text.toString()
                .split("\n").map { it.trim() }.filter { it.isNotEmpty() }
                .joinToString(",")
            GroqApiHelper.saveKeys(this, keys)
            Toast.makeText(this, "Keys saved", Toast.LENGTH_SHORT).show()
        }

        // Stats
        StatsTracker.init(this)
        findViewById<TextView>(R.id.tvStats).text = StatsTracker.getTodaySummary()

        updateStatus()
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
        try { findViewById<TextView>(R.id.tvStats).text = StatsTracker.getTodaySummary() } catch (_: Exception) {}
    }

    private fun updateStatus() {
        val hasOverlay = Settings.canDrawOverlays(this)
        val hasAccessibility = isAccessibilityEnabled()
        val hasNotification = isNotificationListenerEnabled()

        val status = when {
            hasNotification && hasOverlay && hasAccessibility -> "✅ Fully set up — open Fiverr and tap ⚡"
            hasNotification -> "🟡 Aggregator ready — Smart Reply needs overlay + accessibility"
            else -> "⚪ Grant Notification Access to get started"
        }
        findViewById<TextView>(R.id.tvStatus).text = status

        setPermissionState(R.id.tvNotificationState, hasNotification)
        setPermissionState(R.id.tvOverlayState, hasOverlay)
        setPermissionState(R.id.tvAccessibilityState, hasAccessibility)
    }

    private fun setPermissionState(viewId: Int, granted: Boolean) {
        val tv = findViewById<TextView>(viewId)
        tv.text = if (granted) "Granted" else "Not granted"
        tv.setTextColor(android.graphics.Color.parseColor(if (granted) "#81C784" else "#E57373"))
    }

    private fun isAccessibilityEnabled(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.contains(packageName)
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver, "enabled_notification_listeners"
        ) ?: return false
        return enabled.contains(packageName)
    }
}
