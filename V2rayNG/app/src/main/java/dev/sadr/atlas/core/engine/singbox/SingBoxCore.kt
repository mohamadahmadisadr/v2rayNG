package dev.sadr.atlas.core.engine.singbox

import com.google.gson.JsonParser
import dev.sadr.atlas.core.engine.ProxyCore
import dev.sadr.atlas.core.engine.ProxyCoreCallback
import dev.sadr.atlas.core.engine.ProxyProcessFinder
import libsingbox.CoreCallbackHandler
import libsingbox.CoreController
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL

/**
 * [ProxyCore] adapter over the sing-box gomobile `CoreController` (package `libsingbox`).
 *
 * Delay measurement proxies a request through the local SOCKS inbound (port parsed from the
 * config at [startLoop] time). It does two attempts over a kept-alive connection and returns
 * the faster one — the first warms the proxied connection, the second reflects steady-state
 * latency — matching xray's measureDelay (min of attempts) so the numbers are comparable.
 */
internal class SingBoxCore(private val controller: CoreController) : ProxyCore {

    @Volatile
    private var socksPort: Int = 0

    override val isRunning: Boolean
        get() = controller.isRunning

    override fun startLoop(config: String, tunFd: Int) {
        socksPort = parseSocksPort(config)
        controller.startLoop(config, tunFd)
    }

    override fun stopLoop() {
        controller.stopLoop()
    }

    override fun measureDelay(url: String): Long {
        val port = socksPort
        if (port <= 0) return -1L
        val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", port))
        var best = -1L
        repeat(2) {
            try {
                val conn = (URL(url).openConnection(proxy) as HttpURLConnection).apply {
                    connectTimeout = 3000
                    readTimeout = 5000
                    instanceFollowRedirects = false
                    setRequestProperty("Connection", "keep-alive")
                }
                val start = System.currentTimeMillis()
                val code = conn.responseCode
                // Drain (don't disconnect) so the socket returns to the keep-alive pool and the
                // second attempt reuses the already-established proxied connection.
                conn.inputStream.use { it.readBytes() }
                val elapsed = System.currentTimeMillis() - start
                if (code in 200..399 && (best < 0 || elapsed < best)) best = elapsed
            } catch (_: Exception) {
            }
        }
        return best
    }

    override fun queryAllOutboundTrafficStats(): String =
        controller.queryAllOutboundTrafficStats()

    override fun registerProcessFinder(finder: ProxyProcessFinder?) {
        // Process-based routing isn't wired in the sing-box wrapper for phase 1; no-op.
    }

    /** Extracts the first inbound `listen_port` from the sing-box config, or 0 if absent. */
    private fun parseSocksPort(config: String): Int {
        return try {
            val inbounds = JsonParser.parseString(config).asJsonObject
                .getAsJsonArray("inbounds") ?: return 0
            for (element in inbounds) {
                val obj = element.asJsonObject
                if (obj.has("listen_port")) return obj.get("listen_port").asInt
            }
            0
        } catch (_: Exception) {
            0
        }
    }
}

/** Adapts a [ProxyCoreCallback] to sing-box's `CoreCallbackHandler`. */
internal class SingBoxCoreCallbackAdapter(
    private val callback: ProxyCoreCallback
) : CoreCallbackHandler {
    override fun startup(): Long = callback.onStartup()
    override fun shutdown(): Long = callback.onShutdown()
    override fun onEmitStatus(status: Long, message: String?): Long =
        callback.onEmitStatus(status, message)
}
