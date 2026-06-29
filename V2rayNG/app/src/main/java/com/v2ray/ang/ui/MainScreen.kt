package com.v2ray.ang.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import com.v2ray.ang.viewmodel.MainViewModel
import kotlinx.coroutines.launch

sealed class Screen(val route: String, val title: Int, val icon: ImageVector, val selectedIcon: ImageVector) {
    object Home : Screen("home", R.string.title_server, Icons.Outlined.Home, Icons.Filled.Home)
    object Settings : Screen("settings", R.string.title_settings, Icons.Outlined.Settings, Icons.Filled.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    mainViewModel: MainViewModel,
    isRunning: Boolean,
    onFabClick: () -> Unit,
    onMenuClick: (Int) -> Unit,
    onDrawerItemClick: (Int) -> Unit,
    onSelectServer: (String) -> Unit,
    onMoreClick: (String, com.v2ray.ang.dto.entities.ProfileItem, Int) -> Unit
) {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showImportMenu by remember { mutableStateOf(false) }
    var showOptionsMenu by remember { mutableStateOf(false) }
    
    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = currentScreen == Screen.Home,
        drawerContent = {
            ModalDrawerSheet {
                DrawerHeader()
                Spacer(Modifier.height(12.dp))
                DrawerContent(onDrawerItemClick = {
                    onDrawerItemClick(it)
                    scope.launch { drawerState.close() }
                })
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(currentScreen.title)) },
                    navigationIcon = {
                        if (currentScreen == Screen.Home) {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = null)
                            }
                        }
                    },
                    actions = {
                        if (currentScreen == Screen.Home) {
                            Box {
                                IconButton(onClick = { showImportMenu = true }) {
                                    Icon(painterResource(R.drawable.ic_add_24dp), contentDescription = null)
                                }
                                ImportConfigDropdownMenu(
                                    expanded = showImportMenu,
                                    onDismiss = { showImportMenu = false },
                                    onItemClick = onMenuClick
                                )
                            }
                            Box {
                                IconButton(onClick = { showOptionsMenu = true }) {
                                    Icon(painterResource(R.drawable.ic_more_vert_24dp), contentDescription = null)
                                }
                                MainOptionsMenu(
                                    expanded = showOptionsMenu,
                                    onDismiss = { showOptionsMenu = false },
                                    onItemClick = onMenuClick
                                )
                            }
                        }
                    }
                )
            },
            bottomBar = {
                NavigationBar {
                    val items = listOf(Screen.Home, Screen.Settings)
                    items.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(if (currentScreen == screen) screen.selectedIcon else screen.icon, contentDescription = null) },
                            label = { Text(stringResource(screen.title)) },
                            selected = currentScreen == screen,
                            onClick = { currentScreen = screen }
                        )
                    }
                }
            }
        ) { padding ->
            Box(modifier = Modifier.padding(padding)) {
                when (currentScreen) {
                    Screen.Home -> HomeContent(
                        mainViewModel = mainViewModel,
                        isRunning = isRunning,
                        onFabClick = onFabClick,
                        onSelectServer = onSelectServer,
                        onMoreClick = onMoreClick
                    )
                    Screen.Settings -> SettingsContent()
                }
            }
        }
    }
}

@Composable
fun HomeContent(
    mainViewModel: MainViewModel,
    isRunning: Boolean,
    onFabClick: () -> Unit,
    onSelectServer: (String) -> Unit,
    onMoreClick: (String, com.v2ray.ang.dto.entities.ProfileItem, Int) -> Unit
) {
    val isLoading by mainViewModel.isLoadingFlow.collectAsStateWithLifecycle()
    val autoBestProgress by mainViewModel.autoBestProgressFlow.collectAsStateWithLifecycle()

    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val isShortScreen = screenHeight < 640.dp
    
    val buttonSize = if (isShortScreen) 120.dp else 160.dp
    val ringSize = if (isShortScreen) 150.dp else 200.dp
    val topPadding = if (isShortScreen) 8.dp else 16.dp
    val itemSpacing = if (isShortScreen) 16.dp else 32.dp

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Main Connection Area - Give it more space and allow internal scrolling if needed
        Box(
            modifier = Modifier
                .weight(if (isShortScreen) 1f else 1.5f)
                .fillMaxWidth()
                .padding(top = topPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Connection Status
                Text(
                    text = when {
                        isLoading -> "SEARCHING..."
                        isRunning -> "CONNECTED"
                        else -> "DISCONNECTED"
                    },
                    style = if (isShortScreen) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        isRunning -> Color(0xFF4CAF50)
                        isLoading -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.outline
                    }
                )
                
                Spacer(modifier = Modifier.height(itemSpacing))

                // Connection Button and Progress Ring
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(vertical = if (isShortScreen) 8.dp else 16.dp)
                ) {
                    // Outer Progress / Status Ring
                    if (isLoading || isRunning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(ringSize), // slightly larger than button
                            progress = { if (isRunning) 1f else 0.7f },
                            color = if (isRunning) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary,
                            strokeWidth = if (isShortScreen) 3.dp else 4.dp,
                            trackColor = if (isRunning) Color(0xFF4CAF50).copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant,
                            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                        )
                    }

                    ConnectionCircleButton(
                        isRunning = isRunning,
                        isLoading = isLoading,
                        size = buttonSize,
                        onClick = onFabClick
                    )
                }

                // New Design for Progress Stats
                if (isLoading && autoBestProgress.isNotEmpty()) {
                    Card(
                        modifier = Modifier
                            .padding(top = if (isShortScreen) 12.dp else 24.dp)
                            .padding(horizontal = 32.dp),
                        shape = CircleShape,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = autoBestProgress,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else if (isRunning) {
                    Text(
                        text = "SECURE CONNECTION ACTIVE",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF4CAF50),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = if (isShortScreen) 12.dp else 24.dp)
                    )
                }

                Spacer(modifier = Modifier.height(itemSpacing))
            }
        }

        // Server List at the bottom
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            ServerListScreen(
                mainViewModel = mainViewModel,
                onSelect = onSelectServer,
                onMoreClick = onMoreClick
            )
        }
    }
}

@Composable
fun ConnectionCircleButton(
    isRunning: Boolean,
    isLoading: Boolean,
    size: androidx.compose.ui.unit.Dp = 160.dp,
    onClick: () -> Unit
) {
    val color = when {
        isLoading -> MaterialTheme.colorScheme.primaryContainer
        isRunning -> Color(0xFF4CAF50)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = listOf(color.copy(alpha = 0.8f), color)
                )
            )
            .border(
                width = 8.dp,
                color = color.copy(alpha = 0.3f),
                shape = CircleShape
            )
            .clickable(enabled = !isLoading) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(size * 0.6f),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                strokeWidth = 4.dp
            )
        } else {
            Icon(
                painter = painterResource(if (isRunning) R.drawable.ic_stop_24dp else R.drawable.ic_play_24dp),
                contentDescription = null,
                modifier = Modifier.size(size * 0.4f),
                tint = if (isRunning) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SettingsContent() {
    AndroidView(
        factory = { ctx ->
            FragmentContainerView(ctx).apply {
                id = R.id.fragment_settings
                val fragmentActivity = ctx as? FragmentActivity
                fragmentActivity?.supportFragmentManager?.beginTransaction()
                    ?.replace(id, SettingsFragment())
                    ?.commit()
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
fun DrawerHeader() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .background(MaterialTheme.colorScheme.primary)
            .padding(24.dp),
        verticalArrangement = Arrangement.Bottom
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.onPrimary
        )
        Text(
            text = "Secure Proxy Client",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
fun DrawerContent(onDrawerItemClick: (Int) -> Unit) {
    val items = listOf(
        DrawerItemData(R.id.sub_setting, R.drawable.ic_subscriptions_24dp, R.string.title_sub_setting),
        DrawerItemData(R.id.per_app_proxy_settings, R.drawable.ic_per_apps_24dp, R.string.per_app_proxy_settings),
        DrawerItemData(R.id.routing_setting, R.drawable.ic_routing_24dp, R.string.routing_settings_title),
        DrawerItemData(R.id.user_asset_setting, R.drawable.ic_file_24dp, R.string.title_user_asset_setting),
        null, // Divider
        DrawerItemData(R.id.promotion, R.drawable.ic_promotion_24dp, R.string.title_pref_promotion),
        DrawerItemData(R.id.logcat, R.drawable.ic_logcat_24dp, R.string.title_logcat),
        DrawerItemData(R.id.check_for_update, R.drawable.ic_check_update_24dp, R.string.update_check_for_update),
        DrawerItemData(R.id.backup_restore, R.drawable.ic_restore_24dp, R.string.title_configuration_backup_restore),
        DrawerItemData(R.id.about, R.drawable.ic_about_24dp, R.string.title_about)
    )

    Column(modifier = Modifier.padding(12.dp)) {
        items.forEach { item ->
            if (item == null) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            } else {
                NavigationDrawerItem(
                    icon = { Icon(painterResource(item.icon), contentDescription = null) },
                    label = { Text(stringResource(item.title)) },
                    selected = false,
                    onClick = { onDrawerItemClick(item.id) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
            }
        }
    }
}

data class DrawerItemData(val id: Int, val icon: Int, val title: Int)

@Composable
fun ImportConfigDropdownMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onItemClick: (Int) -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss
    ) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.menu_item_import_config_qrcode)) },
            onClick = { onItemClick(R.id.import_qrcode); onDismiss() }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.menu_item_import_config_clipboard)) },
            onClick = { onItemClick(R.id.import_clipboard); onDismiss() }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.menu_item_import_config_local)) },
            onClick = { onItemClick(R.id.import_local); onDismiss() }
        )
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text(stringResource(R.string.menu_item_import_config_manually_vmess)) },
            onClick = { onItemClick(R.id.import_manually_vmess); onDismiss() }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.menu_item_import_config_manually_vless)) },
            onClick = { onItemClick(R.id.import_manually_vless); onDismiss() }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.menu_item_import_config_manually_ss)) },
            onClick = { onItemClick(R.id.import_manually_ss); onDismiss() }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.menu_item_import_config_manually_trojan)) },
            onClick = { onItemClick(R.id.import_manually_trojan); onDismiss() }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.menu_item_import_config_manually_hysteria2)) },
            onClick = { onItemClick(R.id.import_manually_hysteria2); onDismiss() }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.menu_item_import_config_manually_wireguard)) },
            onClick = { onItemClick(R.id.import_manually_wireguard); onDismiss() }
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
        onDismissRequest = onDismiss
    ) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.title_sub_update)) },
            onClick = { onItemClick(R.id.sub_update); onDismiss() }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.title_real_ping_all_server)) },
            onClick = { onItemClick(R.id.real_ping_all); onDismiss() }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.title_sort_by_test_results)) },
            onClick = { onItemClick(R.id.sort_by_test_results); onDismiss() }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.title_service_restart)) },
            onClick = { onItemClick(R.id.service_restart); onDismiss() }
        )
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text(stringResource(R.string.title_del_duplicate_config)) },
            onClick = { onItemClick(R.id.del_duplicate_config); onDismiss() }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.title_del_invalid_config)) },
            onClick = { onItemClick(R.id.del_invalid_config); onDismiss() }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.title_del_all_config)) },
            onClick = { onItemClick(R.id.del_all_config); onDismiss() }
        )
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text(stringResource(R.string.title_export_all)) },
            onClick = { onItemClick(R.id.export_all); onDismiss() }
        )
    }
}
