package dev.sadr.atlas.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.sadr.atlas.R
import dev.sadr.atlas.dto.GroupMapItem
import dev.sadr.atlas.dto.entities.ProfileItem
import dev.sadr.atlas.enums.EConfigType
import dev.sadr.atlas.extension.isComplexType
import dev.sadr.atlas.extension.nullIfBlank
import dev.sadr.atlas.handler.AngConfigManager
import dev.sadr.atlas.handler.MmkvManager
import dev.sadr.atlas.ui.theme.DangerRed
import dev.sadr.atlas.ui.theme.SuccessGreen
import dev.sadr.atlas.ui.theme.WarningAmber
import dev.sadr.atlas.viewmodel.MainViewModel

@Composable
fun ConfigsScreen(
    mainViewModel: MainViewModel,
    groups: List<GroupMapItem>,
    onSelect: (String) -> Unit,
    onMoreClick: (String, ProfileItem, Int) -> Unit,
    onMenuClick: (Int) -> Unit,
    onImportClick: () -> Unit
) {
    val servers by mainViewModel.serversCacheFlow.collectAsStateWithLifecycle()
    val selectedGuid by mainViewModel.selectedGuidFlow.collectAsStateWithLifecycle()
    val testResults by mainViewModel.testResultsFlow.collectAsStateWithLifecycle()
    val currentSubId by mainViewModel.subscriptionIdFlow.collectAsStateWithLifecycle()

    var query by rememberSaveable { mutableStateOf(mainViewModel.keywordFilter) }
    var showOptionsMenu by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp, top = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.title_server),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${servers.size} configs",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            FilledTonalIconButton(onClick = onImportClick) {
                Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.menu_item_add_config))
            }
            IconButton(onClick = { onMenuClick(R.id.real_ping_all) }) {
                Icon(
                    Icons.Rounded.NetworkCheck,
                    contentDescription = stringResource(R.string.title_real_ping_all_server)
                )
            }
            Box {
                IconButton(onClick = { showOptionsMenu = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = null)
                }
                MainOptionsMenu(
                    expanded = showOptionsMenu,
                    onDismiss = { showOptionsMenu = false },
                    onItemClick = onMenuClick
                )
            }
        }

        // Search field
        OutlinedTextField(
            value = query,
            onValueChange = {
                query = it
                mainViewModel.filterConfig(it)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            placeholder = { Text(stringResource(R.string.menu_item_search)) },
            leadingIcon = {
                Icon(
                    Icons.Rounded.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer
            )
        )

        // Group chips
        if (groups.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(groups) { group ->
                    FilterChip(
                        selected = currentSubId == group.id,
                        onClick = { mainViewModel.subscriptionIdChanged(group.id) },
                        label = { Text(group.remarks) },
                        shape = CircleShape,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = currentSubId == group.id,
                            borderColor = MaterialTheme.colorScheme.outlineVariant
                        )
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

        // Data used / expiry for the selected subscription (from its
        // `subscription-userinfo` header). Only real subscriptions carry this;
        // synthetic groups ("My Configs", "Free") decode to null.
        val selectedSub = remember(currentSubId, servers) {
            if (currentSubId.isNotEmpty()) MmkvManager.decodeSubscription(currentSubId) else null
        }
        if (selectedSub != null && selectedSub.hasUsageInfo()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                SubscriptionUsageContent(
                    item = selectedSub,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                )
            }
        }

        if (servers.isEmpty()) {
            EmptyConfigsState(onMenuClick = onMenuClick, onImportClick = onImportClick)
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                itemsIndexed(servers, key = { _, item -> item.guid }) { index, server ->
                    ConfigCard(
                        profile = server.profile,
                        isSelected = server.guid == selectedGuid,
                        testResult = testResults[server.guid].orEmpty(),
                        subscriptionRemarks = subscriptionRemarks(server.profile, mainViewModel.subscriptionId),
                        onSelect = { onSelect(server.guid) },
                        onMoreClick = { onMoreClick(server.guid, server.profile, index) }
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(88.dp))
                }
            }
        }
    }
}

@Composable
private fun EmptyConfigsState(onMenuClick: (Int) -> Unit, onImportClick: () -> Unit) {
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
                imageVector = Icons.Outlined.CloudOff,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "No configs yet",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "Import a config from QR code, clipboard, or a subscription to get started",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onImportClick,
                shape = CircleShape
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add config")
            }
            OutlinedButton(
                onClick = { onMenuClick(R.id.import_qrcode) },
                shape = CircleShape
            ) {
                Icon(Icons.Rounded.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Scan QR")
            }
        }
    }
}

@Composable
private fun ConfigCard(
    profile: ProfileItem,
    isSelected: Boolean,
    testResult: String,
    subscriptionRemarks: String,
    onSelect: () -> Unit,
    onMoreClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable { onSelect() },
        shape = RoundedCornerShape(18.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        else MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                ProtocolAvatar(profile = profile, size = 42.dp)
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .align(Alignment.BottomEnd)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            modifier = Modifier.size(11.dp),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = profile.remarks,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = address(profile),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 1.dp)
                )
                Row(
                    modifier = Modifier.padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TagLabel(text = protocolDescription(profile))
                    if (subscriptionRemarks.isNotEmpty()) {
                        TagLabel(
                            text = subscriptionRemarks,
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            textColor = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            if (testResult.isNotEmpty()) {
                PingBadge(result = testResult)
            }
            IconButton(onClick = onMoreClick) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun TagLabel(
    text: String,
    color: Color = MaterialTheme.colorScheme.secondaryContainer,
    textColor: Color = MaterialTheme.colorScheme.onSecondaryContainer
) {
    Surface(color = color, shape = RoundedCornerShape(6.dp)) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
internal fun PingBadge(result: String) {
    val delayMs = result.takeWhile { it.isDigit() }.toLongOrNull()
    val isTimeout = result == "timeout"
    val color = when {
        isTimeout -> DangerRed
        delayMs == null -> MaterialTheme.colorScheme.onSurfaceVariant
        delayMs < 1000 -> SuccessGreen
        delayMs < 3000 -> WarningAmber
        else -> DangerRed
    }
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = CircleShape
    ) {
        Text(
            text = result,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
internal fun ProtocolAvatar(profile: ProfileItem, size: Dp) {
    val (short, colors) = when (profile.configType) {
        EConfigType.VMESS -> "VM" to listOf(Color(0xFF4C7DFF), Color(0xFF2F5BE7))
        EConfigType.VLESS -> "VL" to listOf(Color(0xFF8B5CF6), Color(0xFF6D28D9))
        EConfigType.SHADOWSOCKS -> "SS" to listOf(Color(0xFF14B8A6), Color(0xFF0F766E))
        EConfigType.TROJAN -> "TR" to listOf(Color(0xFFF97316), Color(0xFFC2410C))
        EConfigType.WIREGUARD -> "WG" to listOf(Color(0xFFF59E0B), Color(0xFFB45309))
        EConfigType.HYSTERIA2 -> "HY" to listOf(Color(0xFFEC4899), Color(0xFFBE185D))
        EConfigType.SOCKS -> "SO" to listOf(Color(0xFF64748B), Color(0xFF475569))
        EConfigType.HTTP -> "HT" to listOf(Color(0xFF64748B), Color(0xFF475569))
        else -> profile.configType.name.take(2) to listOf(Color(0xFF6B7280), Color(0xFF4B5563))
    }
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.linearGradient(colors)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = short,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

@Composable
fun MainOptionsMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onItemClick: (Int) -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(14.dp)
    ) {
        val entries = listOf(
            R.id.sub_update to R.string.title_sub_update,
            R.id.real_ping_all to R.string.title_real_ping_all_server,
            R.id.sort_by_test_results to R.string.title_sort_by_test_results,
            R.id.auto_best_config to R.string.title_auto_best_config,
            R.id.service_restart to R.string.title_service_restart
        )
        entries.forEach { (id, title) ->
            DropdownMenuItem(
                text = { Text(stringResource(title)) },
                onClick = { onItemClick(id); onDismiss() }
            )
        }
        HorizontalDivider()
        val cleanupEntries = listOf(
            R.id.del_duplicate_config to R.string.title_del_duplicate_config,
            R.id.del_invalid_config to R.string.title_del_invalid_config,
            R.id.del_all_config to R.string.title_del_all_config
        )
        cleanupEntries.forEach { (id, title) ->
            DropdownMenuItem(
                text = { Text(stringResource(title)) },
                onClick = { onItemClick(id); onDismiss() }
            )
        }
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text(stringResource(R.string.title_export_all)) },
            onClick = { onItemClick(R.id.export_all); onDismiss() }
        )
    }
}

// Logic helpers

internal fun address(profile: ProfileItem): String {
    return profile.description.nullIfBlank() ?: AngConfigManager.generateDescription(profile)
}

internal fun subscriptionRemarks(profile: ProfileItem, subscriptionId: String): String {
    val subRemarks =
        if (subscriptionId.isEmpty())
            MmkvManager.decodeSubscription(profile.subscriptionId)?.remarks?.firstOrNull()
        else
            null
    return subRemarks?.toString() ?: ""
}

internal fun protocolDescription(profile: ProfileItem): String {
    if (profile.configType.isComplexType()) {
        return profile.configType.name
    }

    val parts = mutableListOf<String>()
    parts.add(profile.configType.name)

    profile.network?.let { net ->
        if (net.isNotBlank() && !net.equals("tcp", ignoreCase = true)) {
            parts.add(net)
        }
    }

    profile.security?.let { sec ->
        if (sec.isNotBlank()) {
            if (profile.insecure == true && sec.equals("tls", ignoreCase = true)) {
                parts.add("$sec insecure")
            } else {
                parts.add(sec)
            }
        }
    }

    return parts.joinToString(" / ")
}
