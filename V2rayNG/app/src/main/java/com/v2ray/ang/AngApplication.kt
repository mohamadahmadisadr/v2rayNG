package com.v2ray.ang

import android.content.Context
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
        super.onCreate()

        MMKV.initialize(this)
        clearStaleUidCache()
        
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

    private fun clearStaleUidCache() {
        try {
            val currentUid = packageManager.getPackageUid(packageName, 0)
            val cachedUid = MmkvManager.decodeSettingsInt("app_uid", -1)
            if (cachedUid != -1 && cachedUid != currentUid) {
                // UID changed (reinstall) — clear ALL cached state
                MmkvManager.removeSettings("app_uid")
                MmkvManager.removeSettings("vpn_prepared")
                MmkvManager.removeSettings(AppConfig.PREF_LAST_WORKING_CONFIG)
                LogUtil.w(AppConfig.TAG, "UID changed from $cachedUid to $currentUid, cleared cache")
            }
            MmkvManager.encodeSettings("app_uid", currentUid)
        } catch (_: Exception) {
        }
    }
}
