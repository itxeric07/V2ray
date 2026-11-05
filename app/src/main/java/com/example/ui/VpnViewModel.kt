package com.example.ui

import android.app.Application
import android.content.Intent
import android.net.TrafficStats
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppSettingsState
import com.example.data.ServerEntity
import com.example.data.ServerFilter
import com.example.data.SubscriptionEntity
import com.example.data.VpnDatabase
import com.example.data.VpnPreferencesStore
import com.example.vpn.V2RayParserAndFletcher
import com.example.vpn.XrayVpnService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.max

enum class VpnScreen {
    SPLASH_FETCHING,
    SERVER_LIST,
    CONNECTED_HOME,
    SETTINGS,
    ADD_SERVER
}

enum class AddServerTab(val title: String) {
    SUBSCRIPTION("Subscription"),
    MANUAL("Manual"),
    QR_CODE("QR Code")
}

data class ConnectionTelemetryState(
    val isConnected: Boolean = false,
    val isConnecting: Boolean = false,
    val elapsedSeconds: Long = 0L,
    val uploadBytes: Long = 0L,
    val downloadBytes: Long = 0L,
    val serverIpLabel: String = "—",
    val realPublicIpLabel: String = "Detecting…",
    val realPublicCountryCode: String = "DE"
) {
    val formattedTimer: String
        get() {
            val hours = elapsedSeconds / 3600
            val minutes = (elapsedSeconds % 3600) / 60
            val seconds = elapsedSeconds % 60
            return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
        }

    val formattedUpload: String
        get() = formatByteCount(uploadBytes)

    val formattedDownload: String
        get() = formatByteCount(downloadBytes)

    companion object {
        fun formatByteCount(bytes: Long): String {
            val safe = max(0L, bytes)
            return when {
                safe < 1024L -> "$safe B"
                safe < 1024L * 1024L -> String.format(Locale.US, "%.1f KB", safe / 1024.0)
                safe < 1024L * 1024L * 1024L -> String.format(Locale.US, "%.2f MB", safe / (1024.0 * 1024.0))
                else -> String.format(Locale.US, "%.2f GB", safe / (1024.0 * 1024.0 * 1024.0))
            }
        }
    }
}

class VpnViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = VpnDatabase.getInstance(application).vpnDao()
    private val prefsStore = VpnPreferencesStore(application)
    private val fletcher = V2RayParserAndFletcher()

    private val _settings = MutableStateFlow(prefsStore.loadSettings())
    val settings: StateFlow<AppSettingsState> = _settings.asStateFlow()

    private val _screenStack = MutableStateFlow(listOf(VpnScreen.SPLASH_FETCHING))
    private val _currentScreen = MutableStateFlow(VpnScreen.SPLASH_FETCHING)
    val currentScreen: StateFlow<VpnScreen> = _currentScreen.asStateFlow()

    private val _selectedFilter = MutableStateFlow(ServerFilter.ALL)
    val selectedFilter: StateFlow<ServerFilter> = _selectedFilter.asStateFlow()

    private val _telemetry = MutableStateFlow(ConnectionTelemetryState())
    val telemetry: StateFlow<ConnectionTelemetryState> = _telemetry.asStateFlow()

    private val _fetchingStatusMessage = MutableStateFlow("Auto configuring for you")
    val fetchingStatusMessage: StateFlow<String> = _fetchingStatusMessage.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    private val _activeAddTab = MutableStateFlow(AddServerTab.SUBSCRIPTION)
    val activeAddTab: StateFlow<AddServerTab> = _activeAddTab.asStateFlow()

    private val _inspectingJsonServer = MutableStateFlow<ServerEntity?>(null)
    val inspectingJsonServer: StateFlow<ServerEntity?> = _inspectingJsonServer.asStateFlow()

    val allServers: StateFlow<List<ServerEntity>> = dao.observeAllServers()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val filteredServers: StateFlow<List<ServerEntity>> = combine(
        allServers,
        _selectedFilter
    ) { servers, filter ->
        when (filter) {
            ServerFilter.ALL -> {
                servers.sortedWith(
                    compareByDescending<ServerEntity> { it.isSelected }
                        .thenBy { it.pingMs }
                )
            }
            ServerFilter.RECOMMENDED -> {
                servers.sortedBy { it.pingMs }.take(15)
            }
            ServerFilter.LOW_PING -> {
                val threshold = servers.map { it.pingMs }.minOrNull()?.let { max(120, it + 45) } ?: 120
                servers.filter { it.pingMs in 1..threshold }.sortedBy { it.pingMs }
            }
            ServerFilter.HIGH_SPEED -> {
                servers.filter {
                    it.transport.equals("Reality", ignoreCase = true) ||
                        it.transport.equals("VLESS", ignoreCase = true) ||
                        it.transport.equals("gRPC", ignoreCase = true) ||
                        it.transport.equals("WebSocket", ignoreCase = true)
                }.sortedBy { it.pingMs }.take(15)
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val selectedServer: StateFlow<ServerEntity?> = allServers.combine(_telemetry) { list, _ ->
        list.firstOrNull { it.isSelected } ?: list.firstOrNull()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    private var timerJob: Job? = null
    private var baselineTxBytes: Long = 0L
    private var baselineRxBytes: Long = 0L

    init {
        observeVpnServiceState()
        refreshRealPublicIp()
        initializeLiveServersOnStartup()
        startRealTrafficTelemetryLoop()
    }

    /**
     * Keeps UI connection state strictly synchronized with the real Android OS XrayVpnService.
     * If the OS VPN service stops, fails to establish, or is revoked in system settings,
     * the UI immediately shows Disconnected.
     */
    private fun observeVpnServiceState() {
        viewModelScope.launch {
            XrayVpnService.isServiceRunning.collect { running ->
                if (running) {
                    baselineTxBytes = safeTrafficStat { TrafficStats.getTotalTxBytes() }
                    baselineRxBytes = safeTrafficStat { TrafficStats.getTotalRxBytes() }
                    _telemetry.update {
                        it.copy(
                            isConnected = true,
                            isConnecting = false,
                            elapsedSeconds = 0L,
                            uploadBytes = 0L,
                            downloadBytes = 0L
                        )
                    }
                } else {
                    _telemetry.update {
                        it.copy(
                            isConnected = false,
                            isConnecting = false
                        )
                    }
                }
            }
        }
        viewModelScope.launch {
            XrayVpnService.lastError.collect { err ->
                if (!err.isNullOrBlank()) {
                    showMessage(err)
                }
            }
        }
    }

    fun refreshRealPublicIp() {
        viewModelScope.launch {
            val (ip, country, code) = fletcher.fetchDevicePublicIpInfo()
            _telemetry.update {
                it.copy(
                    realPublicIpLabel = "$ip · $country",
                    realPublicCountryCode = code
                )
            }
        }
    }

    /**
     * On startup, checks for cached real servers or automatically fetches & ping-ranks the Top 15
     * live servers from the user's configured subscription URL.
     */
    private fun initializeLiveServersOnStartup() {
        viewModelScope.launch {
            val existing = dao.getAllServersOnce()
            val canAutoConnect = _settings.value.startOnAppOpen &&
                android.net.VpnService.prepare(getApplication()) == null
            if (existing.isEmpty() || _settings.value.autoConfigure) {
                runFletcherAutoFetchAndPingSweep(
                    returnScreen = VpnScreen.CONNECTED_HOME,
                    autoConnectAfterFetch = canAutoConnect
                )
            } else {
                _screenStack.value = listOf(VpnScreen.CONNECTED_HOME)
                _currentScreen.value = VpnScreen.CONNECTED_HOME
                if (canAutoConnect) {
                    val active = existing.firstOrNull { it.isSelected } ?: existing.first()
                    connectToServer(active)
                }
            }
        }
    }

    /**
     * Reads real Android OS TrafficStats TX/RX counters while connected.
     */
    private fun startRealTrafficTelemetryLoop() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000L)
                if (_telemetry.value.isConnected && !_telemetry.value.isConnecting) {
                    val currentTx = safeTrafficStat { TrafficStats.getTotalTxBytes() }
                    val currentRx = safeTrafficStat { TrafficStats.getTotalRxBytes() }
                    val deltaTx = if (baselineTxBytes > 0 && currentTx >= baselineTxBytes) {
                        currentTx - baselineTxBytes
                    } else 0L
                    val deltaRx = if (baselineRxBytes > 0 && currentRx >= baselineRxBytes) {
                        currentRx - baselineRxBytes
                    } else 0L

                    _telemetry.update { state ->
                        state.copy(
                            elapsedSeconds = state.elapsedSeconds + 1,
                            uploadBytes = deltaTx,
                            downloadBytes = deltaRx
                        )
                    }
                }
            }
        }
    }

    private fun safeTrafficStat(block: () -> Long): Long {
        return try {
            val v = block()
            if (v == TrafficStats.UNSUPPORTED.toLong() || v < 0L) 0L else v
        } catch (_: Exception) {
            0L
        }
    }

    fun navigateTo(screen: VpnScreen) {
        if (_currentScreen.value == screen) return
        _screenStack.update { it + screen }
        _currentScreen.value = screen
    }

    fun navigateBack(): Boolean {
        val stack = _screenStack.value
        if (stack.size > 1) {
            val newStack = stack.dropLast(1)
            _screenStack.value = newStack
            _currentScreen.value = newStack.last()
            return true
        }
        return false
    }

    fun setFilter(filter: ServerFilter) {
        _selectedFilter.value = filter
    }

    fun setAddServerTab(tab: AddServerTab) {
        _activeAddTab.value = tab
    }

    /** Selects a server without starting the VPN. Used while Android shows its consent dialog. */
    fun selectServer(server: ServerEntity, openConnectedScreen: Boolean = true) {
        viewModelScope.launch {
            dao.clearSelection()
            dao.markSelected(server.id)
            if (openConnectedScreen) navigateTo(VpnScreen.CONNECTED_HOME)
        }
    }

    fun selectAndConnectServer(server: ServerEntity, openConnectedScreen: Boolean = true) {
        viewModelScope.launch {
            dao.clearSelection()
            dao.markSelected(server.id)
            if (openConnectedScreen) {
                navigateTo(VpnScreen.CONNECTED_HOME)
            }
            connectToServer(server)
        }
    }

    fun toggleConnection() {
        val currentlyConnected = _telemetry.value.isConnected
        if (currentlyConnected) {
            _telemetry.update {
                it.copy(
                    isConnected = false,
                    isConnecting = false
                )
            }
            stopAndroidVpnService()
            val name = selectedServer.value?.countryName ?: "VPN"
            showMessage("Disconnected from $name")
        } else {
            val active = selectedServer.value
            if (active == null) {
                showMessage("No server available yet — fetching live servers…")
                runFletcherAutoFetchAndPingSweep(
                    returnScreen = VpnScreen.CONNECTED_HOME,
                    autoConnectAfterFetch = true
                )
                return
            }
            viewModelScope.launch {
                connectToServer(active)
            }
        }
    }

    private suspend fun connectToServer(server: ServerEntity) {
        // Never claim Connected if Android OS VPN consent hasn't been granted yet
        if (android.net.VpnService.prepare(getApplication()) != null) {
            _telemetry.update { it.copy(isConnecting = false, isConnected = false) }
            showMessage("Tap Connect and accept the Android VPN prompt to start the tunnel")
            return
        }

        _telemetry.update { it.copy(isConnecting = true) }
        val livePing = withContext(Dispatchers.IO) {
            fletcher.measureRealTcpLatencyOrNull(server.host, server.port, timeoutMs = 2200)
        }
        if (livePing == null) {
            _telemetry.update { it.copy(isConnecting = false, isConnected = false) }
            showMessage("${server.countryName} (${server.host}:${server.port}) did not respond to TCP handshake")
            return
        }

        val resolvedIp = server.resolvedIp.ifBlank { fletcher.resolveServerIpAddress(server.host) }
        val updatedServer = server.copy(
            pingMs = livePing,
            resolvedIp = resolvedIp,
            isSelected = true
        )
        dao.updateServer(updatedServer)

        _telemetry.update { prev ->
            prev.copy(
                serverIpLabel = resolvedIp
            )
        }
        startAndroidVpnService(updatedServer)
        showMessage("Connecting to ${server.countryName} ($livePing ms)…")
    }

    /**
     * Runs the real Fletcher Auto-Fetcher & Concurrent TCP Ping Ranker, saving the Top 15
     * fastest reachable servers to the Room database.
     */
    fun runFletcherAutoFetchAndPingSweep(
        returnScreen: VpnScreen = VpnScreen.SERVER_LIST,
        autoConnectAfterFetch: Boolean = false
    ) {
        _currentScreen.value = VpnScreen.SPLASH_FETCHING
        viewModelScope.launch {
            _fetchingStatusMessage.value = "Auto configuring for you"

            val subs = dao.getSubscriptionsOnce()
            val targetSubUrl = subs.firstOrNull()?.url?.takeIf { it.isNotBlank() }
                ?: _settings.value.activeSubscriptionUrl

            val subResult = fletcher.fetchSubscriptionAndRankTop15(targetSubUrl) { step ->
                _fetchingStatusMessage.value = step
            }

            subResult.fold(
                onSuccess = { rankedTop15 ->
                    if (_settings.value.subReplaceExisting) {
                        dao.deleteFetchedServers()
                    }
                    dao.clearSelection()
                    val withSelected = rankedTop15.mapIndexed { idx, srv ->
                        srv.copy(isSelected = idx == 0)
                    }
                    dao.insertServers(withSelected)

                    _screenStack.value = listOf(VpnScreen.CONNECTED_HOME, returnScreen).distinct()
                    _currentScreen.value = returnScreen

                    val fastest = withSelected.firstOrNull()
                    if (autoConnectAfterFetch && fastest != null) {
                        val savedFirst = dao.getAllServersOnce().firstOrNull { it.isSelected } ?: fastest
                        connectToServer(savedFirst)
                    } else if (fastest != null) {
                        val ip = fletcher.resolveServerIpAddress(fastest.host)
                        _telemetry.update { it.copy(serverIpLabel = ip) }
                        showMessage(
                            "Ranked Top ${withSelected.size} live servers · #1 ${fastest.countryName} (${fastest.pingMs} ms)"
                        )
                    }
                },
                onFailure = { error ->
                    // Re-benchmark any already saved servers if feed fetch failed
                    val existing = dao.getAllServersOnce()
                    if (existing.isNotEmpty()) {
                        _fetchingStatusMessage.value = "Re-testing saved servers…"
                        val reRanked = fletcher.benchmarkAndRankTop15(existing) { step ->
                            _fetchingStatusMessage.value = step
                        }
                        if (reRanked.isNotEmpty()) {
                            dao.deleteAllServers()
                            dao.insertServers(
                                reRanked.mapIndexed { index, srv -> srv.copy(isSelected = index == 0) }
                            )
                        }
                        _currentScreen.value = returnScreen
                        showMessage("Updated latency for ${reRanked.size} reachable servers")
                    } else {
                        _currentScreen.value = VpnScreen.ADD_SERVER
                        showMessage("Fetch failed: ${error.message ?: "Check subscription URL"}")
                    }
                }
            )
        }
    }

    /**
     * Adds a user-provided V2Ray Subscription URL (or inline URI/Base64 payload), fetches nodes,
     * runs parallel TCP ping tests, drops dead nodes, and ranks the Top 15 fastest servers.
     */
    fun addAndFetchSubscriptionUrl(rawUrlOrLinks: String) {
        val input = rawUrlOrLinks.trim()
        if (input.isBlank()) {
            showMessage("Please enter a valid V2Ray subscription URL or URI link")
            return
        }

        if (input.startsWith("vmess://") ||
            input.startsWith("vless://") ||
            input.startsWith("trojan://") ||
            input.startsWith("ss://") ||
            input.startsWith("hy2://") ||
            input.startsWith("{")
        ) {
            importFromTextOrClipboard(input)
            return
        }

        updateSettings { it.copy(activeSubscriptionUrl = input) }
        _currentScreen.value = VpnScreen.SPLASH_FETCHING

        viewModelScope.launch {
            _fetchingStatusMessage.value = "Auto configuring for you"
            dao.insertSubscription(
                SubscriptionEntity(
                    url = input,
                    autoUpdate = _settings.value.subAutoUpdate,
                    replaceExisting = _settings.value.subReplaceExisting
                )
            )

            val fetchResult = fletcher.fetchSubscriptionAndRankTop15(input) { status ->
                _fetchingStatusMessage.value = status
            }

            fetchResult.fold(
                onSuccess = { top15Nodes ->
                    if (_settings.value.subReplaceExisting) {
                        dao.deleteFetchedServers()
                    }
                    dao.clearSelection()
                    val marked = top15Nodes.mapIndexed { index, srv ->
                        srv.copy(isSelected = index == 0)
                    }
                    dao.insertServers(marked)
                    val fastest = marked.firstOrNull()
                    if (fastest != null) {
                        val ip = fletcher.resolveServerIpAddress(fastest.host)
                        _telemetry.update { it.copy(serverIpLabel = ip) }
                    }
                    _currentScreen.value = VpnScreen.SERVER_LIST
                    showMessage(
                        "Fetched & ranked Top ${marked.size} live servers! Fastest: ${fastest?.countryName ?: ""} (${fastest?.pingMs ?: 0} ms)"
                    )
                },
                onFailure = { err ->
                    _currentScreen.value = VpnScreen.ADD_SERVER
                    showMessage("Could not fetch servers: ${err.message}")
                }
            )
        }
    }

    /**
     * Imports V2Ray/Xray servers from clipboard text and runs real TCP latency tests.
     */
    fun importFromTextOrClipboard(rawText: String) {
        val trimmed = rawText.trim()
        if (trimmed.isBlank()) {
            showMessage("Clipboard is empty — copy a vless://, vmess://, trojan://, ss://, or Xray JSON config first")
            return
        }
        viewModelScope.launch {
            val parsed = fletcher.parseImportPayload(trimmed, isCustom = true)
            if (parsed.isEmpty()) {
                showMessage("No valid V2Ray URI or Xray JSON found in clipboard")
                return@launch
            }
            showMessage("Testing latency for ${parsed.size} imported server(s)…")
            val benchmarked = fletcher.benchmarkAndRankTop15(parsed)
            if (benchmarked.isEmpty()) {
                showMessage("Parsed ${parsed.size} server(s), but none responded to TCP ping")
                return@launch
            }
            dao.clearSelection()
            val toInsert = benchmarked.mapIndexed { idx, server ->
                server.copy(isSelected = idx == 0)
            }
            dao.insertServers(toInsert)
            navigateTo(VpnScreen.SERVER_LIST)
            val best = toInsert.first()
            showMessage("Imported ${toInsert.size} live server(s) · Fastest: ${best.countryName} (${best.pingMs} ms)")
        }
    }

    /**
     * Validates, pings, and saves a manual custom V2Ray/Xray server or Raw Xray JSON configuration.
     */
    fun addManualCustomServer(
        countryName: String,
        host: String,
        port: Int,
        protocol: String,
        transport: String,
        uuidOrPassword: String,
        sni: String,
        rawJsonInput: String
    ) {
        viewModelScope.launch {
            if (rawJsonInput.trim().startsWith("{")) {
                val parsedFromJson = fletcher.parseRawXrayJson(rawJsonInput.trim(), isCustom = true)
                if (parsedFromJson.isEmpty()) {
                    showMessage("Invalid Xray JSON configuration — check outbounds syntax")
                    return@launch
                }
                val candidate = parsedFromJson.first()
                val livePing = withContext(Dispatchers.IO) {
                    fletcher.measureRealTcpLatencyOrNull(candidate.host, candidate.port, 2000)
                }
                if (livePing == null) {
                    showMessage("Server ${candidate.host}:${candidate.port} is unreachable (TCP timeout)")
                    return@launch
                }
                dao.clearSelection()
                val verified = candidate.copy(pingMs = livePing, isSelected = true)
                dao.insertServer(verified)
                navigateTo(VpnScreen.SERVER_LIST)
                showMessage("Added custom Xray JSON server (${verified.countryName} · $livePing ms)")
                return@launch
            }

            val cleanHost = host.trim()
            if (cleanHost.isBlank()) {
                showMessage("Please enter a valid server hostname or IP address")
                return@launch
            }
            if (uuidOrPassword.isBlank()) {
                showMessage("Please enter a UUID or password for the server")
                return@launch
            }

            showMessage("Testing TCP connection to $cleanHost:$port…")
            val livePing = withContext(Dispatchers.IO) {
                fletcher.measureRealTcpLatencyOrNull(cleanHost, port, 2000)
            }
            if (livePing == null) {
                showMessage("Host $cleanHost:$port did not respond to TCP ping")
                return@launch
            }

            val baseServer = ServerEntity(
                countryName = countryName.trim(),
                countryCode = "DE",
                protocolDisplay = "V2Ray · $transport",
                protocolCore = "$protocol (V2Ray)",
                transport = transport,
                host = cleanHost,
                port = port.coerceIn(1, 65535),
                uuidOrPassword = uuidOrPassword.trim(),
                sni = sni.trim(),
                pingMs = livePing,
                isRecommendedTop15 = true,
                rankOrder = 1,
                isSelected = true,
                isCustom = true
            )
            val enriched = fletcher.enrichServerWithIpAndCountry(baseServer, rank = 1)
            val finalServer = if (countryName.trim().isNotBlank()) {
                enriched.copy(countryName = countryName.trim())
            } else {
                enriched
            }
            val withJson = finalServer.copy(
                rawConfigJson = fletcher.generateXrayJsonForServer(finalServer)
            )
            dao.clearSelection()
            dao.insertServer(withJson)
            navigateTo(VpnScreen.SERVER_LIST)
            showMessage("Added ${withJson.countryName} · ${withJson.resolvedIp} ($livePing ms)")
        }
    }

    fun pingSingleServer(server: ServerEntity) {
        viewModelScope.launch {
            val livePing = withContext(Dispatchers.IO) {
                fletcher.measureRealTcpLatencyOrNull(server.host, server.port, 1800)
            }
            if (livePing != null) {
                dao.updateServer(server.copy(pingMs = livePing))
                showMessage("${server.countryName} live TCP ping: $livePing ms")
            } else {
                showMessage("${server.countryName} (${server.host}:${server.port}) timed out")
            }
        }
    }

    fun deleteServer(server: ServerEntity) {
        viewModelScope.launch {
            dao.deleteServerById(server.id)
            showMessage("Removed ${server.countryName}")
        }
    }

    fun openJsonInspector(server: ServerEntity) {
        val json = server.rawConfigJson.ifBlank { fletcher.generateXrayJsonForServer(server) }
        _inspectingJsonServer.value = server.copy(rawConfigJson = json)
    }

    fun closeJsonInspector() {
        _inspectingJsonServer.value = null
    }

    fun saveEditedServerJson(server: ServerEntity, updatedJson: String) {
        viewModelScope.launch {
            dao.updateServer(server.copy(rawConfigJson = updatedJson))
            _inspectingJsonServer.value = null
            showMessage("Saved Xray JSON config for ${server.countryName}")
        }
    }

    fun updateSettings(transform: (AppSettingsState) -> AppSettingsState) {
        _settings.update { current ->
            val updated = transform(current)
            prefsStore.saveSettings(updated)
            updated
        }
    }

    fun cycleUpdateInterval() {
        val intervals = listOf("15 min", "30 min", "1 hour", "6 hours", "12 hours")
        updateSettings { current ->
            val nextIdx = (intervals.indexOf(current.updateIntervalLabel) + 1) % intervals.size
            val next = intervals[nextIdx]
            current.copy(updateIntervalLabel = next, subUpdateInterval = next)
        }
    }

    fun cycleProtocolPreference() {
        val options = listOf("Auto (V2Ray)", "VLESS (Reality)", "VMess (WebSocket)", "Trojan (TLS)", "Xray Custom JSON")
        updateSettings { current ->
            val nextIdx = (options.indexOf(current.protocolPreference) + 1) % options.size
            current.copy(protocolPreference = options[nextIdx])
        }
    }

    fun cycleRoutingMode() {
        val options = listOf("Global", "Bypass LAN", "Bypass Mainland & LAN", "Direct Only")
        updateSettings { current ->
            val nextIdx = (options.indexOf(current.routingMode) + 1) % options.size
            current.copy(routingMode = options[nextIdx])
        }
    }

    fun cycleDnsMode() {
        val options = listOf("Auto (DoH)", "Cloudflare 1.1.1.1", "Google 8.8.8.8", "Quad9 9.9.9.9")
        updateSettings { current ->
            val nextIdx = (options.indexOf(current.dnsMode) + 1) % options.size
            current.copy(dnsMode = options[nextIdx])
        }
    }

    fun clearServerCacheAndReset() {
        viewModelScope.launch {
            stopAndroidVpnService()
            _telemetry.value = ConnectionTelemetryState()
            dao.deleteAllServers()
            showMessage("Cleared all cached servers")
        }
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    fun showMessage(msg: String) {
        _snackbarMessage.value = msg
    }

    private fun startAndroidVpnService(server: ServerEntity) {
        try {
            val ctx = getApplication<Application>()
            val currentSettings = _settings.value
            val xrayJson = server.rawConfigJson.ifBlank { fletcher.generateXrayJsonForServer(server) }
            val intent = Intent(ctx, XrayVpnService::class.java).apply {
                action = XrayVpnService.ACTION_CONNECT
                putExtra(XrayVpnService.EXTRA_SERVER_COUNTRY, server.countryName)
                putExtra(XrayVpnService.EXTRA_SERVER_HOST, server.host)
                putExtra(XrayVpnService.EXTRA_SERVER_PORT, server.port)
                putExtra(XrayVpnService.EXTRA_SERVER_PROTOCOL, server.protocolCore)
                putExtra(XrayVpnService.EXTRA_XRAY_JSON, xrayJson)
                putExtra(XrayVpnService.EXTRA_ENABLE_IPV6, currentSettings.ipv6Enabled)
                putExtra(XrayVpnService.EXTRA_DNS_MODE, currentSettings.dnsMode)
                putExtra(XrayVpnService.EXTRA_ROUTING_MODE, currentSettings.routingMode)
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                ctx.startForegroundService(intent)
            } else {
                ctx.startService(intent)
            }
        } catch (e: Exception) {
            _telemetry.update { it.copy(isConnecting = false, isConnected = false) }
            showMessage("Could not start VPN service: ${e.message}")
        }
    }

    private fun stopAndroidVpnService() {
        try {
            val ctx = getApplication<Application>()
            val intent = Intent(ctx, XrayVpnService::class.java).apply {
                action = XrayVpnService.ACTION_DISCONNECT
            }
            ctx.startService(intent)
        } catch (_: Exception) {
        }
    }
}
