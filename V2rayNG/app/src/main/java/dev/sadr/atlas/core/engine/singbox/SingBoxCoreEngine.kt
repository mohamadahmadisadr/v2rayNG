package dev.sadr.atlas.core.engine.singbox

import android.content.Context
import dev.sadr.atlas.AppConfig
import dev.sadr.atlas.core.engine.ProxyCore
import dev.sadr.atlas.core.engine.ProxyCoreCallback
import dev.sadr.atlas.core.engine.ProxyCoreEngine
import dev.sadr.atlas.util.LogUtil
import go.Seq
import libsingbox.Libsingbox

/**
 * [ProxyCoreEngine] backed by sing-box (the combined `libcore` gomobile AAR, package
 * `libsingbox`). Shares the single `libgojni.so` / `go.Seq` runtime with the xray engine.
 */
object SingBoxCoreEngine : ProxyCoreEngine {

    override fun ensureLibraryLoaded() {
        // Same native lib as xray (one libgojni.so in the combined AAR); idempotent.
        try {
            System.loadLibrary("gojni")
        } catch (_: Throwable) {
        }
    }

    override fun initEnv(context: Context) {
        ensureLibraryLoaded()
        try {
            Seq.setContext(context.applicationContext)
        } catch (e: Throwable) {
            LogUtil.e(AppConfig.TAG, "Failed to set sing-box native context", e)
        }
        // Phase-1 minimal configs need no asset/geo initialization.
    }

    override fun version(): String {
        return try {
            Libsingbox.checkVersion()
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to check sing-box version", e)
            "Unknown"
        }
    }

    override fun reconcileBrowserDialer(dialerAddr: String) {
        // sing-box has no browser-dialer transport; no-op.
    }

    override fun createCore(callback: ProxyCoreCallback): ProxyCore {
        return try {
            SingBoxCore(Libsingbox.newCoreController(SingBoxCoreCallbackAdapter(callback)))
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to create sing-box core controller", e)
            throw e
        }
    }
}
