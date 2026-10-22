package dev.arkava.vpn.platform

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import dev.arkava.vpn.core.LibXrayClient
import dev.arkava.vpn.core.ProfileValidator
import dev.arkava.vpn.core.TunConfigBuilder
import dev.arkava.vpn.storage.SecureProfileStore
import org.json.JSONObject

/** Owns the Android TUN and the libXray lifecycle. Connectivity is never inferred from start alone. */
class ArkavaVpnService : VpnService() {
    private var tun: ParcelFileDescriptor? = null
    private var engine: LibXrayClient? = null
    private var dnsConfigured = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PREPARE -> {
                enterForeground("Ready; no profile has been started")
            }
            ACTION_CONNECT -> {
                enterForeground("Preparing VPN tunnel")
                val profileId = intent.getStringExtra(EXTRA_PROFILE_ID)
                val profile = runCatching {
                    require(!profileId.isNullOrBlank()) { "Missing profile ID" }
                    SecureProfileStore(this).loadAll().firstOrNull { it.id == profileId }
                        ?: error("Saved profile was not found")
                }
                if (profile.isFailure) {
                    enterForeground("Connection refused: ${profile.exceptionOrNull()?.message.orEmpty().take(100)}")
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf(startId)
                } else {
                    val selected = profile.getOrThrow()
                    runCatching {
                        require(selected.engineId.equals("xray", ignoreCase = true)) { "Profile engine is not Xray" }
                        val validated = ProfileValidator.validateJson(selected.configJson)
                        require(validated.isSuccess) { validated.exceptionOrNull()?.message ?: "Invalid profile" }
                        startTunnel(selected.configJson)
                    }.onFailure { error ->
                        cleanupEngine()
                        enterForeground("Connection failed: ${error.message.orEmpty().take(120)}")
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf(startId)
                    }
                }
            }
            ACTION_STOP -> {
                cleanupEngine()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
            }
            else -> stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    private fun startTunnel(configJson: String) {
        check(tun == null && engine == null) { "A VPN session is already active" }
        val config = JSONObject(configJson)
        val client = LibXrayClient()
        val descriptor = Builder()
            .setSession("ARKAVA VPN")
            .setMtu(TUN_MTU)
            .addAddress(TUN_IPV4_ADDRESS, 32)
            .addRoute("0.0.0.0", 0)
            .addDnsServer(DNS_SERVER)
            .addAddress(TUN_IPV6_ADDRESS, 128)
            .addRoute("::", 0)
            .establish() ?: error("Android refused to establish the VPN TUN")
        tun = descriptor
        engine = client
        try {
            // Mark first so cleanup attempts ResetDNS even if setup partially fails.
            dnsConfigured = true
            client.configureAndroidController(this, "$DNS_SERVER:53")
            val runtimeConfig = TunConfigBuilder.prepare(config.toString(), descriptor.fd, TUN_MTU)
            client.start(runtimeConfig)
            check(client.isRunning()) { "Xray core did not report a running state" }
            // Core-running is not equivalent to verified end-to-end connectivity.
            enterForeground("Xray core started; tunnel connectivity not yet verified")
        } catch (error: Throwable) {
            cleanupEngine()
            throw error
        }
    }

    private fun cleanupEngine() {
        val client = engine
        engine = null
        if (client != null) runCatching { client.stop() }
        if (dnsConfigured && client != null) runCatching { client.resetAndroidDns() }
        dnsConfigured = false
        runCatching { tun?.close() }
        tun = null
    }

    override fun onDestroy() {
        cleanupEngine()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = super.onBind(intent)

    private fun enterForeground(message: String) {
        val statusNotification = notification(message)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, statusNotification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, statusNotification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "ARKAVA VPN", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun notification(message: String): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder.setContentTitle("ARKAVA VPN")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val ACTION_PREPARE = "dev.arkava.vpn.action.PREPARE"
        const val ACTION_CONNECT = "dev.arkava.vpn.action.CONNECT"
        const val ACTION_STOP = "dev.arkava.vpn.action.STOP"
        const val EXTRA_PROFILE_ID = "dev.arkava.vpn.extra.PROFILE_ID"
        private const val CHANNEL_ID = "arkava_vpn"
        private const val NOTIFICATION_ID = 7402
        private const val TUN_MTU = 1500
        private const val TUN_IPV4_ADDRESS = "10.111.0.1"
        private const val TUN_IPV6_ADDRESS = "fd00:1:fd00::1"
        private const val DNS_SERVER = "1.1.1.1"
    }
}