package com.v2ray.ang.handler

import android.content.Context
import com.v2ray.ang.AppConfig
import com.v2ray.ang.core.CoreConfigManager
import com.v2ray.ang.core.CoreNativeManager
import com.v2ray.ang.dto.IPAPIInfo
import com.v2ray.ang.dto.UrlContentRequest
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.extension.isComplexType
import com.v2ray.ang.extension.isNotNullEmpty
import com.v2ray.ang.util.HttpUtil
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.LogUtil
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.UnknownHostException
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

object SpeedtestManager {

    /**
     * Measures the time taken to establish a TCP connection to a given URL and port.
     *
     * @param url The URL to connect to.
     * @param port The port to connect to.
     * @return The connection time in milliseconds, or -1 if the connection failed.
     */
    fun socketConnectTime(url: String, port: Int, timeoutMs: Int = 3000): Long {
        var socket: Socket? = null
        val start = System.currentTimeMillis()

        try {
            socket = Socket()
            socket.connect(InetSocketAddress(url, port), timeoutMs)

            return System.currentTimeMillis() - start
        } catch (e: UnknownHostException) {
            // LogUtil.e(AppConfig.TAG, "Unknown host: $url", e)
        } catch (e: IOException) {
            // LogUtil.e(AppConfig.TAG, "socketConnectTime IOException: ${e.message}")
        } catch (e: Exception) {
            // LogUtil.e(AppConfig.TAG, "Failed to establish socket connection to $url:$port", e)
        } finally {
            socket?.let { s ->
                try {
                    if (!s.isClosed) {
                        s.close()
                    }
                } catch (closeEx: IOException) {
                }
            }
        }
        return -1
    }

    /**
     * Attempts a TLS handshake to a given host and port.
     */
    fun tlsHandshake(host: String, port: Int, timeoutMs: Int = 2000): Int {
        var socket: Socket? = null
        try {
            val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
            socket = factory.createSocket()
            socket.connect(InetSocketAddress(host, port), timeoutMs)
            socket.soTimeout = timeoutMs
            val sslSocket = socket as SSLSocket
            sslSocket.enabledProtocols = arrayOf("TLSv1.2", "TLSv1.3")
            sslSocket.startHandshake()
            return 0 // OK
        } catch (e: Exception) {
            val msg = e.message ?: ""
            if (msg.contains("connection reset") || msg.contains("handshake failure")) {
                return 1 // Rejected
            }
            // LogUtil.e(AppConfig.TAG, "TLS handshake failed for $host:$port", e)
        } finally {
            try {
                socket?.close()
            } catch (_: Exception) {
            }
        }
        return 2 // Unknown error, let ping decide
    }

    suspend fun tlsHandshakeCheck(host: String, port: Int, sni: String): Boolean {
        return withContext(Dispatchers.IO) {
            var socket: SSLSocket? = null
            try {
                withTimeout(2000) {
                    val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
                    socket = factory.createSocket() as SSLSocket
                    
                    // Set SNI
                    if (sni.isNotBlank()) {
                        val params = socket?.sslParameters
                        params?.serverNames = listOf(SNIHostName(sni))
                        socket?.sslParameters = params
                    }

                    socket?.connect(InetSocketAddress(host, port), 1500)
                    socket?.soTimeout = 1500
                    socket?.startHandshake()
                    true
                }
            } catch (_: Exception) {
                false
            } finally {
                try {
                    socket?.close()
                } catch (_: Exception) {
                }
            }
        }
    }

    suspend fun startRealPing(context: Context, profileItem: ProfileItem, workerId: Int = 0): Long {
        val retFailure = -1L

        if (!profileItem.configType.isComplexType()
            && profileItem.configType != EConfigType.HYSTERIA2
            && profileItem.server.isNotNullEmpty()
            && profileItem.serverPort?.toIntOrNull() != null
        ) {
            val url = profileItem.server.orEmpty()
            val port = profileItem.serverPort.orEmpty().toInt()
            val tcpTime = socketConnectTime(url, port, 2000)
            if (tcpTime <= -1L) {
                return retFailure
            }
        }

        val configResult = CoreConfigManager.getV2rayConfig4Speedtest(context, profileItem, 10900 + workerId)
        if (!configResult.status) {
            return retFailure
        }
        
        val url1 = SettingsManager.getDelayTestUrl()
        var delay = CoreNativeManager.measureOutboundDelay(configResult.content, url1, workerId)
        if (delay <= 0) {
            val url2 = if (url1 == AppConfig.DELAY_TEST_URL) AppConfig.DELAY_TEST_URL2 else AppConfig.DELAY_TEST_URL
            delay = CoreNativeManager.measureOutboundDelay(configResult.content, url2, workerId)
        }
        if (delay <= 0) {
            delay = CoreNativeManager.measureOutboundDelay(configResult.content, AppConfig.DELAY_TEST_URL3, workerId)
        }
        return delay
    }

    suspend fun startRealPingWithUrl(context: Context, profileItem: ProfileItem, testUrl: String, timeoutMs: Int, workerId: Int = 0): Long {
        val retFailure = -1L

        val configResult = CoreConfigManager.getV2rayConfig4Speedtest(context, profileItem, 10900 + workerId)
        if (!configResult.status) {
            return retFailure
        }
        
        return CoreNativeManager.measureOutboundDelay(configResult.content, testUrl, workerId)
    }

    suspend fun startRealPing(context: Context, guid: String, workerId: Int = 0): Long {
        val retFailure = -1L

        val config = MmkvManager.decodeServerConfig(guid) ?: return retFailure
        if (!config.configType.isComplexType()
            && config.configType != EConfigType.HYSTERIA2
            && config.server.isNotNullEmpty()
            && config.serverPort?.toIntOrNull() != null
        ) {
            val url = config.server.orEmpty()
            val port = config.serverPort.orEmpty().toInt()
            val tcpTime = socketConnectTime(url, port, 2000)
            if (tcpTime <= -1L) {
                return retFailure
            }
        }

        val configResult = CoreConfigManager.getV2rayConfig4Speedtest(context, guid, 10900 + workerId)
        if (!configResult.status) {
            return retFailure
        }
        val url1 = SettingsManager.getDelayTestUrl()
        var delay = CoreNativeManager.measureOutboundDelay(configResult.content, url1, workerId)
        if (delay <= 0) {
            val url2 = if (url1 == AppConfig.DELAY_TEST_URL) AppConfig.DELAY_TEST_URL2 else AppConfig.DELAY_TEST_URL
            delay = CoreNativeManager.measureOutboundDelay(configResult.content, url2, workerId)
        }
        if (delay <= 0) {
            delay = CoreNativeManager.measureOutboundDelay(configResult.content, AppConfig.DELAY_TEST_URL3, workerId)
        }
        return delay
    }

    fun getRemoteIPInfo(): String? {
        val url = MmkvManager.decodeSettingsString(AppConfig.PREF_IP_API_URL)
            .takeIf { !it.isNullOrBlank() } ?: AppConfig.IP_API_URL

        val proxyUsername = SettingsManager.getSocksUsername()
        val proxyPassword = SettingsManager.getSocksPassword()
        val httpPort = SettingsManager.getHttpPort()
        if (httpPort == 0) return null
        val content = HttpUtil.getUrlContent(
            UrlContentRequest(
                url = url,
                timeout = 5000,
                httpPort = httpPort,
                proxyUsername = proxyUsername,
                proxyPassword = proxyPassword
            )
        ) ?: return null
        val ipInfo = JsonUtil.fromJsonSafe(content, IPAPIInfo::class.java) ?: return null

        val ip = listOf(
            ipInfo.ip,
            ipInfo.clientIp,
            ipInfo.ip_addr,
            ipInfo.query
        ).firstOrNull { !it.isNullOrBlank() }

        val country = listOf(
            ipInfo.country_code,
            ipInfo.country,
            ipInfo.countryCode,
            ipInfo.location?.country_code
        ).firstOrNull { !it.isNullOrBlank() }

        return "(${country ?: "unknown"}) ${ip ?: "unknown"}"
    }
}
