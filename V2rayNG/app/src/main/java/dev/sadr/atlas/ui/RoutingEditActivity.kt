package dev.sadr.atlas.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import dev.sadr.atlas.AppConfig.BUILTIN_OUTBOUND_TAGS
import dev.sadr.atlas.R
import dev.sadr.atlas.dto.entities.RulesetItem
import dev.sadr.atlas.extension.toast
import dev.sadr.atlas.extension.toastSuccess
import dev.sadr.atlas.handler.SettingsManager
import dev.sadr.atlas.ui.theme.DangerRed
import dev.sadr.atlas.ui.theme.V2Theme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class RoutingEditActivity : BaseActivity() {
    private val position by lazy { intent.getIntExtra("position", -1) }

    private var rulesetState = mutableStateOf(RulesetItem())
    private val suggestions = mutableStateListOf<String>()

    private val processPickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val selectedPackages = AppPickerActivity.getSelectedPackages(result.data)
            rulesetState.value = rulesetState.value.copy(process = selectedPackages)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val rulesetItem = SettingsManager.getRoutingRuleset(position) ?: RulesetItem(outboundTag = BUILTIN_OUTBOUND_TAGS.first())
        rulesetState.value = rulesetItem

        suggestions.addAll((BUILTIN_OUTBOUND_TAGS.toList() + SettingsManager.getProfileRemarks()).distinct())

        setContent {
            V2Theme {
                RoutingEditScreen(
                    ruleset = rulesetState.value,
                    suggestions = suggestions,
                    canDelete = position >= 0,
                    canUseProcessRouting = SettingsManager.canUseProcessRouting(),
                    onBack = { finish() },
                    onDelete = { deleteServer() },
                    onSave = { saveServer() },
                    onRulesetChange = { rulesetState.value = it },
                    onPickProcess = {
                        processPickerLauncher.launch(
                            AppPickerActivity.createIntent(
                                context = this,
                                selectedPackages = rulesetState.value.process.orEmpty(),
                                title = getString(R.string.routing_settings_process)
                            )
                        )
                    }
                )
                AtlasDialogHost(atlasDialog)
            }
        }
    }

    private fun saveServer() {
        val rulesetItem = rulesetState.value
        if (rulesetItem.remarks.isNullOrEmpty()) {
            toast(R.string.sub_setting_remarks)
            return
        }

        SettingsManager.saveRoutingRuleset(position, rulesetItem)
        toastSuccess(R.string.toast_success)
        finish()
    }

    private fun deleteServer() {
        if (position >= 0) {
            atlasDialog.confirm(
                message = getString(R.string.del_config_comfirm),
                destructive = true
            ) {
                lifecycleScope.launch(Dispatchers.IO) {
                    SettingsManager.removeRoutingRuleset(position)
                    launch(Dispatchers.Main) { finish() }
                }
            }
        }
    }
}

@Composable
fun RoutingEditScreen(
    ruleset: RulesetItem,
    suggestions: List<String>,
    canDelete: Boolean,
    canUseProcessRouting: Boolean,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    onSave: () -> Unit,
    onRulesetChange: (RulesetItem) -> Unit,
    onPickProcess: () -> Unit
) {
    AtlasSubScreen(
        title = stringResource(R.string.routing_settings_rule_title),
        onBack = onBack,
        actions = {
            if (canDelete) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Rounded.Delete, contentDescription = "Delete", tint = DangerRed)
                }
            }
            IconButton(onClick = onSave) {
                Icon(Icons.Rounded.Check, contentDescription = "Save", tint = MaterialTheme.colorScheme.primary)
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            RoutingEditField(
                value = ruleset.remarks.orEmpty(),
                onValueChange = { onRulesetChange(ruleset.copy(remarks = it)) },
                label = stringResource(R.string.sub_setting_remarks)
            )

            OutboundTagSelector(
                selectedTag = ruleset.outboundTag,
                suggestions = suggestions,
                onTagSelected = { onRulesetChange(ruleset.copy(outboundTag = it)) }
            )

            SectionCard {
                SettingRow(
                    icon = Icons.Rounded.Check,
                    title = "Locked",
                    subtitle = "Keep this rule when importing presets",
                    trailing = {
                        Switch(
                            checked = ruleset.locked == true,
                            onCheckedChange = { onRulesetChange(ruleset.copy(locked = it)) }
                        )
                    }
                )
            }

            RoutingEditField(
                value = ruleset.port.orEmpty(),
                onValueChange = { onRulesetChange(ruleset.copy(port = it)) },
                label = stringResource(R.string.routing_settings_port)
            )

            RoutingEditField(
                value = ruleset.domain?.joinToString(",") ?: "",
                onValueChange = { onRulesetChange(ruleset.copy(domain = it.split(",").map { s -> s.trim() }.filter { s -> s.isNotEmpty() })) },
                label = stringResource(R.string.routing_settings_domain)
            )

            RoutingEditField(
                value = ruleset.ip?.joinToString(",") ?: "",
                onValueChange = { onRulesetChange(ruleset.copy(ip = it.split(",").map { s -> s.trim() }.filter { s -> s.isNotEmpty() })) },
                label = stringResource(R.string.routing_settings_ip)
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                RoutingEditField(
                    value = ruleset.process?.joinToString(",") ?: "",
                    onValueChange = { onRulesetChange(ruleset.copy(process = it.split(",").map { s -> s.trim() }.filter { s -> s.isNotEmpty() })) },
                    label = stringResource(R.string.routing_settings_process),
                    modifier = Modifier.weight(1f),
                    enabled = canUseProcessRouting
                )
                IconButton(onClick = onPickProcess, enabled = canUseProcessRouting) {
                    Icon(painterResource(R.drawable.ic_per_apps_24dp), contentDescription = "Pick Apps")
                }
            }

            RoutingEditField(
                value = ruleset.protocol?.joinToString(",") ?: "",
                onValueChange = { onRulesetChange(ruleset.copy(protocol = it.split(",").map { s -> s.trim() }.filter { s -> s.isNotEmpty() })) },
                label = stringResource(R.string.routing_settings_protocol)
            )

            RoutingEditField(
                value = ruleset.network.orEmpty(),
                onValueChange = { onRulesetChange(ruleset.copy(network = it)) },
                label = stringResource(R.string.routing_settings_network)
            )

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@Composable
private fun RoutingEditField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        colors = atlasFieldColors()
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutboundTagSelector(
    selectedTag: String,
    suggestions: List<String>,
    onTagSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = selectedTag,
            onValueChange = onTagSelected,
            label = { Text(stringResource(R.string.routing_settings_outbound_tag)) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            readOnly = false,
            shape = RoundedCornerShape(16.dp),
            colors = atlasFieldColors(),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(14.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ) {
            suggestions.forEach { suggestion ->
                DropdownMenuItem(
                    text = { Text(suggestion) },
                    onClick = {
                        onTagSelected(suggestion)
                        expanded = false
                    }
                )
            }
        }
    }
}
