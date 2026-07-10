package dev.sadr.atlas.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dev.sadr.atlas.AppConfig
import dev.sadr.atlas.AppConfig.WEBDAV_BACKUP_FILE_NAME
import dev.sadr.atlas.BuildConfig
import dev.sadr.atlas.R
import dev.sadr.atlas.dto.entities.WebDavConfig
import dev.sadr.atlas.extension.toastError
import dev.sadr.atlas.extension.toastSuccess
import dev.sadr.atlas.handler.MmkvManager
import dev.sadr.atlas.handler.SettingsChangeManager
import dev.sadr.atlas.handler.SettingsManager
import dev.sadr.atlas.handler.WebDavManager
import dev.sadr.atlas.ui.theme.V2Theme
import dev.sadr.atlas.util.LogUtil
import dev.sadr.atlas.util.ZipUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

class BackupActivity : HelperBaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            V2Theme {
                BackupScreen(
                    isLoadingFlow = isLoadingFlow,
                    onBack = { finish() },
                    onBackup = { showBackupOptions() },
                    onRestore = { showRestoreOptions() },
                    onShare = { shareBackup() },
                    loadWebDavConfig = { MmkvManager.decodeWebDavConfig() ?: WebDavConfig(baseUrl = "") },
                    saveWebDavConfig = { config ->
                        MmkvManager.encodeWebDavConfig(config)
                        toastSuccess(R.string.toast_success)
                    }
                )
                AtlasDialogHost(atlasDialog)
            }
        }
    }

    private fun showBackupOptions() {
        val options = resources.getStringArray(R.array.config_backup_options)
        atlasDialog.list(
            items = options.toList(),
            title = getString(R.string.title_configuration_backup)
        ) { which ->
            when (which) {
                0 -> backupViaLocal()
                1 -> backupViaWebDav()
            }
        }
    }

    private fun showRestoreOptions() {
        val options = resources.getStringArray(R.array.config_backup_options)
        atlasDialog.list(
            items = options.toList(),
            title = getString(R.string.title_configuration_restore)
        ) { which ->
            when (which) {
                0 -> restoreViaLocal()
                1 -> restoreViaWebDav()
            }
        }
    }

    private fun shareBackup() {
        val ret = backupConfigurationToCache()
        if (ret.first) {
            startActivity(Intent.createChooser(
                Intent(Intent.ACTION_SEND).setType("application/zip")
                    .setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    .putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(this, BuildConfig.APPLICATION_ID + ".cache", File(ret.second))),
                getString(R.string.title_configuration_share)
            ))
        } else {
            toastError(R.string.toast_failure)
        }
    }

    private fun backupConfigurationToCache(): Pair<Boolean, String> {
        val dateFormatted = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.getDefault()).format(System.currentTimeMillis())
        val folderName = "${getString(R.string.app_name)}_${dateFormatted}"
        val backupDir = File(cacheDir, folderName).absolutePath
        val outputZipFilePath = File(cacheDir, "$folderName.zip").absolutePath

        val count = MmkvManager.backupAll(backupDir)
        if (count <= 0) return Pair(false, "")
        return if (ZipUtil.zipFromFolder(backupDir, outputZipFilePath)) Pair(true, outputZipFilePath) else Pair(false, "")
    }

    private fun restoreConfiguration(zipFile: File): Boolean {
        val backupDir = File(cacheDir, System.currentTimeMillis().toString()).absolutePath
        if (!ZipUtil.unzipToFolder(zipFile, backupDir)) return false
        val count = MmkvManager.restoreAll(backupDir)
        SettingsChangeManager.makeSetupGroupTab()
        SettingsChangeManager.makeRestartService()
        SettingsManager.initApp(this)
        return count > 0
    }

    private fun backupViaLocal() {
        val dateFormatted = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.getDefault()).format(System.currentTimeMillis())
        val defaultFileName = "${getString(R.string.app_name)}_${dateFormatted}.zip"

        launchCreateDocument(defaultFileName) { uri ->
            if (uri != null) {
                try {
                    val ret = backupConfigurationToCache()
                    if (ret.first) {
                        contentResolver.openOutputStream(uri)?.use { output ->
                            File(ret.second).inputStream().use { input -> input.copyTo(output) }
                        }
                        File(ret.second).delete()
                        toastSuccess(R.string.toast_success)
                    } else {
                        toastError(R.string.toast_failure)
                    }
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Failed to backup", e)
                    toastError(R.string.toast_failure)
                }
            }
        }
    }

    private fun restoreViaLocal() {
        launchFileChooser { uri ->
            if (uri == null) return@launchFileChooser
            try {
                val targetFile = File(cacheDir, "${System.currentTimeMillis()}.zip")
                contentResolver.openInputStream(uri).use { input ->
                    targetFile.outputStream().use { fileOut -> input?.copyTo(fileOut) }
                }
                if (restoreConfiguration(targetFile)) toastSuccess(R.string.toast_success)
                else toastError(R.string.toast_failure)
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Restore error", e)
                toastError(R.string.toast_failure)
            }
        }
    }

    private fun backupViaWebDav() {
        val saved = MmkvManager.decodeWebDavConfig()
        if (saved == null || saved.baseUrl.isEmpty()) {
            toastError(R.string.title_webdav_config_setting_unknown)
            return
        }
        showLoading()
        lifecycleScope.launch(Dispatchers.IO) {
            var tempFile: File? = null
            try {
                val ret = backupConfigurationToCache()
                if (ret.first) {
                    tempFile = File(ret.second)
                    WebDavManager.init(saved)
                    val ok = WebDavManager.uploadFile(tempFile, WEBDAV_BACKUP_FILE_NAME)
                    withContext(Dispatchers.Main) { if (ok) toastSuccess(R.string.toast_success) else toastError(R.string.toast_failure) }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { toastError(R.string.toast_failure) }
            } finally {
                tempFile?.delete()
                withContext(Dispatchers.Main) { hideLoading() }
            }
        }
    }

    private fun restoreViaWebDav() {
        val saved = MmkvManager.decodeWebDavConfig()
        if (saved == null || saved.baseUrl.isEmpty()) {
            toastError(R.string.title_webdav_config_setting_unknown)
            return
        }
        showLoading()
        lifecycleScope.launch(Dispatchers.IO) {
            var target: File? = null
            try {
                target = File(cacheDir, "download_${System.currentTimeMillis()}.zip")
                WebDavManager.init(saved)
                if (WebDavManager.downloadFile(WEBDAV_BACKUP_FILE_NAME, target)) {
                    val restored = restoreConfiguration(target)
                    withContext(Dispatchers.Main) { if (restored) toastSuccess(R.string.toast_success) else toastError(R.string.toast_failure) }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { toastError(R.string.toast_failure) }
            } finally {
                target?.delete()
                withContext(Dispatchers.Main) { hideLoading() }
            }
        }
    }
}

@Composable
fun BackupScreen(
    isLoadingFlow: kotlinx.coroutines.flow.StateFlow<Boolean>,
    onBack: () -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    onShare: () -> Unit,
    loadWebDavConfig: () -> WebDavConfig,
    saveWebDavConfig: (WebDavConfig) -> Unit
) {
    val isLoading by isLoadingFlow.collectAsStateWithLifecycle()
    var showWebDavDialog by remember { mutableStateOf(false) }

    AtlasSubScreen(
        title = stringResource(R.string.title_configuration_backup_restore),
        onBack = onBack,
        isLoading = isLoading
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            SectionLabel(text = "Local")
            SectionCard {
                SettingRow(
                    icon = Icons.Rounded.Save,
                    title = stringResource(R.string.title_configuration_backup),
                    subtitle = "Backup current configuration",
                    onClick = onBackup
                )
                RowDivider()
                SettingRow(
                    icon = Icons.Rounded.Restore,
                    title = stringResource(R.string.title_configuration_restore),
                    subtitle = "Restore from previous backup",
                    onClick = onRestore
                )
                RowDivider()
                SettingRow(
                    icon = Icons.Rounded.Share,
                    title = stringResource(R.string.title_configuration_share),
                    subtitle = "Share configuration as zip",
                    onClick = onShare
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            SectionLabel(text = "Cloud")
            SectionCard {
                SettingRow(
                    icon = Icons.Rounded.Cloud,
                    title = stringResource(R.string.title_webdav_config_setting),
                    subtitle = "Configure WebDAV sync",
                    onClick = { showWebDavDialog = true }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showWebDavDialog) {
        var config by remember { mutableStateOf(loadWebDavConfig()) }
        AlertDialog(
            onDismissRequest = { showWebDavDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text(stringResource(R.string.title_webdav_config_setting)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = config.baseUrl, onValueChange = { config = config.copy(baseUrl = it) }, label = { Text("WebDAV URL") })
                    OutlinedTextField(value = config.username ?: "", onValueChange = { config = config.copy(username = it.ifEmpty { null }) }, label = { Text("Username") })
                    OutlinedTextField(value = config.password ?: "", onValueChange = { config = config.copy(password = it.ifEmpty { null }) }, label = { Text("Password") })
                    OutlinedTextField(value = config.remoteBasePath, onValueChange = { config = config.copy(remoteBasePath = it.ifEmpty { "/" }) }, label = { Text("Remote Path") })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    saveWebDavConfig(config)
                    showWebDavDialog = false
                }) { Text(stringResource(R.string.menu_item_save_config)) }
            },
            dismissButton = {
                TextButton(onClick = { showWebDavDialog = false }) { Text(stringResource(android.R.string.cancel)) }
            }
        )
    }
}
