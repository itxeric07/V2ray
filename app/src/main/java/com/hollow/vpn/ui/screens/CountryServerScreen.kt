package com.hollow.vpn.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.hollow.vpn.data.ServerEntity
import com.hollow.vpn.ui.components.CircularCountryFlag
import com.hollow.vpn.ui.components.SageOrganicBackground
import com.hollow.vpn.ui.theme.ForestEmerald
import com.hollow.vpn.ui.theme.PureWhiteCard
import com.hollow.vpn.ui.theme.SubtleCardBorder
import com.hollow.vpn.ui.theme.TextPrimaryDark
import com.hollow.vpn.ui.theme.TextSecondaryMuted

@Composable
fun CountryServerListScreen(
    servers: List<ServerEntity>,
    onSelectServer: (ServerEntity) -> Unit,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAddServer: () -> Unit,
    onPingServer: (ServerEntity) -> Unit,
    onInspectJson: (ServerEntity) -> Unit,
    onDeleteServer: (ServerEntity) -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    val groups = servers.groupBy { it.countryName.ifBlank { "Unknown" } }
        .toSortedMap(String.CASE_INSENSITIVE_ORDER)
    val expanded = remember { mutableStateMapOf<String, Boolean>() }

    SageOrganicBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp)
        ) {
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Servers", style = MaterialTheme.typography.headlineMedium, color = TextPrimaryDark)
                    Text("Choose a country and server", style = MaterialTheme.typography.bodyMedium, color = TextSecondaryMuted)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularHeaderIconButton(Icons.Rounded.Add, "Add server", "country_add", onOpenAddServer)
                    CircularHeaderIconButton(Icons.Rounded.Refresh, "Refresh servers", "country_refresh", onRefresh)
                    CircularHeaderIconButton(Icons.Rounded.Settings, "Settings", "country_settings", onOpenSettings)
                }
            }
            Spacer(Modifier.height(16.dp))
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (groups.isEmpty()) {
                    item {
                        Surface(shape = RoundedCornerShape(18.dp), color = PureWhiteCard) {
                            Text("No servers available. Tap refresh to fetch servers.", modifier = Modifier.padding(22.dp), color = TextSecondaryMuted)
                        }
                    }
                }
                groups.forEach { (country, countryServers) ->
                    val isExpanded = expanded[country] ?: (groups.size == 1)
                    item(key = "country_$country") {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .clickable { expanded[country] = !isExpanded },
                            color = PureWhiteCard,
                            border = BorderStroke(1.dp, SubtleCardBorder),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularCountryFlag(countryServers.first().countryCode, 30.dp)
                                Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                                    Text(country, style = MaterialTheme.typography.titleMedium, color = TextPrimaryDark)
                                    Text("${countryServers.size} server${if (countryServers.size == 1) "" else "s"}", style = MaterialTheme.typography.bodySmall, color = TextSecondaryMuted)
                                }
                                Icon(
                                    if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                    contentDescription = if (isExpanded) "Collapse $country" else "Expand $country",
                                    tint = ForestEmerald,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                    if (isExpanded) {
                        items(countryServers, key = { "server_${it.id}" }) { server ->
                            Surface(
                                modifier = Modifier.fillMaxWidth().padding(start = 10.dp),
                                shape = RoundedCornerShape(16.dp),
                                color = PureWhiteCard
                            ) {
                                ServerItemRowContent(
                                    server = server,
                                    showLightningBadge = server.isSelected,
                                    onSelect = { onSelectServer(server) },
                                    onPing = { onPingServer(server) },
                                    onInspectJson = { onInspectJson(server) },
                                    onDelete = { onDeleteServer(server) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
