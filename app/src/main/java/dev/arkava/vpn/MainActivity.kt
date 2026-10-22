package dev.arkava.vpn

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.widget.TextView
import dev.arkava.vpn.platform.ArkavaVpnService

/** Temporary bootstrap only; product UI is deferred. */
class MainActivity : Activity() {
    private val consentRequestCode = 7401
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply {
            text = "ARKAVA VPN\nCore foundation initialized. UI is deferred."
            textSize = 18f
            setPadding(32, 48, 32, 32)
        })
    }
    fun requestVpnConsent() {
        val intent = VpnService.prepare(this)
        if (intent != null) startActivityForResult(intent, consentRequestCode) else onVpnConsentGranted()
    }
    @Deprecated("Use Activity Result APIs when adding production UI")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == consentRequestCode && resultCode == RESULT_OK) onVpnConsentGranted()
    }
    private fun onVpnConsentGranted() {
        startService(Intent(this, ArkavaVpnService::class.java).setAction(ArkavaVpnService.ACTION_PREPARE))
    }
}
