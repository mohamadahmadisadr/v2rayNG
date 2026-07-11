package dev.sadr.atlas.handler

import android.content.Context
import dev.sadr.atlas.AppConfig
import dev.sadr.atlas.core.CoreConfigManager
import dev.sadr.atlas.core.ProxyConfigManager
import dev.sadr.atlas.core.CoreNativeManager
import dev.sadr.atlas.dto.IPAPIInfo
import dev.sadr.atlas.dto.UrlContentRequest
import dev.sadr.atlas.dto.entities.ProfileItem
import dev.sadr.atlas.dto.entities.ServerAffiliationInfo
import dev.sadr.atlas.enums.EConfigType
import dev.sadr.atlas.extension.isComplexType
import dev.sadr.atlas.extension.isNotNullEmpty
import dev.sadr.atlas.util.HttpUtil
import dev.sadr.atlas.util.JsonUtil
import dev.sadr.atlas.util.LogUtil
import dev.sadr.atlas.util.Utils
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

object SpeedtestManager {

    /**
     * Dedicated, bounded dispatcher for the blocking TCP/TLS pre-filter probes.
     *
     * These probes use the blocking [java.net.Socket] API, so each in-flight
     * connection pins one OS thread until it connects or times out. Running them
     * on the shared [Dispatchers.IO] pool would inflate that pool with dozens of
     * threads parked in connect() and starve every other IO coroutine in the app.
     * A [limitedParallelism] view gives the probes their own capped thread budget,
     * isolated from the shared IO pool.
     */
    val probeDispatcher = Dispatchers.IO.limitedParallelism(30)

    /**
     * Dedicated dispatcher for the blocking DNS resolution. Kept separate from
     * [probeDispatcher] so a slow resolver can't occupy connect-probe threads: a
     * lookup that overruns [DNS_TIMEOUT_MS] is abandoned on this pool while the
     * probe pool moves on.
     */
    private val dnsDispatcher = Dispatchers.IO.limitedParallelism(15)

    /**
     * Scope for the blocking DNS lookups. It is intentionally NOT a structured child of the
     * caller: a lookup that overruns [DNS_TIMEOUT_MS] is abandoned here (the blocking
     * `getByName` ignores cancellation and keeps running until the OS resolver returns), so it
     * must live on a scope the caller does not join — otherwise the caller would block on it.
     */
    private val dnsScope = CoroutineScope(SupervisorJob() + dnsDispatcher)

    private class DnsEntry(val address: InetAddress?, val timestamp: Long)

    private val dnsCache = ConcurrentHashMap<String, DnsEntry>()
    private const val DNS_CACHE_EXPIRY_MS = 10 * 60 * 1000L // 10 minutes
    private const val DNS_TIMEOUT_MS = 1500L

    /**
     * Resolves a host to an [InetAddress] with per-host caching and a bounded timeout.
     *
     * `InetSocketAddress(String, Int)` resolves DNS synchronously in its constructor
     * with no timeout, so a dead hostname can pin a probe thread for the OS resolver's
     * full timeout (often several seconds). Resolving here instead lets us (a) cap the
     * wait, (b) cache the result so each host is paid for at most once, and (c) reuse
     * one resolution for both the TCP and TLS probes of the same host.
     *
     * @return the resolved address, or null if the host is unresolvable / timed out.
     */
    suspend fun resolveHost(host: String): InetAddress? {
        val now = System.currentTimeMillis()
        dnsCache[host]?.let { if (now - it.timestamp < DNS_CACHE_EXPIRY_MS) return it.address }

        val address: InetAddress? = if (Utils.isPureIpAddress(host)) {
            // Literal IP: no network I/O involved.
            try {
                InetAddress.getByName(host)
            } catch (_: Exception) {
                null
            }
        } else {
            val deferred = dnsScope.async {
                try {
                    InetAddress.getByName(host)
                } catch (_: Exception) {
                    null
                }
            }
            // Bound the perceived wait; if the blocking lookup overruns we abandon it (it
            // finishes and frees its dnsDispatcher thread on its own) and treat the host as
            // unresolved for this run. `deferred` lives on dnsScope, so we don't join it.
            withTimeoutOrNull(DNS_TIMEOUT_MS) { deferred.await() }
                .also { if (it == null) deferred.cancel() }
        }

        dnsCache[host] = DnsEntry(address, now)
        return address
    }

    /** Drops DNS cache entries older than [DNS_CACHE_EXPIRY_MS]; called at the start of a scan. */
    fun pruneDnsCache() {
        val now = System.currentTimeMillis()
        dnsCache.entries.removeIf { now - it.value.timestamp > DNS_CACHE_EXPIRY_MS }
        if (dnsCache.size > 10000) dnsCache.clear()
    }

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
     * TCP connect timing against a pre-resolved [address]. Unlike the `String` overload,
     * this skips the synchronous DNS lookup that `InetSocketAddress(String, Int)` performs
     * in its constructor — the caller is expected to have resolved the host via [resolveHost].
     */
    fun socketConnectTime(address: InetAddress, port: Int, timeoutMs: Int = 3000): Long {
        var socket: Socket? = null
        val start = System.currentTimeMillis()
        try {
            socket = Socket()
            socket.connect(InetSocketAddress(address, port), timeoutMs)
            return System.currentTimeMillis() - start
        } catch (e: IOException) {
        } catch (e: Exception) {
        } finally {
            socket?.let { s ->
                try {
                    if (!s.isClosed) s.close()
                } catch (_: IOException) {
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

    suspend fun tlsHandshakeCheck(address: InetAddress, port: Int, sni: String): Boolean {
        return withContext(probeDispatcher) {
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

                    socket?.connect(InetSocketAddress(address, port), 1500)
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

        val configResult = ProxyConfigManager.getSpeedtestConfig(context, profileItem, 10900 + workerId)
        if (!configResult.status) {
            // Engine can't run this profile (e.g. an xray-only protocol under sing-box):
            // surface a distinct "N/A" rather than a misleading "timeout".
            return if (configResult.errorMessage.startsWith("sing-box:")) {
                ServerAffiliationInfo.UNSUPPORTED
            } else {
                retFailure
            }
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

        val configResult = ProxyConfigManager.getSpeedtestConfig(context, profileItem, 10900 + workerId)
        if (!configResult.status) {
            // Engine can't run this profile (e.g. an xray-only protocol under sing-box):
            // surface a distinct "N/A" rather than a misleading "timeout".
            return if (configResult.errorMessage.startsWith("sing-box:")) {
                ServerAffiliationInfo.UNSUPPORTED
            } else {
                retFailure
            }
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

        val configResult = ProxyConfigManager.getSpeedtestConfig(context, guid, 10900 + workerId)
        if (!configResult.status) {
            // Engine can't run this profile (e.g. an xray-only protocol under sing-box):
            // surface a distinct "N/A" rather than a misleading "timeout".
            return if (configResult.errorMessage.startsWith("sing-box:")) {
                ServerAffiliationInfo.UNSUPPORTED
            } else {
                retFailure
            }
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
