package dev.sadr.atlas.core.engine

import android.content.Context

/**
 * Engine-agnostic factory + process-global environment for a proxy core implementation.
 *
 * There is one engine per core technology (xray today, sing-box later). [CoreNativeManager]
 * holds the active engine behind this interface; swapping engines is a single assignment.
 */
interface ProxyCoreEngine {

    /** Loads the engine's native library exactly once per process. */
    fun ensureLibraryLoaded()

    /**
     * Initializes process-global engine state (asset/cert paths, keys, native context).
     * Must be idempotent — safe to call more than once.
     */
    fun initEnv(context: Context)

    /** Human-readable engine + core version string. */
    fun version(): String

    /**
     * Engine-specific hook for the browser-dialer transport. No-op on engines without it.
     */
    fun reconcileBrowserDialer(dialerAddr: String)

    /** Creates a new, stopped core instance wired to [callback]. */
    fun createCore(callback: ProxyCoreCallback): ProxyCore
}
