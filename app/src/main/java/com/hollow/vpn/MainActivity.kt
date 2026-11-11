package com.hollow.vpn

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hollow.vpn.ui.VpnScreen
import com.hollow.vpn.ui.VpnViewModel
import com.hollow.vpn.ui.screens.AddServerScreen
import com.hollow.vpn.ui.screens.CountryServerListScreen
import com.hollow.vpn.ui.screens.ConnectedHomeScreen
import com.hollow.vpn.ui.screens.RawXrayJsonEditorDialog
import com.hollow.vpn.ui.screens.SettingsScreen
import com.hollow.vpn.ui.screens.SplashFetchingScreen
import com.hollow.vpn.ui.theme.ForestEmerald
import com.hollow.vpn.ui.theme.HollowVpnTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HollowVpnTheme {
                HollowVpnApp()
            }
        }
    }
}

@Composable
fun HollowVpnApp(
    vpnViewModel: VpnViewModel = viewModel()
) {
    val context = LocalContext.current
    val currentScreen by vpnViewModel.currentScreen.collectAsStateWithLifecycle()
    val filteredServers by vpnViewModel.filteredServers.collectAsStateWithLifecycle()
    val selectedServer by vpnViewModel.selectedServer.collectAsStateWithLifecycle()
    val telemetry by vpnViewModel.telemetry.collectAsStateWithLifecycle()
    val settings by vpnViewModel.settings.collectAsStateWithLifecycle()
    val fetchingStatus by vpnViewModel.fetchingStatusMessage.collectAsStateWithLifecycle()
    val snackbarMessage by vpnViewModel.snackbarMessage.collectAsStateWithLifecycle()
    val activeAddTab by vpnViewModel.activeAddTab.collectAsStateWithLifecycle()
    val inspectingJsonServer by vpnViewModel.inspectingJsonServer.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    // Apply "Hide from Recent Apps" setting dynamically when toggled
    LaunchedEffect(settings.hideFromRecentApps) {
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            am?.appTasks?.firstOrNull()?.setExcludeFromRecents(settings.hideFromRecentApps)
        } catch (_: Exception) {
        }
    }

    LaunchedEffect(snackbarMessage) {
        val msg = snackbarMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        vpnViewModel.clearSnackbar()
    }

    // Android VpnService system consent launcher
    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            vpnViewModel.toggleConnection()
        } else {
            vpnViewModel.showMessage("VPN permission was denied")
        }
    }

    val requestOrToggleVpn = {
        val prepareIntent = try {
            if (!telemetry.isConnected) VpnService.prepare(context) else null
        } catch (_: Exception) {
            null
        }
        if (prepareIntent != null && context is Activity) {
            vpnPermissionLauncher.launch(prepareIntent)
        } else {
            vpnViewModel.toggleConnection()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Crossfade(
            targetState = currentScreen,
            animationSpec = tween(durationMillis = 220),
            label = "vpn_screen_crossfade"
        ) { screen ->
            when (screen) {
                VpnScreen.SPLASH_FETCHING -> {
                    SplashFetchingScreen(
                        statusSubtitle = fetchingStatus,
                        onCancelOrSkip = {
                            vpnViewModel.navigateTo(VpnScreen.SERVER_LIST)
                        }
                    )
                }

                VpnScreen.SERVER_LIST -> {
                    CountryServerListScreen(
                        servers = filteredServers,
                        onSelectServer = { server ->
                            val prepareIntent = try {
                                VpnService.prepare(context)
                            } catch (_: Exception) {
                                null
                            }
                            if (prepareIntent != null && context is Activity) {
                                // Persist the selection, but do not start the service until the
                                // Android VPN consent activity returns RESULT_OK.
                                vpnViewModel.selectServer(server, openConnectedScreen = true)
                                vpnPermissionLauncher.launch(prepareIntent)
                            } else {
                                vpnViewModel.selectAndConnectServer(
                                    server = server,
                                    openConnectedScreen = true
                                )
                            }
                        },
                        onRefresh = {
                            vpnViewModel.runFletcherAutoFetchAndPingSweep(
                                returnScreen = VpnScreen.SERVER_LIST
                            )
                        },
                        onOpenSettings = {
                            vpnViewModel.navigateTo(VpnScreen.SETTINGS)
                        },
                        onOpenAddServer = {
                            vpnViewModel.navigateTo(VpnScreen.ADD_SERVER)
                        },
                        onPingServer = vpnViewModel::pingSingleServer,
                        onInspectJson = vpnViewModel::openJsonInspector,
                        onDeleteServer = vpnViewModel::deleteServer,
                        onBack = {
                            if (!vpnViewModel.navigateBack()) {
                                vpnViewModel.navigateTo(VpnScreen.CONNECTED_HOME)
                            }
                        }
                    )
                }

                VpnScreen.CONNECTED_HOME -> {
                    ConnectedHomeScreen(
                        selectedServer = selectedServer,
                        telemetry = telemetry,
                        onToggleConnect = requestOrToggleVpn,
                        onOpenServerList = {
                            vpnViewModel.navigateTo(VpnScreen.SERVER_LIST)
                        },
                        onOpenSettings = {
                            vpnViewModel.navigateTo(VpnScreen.SETTINGS)
                        },
                        onOpenAddServer = {
                            vpnViewModel.navigateTo(VpnScreen.ADD_SERVER)
                        }
                    )
                }

                VpnScreen.SETTINGS -> {
                    SettingsScreen(
                        settings = settings,
                        onUpdateSettings = vpnViewModel::updateSettings,
                        onOpenAddSubscription = {
                            vpnViewModel.navigateTo(VpnScreen.ADD_SERVER)
                        },
                        onClearCache = vpnViewModel::clearServerCacheAndReset,
                        onBack = {
                            if (!vpnViewModel.navigateBack()) {
                                vpnViewModel.navigateTo(VpnScreen.CONNECTED_HOME)
                            }
                        }
                    )
                }

                VpnScreen.ADD_SERVER -> {
                    AddServerScreen(
                        activeTab = activeAddTab,
                        settings = settings,
                        onSelectTab = vpnViewModel::setAddServerTab,
                        onAddAndFetchSubscription = vpnViewModel::addAndFetchSubscriptionUrl,
                        onImportClipboardText = vpnViewModel::importFromTextOrClipboard,
                        onAddManualServer = vpnViewModel::addManualCustomServer,
                        onUpdateSettings = vpnViewModel::updateSettings,
                        onBack = {
                            if (!vpnViewModel.navigateBack()) {
                                vpnViewModel.navigateTo(VpnScreen.SERVER_LIST)
                            }
                        }
                    )
                }
            }
        }

        if (currentScreen == VpnScreen.CONNECTED_HOME || currentScreen == VpnScreen.SERVER_LIST) {
            FloatingNavigationPill(
                selectedScreen = currentScreen,
                onSelect = { vpnViewModel.navigateTo(it) }
            )
        }

        inspectingJsonServer?.let { server ->
            RawXrayJsonEditorDialog(
                server = server,
                onDismiss = vpnViewModel::closeJsonInspector,
                onSaveJson = { updatedJson ->
                    vpnViewModel.saveEditedServerJson(server, updatedJson)
                }
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) { data ->
            Snackbar(
                shape = RoundedCornerShape(16.dp),
                containerColor = ForestEmerald,
                contentColor = Color.White
            ) {
                Text(text = data.visuals.message)
            }
        }
    }
}

@Composable
private fun BoxScope.FloatingNavigationPill(
    selectedScreen: VpnScreen,
    onSelect: (VpnScreen) -> Unit
) {
    var expanded by remember { mutableStateOf(true) }
    val items = listOf(
        VpnScreen.CONNECTED_HOME to (Icons.Outlined.Dashboard to "Dashboard"),
        VpnScreen.SERVER_LIST to (Icons.Outlined.Dns to "Servers")
    )
    Surface(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .navigationBarsPadding()
            .padding(bottom = 14.dp)
            .animateContentSize(),
        shape = RoundedCornerShape(30.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        tonalElevation = 6.dp,
        shadowElevation = 10.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { index, (screen, item) ->
                val selected = selectedScreen == screen
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .clickable {
                            onSelect(screen)
                            if (!selected) expanded = true
                        },
                    shape = RoundedCornerShape(24.dp),
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.Icon(
                            imageVector = item.first,
                            contentDescription = item.second,
                            tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        if (expanded) {
                            Spacer(Modifier.width(7.dp))
                            Text(
                                text = item.second,
                                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
                if (index == 0) Spacer(Modifier.width(3.dp))
            }
            Spacer(Modifier.width(2.dp))
            Surface(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(50))
                    .clickable { expanded = !expanded },
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                androidx.compose.material3.Icon(
                    imageVector = if (expanded) Icons.Outlined.Dns else Icons.Outlined.Dashboard,
                    contentDescription = if (expanded) "Collapse navigation" else "Expand navigation",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(6.dp)
                )
            }
        }
    }
}
