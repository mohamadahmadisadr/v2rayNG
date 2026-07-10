package dev.sadr.atlas.core.engine

import dev.sadr.atlas.AppConfig
import dev.sadr.atlas.core.engine.xray.XrayCoreEngine
import dev.sadr.atlas.handler.MmkvManager
import dev.sadr.atlas.util.LogUtil

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

    /**
     * Maps an [EngineType] to its concrete engine. [EngineType.SINGBOX] is not yet backed by
     * a native library, so it falls back to xray (logged) — a flipped flag can never brick the
     * connect path. Replace the fallback with `SingBoxCoreEngine` once it exists.
     */
    fun engineFor(type: EngineType): ProxyCoreEngine = when (type) {
        EngineType.XRAY -> XrayCoreEngine
        EngineType.SINGBOX -> {
            LogUtil.w(AppConfig.TAG, "sing-box engine not available yet; falling back to xray")
            XrayCoreEngine
        }
    }

    /** The engine to run this session, resolved from settings. */
    fun activeEngine(): ProxyCoreEngine = engineFor(selected())
}
