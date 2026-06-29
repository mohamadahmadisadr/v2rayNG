package com.v2ray.ang

import android.content.Context
import android.os.Process
import androidx.multidex.MultiDexApplication
import androidx.work.Configuration
import androidx.work.WorkManager
import com.tencent.mmkv.MMKV
import androidx.hilt.work.HiltWorkerFactory
import com.v2ray.ang.AppConfig.ANG_PACKAGE
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.util.LogUtil
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlin.system.exitProcess

@HiltAndroidApp
class AngApplication : MultiDexApplication(), Configuration.Provider {
    companion object {
        lateinit var application: AngApplication
    }

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setDefaultProcessName("${ANG_PACKAGE}:bg")
            .setWorkerFactory(workerFactory)
            .build()

    /**
     * Attaches the base context to the application.
     * @param base The base context.
     */
    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        application = this
    }

    /**
     * Initializes the application.
     */
    override fun onCreate() {
        killIfStaleProcess()
        super.onCreate()

        MMKV.initialize(this)
        
        // Ensure native library is loaded early and only once
        com.v2ray.ang.core.CoreNativeManager.ensureLibraryLoaded()

        // WorkManager will be automatically initialized via getWorkManagerConfiguration()
        // if we don't call initialize() ourselves and remove it from Manifest (or if we do call it)
        // Actually, with Configuration.Provider, it's better to let it auto-initialize.
        // But since we have custom process name, we might need to be careful.

        // Ensure critical preference defaults are present in MMKV early
        SettingsManager.initApp(this)
        SettingsManager.setNightMode()

        es.dmoral.toasty.Toasty.Config.getInstance()
            .setGravity(android.view.Gravity.BOTTOM, 0, 300)
            .apply()
    }

    private fun killIfStaleProcess() {
        try {
            val packageUid = packageManager.getPackageUid(packageName, 0)
            val processUid = Process.myUid()

            if (processUid != packageUid) {
                // This process belongs to a stale installation (old UID).
                // Any Binder calls it makes (e.g. VpnService.prepare) will fail with
                // SecurityException because system_server will reject them.
                // Kill immediately before reaching any UI or service code.
                LogUtil.w(
                    AppConfig.TAG,
                    "Stale process detected: process uid=$processUid, package uid=$packageUid. Killing."
                )
                Process.killProcess(Process.myPid())
                // killProcess is asynchronous on some APIs; also call exitProcess as backup
                exitProcess(0)
            }

            // Process uid matches — store it for diagnostic use
            MmkvManager.encodeSettings("app_uid", packageUid)

        } catch (_: Exception) {
            // PackageManager unavailable — cannot verify; proceed normally
        }
    }
}
