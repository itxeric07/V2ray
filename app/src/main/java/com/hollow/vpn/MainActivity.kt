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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.hollow.vpn.ui.screens.ConnectedHomeScreen
import com.hollow.vpn.ui.screens.RawXrayJsonEditorDialog
import com.hollow.vpn.ui.screens.ServerListScreen
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
    val selectedFilter by vpnViewModel.selectedFilter.collectAsStateWithLifecycle()
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
                    ServerListScreen(
                        servers = filteredServers,
                        selectedFilter = selectedFilter,
                        onSelectFilter = vpnViewModel::setFilter,
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
                        onRefreshFletcher = {
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
