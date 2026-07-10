package dev.sadr.atlas.viewmodel

import androidx.lifecycle.ViewModel
import dev.sadr.atlas.dto.entities.RulesetItem
import dev.sadr.atlas.handler.MmkvManager
import dev.sadr.atlas.handler.SettingsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class RoutingSettingsViewModel @Inject constructor(
    private val mmkvManager: MmkvManager,
    private val settingsManager: SettingsManager
) : ViewModel() {
    private val rulesets: MutableList<RulesetItem> = mutableListOf()

    private val _rulesetsFlow = MutableStateFlow<List<RulesetItem>>(emptyList())
    val rulesetsFlow = _rulesetsFlow.asStateFlow()

    @Synchronized
    fun getAll(): List<RulesetItem> = rulesets.toList()

    @Synchronized
    fun reload() {
        rulesets.clear()
        rulesets.addAll(mmkvManager.decodeRoutingRulesets() ?: mutableListOf())
        _rulesetsFlow.value = rulesets.toList()
    }

    @Synchronized
    fun update(position: Int, item: RulesetItem) {
        if (position in rulesets.indices) {
            rulesets[position] = item
            settingsManager.saveRoutingRuleset(position, item)
        }
    }

    @Synchronized
    fun swap(fromPosition: Int, toPosition: Int) {
        if (fromPosition in rulesets.indices && toPosition in rulesets.indices) {
            java.util.Collections.swap(rulesets, fromPosition, toPosition)
            settingsManager.swapRoutingRuleset(fromPosition, toPosition)
            _rulesetsFlow.value = rulesets.toList()
        }
    }
}

