package dev.sadr.atlas.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.text.TextUtils
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dev.sadr.atlas.AppConfig
import dev.sadr.atlas.AppConfig.ANG_PACKAGE
import dev.sadr.atlas.R
import dev.sadr.atlas.dto.AppInfo
import dev.sadr.atlas.dto.UrlContentRequest
import dev.sadr.atlas.extension.toast
import dev.sadr.atlas.extension.toastSuccess
import dev.sadr.atlas.extension.v2RayApplication
import dev.sadr.atlas.handler.MmkvManager
import dev.sadr.atlas.handler.SettingsChangeManager
import dev.sadr.atlas.handler.SettingsManager
import dev.sadr.atlas.util.AppManagerUtil
import dev.sadr.atlas.util.HttpUtil
import dev.sadr.atlas.util.LogUtil
import dev.sadr.atlas.util.Utils
import dev.sadr.atlas.viewmodel.PerAppProxyViewModel
import es.dmoral.toasty.Toasty
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator

class PerAppProxyActivity : BaseActivity() {
    private val viewModel: PerAppProxyViewModel by viewModels()
    private val appsAll = mutableStateListOf<AppInfo>()
    private var searchQuery = mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            dev.sadr.atlas.ui.theme.V2Theme {
                PerAppProxyScreen(
                    viewModel = viewModel,
                    apps = getFilteredApps(),
                    searchQuery = searchQuery.value,
                    isLoadingFlow = isLoadingFlow,
                    onBack = { finish() },
                    onSearchQueryChange = { searchQuery.value = it },
                    onSelectAll = { selectAllApp() },
                    onInvertSelection = { invertSelection() },
                    onAutoSelect = { selectProxyAppAuto() },
                    onImportClipboard = { importProxyApp() },
                    onExportClipboard = { exportProxyApp() }
                )
            }
        }

        loadApps()
    }

    private fun getFilteredApps(): List<AppInfo> {
        val query = searchQuery.value.uppercase()
        return appsAll.filter { app ->
            query.isEmpty() || app.appName.uppercase().contains(query) || app.packageName.uppercase().contains(query)
        }
    }

    private fun loadApps() {
        showLoading()
        lifecycleScope.launch {
            try {
                val apps = withContext(Dispatchers.IO) {
                    val appsList = AppManagerUtil.loadNetworkAppList(this@PerAppProxyActivity)
                    val blacklistSet = viewModel.getAll()
                    if (blacklistSet.isNotEmpty()) {
                        appsList.sortedWith { p1, p2 ->
                            val p1Selected = blacklistSet.contains(p1.packageName)
                            val p2Selected = blacklistSet.contains(p2.packageName)
                            when {
                                p1Selected && !p2Selected -> -1
                                !p1Selected && p2Selected -> 1
                                p1.isSystemApp && !p2.isSystemApp -> 1
                                !p1.isSystemApp && p2.isSystemApp -> -1
                                else -> p1.appName.lowercase().compareTo(p2.appName.lowercase())
                            }
                        }
                    } else {
                        val collator = Collator.getInstance()
                        appsList.sortedWith(compareBy(collator) { it.appName })
                    }
                }
                appsAll.clear()
                appsAll.addAll(apps)
            } catch (e: Exception) {
                LogUtil.e(ANG_PACKAGE, "Error loading apps", e)
            } finally {
                hideLoading()
            }
        }
    }

    private fun selectAllApp() {
        val pkgNames = appsAll.map { it.packageName }
        val allSelected = pkgNames.all { viewModel.contains(it) }
        if (allSelected) viewModel.removeAll(pkgNames) else viewModel.addAll(pkgNames)
        allowPerAppProxy()
    }

    private fun invertSelection() {
        appsAll.forEach { viewModel.toggle(it.packageName) }
        allowPerAppProxy()
    }

    private fun selectProxyAppAuto() {
        toast(R.string.msg_downloading_content)
        showLoading()
        lifecycleScope.launch(Dispatchers.IO) {
            val url = AppConfig.ANDROID_PACKAGE_NAME_LIST_URL
            var content = HttpUtil.getUrlContent(UrlContentRequest(url = url, timeout = 5000))
            if (content.isNullOrEmpty()) {
                content = HttpUtil.getUrlContent(UrlContentRequest(
                    url = url, timeout = 5000, 
                    httpPort = SettingsManager.getHttpPort(), 
                    proxyUsername = SettingsManager.getSocksUsername(), 
                    proxyPassword = SettingsManager.getSocksPassword()
                )) ?: ""
            }
            withContext(Dispatchers.Main) {
                selectProxyApp(content, true)
                toastSuccess(R.string.toast_success)
                hideLoading()
            }
        }
    }

    private fun importProxyApp() {
        val content = Utils.getClipboard(this)
        if (content.isNotEmpty()) {
            selectProxyApp(content, false)
            toastSuccess(R.string.toast_success)
        }
    }

    private fun exportProxyApp() {
        val bypass = MmkvManager.decodeSettingsBool(AppConfig.PREF_BYPASS_APPS, false)
        val lst = (listOf(bypass.toString()) + viewModel.getAll()).joinToString(System.lineSeparator())
        Utils.setClipboard(this, lst)
        toastSuccess(R.string.toast_success)
    }

    private fun allowPerAppProxy() {
        MmkvManager.encodeSettings(AppConfig.PREF_PER_APP_PROXY, true)
        SettingsChangeManager.makeRestartService()
    }

    private fun selectProxyApp(content: String, force: Boolean) {
        val proxyApps = content.ifEmpty { Utils.readTextFromAssets(v2RayApplication, "proxy_package_name") }
        if (proxyApps.isEmpty()) return

        viewModel.clear()
        val bypass = MmkvManager.decodeSettingsBool(AppConfig.PREF_BYPASS_APPS, false)
        appsAll.forEach { app ->
            val pkg = app.packageName
            val isIn = proxyApps.contains(pkg) || (force && pkg.startsWith("com.google") && pkg != "com.google.android.webview")
            if (bypass != isIn) viewModel.add(pkg)
        }
    }
}

@Composable
fun PerAppProxyScreen(
    viewModel: PerAppProxyViewModel,
    apps: List<AppInfo>,
    searchQuery: String,
    isLoadingFlow: kotlinx.coroutines.flow.StateFlow<Boolean>,
    onBack: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSelectAll: () -> Unit,
    onInvertSelection: () -> Unit,
    onAutoSelect: () -> Unit,
    onImportClipboard: () -> Unit,
    onExportClipboard: () -> Unit
) {
    val isLoading by isLoadingFlow.collectAsStateWithLifecycle()
    val blacklist by viewModel.blacklistFlow.collectAsStateWithLifecycle()
    var showMenu by remember { mutableStateOf(false) }

    var perAppProxy by remember { mutableStateOf(MmkvManager.decodeSettingsBool(AppConfig.PREF_PER_APP_PROXY, false)) }
    var bypassApps by remember { mutableStateOf(MmkvManager.decodeSettingsBool(AppConfig.PREF_BYPASS_APPS, false)) }

    AtlasSubScreen(
        title = stringResource(R.string.per_app_proxy_settings),
        onBack = onBack,
        isLoading = isLoading,
        actions = {
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "More")
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    shape = RoundedCornerShape(14.dp)
                ) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.menu_item_select_all)) }, onClick = { showMenu = false; onSelectAll() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.menu_item_invert_selection)) }, onClick = { showMenu = false; onInvertSelection() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.menu_item_select_proxy_app)) }, onClick = { showMenu = false; onAutoSelect() })
                    DropdownMenuItem(text = { Text("Import from clipboard") }, onClick = { showMenu = false; onImportClipboard() })
                    DropdownMenuItem(text = { Text("Export to clipboard") }, onClick = { showMenu = false; onExportClipboard() })
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
                        icon = Icons.Rounded.FilterAlt,
                        title = "Enable per-app proxy",
                        subtitle = "Route only selected apps through VPN",
                        trailing = {
                            Switch(checked = perAppProxy, onCheckedChange = {
                                perAppProxy = it
                                MmkvManager.encodeSettings(AppConfig.PREF_PER_APP_PROXY, it)
                                SettingsChangeManager.makeRestartService()
                            })
                        }
                    )
                    RowDivider()
                    SettingRow(
                        icon = Icons.Rounded.SwapHoriz,
                        title = stringResource(R.string.switch_bypass_apps_mode),
                        subtitle = "Selected apps bypass the VPN instead",
                        trailing = {
                            Switch(checked = bypassApps, onCheckedChange = {
                                bypassApps = it
                                MmkvManager.encodeSettings(AppConfig.PREF_BYPASS_APPS, it)
                                SettingsChangeManager.makeRestartService()
                            })
                        }
                    )
                }

                AtlasSearchField(
                    query = searchQuery,
                    onQueryChange = onSearchQueryChange,
                    modifier = Modifier.padding(vertical = 14.dp)
                )
            }

            itemsIndexed(apps, key = { _, app -> app.packageName }) { index, app ->
                Surface(
                    modifier = Modifier.padding(top = if (index == 0) 0.dp else 3.dp),
                    shape = sectionItemShape(index, apps.lastIndex),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    AppProxyRow(
                        app = app,
                        isSelected = blacklist.contains(app.packageName),
                        onToggle = { viewModel.toggle(app.packageName) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AppProxyRow(app: AppInfo, isSelected: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val bitmap = remember(app.appIcon) { app.appIcon.toBitmap().asImageBitmap() }
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.appName,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = app.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Checkbox(checked = isSelected, onCheckedChange = { onToggle() })
    }
}
