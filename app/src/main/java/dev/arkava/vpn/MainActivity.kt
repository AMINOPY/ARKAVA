package dev.arkava.vpn

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import dev.arkava.vpn.core.ConnectionProfile
import dev.arkava.vpn.core.ProfileValidator
import dev.arkava.vpn.platform.ArkavaVpnService
import dev.arkava.vpn.storage.SecureProfileStore
import java.util.UUID

/** Functional bootstrap form for saving an Xray JSON profile and exercising the native core. */
class MainActivity : Activity() {
    private val consentRequestCode = 7401
    private lateinit var profileName: EditText
    private lateinit var profileJson: EditText
    private lateinit var status: TextView
    private var selectedProfileId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selectedProfileId = savedInstanceState?.getString(STATE_PROFILE_ID)
        profileName = EditText(this).apply {
            hint = "Profile name"
            setSingleLine(true)
            setText("My Xray profile")
        }
        profileJson = EditText(this).apply {
            hint = "Paste full Xray JSON configuration (must include outbounds)"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setSingleLine(false)
            minLines = 8
            gravity = Gravity.TOP or Gravity.START
        }
        status = TextView(this).apply {
            text = "Profiles are encrypted in Android Keystore-backed storage. End-to-end tunnel health is not yet verified."
            textSize = 14f
            setPadding(4, 12, 4, 12)
        }
        val saveButton = Button(this).apply {
            text = "Validate and save profile"
            setOnClickListener { saveProfile() }
        }
        val connectButton = Button(this).apply {
            text = "Connect saved profile"
            setOnClickListener { requestVpnConsent() }
        }
        val stopButton = Button(this).apply {
            text = "Disconnect"
            setOnClickListener {
                startService(Intent(this@MainActivity, ArkavaVpnService::class.java).setAction(ArkavaVpnService.ACTION_STOP))
                status.text = "Disconnect requested."
            }
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)
            addView(TextView(this@MainActivity).apply { text = "ARKAVA VPN"; textSize = 24f },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(profileName, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(profileJson, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(saveButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(connectButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(stopButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(status, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        setContentView(ScrollView(this).apply { addView(content) })
    }

    private fun saveProfile() {
        val name = profileName.text.toString().trim()
        val config = profileJson.text.toString().trim()
        val validation = ProfileValidator.validateJson(config)
        if (validation.isFailure) {
            status.text = "Profile rejected: ${validation.exceptionOrNull()?.message.orEmpty()}"
            return
        }
        runCatching {
            val id = selectedProfileId ?: UUID.randomUUID().toString()
            SecureProfileStore(this).save(ConnectionProfile(id, name, "xray", config))
            selectedProfileId = id
            status.text = "Profile saved securely. Connect only with a configuration you trust."
        }.onFailure { status.text = "Could not save profile: ${it.message.orEmpty()}" }
    }

    private fun requestVpnConsent() {
        if (selectedProfileId == null) {
            status.text = "Save a valid Xray JSON profile first."
            return
        }
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
        if (requestCode == consentRequestCode) {
            if (resultCode == RESULT_OK) onVpnConsentGranted()
            else status.text = "VPN permission was not granted."
        }
    }

    private fun onVpnConsentGranted() {
        val id = selectedProfileId ?: return
        startForegroundService(
            Intent(this, ArkavaVpnService::class.java)
                .setAction(ArkavaVpnService.ACTION_CONNECT)
                .putExtra(ArkavaVpnService.EXTRA_PROFILE_ID, id)
        )
        status.text = "Connection requested. Check the persistent notification for core status."
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_PROFILE_ID, selectedProfileId)
        super.onSaveInstanceState(outState)
    }

    private companion object { const val STATE_PROFILE_ID = "selected_profile_id" }
}