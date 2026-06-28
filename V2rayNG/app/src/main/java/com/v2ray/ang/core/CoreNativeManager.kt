package com.v2ray.ang.core

import android.content.Context
import com.v2ray.ang.AppConfig
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import go.Seq
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * V2Ray Native Library Manager
 */
object CoreNativeManager {
    private val initialized = AtomicBoolean(false)
    private val libraryLoaded = AtomicBoolean(false)

    /**
     * Implementation of CoreCallbackHandler for internal tests.
     */
    private class InternalCoreCallback : CoreCallbackHandler {
        override fun startup(): Long = 0
        override fun shutdown(): Long = 0
        override fun onEmitStatus(l: Long, s: String?): Long = 0
    }

    /**
     * Ensure the native Go library is loaded exactly once per process.
     */
    fun ensureLibraryLoaded() {
        if (libraryLoaded.compareAndSet(false, true)) {
            try {
                // Try standard Gomobile names
                System.loadLibrary("gojni")
            } catch (_: UnsatisfiedLinkError) {
                try {
                    System.loadLibrary("v2ray")
                } catch (_: Exception) {}
            } catch (_: Exception) {}
        }
    }

    /**
     * Initialize V2Ray core environment.
     */
    fun initCoreEnv(context: Context?) {
        if (context == null) {
            LogUtil.w(AppConfig.TAG, "V2Ray core environment initialization skipped: context is null")
            return
        }
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

    fun reconcileBrowserDialer(dialerAddr: String) {
        try {
            Libv2ray.reconcileBrowserDialer(dialerAddr)
            LogUtil.i(AppConfig.TAG, "Browser dialer reconciled successfully with address: $dialerAddr")
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to reconcile browser dialer with address: $dialerAddr", e)
        }
    }


    /**
     * Get V2Ray core version.
     */
    fun getLibVersion(): String {
        return try {
            Libv2ray.checkVersionX()
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to check V2Ray version", e)
            "Unknown"
        }
    }

    private val testControllers = arrayOfNulls<CoreController>(3)
    private val testControllerMutexes = Array(3) { Mutex() }
    
    // Allow concurrent core operations up to the number of controllers
    private val coreOperationSemaphore = Semaphore(3)

    /**
     * Measure outbound connection delay using a robust manual lifecycle.
     * uses a pool of 3 persistent controllers.
     */
    suspend fun measureOutboundDelay(config: String, testUrl: String, workerId: Int = 0): Long {
        if (config.isBlank()) return -1L
        val id = workerId % 3
        
        return testControllerMutexes[id].withLock {
            var controller = testControllers[id]
            
            coreOperationSemaphore.withPermit {
                if (controller == null) {
                    controller = Libv2ray.newCoreController(InternalCoreCallback())
                    testControllers[id] = controller
                }

                try {
                    if (controller?.isRunning == true) {
                        controller?.stopLoop()
                    }
                    
                    controller?.startLoop(config, 0)
                    
                    var ready = false
                    for (i in 1..50) {
                        if (controller?.isRunning == true) {
                            ready = true
                            break
                        }
                        delay(20)
                    }
                    
                    if (!ready) {
                        LogUtil.w(AppConfig.TAG, "Test-Core-$id: failed to start")
                        return@withPermit -1L
                    }

                    // Reduced settle time
                    delay(100)

                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Test-Core-$id: startLoop failed", e)
                    return@withPermit -1L
                }
            }

            try {
                controller?.measureDelay(testUrl) ?: -1L
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                val msg = e.message ?: ""
                if (msg.contains("StandaloneCoroutine was cancelled")) {
                    throw CancellationException(msg)
                }
                if (msg.contains("core instance is nil")) {
                    LogUtil.w(AppConfig.TAG, "Test-Core-$id: instance is nil, skipping")
                    return@withLock -1L
                }
                when {
                    msg.contains("closed pipe") -> {
                        LogUtil.d(AppConfig.TAG, "Test-Core-$id: interrupted (closed pipe)")
                        -1L
                    }
                    msg.contains("REALITY: received real certificate") ||
                    msg.contains("certificate is valid for") ||
                    msg.contains("tls: failed to verify certificate") ||
                    msg.contains("tls: CurvePreferences includes unsupported curve") ||
                    msg.contains("x509") || msg.contains("certificate") -> {
                        LogUtil.w(AppConfig.TAG, "Test-Core-$id: fatal config error: $msg")
                        -2L // Fatal/Blacklist
                    }
                    msg.contains("invalid status: 400") || msg.contains("invalid status: 403") || 
                    msg.contains("invalid status: 401") || msg.contains("invalid status: 407") -> {
                        LogUtil.d(AppConfig.TAG, "Test-Core-$id: partial success (HTTP $msg)")
                        5000L 
                    }
                    msg.contains("tls: handshake failure") -> {
                        LogUtil.d(AppConfig.TAG, "Test-Core-$id: Handshake failure")
                        -1L
                    }
                    msg.contains("EOF") -> {
                        LogUtil.d(AppConfig.TAG, "Test-Core-$id: Connection EOF")
                        -1L
                    }
                    msg.contains("context deadline exceeded") -> {
                        LogUtil.d(AppConfig.TAG, "Test-Core-$id: Ping timeout")
                        -1L
                    }
                    else -> {
                        LogUtil.e(AppConfig.TAG, "Test-Core-$id: Ping failed: $msg")
                        -1L
                    }
                }
            } finally {
                coreOperationSemaphore.withPermit {
                    try {
                        if (controller?.isRunning == true) {
                            controller?.stopLoop()
                        }
                    } catch (_: Throwable) {}
                }
                delay(100)
            }
        }
    }

    /**
     * Stop all test controllers when the discovery session is finished.
     */
    suspend fun stopTestControllers() {
        for (i in 0..2) {
            testControllerMutexes[i].withLock {
                coreOperationSemaphore.withPermit {
                    testControllers[i]?.let {
                        if (it.isRunning == true) it.stopLoop()
                    }
                    testControllers[i] = null
                }
            }
        }
        LogUtil.i(AppConfig.TAG, "All test cores stopped")
    }

    /**
     * Create a new core controller instance.
     */
    fun newCoreController(handler: CoreCallbackHandler): CoreController {
        return try {
            Libv2ray.newCoreController(handler)
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to create core controller", e)
            throw e
        }
    }
}
