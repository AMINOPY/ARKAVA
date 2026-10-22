package dev.arkava.vpn

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.VpnService
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.animation.AnimationUtils
import android.view.animation.DecelerateInterpolator
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import dev.arkava.vpn.core.ConnectionProfile
import dev.arkava.vpn.core.ProfileValidator
import dev.arkava.vpn.platform.ArkavaVpnService
import dev.arkava.vpn.storage.SecureProfileStore
import java.util.UUID

/** PHANTOM COMMAND: tactical glass UI layered over the existing VPN bootstrap. */
class MainActivity : Activity() {
    private val consentRequestCode = 7401
    private val bg = Color.rgb(6, 11, 14)
    private val panel = Color.rgb(14, 23, 27)
    private val panelRaised = Color.rgb(19, 31, 35)
    private val green = Color.rgb(104, 255, 183)
    private val textMain = Color.rgb(231, 244, 239)
    private val textMuted = Color.rgb(135, 159, 153)
    private val amber = Color.rgb(255, 202, 112)
    private val red = Color.rgb(255, 105, 116)

    private lateinit var root: LinearLayout
    private lateinit var pageBody: LinearLayout
    private lateinit var status: TextView
    private lateinit var profileName: EditText
    private lateinit var profileJson: EditText
    private var selectedProfileId: String? = null
    private var selectedPage = PAGE_HOME
    private var serviceState = "STANDBY"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selectedProfileId = savedInstanceState?.getString(STATE_PROFILE_ID)
        selectedPage = savedInstanceState?.getString(STATE_PAGE) ?: PAGE_HOME
        render()
    }

    private fun render() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), dp(14))
            setBackgroundColor(bg)
        }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(12))
        }
        top.addView(text("☰", 27f, green, true).apply {
            gravity = Gravity.CENTER
            background = rounded(panelRaised, 15)
            setPadding(dp(13), dp(7), dp(13), dp(7))
            setOnClickListener { showNavigation() }
        }, LinearLayout.LayoutParams(dp(52), dp(48)))
        val brand = column().apply {
            addView(text("ARKAVA", 19f, textMain, true))
            addView(text("PHANTOM COMMAND  /  CORE UI", 9f, green, true))
        }
        top.addView(brand, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(12) })
        top.addView(text("●", 13f, if (serviceState == "ACTIVE") green else amber, true))
        top.addView(text("  " + if (serviceState == "ACTIVE") "ONLINE" else "STANDBY", 10f, textMuted, true))
        root.addView(top)

        pageBody = column()
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            addView(pageBody, ScrollView.LayoutParams(-1, -2))
        }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = rounded(panel, 24)
            setPadding(dp(5), dp(5), dp(5), dp(5))
        }
        listOf(
            Triple(PAGE_HOME, "⌁", "RADAR"),
            Triple(PAGE_NODES, "▦", "NODES"),
            Triple(PAGE_INSIGHT, "⌁", "INSIGHT"),
            Triple(PAGE_CONTROL, "⚙", "CONTROL")
        ).forEach { (page, icon, label) ->
            nav.addView(column().apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(8), 0, dp(8))
                addView(text(icon, 19f, if (selectedPage == page) green else textMuted, true))
                addView(text(label, 9f, if (selectedPage == page) green else textMuted, true))
                background = if (selectedPage == page) rounded(Color.rgb(25, 51, 45), 17) else null
                setOnClickListener { selectedPage = page; render() }
            }, LinearLayout.LayoutParams(0, -2, 1f))
        }
        root.addView(nav, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        setContentView(root)
        when (selectedPage) {
            PAGE_HOME -> renderHome()
            PAGE_NODES -> renderNodes()
            PAGE_INSIGHT -> renderInsight()
            else -> renderControl()
        }
        animatePageEntrance()
    }

    /** Staggered HUD reveal: XML defines motion, Kotlin staggers it per component. */
    private fun animatePageEntrance() {
        if (!::pageBody.isInitialized) return
        for (index in 0 until pageBody.childCount) {
            val child = pageBody.getChildAt(index)
            val reveal = AnimationUtils.loadAnimation(this, R.anim.phantom_page_reveal)
            reveal.startOffset = (index * 42L).coerceAtMost(260L)
            child.startAnimation(reveal)
        }
    }

    private fun renderHome() {
        pageBody.addView(text("TACTICAL NETWORK DISPLAY", 10f, textMuted, true))
        pageBody.addView(text("Secure your connection.", 25f, textMain, true).apply {
            setPadding(0, dp(4), 0, dp(2))
        })
        pageBody.addView(text("REAL-TIME CORE STATUS  /  X-RAY ENGINE", 10f, green, true))
        val radarPanel = card().apply {
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(14), dp(12), dp(14))
        }
        radarPanel.addView(PhantomRadarView(this).apply {
            setMode(false)
            contentDescription = "Decorative radar visualization. It does not indicate verified connectivity."
        }, LinearLayout.LayoutParams(-1, dp(260)))
        radarPanel.addView(text("NO SIGNAL", 20f, red, true).apply { gravity = Gravity.CENTER })
        radarPanel.addView(text("TUNNEL STATUS IS NOT VERIFIED", 9f, textMuted, true).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(2))
        })
        pageBody.addView(radarPanel, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })

        val profile = currentProfile()
        val serverCard = card().apply { setPadding(dp(15), dp(14), dp(15), dp(14)) }
        serverCard.addView(text("ACTIVE PROFILE", 10f, textMuted, true))
        serverCard.addView(text(profile?.name ?: "No profile selected", 17f, textMain, true).apply {
            setPadding(0, dp(5), 0, dp(3))
        })
        serverCard.addView(text(profile?.let { "ENGINE  /  " + it.engineId.uppercase() } ?: "Add an Xray profile in NODES", 10f, green, true))
        serverCard.addView(actionButton("SELECT CONFIGURATION", false) {
            selectedPage = PAGE_NODES; render()
        }, LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(12) })
        pageBody.addView(serverCard, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })

        val metrics = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        metrics.addView(metric("LATENCY", "— ms", "awaiting test"), LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(5) })
        metrics.addView(metric("DOWNLOAD", "—", "not measured"), LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(3); rightMargin = dp(3) })
        metrics.addView(metric("UPLOAD", "—", "not measured"), LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(5) })
        pageBody.addView(metrics, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })

        pageBody.addView(actionButton("⟡   CONNECT", true) { requestVpnConsent() },
            LinearLayout.LayoutParams(-1, dp(56)).apply { topMargin = dp(14) })
        pageBody.addView(actionButton("DISCONNECT", false) {
            startService(Intent(this, ArkavaVpnService::class.java).setAction(ArkavaVpnService.ACTION_STOP))
            serviceState = "STANDBY"
            showStatus("Disconnect requested. Confirm final state in Android VPN status.")
        }, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(8) })
        status = text("CORE CONNECTIVITY HAS NOT BEEN VERIFIED", 11f, amber, true).apply {
            setPadding(dp(12), dp(13), dp(12), dp(13))
            background = rounded(Color.rgb(42, 34, 20), 14)
        }
        pageBody.addView(status, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
    }

    private fun renderNodes() {
        pageBody.addView(text("NODE MANAGEMENT", 10f, green, true))
        pageBody.addView(text("Configurations", 26f, textMain, true).apply { setPadding(0, dp(4), 0, dp(3)) })
        pageBody.addView(text("Profiles are encrypted locally. Ping and reachability tests are not yet wired into this screen.", 12f, textMuted))
        val profiles = runCatching { SecureProfileStore(this).loadAll() }.getOrDefault(emptyList())
        if (profiles.isEmpty()) {
            val empty = card().apply { gravity = Gravity.CENTER; setPadding(dp(18), dp(24), dp(18), dp(24)) }
            empty.addView(text("◈", 32f, green, true).apply { gravity = Gravity.CENTER })
            empty.addView(text("NO CONFIGURATIONS", 14f, textMain, true).apply {
                gravity = Gravity.CENTER; setPadding(0, dp(8), 0, dp(4))
            })
            empty.addView(text("Paste a valid Xray JSON profile below to get started.", 12f, textMuted).apply { gravity = Gravity.CENTER })
            pageBody.addView(empty, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
        } else {
            profiles.forEach { profile ->
                val item = card().apply { setPadding(dp(14), dp(12), dp(14), dp(12)) }
                val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                val details = column()
                details.addView(text(profile.name, 16f, textMain, true))
                details.addView(text("XRAY  /  " + if (profile.id == selectedProfileId) "SELECTED" else "SAVED", 10f, if (profile.id == selectedProfileId) green else textMuted, true))
                line.addView(details, LinearLayout.LayoutParams(0, -2, 1f))
                line.addView(actionButton(if (profile.id == selectedProfileId) "SELECTED" else "SELECT", profile.id == selectedProfileId) {
                    selectedProfileId = profile.id
                    showStatus("Selected " + profile.name)
                    render()
                })
                item.addView(line)
                item.addView(actionButton("DELETE PROFILE", false) {
                    AlertDialog.Builder(this)
                        .setTitle("Delete " + profile.name + "?")
                        .setMessage("This removes the encrypted saved profile from this device.")
                        .setNegativeButton("CANCEL", null)
                        .setPositiveButton("DELETE") { _, _ ->
                            runCatching { SecureProfileStore(this).delete(profile.id) }
                                .onSuccess {
                                    if (selectedProfileId == profile.id) selectedProfileId = null
                                    showStatus("Profile deleted.")
                                    render()
                                }
                                .onFailure { showStatus("Delete failed: " + it.message.orEmpty()) }
                        }.show()
                }, LinearLayout.LayoutParams(-1, dp(38)).apply { topMargin = dp(8) })
                pageBody.addView(item, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
            }
        }

        val editor = card().apply { setPadding(dp(14), dp(15), dp(14), dp(14)) }
        editor.addView(text("IMPORT X-RAY PROFILE", 11f, green, true))
        profileName = EditText(this).apply {
            hint = "Profile name"
            setSingleLine(true)
            setTextColor(textMain)
            setHintTextColor(textMuted)
            textSize = 14f
            setPadding(dp(12), dp(9), dp(12), dp(9))
            background = rounded(panelRaised, 12)
            val existing = profiles.firstOrNull { it.id == selectedProfileId }
            setText(existing?.name ?: "")
        }
        editor.addView(profileName, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(10) })
        profileJson = EditText(this).apply {
            hint = "Paste full Xray JSON (must include outbounds)"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setSingleLine(false)
            minLines = 7
            gravity = Gravity.TOP or Gravity.START
            setTextColor(textMain)
            setHintTextColor(textMuted)
            textSize = 12f
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = rounded(panelRaised, 12)
        }
        editor.addView(profileJson, LinearLayout.LayoutParams(-1, dp(170)).apply { topMargin = dp(8) })
        editor.addView(actionButton("VALIDATE & SAVE SECURELY", true) { saveProfile() },
            LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(10) })
        pageBody.addView(editor, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14); bottomMargin = dp(12) })
        status = text("", 11f, amber, true)
        pageBody.addView(status)
    }

    private fun renderInsight() {
        pageBody.addView(text("SYSTEM TELEMETRY", 10f, green, true))
        pageBody.addView(text("Tunnel insight", 26f, textMain, true).apply { setPadding(0, dp(4), 0, dp(4)) })
        pageBody.addView(text("Only verified measurements should appear here. Live traffic, latency history and packet health are not yet exposed by the engine integration.", 12f, textMuted))
        val warning = card().apply { setPadding(dp(15), dp(15), dp(15), dp(15)) }
        warning.addView(text("◉   TELEMETRY OFFLINE", 13f, amber, true))
        warning.addView(text("No fabricated ping, speed or uptime values are shown. Metrics will populate when the core exposes reliable measurements.", 12f, textMuted).apply { setPadding(0, dp(8), 0, 0) })
        pageBody.addView(warning, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
        listOf("LATENCY" to "No measurement", "DOWNLOAD / UPLOAD" to "Not available", "PACKET HEALTH" to "Not verified", "SESSION DURATION" to "Not tracked").forEach { (title, value) ->
            val item = card().apply { setPadding(dp(14), dp(14), dp(14), dp(14)) }
            item.addView(text(title, 10f, textMuted, true))
            item.addView(text(value, 15f, textMain, true).apply { setPadding(0, dp(5), 0, 0) })
            pageBody.addView(item, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        }
    }

    private fun renderControl() {
        pageBody.addView(text("SYSTEM CONTROL", 10f, green, true))
        pageBody.addView(text("Settings", 26f, textMain, true).apply { setPadding(0, dp(4), 0, dp(4)) })
        listOf(
            "CONNECTION" to "Connect requires a valid saved Xray profile and Android VPN permission.",
            "PRIVACY" to "Profiles are encrypted using an Android Keystore-backed AES-GCM key.",
            "VISUALS" to "PHANTOM COMMAND uses a dark instrument panel with selective holographic motion.",
            "ENGINE STATUS" to "The current integration does not prove end-to-end traffic forwarding, DNS behavior or IPv6 leak protection."
        ).forEach { (title, detail) ->
            val item = card().apply { setPadding(dp(14), dp(14), dp(14), dp(14)) }
            item.addView(text(title, 10f, green, true))
            item.addView(text(detail, 13f, textMuted).apply { setPadding(0, dp(7), 0, 0) })
            pageBody.addView(item, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(9) })
        }
        pageBody.addView(actionButton("MANAGE CONFIGURATIONS", false) { selectedPage = PAGE_NODES; render() },
            LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(14) })
    }

    private fun showNavigation() {
        val items = arrayOf("⌁  Radar dashboard", "▦  Configurations / nodes", "⌁  Tunnel insight", "⚙  Control & privacy")
        val dialog = AlertDialog.Builder(this)
            .setTitle("ARKAVA  /  NAVIGATION")
            .setItems(items) { _, which ->
                selectedPage = when (which) {
                    1 -> PAGE_NODES
                    2 -> PAGE_INSIGHT
                    3 -> PAGE_CONTROL
                    else -> PAGE_HOME
                }
                render()
            }
            .setNegativeButton("CLOSE", null)
            .create()
        dialog.setOnShowListener { dialog.window?.setBackgroundDrawable(rounded(panel, 20)) }
        dialog.show()
    }

    private fun saveProfile() {
        val name = profileName.text.toString().trim()
        val config = profileJson.text.toString().trim()
        if (name.isBlank()) {
            showStatus("Enter a name for this profile.")
            return
        }
        val validation = ProfileValidator.validateJson(config)
        if (validation.isFailure) {
            showStatus("Profile rejected: " + validation.exceptionOrNull()?.message.orEmpty())
            return
        }
        runCatching {
            val existingId = selectedProfileId?.takeIf { id ->
                runCatching { SecureProfileStore(this).loadAll().any { it.id == id } }.getOrDefault(false)
            }
            val id = existingId ?: UUID.randomUUID().toString()
            SecureProfileStore(this).save(ConnectionProfile(id, name, "xray", config))
            selectedProfileId = id
            showStatus("Profile validated and saved securely.")
            render()
        }.onFailure { showStatus("Could not save profile: " + it.message.orEmpty()) }
    }

    private fun requestVpnConsent() {
        if (currentProfile() == null) {
            showStatus("Save or select a valid Xray profile first.")
            selectedPage = PAGE_NODES
            render()
            return
        }
        val consentIntent = VpnService.prepare(this)
        if (consentIntent == null) onVpnConsentGranted()
        else {
            @Suppress("DEPRECATION")
            startActivityForResult(consentIntent, consentRequestCode)
        }
    }

    @Deprecated("Uses activity-result callback to keep the core dependency-light.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == consentRequestCode) {
            if (resultCode == RESULT_OK) onVpnConsentGranted()
            else showStatus("VPN permission was not granted.")
        }
    }

    private fun onVpnConsentGranted() {
        val id = selectedProfileId ?: currentProfile()?.id
        if (id == null) {
            showStatus("No profile selected.")
            return
        }
        startForegroundService(
            Intent(this, ArkavaVpnService::class.java)
                .setAction(ArkavaVpnService.ACTION_CONNECT)
                .putExtra(ArkavaVpnService.EXTRA_PROFILE_ID, id)
        )
        serviceState = "CONNECTING"
        showStatus("Connection requested. Core start does not yet confirm internet traffic.")
    }

    private fun currentProfile(): ConnectionProfile? {
        val profiles = runCatching { SecureProfileStore(this).loadAll() }.getOrDefault(emptyList())
        return profiles.firstOrNull { it.id == selectedProfileId }
            ?: profiles.firstOrNull()?.also { selectedProfileId = it.id }
    }

    private fun showStatus(message: String) {
        if (::status.isInitialized) status.text = message
        else AlertDialog.Builder(this).setMessage(message).setPositiveButton("OK", null).show()
    }

    private fun metric(label: String, value: String, note: String): LinearLayout = card().apply {
        setPadding(dp(9), dp(11), dp(9), dp(11))
        addView(text(label, 9f, textMuted, true))
        addView(text(value, 16f, textMain, true).apply { setPadding(0, dp(6), 0, dp(3)) })
        addView(text(note, 9f, textMuted))
    }

    private fun actionButton(label: String, primary: Boolean, action: () -> Unit): TextView = text(
        label, 11f, if (primary) Color.rgb(4, 24, 17) else green, true
    ).apply {
        gravity = Gravity.CENTER
        setPadding(dp(12), dp(8), dp(12), dp(8))
        background = rounded(if (primary) green else Color.rgb(22, 42, 42), 14, if (primary) green else Color.rgb(43, 83, 72))
        isClickable = true
        isFocusable = true
        val target = this
        setOnClickListener {
            // Small tactile press response while keeping the action immediate.
            AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(target, View.SCALE_X, 1f, 0.975f, 1f),
                    ObjectAnimator.ofFloat(target, View.SCALE_Y, 1f, 0.975f, 1f),
                    ObjectAnimator.ofFloat(target, View.ALPHA, 1f, 0.84f, 1f)
                )
                duration = 150L
                interpolator = DecelerateInterpolator()
                start()
            }
            action()
        }
    }

    private fun card(): LinearLayout = column().apply {
        background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.rgb(20, 34, 38), Color.rgb(10, 18, 22))
        ).apply {
            cornerRadius = dp(22).toFloat()
            setStroke(dp(1), Color.rgb(35, 65, 62))
        }
    }

    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun text(value: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        gravity = Gravity.CENTER_VERTICAL
        includeFontPadding = false
    }
    private fun rounded(color: Int, radiusDp: Int, strokeColor: Int? = null) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
        strokeColor?.let { setStroke(dp(1), it) }
    }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_PROFILE_ID, selectedProfileId)
        outState.putString(STATE_PAGE, selectedPage)
        super.onSaveInstanceState(outState)
    }

    private companion object {
        const val PAGE_HOME = "radar"
        const val PAGE_NODES = "nodes"
        const val PAGE_INSIGHT = "insight"
        const val PAGE_CONTROL = "control"
        const val STATE_PROFILE_ID = "selected_profile_id"
        const val STATE_PAGE = "selected_page"
    }
}
