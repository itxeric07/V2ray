package com.hollow.vpn.ui.screens

import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hollow.vpn.R
import com.hollow.vpn.data.AppSettingsState
import com.hollow.vpn.data.ServerEntity
import com.hollow.vpn.ui.AddServerTab
import com.hollow.vpn.ui.components.SageOrganicBackground
import com.hollow.vpn.ui.theme.DisconnectCrimson
import com.hollow.vpn.ui.theme.DividerSage
import com.hollow.vpn.ui.theme.EmeraldSwitchActive
import com.hollow.vpn.ui.theme.ForestEmerald
import com.hollow.vpn.ui.theme.InputFieldBg
import com.hollow.vpn.ui.theme.JetBrainsMonoFamily
import com.hollow.vpn.ui.theme.PureWhiteCard
import com.hollow.vpn.ui.theme.SelectedMintCard
import com.hollow.vpn.ui.theme.SubtleCardBorder
import com.hollow.vpn.ui.theme.TextPrimaryDark
import com.hollow.vpn.ui.theme.TextSecondaryMuted
import com.hollow.vpn.ui.theme.TextTertiaryHint

data class SettingChoiceOption(
    val value: String,
    val subtitle: String
)

private enum class ActiveSettingDialog {
    UPDATE_INTERVAL,
    PROTOCOL,
    ROUTING_MODE,
    DNS_PROVIDER,
    CLEAR_CACHE_CONFIRM
}

private val UPDATE_INTERVAL_OPTIONS = listOf(
    SettingChoiceOption("15 min", "High-frequency auto-refresh"),
    SettingChoiceOption("30 min", "Balanced refresh interval"),
    SettingChoiceOption("1 hour", "Recommended standard interval"),
    SettingChoiceOption("6 hours", "Battery-saving interval"),
    SettingChoiceOption("12 hours", "Twice-daily subscription refresh"),
    SettingChoiceOption("24 hours", "Daily subscription refresh")
)

private val PROTOCOL_OPTIONS = listOf(
    SettingChoiceOption("Auto (V2Ray)", "Automatically use the fastest available V2Ray/Xray protocol"),
    SettingChoiceOption("VLESS (Reality)", "Xray Reality TLS camouflage with ultra-low latency"),
    SettingChoiceOption("VLESS (WS / TCP)", "Lightweight stateless V2Ray VLESS transport"),
    SettingChoiceOption("VMess (WebSocket)", "Encrypted V2Ray WebSocket tunnel"),
    SettingChoiceOption("Trojan (TLS)", "HTTPS-disguised TLS tunnel"),
    SettingChoiceOption("gRPC", "Multiplexed HTTP/2 gRPC stream transport"),
    SettingChoiceOption("Shadowsocks", "Fast stream/AEAD cipher proxy")
)

private val ROUTING_MODE_OPTIONS = listOf(
    SettingChoiceOption("Global", "Route all device traffic through the VPN tunnel"),
    SettingChoiceOption("Bypass LAN", "Tunnel internet traffic while keeping local LAN (192.168.x.x) direct"),
    SettingChoiceOption("Bypass LAN & Domestic", "Keep local & domestic subnets direct; tunnel international traffic"),
    SettingChoiceOption("Direct Only", "Diagnostic mode with direct connection")
)

private val DNS_PROVIDER_OPTIONS = listOf(
    SettingChoiceOption("Auto (DoH)", "Encrypted DNS-over-HTTPS (1.1.1.1 / 1.0.0.1)"),
    SettingChoiceOption("Cloudflare 1.1.1.1", "Fast privacy-first DNS resolver (1.1.1.1)"),
    SettingChoiceOption("Google 8.8.8.8", "Reliable global DNS resolver (8.8.8.8)"),
    SettingChoiceOption("Quad9 9.9.9.9", "Security & malware-blocking DNS (9.9.9.9)"),
    SettingChoiceOption("AdGuard 94.140.14.14", "Ad-blocking and tracker-filtering DNS (94.140.14.14)")
)

/**
 * SCREEN 4: Settings Screen
 * Opens interactive selection dialogs showing all available options when Protocol, Routing Mode,
 * DNS, or Update Interval are tapped so the user can inspect and choose their preferred option.
 */
@Composable
fun SettingsScreen(
    settings: AppSettingsState,
    onUpdateSettings: ((AppSettingsState) -> AppSettingsState) -> Unit,
    onOpenAddSubscription: () -> Unit,
    onClearCache: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    var activeDialog by remember { mutableStateOf<ActiveSettingDialog?>(null) }

    SageOrganicBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .widthIn(max = 580.dp)
                .align(Alignment.TopCenter)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
        ) {
            Spacer(modifier = Modifier.height(14.dp))

            // Top Navigation Bar: Circular "<" button + "Settings"
            ScreenTopBarWithBack(
                title = stringResource(R.string.settings_title),
                onBack = onBack
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Card 1: Configuration
            SettingsGroupCard(sectionTitle = stringResource(R.string.section_configuration)) {
                SettingsToggleRow(
                    icon = Icons.Outlined.CloudQueue,
                    title = stringResource(R.string.setting_auto_configure),
                    subtitle = stringResource(R.string.setting_auto_configure_sub),
                    checked = settings.autoConfigure,
                    onCheckedChange = { enabled ->
                        onUpdateSettings { it.copy(autoConfigure = enabled) }
                    },
                    testTag = "switch_auto_configure"
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = DividerSage)
                SettingsValueNavigationRow(
                    icon = Icons.Outlined.Sync,
                    title = stringResource(R.string.setting_update_interval),
                    subtitle = stringResource(R.string.setting_update_interval_sub),
                    valueText = settings.updateIntervalLabel,
                    onClick = { activeDialog = ActiveSettingDialog.UPDATE_INTERVAL },
                    testTag = "row_update_interval"
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = DividerSage)
                SettingsValueNavigationRow(
                    icon = Icons.Outlined.Link,
                    title = stringResource(R.string.setting_custom_subscription),
                    subtitle = stringResource(R.string.setting_custom_subscription_sub),
                    valueText = null,
                    onClick = onOpenAddSubscription,
                    testTag = "row_custom_subscription"
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Card 2: Connection
            SettingsGroupCard(sectionTitle = stringResource(R.string.section_connection)) {
                SettingsValueNavigationRow(
                    icon = Icons.Rounded.AccountTree,
                    title = stringResource(R.string.setting_protocol),
                    subtitle = null,
                    valueText = settings.protocolPreference,
                    onClick = { activeDialog = ActiveSettingDialog.PROTOCOL },
                    testTag = "row_protocol"
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = DividerSage)
                SettingsValueNavigationRow(
                    icon = Icons.Rounded.Route,
                    title = stringResource(R.string.setting_routing_mode),
                    subtitle = null,
                    valueText = settings.routingMode,
                    onClick = { activeDialog = ActiveSettingDialog.ROUTING_MODE },
                    testTag = "row_routing_mode"
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = DividerSage)
                SettingsValueNavigationRow(
                    icon = Icons.Outlined.Dns,
                    title = stringResource(R.string.setting_dns),
                    subtitle = null,
                    valueText = settings.dnsMode,
                    onClick = { activeDialog = ActiveSettingDialog.DNS_PROVIDER },
                    testTag = "row_dns"
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = DividerSage)
                SettingsToggleRow(
                    icon = Icons.Outlined.Shield,
                    title = stringResource(R.string.setting_tun_mode),
                    subtitle = stringResource(R.string.setting_tun_mode_sub),
                    checked = settings.tunMode,
                    onCheckedChange = { enabled ->
                        onUpdateSettings { it.copy(tunMode = enabled) }
                    },
                    testTag = "switch_tun_mode"
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = DividerSage)
                SettingsToggleRow(
                    icon = Icons.Outlined.Language,
                    title = stringResource(R.string.setting_ipv6),
                    subtitle = stringResource(R.string.setting_ipv6_sub),
                    checked = settings.ipv6Enabled,
                    onCheckedChange = { enabled ->
                        onUpdateSettings { it.copy(ipv6Enabled = enabled) }
                    },
                    testTag = "switch_ipv6"
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Card 3: App
            SettingsGroupCard(sectionTitle = stringResource(R.string.section_app)) {
                SettingsToggleRow(
                    icon = Icons.Outlined.PlayArrow,
                    title = stringResource(R.string.setting_start_on_open),
                    subtitle = stringResource(R.string.setting_start_on_open_sub),
                    checked = settings.startOnAppOpen,
                    onCheckedChange = { enabled ->
                        onUpdateSettings { it.copy(startOnAppOpen = enabled) }
                    },
                    testTag = "switch_start_on_open"
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = DividerSage)
                SettingsToggleRow(
                    icon = Icons.Outlined.VisibilityOff,
                    title = stringResource(R.string.setting_hide_recents),
                    subtitle = stringResource(R.string.setting_hide_recents_sub),
                    checked = settings.hideFromRecentApps,
                    onCheckedChange = { enabled ->
                        onUpdateSettings { it.copy(hideFromRecentApps = enabled) }
                    },
                    testTag = "switch_hide_recents"
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = DividerSage)
                SettingsValueNavigationRow(
                    icon = Icons.Outlined.DeleteOutline,
                    title = stringResource(R.string.setting_clear_cache),
                    subtitle = stringResource(R.string.setting_clear_cache_sub),
                    valueText = null,
                    onClick = { activeDialog = ActiveSettingDialog.CLEAR_CACHE_CONFIRM },
                    testTag = "row_clear_cache"
                )
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }

    when (activeDialog) {
        ActiveSettingDialog.UPDATE_INTERVAL -> {
            SettingOptionPickerDialog(
                title = "Select Update Interval",
                options = UPDATE_INTERVAL_OPTIONS,
                selectedValue = settings.updateIntervalLabel,
                onSelect = { chosen ->
                    onUpdateSettings {
                        it.copy(updateIntervalLabel = chosen, subUpdateInterval = chosen)
                    }
                    activeDialog = null
                },
                onDismiss = { activeDialog = null }
            )
        }

        ActiveSettingDialog.PROTOCOL -> {
            SettingOptionPickerDialog(
                title = "Select V2Ray / Xray Protocol",
                options = PROTOCOL_OPTIONS,
                selectedValue = settings.protocolPreference,
                onSelect = { chosen ->
                    onUpdateSettings { it.copy(protocolPreference = chosen) }
                    activeDialog = null
                },
                onDismiss = { activeDialog = null }
            )
        }

        ActiveSettingDialog.ROUTING_MODE -> {
            SettingOptionPickerDialog(
                title = "Select Routing Mode",
                options = ROUTING_MODE_OPTIONS,
                selectedValue = settings.routingMode,
                onSelect = { chosen ->
                    onUpdateSettings { it.copy(routingMode = chosen) }
                    activeDialog = null
                },
                onDismiss = { activeDialog = null }
            )
        }

        ActiveSettingDialog.DNS_PROVIDER -> {
            SettingOptionPickerDialog(
                title = "Select DNS Provider",
                options = DNS_PROVIDER_OPTIONS,
                selectedValue = settings.dnsMode,
                onSelect = { chosen ->
                    onUpdateSettings { it.copy(dnsMode = chosen) }
                    activeDialog = null
                },
                onDismiss = { activeDialog = null }
            )
        }

        ActiveSettingDialog.CLEAR_CACHE_CONFIRM -> {
            AlertDialog(
                onDismissRequest = { activeDialog = null },
                containerColor = PureWhiteCard,
                shape = RoundedCornerShape(24.dp),
                title = {
                    Text(
                        text = "Clear Cached Servers?",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimaryDark
                    )
                },
                text = {
                    Text(
                        text = "This will remove all currently cached servers from your device. You can fetch fresh servers anytime from your subscription link.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondaryMuted
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            activeDialog = null
                            onClearCache()
                        }
                    ) {
                        Text("Clear Cache", color = DisconnectCrimson, fontWeight = FontWeight.SemiBold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { activeDialog = null }) {
                        Text("Cancel", color = TextSecondaryMuted)
                    }
                }
            )
        }

        null -> Unit
    }
}

/**
 * Reusable Material 3 option picker dialog showing all available choices with radio buttons
 * and subtitles so the user can inspect and choose their preferred setting.
 */
@Composable
fun SettingOptionPickerDialog(
    title: String,
    options: List<SettingChoiceOption>,
    selectedValue: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PureWhiteCard,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                ),
                color = TextPrimaryDark
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                options.forEach { option ->
                    val isSelected = option.value == selectedValue
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onSelect(option.value) },
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) SelectedMintCard else InputFieldBg,
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (isSelected) ForestEmerald.copy(alpha = 0.4f) else SubtleCardBorder
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { onSelect(option.value) },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = ForestEmerald,
                                    unselectedColor = TextSecondaryMuted
                                )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = option.value,
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp
                                    ),
                                    color = TextPrimaryDark
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = option.subtitle,
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                    color = TextSecondaryMuted
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = ForestEmerald, fontWeight = FontWeight.SemiBold)
            }
        }
    )
}

/**
 * SCREEN 5: Add Server Screen ("Add Server")
 */
@Composable
fun AddServerScreen(
    activeTab: AddServerTab,
    settings: AppSettingsState,
    onSelectTab: (AddServerTab) -> Unit,
    onAddAndFetchSubscription: (String) -> Unit,
    onImportClipboardText: (String) -> Unit,
    onAddManualServer: (
        countryName: String,
        host: String,
        port: Int,
        protocol: String,
        transport: String,
        uuid: String,
        sni: String,
        rawJson: String
    ) -> Unit,
    onUpdateSettings: ((AppSettingsState) -> AppSettingsState) -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    var subscriptionUrl by remember(settings.activeSubscriptionUrl) {
        mutableStateOf(settings.activeSubscriptionUrl)
    }
    var showIntervalPicker by remember { mutableStateOf(false) }

    // Manual / Raw JSON state
    var manualCountry by remember { mutableStateOf("") }
    var manualHost by remember { mutableStateOf("") }
    var manualPort by remember { mutableStateOf("443") }
    var manualProtocol by remember { mutableStateOf("VLESS") }
    var manualTransport by remember { mutableStateOf("Reality") }
    var manualUuid by remember { mutableStateOf("") }
    var manualSni by remember { mutableStateOf("") }
    var manualRawJson by remember { mutableStateOf("") }

    // QR Code URI state
    var qrUriPayload by remember { mutableStateOf("") }

    SageOrganicBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .widthIn(max = 580.dp)
                .align(Alignment.TopCenter)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
        ) {
            Spacer(modifier = Modifier.height(14.dp))

            // Top Navigation Bar: Circular "<" button + "Add Server"
            ScreenTopBarWithBack(
                title = stringResource(R.string.add_server_title),
                onBack = onBack
            )

            Spacer(modifier = Modifier.height(18.dp))

            // 3-Way Segmented Pill Tab Bar: Subscription | Manual | QR Code
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = 3.dp,
                        shape = RoundedCornerShape(50),
                        ambientColor = Color.Black.copy(alpha = 0.04f)
                    ),
                shape = RoundedCornerShape(50),
                color = PureWhiteCard.copy(alpha = 0.90f),
                border = BorderStroke(1.dp, SubtleCardBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    AddServerTab.entries.forEach { tab ->
                        val selected = tab == activeTab
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(50))
                                .clickable { onSelectTab(tab) }
                                .testTag("tab_${tab.name.lowercase()}"),
                            shape = RoundedCornerShape(50),
                            color = if (selected) ForestEmerald else Color.Transparent
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 9.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = tab.title,
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                        fontSize = 13.5.sp
                                    ),
                                    color = if (selected) Color.White else TextSecondaryMuted
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            when (activeTab) {
                AddServerTab.SUBSCRIPTION -> {
                    // Card 1: V2Ray Subscription Link + "Add & Fetch" Button
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(
                                elevation = 5.dp,
                                shape = RoundedCornerShape(22.dp),
                                ambientColor = Color.Black.copy(alpha = 0.05f)
                            ),
                        shape = RoundedCornerShape(22.dp),
                        color = PureWhiteCard
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.label_subscription_link),
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.5.sp
                                ),
                                color = TextPrimaryDark
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Rounded Subscription URL Text Field
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(50),
                                color = InputFieldBg,
                                border = BorderStroke(1.dp, SubtleCardBorder)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 13.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Link,
                                        contentDescription = null,
                                        tint = TextSecondaryMuted,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Box(modifier = Modifier.weight(1f)) {
                                        if (subscriptionUrl.isEmpty()) {
                                            Text(
                                                text = stringResource(R.string.hint_subscription_url),
                                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                                                color = TextTertiaryHint
                                            )
                                        }
                                        BasicTextField(
                                            value = subscriptionUrl,
                                            onValueChange = { subscriptionUrl = it },
                                            singleLine = true,
                                            textStyle = TextStyle(
                                                color = TextPrimaryDark,
                                                fontSize = 14.sp
                                            ),
                                            cursorBrush = SolidColor(ForestEmerald),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("subscription_url_input")
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Dark Forest Emerald Pill Button: "↓ Add & Fetch"
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(50))
                                    .clickable {
                                        onAddAndFetchSubscription(subscriptionUrl)
                                    }
                                    .testTag("btn_add_and_fetch"),
                                shape = RoundedCornerShape(50),
                                color = ForestEmerald
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Download,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(R.string.action_add_and_fetch),
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.5.sp
                                        ),
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Card 2: Import from Clipboard
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(
                                elevation = 5.dp,
                                shape = RoundedCornerShape(22.dp),
                                ambientColor = Color.Black.copy(alpha = 0.05f)
                            )
                            .clip(RoundedCornerShape(22.dp))
                            .clickable {
                                val clipboard =
                                    context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                val pasted = clipboard?.primaryClip
                                    ?.getItemAt(0)
                                    ?.coerceToText(context)
                                    ?.toString()
                                    .orEmpty()
                                onImportClipboardText(pasted)
                            }
                            .testTag("card_import_clipboard"),
                        shape = RoundedCornerShape(22.dp),
                        color = PureWhiteCard
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ContentPaste,
                                contentDescription = null,
                                tint = TextPrimaryDark,
                                modifier = Modifier.size(21.dp)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.action_import_clipboard),
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.5.sp
                                    ),
                                    color = TextPrimaryDark
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = stringResource(R.string.action_import_clipboard_sub),
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                    color = TextSecondaryMuted
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                contentDescription = null,
                                tint = TextSecondaryMuted,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Card 3: Subscription Options
                    SettingsGroupCard(sectionTitle = stringResource(R.string.section_subscription_options)) {
                        SettingsToggleRow(
                            icon = Icons.Outlined.Sync,
                            title = stringResource(R.string.setting_auto_update),
                            subtitle = null,
                            checked = settings.subAutoUpdate,
                            onCheckedChange = { enabled ->
                                onUpdateSettings { it.copy(subAutoUpdate = enabled) }
                            },
                            testTag = "switch_sub_auto_update"
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = DividerSage)
                        SettingsValueNavigationRow(
                            icon = Icons.Outlined.Schedule,
                            title = stringResource(R.string.setting_update_interval),
                            subtitle = null,
                            valueText = settings.subUpdateInterval,
                            onClick = { showIntervalPicker = true },
                            testTag = "row_sub_update_interval"
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = DividerSage)
                        SettingsToggleRow(
                            icon = Icons.Outlined.SwapHoriz,
                            title = stringResource(R.string.setting_replace_existing),
                            subtitle = stringResource(R.string.setting_replace_existing_sub),
                            checked = settings.subReplaceExisting,
                            onCheckedChange = { enabled ->
                                onUpdateSettings { it.copy(subReplaceExisting = enabled) }
                            },
                            testTag = "switch_sub_replace_existing"
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Card 4: Supported Formats
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(
                                elevation = 5.dp,
                                shape = RoundedCornerShape(22.dp),
                                ambientColor = Color.Black.copy(alpha = 0.05f)
                            ),
                        shape = RoundedCornerShape(22.dp),
                        color = PureWhiteCard
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.section_supported_formats),
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.5.sp
                                ),
                                color = TextPrimaryDark
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            SupportedFormatBullet("V2Ray (vmess, vless, trojan)")
                            SupportedFormatBullet("Clash / Sing-Box links")
                            SupportedFormatBullet("Multiple links (base64)")
                            SupportedFormatBullet("QR Code import & Raw Xray JSON")
                        }
                    }
                }

                AddServerTab.MANUAL -> {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(
                                elevation = 5.dp,
                                shape = RoundedCornerShape(22.dp),
                                ambientColor = Color.Black.copy(alpha = 0.05f)
                            ),
                        shape = RoundedCornerShape(22.dp),
                        color = PureWhiteCard
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Custom V2Ray / Xray Server",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                ),
                                color = TextPrimaryDark
                            )

                            OutlinedTextField(
                                value = manualCountry,
                                onValueChange = { manualCountry = it },
                                label = { Text("Server Country / Remark") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedTextField(
                                    value = manualHost,
                                    onValueChange = { manualHost = it },
                                    label = { Text("Server Address / Host") },
                                    singleLine = true,
                                    modifier = Modifier.weight(0.68f)
                                )
                                OutlinedTextField(
                                    value = manualPort,
                                    onValueChange = { manualPort = it },
                                    label = { Text("Port") },
                                    singleLine = true,
                                    modifier = Modifier.weight(0.32f)
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedTextField(
                                    value = manualProtocol,
                                    onValueChange = { manualProtocol = it },
                                    label = { Text("Protocol (VLESS/VMess/Trojan)") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = manualTransport,
                                    onValueChange = { manualTransport = it },
                                    label = { Text("Transport (Reality/gRPC/WS)") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            OutlinedTextField(
                                value = manualUuid,
                                onValueChange = { manualUuid = it },
                                label = { Text("UUID / Trojan Password") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = manualSni,
                                onValueChange = { manualSni = it },
                                label = { Text("SNI / Server Name") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = manualRawJson,
                                onValueChange = { manualRawJson = it },
                                label = { Text("Optional Raw Xray JSON Config (Overrides fields if provided)") },
                                textStyle = TextStyle(
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = 12.sp
                                ),
                                minLines = 4,
                                maxLines = 8,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("raw_xray_json_input")
                            )

                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(50))
                                    .clickable {
                                        onAddManualServer(
                                            manualCountry,
                                            manualHost,
                                            manualPort.toIntOrNull() ?: 443,
                                            manualProtocol,
                                            manualTransport,
                                            manualUuid,
                                            manualSni,
                                            manualRawJson
                                        )
                                    }
                                    .testTag("btn_save_manual_server"),
                                shape = RoundedCornerShape(50),
                                color = ForestEmerald
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "Save Custom Server & Ping Test",
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.5.sp
                                        ),
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }

                AddServerTab.QR_CODE -> {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(
                                elevation = 5.dp,
                                shape = RoundedCornerShape(22.dp),
                                ambientColor = Color.Black.copy(alpha = 0.05f)
                            ),
                        shape = RoundedCornerShape(22.dp),
                        color = PureWhiteCard
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(InputFieldBg),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.QrCodeScanner,
                                    contentDescription = null,
                                    tint = ForestEmerald,
                                    modifier = Modifier.size(36.dp)
                                )
                            }

                            Text(
                                text = "Import V2Ray / Xray QR Payload",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = TextPrimaryDark
                            )

                            Text(
                                text = "Paste or decode a V2Ray QR URI (vless://, vmess://, trojan://, ss://) to import and benchmark latency immediately.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondaryMuted
                            )

                            OutlinedTextField(
                                value = qrUriPayload,
                                onValueChange = { qrUriPayload = it },
                                label = { Text("QR Code Decoded V2Ray URI") },
                                textStyle = TextStyle(
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = 12.sp
                                ),
                                minLines = 3,
                                maxLines = 5,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(50))
                                    .clickable { onImportClipboardText(qrUriPayload) }
                                    .testTag("btn_import_qr_uri"),
                                shape = RoundedCornerShape(50),
                                color = ForestEmerald
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "Import QR Server & Ping Test",
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.5.sp
                                        ),
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }

    if (showIntervalPicker) {
        SettingOptionPickerDialog(
            title = "Select Update Interval",
            options = UPDATE_INTERVAL_OPTIONS,
            selectedValue = settings.subUpdateInterval,
            onSelect = { chosen ->
                onUpdateSettings {
                    it.copy(updateIntervalLabel = chosen, subUpdateInterval = chosen)
                }
                showIntervalPicker = false
            },
            onDismiss = { showIntervalPicker = false }
        )
    }
}

@Composable
private fun ScreenTopBarWithBack(
    title: String,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier
                .size(38.dp)
                .shadow(
                    elevation = 3.dp,
                    shape = CircleShape,
                    ambientColor = Color.Black.copy(alpha = 0.06f)
                )
                .clip(CircleShape)
                .clickable(onClick = onBack)
                .testTag("btn_navigate_back"),
            shape = CircleShape,
            color = PureWhiteCard.copy(alpha = 0.92f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                    contentDescription = "Back",
                    tint = TextPrimaryDark,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 21.sp
            ),
            color = TextPrimaryDark
        )
    }
}

@Composable
private fun SettingsGroupCard(
    sectionTitle: String,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 5.dp,
                shape = RoundedCornerShape(22.dp),
                ambientColor = Color.Black.copy(alpha = 0.05f)
            ),
        shape = RoundedCornerShape(22.dp),
        color = PureWhiteCard
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = sectionTitle,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                ),
                color = TextPrimaryDark,
                modifier = Modifier.padding(start = 18.dp, top = 16.dp, bottom = 6.dp)
            )
            content()
        }
    }
}

@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 18.dp, vertical = 12.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = TextPrimaryDark,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.2.sp
                ),
                color = TextPrimaryDark
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = TextSecondaryMuted
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.scale(0.86f),
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = EmeraldSwitchActive,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Color(0xFFDCE5E0),
                uncheckedBorderColor = Color.Transparent
            )
        )
    }
}

@Composable
private fun SettingsValueNavigationRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    valueText: String?,
    onClick: () -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = TextPrimaryDark,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.2.sp
                ),
                color = TextPrimaryDark
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = TextSecondaryMuted
                )
            }
        }
        if (!valueText.isNullOrBlank()) {
            Text(
                text = valueText,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp
                ),
                color = TextSecondaryMuted
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = TextSecondaryMuted,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun SupportedFormatBullet(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(4.5.dp)
                .clip(CircleShape)
                .background(TextSecondaryMuted)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp),
            color = TextSecondaryMuted
        )
    }
}

/**
 * Dialog for viewing and editing Raw Xray JSON configurations for any server.
 */
@Composable
fun RawXrayJsonEditorDialog(
    server: ServerEntity,
    onDismiss: () -> Unit,
    onSaveJson: (String) -> Unit
) {
    var jsonText by remember(server) { mutableStateOf(server.rawConfigJson) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PureWhiteCard,
        title = {
            Text(
                text = "${server.countryName} · Xray JSON Config",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = TextPrimaryDark
            )
        },
        text = {
            OutlinedTextField(
                value = jsonText,
                onValueChange = { jsonText = it },
                textStyle = TextStyle(
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 12.sp,
                    color = TextPrimaryDark
                ),
                minLines = 10,
                maxLines = 15,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onSaveJson(jsonText) }) {
                Text("Save Config", color = ForestEmerald, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondaryMuted)
            }
        }
    )
}
