package dev.sadr.atlas.viewmodel

import androidx.lifecycle.ViewModel
import dev.sadr.atlas.dto.entities.SubscriptionCache
import dev.sadr.atlas.dto.entities.SubscriptionItem
import dev.sadr.atlas.handler.MmkvManager
import dev.sadr.atlas.handler.SettingsChangeManager
import dev.sadr.atlas.handler.SettingsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class SubscriptionsViewModel @Inject constructor(
    private val mmkvManager: MmkvManager,
    private val settingsManager: SettingsManager
) : ViewModel() {
    private val subscriptions: MutableList<SubscriptionCache> =
        mmkvManager.decodeSubscriptions().toMutableList()

    private val _subscriptionsFlow = MutableStateFlow<List<SubscriptionCache>>(emptyList())
    val subscriptionsFlow = _subscriptionsFlow.asStateFlow()

    init {
        _subscriptionsFlow.value = subscriptions.toList()
    }

    @Synchronized
    fun getAll(): List<SubscriptionCache> = subscriptions.toList()

    @Synchronized
    fun reload() {
        subscriptions.clear()
        subscriptions.addAll(mmkvManager.decodeSubscriptions())
        _subscriptionsFlow.value = subscriptions.toList()
    }

    @Synchronized
    fun remove(subId: String): Boolean {
        val changed = subscriptions.removeAll { it.guid == subId }
        if (changed) {
            settingsManager.removeSubscriptionWithDefault(subId)
            SettingsChangeManager.makeSetupGroupTab()
            _subscriptionsFlow.value = subscriptions.toList()
        }
        return changed
    }

    @Synchronized
    fun update(subId: String, item: SubscriptionItem) {
        val idx = subscriptions.indexOfFirst { it.guid == subId }
        if (idx >= 0) {
            subscriptions[idx] = SubscriptionCache(subId, item)
            mmkvManager.encodeSubscription(subId, item)
            _subscriptionsFlow.value = subscriptions.toList()
        }
    }

    @Synchronized
    fun swap(fromPosition: Int, toPosition: Int) {
        if (fromPosition in subscriptions.indices && toPosition in subscriptions.indices) {
            val item = subscriptions.removeAt(fromPosition)
            subscriptions.add(toPosition, item)
            settingsManager.swapSubscriptions(fromPosition, toPosition)
            SettingsChangeManager.makeSetupGroupTab()
            _subscriptionsFlow.value = subscriptions.toList()
        }
    }
}

