package dev.sadr.atlas.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dev.sadr.atlas.AppConfig
import dev.sadr.atlas.R
import dev.sadr.atlas.dto.entities.SubscriptionCache
import dev.sadr.atlas.extension.toTrafficString
import dev.sadr.atlas.extension.toast
import dev.sadr.atlas.handler.AngConfigManager
import dev.sadr.atlas.handler.MmkvManager
import dev.sadr.atlas.ui.theme.AccentBlue
import dev.sadr.atlas.ui.theme.AccentBlueDeep
import dev.sadr.atlas.ui.theme.SuccessGreen
import dev.sadr.atlas.ui.theme.V2Theme
import dev.sadr.atlas.util.QRCodeDecoder
import dev.sadr.atlas.viewmodel.SubscriptionsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SubSettingActivity : BaseActivity() {
    private val viewModel: SubscriptionsViewModel by viewModels()
    private var qrCodeUrl = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            V2Theme {
                val url by qrCodeUrl
                SubSettingScreen(
                    viewModel = viewModel,
                    onBack = { finish() },
                    onAdd = { startActivity(Intent(this, SubEditActivity::class.java)) },
                    onUpdateAll = { updateAllSubscriptions() },
                    onEdit = { guid ->
                        startActivity(Intent(this, SubEditActivity::class.java).putExtra("subId", guid))
                    },
                    onRemove = { guid -> removeSubscription(guid) },
                    onShare = { shareUrl -> qrCodeUrl.value = shareUrl }
                )
                AtlasDialogHost(atlasDialog)

                if (url != null) {
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { qrCodeUrl.value = null },
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        confirmButton = { TextButton(onClick = { qrCodeUrl.value = null }) { Text("OK") } },
                        title = { Text("QR Code") },
                        text = {
                            val bitmap = remember(url) { QRCodeDecoder.createQRCode(url!!) }
                            bitmap?.let {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = Color.White
                                ) {
                                    Image(
                                        bitmap = it.asImageBitmap(),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(300.dp)
                                            .padding(16.dp)
                                    )
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.reload()
    }

    private fun updateAllSubscriptions() {
        showLoading()
        lifecycleScope.launch(Dispatchers.IO) {
            val result = AngConfigManager.updateConfigViaSubAll()
            delay(500L)
            withContext(Dispatchers.Main) {
                if (result.successCount + result.failureCount + result.skipCount == 0) {
                    toast(R.string.title_update_subscription_no_subscription)
                } else if (result.successCount > 0 && result.failureCount + result.skipCount == 0) {
                    toast(getString(R.string.title_update_config_count, result.configCount))
                } else {
                    toast(getString(R.string.title_update_subscription_result,
                        result.configCount, result.successCount, result.failureCount, result.skipCount))
                }
                hideLoading()
                viewModel.reload()
            }
        }
    }

    private fun removeSubscription(guid: String) {
        if (MmkvManager.decodeSettingsBool(AppConfig.PREF_CONFIRM_REMOVE)) {
            atlasDialog.confirm(
                message = getString(R.string.del_config_comfirm),
                destructive = true
            ) {
                viewModel.remove(guid)
            }
        } else {
            viewModel.remove(guid)
        }
    }
}

@Composable
fun SubSettingScreen(
    viewModel: SubscriptionsViewModel,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onUpdateAll: () -> Unit,
    onEdit: (String) -> Unit,
    onRemove: (String) -> Unit,
    onShare: (String) -> Unit
) {
    val subscriptions by viewModel.subscriptionsFlow.collectAsStateWithLifecycle()

    AtlasSubScreen(
        title = stringResource(R.string.title_sub_setting),
        onBack = onBack,
        actions = {
            IconButton(onClick = onAdd) {
                Icon(Icons.Rounded.Add, contentDescription = "Add")
            }
            IconButton(onClick = onUpdateAll) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Update All")
            }
        }
    ) { padding ->
        if (subscriptions.isEmpty()) {
            EmptySubscriptionsState(modifier = Modifier.padding(padding), onAdd = onAdd)
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(subscriptions, key = { it.guid }) { sub ->
                    SubscriptionCard(
                        subscription = sub,
                        onClick = { onEdit(sub.guid) },
                        onRemove = { onRemove(sub.guid) },
                        onShare = { onShare(sub.subscription.url) }
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptySubscriptionsState(modifier: Modifier = Modifier, onAdd: () -> Unit) {
    Column(
        modifier = modifier
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
                imageVector = Icons.Outlined.CloudOff,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "No subscriptions yet",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "Add a subscription group to fetch configs automatically",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        androidx.compose.material3.Button(onClick = onAdd, shape = CircleShape) {
            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Add subscription")
        }
    }
}

@Composable
private fun SubscriptionCard(
    subscription: SubscriptionCache,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    onShare: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val enabled = subscription.subscription.enabled

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
      Column {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(AccentBlue, AccentBlueDeep))),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = subscription.subscription.remarks.take(1).uppercase().ifEmpty { "S" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = subscription.subscription.remarks,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subscription.subscription.url.isNotEmpty()) {
                    Text(
                        text = subscription.subscription.url,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 1.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            val statusColor = if (enabled) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant
            Surface(color = statusColor.copy(alpha = 0.12f), shape = CircleShape) {
                Text(
                    text = if (enabled) "On" else "Off",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = statusColor,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
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
                    DropdownMenuItem(text = { Text("Edit") }, onClick = { showMenu = false; onClick() })
                    DropdownMenuItem(text = { Text("Share") }, onClick = { showMenu = false; onShare() })
                    DropdownMenuItem(text = { Text("Remove") }, onClick = { showMenu = false; onRemove() })
                }
            }
        }
        SubscriptionUsage(subscription.subscription)
      }
    }
}

/**
 * Shows the data used and expiry reported by the server's `subscription-userinfo`
 * header. Renders nothing when the server didn't report any usage info.
 */
@Composable
private fun SubscriptionUsage(item: dev.sadr.atlas.dto.entities.SubscriptionItem) {
    val hasUsage = item.download >= 0 || item.upload >= 0 || item.total >= 0
    val hasExpiry = item.expire >= 0
    if (!hasUsage && !hasExpiry) return

    val used = (item.upload.coerceAtLeast(0)) + (item.download.coerceAtLeast(0))
    val total = item.total
    val usedText = when {
        total > 0 -> "${used.toTrafficString()} / ${total.toTrafficString()}"
        hasUsage -> "${used.toTrafficString()} used"
        else -> null
    }
    val progress = if (total > 0) (used.toFloat() / total.toFloat()).coerceIn(0f, 1f) else null

    val expiryText = when {
        !hasExpiry -> null
        item.expire == 0L -> "Never expires"
        else -> {
            val now = System.currentTimeMillis() / 1000L
            val date = remember(item.expire) {
                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(item.expire * 1000L))
            }
            if (item.expire <= now) "Expired" else "Expires $date"
        }
    }

    Column(
        modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp)
    ) {
        if (progress != null) {
            androidx.compose.material3.LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape),
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
            Spacer(modifier = Modifier.height(6.dp))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (usedText != null) {
                Text(
                    text = usedText,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (expiryText != null) {
                val expired = expiryText == "Expired"
                Text(
                    text = expiryText,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (expired) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
