package com.v2ray.ang.viewmodel

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.AssetManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.v2ray.ang.AngApplication
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.core.CoreServiceManager
import com.v2ray.ang.dto.GroupMapItem
import com.v2ray.ang.dto.SubscriptionUpdateResult
import com.v2ray.ang.dto.TestServiceMessage
import com.v2ray.ang.dto.entities.ServersCache
import com.v2ray.ang.dto.entities.SubscriptionCache
import com.v2ray.ang.extension.isComplexType
import com.v2ray.ang.extension.matchesPattern
import com.v2ray.ang.extension.toSpeedString
import com.v2ray.ang.extension.toastError
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.AutoBestConfigManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.MessageUtil
import com.v2ray.ang.util.Utils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Collections
import java.util.regex.PatternSyntaxException
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    application: Application,
    private val mmkvManager: MmkvManager,
    private val settingsManager: SettingsManager,
    private val angConfigManager: AngConfigManager,
    private val autoBestConfigManager: AutoBestConfigManager,
    private val coreServiceManager: CoreServiceManager
) : AndroidViewModel(application) {
    private var serverList = mutableListOf<String>()
    
    private val _subscriptionIdFlow = MutableStateFlow(mmkvManager.decodeSettingsString(AppConfig.CACHE_SUBSCRIPTION_ID, "").orEmpty())
    val subscriptionIdFlow = _subscriptionIdFlow.asStateFlow()
    
    var subscriptionId: String
        get() = _subscriptionIdFlow.value
        set(value) {
            _subscriptionIdFlow.value = value
            mmkvManager.encodeSettings(AppConfig.CACHE_SUBSCRIPTION_ID, value)
        }

    var keywordFilter = ""
    val serversCache = mutableListOf<ServersCache>()
    private val _isRunningFlow = MutableStateFlow(false)
    val isRunningFlow = _isRunningFlow.asStateFlow()

    private val _updateTestResultAction = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val updateTestResultAction: SharedFlow<String> = _updateTestResultAction.asSharedFlow()

    private val _updateGroupsAction = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val updateGroupsAction: SharedFlow<Unit> = _updateGroupsAction.asSharedFlow()

    private val _startServiceAction = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val startServiceAction: SharedFlow<Unit> = _startServiceAction.asSharedFlow()

    private val _isLoadingFlow = MutableStateFlow(false)
    val isLoadingFlow = _isLoadingFlow.asStateFlow()

    private val _autoBestProgressFlow = MutableStateFlow("")
    val autoBestProgressFlow = _autoBestProgressFlow.asStateFlow()

    fun setLoading(loading: Boolean) {
        _isLoadingFlow.value = loading
    }

    fun startAutoBestConfig() {
        _isLoadingFlow.value = true
        // Clear filter immediately to show fresh progress
        subscriptionId = AppConfig.AUTO_BEST_SUBSCRIPTION_ID
        keywordFilter = ""
        reloadServerList()
        _updateGroupsAction.tryEmit(Unit)

        autoBestConfigManager.start(
            getApplication<Application>(),
            onProgress = { progress: String ->
                _autoBestProgressFlow.value = progress
            },
            onComplete = { bestGuids: List<String> ->
                if (bestGuids.isEmpty()) {
                    // Incremental update signal
                    reloadServerList()
                    return@start
                }
                
                _isLoadingFlow.value = false
                _autoBestProgressFlow.value = ""
                
                // 1. Select the best one in MMKV first
                mmkvManager.setSelectServer(bestGuids.first())
                
                // 2. Reload list and cache, then trigger actions
                reloadServerList {
                    _updateGroupsAction.tryEmit(Unit)
                    updateSelectedGuid()
                    _startServiceAction.tryEmit(Unit)
                }
            }
        )
    }

    private val _serversCacheFlow = MutableStateFlow<List<ServersCache>>(emptyList())
    val serversCacheFlow = _serversCacheFlow.asStateFlow()

    private val _selectedGuidFlow = MutableStateFlow(mmkvManager.getSelectServer() ?: "")
    val selectedGuidFlow = _selectedGuidFlow.asStateFlow()

    private val _testResultsFlow = MutableStateFlow<Map<String, String>>(emptyMap())
    val testResultsFlow = _testResultsFlow.asStateFlow()

    private val _upSpeedFlow = MutableStateFlow("0 B/s")
    val upSpeedFlow = _upSpeedFlow.asStateFlow()

    private val _downSpeedFlow = MutableStateFlow("0 B/s")
    val downSpeedFlow = _downSpeedFlow.asStateFlow()

    private var lastTrafficStats: List<com.v2ray.ang.dto.OutboundTrafficStat> = emptyList()
    private var lastTrafficTime = 0L

    fun updateTrafficStats(stats: List<com.v2ray.ang.dto.OutboundTrafficStat>) {
        val now = System.currentTimeMillis()
        if (lastTrafficTime > 0) {
            val duration = (now - lastTrafficTime) / 1000.0
            if (duration > 0) {
                var up = 0L
                var down = 0L
                stats.forEach { stat ->
                    if (stat.direction == AppConfig.UPLINK) up += stat.value
                    if (stat.direction == AppConfig.DOWNLINK) down += stat.value
                }
                _upSpeedFlow.value = (up / duration).toLong().toSpeedString()
                _downSpeedFlow.value = (down / duration).toLong().toSpeedString()
            }
        }
        lastTrafficStats = stats
        lastTrafficTime = now
    }

    fun isTunnelHealthy(): Boolean {
        // Only judge if we have relatively fresh stats
        val now = System.currentTimeMillis()
        if (now - lastTrafficTime > 20000) return true // Stats too old, assume OK
        
        var totalUp = 0L
        var totalDown = 0L
        lastTrafficStats.forEach { 
            if (it.direction == AppConfig.UPLINK) totalUp += it.value 
            if (it.direction == AppConfig.DOWNLINK) totalDown += it.value 
        }
        
        // Stalled: Sent data but received absolutely nothing back
        if (totalUp > 100 && totalDown == 0L) {
            LogUtil.d(AppConfig.TAG, "Tunnel appears stalled: Up=$totalUp, Down=$totalDown")
            return false
        }
        
        // Idle (both 0) or active (down > 0) are considered healthy
        return true
    }

    fun updateSelectedGuid() {
        _selectedGuidFlow.value = mmkvManager.getSelectServer() ?: ""
    }

    fun updateTestResults() {
        val results = mutableMapOf<String, String>()
        val currentCache = synchronized(this) { serversCache.toList() }
        currentCache.forEach {
            val aff = mmkvManager.decodeServerAffiliationInfo(it.guid)
            results[it.guid] = aff?.getTestDelayString().orEmpty()
        }
        _testResultsFlow.value = results
    }

    private fun updateTestResult(guid: String) {
        val aff = mmkvManager.decodeServerAffiliationInfo(guid)
        val currentResults = _testResultsFlow.value.toMutableMap()
        currentResults[guid] = aff?.getTestDelayString().orEmpty()
        _testResultsFlow.value = currentResults
    }

    /**
     * Refer to the official documentation for [registerReceiver](https://developer.android.com/reference/androidx/core/content/ContextCompat#registerReceiver(android.content.Context,android.content.BroadcastReceiver,android.content.IntentFilter,int):
     * `registerReceiver(Context, BroadcastReceiver, IntentFilter, int)`.
     */
    fun startListenBroadcast() {
        val mFilter = IntentFilter(AppConfig.BROADCAST_ACTION_ACTIVITY)
        ContextCompat.registerReceiver(getApplication(), mMsgReceiver, mFilter, Utils.receiverFlags())
        MessageUtil.sendMsg2Service(getApplication(), AppConfig.MSG_REGISTER_CLIENT, "")
    }

    /**
     * Called when the ViewModel is cleared.
     */
    override fun onCleared() {
        getApplication<Application>().unregisterReceiver(mMsgReceiver)
        LogUtil.i(AppConfig.TAG, "Main ViewModel is cleared")
        super.onCleared()
    }

    private var reloadJob: Job? = null

    /**
     * Reloads the server list based on current subscription filter.
     */
    fun reloadServerList(onComplete: (() -> Unit)? = null) {
        reloadJob?.cancel()
        reloadJob = viewModelScope.launch(Dispatchers.IO) {
            val list = if (subscriptionId.isEmpty()) {
                mmkvManager.decodeAllServerList()
            } else {
                mmkvManager.decodeServerList(subscriptionId)
            }

            synchronized(this@MainViewModel) {
                serverList = list
                updateCache()
            }

            withContext(Dispatchers.Main) {
                onComplete?.invoke()
            }
        }
    }

    /**
     * Removes a server by its GUID.
     * @param guid The GUID of the server to remove.
     */
    fun removeServer(guid: String) {
        serverList.remove(guid)
        mmkvManager.removeServer(guid)
        val index = getPosition(guid)
        if (index >= 0) {
            serversCache.removeAt(index)
            _serversCacheFlow.value = serversCache.toList()
        }
    }

    /**
     * Swaps the positions of two servers.
     * @param fromPosition The initial position of the server.
     * @param toPosition The target position of the server.
     */
    fun swapServer(fromPosition: Int, toPosition: Int) {
        if (subscriptionId.isEmpty()) {
            return
        }

        Collections.swap(serverList, fromPosition, toPosition)
        Collections.swap(serversCache, fromPosition, toPosition)
        _serversCacheFlow.value = serversCache.toList()

        mmkvManager.encodeServerList(serverList, subscriptionId)
    }

    /**
     * Updates the cache of servers.
     */
    @Synchronized
    fun updateCache() {
        serversCache.clear()
        val kw = keywordFilter.trim()
        val searchRegex = try {
            if (kw.isNotEmpty()) Regex(kw, setOf(RegexOption.IGNORE_CASE)) else null
        } catch (e: PatternSyntaxException) {
            null // Fallback to literal search if regex is invalid
        }
        for (guid in serverList) {
            val profile = mmkvManager.decodeServerConfig(guid) ?: continue
            if (kw.isEmpty()) {
                serversCache.add(ServersCache(guid, profile))
                continue
            }

            val remarks = profile.remarks
            val description = profile.description.orEmpty()
            val server = profile.server.orEmpty()
            val protocol = profile.configType.name
            if (remarks.matchesPattern(searchRegex, kw)
                || description.matchesPattern(searchRegex, kw)
                || server.matchesPattern(searchRegex, kw)
                || protocol.matchesPattern(searchRegex, kw)
            ) {
                serversCache.add(ServersCache(guid, profile))
            }
        }
        _serversCacheFlow.value = serversCache.toList()
        updateSelectedGuid()
        updateTestResults()
    }

    /**
     * Updates the configuration via subscription for all servers.
     * @return Detailed result of the subscription update operation.
     */
    fun updateConfigViaSubAll(): SubscriptionUpdateResult {
        if (subscriptionId.isEmpty()) {
            return angConfigManager.updateConfigViaSubAll()
        } else {
            val subItem = mmkvManager.decodeSubscription(subscriptionId) ?: return SubscriptionUpdateResult()
            return angConfigManager.updateConfigViaSub(SubscriptionCache(subscriptionId, subItem))
        }
    }

    /**
     * Exports all servers.
     * @return The number of exported servers.
     */
    fun exportAllServer(): Int {
        val serverListCopy = synchronized(this) {
            if (subscriptionId.isEmpty() && keywordFilter.isEmpty()) {
                serverList.toList()
            } else {
                serversCache.map { it.guid }.toList()
            }
        }

        val ret = angConfigManager.shareNonCustomConfigsToClipboard(
            getApplication<AngApplication>(),
            serverListCopy
        )
        return ret
    }

    /**
     * Tests the real ping for all servers.
     */
    fun testAllRealPing() {
        MessageUtil.sendMsg2TestService(
            getApplication(),
            TestServiceMessage(key = AppConfig.MSG_MEASURE_CONFIG_CANCEL)
        )
        val guids = synchronized(this) { serversCache.map { it.guid }.toList() }
        mmkvManager.clearAllTestDelayResults(guids)
        updateTestResults()

        viewModelScope.launch(Dispatchers.Default) {
            if (guids.isEmpty()) {
                return@launch
            }
            MessageUtil.sendMsg2TestService(
                getApplication(),
                TestServiceMessage(
                    key = AppConfig.MSG_MEASURE_CONFIG_START,
                    subscriptionId = subscriptionId,
                    serverGuids = if (keywordFilter.isNotEmpty()) guids else emptyList()
                )
            )
        }
    }

    /**
     * Tests the real ping for the current server.
     */
    fun testCurrentServerRealPing() {
        MessageUtil.sendMsg2Service(getApplication(), AppConfig.MSG_MEASURE_DELAY, "")
    }

    /**
     * Changes the subscription ID.
     * @param id The new subscription ID.
     */
    fun subscriptionIdChanged(id: String) {
        if (subscriptionId != id) {
            subscriptionId = id
            mmkvManager.encodeSettings(AppConfig.CACHE_SUBSCRIPTION_ID, subscriptionId)
        }
        reloadServerList()
    }

    /**
     * Gets the subscriptions.
     * @param context The context.
     * @return A pair of lists containing the subscription IDs and remarks.
     */
    fun getSubscriptions(context: Context): List<GroupMapItem> {
        val subscriptions = mmkvManager.decodeSubscriptions()
        if (subscriptionId.isNotEmpty()
            && subscriptionId != AppConfig.AUTO_BEST_SUBSCRIPTION_ID
            && !subscriptions.map { it.guid }.contains(subscriptionId)
        ) {
            subscriptionIdChanged("")
        }

        val groups = mutableListOf<GroupMapItem>()
        if (mmkvManager.decodeSettingsBool(AppConfig.PREF_GROUP_ALL_DISPLAY)) {
            groups.add(
                GroupMapItem(
                    id = "",
                    remarks = context.getString(R.string.filter_config_all)
                )
            )
        }

        // Add Auto-Best group if it has items or is currently selected
        val autoBestCount = mmkvManager.decodeServerList(AppConfig.AUTO_BEST_SUBSCRIPTION_ID).size
        if (autoBestCount > 0 || subscriptionId == AppConfig.AUTO_BEST_SUBSCRIPTION_ID) {
            groups.add(
                GroupMapItem(
                    id = AppConfig.AUTO_BEST_SUBSCRIPTION_ID,
                    remarks = "Auto Best Discovery"
                )
            )
        }

        subscriptions.forEach { sub ->
            groups.add(
                GroupMapItem(
                    id = sub.guid,
                    remarks = sub.subscription.remarks
                )
            )
        }
        return groups
    }

    /**
     * Gets the position of a server by its GUID.
     * @param guid The GUID of the server.
     * @return The position of the server.
     */
    fun getPosition(guid: String): Int = synchronized(this) {
        serversCache.forEachIndexed { index, it ->
            if (it.guid == guid)
                return index
        }
        -1
    }

    /**
     * Removes duplicate servers.
     * Excludes servers with complex types (Custom, PolicyGroup, or ProxyChain) from duplicate comparison.
     * @return The number of removed servers.
     */
    fun removeDuplicateServer(): Int {
        val serversCacheCopy = synchronized(this) { serversCache.toList() }
        val deleteServer = mutableListOf<String>()

        serversCacheCopy.forEachIndexed { index, sc ->
            val profile = sc.profile
            // Skip if this profile has a complex config type
            if (profile.configType.isComplexType()) {
                return@forEachIndexed
            }

            serversCacheCopy.forEachIndexed { index2, sc2 ->
                if (index2 > index) {
                    val profile2 = sc2.profile
                    // Skip if the second profile has a complex config type
                    if (profile2.configType.isComplexType()) {
                        return@forEachIndexed
                    }

                    if (profile.isSameConfig(profile2) && !deleteServer.contains(sc2.guid)) {
                        deleteServer.add(sc2.guid)
                    }
                }
            }
        }
        for (it in deleteServer) {
            mmkvManager.removeServer(it)
        }

        return deleteServer.count()
    }

    /**
     * Removes all servers.
     * @return The number of removed servers.
     */
    fun removeAllServer(): Int {
        val count =
            if (subscriptionId.isEmpty() && keywordFilter.isEmpty()) {
                mmkvManager.removeAllServer()
            } else {
                val serversCopy = synchronized(this) { serversCache.toList() }
                for (item in serversCopy) {
                    mmkvManager.removeServer(item.guid)
                }
                serversCopy.count()
            }
        _updateGroupsAction.tryEmit(Unit)
        return count
    }

    /**
     * Removes invalid servers.
     * @return The number of removed servers.
     */
    fun removeInvalidServer(): Int {
        var count = 0
        if (subscriptionId.isEmpty() && keywordFilter.isEmpty()) {
            count += mmkvManager.removeInvalidServer("")
        } else {
            val serversCopy = synchronized(this) { serversCache.toList() }
            for (item in serversCopy) {
                count += mmkvManager.removeInvalidServer(item.guid)
            }
        }
        return count
    }

    /**
     * Sorts servers by their test results.
     */
    fun sortByTestResults() {
        if (subscriptionId.isEmpty()) {
            mmkvManager.decodeSubsList().forEach { guid ->
                sortByTestResultsForSub(guid)
            }
        } else {
            sortByTestResultsForSub(subscriptionId)
        }
    }

    /**
     * Sorts servers by their test results for a specific subscription.
     * @param subId The subscription ID to sort servers for.
     */
    private fun sortByTestResultsForSub(subId: String) {
        data class ServerDelay(var guid: String, var testDelayMillis: Long)

        val serverDelays = mutableListOf<ServerDelay>()
        val serverListToSort = mmkvManager.decodeServerList(subId)

        serverListToSort.forEach { key ->
            val delay = mmkvManager.decodeServerAffiliationInfo(key)?.testDelayMillis ?: 0L
            serverDelays.add(ServerDelay(key, if (delay <= 0L) 999999 else delay))
        }
        serverDelays.sortBy { it.testDelayMillis }

        val sortedServerList = serverDelays.map { it.guid }.toMutableList()

        // Save the sorted list for this subscription
        mmkvManager.encodeServerList(sortedServerList, subId)
    }


    /**
     * Initializes assets.
     * @param assets The asset manager.
     */
    fun initAssets(assets: AssetManager) {
        viewModelScope.launch(Dispatchers.Default) {
            settingsManager.initAssets(getApplication<AngApplication>(), assets)
        }
    }

    /**
     * Filters the configuration by a keyword.
     * @param keyword The keyword to filter by.
     */
    fun filterConfig(keyword: String) {
        if (keyword == keywordFilter) {
            return
        }
        keywordFilter = keyword
        reloadServerList()
    }

    fun findSubscriptionIdBySelect(): String? {
        // Get the selected server GUID
        val selectedGuid = mmkvManager.getSelectServer()
        if (selectedGuid.isNullOrEmpty()) {
            return null
        }

        val config = mmkvManager.decodeServerConfig(selectedGuid)
        return config?.subscriptionId
    }

    fun onTestsFinished() {
        viewModelScope.launch(Dispatchers.Default) {
            if (mmkvManager.decodeSettingsBool(AppConfig.PREF_AUTO_REMOVE_INVALID_AFTER_TEST)) {
                removeInvalidServer()
            }

            if (mmkvManager.decodeSettingsBool(AppConfig.PREF_AUTO_SORT_AFTER_TEST)) {
                sortByTestResults()
            }

            withContext(Dispatchers.Main) {
                reloadServerList()
            }
        }
    }

    private val mMsgReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            val key = intent?.getIntExtra("key", 0) ?: return
            val content = intent.getStringExtra("content")
            viewModelScope.launch(Dispatchers.Main) {
                when (key) {
                    AppConfig.MSG_STATE_RUNNING -> {
                        _isRunningFlow.value = true
                        _isLoadingFlow.value = false
                        if (intent.getStringExtra("msg") == "TRAFFIC_UPDATE") {
                            updateTrafficStats(coreServiceManager.queryAllOutboundTrafficStats())
                        }
                    }

                    AppConfig.MSG_STATE_NOT_RUNNING -> {
                        _isRunningFlow.value = false
                        _isLoadingFlow.value = false
                    }

                    AppConfig.MSG_STATE_START_SUCCESS -> {
                        _isRunningFlow.value = true
                        _isLoadingFlow.value = false
                    }

                    AppConfig.MSG_STATE_START_FAILURE -> {
                        if (!content.isNullOrBlank()) {
                            getApplication<Application>().toastError(content)
                        } else {
                            getApplication<Application>().toastError(R.string.toast_services_failure)
                        }
                        _isRunningFlow.value = false
                        _isLoadingFlow.value = false
                    }

                    AppConfig.MSG_STATE_STOP_SUCCESS -> {
                        _isRunningFlow.value = false
                        _isLoadingFlow.value = false
                    }

                    AppConfig.MSG_MEASURE_DELAY_SUCCESS -> {
                        _updateTestResultAction.tryEmit(content ?: "")
                        // For current server real ping, we might need to update the specific item if it's in the list
                        mmkvManager.getSelectServer()?.let { updateTestResult(it) }
                    }

                    AppConfig.MSG_MEASURE_CONFIG_SUCCESS -> {
                        LogUtil.d(AppConfig.TAG, "Ping success: $content")
                        updateTestResult(content ?: "")
                    }

                    AppConfig.MSG_MEASURE_CONFIG_NOTIFY -> {
                        _updateTestResultAction.tryEmit(
                            getApplication<Application>().getString(R.string.connection_runing_task_left, content)
                        )
                    }

                    AppConfig.MSG_MEASURE_CONFIG_FINISH -> {
                        if (content == "0") {
                            onTestsFinished()
                        }
                    }
                }
            }
        }
    }
}
