package dev.sadr.atlas.service

import android.content.Context
import dev.sadr.atlas.core.CoreConfigManager
import dev.sadr.atlas.core.CoreNativeManager
import dev.sadr.atlas.dto.RealPingEvent
import dev.sadr.atlas.enums.EConfigType
import dev.sadr.atlas.extension.isComplexType
import dev.sadr.atlas.extension.isNotNullEmpty
import dev.sadr.atlas.handler.MmkvManager
import dev.sadr.atlas.handler.SettingsManager
import dev.sadr.atlas.handler.SpeedtestManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Worker that runs a batch of real-ping tests independently.
 * Each batch owns its own CoroutineScope/dispatcher and can be cancelled separately.
 */
class RealPingWorkerService(
    private val context: Context,
    private val guids: List<String>,
    private val onEvent: (RealPingEvent) -> Unit = {}
) {
    private val job = SupervisorJob()
    private val concurrency = SettingsManager.getRealPingConcurrency()
    private val dispatcher = Executors.newFixedThreadPool(concurrency).asCoroutineDispatcher()
    private val scope = CoroutineScope(job + dispatcher + CoroutineName("RealPingBatchWorker"))

    private val runningCount = AtomicInteger(0)
    private val totalCount = AtomicInteger(0)

    fun start() {
        // Max safe concurrency is 89 to avoid port range overlap with AutoBestConfigManager (10810+)
        val jobs = guids.mapIndexed { index, guid ->
            totalCount.incrementAndGet()
            scope.launch {
                runningCount.incrementAndGet()
                try {
                    val workerId = index % concurrency
                    val result = SpeedtestManager.startRealPing(context, guid, workerId)
                    onEvent(RealPingEvent.Result(guid, result))
                } catch (_: Throwable) {
                    // ignore
                } finally {
                    val count = totalCount.decrementAndGet()
                    val left = runningCount.decrementAndGet()
                    onEvent(RealPingEvent.Progress("$left / $count"))
                }
            }
        }

        scope.launch {
            try {
                joinAll(*jobs.toTypedArray())
                onEvent(RealPingEvent.Finish("0"))
            } catch (_: CancellationException) {
                onEvent(RealPingEvent.Finish("-1"))
            } finally {
                close()
            }
        }
    }

    fun cancel() {
        job.cancel()
    }

    private fun close() {
        try {
            dispatcher.close()
        } catch (_: Throwable) {
            // ignore
        }
    }
}
