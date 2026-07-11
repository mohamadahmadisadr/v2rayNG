package dev.sadr.atlas.core

import android.content.Context
import dev.sadr.atlas.core.engine.EngineType
import dev.sadr.atlas.core.engine.singbox.SingBoxConfigBuilder
import dev.sadr.atlas.core.engine.singbox.SingBoxConfigResult
import dev.sadr.atlas.dto.ConfigResult
import dev.sadr.atlas.dto.entities.ProfileItem
import dev.sadr.atlas.handler.MmkvManager

/**
 * Engine-aware config facade. The rest of the app asks for a config for a profile; this routes
 * to the xray builder ([CoreConfigManager]) or the sing-box builder ([SingBoxConfigBuilder])
 * based on the currently-active engine, so callers don't branch on engine type themselves.
 *
 * Currently covers the speedtest/ping path (the VPN run path branches inline in
 * [CoreServiceManager]). For sing-box the speedtest config includes a local SOCKS inbound on
 * [port] so [dev.sadr.atlas.core.engine.singbox.SingBoxCore.measureDelay] can dial through it.
 */
object ProxyConfigManager {

    fun getSpeedtestConfig(context: Context, profile: ProfileItem, port: Int): ConfigResult {
        return when (CoreNativeManager.activeEngineType) {
            EngineType.XRAY -> CoreConfigManager.getV2rayConfig4Speedtest(context, profile, port)
            EngineType.SINGBOX -> toConfigResult(profile.guid, SingBoxConfigBuilder.build(profile, port))
        }
    }

    fun getSpeedtestConfig(context: Context, guid: String, port: Int): ConfigResult {
        return when (CoreNativeManager.activeEngineType) {
            EngineType.XRAY -> CoreConfigManager.getV2rayConfig4Speedtest(context, guid, port)
            EngineType.SINGBOX -> {
                val profile = MmkvManager.decodeServerConfig(guid)
                    ?: return ConfigResult(status = false, guid = guid, errorMessage = "Config not found")
                toConfigResult(guid, SingBoxConfigBuilder.build(profile, port))
            }
        }
    }

    private fun toConfigResult(guid: String, result: SingBoxConfigResult): ConfigResult = when (result) {
        is SingBoxConfigResult.Success -> ConfigResult(status = true, guid = guid, content = result.json)
        is SingBoxConfigResult.Unsupported ->
            ConfigResult(status = false, guid = guid, errorMessage = "sing-box: ${result.reason}")
    }
}
