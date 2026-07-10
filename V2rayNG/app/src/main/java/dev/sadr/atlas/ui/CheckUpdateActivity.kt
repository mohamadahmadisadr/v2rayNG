package dev.sadr.atlas.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dev.sadr.atlas.AppConfig
import dev.sadr.atlas.BuildConfig
import dev.sadr.atlas.R
import dev.sadr.atlas.core.CoreNativeManager
import dev.sadr.atlas.dto.CheckUpdateResult
import dev.sadr.atlas.extension.toast
import dev.sadr.atlas.extension.toastError
import dev.sadr.atlas.extension.toastSuccess
import dev.sadr.atlas.handler.MmkvManager
import dev.sadr.atlas.handler.UpdateCheckerManager
import dev.sadr.atlas.util.LogUtil
import dev.sadr.atlas.util.Utils
import kotlinx.coroutines.launch

class CheckUpdateActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val initialPreRelease = MmkvManager.decodeSettingsBool(AppConfig.PREF_CHECK_UPDATE_PRE_RELEASE, false)

        setContent {
            dev.sadr.atlas.ui.theme.V2Theme {
                CheckUpdateScreen(
                    version = "v${BuildConfig.VERSION_NAME} (${CoreNativeManager.getLibVersion()})",
                    initialPreRelease = initialPreRelease,
                    isLoadingFlow = isLoadingFlow,
                    onBack = { finish() },
                    onCheckUpdate = { checkForUpdates(it) },
                    onPreReleaseChanged = { MmkvManager.encodeSettings(AppConfig.PREF_CHECK_UPDATE_PRE_RELEASE, it) }
                )
                AtlasDialogHost(atlasDialog)
            }
        }

        checkForUpdates(initialPreRelease)
    }

    private fun checkForUpdates(includePreRelease: Boolean) {
        toast(R.string.update_checking_for_update)
        showLoading()

        lifecycleScope.launch {
            try {
                val result = UpdateCheckerManager.checkForUpdate(includePreRelease)
                if (result.hasUpdate) {
                    showUpdateDialog(result)
                } else {
                    toastSuccess(R.string.update_already_latest_version)
                }
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to check for updates: ${e.message}")
                toastError(e.message ?: getString(R.string.toast_failure))
            } finally {
                hideLoading()
            }
        }
    }

    private fun showUpdateDialog(result: CheckUpdateResult) {
        atlasDialog.confirm(
            message = result.releaseNotes.orEmpty(),
            title = getString(R.string.update_new_version_found, result.latestVersion),
            confirmText = getString(R.string.update_now)
        ) {
            result.downloadUrl?.let {
                Utils.openUri(this, it)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckUpdateScreen(
    version: String,
    initialPreRelease: Boolean,
    isLoadingFlow: kotlinx.coroutines.flow.StateFlow<Boolean>,
    onBack: () -> Unit,
    onCheckUpdate: (Boolean) -> Unit,
    onPreReleaseChanged: (Boolean) -> Unit
) {
    var checkPreRelease by remember { mutableStateOf(initialPreRelease) }
    val isLoading by isLoadingFlow.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.update_check_for_update)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            if (isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            
            Column(modifier = Modifier.padding(16.dp)) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.update_check_for_update)) },
                    supportingContent = { Text(version) },
                    modifier = Modifier.clickable { onCheckUpdate(checkPreRelease) }
                )
                
                HorizontalDivider()
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = stringResource(R.string.update_check_pre_release), style = MaterialTheme.typography.bodyLarge)
                    }
                    Switch(
                        checked = checkPreRelease,
                        onCheckedChange = {
                            checkPreRelease = it
                            onPreReleaseChanged(it)
                        }
                    )
                }
            }
        }
    }
}
