package dev.sadr.atlas.core.engine

/**
 * Engine-agnostic abstraction of a single proxy core instance (one running tunnel or
 * one test/ping instance).
 *
 * This is the seam that lets the app swap the underlying engine (currently xray via
 * [dev.sadr.atlas.core.engine.xray.XrayCore], later sing-box) without rewriting the
 * managers that drive it. Method names/semantics mirror the xray `CoreController` so the
 * xray adapter is a thin pass-through; other engines adapt to this contract.
 */
interface ProxyCore {

    /** Whether the core is currently started and running. */
    val isRunning: Boolean

    /**
     * Starts the core with the given rendered config.
     *
     * @param config the engine-native configuration content (JSON for xray).
     * @param tunFd the VPN TUN file descriptor, or 0 when no TUN is used (test/ping instances).
     */
    fun startLoop(config: String, tunFd: Int)

    /** Stops the core and releases its resources. Safe to call when not running. */
    fun stopLoop()

    /**
     * Measures round-trip delay to [url] through this running core, in milliseconds.
     *
     * Implementations may throw to signal specific failure modes (e.g. TLS/cert errors,
     * HTTP status, timeouts); callers parse those messages. Returns a non-positive value
     * or throws on failure.
     */
    fun measureDelay(url: String): Long

    /**
     * Returns accumulated outbound traffic stats as the engine's raw single-line encoding
     * (`tag,direction,value;...` for xray), or an empty string if unavailable.
     */
    fun queryAllOutboundTrafficStats(): String

    /**
     * Registers (or clears, when null) a per-app process resolver used for Android
     * process-based routing. No-op on engines that don't support it.
     */
    fun registerProcessFinder(finder: ProxyProcessFinder?)
}

/**
 * Lifecycle callbacks emitted by a [ProxyCore]. Mirrors xray's `CoreCallbackHandler`;
 * adapters translate to/from the engine-native callback type.
 */
interface ProxyCoreCallback {
    fun onStartup(): Long
    fun onShutdown(): Long
    fun onEmitStatus(code: Long, message: String?): Long
}

/**
 * Resolves the owning app (UID) of a connection for process-based routing.
 * Mirrors xray's `ProcessFinder`.
 */
fun interface ProxyProcessFinder {
    fun findProcessByConnection(
        network: String,
        srcIP: String,
        srcPort: Long,
        destIP: String,
        destPort: Long
    ): Long
}
