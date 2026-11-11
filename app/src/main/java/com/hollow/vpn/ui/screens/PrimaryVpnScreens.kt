package com.hollow.vpn.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hollow.vpn.R
import com.hollow.vpn.data.ServerEntity
import com.hollow.vpn.data.ServerFilter
import com.hollow.vpn.ui.ConnectionTelemetryState
import com.hollow.vpn.ui.components.CircularCountryFlag
import com.hollow.vpn.ui.components.FetchingSpinnerRing
import com.hollow.vpn.ui.components.OrganicEmeraldPowerOrb
import com.hollow.vpn.ui.components.SageOrganicBackground
import com.hollow.vpn.ui.components.SignalStrengthBars
import com.hollow.vpn.ui.components.HollowVpnLogoCard
import com.hollow.vpn.ui.theme.DisconnectCrimson
import com.hollow.vpn.ui.theme.DisconnectPillBg
import com.hollow.vpn.ui.theme.DividerSage
import com.hollow.vpn.ui.theme.EmeraldAccent
import com.hollow.vpn.ui.theme.ForestEmerald
import com.hollow.vpn.ui.theme.PureWhiteCard
import com.hollow.vpn.ui.theme.SelectedMintCard
import com.hollow.vpn.ui.theme.SubtleCardBorder
import com.hollow.vpn.ui.theme.TextPrimaryDark
import com.hollow.vpn.ui.theme.TextSecondaryMuted

/**
 * SCREEN 1: Splash / Auto-Fetching Servers Screen ("Hollow VPN — Fast. Private. Open Internet.")
 * Matches the leftmost phone in the user's reference screenshot.
 */
@Composable
fun SplashFetchingScreen(
    statusSubtitle: String,
    onCancelOrSkip: () -> Unit
) {
    BackHandler { onCancelOrSkip() }

    SageOrganicBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.weight(0.95f))

            HollowVpnLogoCard(cardSize = 88.dp)

            Spacer(modifier = Modifier.height(22.dp))

            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 25.sp
                ),
                color = TextPrimaryDark
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = stringResource(R.string.tagline_fast_private),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                color = TextSecondaryMuted
            )

            Spacer(modifier = Modifier.height(52.dp))

            FetchingSpinnerRing(size = 44.dp)

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = stringResource(R.string.fetching_servers_title),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                ),
                color = TextPrimaryDark
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = statusSubtitle,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                color = TextSecondaryMuted
            )

            Spacer(modifier = Modifier.weight(1.1f))
        }
    }
}

/**
 * SCREEN 2: Server List Screen ("VPN — Auto fetched servers")
 * Matches the 2nd phone in the user's reference screenshot, listing the Top 15 fastest servers.
 */
@Composable
fun ServerListScreen(
    servers: List<ServerEntity>,
    selectedFilter: ServerFilter,
    onSelectFilter: (ServerFilter) -> Unit,
    onSelectServer: (ServerEntity) -> Unit,
    onRefreshFletcher: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAddServer: () -> Unit,
    onPingServer: (ServerEntity) -> Unit,
    onInspectJson: (ServerEntity) -> Unit,
    onDeleteServer: (ServerEntity) -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    SageOrganicBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .widthIn(max = 600.dp)
                .align(Alignment.TopCenter)
                .padding(horizontal = 18.dp)
        ) {
            Spacer(modifier = Modifier.height(14.dp))

            // Top Header Row: "VPN" + "Auto fetched servers" & circular Refresh / Settings buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.vpn_header_title),
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 26.sp
                        ),
                        color = TextPrimaryDark
                    )
                    Text(
                        text = stringResource(R.string.vpn_header_subtitle),
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp),
                        color = TextSecondaryMuted
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularHeaderIconButton(
                        icon = Icons.Rounded.Add,
                        contentDescription = "Add Custom Server",
                        testTag = "btn_open_add_server",
                        onClick = onOpenAddServer
                    )
                    CircularHeaderIconButton(
                        icon = Icons.Rounded.Refresh,
                        contentDescription = "Auto Fetch & Ping Top 15 Servers",
                        testTag = "btn_refresh_fletcher",
                        onClick = onRefreshFletcher
                    )
                    CircularHeaderIconButton(
                        icon = Icons.Rounded.Settings,
                        contentDescription = "Open Settings",
                        testTag = "btn_open_settings",
                        onClick = onOpenSettings
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Filter Pills Row: All | Recommended | Low Ping | High Speed
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ServerFilter.entries.forEach { filter ->
                    val isSelected = filter == selectedFilter
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { onSelectFilter(filter) }
                            .testTag("filter_chip_${filter.name.lowercase()}"),
                        shape = RoundedCornerShape(50),
                        color = if (isSelected) ForestEmerald else PureWhiteCard.copy(alpha = 0.85f),
                        border = if (isSelected) null else BorderStroke(1.dp, SubtleCardBorder)
                    ) {
                        Text(
                            text = filter.label,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                            ),
                            color = if (isSelected) Color.White else TextSecondaryMuted,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (servers.isEmpty()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp),
                    shape = RoundedCornerShape(22.dp),
                    color = PureWhiteCard
                ) {
                    Column(
                        modifier = Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No servers match this filter",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimaryDark
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Tap Refresh to auto-fetch and ping-test the Top 15 servers.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondaryMuted
                        )
                    }
                }
            } else {
                val topHighlighted = servers.first()
                val restServers = servers.drop(1)

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("server_lazy_column"),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 1. Highlighted Top/Selected Server Card (Mint Green Card with Lightning Badge)
                    item(key = "top_${topHighlighted.id}") {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(
                                    elevation = 4.dp,
                                    shape = RoundedCornerShape(20.dp),
                                    ambientColor = ForestEmerald.copy(alpha = 0.10f)
                                )
                                .clip(RoundedCornerShape(20.dp))
                                .clickable { onSelectServer(topHighlighted) }
                                .testTag("server_card_featured"),
                            shape = RoundedCornerShape(20.dp),
                            color = SelectedMintCard,
                            border = BorderStroke(1.dp, Color(0xFFC7E8D5))
                        ) {
                            ServerItemRowContent(
                                server = topHighlighted,
                                showLightningBadge = true,
                                onSelect = { onSelectServer(topHighlighted) },
                                onPing = { onPingServer(topHighlighted) },
                                onInspectJson = { onInspectJson(topHighlighted) },
                                onDelete = { onDeleteServer(topHighlighted) }
                            )
                        }
                    }

                    // 2. Remaining Ranked Servers inside a Unified Crisp White Rounded Card
                    if (restServers.isNotEmpty()) {
                        item(key = "grouped_server_card") {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .shadow(
                                        elevation = 6.dp,
                                        shape = RoundedCornerShape(22.dp),
                                        ambientColor = Color.Black.copy(alpha = 0.05f)
                                    ),
                                shape = RoundedCornerShape(22.dp),
                                color = PureWhiteCard
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    restServers.forEachIndexed { index, server ->
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { onSelectServer(server) }
                                                .testTag("server_row_${server.countryCode.lowercase()}_$index")
                                        ) {
                                            ServerItemRowContent(
                                                server = server,
                                                showLightningBadge = false,
                                                onSelect = { onSelectServer(server) },
                                                onPing = { onPingServer(server) },
                                                onInspectJson = { onInspectJson(server) },
                                                onDelete = { onDeleteServer(server) }
                                            )
                                        }
                                        if (index < restServers.lastIndex) {
                                            HorizontalDivider(
                                                modifier = Modifier.padding(horizontal = 16.dp),
                                                thickness = 1.dp,
                                                color = DividerSage
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CircularHeaderIconButton(
    icon: ImageVector,
    contentDescription: String,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .size(40.dp)
            .shadow(
                elevation = 3.dp,
                shape = CircleShape,
                ambientColor = Color.Black.copy(alpha = 0.06f)
            )
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .testTag(testTag),
        shape = CircleShape,
        color = PureWhiteCard.copy(alpha = 0.92f)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = TextPrimaryDark,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun ServerItemRowContent(
    server: ServerEntity,
    showLightningBadge: Boolean,
    onSelect: () -> Unit,
    onPing: () -> Unit,
    onInspectJson: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularCountryFlag(
            countryCode = server.countryCode,
            size = 34.dp
        )

        Spacer(modifier = Modifier.width(13.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = server.countryName,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.5.sp
                ),
                color = TextPrimaryDark,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = server.protocolDisplay,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.2.sp),
                color = TextSecondaryMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "IP: ${server.resolvedIp.ifBlank { server.host }}:${server.port}",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp),
                color = TextSecondaryMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (showLightningBadge) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(EmeraldAccent.copy(alpha = 0.20f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Bolt,
                    contentDescription = "Fastest Recommended Server",
                    tint = EmeraldAccent,
                    modifier = Modifier.size(14.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
        }

        SignalStrengthBars(pingMs = server.pingMs)

        Spacer(modifier = Modifier.width(6.dp))

        Text(
            text = "${server.pingMs} ms",
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Medium,
                fontSize = 12.5.sp
            ),
            color = if (showLightningBadge) EmeraldAccent else TextSecondaryMuted
        )

        Spacer(modifier = Modifier.width(4.dp))

        Box {
            Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = "Server Options",
                tint = TextSecondaryMuted,
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickable { menuExpanded = true }
                    .padding(4.dp)
            )

            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Connect Now") },
                    onClick = {
                        menuExpanded = false
                        onSelect()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Ping Test (${server.pingMs} ms)") },
                    onClick = {
                        menuExpanded = false
                        onPing()
                    }
                )
                DropdownMenuItem(
                    text = { Text("View / Edit Xray JSON") },
                    onClick = {
                        menuExpanded = false
                        onInspectJson()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Delete Server", color = DisconnectCrimson) },
                    onClick = {
                        menuExpanded = false
                        onDelete()
                    }
                )
            }
        }
    }
}

/**
 * SCREEN 3: Connected / Home Tunnel Screen
 * Matches the center phone in the user's reference screenshot pixel-for-pixel.
 */
@Composable
fun ConnectedHomeScreen(
    selectedServer: ServerEntity?,
    telemetry: ConnectionTelemetryState,
    onToggleConnect: () -> Unit,
    onOpenServerList: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAddServer: () -> Unit
) {
    val activeCountryName = selectedServer?.countryName ?: "Select Server"
    val activeCountryCode = selectedServer?.countryCode ?: "DE"
    val activeProtocol = selectedServer?.protocolCore ?: "Auto (V2Ray)"
    val activePingLabel = selectedServer?.let { "${it.pingMs} ms" } ?: "—"
    val cleanServerIp = selectedServer?.resolvedIp?.takeIf { it.isNotBlank() }
        ?: selectedServer?.host?.takeIf { it.isNotBlank() }
        ?: telemetry.serverIpLabel
    val effectiveRealIp = if (telemetry.isConnected && !cleanServerIp.isNullOrBlank() && cleanServerIp != "—") {
        cleanServerIp
    } else {
        telemetry.realPublicIpLabel
    }
    SageOrganicBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .widthIn(max = 560.dp)
                .align(Alignment.TopCenter)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 112.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(10.dp))

            // Subtle top utility bar so Settings and Add Server are always 1 tap away from Home too
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularHeaderIconButton(
                    icon = Icons.Rounded.Add,
                    contentDescription = "Add Server",
                    testTag = "home_add_server_button",
                    onClick = onOpenAddServer
                )
                Spacer(modifier = Modifier.width(8.dp))
                CircularHeaderIconButton(
                    icon = Icons.Rounded.Settings,
                    contentDescription = "Settings",
                    testTag = "home_settings_button",
                    onClick = onOpenSettings
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Hero Multi-Layered Emerald Organic Wave Orb + Central White Power Button
            OrganicEmeraldPowerOrb(
                isConnected = telemetry.isConnected,
                isConnecting = telemetry.isConnecting,
                onToggleClick = onToggleConnect
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Status Title: "Connected"
            Text(
                text = when {
                    telemetry.isConnecting -> stringResource(R.string.status_connecting)
                    telemetry.isConnected -> stringResource(R.string.status_connected)
                    else -> stringResource(R.string.status_disconnected)
                },
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 26.sp
                ),
                color = TextPrimaryDark,
                modifier = Modifier.testTag("connection_status_text")
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Live Timer: "00:12:36"
            Text(
                text = telemetry.formattedTimer,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp
                ),
                color = TextSecondaryMuted,
                modifier = Modifier.testTag("connection_timer_text")
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Active Country Pill Selector: [ 🇩🇪 Germany  > ]
            Surface(
                modifier = Modifier
                    .shadow(
                        elevation = 5.dp,
                        shape = RoundedCornerShape(50),
                        ambientColor = Color.Black.copy(alpha = 0.06f)
                    )
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onOpenServerList)
                    .testTag("active_server_pill"),
                shape = RoundedCornerShape(50),
                color = PureWhiteCard
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularCountryFlag(
                        countryCode = activeCountryCode,
                        size = 20.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = activeCountryName,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.5.sp
                        ),
                        color = TextPrimaryDark
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = "Choose Server",
                        tint = TextSecondaryMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            // 5-Row White Rounded Telemetry Card
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = 6.dp,
                        shape = RoundedCornerShape(22.dp),
                        ambientColor = Color.Black.copy(alpha = 0.05f)
                    ),
                shape = RoundedCornerShape(22.dp),
                color = PureWhiteCard
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    TelemetryInfoRow(
                        icon = Icons.Outlined.Description,
                        label = stringResource(R.string.label_protocol),
                        value = activeProtocol
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = DividerSage
                    )
                    TelemetryInfoRow(
                        icon = Icons.Rounded.Bolt,
                        label = stringResource(R.string.label_latency),
                        value = activePingLabel
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = DividerSage
                    )
                    TelemetryInfoRow(
                        icon = Icons.Outlined.LocationOn,
                        label = stringResource(R.string.label_server_ip),
                        value = cleanServerIp
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = DividerSage
                    )
                    TelemetryInfoRow(
                        icon = Icons.Outlined.LocationOn,
                        label = "Real IP",
                        value = telemetry.realPublicIpLabel,
                        flagCode = telemetry.realPublicCountryCode
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = DividerSage
                    )
                    TelemetryInfoRow(
                        icon = Icons.Rounded.ArrowUpward,
                        label = stringResource(R.string.label_upload),
                        value = telemetry.formattedUpload
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = DividerSage
                    )
                    TelemetryInfoRow(
                        icon = Icons.Rounded.ArrowDownward,
                        label = stringResource(R.string.label_download),
                        value = telemetry.formattedDownload
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Bottom Action Pill Button: Soft Coral "Disconnect" (or Forest Emerald "Connect")
            val isConnected = telemetry.isConnected
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onToggleConnect)
                    .testTag("bottom_disconnect_connect_button"),
                shape = RoundedCornerShape(50),
                color = if (isConnected) DisconnectPillBg else ForestEmerald
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isConnected) {
                        Box(
                            modifier = Modifier
                                .size(15.dp)
                                .clip(RoundedCornerShape(3.5.dp))
                                .background(DisconnectCrimson)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.action_disconnect),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.5.sp
                            ),
                            color = DisconnectCrimson
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.PowerSettingsNew,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.action_connect),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.5.sp
                            ),
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun TelemetryInfoRow(
    icon: ImageVector,
    label: String,
    value: String,
    flagCode: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = TextSecondaryMuted,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.8.sp),
                color = TextSecondaryMuted
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
            if (!flagCode.isNullOrBlank()) {
                CircularCountryFlag(
                    countryCode = flagCode,
                    size = 16.dp
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.5.sp
                ),
                color = TextPrimaryDark,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
