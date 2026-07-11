package dev.sadr.atlas.core.engine.singbox

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import dev.sadr.atlas.dto.entities.ProfileItem
import dev.sadr.atlas.util.JsonUtil

/**
 * Result of assembling a full sing-box config from a [ProfileItem].
 * Propagates [SingBoxOutboundResult.Unsupported] reasons so callers can surface a clear
 * message instead of handing sing-box a config it will reject.
 */
sealed interface SingBoxConfigResult {
    data class Success(val json: String) : SingBoxConfigResult
    data class Unsupported(val reason: String) : SingBoxConfigResult
}

/**
 * Assembles complete sing-box configurations from a [ProfileItem].
 *
 * PHASE 1 (current): sing-box runs behind the existing hev-socks5-tunnel, exactly like xray —
 * hev owns the TUN and forwards to a local SOCKS inbound, sing-box proxies out. The config is
 * kept intentionally minimal (log + one mixed inbound + the proxy outbound + `route.final`) so
 * it only relies on schema fields that are stable across sing-box versions; DNS, routing rules,
 * per-app proxy and sniffing parity come in a later phase once the engine is proven to connect.
 *
 * NOTE: outbound-socket protection (so the proxy connection isn't routed back into the VPN TUN)
 * is wired in the Go wrapper via sing-box's platform interface, not here in the config.
 */
object SingBoxConfigBuilder {

    const val PROXY_TAG = "proxy"

    /**
     * Full run config for the phase-1 hev bridge: a local mixed (SOCKS+HTTP) inbound that hev
     * feeds, forwarding everything to the translated proxy outbound.
     *
     * @param socksPort the local port hev connects to (SettingsManager.getSocksPort()).
     */
    fun build(profile: ProfileItem, socksPort: Int, logLevel: String = "warn"): SingBoxConfigResult {
        val outbound = when (val r = SingBoxOutboundBuilder.build(profile, PROXY_TAG)) {
            is SingBoxOutboundResult.Success -> r.outbound
            is SingBoxOutboundResult.Unsupported -> return SingBoxConfigResult.Unsupported(r.reason)
        }

        val root = JsonObject()
        root.add("log", logBlock(logLevel))

        root.add("inbounds", JsonArray().apply {
            add(JsonObject().apply {
                addProperty("type", "mixed")
                addProperty("tag", "socks-in")
                addProperty("listen", "127.0.0.1")
                addProperty("listen_port", socksPort)
            })
        })

        root.add("outbounds", JsonArray().apply { add(outbound) })
        root.add("route", JsonObject().apply { addProperty("final", PROXY_TAG) })

        // clash_api on a per-instance port so SingBoxCore.measureDelay can use sing-box's
        // native in-core URLTest (/proxies/proxy/delay) — an apples-to-apples equivalent of
        // xray's measureDelay, instead of a costly external SOCKS request.
        root.add("experimental", JsonObject().apply {
            add("clash_api", JsonObject().apply {
                addProperty("external_controller", "127.0.0.1:${clashPortFor(socksPort)}")
            })
        })

        return SingBoxConfigResult.Success(JsonUtil.toJson(root))
    }

    /** clash_api control port derived from the SOCKS port, in a separate range to avoid collisions. */
    fun clashPortFor(socksPort: Int): Int = socksPort + 5000

    private fun logBlock(level: String): JsonObject = JsonObject().apply {
        addProperty("level", level)
        addProperty("timestamp", false)
    }
}
