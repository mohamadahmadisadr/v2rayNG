package dev.sadr.atlas.core.engine.singbox

import com.google.gson.JsonParser
import dev.sadr.atlas.AppConfig
import dev.sadr.atlas.dto.entities.ProfileItem
import dev.sadr.atlas.enums.EConfigType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [SingBoxOutboundBuilder] / [SingBoxConfigBuilder].
 *
 * These validate the JSON *structure* the translator emits; they do not (and can't here) prove
 * sing-box accepts it at runtime — that needs the built AAR.
 */
class SingBoxOutboundBuilderTest {

    private fun success(profile: ProfileItem) =
        (SingBoxOutboundBuilder.build(profile) as SingBoxOutboundResult.Success).outbound

    @Test
    fun `vless with reality and ws transport`() {
        val p = ProfileItem(
            configType = EConfigType.VLESS,
            server = "example.com",
            serverPort = "443",
            password = "uuid-123",
            flow = "xtls-rprx-vision",
            security = AppConfig.REALITY,
            sni = "sni.example.com",
            publicKey = "PBK",
            shortId = "abcd",
            fingerPrint = "chrome",
            network = "ws",
            path = "/ws",
            host = "host.example.com",
        )
        val out = success(p)

        assertEquals("vless", out.get("type").asString)
        assertEquals("example.com", out.get("server").asString)
        assertEquals(443, out.get("server_port").asInt)
        assertEquals("uuid-123", out.get("uuid").asString)
        assertEquals("xtls-rprx-vision", out.get("flow").asString)

        val tls = out.getAsJsonObject("tls")
        assertTrue(tls.get("enabled").asBoolean)
        assertEquals("sni.example.com", tls.get("server_name").asString)
        assertEquals("PBK", tls.getAsJsonObject("reality").get("public_key").asString)
        assertEquals("abcd", tls.getAsJsonObject("reality").get("short_id").asString)
        assertEquals("chrome", tls.getAsJsonObject("utls").get("fingerprint").asString)

        val transport = out.getAsJsonObject("transport")
        assertEquals("ws", transport.get("type").asString)
        assertEquals("/ws", transport.get("path").asString)
        assertEquals("host.example.com", transport.getAsJsonObject("headers").get("Host").asString)
    }

    @Test
    fun `vmess maps cipher to security`() {
        val p = ProfileItem(
            configType = EConfigType.VMESS,
            server = "1.2.3.4",
            serverPort = "80",
            password = "vmess-uuid",
            method = "aes-128-gcm",
            network = "tcp",
        )
        val out = success(p)
        assertEquals("vmess", out.get("type").asString)
        assertEquals("vmess-uuid", out.get("uuid").asString)
        assertEquals("aes-128-gcm", out.get("security").asString)
        assertEquals(0, out.get("alter_id").asInt)
        assertFalse(out.has("tls"))       // no security set
        assertFalse(out.has("transport")) // plain tcp
    }

    @Test
    fun `shadowsocks method and password`() {
        val p = ProfileItem(
            configType = EConfigType.SHADOWSOCKS,
            server = "ss.example.com",
            serverPort = "8388",
            method = "aes-256-gcm",
            password = "secret",
        )
        val out = success(p)
        assertEquals("shadowsocks", out.get("type").asString)
        assertEquals("aes-256-gcm", out.get("method").asString)
        assertEquals("secret", out.get("password").asString)
    }

    @Test
    fun `mkcp is reported unsupported`() {
        val p = ProfileItem(
            configType = EConfigType.VLESS,
            server = "example.com",
            serverPort = "443",
            password = "id",
            network = "kcp",
        )
        val r = SingBoxOutboundBuilder.build(p)
        assertTrue(r is SingBoxOutboundResult.Unsupported)
    }

    @Test
    fun `xhttp is reported unsupported`() {
        val p = ProfileItem(
            configType = EConfigType.VLESS,
            server = "example.com",
            serverPort = "443",
            password = "id",
            network = "xhttp",
        )
        assertTrue(SingBoxOutboundBuilder.build(p) is SingBoxOutboundResult.Unsupported)
    }

    @Test
    fun `full config has inbound and proxy outbound`() {
        val p = ProfileItem(
            configType = EConfigType.TROJAN,
            server = "t.example.com",
            serverPort = "443",
            password = "pw",
            security = AppConfig.TLS,
            network = "tcp",
        )
        val result = SingBoxConfigBuilder.build(p, socksPort = 10808)
        assertTrue(result is SingBoxConfigResult.Success)

        val root = JsonParser.parseString((result as SingBoxConfigResult.Success).json).asJsonObject
        val inbound = root.getAsJsonArray("inbounds").get(0).asJsonObject
        assertEquals("mixed", inbound.get("type").asString)
        assertEquals(10808, inbound.get("listen_port").asInt)

        val outbound = root.getAsJsonArray("outbounds").get(0).asJsonObject
        assertEquals("trojan", outbound.get("type").asString)
        assertEquals("proxy", outbound.get("tag").asString)
        assertEquals("proxy", root.getAsJsonObject("route").get("final").asString)
    }

    @Test
    fun `unsupported config type propagates through config builder`() {
        val p = ProfileItem(
            configType = EConfigType.HYSTERIA2,
            server = "h.example.com",
            serverPort = "443",
            password = "pw",
        )
        assertTrue(SingBoxConfigBuilder.build(p, socksPort = 10808) is SingBoxConfigResult.Unsupported)
    }
}
