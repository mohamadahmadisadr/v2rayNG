package dev.sadr.atlas.fmt

import android.text.TextUtils
import dev.sadr.atlas.AppConfig
import dev.sadr.atlas.dto.VmessQRCode
import dev.sadr.atlas.dto.entities.ProfileItem
import dev.sadr.atlas.enums.EConfigType
import dev.sadr.atlas.enums.NetworkType
import dev.sadr.atlas.extension.idnHost
import dev.sadr.atlas.util.JsonUtil
import dev.sadr.atlas.util.LogUtil
import dev.sadr.atlas.util.Utils
import java.net.URI
import java.net.URISyntaxException
import java.net.URLEncoder

object VlessFmt : FmtBase() {

    /**
     * Parses a Vless URI string into a ProfileItem object.
     *
     * @param str the Vless URI string to parse
     * @return the parsed ProfileItem object, or null if parsing fails
     */
    fun parse(str: String): ProfileItem? {
        var rawUrl = str.trim()
        
        // Pattern 2: JSON object as the entire authority
        if (rawUrl.substringAfter("://").trimStart().startsWith("{")) {
            return parseLegacyJsonFormat(rawUrl)
        }

        // Pattern 3: Strip trailing backtick and URL-encode userinfo
        rawUrl = rawUrl.removeSuffix("`")
        val schemeEnd = rawUrl.indexOf("://") + 3
        val atIndex = rawUrl.indexOf("@", schemeEnd)
        if (atIndex != -1) {
            val userInfo = rawUrl.substring(schemeEnd, atIndex)
            val encoded = URLEncoder.encode(userInfo, "UTF-8").replace("+", "%20")
            rawUrl = rawUrl.substring(0, schemeEnd) + encoded + rawUrl.substring(atIndex)
        }

        // Pattern 1: Unencoded JSON in query parameters
        rawUrl = rawUrl.replace(Regex("extra=([^&]*)")) {
            "extra=" + URLEncoder.encode(it.groupValues[1], "UTF-8")
        }
        // General fix for any param whose value starts with {
        rawUrl = rawUrl.replace(Regex("=([{][^&]*)")) {
            "=" + URLEncoder.encode(it.groupValues[1], "UTF-8")
        }

        // Pattern 4: Emoji and non-ASCII in fragment
        val fragmentIndex = rawUrl.indexOf("#")
        if (fragmentIndex != -1) {
            val fragment = rawUrl.substring(fragmentIndex + 1)
            rawUrl = rawUrl.substring(0, fragmentIndex) + "#" + URLEncoder.encode(fragment, "UTF-8")
        }

        try {
            val config = ProfileItem.create(EConfigType.VLESS)
            val uri = URI(Utils.fixIllegalUrl(rawUrl))
            
            val queryParam = getQueryParam(uri)

            val rawEncryption = queryParam["encryption"] ?: "none"
            val encryption = rawEncryption.substringBefore("@").trim()
            // Besides none/zero, Xray supports VLESS Encryption values such as
            // "mlkem768x25519plus.native.0rtt.<key>" (post-quantum) — pass them through.
            val validEncryption = encryption == "none" || encryption == "zero"
                    || encryption.startsWith("mlkem768x25519plus")
                    || encryption.startsWith("x25519")
            if (!validEncryption) {
                LogUtil.d(AppConfig.TAG, "Skipping VLESS config: invalid encryption '$encryption'")
                return null
            }

            config.remarks = Utils.decodeURIComponent(uri.fragment.orEmpty())?.let { it.ifEmpty { "none" } } ?: "none"
            config.server = uri.idnHost
            config.serverPort = uri.port.toString()
            config.password = Utils.decodeURIComponent(uri.userInfo.orEmpty())?.trim() ?: return null
            config.method = encryption

            getItemFormQuery(config, queryParam)

            val rawFlow = queryParam["flow"] ?: ""
            config.flow = when {
                rawFlow.isEmpty() || rawFlow == "none" -> ""
                rawFlow.startsWith("xtls-rprx-vision-udp443") -> "xtls-rprx-vision-udp443"
                rawFlow.startsWith("xtls-rprx-vision") -> "xtls-rprx-vision"
                else -> "" // Default to empty instead of null to allow testing transport
            }

            if (config.security == AppConfig.REALITY) {
                if (config.publicKey.isNullOrEmpty()) {
                    LogUtil.d(AppConfig.TAG, "Skipping REALITY config: missing publicKey")
                    return null
                }
            }

            return config
        } catch (_: URISyntaxException) {
            LogUtil.d(AppConfig.TAG, "Vless parsing failed for malformed URI: $rawUrl")
            return null
        } catch (e: Exception) {
            LogUtil.d(AppConfig.TAG, "Vless parsing failed: ${e.message}")
            return null
        }
    }

    /**
     * Parses the legacy JSON share format for VLESS.
     */
    private fun parseLegacyJsonFormat(str: String): ProfileItem? {
        try {
            val jsonPart = str.substringAfter("://").substringBefore("#").trim()
            val vlessJson = JsonUtil.fromJson(jsonPart, VmessQRCode::class.java) ?: return null
            
            if (TextUtils.isEmpty(vlessJson.add)
                || TextUtils.isEmpty(vlessJson.port)
                || TextUtils.isEmpty(vlessJson.id)
            ) {
                return null
            }

            val config = ProfileItem.create(EConfigType.VLESS)
            config.remarks = vlessJson.ps.ifEmpty { str.substringAfter("#", "none") }
            config.server = vlessJson.add
            config.serverPort = vlessJson.port
            config.password = vlessJson.id.trim()
            config.method = if (TextUtils.isEmpty(vlessJson.scy)) "none" else vlessJson.scy
            
            config.network = vlessJson.net.ifEmpty { NetworkType.TCP.type }
            config.headerType = vlessJson.type
            config.host = vlessJson.host
            config.path = vlessJson.path

            when (NetworkType.fromString(config.network)) {
                NetworkType.GRPC -> {
                    config.serviceName = vlessJson.path
                    config.authority = vlessJson.host
                }
                else -> {}
            }

            config.security = vlessJson.tls
            config.sni = vlessJson.sni
            config.fingerPrint = vlessJson.fp
            config.alpn = vlessJson.alpn
            config.insecure = vlessJson.insecure == "1"

            return config
        } catch (e: Exception) {
            LogUtil.d(AppConfig.TAG, "Failed to parse legacy VLESS JSON format")
            return null
        }
    }

    /**
     * Converts a ProfileItem object to a URI string.
     *
     * @param config the ProfileItem object to convert
     * @return the converted URI string
     */
    fun toUri(config: ProfileItem): String {
        val dicQuery = getQueryDic(config)
        dicQuery["encryption"] = config.method ?: "none"

        return toUri(config, config.password, dicQuery)
    }

}
