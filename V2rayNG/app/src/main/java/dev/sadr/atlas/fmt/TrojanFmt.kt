package dev.sadr.atlas.fmt

import dev.sadr.atlas.AppConfig
import dev.sadr.atlas.dto.entities.ProfileItem
import dev.sadr.atlas.enums.EConfigType
import dev.sadr.atlas.enums.NetworkType
import dev.sadr.atlas.extension.idnHost
import dev.sadr.atlas.handler.MmkvManager
import dev.sadr.atlas.util.Utils
import java.net.URI

object TrojanFmt : FmtBase() {
    /**
     * Parses a Trojan URI string into a ProfileItem object.
     *
     * @param str the Trojan URI string to parse
     * @return the parsed ProfileItem object, or null if parsing fails
     */
    fun parse(str: String): ProfileItem? {
        try {
            val config = ProfileItem.create(EConfigType.TROJAN)

            val uri = URI(Utils.fixIllegalUrl(str))
            config.remarks = Utils.decodeURIComponent(uri.fragment.orEmpty())?.let { it.ifEmpty { "none" } } ?: "none"
            config.server = uri.idnHost
            config.serverPort = uri.port.toString()
            config.password = Utils.decodeURIComponent(uri.userInfo.orEmpty())?.trim() ?: return null

            if (uri.rawQuery.isNullOrEmpty()) {
                config.network = NetworkType.TCP.type
                config.security = AppConfig.TLS
                config.insecure = false
            } else {
                val queryParam = getQueryParam(uri)

                getItemFormQuery(config, queryParam)
                config.security = queryParam["security"] ?: AppConfig.TLS
            }

            return config
        } catch (_: Exception) {
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

        return toUri(config, config.password, dicQuery)
    }
}
