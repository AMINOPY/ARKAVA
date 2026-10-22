package dev.arkava.vpn.platform

import android.app.Notification
import android.app.NotificationChannel
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.IBinder

/** Lifecycle shell only. It does not claim a tunnel is connected before a real engine is integrated. */
class ArkavaVpnService : VpnService() {
    override fun onCreate() { super.onCreate(); createNotificationChannel() }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PREPARE -> startForeground(NOTIFICATION_ID, notification("Engine integration pending"))
            ACTION_STOP -> { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
            else -> stopSelf(startId)
        }
        return START_NOT_STICKY
    }
    override fun onBind(intent: Intent?): IBinder? = super.onBind(intent)
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(android.app.NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "ARKAVA VPN", android.app.NotificationManager.IMPORTANCE_LOW))
        }
    }
    private fun notification(message: String): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(this, CHANNEL_ID)
            else @Suppress("DEPRECATION") Notification.Builder(this)
        return builder.setContentTitle("ARKAVA VPN").setContentText(message)
            .setSmallIcon(android.R.drawable.stat_sys_warning).setOngoing(true).build()
    }
    companion object {
        const val ACTION_PREPARE = "dev.arkava.vpn.action.PREPARE"
        const val ACTION_STOP = "dev.arkava.vpn.action.STOP"
        private const val CHANNEL_ID = "arkava_vpn"
        private const val NOTIFICATION_ID = 7402
    }
}
