package dev.sadr.atlas.core.engine.singbox

import com.google.gson.JsonParser
import dev.sadr.atlas.core.engine.ProxyCore
import dev.sadr.atlas.core.engine.ProxyCoreCallback
import dev.sadr.atlas.core.engine.ProxyProcessFinder
import libsingbox.CoreCallbackHandler
import libsingbox.CoreController
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * [ProxyCore] adapter over the sing-box gomobile `CoreController` (package `libsingbox`).
 *
 * Delay measurement uses sing-box's own clash_api URLTest endpoint (measured in-core, like
 * xray's measureDelay), reached via the local clash controller port parsed from the config at
 * [startLoop] time. This avoids the overhead of proxying a full external request.
 */
internal class SingBoxCore(private val controller: CoreController) : ProxyCore {

    @Volatile
    private var clashPort: Int = 0

    override val isRunning: Boolean
        get() = controller.isRunning

    override fun startLoop(config: String, tunFd: Int) {
        clashPort = parseClashPort(config)
        controller.startLoop(config, tunFd)
    }

    override fun stopLoop() {
        controller.stopLoop()
    }

    override fun measureDelay(url: String): Long {
        val port = clashPort
        if (port <= 0) return -1L
        var conn: HttpURLConnection? = null
        return try {
            val encoded = URLEncoder.encode(url, "UTF-8")
            val api = URL("http://127.0.0.1:$port/proxies/${SingBoxConfigBuilder.PROXY_TAG}/delay?timeout=5000&url=$encoded")
            conn = (api.openConnection() as HttpURLConnection).apply {
                connectTimeout = 2000
                readTimeout = 8000
                requestMethod = "GET"
            }
            if (conn.responseCode != 200) return -1L
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            JsonParser.parseString(body).asJsonObject.get("delay").asLong
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

    /** Extracts the clash_api controller port from `experimental.clash_api.external_controller`. */
    private fun parseClashPort(config: String): Int {
        return try {
            val controllerAddr = JsonParser.parseString(config).asJsonObject
                .getAsJsonObject("experimental")
                ?.getAsJsonObject("clash_api")
                ?.get("external_controller")?.asString ?: return 0
            controllerAddr.substringAfterLast(':').toIntOrNull() ?: 0
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
