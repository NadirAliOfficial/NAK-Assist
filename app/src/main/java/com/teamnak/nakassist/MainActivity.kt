package com.teamnak.nakassist

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
            android.view.WindowManager.LayoutParams.FLAG_SECURE
        )

        findViewById<Button>(R.id.btnOverlay).setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")))
            } else {
                Toast.makeText(this, "Already granted", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<Button>(R.id.btnAccessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        findViewById<Button>(R.id.btnNotification).setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        val btnAway = findViewById<Button>(R.id.btnAwayMode)
        btnAway.setOnClickListener {
            MessageNotificationService.awayMode = !MessageNotificationService.awayMode
            val on = MessageNotificationService.awayMode
            btnAway.text = if (on) "💤 Away Mode: ON" else "💤 Away Mode: OFF"
            btnAway.backgroundTintList = android.content.res.ColorStateList.valueOf(
                if (on) android.graphics.Color.parseColor("#9C27B0")
                else android.graphics.Color.parseColor("#555555")
            )
            FloatingButtonManager.setAwayMode(on)
            Toast.makeText(this,
                if (on) "Away Mode ON — auto-replies enabled" else "Away Mode OFF",
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
            Toast.makeText(this, "✅ Keys saved", Toast.LENGTH_SHORT).show()
        }

        updateStatus()
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun updateStatus() {
        val hasOverlay = Settings.canDrawOverlays(this)
        val hasAccessibility = isAccessibilityEnabled()
        val hasNotification = isNotificationListenerEnabled()

        val status = when {
            hasOverlay && hasAccessibility && hasNotification -> "✅ Fully ready — open Fiverr and tap ⚡"
            hasOverlay && hasAccessibility -> "⚠️ Ready (no notification access)"
            !hasOverlay -> "❌ Missing overlay permission"
            else -> "❌ Enable accessibility service"
        }
        findViewById<TextView>(R.id.tvStatus).text = status
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
