package dev.sadr.atlas.core.engine.xray

import android.content.Context
import dev.sadr.atlas.AppConfig
import dev.sadr.atlas.core.engine.ProxyCore
import dev.sadr.atlas.core.engine.ProxyCoreCallback
import dev.sadr.atlas.core.engine.ProxyCoreEngine
import dev.sadr.atlas.util.LogUtil
import dev.sadr.atlas.util.Utils
import go.Seq
import libv2ray.Libv2ray
import java.util.concurrent.atomic.AtomicBoolean

/**
 * [ProxyCoreEngine] backed by xray (the `libv2ray` gomobile AAR). Holds the process-global
 * xray environment and mints [XrayCore] instances. This is the concrete engine that
 * [dev.sadr.atlas.core.CoreNativeManager] delegates to today.
 */
object XrayCoreEngine : ProxyCoreEngine {

    private val initialized = AtomicBoolean(false)
    private val libraryLoaded = AtomicBoolean(false)

    override fun ensureLibraryLoaded() {
        if (libraryLoaded.compareAndSet(false, true)) {
            try {
                System.loadLibrary("gojni")
            } catch (_: UnsatisfiedLinkError) {
                try {
                    System.loadLibrary("v2ray")
                } catch (_: Exception) {}
            } catch (_: Exception) {}
        }
    }

    override fun initEnv(context: Context) {
        ensureLibraryLoaded()
        if (initialized.compareAndSet(false, true)) {
            try {
                Seq.setContext(context.applicationContext)
                val assetPath = Utils.userAssetPath(context)
                val deviceId = Utils.getDeviceIdForXUDPBaseKey()
                Libv2ray.initCoreEnv(assetPath, deviceId)
                LogUtil.i(AppConfig.TAG, "V2Ray core environment initialized successfully")
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to initialize V2Ray core environment", e)
                initialized.set(false)
                throw e
            }
        } else {
            LogUtil.d(AppConfig.TAG, "V2Ray core environment already initialized, skipping")
        }
    }

    override fun version(): String {
        return try {
            Libv2ray.checkVersionX()
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to check V2Ray version", e)
            "Unknown"
        }
    }

    override fun reconcileBrowserDialer(dialerAddr: String) {
        try {
            Libv2ray.reconcileBrowserDialer(dialerAddr)
            LogUtil.i(AppConfig.TAG, "Browser dialer reconciled successfully with address: $dialerAddr")
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to reconcile browser dialer with address: $dialerAddr", e)
        }
    }

    override fun createCore(callback: ProxyCoreCallback): ProxyCore {
        return try {
            XrayCore(Libv2ray.newCoreController(XrayCoreCallbackAdapter(callback)))
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to create core controller", e)
            throw e
        }
    }
}
