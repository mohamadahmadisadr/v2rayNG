package dev.sadr.atlas.core

import android.content.Context
import dev.sadr.atlas.AppConfig
import dev.sadr.atlas.core.engine.EngineType
import dev.sadr.atlas.core.engine.ProxyCore
import dev.sadr.atlas.core.engine.ProxyCoreCallback
import dev.sadr.atlas.core.engine.ProxyCoreEngine
import dev.sadr.atlas.core.engine.singbox.SingBoxCoreEngine
import dev.sadr.atlas.core.engine.xray.XrayCoreEngine
import dev.sadr.atlas.util.LogUtil
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Proxy core manager.
 *
 * Delegates all engine work to a [ProxyCoreEngine]; swapping the whole app to a different
 * core (e.g. sing-box) is a single assignment to [engine]. Everything below is engine
 * agnostic and speaks only the [ProxyCore] abstraction.
 */
object CoreNativeManager {

    /**
     * The active proxy engine. The single point to change when swapping cores.
     * Defaults to [XrayCoreEngine].
     */
    var engine: ProxyCoreEngine = XrayCoreEngine

    /** The type of the currently-active [engine], for callers that must build engine-native configs. */
    val activeEngineType: EngineType
        get() = if (engine === SingBoxCoreEngine) EngineType.SINGBOX else EngineType.XRAY

    /**
     * Callback for internal test/ping cores — they don't need lifecycle notifications.
     */
    private class InternalCoreCallback : ProxyCoreCallback {
        override fun onStartup(): Long = 0
        override fun onShutdown(): Long = 0
        override fun onEmitStatus(code: Long, message: String?): Long = 0
    }

    /**
     * Ensure the native engine library is loaded exactly once per process.
     */
    fun ensureLibraryLoaded() {
        engine.ensureLibraryLoaded()
    }

    /**
     * Initialize the proxy core environment.
     */
    fun initCoreEnv(context: Context?) {
        if (context == null) {
            LogUtil.w(AppConfig.TAG, "Core environment initialization skipped: context is null")
            return
        }
        engine.initEnv(context)
    }

    fun reconcileBrowserDialer(dialerAddr: String) {
        engine.reconcileBrowserDialer(dialerAddr)
    }

    /**
     * Get the proxy core version.
     */
    fun getLibVersion(): String = engine.version()

    private const val MAX_TEST_CONTROLLERS = 64
    private val testControllers = arrayOfNulls<ProxyCore>(MAX_TEST_CONTROLLERS)
    private val testControllerMutexes = Array(MAX_TEST_CONTROLLERS) { Mutex() }

    // Allow concurrent core operations up to the number of controllers
    private val coreOperationSemaphore = Semaphore(MAX_TEST_CONTROLLERS)

    /**
     * Measure outbound connection delay using a robust manual lifecycle.
     * uses a pool of persistent controllers.
     */
    suspend fun measureOutboundDelay(config: String, testUrl: String, workerId: Int = 0): Long {
        if (config.isBlank()) return -1L
        val id = workerId % MAX_TEST_CONTROLLERS

        return testControllerMutexes[id].withLock {
            var controller = testControllers[id]

            coreOperationSemaphore.withPermit {
                if (controller == null) {
                    controller = engine.createCore(InternalCoreCallback())
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
        for (i in 0 until MAX_TEST_CONTROLLERS) {
            testControllerMutexes[i].withLock {
                coreOperationSemaphore.withPermit {
                    testControllers[i]?.let {
                        if (it.isRunning) it.stopLoop()
                    }
                    testControllers[i] = null
                }
            }
        }
        LogUtil.i(AppConfig.TAG, "All test cores stopped")
    }

    /**
     * Create a new proxy core instance wired to [callback].
     */
    fun createCore(callback: ProxyCoreCallback): ProxyCore {
        return engine.createCore(callback)
    }
}
