package dev.sadr.atlas.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.sadr.atlas.R
import dev.sadr.atlas.dto.entities.ProfileItem
import dev.sadr.atlas.extension.isComplexType
import dev.sadr.atlas.ui.theme.DangerRed
import dev.sadr.atlas.util.Utils

enum class ServerAction { ShowQr, CopyLink, CopyFull, Edit, Delete }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerActionSheet(
    profile: ProfileItem,
    onDismiss: () -> Unit,
    onAction: (ServerAction) -> Unit
) {
    val isComplex = profile.configType.isComplexType()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            // Header
            Row(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ProtocolAvatar(profile = profile, size = 44.dp)
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = profile.remarks,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = protocolDescription(profile),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            if (!isComplex) {
                SheetActionRow(Icons.Rounded.QrCode2, "Show QR code") { onAction(ServerAction.ShowQr) }
                SheetActionRow(Icons.Rounded.ContentCopy, "Copy share link") { onAction(ServerAction.CopyLink) }
            }
            SheetActionRow(Icons.Rounded.DataObject, "Copy full config") { onAction(ServerAction.CopyFull) }
            SheetActionRow(Icons.Rounded.Edit, "Edit") { onAction(ServerAction.Edit) }
            SheetActionRow(Icons.Rounded.Delete, "Delete", tint = DangerRed) { onAction(ServerAction.Delete) }
        }
    }
}

@Composable
private fun SheetActionRow(
    icon: ImageVector,
    label: String,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    if (tint == DangerRed) DangerRed.copy(alpha = 0.12f)
                    else MaterialTheme.colorScheme.surfaceContainerHighest
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = if (tint == DangerRed) DangerRed else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = tint
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ImportConfigSheet(
    onDismiss: () -> Unit,
    onImportText: (String) -> Unit,
    onQuickAction: (Int) -> Unit
) {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Add Config",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Paste anything: a subscription link, config links, or a full config — it is detected automatically",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )

            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                placeholder = { Text("vless://…, https://sub-url, …") },
                minLines = 2,
                maxLines = 4,
                shape = RoundedCornerShape(16.dp),
                trailingIcon = {
                    IconButton(onClick = {
                        try {
                            text = Utils.getClipboard(context)
                        } catch (_: Exception) {
                        }
                    }) {
                        Icon(
                            Icons.Rounded.ContentPaste,
                            contentDescription = "Paste",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedBorderColor = MaterialTheme.colorScheme.primary
                )
            )

            Button(
                onClick = { onImportText(text) },
                enabled = text.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .height(48.dp),
                shape = CircleShape
            ) {
                Text("Import")
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                QuickActionTile(
                    icon = Icons.Rounded.QrCodeScanner,
                    label = stringResource(R.string.menu_item_import_config_qrcode),
                    modifier = Modifier.weight(1f)
                ) { onQuickAction(R.id.import_qrcode) }
                QuickActionTile(
                    icon = Icons.Rounded.FileOpen,
                    label = stringResource(R.string.menu_item_import_config_local),
                    modifier = Modifier.weight(1f)
                ) { onQuickAction(R.id.import_local) }
            }

            Text(
                text = "Create manually",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val manualTypes = listOf(
                    "VLESS" to R.id.import_manually_vless,
                    "VMess" to R.id.import_manually_vmess,
                    "Trojan" to R.id.import_manually_trojan,
                    "Shadowsocks" to R.id.import_manually_ss,
                    "Hysteria2" to R.id.import_manually_hysteria2,
                    "WireGuard" to R.id.import_manually_wireguard,
                    "SOCKS" to R.id.import_manually_socks,
                    "HTTP" to R.id.import_manually_http,
                    "Policy group" to R.id.import_manually_policy_group,
                    "Proxy chain" to R.id.import_manually_proxy_chain
                )
                manualTypes.forEach { (label, id) ->
                    AssistChip(
                        onClick = { onQuickAction(id) },
                        label = { Text(label) },
                        shape = CircleShape,
                        border = AssistChipDefaults.assistChipBorder(
                            enabled = true,
                            borderColor = MaterialTheme.colorScheme.outlineVariant
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickActionTile(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        Column(
            modifier = Modifier.padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 6.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
