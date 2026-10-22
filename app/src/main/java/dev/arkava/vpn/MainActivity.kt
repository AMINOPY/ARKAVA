package dev.arkava.vpn

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import dev.arkava.vpn.platform.ArkavaVpnService

/** Minimal bootstrap screen for verifying Android's VPN-consent flow. */
class MainActivity : Activity() {
    private val consentRequestCode = 7401

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val title = TextView(this).apply {
            text = "ARKAVA VPN"
            textSize = 24f
            gravity = Gravity.CENTER
        }
        val status = TextView(this).apply {
            text = "Core foundation ready — tunnel engine is not installed yet."
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(24, 16, 24, 24)
        }
        val prepareButton = Button(this).apply {
            text = "Grant VPN permission"
            setOnClickListener { requestVpnConsent() }
        }
        val stopButton = Button(this).apply {
            text = "Stop VPN service"
            setOnClickListener {
                startService(Intent(this@MainActivity, ArkavaVpnService::class.java)
                    .setAction(ArkavaVpnService.ACTION_STOP))
            }
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(24, 24, 24, 24)
            addView(title, LinearLayout.LayoutParams(-1, -2))
            addView(status, LinearLayout.LayoutParams(-1, -2))
            addView(prepareButton, LinearLayout.LayoutParams(-1, -2))
            addView(stopButton, LinearLayout.LayoutParams(-1, -2))
        })
    }

    private fun requestVpnConsent() {
        val consentIntent = VpnService.prepare(this)
        if (consentIntent == null) {
            onVpnConsentGranted()
        } else {
            @Suppress("DEPRECATION")
            startActivityForResult(consentIntent, consentRequestCode)
        }
    }

    @Deprecated("Uses platform activity-result callback for a dependency-light bootstrap.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == consentRequestCode && resultCode == RESULT_OK) onVpnConsentGranted()
    }

    private fun onVpnConsentGranted() {
        startForegroundService(
            Intent(this, ArkavaVpnService::class.java).setAction(ArkavaVpnService.ACTION_PREPARE)
        )
    }
}
