package dev.sadr.atlas.handler

import android.content.Context
import android.os.Build
import dev.sadr.atlas.AppConfig
import dev.sadr.atlas.core.CoreConfigManager
import dev.sadr.atlas.core.ProxyConfigManager
import dev.sadr.atlas.core.CoreNativeManager
import dev.sadr.atlas.dto.LastWorkingConfig
import dev.sadr.atlas.dto.ParseStats
import dev.sadr.atlas.dto.UrlContentRequest
import dev.sadr.atlas.dto.entities.ProfileItem
import dev.sadr.atlas.util.HttpUtil
import dev.sadr.atlas.util.JsonUtil
import dev.sadr.atlas.util.LogUtil
import dev.sadr.atlas.extension.isNotNullEmpty
import dev.sadr.atlas.util.Utils
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds

object AutoBestConfigManager {

    private var testingJob: Job? = null
    private const val TARGET_COUNT = 3
    private val SUB_ID = AppConfig.AUTO_BEST_SUBSCRIPTION_ID

    private val tcpCache = ConcurrentHashMap<String, Pair<Boolean, Long>>()
    private const val CACHE_EXPIRY_MS = 10 * 60 * 1000L // 10 minutes

    private fun isEmulator(): Boolean {
        return Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
                || "google_sdk" == Build.PRODUCT
    }

    private fun isLowMemory(): Boolean {
        val runtime = Runtime.getRuntime()
        val freeMemMB = (runtime.maxMemory() - runtime.totalMemory() + runtime.freeMemory()) / 1024 / 1024
        return freeMemMB < 60 // 60MB threshold
    }

    private fun getLastWorkingConfig(): LastWorkingConfig? {
        val json = MmkvManager.decodeSettingsString(AppConfig.PREF_LAST_WORKING_CONFIG) ?: return null
        return JsonUtil.fromJsonSafe(json, LastWorkingConfig::class.java)
    }

    private fun saveLastWorkingConfig(config: LastWorkingConfig) {
        MmkvManager.encodeSettings(AppConfig.PREF_LAST_WORKING_CONFIG, JsonUtil.toJson(config))
    }

    private fun clearLastWorkingConfig() {
        MmkvManager.removeSettings(AppConfig.PREF_LAST_WORKING_CONFIG)
    }

    fun start(
        context: Context,
        onProgress: (String) -> Unit,
        onComplete: (List<String>) -> Unit
    ) {
        // Prune stale TCP cache entries before each run
        val pruneNow = System.currentTimeMillis()
        tcpCache.entries.removeIf { pruneNow - it.value.second > CACHE_EXPIRY_MS }
        if (tcpCache.size > 10000) {
            tcpCache.clear()
        }
        SpeedtestManager.pruneDnsCache()

        testingJob?.cancel()
        testingJob = CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val stats = ParseStats()
            try {
                // STARTUP LOGIC: Check last working config
                val lastWorking = getLastWorkingConfig()
                if (lastWorking != null) {
                    // Source of truth is MMKV; if the user deleted the profile, don't
                    // resurrect it from the cache — fall through to full discovery.
                    val currentProfile = MmkvManager.decodeServerConfig(lastWorking.profile.guid)
                    if (currentProfile == null) {
                        clearLastWorkingConfig()
                    } else {
                        onProgress("Verifying last connection...")
                        val tcpTime = withContext(SpeedtestManager.probeDispatcher) {
                            SpeedtestManager.socketConnectTime(
                                currentProfile.server ?: lastWorking.host,
                                currentProfile.serverPort?.toIntOrNull() ?: lastWorking.port,
                                2000
                            )
                        }
                        if (tcpTime > 0) {
                            onProgress("Connected (Fast-track)")
                            lastWorking.failCount = 0
                            // Update cache with potentially edited profile
                            saveLastWorkingConfig(lastWorking.copy(profile = currentProfile))

                            withContext(Dispatchers.Main) {
                                onComplete(listOf(currentProfile.guid))
                            }
                            // stop discovery loop
                            testingJob?.cancel()
                            return@launch
                        } else {
                            lastWorking.failCount++
                            if (lastWorking.failCount >= 3) {
                                clearLastWorkingConfig()
                            } else {
                                saveLastWorkingConfig(lastWorking)
                            }
                        }
                    }
                }

                // FULL DISCOVERY FLOW
                // NOTE: we do NOT wipe the pool here. The old "last-known-good"
                // list is kept until a fresh, non-empty fetch succeeds, so a
                // blocked broker endpoint never empties the Free tab.
                onProgress("Refreshing server list...")

                if (isEmulator()) {
                    onProgress("Emulator detected: results may differ")
                    delay(1000.milliseconds)
                }

                onProgress("Fetching configs...")
                val allLines = fetchFreePoolWithFallback(onProgress)

                if (allLines.isEmpty()) {
                    // Fetch failed / all domains blocked: keep the existing pool.
                    val existing = MmkvManager.decodeServerList(SUB_ID)
                    if (existing.isNotEmpty()) {
                        onProgress("Offline: showing last saved servers")
                    } else {
                        onProgress("No configs found or network error")
                    }
                    withContext(Dispatchers.Main) {
                        onComplete(existing)
                    }
                    return@launch
                }

                // Fresh list in hand — now it is safe to replace the pool.
                MmkvManager.removeServerViaSubid(SUB_ID)

                stats.total = allLines.size
                onProgress("Fetched ${allLines.size} configs. Deduping...")
                
                val subItem = MmkvManager.decodeSubscription(SUB_ID)
                val uniqueConfigs = ConcurrentHashMap<String, ProfileItem>()
                
                // Deduplicate and blacklist check
                allLines.forEach { line ->
                    val profile = AngConfigManager.parseConfig(line, SUB_ID, subItem)
                    if (profile == null) {
                        stats.skippedMalformed++
                        return@forEach
                    }
                    if (profile.server.isNotNullEmpty() && profile.serverPort.isNotNullEmpty()) {
                        val key = "${profile.server}:${profile.serverPort}"
                        if (MmkvManager.isBlacklisted(key)) {
                            return@forEach
                        }
                        if (uniqueConfigs.putIfAbsent(key, profile) != null) {
                            stats.skippedDuplicate++
                        }
                    } else {
                        stats.skippedMalformed++
                    }
                }
                
                val candidateList = uniqueConfigs.values.toList().shuffled()
                val totalCandidates = candidateList.size
                onProgress("Unique: $totalCandidates. Starting pipeline...")

                val tcpPassChannel = Channel<ProfileItem>(capacity = 100)
                val resultChannel = Channel<Pair<ProfileItem, Long>>(capacity = Channel.UNLIMITED)
                val tcpCheckedCount = AtomicInteger(0)
                val pingTestingCount = AtomicInteger(0)
                
                val lowMemory = isLowMemory()
                if (lowMemory) {
                    onProgress("Low memory: reducing workers")
                    delay(1000.milliseconds)
                }

                // Phase 1 & 2 Producer
                val producerJob = launch {
                    // Concurrency of the blocking TCP/TLS pre-filter. Kept in step with
                    // SpeedtestManager.probeDispatcher's parallelism (30); the downstream
                    // ping phase only runs 3 native cores, so a higher fan-out here just
                    // spawns idle blocking threads without speeding up discovery.
                    val tcpSemaphore = Semaphore(if (lowMemory) 15 else 30)
                    val now = System.currentTimeMillis()
                    candidateList.map { profile ->
                        launch {
                            if (!isActive) return@launch
                            tcpSemaphore.withPermit {
                                try {
                                    val isTls = profile.security == AppConfig.TLS || profile.security == AppConfig.REALITY || profile.serverPort == "443"
                                    val cacheKey = if (isTls) {
                                        "${profile.server}:${profile.serverPort}:${profile.sni ?: ""}"
                                    } else {
                                        "${profile.server}:${profile.serverPort}"
                                    }

                                    val cached = tcpCache[cacheKey]
                                    if (cached != null && (now - cached.second) < CACHE_EXPIRY_MS) {
                                        if (cached.first) {
                                            tcpPassChannel.send(profile)
                                        } else {
                                            stats.skippedTcpFail++
                                        }
                                        return@withPermit
                                    }

                                    // Resolve DNS once and reuse for both the TCP and TLS
                                    // probes. A host that can't be resolved (or times out) is
                                    // treated as a TCP failure without wasting a connect attempt.
                                    val resolved = SpeedtestManager.resolveHost(profile.server!!)
                                    if (resolved == null) {
                                        tcpCache[cacheKey] = false to now
                                        stats.skippedTcpFail++
                                        return@withPermit
                                    }

                                    val port = profile.serverPort!!.toInt()
                                    val tcpTime = withContext(SpeedtestManager.probeDispatcher) {
                                        SpeedtestManager.socketConnectTime(resolved, port, 1500)
                                    }
                                    val tcpSuccess = tcpTime > 0
                                    var preFilterPassed = tcpSuccess

                                    if (tcpSuccess && isTls) {
                                        preFilterPassed = SpeedtestManager.tlsHandshakeCheck(
                                            resolved,
                                            port,
                                            profile.sni ?: profile.server!!
                                        )
                                    }

                                    tcpCache[cacheKey] = preFilterPassed to now

                                    if (preFilterPassed) {
                                        tcpPassChannel.send(profile)
                                    } else {
                                        stats.skippedTcpFail++
                                    }
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (_: Exception) {
                                    stats.skippedTcpFail++
                                } finally {
                                    val checked = tcpCheckedCount.incrementAndGet()
                                    if (checked % 100 == 0 || checked == totalCandidates) {
                                        updateProgress(onProgress, checked, totalCandidates, pingTestingCount)
                                    }
                                }
                            }
                        }
                    }.joinAll()
                    tcpPassChannel.close()
                }

                // Consumers
                val consumerCount = if (lowMemory) 1 else 3
                val lastIncrementalUpdate = java.util.concurrent.atomic.AtomicLong(0L)
                val consumerJobs = (0 until consumerCount).map { workerId ->
                    launch {
                        for (profile in tcpPassChannel) {
                            pingTestingCount.incrementAndGet()
                            updateProgress(onProgress, tcpCheckedCount.get(), totalCandidates, pingTestingCount)
                            
                            try {
                                profile.insecure = true
                                if (profile.fingerPrint.isNullOrBlank()) profile.fingerPrint = "chrome"

                                val configResult = ProxyConfigManager.getSpeedtestConfig(context, profile, 10810 + workerId)
                                if (!configResult.status) {
                                    continue
                                }

                                val delay = CoreNativeManager.measureOutboundDelay(
                                    configResult.content,
                                    "http://cp.cloudflare.com/generate_204",
                                    workerId
                                )
                                if (delay == -2L) {
                                    MmkvManager.blacklistConfig("${profile.server}:${profile.serverPort}")
                                } else if (delay > 0) {
                                    // SUCCESS: latency < 5000 (standard 2xx/3xx)
                                    // PARTIAL: latency == 5000 (4xx status)
                                    
                                    // Assign a stable GUID to avoid duplicates
                                    if (profile.guid.isEmpty()) {
                                        profile.guid = Utils.getUuid()
                                    }
                                    MmkvManager.encodeServerConfig(profile.guid, profile)
                                    MmkvManager.encodeServerTestDelayMillis(profile.guid, delay)
                                    
                                    val now = System.currentTimeMillis()
                                    val last = lastIncrementalUpdate.get()
                                    if (now - last > 500 && lastIncrementalUpdate.compareAndSet(last, now)) {
                                        withContext(Dispatchers.Main) {
                                            onComplete(emptyList()) // trigger incremental reload
                                        }
                                    }
                                    resultChannel.send(profile to delay)
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Exception) {
                            } finally {
                                pingTestingCount.decrementAndGet()
                                updateProgress(onProgress, tcpCheckedCount.get(), totalCandidates, pingTestingCount)
                            }
                        }
                    }
                }

                val bestResults = mutableListOf<Pair<ProfileItem, Long>>()

                // Collector: gather up to TOP_N fast (<3000ms) results before
                // exiting, so the winner can be chosen RANDOMLY among the top-N
                // rather than always the single #1 (de-concentrates user load). If
                // fewer than TOP_N fast configs exist, the channel is exhausted and
                // closed by the waiter below.
                val fastResults = AtomicInteger(0)
                val collectorJob = launch {
                    try {
                        for (result in resultChannel) {
                            bestResults.add(result)
                            if (result.second < 3000 && fastResults.incrementAndGet() >= AppConfig.AUTO_BEST_TOP_N) {
                                resultChannel.close()
                                break
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {}
                }

                // Wait until enough results or everything exhausted
                val waiterJob = launch {
                    try {
                        producerJob.join()
                        consumerJobs.joinAll()
                    } catch (_: CancellationException) {
                    } finally {
                        resultChannel.close()
                    }
                }

                collectorJob.join()
                
                // Once collector is done (found winner or exhausted), stop all workers
                producerJob.cancel()
                consumerJobs.forEach { it.cancel() }
                waiterJob.cancel()

                // Final cleanup of test cores
                CoreNativeManager.stopTestControllers()
                
                if (bestResults.isNotEmpty()) {
                    val finalBest = bestResults.sortedBy { it.second }

                    // Pick the winner RANDOMLY among the top-N lowest-latency
                    // configs (not always #1) so Atlas users spread across servers
                    // instead of all piling onto the single fastest one.
                    val topN = finalBest.take(minOf(AppConfig.AUTO_BEST_TOP_N, finalBest.size))
                    val (winner, latency) = topN.random()

                    stats.success = bestResults.size
                    LogUtil.i(AppConfig.TAG, stats.getSummary())

                    saveLastWorkingConfig(LastWorkingConfig(
                        profile = winner,
                        host = winner.server!!,
                        port = winner.serverPort!!.toInt(),
                        lastVerified = System.currentTimeMillis(),
                        latencyMs = latency
                    ))

                    onProgress("Connected")

                    // Return the randomly chosen winner FIRST (the caller connects
                    // bestGuids.first()), followed by the other top configs.
                    val finalGuids = (listOf(winner.guid) + finalBest.take(TARGET_COUNT).map { it.first.guid })
                        .distinct()
                        .take(TARGET_COUNT)
                    withContext(Dispatchers.Main) {
                        onComplete(finalGuids)
                    }
                } else {
                    onProgress("No responsive configs found")
                    LogUtil.i(AppConfig.TAG, stats.getSummary())
                    withContext(Dispatchers.Main) {
                        onComplete(emptyList())
                    }
                }

            } catch (e: CancellationException) {
                // rethrow to stop scope
                throw e
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "AutoBestConfig failed", e)
                onProgress("Error: ${e.message}")
            }
        }
    }

    /**
     * Fetches the Free pool from the broker, trying each domain in
     * [AppConfig.FREE_BROKER_DOMAINS] in order until one returns usable configs.
     * The broker serves the v2rayNG subscription format (base64 of newline-joined
     * URIs); the legacy public source is plaintext — so we try base64-decoding the
     * body and fall back to raw. Returns an empty list only if every domain fails,
     * which the caller treats as "keep the last-known-good pool".
     */
    private fun fetchFreePoolWithFallback(onProgress: (String) -> Unit): List<String> {
        // Settings override (if any) is tried first, then the built-in defaults.
        val custom = MmkvManager.decodeSettingsString(AppConfig.PREF_FREE_BROKER_URL)?.trim()
        val urls = if (!custom.isNullOrBlank()) {
            listOf(custom) + AppConfig.FREE_BROKER_DOMAINS.filter { it != custom }
        } else {
            AppConfig.FREE_BROKER_DOMAINS
        }

        for ((index, url) in urls.withIndex()) {
            val body = try {
                HttpUtil.getUrlContentWithUserAgent(UrlContentRequest(url = url, timeout = 20000))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            if (body.isNullOrBlank()) continue

            val text = Utils.tryDecodeBase64(body.trim()) ?: body
            val lines = text.lineSequence()
                .map { it.trim() }
                .filter { it.isNotBlank() && it.contains("://") }
                .take(25000)
                .toList()

            if (lines.isNotEmpty()) {
                if (index > 0) onProgress("Using fallback source ${index + 1}")
                return lines
            }
        }
        return emptyList()
    }

    private fun updateProgress(
        onProgress: (String) -> Unit,
        checked: Int,
        total: Int,
        testing: AtomicInteger
    ) {
        onProgress("TCP: $checked/$total | Testing: ${testing.get()}")
    }

    fun stop() {
        testingJob?.cancel()
        testingJob = null
        CoroutineScope(Dispatchers.IO).launch {
            try {
                CoreNativeManager.stopTestControllers()
            } catch (_: Exception) {}
        }
    }
}
