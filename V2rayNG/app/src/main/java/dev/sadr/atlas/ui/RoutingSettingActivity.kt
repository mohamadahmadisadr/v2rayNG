package dev.sadr.atlas.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AltRoute
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dev.sadr.atlas.AppConfig
import dev.sadr.atlas.R
import dev.sadr.atlas.dto.entities.RulesetItem
import dev.sadr.atlas.extension.toastError
import dev.sadr.atlas.extension.toastSuccess
import dev.sadr.atlas.handler.MmkvManager
import dev.sadr.atlas.handler.SettingsManager
import dev.sadr.atlas.ui.theme.DangerRed
import dev.sadr.atlas.ui.theme.SuccessGreen
import dev.sadr.atlas.util.JsonUtil
import dev.sadr.atlas.util.LogUtil
import dev.sadr.atlas.util.Utils
import dev.sadr.atlas.viewmodel.RoutingSettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RoutingSettingActivity : HelperBaseActivity() {
    private val viewModel: RoutingSettingsViewModel by viewModels()
    private val domainStrategy = mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        domainStrategy.value = getDomainStrategy()
        setContent {
            dev.sadr.atlas.ui.theme.V2Theme {
                RoutingSettingScreen(
                    viewModel = viewModel,
                    onBack = { finish() },
                    onAddRule = { startActivity(Intent(this, RoutingEditActivity::class.java)) },
                    onImportPredefined = { importPredefined() },
                    onImportClipboard = { importFromClipboard() },
                    onImportQRcode = { importQRcode() },
                    onExportClipboard = { export2Clipboard() },
                    onEditRule = { position ->
                        startActivity(Intent(this, RoutingEditActivity::class.java).putExtra("position", position))
                    },
                    domainStrategy = domainStrategy.value,
                    onSetDomainStrategy = { setDomainStrategy() }
                )
                AtlasDialogHost(atlasDialog)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.reload()
    }

    private fun getDomainStrategy(): String {
        val routing_domain_strategy = resources.getStringArray(R.array.routing_domain_strategy)
        return MmkvManager.decodeSettingsString(AppConfig.PREF_ROUTING_DOMAIN_STRATEGY) ?: routing_domain_strategy.first()
    }

    private fun setDomainStrategy() {
        val routing_domain_strategy = resources.getStringArray(R.array.routing_domain_strategy)
        atlasDialog.list(
            items = routing_domain_strategy.toList(),
            title = getString(R.string.routing_settings_domain_strategy)
        ) { i ->
            try {
                val value = routing_domain_strategy[i]
                MmkvManager.encodeSettings(AppConfig.PREF_ROUTING_DOMAIN_STRATEGY, value)
                domainStrategy.value = value
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to set domain strategy", e)
            }
        }
    }

    private fun importPredefined() {
        val preset_rulesets = resources.getStringArray(R.array.preset_rulesets)
        atlasDialog.list(
            items = preset_rulesets.toList(),
            title = getString(R.string.routing_settings_import_predefined_rulesets)
        ) { i ->
            atlasDialog.confirm(message = getString(R.string.routing_settings_import_rulesets_tip)) {
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        SettingsManager.resetRoutingRulesetsFromPresets(this@RoutingSettingActivity, i)
                        withContext(Dispatchers.Main) {
                            viewModel.reload()
                            toastSuccess(R.string.toast_success)
                        }
                    } catch (e: Exception) {
                        LogUtil.e(AppConfig.TAG, "Failed to import predefined ruleset", e)
                    }
                }
            }
        }
    }

    private fun importFromClipboard() {
        atlasDialog.confirm(message = getString(R.string.routing_settings_import_rulesets_tip)) {
            val clipboard = try {
                Utils.getClipboard(this)
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to get clipboard content", e)
                toastError(R.string.toast_failure)
                return@confirm
            }
            lifecycleScope.launch(Dispatchers.IO) {
                val result = SettingsManager.resetRoutingRulesets(clipboard)
                withContext(Dispatchers.Main) {
                    if (result) {
                        viewModel.reload()
                        toastSuccess(R.string.toast_success)
                    } else {
                        toastError(R.string.toast_failure)
                    }
                }
            }
        }
    }

    private fun importQRcode(): Boolean {
        launchQRCodeScanner { scanResult ->
            if (scanResult != null) {
                importRulesetsFromQRcode(scanResult)
            }
        }
        return true
    }

    private fun export2Clipboard() {
        val rulesetList = MmkvManager.decodeRoutingRulesets()
        if (rulesetList.isNullOrEmpty()) {
            toastError(R.string.toast_failure)
        } else {
            Utils.setClipboard(this, JsonUtil.toJson(rulesetList))
            toastSuccess(R.string.toast_success)
        }
    }

    private fun importRulesetsFromQRcode(qrcode: String?): Boolean {
        atlasDialog.confirm(message = getString(R.string.routing_settings_import_rulesets_tip)) {
            lifecycleScope.launch(Dispatchers.IO) {
                val result = SettingsManager.resetRoutingRulesets(qrcode)
                withContext(Dispatchers.Main) {
                    if (result) {
                        viewModel.reload()
                        toastSuccess(R.string.toast_success)
                    } else {
                        toastError(R.string.toast_failure)
                    }
                }
            }
        }
        return true
    }
}

@Composable
fun RoutingSettingScreen(
    viewModel: RoutingSettingsViewModel,
    onBack: () -> Unit,
    onAddRule: () -> Unit,
    onImportPredefined: () -> Unit,
    onImportClipboard: () -> Unit,
    onImportQRcode: () -> Unit,
    onExportClipboard: () -> Unit,
    onEditRule: (Int) -> Unit,
    domainStrategy: String,
    onSetDomainStrategy: () -> Unit
) {
    val rulesets by viewModel.rulesetsFlow.collectAsStateWithLifecycle()
    var showMenu by remember { mutableStateOf(false) }

    AtlasSubScreen(
        title = stringResource(R.string.routing_settings_title),
        onBack = onBack,
        actions = {
            IconButton(onClick = onAddRule) {
                Icon(Icons.Rounded.Add, contentDescription = "Add Rule")
            }
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "More")
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    shape = RoundedCornerShape(14.dp)
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.routing_settings_import_predefined_rulesets)) },
                        onClick = { showMenu = false; onImportPredefined() }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.routing_settings_import_rulesets_from_clipboard)) },
                        onClick = { showMenu = false; onImportClipboard() }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.routing_settings_import_rulesets_from_qrcode)) },
                        onClick = { showMenu = false; onImportQRcode() }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.routing_settings_export_rulesets_to_clipboard)) },
                        onClick = { showMenu = false; onExportClipboard() }
                    )
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp)
        ) {
            item {
                SectionCard {
                    SettingRow(
                        icon = Icons.Rounded.AltRoute,
                        title = stringResource(R.string.routing_settings_domain_strategy),
                        subtitle = domainStrategy,
                        onClick = onSetDomainStrategy
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
                if (rulesets.isNotEmpty()) {
                    SectionLabel(text = "Rules")
                }
            }

            itemsIndexed(rulesets) { index, item ->
                Surface(
                    modifier = Modifier.padding(top = if (index == 0) 0.dp else 3.dp),
                    shape = sectionItemShape(index, rulesets.lastIndex),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    RuleRow(item = item, onClick = { onEditRule(index) })
                }
            }
        }
    }
}

@Composable
private fun RuleRow(item: RulesetItem, onClick: () -> Unit) {
    val tag = item.outboundTag
    val color = when (tag) {
        "direct" -> SuccessGreen
        "block" -> DangerRed
        else -> MaterialTheme.colorScheme.primary
    }
    SettingRow(
        icon = Icons.Rounded.AltRoute,
        title = item.remarks.orEmpty().ifEmpty { tag },
        subtitle = "Port ${item.port}",
        iconTint = color,
        iconContainer = color.copy(alpha = 0.12f),
        onClick = onClick,
        trailing = {
            Surface(color = color.copy(alpha = 0.12f), shape = CircleShape) {
                Text(
                    text = tag,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = color,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    )
}
