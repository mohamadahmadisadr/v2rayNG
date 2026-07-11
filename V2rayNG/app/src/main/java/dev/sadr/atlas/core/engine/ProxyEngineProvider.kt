package dev.sadr.atlas.core.engine

import dev.sadr.atlas.AppConfig
import dev.sadr.atlas.core.engine.singbox.SingBoxCoreEngine
import dev.sadr.atlas.core.engine.xray.XrayCoreEngine
import dev.sadr.atlas.handler.MmkvManager

/**
 * Resolves which [ProxyCoreEngine] to run, based on the persisted [EngineType] setting.
 *
 * Pure resolver — it does not install the engine itself; the app entry point reads
 * [activeEngine] and assigns it to [dev.sadr.atlas.core.CoreNativeManager.engine]. Keeps the
 * engine package free of any dependency on the manager.
 */
object ProxyEngineProvider {

    /** The engine type currently selected in settings (defaults to [EngineType.XRAY]). */
    fun selected(): EngineType =
        EngineType.fromId(MmkvManager.decodeSettingsString(AppConfig.PREF_PROXY_ENGINE))

    /** Maps an [EngineType] to its concrete engine. */
    fun engineFor(type: EngineType): ProxyCoreEngine = when (type) {
        EngineType.XRAY -> XrayCoreEngine
        EngineType.SINGBOX -> SingBoxCoreEngine
    }

    /** The engine to run this session, resolved from settings. */
    fun activeEngine(): ProxyCoreEngine = engineFor(selected())
}
