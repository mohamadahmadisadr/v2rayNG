package dev.sadr.atlas.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.sadr.atlas.BuildConfig
import dev.sadr.atlas.R
import dev.sadr.atlas.dto.GroupMapItem
import dev.sadr.atlas.dto.entities.ProfileItem
import dev.sadr.atlas.ui.theme.AccentBlue
import dev.sadr.atlas.ui.theme.AccentBlueDeep
import dev.sadr.atlas.ui.theme.SuccessGreen
import dev.sadr.atlas.ui.theme.SuccessGreenDeep
import dev.sadr.atlas.ui.theme.WarningAmber
import dev.sadr.atlas.viewmodel.MainViewModel
import kotlinx.coroutines.delay

private enum class MainTab(val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    Home("Home", Icons.Outlined.Home, Icons.Rounded.Home),
    Configs("Configs", Icons.Outlined.Dns, Icons.Rounded.Dns),
    Settings("Settings", Icons.Outlined.Settings, Icons.Rounded.Settings)
}

@Composable
fun MainScreen(
    mainViewModel: MainViewModel,
    isRunning: Boolean,
    groups: List<GroupMapItem>,
    tabIndex: Int,
    onTabChange: (Int) -> Unit,
    onConnectClick: () -> Unit,
    onAutoBestClick: () -> Unit,
    onMenuClick: (Int) -> Unit,
    onNavItemClick: (Int) -> Unit,
    onSelectServer: (String) -> Unit,
    onMoreClick: (String, ProfileItem, Int) -> Unit,
    onImportClick: () -> Unit
) {
    val currentTab = MainTab.entries[tabIndex.coerceIn(0, MainTab.entries.lastIndex)]

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 0.dp
            ) {
                MainTab.entries.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        icon = {
                            Icon(
                                if (currentTab == tab) tab.selectedIcon else tab.icon,
                                contentDescription = tab.label
                            )
                        },
                        label = { Text(tab.label) },
                        selected = currentTab == tab,
                        onClick = { onTabChange(index) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (currentTab) {
                MainTab.Home -> HomeScreen(
                    mainViewModel = mainViewModel,
                    isRunning = isRunning,
                    onConnectClick = onConnectClick,
                    onAutoBestClick = onAutoBestClick,
                    onGoToConfigs = { onTabChange(MainTab.Configs.ordinal) }
                )

                MainTab.Configs -> ConfigsScreen(
                    mainViewModel = mainViewModel,
                    groups = groups,
                    onSelect = onSelectServer,
                    onMoreClick = onMoreClick,
                    onMenuClick = onMenuClick,
                    onImportClick = onImportClick
                )

                MainTab.Settings -> SettingsScreen(onNavItemClick = onNavItemClick)
            }
        }
    }
}

// region Home

@Composable
private fun HomeScreen(
    mainViewModel: MainViewModel,
    isRunning: Boolean,
    onConnectClick: () -> Unit,
    onAutoBestClick: () -> Unit,
    onGoToConfigs: () -> Unit
) {
    val isLoading by mainViewModel.isLoadingFlow.collectAsStateWithLifecycle()
    val autoBestProgress by mainViewModel.autoBestProgressFlow.collectAsStateWithLifecycle()
    val selectedProfile by mainViewModel.selectedProfileFlow.collectAsStateWithLifecycle()
    val selectedGuid by mainViewModel.selectedGuidFlow.collectAsStateWithLifecycle()
    val testResults by mainViewModel.testResultsFlow.collectAsStateWithLifecycle()
    val connectedAt by mainViewModel.connectedAtFlow.collectAsStateWithLifecycle()
    val downSpeed by mainViewModel.downSpeedFlow.collectAsStateWithLifecycle()
    val upSpeed by mainViewModel.upSpeedFlow.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.weight(1f))
            StatusPill(isRunning = isRunning, isLoading = isLoading)
        }

        Spacer(modifier = Modifier.height(36.dp))

        PowerButton(
            isRunning = isRunning,
            isLoading = isLoading,
            onClick = onConnectClick
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = when {
                isRunning -> "Connected"
                isLoading -> "Connecting…"
                else -> "Not Connected"
            },
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = when {
                isRunning -> MaterialTheme.colorScheme.tertiary
                isLoading -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
        )

        if (isRunning && connectedAt > 0L) {
            ConnectionTimer(connectedAt = connectedAt)
        }

        // Auto-best progress while searching
        AnimatedVisibility(visible = isLoading && autoBestProgress.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
            Surface(
                modifier = Modifier.padding(top = 12.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Bolt,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = autoBestProgress,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Speed stats when connected
        AnimatedVisibility(visible = isRunning, enter = fadeIn(), exit = fadeOut()) {
            SpeedStatsCard(downSpeed = downSpeed, upSpeed = upSpeed)
        }

        if (!isRunning) {
            OutlinedButton(
                onClick = onAutoBestClick,
                shape = CircleShape,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Bolt,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = WarningAmber
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isLoading) "Stop searching" else "Free servers",
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        SelectedServerCard(
            profile = selectedProfile,
            ping = testResults[selectedGuid].orEmpty(),
            onClick = onGoToConfigs
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun StatusPill(isRunning: Boolean, isLoading: Boolean) {
    val color = when {
        isRunning -> MaterialTheme.colorScheme.tertiary
        isLoading -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        shape = CircleShape,
        color = color.copy(alpha = 0.12f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = when {
                    isRunning -> "Active"
                    isLoading -> "Busy"
                    else -> "Idle"
                },
                style = MaterialTheme.typography.labelMedium,
                color = color
            )
        }
    }
}

@Composable
private fun PowerButton(
    isRunning: Boolean,
    isLoading: Boolean,
    onClick: () -> Unit
) {
    val infinite = rememberInfiniteTransition(label = "power")
    val pulse by infinite.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val glowColor by animateColorAsState(
        targetValue = when {
            isRunning -> SuccessGreen
            isLoading -> AccentBlue
            else -> MaterialTheme.colorScheme.outline
        },
        animationSpec = tween(400),
        label = "glow"
    )

    Box(modifier = Modifier.size(232.dp), contentAlignment = Alignment.Center) {
        // Soft outer glow
        Box(
            modifier = Modifier
                .size(212.dp)
                .scale(if (isRunning || isLoading) pulse else 1f)
                .clip(CircleShape)
                .background(glowColor.copy(alpha = if (isRunning || isLoading) 0.16f else 0.08f))
        )

        Box(
            modifier = Modifier
                .size(168.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = when {
                            isRunning -> listOf(Color(0xFF34D399), SuccessGreenDeep)
                            isLoading -> listOf(AccentBlue, AccentBlueDeep)
                            else -> listOf(
                                MaterialTheme.colorScheme.surfaceContainerHighest,
                                MaterialTheme.colorScheme.surfaceContainer
                            )
                        }
                    )
                )
                .border(
                    width = 1.dp,
                    color = if (isRunning || isLoading) Color.Transparent
                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    shape = CircleShape
                )
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.PowerSettingsNew,
                contentDescription = null,
                modifier = Modifier.size(60.dp),
                tint = if (isRunning || isLoading) Color.White
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(194.dp),
                color = AccentBlue,
                strokeWidth = 3.dp,
                strokeCap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun ConnectionTimer(connectedAt: Long) {
    var elapsed by remember { mutableLongStateOf(System.currentTimeMillis() - connectedAt) }
    LaunchedEffect(connectedAt) {
        while (true) {
            elapsed = System.currentTimeMillis() - connectedAt
            delay(1000)
        }
    }
    val totalSec = (elapsed / 1000).coerceAtLeast(0)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    Text(
        text = if (h > 0) "%02d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun SpeedStatsCard(downSpeed: String, upSpeed: String) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 20.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SpeedStat(
                icon = Icons.Rounded.ArrowDownward,
                label = "Download",
                value = downSpeed,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(36.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
            SpeedStat(
                icon = Icons.Rounded.ArrowUpward,
                label = "Upload",
                value = upSpeed,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SpeedStat(
    icon: ImageVector,
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = color)
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SelectedServerCard(
    profile: ProfileItem?,
    ping: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (profile == null) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "No config selected",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Import or pick a server to get started",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                ProtocolAvatar(profile = profile, size = 44.dp)
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
                        text = protocolDescription(profile),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (ping.isNotEmpty()) {
                    PingBadge(result = ping)
                    Spacer(modifier = Modifier.width(4.dp))
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// endregion

// region Settings tab

private data class SettingsEntry(val id: Int, val icon: Int?, val title: String, val subtitle: String? = null)

@Composable
fun SettingsScreen(onNavItemClick: (Int) -> Unit) {
    val configurationItems = listOf(
        SettingsEntry(R.id.core_settings, null, "VPN & Core Settings", "VPN, DNS, mux, core options"),
        SettingsEntry(R.id.sub_setting, R.drawable.ic_subscriptions_24dp, stringResource(R.string.title_sub_setting)),
        SettingsEntry(R.id.per_app_proxy_settings, R.drawable.ic_per_apps_24dp, stringResource(R.string.per_app_proxy_settings)),
        SettingsEntry(R.id.routing_setting, R.drawable.ic_routing_24dp, stringResource(R.string.routing_settings_title)),
        SettingsEntry(R.id.user_asset_setting, R.drawable.ic_file_24dp, stringResource(R.string.title_user_asset_setting))
    )
    val toolsItems = listOf(
        SettingsEntry(R.id.backup_restore, R.drawable.ic_restore_24dp, stringResource(R.string.title_configuration_backup_restore)),
        SettingsEntry(R.id.logcat, R.drawable.ic_logcat_24dp, stringResource(R.string.title_logcat)),
        SettingsEntry(R.id.check_for_update, R.drawable.ic_check_update_24dp, stringResource(R.string.update_check_for_update)),
        SettingsEntry(R.id.about, R.drawable.ic_about_24dp, stringResource(R.string.title_about))
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Text(
            text = stringResource(R.string.title_settings),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 16.dp, bottom = 16.dp)
        )

        SettingsSection(title = "Configuration", items = configurationItems, onNavItemClick = onNavItemClick)
        Spacer(modifier = Modifier.height(20.dp))
        SettingsSection(title = "Tools", items = toolsItems, onNavItemClick = onNavItemClick)

        Text(
            text = "v${BuildConfig.VERSION_NAME} (${BuildConfig.DISTRIBUTION})",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(vertical = 24.dp)
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    items: List<SettingsEntry>,
    onNavItemClick: (Int) -> Unit
) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
    )
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column {
            items.forEachIndexed { index, entry ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavItemClick(entry.id) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (entry.icon != null) {
                            Icon(
                                painter = painterResource(entry.icon),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Settings,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = entry.title,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (entry.subtitle != null) {
                            Text(
                                text = entry.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (index < items.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 66.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

// endregion
