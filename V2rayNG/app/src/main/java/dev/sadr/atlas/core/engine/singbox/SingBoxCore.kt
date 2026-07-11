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
 * Delay measurement is done here in Kotlin rather than in the Go wrapper: the running instance
 * has a local SOCKS inbound (see [SingBoxConfigBuilder]), so [measureDelay] issues an HTTP
 * request through it. The inbound port is parsed from the config at [startLoop] time.
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
        var conn: HttpURLConnection? = null
        return try {
            val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", port))
            conn = (URL(url).openConnection(proxy) as HttpURLConnection).apply {
                connectTimeout = 3000
                readTimeout = 5000
                instanceFollowRedirects = false
                requestMethod = "GET"
            }
            val start = System.currentTimeMillis()
            val code = conn.responseCode // performs the request through the proxy
            val elapsed = System.currentTimeMillis() - start
            if (code in 200..399) elapsed else -1L
        } catch (_: Exception) {
            -1L
        } finally {
            conn?.disconnect()
        }
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
