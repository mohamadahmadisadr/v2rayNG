package dev.sadr.atlas.core.engine.singbox

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import dev.sadr.atlas.AppConfig
import dev.sadr.atlas.dto.entities.ProfileItem
import dev.sadr.atlas.enums.EConfigType
import dev.sadr.atlas.enums.NetworkType

/**
 * Result of translating one [ProfileItem] to a sing-box outbound.
 *
 * sing-box does not implement every xray feature (mkcp, xhttp, and the legacy tcp/http
 * header obfuscation are xray-only), so translation can legitimately fail. Callers should
 * surface [Unsupported.reason] rather than emitting a config sing-box will reject.
 */
sealed interface SingBoxOutboundResult {
    data class Success(val outbound: JsonObject) : SingBoxOutboundResult
    data class Unsupported(val reason: String) : SingBoxOutboundResult
}

/**
 * Translates a [ProfileItem] into a sing-box `outbound` JSON object.
 *
 * First slice: covers VMess, VLESS, Trojan, Shadowsocks, SOCKS and HTTP with TLS/REALITY/uTLS
 * and the ws/grpc/http/httpupgrade transports — the mainstream of real-world configs. WireGuard,
 * Hysteria and Hysteria2 are recognised but not yet translated; mkcp/xhttp/tcp-http-obfs are
 * reported as unsupported because sing-box has no equivalent.
 *
 * Field mapping mirrors the xray builder: VMess/VLESS uuid = [ProfileItem.password],
 * cipher/encryption = [ProfileItem.method]; Trojan/Shadowsocks use [ProfileItem.password]
 * (+ method for SS); SOCKS/HTTP use [ProfileItem.username]/[ProfileItem.password].
 */
object SingBoxOutboundBuilder {

    fun build(profile: ProfileItem, tag: String = "proxy"): SingBoxOutboundResult {
        val server = profile.server
        val port = profile.serverPort?.toIntOrNull()
        if (server.isNullOrBlank() || port == null) {
            return SingBoxOutboundResult.Unsupported("missing server/port")
        }

        val out = JsonObject()
        out.addProperty("tag", tag)
        out.addProperty("server", server)
        out.addProperty("server_port", port)

        when (profile.configType) {
            EConfigType.VMESS -> {
                out.addProperty("type", "vmess")
                out.addProperty("uuid", profile.password.orEmpty())
                out.addProperty("security", profile.method?.takeIf { it.isNotBlank() } ?: AppConfig.DEFAULT_SECURITY)
                out.addProperty("alter_id", 0)
                applyStream(profile, out)?.let { return it }
            }

            EConfigType.VLESS -> {
                out.addProperty("type", "vless")
                out.addProperty("uuid", profile.password.orEmpty())
                profile.flow?.takeIf { it.isNotBlank() }?.let { out.addProperty("flow", it) }
                // xudp packet encoding matches the xray default for VLESS.
                out.addProperty("packet_encoding", "xudp")
                applyStream(profile, out)?.let { return it }
            }

            EConfigType.TROJAN -> {
                out.addProperty("type", "trojan")
                out.addProperty("password", profile.password.orEmpty())
                applyStream(profile, out)?.let { return it }
            }

            EConfigType.SHADOWSOCKS -> {
                out.addProperty("type", "shadowsocks")
                out.addProperty("method", profile.method.orEmpty())
                out.addProperty("password", profile.password.orEmpty())
            }

            EConfigType.SOCKS -> {
                out.addProperty("type", "socks")
                out.addProperty("version", "5")
                profile.username?.takeIf { it.isNotBlank() }?.let { out.addProperty("username", it) }
                profile.password?.takeIf { it.isNotBlank() }?.let { out.addProperty("password", it) }
            }

            EConfigType.HTTP -> {
                out.addProperty("type", "http")
                profile.username?.takeIf { it.isNotBlank() }?.let { out.addProperty("username", it) }
                profile.password?.takeIf { it.isNotBlank() }?.let { out.addProperty("password", it) }
                applyStream(profile, out)?.let { return it }
            }

            EConfigType.WIREGUARD ->
                return SingBoxOutboundResult.Unsupported("WireGuard translation not implemented yet")

            EConfigType.HYSTERIA, EConfigType.HYSTERIA2 ->
                return SingBoxOutboundResult.Unsupported("Hysteria translation not implemented yet")

            else ->
                return SingBoxOutboundResult.Unsupported("Unsupported config type: ${profile.configType}")
        }

        return SingBoxOutboundResult.Success(out)
    }

    /**
     * Adds `tls` and `transport` to [out]. Returns a non-null [SingBoxOutboundResult.Unsupported]
     * if the transport can't be represented in sing-box; null on success.
     */
    private fun applyStream(profile: ProfileItem, out: JsonObject): SingBoxOutboundResult.Unsupported? {
        buildTls(profile)?.let { out.add("tls", it) }

        when (val transport = buildTransport(profile)) {
            is TransportResult.None -> { /* plain tcp: no transport block */ }
            is TransportResult.Block -> out.add("transport", transport.json)
            is TransportResult.Unsupported -> return SingBoxOutboundResult.Unsupported(transport.reason)
        }
        return null
    }

    private fun buildTls(profile: ProfileItem): JsonObject? {
        val security = profile.security
        if (security != AppConfig.TLS && security != AppConfig.REALITY) return null

        val tls = JsonObject()
        tls.addProperty("enabled", true)

        val serverName = profile.sni?.takeIf { it.isNotBlank() }
            ?: profile.host?.takeIf { it.isNotBlank() }
            ?: profile.server
        serverName?.let { tls.addProperty("server_name", it) }

        profile.insecure?.let { tls.addProperty("insecure", it) }

        profile.alpn?.takeIf { it.isNotBlank() }?.let { alpn ->
            val arr = JsonArray()
            alpn.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { arr.add(it) }
            if (arr.size() > 0) tls.add("alpn", arr)
        }

        profile.fingerPrint?.takeIf { it.isNotBlank() }?.let { fp ->
            val utls = JsonObject()
            utls.addProperty("enabled", true)
            utls.addProperty("fingerprint", fp)
            tls.add("utls", utls)
        }

        if (security == AppConfig.REALITY) {
            val reality = JsonObject()
            reality.addProperty("enabled", true)
            profile.publicKey?.let { reality.addProperty("public_key", it) }
            profile.shortId?.takeIf { it.isNotBlank() }?.let { reality.addProperty("short_id", it) }
            tls.add("reality", reality)
        }

        return tls
    }

    private sealed interface TransportResult {
        object None : TransportResult
        data class Block(val json: JsonObject) : TransportResult
        data class Unsupported(val reason: String) : TransportResult
    }

    private fun buildTransport(profile: ProfileItem): TransportResult {
        return when (NetworkType.fromString(profile.network)) {
            NetworkType.TCP -> {
                // sing-box has no equivalent of xray's tcp/http header obfuscation.
                if (profile.headerType == AppConfig.HEADER_TYPE_HTTP) {
                    TransportResult.Unsupported("tcp+http header obfuscation is not supported by sing-box")
                } else {
                    TransportResult.None
                }
            }

            NetworkType.WS -> TransportResult.Block(JsonObject().apply {
                addProperty("type", "ws")
                profile.path?.takeIf { it.isNotBlank() }?.let { addProperty("path", it) }
                profile.host?.takeIf { it.isNotBlank() }?.let { host ->
                    add("headers", JsonObject().apply { addProperty("Host", host) })
                }
            })

            NetworkType.GRPC -> TransportResult.Block(JsonObject().apply {
                addProperty("type", "grpc")
                profile.serviceName?.takeIf { it.isNotBlank() }?.let { addProperty("service_name", it) }
            })

            NetworkType.HTTP, NetworkType.H2 -> TransportResult.Block(JsonObject().apply {
                addProperty("type", "http")
                profile.host?.takeIf { it.isNotBlank() }?.let { host ->
                    add("host", JsonArray().apply {
                        host.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { add(it) }
                    })
                }
                profile.path?.takeIf { it.isNotBlank() }?.let { addProperty("path", it) }
            })

            NetworkType.HTTP_UPGRADE -> TransportResult.Block(JsonObject().apply {
                addProperty("type", "httpupgrade")
                profile.host?.takeIf { it.isNotBlank() }?.let { addProperty("host", it) }
                profile.path?.takeIf { it.isNotBlank() }?.let { addProperty("path", it) }
            })

            NetworkType.KCP ->
                TransportResult.Unsupported("mKCP transport is not supported by sing-box")

            NetworkType.XHTTP ->
                TransportResult.Unsupported("xhttp transport is xray-only, not supported by sing-box")

            NetworkType.HYSTERIA ->
                TransportResult.Unsupported("hysteria transport handled by the Hysteria outbound, not here")
        }
    }
}
