/*
 * Cobalt - A universal capture and personal knowledge archive
 * Copyright (C) 2026 Hanan Bhatti
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.example.web

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.text.format.Formatter
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.local.AppDatabase
import com.example.data.repository.CobaltRepository
import com.example.web.auth.WebCompanionSecurityManager
import com.example.web.server.CobaltEmbeddedServer
import com.example.web.server.WebCompanionRouter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Lifecycle coordinator and controller for the Cobalt Local Web Companion.
 */
object WebCompanionManager {

    private const val NOTIFICATION_CHANNEL_ID = "cobalt_web_companion"
    private const val NOTIFICATION_ID = 2030

    val securityManager = WebCompanionSecurityManager()

    private var server: CobaltEmbeddedServer? = null
    private var router: WebCompanionRouter? = null

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _serverUrl = MutableStateFlow<String?>(null)
    val serverUrl: StateFlow<String?> = _serverUrl.asStateFlow()

    private val _activePin = MutableStateFlow("")
    val activePin: StateFlow<String> = _activePin.asStateFlow()

    private val _activePort = MutableStateFlow(8080)
    val activePort: StateFlow<Int> = _activePort.asStateFlow()

    /**
     * Starts the embedded web companion server on the local network.
     */
    @Synchronized
    fun start(
        context: Context,
        scope: CoroutineScope,
        db: AppDatabase,
        repo: CobaltRepository,
        port: Int = 8080
    ): Boolean {
        if (_isRunning.value) return true

        val localIp = getLocalIpAddress(context)
        val freshPin = securityManager.rotatePin()

        router = WebCompanionRouter(
            securityManager = securityManager,
            repositoryProvider = { repo },
            databaseProvider = { db },
            contextProvider = { context.applicationContext }
        )

        val embeddedServer = CobaltEmbeddedServer(
            requestedPort = port,
            securityManager = securityManager,
            allowedAddressesProvider = {
                val ipList = mutableListOf("localhost", "127.0.0.1")
                if (localIp != "127.0.0.1") ipList.add(localIp)
                ipList
            },
            handler = { req -> router!!.handleRequest(req) }
        )

        val actualPort = embeddedServer.start(scope)
        if (actualPort <= 0) return false

        server = embeddedServer
        _activePort.value = actualPort
        _activePin.value = freshPin
        val url = "http://$localIp:$actualPort"
        _serverUrl.value = url
        _isRunning.value = true

        showForegroundNotification(context, url, freshPin)
        return true
    }

    /**
     * Stops the embedded server and invalidates all session tokens.
     */
    @Synchronized
    fun stop(context: Context) {
        if (!_isRunning.value && server == null) return

        server?.stop()
        server = null
        router = null
        securityManager.reset()

        _isRunning.value = false
        _serverUrl.value = null
        _activePin.value = ""

        cancelNotification(context)
    }

    /**
     * Rotates the 4-digit PIN on demand and invalidates active sessions.
     */
    @Synchronized
    fun rotatePin(context: Context): String {
        val newPin = securityManager.rotatePin()
        _activePin.value = newPin
        val currentUrl = _serverUrl.value
        if (currentUrl != null && _isRunning.value) {
            showForegroundNotification(context, currentUrl, newPin)
        }
        return newPin
    }

    /**
     * Resolves the device's local IPv4 Wi-Fi address.
     */
    fun getLocalIpAddress(context: Context): String {
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val ipInt = wifiManager?.connectionInfo?.ipAddress ?: 0
            if (ipInt != 0) {
                @Suppress("DEPRECATION")
                val formatted = Formatter.formatIpAddress(ipInt)
                if (!formatted.isNullOrBlank() && formatted != "0.0.0.0") {
                    return formatted
                }
            }
        } catch (_: Exception) {}

        // Fallback: enumerate network interfaces
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return "127.0.0.1"
            for (intf in interfaces) {
                if (intf.isLoopback || !intf.isUp) continue
                for (addr in intf.inetAddresses) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val host = addr.hostAddress
                        if (!host.isNullOrBlank()) return host
                    }
                }
            }
        } catch (_: Exception) {}

        return "127.0.0.1"
    }

    private fun showForegroundNotification(context: Context, url: String, pin: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Web Companion",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Status of local desktop web companion server"
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
            manager.createNotificationChannel(channel)
        }

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_custom_globe)
            .setColor(android.graphics.Color.parseColor("#38BDF8"))
            .setContentTitle("Web Companion Active")
            .setContentText("$url • PIN: $pin")
            .setOngoing(true)
            .setAutoCancel(false)
            .setSilent(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        try {
            manager.notify(NOTIFICATION_ID, notification)
        } catch (_: Exception) {}
    }

    private fun cancelNotification(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        try {
            manager.cancel(NOTIFICATION_ID)
        } catch (_: Exception) {}
    }
}
