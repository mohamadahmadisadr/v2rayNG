package dev.sadr.atlas.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dev.sadr.atlas.AppConfig
import dev.sadr.atlas.R
import dev.sadr.atlas.dto.entities.AssetUrlCache
import dev.sadr.atlas.dto.entities.AssetUrlItem
import dev.sadr.atlas.extension.toast
import dev.sadr.atlas.extension.toastError
import dev.sadr.atlas.extension.toastSuccess
import dev.sadr.atlas.handler.MmkvManager
import dev.sadr.atlas.handler.SettingsManager
import dev.sadr.atlas.ui.theme.V2Theme
import dev.sadr.atlas.util.LogUtil
import dev.sadr.atlas.util.Utils
import dev.sadr.atlas.viewmodel.UserAssetViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class UserAssetActivity : HelperBaseActivity() {
    private val viewModel: UserAssetViewModel by viewModels()
    val extDir by lazy { File(Utils.userAssetPath(this)) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            V2Theme {
                UserAssetScreen(
                    viewModel = viewModel,
                    isLoadingFlow = isLoadingFlow,
                    getGeoFilesSources = { getGeoFilesSources() },
                    onBack = { finish() },
                    onAddFile = { showFileChooser() },
                    onAddUrl = { startActivity(Intent(this, UserAssetUrlActivity::class.java)) },
                    onImportQRcode = { importAssetFromQRcode() },
                    onDownloadGeoFiles = { downloadGeoFiles() },
                    onSetGeoFilesSources = { setGeoFilesSources() },
                    onEditAsset = { guid ->
                        startActivity(Intent(this, UserAssetUrlActivity::class.java).putExtra("assetId", guid))
                    },
                    onRemoveAsset = { guid, remarks -> removeAsset(guid, remarks) }
                )
                AtlasDialogHost(atlasDialog)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.reload(getGeoFilesSources())
    }

    private fun getGeoFilesSources(): String {
        return MmkvManager.decodeSettingsString(AppConfig.PREF_GEO_FILES_SOURCES) ?: AppConfig.GEO_FILES_SOURCES.first()
    }

    private fun setGeoFilesSources() {
        atlasDialog.list(items = AppConfig.GEO_FILES_SOURCES) { i ->
            try {
                val value = AppConfig.GEO_FILES_SOURCES[i]
                MmkvManager.encodeSettings(AppConfig.PREF_GEO_FILES_SOURCES, value)
                viewModel.reload(value)
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to set geo files sources", e)
            }
        }
    }

    private fun showFileChooser() {
        launchFileChooser { uri ->
            if (uri == null) return@launchFileChooser
            val assetId = Utils.getUuid()
            runCatching {
                val assetItem = AssetUrlItem(getCursorName(uri) ?: uri.toString(), "file")
                val assetList = MmkvManager.decodeAssetUrls()
                if (assetList.any { it.assetUrl.remarks == assetItem.remarks && it.guid != assetId }) {
                    toast(R.string.msg_remark_is_duplicate)
                } else {
                    MmkvManager.encodeAsset(assetId, assetItem)
                    copyFile(uri)
                }
            }.onFailure {
                toastError(R.string.toast_asset_copy_failed)
                MmkvManager.removeAssetUrl(assetId)
            }
        }
    }

    private fun copyFile(uri: Uri) {
        val targetFile = File(extDir, getCursorName(uri) ?: uri.toString())
        contentResolver.openInputStream(uri).use { inputStream ->
            targetFile.outputStream().use { fileOut ->
                inputStream?.copyTo(fileOut)
                toastSuccess(R.string.toast_success)
                viewModel.reload(getGeoFilesSources())
            }
        }
    }

    private fun getCursorName(uri: Uri): String? = try {
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)) else null
        }
    } catch (e: Exception) {
        LogUtil.e(AppConfig.TAG, "Failed to get cursor name", e)
        null
    }

    private fun importAssetFromQRcode() {
        launchQRCodeScanner { scanResult ->
            if (scanResult != null && Utils.isValidUrl(scanResult)) {
                startActivity(Intent(this, UserAssetUrlActivity::class.java).putExtra(UserAssetUrlActivity.ASSET_URL_QRCODE, scanResult))
            } else if (scanResult != null) {
                toast(R.string.toast_invalid_url)
            }
        }
    }

    private fun downloadGeoFiles() {
        showLoading()
        toast(R.string.msg_downloading_content)
        val proxyUsername = SettingsManager.getSocksUsername()
        val proxyPassword = SettingsManager.getSocksPassword()
        val httpPort = SettingsManager.getHttpPort()
        lifecycleScope.launch(Dispatchers.IO) {
            val result = viewModel.downloadGeoFiles(extDir, httpPort, proxyUsername, proxyPassword)
            withContext(Dispatchers.Main) {
                if (result.successCount > 0) toast(getString(R.string.title_update_config_count, result.successCount))
                else toast(getString(R.string.toast_failure))
                viewModel.reload(getGeoFilesSources())
                hideLoading()
            }
        }
    }

    private fun removeAsset(guid: String, remarks: String) {
        val file = extDir.listFiles()?.find { it.name == remarks }
        atlasDialog.confirm(
            message = getString(R.string.del_config_comfirm),
            destructive = true
        ) {
            file?.delete()
            MmkvManager.removeAssetUrl(guid)
            lifecycleScope.launch(Dispatchers.Default) {
                SettingsManager.initAssets(this@UserAssetActivity, assets)
                withContext(Dispatchers.Main) { viewModel.reload(getGeoFilesSources()) }
            }
        }
    }
}

@Composable
fun UserAssetScreen(
    viewModel: UserAssetViewModel,
    isLoadingFlow: kotlinx.coroutines.flow.StateFlow<Boolean>,
    getGeoFilesSources: () -> String,
    onBack: () -> Unit,
    onAddFile: () -> Unit,
    onAddUrl: () -> Unit,
    onImportQRcode: () -> Unit,
    onDownloadGeoFiles: () -> Unit,
    onSetGeoFilesSources: () -> Unit,
    onEditAsset: (String) -> Unit,
    onRemoveAsset: (String, String) -> Unit
) {
    val assets by viewModel.assetsFlow.collectAsStateWithLifecycle()
    val isLoading by isLoadingFlow.collectAsStateWithLifecycle()
    var showMenu by remember { mutableStateOf(false) }

    AtlasSubScreen(
        title = stringResource(R.string.title_user_asset_setting),
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
                    DropdownMenuItem(text = { Text(stringResource(R.string.menu_item_add_file)) }, onClick = { showMenu = false; onAddFile() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.menu_item_add_url)) }, onClick = { showMenu = false; onAddUrl() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.menu_item_scan_qrcode)) }, onClick = { showMenu = false; onImportQRcode() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.menu_item_download_file)) }, onClick = { showMenu = false; onDownloadGeoFiles() })
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
                        icon = Icons.Rounded.Language,
                        title = stringResource(R.string.asset_geo_files_sources),
                        subtitle = getGeoFilesSources(),
                        onClick = onSetGeoFilesSources
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
                if (assets.isNotEmpty()) {
                    SectionLabel(text = "Files")
                }
            }

            itemsIndexed(assets, key = { _, item -> item.guid }) { index, asset ->
                val shape = sectionItemShape(index, assets.lastIndex)
                androidx.compose.material3.Surface(
                    modifier = Modifier.padding(top = if (index == 0) 0.dp else 3.dp),
                    shape = shape,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column {
                        AssetRow(
                            asset = asset,
                            onEdit = { onEditAsset(asset.guid) },
                            onRemove = { onRemoveAsset(asset.guid, asset.assetUrl.remarks) }
                        )
                    }
                }
            }
        }
    }
}

internal fun sectionItemShape(index: Int, lastIndex: Int): RoundedCornerShape {
    val top = if (index == 0) 20.dp else 4.dp
    val bottom = if (index == lastIndex) 20.dp else 4.dp
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

@Composable
private fun AssetRow(asset: AssetUrlCache, onEdit: () -> Unit, onRemove: () -> Unit) {
    var showMenu by remember { mutableStateOf(false) }

    SettingRow(
        icon = Icons.Outlined.InsertDriveFile,
        title = asset.assetUrl.remarks,
        subtitle = asset.assetUrl.url,
        onClick = onEdit,
        trailing = {
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        Icons.Rounded.MoreVert,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    shape = RoundedCornerShape(14.dp)
                ) {
                    DropdownMenuItem(text = { Text("Edit") }, onClick = { showMenu = false; onEdit() })
                    DropdownMenuItem(text = { Text("Remove") }, onClick = { showMenu = false; onRemove() })
                }
            }
        }
    )
}
