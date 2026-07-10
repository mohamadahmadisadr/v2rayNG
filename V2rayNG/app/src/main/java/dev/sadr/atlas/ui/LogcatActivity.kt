package dev.sadr.atlas.ui

import android.content.ClipData
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dev.sadr.atlas.R
import dev.sadr.atlas.extension.toast
import dev.sadr.atlas.extension.toastSuccess
import dev.sadr.atlas.ui.theme.DangerRed
import dev.sadr.atlas.ui.theme.V2Theme
import dev.sadr.atlas.ui.theme.WarningAmber
import dev.sadr.atlas.util.Utils
import dev.sadr.atlas.viewmodel.LogcatViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LogcatActivity : BaseActivity() {
    private val viewModel: LogcatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            V2Theme {
                LogcatScreen(
                    viewModel = viewModel,
                    onBack = { finish() },
                    onCopyAll = { copyAll() },
                    onShareAll = { shareLogcat() },
                    onClearAll = { clearAll() },
                    onLogLongClick = { onLogLongClick(it) }
                )
            }
        }

        lifecycleScope.launch(Dispatchers.IO) {
            viewModel.loadLogcat()
        }
    }

    private fun onLogLongClick(log: String) {
        Utils.setClipboard(this, log)
        toastSuccess(R.string.toast_success)
    }

    private fun copyAll() {
        val all = viewModel.getAll().joinToString("\n")
        Utils.setClipboard(this, all)
        toastSuccess(R.string.toast_success)
    }

    private fun clearAll() {
        lifecycleScope.launch(Dispatchers.IO) {
            viewModel.clearLogcat()
        }
    }

    private fun shareLogcat() {
        lifecycleScope.launch(Dispatchers.IO) {
            val logText = viewModel.getAll().joinToString("\n")
            val result = try {
                val shareDir = File(cacheDir, "shared_logs").apply { mkdirs() }
                shareDir.listFiles()?.forEach { it.delete() }
                val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
                val logFile = File(shareDir, "AtlasVPN_logcat_$timestamp.txt")
                logFile.writeText(logText, Charsets.UTF_8)
                val uri = FileProvider.getUriForFile(this@LogcatActivity, "${packageName}.cache", logFile)
                uri to logFile.name
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { toast(e.localizedMessage ?: e.toString()) }
                return@launch
            }

            withContext(Dispatchers.Main) {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, result.first)
                    putExtra(Intent.EXTRA_SUBJECT, result.second)
                    putExtra(Intent.EXTRA_TITLE, result.second)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    clipData = ClipData.newUri(contentResolver, result.second, result.first)
                }
                startActivity(Intent.createChooser(shareIntent, getString(R.string.logcat_share)))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LogcatScreen(
    viewModel: LogcatViewModel,
    onBack: () -> Unit,
    onCopyAll: () -> Unit,
    onShareAll: () -> Unit,
    onClearAll: () -> Unit,
    onLogLongClick: (String) -> Unit
) {
    val logs by viewModel.logsFlow.collectAsStateWithLifecycle()
    var searchQuery by remember { mutableStateOf("") }

    AtlasSubScreen(
        title = stringResource(R.string.title_logcat),
        onBack = onBack,
        actions = {
            IconButton(onClick = onCopyAll) {
                Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy All")
            }
            IconButton(onClick = onShareAll) {
                Icon(Icons.Rounded.Share, contentDescription = "Share All")
            }
            IconButton(onClick = onClearAll) {
                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Clear All")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            AtlasSearchField(
                query = searchQuery,
                onQueryChange = {
                    searchQuery = it
                    viewModel.filter(it)
                },
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))

            if (logs.isEmpty()) {
                EmptyLogsState()
            } else {
                SectionCard(
                    modifier = Modifier
                        .padding(start = 20.dp, end = 20.dp, bottom = 20.dp)
                        .fillMaxSize()
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 6.dp)
                    ) {
                        items(logs) { log ->
                            Text(
                                text = log,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = logLineColor(log),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = {},
                                        onLongClick = { onLogLongClick(log) }
                                    )
                                    .padding(horizontal = 14.dp, vertical = 3.dp)
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 14.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun logLineColor(log: String): Color = when {
    log.contains(" E ") || log.contains("/E ") -> DangerRed
    log.contains(" W ") || log.contains("/W ") -> WarningAmber
    else -> MaterialTheme.colorScheme.onSurface
}

@Composable
private fun EmptyLogsState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Terminal,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "No log entries",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}
