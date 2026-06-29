package com.v2ray.ang.handler

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.core.CoreServiceManager
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.extension.toSpeedString
import com.v2ray.ang.ui.MainActivity
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.min

object VpnNotificationManager {
    private const val NOTIFICATION_ID = 1
    private const val NOTIFICATION_PENDING_INTENT_CONTENT = 0
    private const val NOTIFICATION_PENDING_INTENT_STOP_V2RAY = 1
    private const val NOTIFICATION_PENDING_INTENT_RESTART_V2RAY = 2
    private const val NOTIFICATION_ICON_THRESHOLD = 3000
    private const val QUERY_INTERVAL_MS = 3000L

    private var lastQueryTime = 0L
    private var mBuilder: NotificationCompat.Builder? = null
    private var speedNotificationJob: Job? = null
    private var mNotificationManager: android.app.NotificationManager? = null

    /**
     * Starts the speed notification.
     */
    fun startSpeedNotification() {
        if (MmkvManager.decodeSettingsBool(AppConfig.PREF_SPEED_ENABLED) != true) return
        if (speedNotificationJob != null || CoreServiceManager.isRunning() == false) return

        var lastZeroSpeed = false

        speedNotificationJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                lastZeroSpeed = updateSpeedNotificationOnce(lastZeroSpeed)
                delay(QUERY_INTERVAL_MS)
            }
        }
    }

    /**
     * Shows the notification.
     * @param currentConfig The current profile configuration.
     */
    fun showNotification(currentConfig: ProfileItem?) {
        val service = getService() ?: return

        // Reset last query time to avoid querying stats too soon after showing the notification
        lastQueryTime = System.currentTimeMillis()

        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

        val startMainIntent = Intent(service, MainActivity::class.java)
        val contentPendingIntent = PendingIntent.getActivity(service, NOTIFICATION_PENDING_INTENT_CONTENT, startMainIntent, flags)

        val stopV2RayIntent = Intent(AppConfig.BROADCAST_ACTION_SERVICE)
        stopV2RayIntent.`package` = AppConfig.ANG_PACKAGE
        stopV2RayIntent.putExtra("key", AppConfig.MSG_STATE_STOP)
        val stopV2RayPendingIntent = PendingIntent.getBroadcast(service, NOTIFICATION_PENDING_INTENT_STOP_V2RAY, stopV2RayIntent, flags)

        val restartV2RayIntent = Intent(AppConfig.BROADCAST_ACTION_SERVICE)
        restartV2RayIntent.`package` = AppConfig.ANG_PACKAGE
        restartV2RayIntent.putExtra("key", AppConfig.MSG_STATE_RESTART)
        val restartV2RayPendingIntent = PendingIntent.getBroadcast(service, NOTIFICATION_PENDING_INTENT_RESTART_V2RAY, restartV2RayIntent, flags)

        val channelId =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                createNotificationChannel()
            } else {
                ""
            }

        mBuilder = NotificationCompat.Builder(service, channelId)
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentTitle(currentConfig?.remarks ?: "v2rayNG")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentPendingIntent)
            .addAction(
                R.drawable.ic_delete_24dp,
                service.getString(R.string.notification_action_stop_v2ray),
                stopV2RayPendingIntent
            )
            .addAction(
                R.drawable.ic_restore_24dp,
                service.getString(R.string.title_service_restart),
                restartV2RayPendingIntent
            )

        service.startForeground(NOTIFICATION_ID, mBuilder?.build())
    }

    /**
     * Cancels the notification.
     */
    fun cancelNotification() {
        val service = getService() ?: return
        service.stopForeground(Service.STOP_FOREGROUND_REMOVE)

        mBuilder = null
        speedNotificationJob?.cancel()
        speedNotificationJob = null
        mNotificationManager = null
    }

    /**
     * Stops the speed notification.
     */
    fun stopSpeedNotification() {
        speedNotificationJob?.let {
            it.cancel()
            speedNotificationJob = null
            updateNotification("", 0, 0)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel(): String {
        val channelId = "v2rayng_channel_id"
        val channelName = "v2rayng_channel_name"
        val chan = NotificationChannel(
            channelId,
            channelName, android.app.NotificationManager.IMPORTANCE_LOW
        )
        chan.lightColor = Color.DKGRAY
        chan.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        getNotificationManager()?.createNotificationChannel(chan)
        return channelId
    }

    private fun updateNotification(contentText: String?, proxyTotal: Long, directTotal: Long) {
        val service = getService() ?: return
        if (mBuilder == null) {
            showNotification(CoreServiceManager.currentConfig)
        }

        mBuilder?.let { builder ->
            if (contentText != null) {
                builder.setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
                builder.setContentText(contentText)
            }
            getNotificationManager()?.notify(NOTIFICATION_ID, builder.build())
        }
    }

    private fun getNotificationManager(): android.app.NotificationManager? {
        if (mNotificationManager == null) {
            val service = getService() ?: return null
            mNotificationManager = service.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        }
        return mNotificationManager
    }

    private fun updateSpeedNotificationOnce(lastZeroSpeed: Boolean): Boolean {
        var isZeroSpeed = lastZeroSpeed
        try {
            val stats = CoreServiceManager.queryAllOutboundTrafficStats()
            var proxyUplink = 0L
            var proxyDownlink = 0L
            var directUplink = 0L
            var directDownlink = 0L
            
            stats.forEach {
                if (it.tag == AppConfig.TAG_PROXY) {
                    if (it.direction == AppConfig.UPLINK) proxyUplink += it.value
                    if (it.direction == AppConfig.DOWNLINK) proxyDownlink += it.value
                } else if (it.tag == AppConfig.TAG_DIRECT) {
                    if (it.direction == AppConfig.UPLINK) directUplink += it.value
                    if (it.direction == AppConfig.DOWNLINK) directDownlink += it.value
                }
            }

            val queryTime = System.currentTimeMillis()
            val sinceLastQueryInSeconds = (queryTime - lastQueryTime) / 1000.0
            if (sinceLastQueryInSeconds <= 0) return lastZeroSpeed

            val proxyTotal = proxyUplink + proxyDownlink
            val directTotal = directUplink + directDownlink
            val zeroSpeed = proxyTotal + directTotal == 0L
            
            if (!zeroSpeed || !lastZeroSpeed) {
                val text = StringBuilder()
                appendSpeedString(text, AppConfig.TAG_PROXY, proxyUplink / sinceLastQueryInSeconds, proxyDownlink / sinceLastQueryInSeconds)
                appendSpeedString(text, AppConfig.TAG_DIRECT, directUplink / sinceLastQueryInSeconds, directDownlink / sinceLastQueryInSeconds)
                updateNotification(text.toString(), proxyTotal, directTotal)
            }
            lastQueryTime = queryTime
            isZeroSpeed = zeroSpeed
        } catch (_: Exception) {
        }
        return isZeroSpeed
    }

    private fun appendSpeedString(sb: StringBuilder, tag: String, up: Double, down: Double) {
        if (sb.isNotEmpty()) sb.append("\n")
        sb.append("$tag: ↑${up.toLong().toSpeedString()} ↓${down.toLong().toSpeedString()}")
    }

    private fun getService(): Service? {
        return CoreServiceManager.serviceControl?.get()?.getService()
    }
}
