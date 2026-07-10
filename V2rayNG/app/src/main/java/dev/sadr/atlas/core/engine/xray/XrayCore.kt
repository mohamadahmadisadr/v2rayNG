package dev.sadr.atlas.core.engine.xray

import dev.sadr.atlas.core.engine.ProxyCore
import dev.sadr.atlas.core.engine.ProxyCoreCallback
import dev.sadr.atlas.core.engine.ProxyProcessFinder
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.ProcessFinder

/**
 * [ProxyCore] adapter over the xray gomobile `CoreController`. A thin pass-through:
 * the abstraction was modelled on xray's surface, so no behavior is added here.
 */
internal class XrayCore(private val controller: CoreController) : ProxyCore {

    override val isRunning: Boolean
        get() = controller.isRunning

    override fun startLoop(config: String, tunFd: Int) {
        controller.startLoop(config, tunFd)
    }

    override fun stopLoop() {
        controller.stopLoop()
    }

    override fun measureDelay(url: String): Long = controller.measureDelay(url)

    override fun queryAllOutboundTrafficStats(): String =
        controller.queryAllOutboundTrafficStats()

    override fun registerProcessFinder(finder: ProxyProcessFinder?) {
        controller.registerProcessFinder(finder?.let { XrayProcessFinderAdapter(it) })
    }
}

/** Adapts a [ProxyCoreCallback] to xray's `CoreCallbackHandler`. */
internal class XrayCoreCallbackAdapter(
    private val callback: ProxyCoreCallback
) : CoreCallbackHandler {
    override fun startup(): Long = callback.onStartup()
    override fun shutdown(): Long = callback.onShutdown()
    override fun onEmitStatus(l: Long, s: String?): Long = callback.onEmitStatus(l, s)
}

/** Adapts a [ProxyProcessFinder] to xray's `ProcessFinder`. */
internal class XrayProcessFinderAdapter(
    private val finder: ProxyProcessFinder
) : ProcessFinder {
    override fun findProcessByConnection(
        network: String,
        srcIP: String,
        srcPort: Long,
        destIP: String,
        destPort: Long
    ): Long = finder.findProcessByConnection(network, srcIP, srcPort, destIP, destPort)
}
