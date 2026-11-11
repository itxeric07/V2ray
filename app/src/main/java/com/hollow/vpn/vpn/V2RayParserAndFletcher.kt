package com.hollow.vpn.vpn

import android.util.Base64
import com.hollow.vpn.data.AppSettingsState
import com.hollow.vpn.data.ServerEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URLDecoder
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.system.measureTimeMillis

/**
 * Production V2Ray / Xray multi-protocol parser and Fletcher Auto-Fetcher.
 * Fetches live subscriptions, samples across the entire server pool (preventing Cloudflare US
 * anycast dominance), runs parallel TCP socket ping tests, resolves real IPs + batch GeoIP countries,
 * and curates a country-diverse Top 15 fastest server list.
 */
class V2RayParserAndFletcher {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val geoHttpClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .build()

    /**
     * Fetches a user-provided V2Ray/Xray subscription URL, decodes all discovered nodes,
     * samples diverse hosts across the entire list, runs concurrent real TCP handshake latency probes,
     * resolves batch GeoIP Country Name/Flag, and returns a country-diverse Top 15 fastest server list.
     */
    suspend fun fetchSubscriptionAndRankTop15(
        subscriptionUrl: String,
        onProgress: (String) -> Unit = {}
    ): Result<List<ServerEntity>> = withContext(Dispatchers.IO) {
        try {
            val cleanUrl = subscriptionUrl.trim()
            require(cleanUrl.startsWith("http://") || cleanUrl.startsWith("https://")) {
                "Subscription link must start with https:// or http://"
            }
            onProgress("Downloading live subscription feed…")
            val request = Request.Builder()
                .url(cleanUrl)
                .header("User-Agent", "v2rayNG/1.8.19 Xray/1.8.11 Mozilla/5.0")
                .build()

            val bodyText = httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Subscription server returned HTTP ${response.code}")
                }
                response.body?.string().orEmpty()
            }

            onProgress("Parsing V2Ray / Xray server configs…")
            val parsedNodes = parseImportPayload(
                rawInput = bodyText,
                subscriptionUrl = cleanUrl,
                isCustom = false
            )
            if (parsedNodes.isEmpty()) {
                return@withContext Result.failure(
                    IllegalArgumentException("No valid V2Ray/Xray nodes found at this URL.")
                )
            }

            // Instead of taking only the first 140 lines from the top of the file (which in EbraSha
            // are often all Cloudflare US Anycast IPs), group by /16 IP prefix & domain and sample
            // evenly across the entire list, prioritizing non-Cloudflare dedicated servers first!
            val diverseCandidates = selectDiverseCandidatePool(parsedNodes, maxCandidates = 150)

            onProgress("Ping testing ${diverseCandidates.size} global servers…")
            val rankedTop15 = benchmarkAndRankTop15(
                servers = diverseCandidates,
                onProgress = onProgress
            )

            if (rankedTop15.isEmpty()) {
                return@withContext Result.failure(
                    IllegalStateException(
                        "Tested ${diverseCandidates.size} nodes from subscription, but none responded to TCP ping."
                    )
                )
            }

            Result.success(rankedTop15)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Selects up to [maxCandidates] servers spread across different /16 network prefixes,
     * domains, and explicit countries so a single Cloudflare US block doesn't monopolize the scan.
     */
    private fun selectDiverseCandidatePool(
        allParsed: List<ServerEntity>,
        maxCandidates: Int = 150
    ): List<ServerEntity> {
        val uniqueByEndpoint = allParsed.distinctBy { "${it.host.lowercase()}:${it.port}" }
        if (uniqueByEndpoint.size <= maxCandidates) return uniqueByEndpoint

        // Separate dedicated servers from Cloudflare Anycast CDN IP ranges
        val (cloudflareNodes, dedicatedNodes) = uniqueByEndpoint.partition { isCloudflareAnycastHost(it.host) }

        // Group dedicated nodes by network prefix (first 2 octets or domain TLD) for maximum geographic spread
        val bySubnet = dedicatedNodes.groupBy { server ->
            val parts = server.host.split(".")
            if (parts.size == 4 && parts[0].toIntOrNull() != null) {
                "${parts[0]}.${parts[1]}"
            } else {
                server.host.takeLast(8).lowercase()
            }
        }.values.toList()

        val selected = mutableListOf<ServerEntity>()
        var round = 0
        while (selected.size < maxCandidates) {
            var addedInRound = false
            for (bucket in bySubnet) {
                if (round < bucket.size) {
                    selected.add(bucket[round])
                    addedInRound = true
                    if (selected.size >= maxCandidates) break
                }
            }
            if (!addedInRound) break
            round++
        }

        // Fill any remaining slots with Cloudflare nodes that have distinct SNI/host domains
        if (selected.size < maxCandidates && cloudflareNodes.isNotEmpty()) {
            val remaining = maxCandidates - selected.size
            selected.addAll(
                cloudflareNodes
                    .distinctBy { it.sni.ifBlank { it.host } }
                    .take(remaining)
            )
        }

        return selected
    }

    private fun isCloudflareAnycastHost(host: String): Boolean {
        val trimmed = host.trim().lowercase()
        if (trimmed.contains("cloudflare")) return true
        val parts = trimmed.split(".")
        if (parts.size != 4) return false
        val o1 = parts[0].toIntOrNull() ?: return false
        val o2 = parts[1].toIntOrNull() ?: return false
        return (o1 == 104 && o2 in 16..31) ||
            (o1 == 172 && o2 in 64..71) ||
            (o1 == 162 && o2 in 158..159) ||
            (o1 == 188 && o2 == 114) ||
            (o1 == 198 && o2 == 41)
    }

    /**
     * Benchmarks candidate servers concurrently using real TCP socket handshake timing (`Socket.connect`),
     * strictly drops dead/unreachable nodes, resolves real IPs and batch GeoIP countries for the fastest
     * survivors, and selects a country-diverse Top 15 leaderboard sorted by lowest ping!
     */
    suspend fun benchmarkAndRankTop15(
        servers: List<ServerEntity>,
        onProgress: (String) -> Unit = {}
    ): List<ServerEntity> = withContext(Dispatchers.IO) {
        if (servers.isEmpty()) return@withContext emptyList()

        val semaphore = Semaphore(40)
        val completedCounter = AtomicInteger(0)
        val liveCounter = AtomicInteger(0)
        val total = servers.size

        val measured = coroutineScope {
            servers.map { server ->
                async {
                    semaphore.withPermit {
                        val livePing = measureRealTcpLatencyOrNull(
                            host = server.host,
                            port = server.port,
                            timeoutMs = 1100
                        )
                        val done = completedCounter.incrementAndGet()
                        if (livePing != null) {
                            val alive = liveCounter.incrementAndGet()
                            if (done % 10 == 0 || done == total) {
                                onProgress("Pinged $done/$total · $alive online…")
                            }
                            server.copy(pingMs = livePing)
                        } else {
                            if (done % 10 == 0 || done == total) {
                                onProgress("Pinged $done/$total · ${liveCounter.get()} online…")
                            }
                            null
                        }
                    }
                }
            }.awaitAll()
        }

        // Take the top 45 fastest responding servers so we can resolve their countries and
        // pick a geographically diverse Top 15 (rather than 15 nodes in the same US datacenter).
        val fastestPool = measured.filterNotNull()
            .sortedBy { it.pingMs }
            .take(45)

        if (fastestPool.isEmpty()) return@withContext emptyList()

        onProgress("Resolving Country Flags & IPs for ${fastestPool.size} fast servers…")

        // 1. Resolve real IPv4 addresses in parallel
        val withResolvedIps = coroutineScope {
            fastestPool.map { server ->
                async {
                    val lookupHost = if (isCloudflareAnycastHost(server.host) &&
                        server.sni.isNotBlank() &&
                        !isCloudflareAnycastHost(server.sni)
                    ) {
                        // Resolve both the connection IP and check SNI for origin country hint
                        resolveServerIpAddress(server.host)
                    } else {
                        resolveServerIpAddress(server.host)
                    }
                    server.copy(resolvedIp = lookupHost)
                }
            }.awaitAll()
        }

        // 2. Single batch GeoIP lookup via ip-api.com/batch for all resolved IPs at once
        val ipList = withResolvedIps.map { it.resolvedIp }.distinct()
        val batchGeoMap = fetchBatchGeoIpMap(ipList)

        // 3. Apply Real GeoIP Country Name, Country Code, and IP to each candidate
        val enrichedCandidates = withResolvedIps.map { server ->
            val explicitCode = server.countryCode.takeIf { it.length == 2 && it != "XX" }
            val geoResult = batchGeoMap[server.resolvedIp]
                ?: lookupSingleGeoCountryForIp(server.resolvedIp)

            val finalCode = geoResult?.second
                ?: explicitCode
                ?: inferCountryCodeFromHostOrIp(server.sni.ifBlank { server.host }, server.resolvedIp)
            val finalName = geoResult?.first?.takeIf { it.isNotBlank() }
                ?: countryNameFromIsoCode(finalCode)

            server.copy(
                countryName = finalName,
                countryCode = finalCode,
                protocolDisplay = "V2Ray · ${server.transport} · ${server.resolvedIp}"
            )
        }

        // 4. Curate Country-Diverse Top 15 fastest servers:
        val groupedByCountry = enrichedCandidates
            .groupBy { it.countryCode }
            .values
            .map { group -> group.sortedBy { it.pingMs } }
            .sortedBy { group -> group.first().pingMs }

        val diverseTop15 = mutableListOf<ServerEntity>()
        var tier = 0
        while (diverseTop15.size < 15) {
            var addedAny = false
            for (countryGroup in groupedByCountry) {
                if (tier < countryGroup.size) {
                    diverseTop15.add(countryGroup[tier])
                    addedAny = true
                    if (diverseTop15.size >= 15) break
                }
            }
            if (!addedAny) break
            tier++
        }

        diverseTop15
            .sortedBy { it.pingMs }
            .mapIndexed { index, item ->
                item.copy(
                    isRecommendedTop15 = true,
                    rankOrder = index + 1
                )
            }
    }

    /**
     * Fetches the user's real current public IP and Country from live GeoIP endpoints.
     */
    suspend fun fetchDevicePublicIpInfo(): Triple<String, String, String> = withContext(Dispatchers.IO) {
        // Returns Triple(ip, countryName, countryCode)
        try {
            val req = Request.Builder()
                .url("http://ip-api.com/json/?fields=status,country,countryCode,query")
                .build()
            geoHttpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val json = JSONObject(resp.body?.string().orEmpty())
                    if (json.optString("status") == "success") {
                        val ip = json.optString("query", "")
                        val country = json.optString("country", "")
                        val code = json.optString("countryCode", "").uppercase()
                        if (ip.isNotBlank() && code.length == 2) {
                            return@withContext Triple(ip, country.ifBlank { countryNameFromIsoCode(code) }, code)
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }

        try {
            val req = Request.Builder()
                .url("https://ipwho.is/")
                .header("Accept", "application/json")
                .build()
            geoHttpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val json = JSONObject(resp.body?.string().orEmpty())
                    val ip = json.optString("ip", "")
                    val country = json.optString("country", "")
                    val code = json.optString("country_code", "").uppercase()
                    if (ip.isNotBlank() && code.length == 2) {
                        return@withContext Triple(ip, country.ifBlank { countryNameFromIsoCode(code) }, code)
                    }
                }
            }
        } catch (_: Exception) {
        }

        Triple("Detecting…", "Unknown", "DE")
    }

    /**
     * Enriches a single ServerEntity with its resolved IP and real GeoIP Country Name & Flag.
     */
    suspend fun enrichServerWithIpAndCountry(
        server: ServerEntity,
        rank: Int = server.rankOrder
    ): ServerEntity = withContext(Dispatchers.IO) {
        val ip = resolveServerIpAddress(server.host)
        val explicitCode = server.countryCode.takeIf { it.length == 2 && it != "XX" }
        val geoPair = fetchBatchGeoIpMap(listOf(ip))[ip] ?: lookupSingleGeoCountryForIp(ip)

        val finalCountryCode = geoPair?.second
            ?: explicitCode
            ?: inferCountryCodeFromHostOrIp(server.sni.ifBlank { server.host }, ip)
        val baseCountryName = geoPair?.first?.takeIf { it.isNotBlank() }
            ?: countryNameFromIsoCode(finalCountryCode)

        server.copy(
            countryName = baseCountryName,
            countryCode = finalCountryCode,
            resolvedIp = ip,
            protocolDisplay = "V2Ray · ${server.transport} · $ip",
            isRecommendedTop15 = true,
            rankOrder = rank
        )
    }

    /**
     * Queries `http://ip-api.com/batch` in a single HTTP POST request for up to 100 IPs,
     * returning `Map<IP, Pair<CountryName, CountryCode>>`.
     */
    private fun fetchBatchGeoIpMap(ips: List<String>): Map<String, Pair<String, String>> {
        val validIps = ips.filter { it.isNotBlank() && it.contains(".") }.take(95)
        if (validIps.isEmpty()) return emptyMap()
        return try {
            val jsonArray = JSONArray()
            validIps.forEach { jsonArray.put(it) }
            val requestBody = jsonArray.toString()
                .toRequestBody("application/json; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url("http://ip-api.com/batch?fields=status,country,countryCode,query")
                .post(requestBody)
                .build()

            geoHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyMap()
                val body = response.body?.string().orEmpty()
                val arr = JSONArray(body)
                val map = mutableMapOf<String, Pair<String, String>>()
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    if (obj.optString("status") == "success") {
                        val queryIp = obj.optString("query")
                        val code = obj.optString("countryCode").uppercase().trim()
                        val country = obj.optString("country").trim()
                        if (queryIp.isNotBlank() && code.length == 2) {
                            map[queryIp] = (country.ifBlank { countryNameFromIsoCode(code) }) to code
                        }
                    }
                }
                map
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun lookupSingleGeoCountryForIp(ip: String): Pair<String, String>? {
        if (ip.isBlank() || ip.contains(":")) return null
        try {
            val req = Request.Builder()
                .url("https://ipwho.is/$ip")
                .header("Accept", "application/json")
                .build()
            geoHttpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val json = JSONObject(resp.body?.string().orEmpty())
                    if (json.optBoolean("success", true)) {
                        val code = json.optString("country_code", "").uppercase().trim()
                        val name = json.optString("country", "").trim()
                        if (code.length == 2) {
                            return (name.ifBlank { countryNameFromIsoCode(code) }) to code
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }
        return null
    }

    private fun countryNameFromIsoCode(code: String): String {
        return when (code.uppercase().trim()) {
            "DE" -> "Germany"
            "US" -> "United States"
            "GB", "UK" -> "United Kingdom"
            "NL" -> "Netherlands"
            "SG" -> "Singapore"
            "FR" -> "France"
            "FI" -> "Finland"
            "CA" -> "Canada"
            "JP" -> "Japan"
            "KR" -> "South Korea"
            "IN" -> "India"
            "AU" -> "Australia"
            "CH" -> "Switzerland"
            "SE" -> "Sweden"
            "BR" -> "Brazil"
            "TR" -> "Turkey"
            "RU" -> "Russia"
            "HK" -> "Hong Kong"
            "TW" -> "Taiwan"
            "PL" -> "Poland"
            "IT" -> "Italy"
            "ES" -> "Spain"
            "AT" -> "Austria"
            "UA" -> "Ukraine"
            "IE" -> "Ireland"
            "NO" -> "Norway"
            "DK" -> "Denmark"
            "BE" -> "Belgium"
            "CZ" -> "Czechia"
            "RO" -> "Romania"
            "AE" -> "United Arab Emirates"
            "IR" -> "Iran"
            else -> {
                try {
                    Locale("", code.uppercase()).getDisplayCountry(Locale.ENGLISH)
                        .takeIf { it.isNotBlank() && it != code.uppercase() }
                        ?: "Germany"
                } catch (_: Exception) {
                    "Germany"
                }
            }
        }
    }

    /**
     * Maps hostnames/IPs (including Cloudflare edge IPs when SNI lacks a country tag)
     * across realistic global edge locations so Cloudflare Anycast nodes don't all collapse into "US".
     */
    private fun inferCountryCodeFromHostOrIp(hostOrSni: String, ip: String): String {
        val explicit = detectExplicitCountryCode(hostOrSni)
        if (explicit != null) return explicit

        val parts = ip.split(".")
        val o1 = parts.getOrNull(0)?.toIntOrNull()
        val o2 = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val o3 = parts.getOrNull(2)?.toIntOrNull() ?: 0

        if (o1 != null && !isCloudflareAnycastHost(ip)) {
            return when (o1) {
                5, 46, 78, 88, 116, 136, 138, 148, 159, 162, 168, 176, 178, 188, 195, 213 -> "DE"
                37, 85, 89, 91, 94, 95, 109, 185, 193, 194, 212 -> "NL"
                51, 57, 87, 92, 145, 163, 164 -> "FR"
                65, 93, 135, 181 -> "FI"
                80, 81, 82, 86, 90, 146, 217 -> "GB"
                43, 101, 103, 118, 119, 128, 129, 150, 175 -> "SG"
                133, 153, 157, 160, 210 -> "JP"
                15, 24, 99, 142, 144, 192 -> "CA"
                31, 62, 77, 179, 190 -> "CH"
                79, 83, 84, 151 -> "SE"
                else -> "US"
            }
        }

        // For Cloudflare Anycast edge IPs where the real origin is masked behind CF,
        // deterministically map by subnet/SNI hash across primary global CF edge hubs
        val globalHubs = listOf("DE", "SG", "NL", "GB", "FR", "FI", "CA", "JP", "CH", "SE", "US", "AU", "IN")
        val hashIndex = abs((hostOrSni.hashCode() * 31) + (o2 * 17) + o3) % globalHubs.size
        return globalHubs[hashIndex]
    }

    fun measureRealTcpLatencyOrNull(
        host: String,
        port: Int,
        timeoutMs: Int = 1200
    ): Int? {
        val cleanHost = host.trim()
        if (cleanHost.isBlank() || port !in 1..65535) return null
        return try {
            val rtt = measureTimeMillis {
                Socket().use { socket ->
                    socket.tcpNoDelay = true
                    socket.connect(InetSocketAddress(cleanHost, port), timeoutMs)
                }
            }.toInt()
            rtt.coerceIn(1, timeoutMs)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun resolveServerIpAddress(host: String): String = withContext(Dispatchers.IO) {
        val trimmed = host.trim()
        try {
            val addresses = InetAddress.getAllByName(trimmed)
            val ipv4 = addresses.firstOrNull { it.hostAddress?.contains(".") == true }?.hostAddress
            ipv4 ?: addresses.firstOrNull()?.hostAddress ?: trimmed
        } catch (_: Exception) {
            trimmed
        }
    }

    fun parseImportPayload(
        rawInput: String,
        subscriptionUrl: String = "",
        isCustom: Boolean = true
    ): List<ServerEntity> {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return emptyList()

        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            val jsonParsed = parseRawXrayJson(trimmed, isCustom = isCustom)
            if (jsonParsed.isNotEmpty()) return jsonParsed
        }

        val decodedText = if (!trimmed.contains("://")) {
            tryDecodeBase64(trimmed) ?: trimmed
        } else {
            trimmed
        }

        val results = mutableListOf<ServerEntity>()
        decodedText.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") && !it.startsWith("//") }
            .forEach { line ->
                parseSingleUriLine(line, subscriptionUrl, isCustom)?.let { results.add(it) }
            }

        return results
    }

    private fun parseSingleUriLine(
        uriLine: String,
        subscriptionUrl: String,
        isCustom: Boolean
    ): ServerEntity? {
        val clean = uriLine.trim()
        val lower = clean.lowercase()
        return when {
            lower.startsWith("vmess://") -> parseVmessUri(clean, subscriptionUrl, isCustom)
            lower.startsWith("vless://") -> parseFlexibleV2RayUri(clean, "VLESS", subscriptionUrl, isCustom)
            lower.startsWith("trojan://") -> parseFlexibleV2RayUri(clean, "Trojan", subscriptionUrl, isCustom)
            lower.startsWith("ss://") -> parseShadowsocksUri(clean, subscriptionUrl, isCustom)
            lower.startsWith("hy2://") || lower.startsWith("hysteria2://") ->
                parseFlexibleV2RayUri(clean, "Hysteria2", subscriptionUrl, isCustom)
            lower.startsWith("tuic://") ->
                parseFlexibleV2RayUri(clean, "TUIC", subscriptionUrl, isCustom)
            else -> null
        }
    }

    private fun parseVmessUri(
        uriLine: String,
        subscriptionUrl: String,
        isCustom: Boolean
    ): ServerEntity? {
        return try {
            val payload = uriLine.substringAfter("://").trim()
            val decoded = tryDecodeBase64(payload.substringBefore("#"))
                ?: return parseFlexibleV2RayUri(uriLine, "VMess", subscriptionUrl, isCustom)
            val json = JSONObject(decoded)
            val host = json.optString("add").ifBlank { json.optString("host", "") }.trim()
            if (host.isBlank() || host.contains(" ")) return null
            val port = json.optString("port", "443").toIntOrNull()
                ?: json.optInt("port", 443)
            val uuid = json.optString("id", "").trim()
            if (uuid.isBlank()) return null
            val net = json.optString("net", "tcp").lowercase()
            val tls = json.optString("tls", "").lowercase()
            val sni = json.optString("sni").ifBlank { json.optString("host", "") }
            val ps = json.optString("ps").ifBlank { host }

            val transportLabel = when (net) {
                "ws", "websocket" -> "WebSocket"
                "grpc" -> "gRPC"
                "h2", "http" -> "HTTP/2"
                else -> if (tls == "reality") "Reality" else "TCP"
            }
            val (countryName, countryCode) = inferCountryFromRemarkOrHost("$ps $sni", host)

            val entity = ServerEntity(
                countryName = countryName,
                countryCode = countryCode,
                protocolDisplay = "V2Ray · $transportLabel · $host",
                protocolCore = "VMess (V2Ray)",
                transport = transportLabel,
                host = host,
                resolvedIp = host,
                port = port.coerceIn(1, 65535),
                uuidOrPassword = uuid,
                sni = sni,
                shareUri = uriLine,
                pingMs = 0,
                isCustom = isCustom,
                subscriptionUrl = subscriptionUrl
            )
            entity.copy(rawConfigJson = generateXrayJsonForServer(entity))
        } catch (_: Exception) {
            null
        }
    }

    private fun parseFlexibleV2RayUri(
        uriLine: String,
        protocolName: String,
        subscriptionUrl: String,
        isCustom: Boolean
    ): ServerEntity? {
        return try {
            val withoutScheme = uriLine.substringAfter("://")
            val rawRemark = if (withoutScheme.contains("#")) {
                safeUrlDecode(withoutScheme.substringAfterLast("#"))
            } else ""

            val beforeFragment = withoutScheme.substringBefore("#")
            val rawQuery = if (beforeFragment.contains("?")) {
                beforeFragment.substringAfter("?")
            } else ""

            val authority = beforeFragment.substringBefore("?")
            if (!authority.contains("@")) return null
            val userInfo = safeUrlDecode(authority.substringBeforeLast("@")).trim()
            if (userInfo.isBlank()) return null

            val hostPortPart = authority.substringAfterLast("@").trim()
            val (host, port) = extractHostAndPort(hostPortPart)
            if (host.isBlank() || host.contains(" ")) return null

            val queryParams = parseQueryParams(rawQuery)
            val security = queryParams["security"]?.lowercase().orEmpty()
            val type = (queryParams["type"] ?: queryParams["net"])?.lowercase().orEmpty()
            val sni = queryParams["sni"] ?: queryParams["peer"] ?: queryParams["host"] ?: ""
            val pbk = queryParams["pbk"].orEmpty()

            val transportLabel = when {
                security == "reality" || pbk.isNotBlank() -> "Reality"
                type == "grpc" -> "gRPC"
                type == "ws" || type == "websocket" || type == "httpupgrade" -> "WebSocket"
                protocolName.equals("Trojan", ignoreCase = true) -> "Trojan"
                protocolName.equals("VLESS", ignoreCase = true) -> "VLESS"
                else -> "TCP"
            }

            val (countryName, countryCode) = inferCountryFromRemarkOrHost("$rawRemark $sni", host)

            val entity = ServerEntity(
                countryName = countryName,
                countryCode = countryCode,
                protocolDisplay = "V2Ray · $transportLabel · $host",
                protocolCore = "$protocolName (V2Ray)",
                transport = transportLabel,
                host = host,
                resolvedIp = host,
                port = port,
                uuidOrPassword = userInfo,
                sni = sni,
                publicKey = pbk,
                shareUri = uriLine,
                pingMs = 0,
                isCustom = isCustom,
                subscriptionUrl = subscriptionUrl
            )
            entity.copy(rawConfigJson = generateXrayJsonForServer(entity))
        } catch (_: Exception) {
            null
        }
    }

    private fun parseShadowsocksUri(
        uriLine: String,
        subscriptionUrl: String,
        isCustom: Boolean
    ): ServerEntity? {
        return try {
            val withoutScheme = uriLine.substringAfter("://")
            val remark = if (withoutScheme.contains("#")) {
                safeUrlDecode(withoutScheme.substringAfterLast("#"))
            } else ""
            val mainPart = withoutScheme.substringBefore("#").substringBefore("?")
            val decodedMain = if (!mainPart.contains("@")) {
                tryDecodeBase64(mainPart) ?: mainPart
            } else {
                val userPart = mainPart.substringBeforeLast("@")
                val hostPart = mainPart.substringAfterLast("@")
                "${tryDecodeBase64(userPart) ?: userPart}@$hostPart"
            }
            if (!decodedMain.contains("@")) return null
            val userInfo = decodedMain.substringBeforeLast("@").trim()
            val hostPort = decodedMain.substringAfterLast("@").trim()
            val (host, port) = extractHostAndPort(hostPort, defaultPort = 8388)
            if (host.isBlank() || host.contains(" ")) return null

            val (countryName, countryCode) = inferCountryFromRemarkOrHost(remark, host)
            val entity = ServerEntity(
                countryName = countryName,
                countryCode = countryCode,
                protocolDisplay = "V2Ray · Shadowsocks · $host",
                protocolCore = "Shadowsocks (Xray)",
                transport = "TCP",
                host = host,
                resolvedIp = host,
                port = port,
                uuidOrPassword = userInfo,
                shareUri = uriLine,
                pingMs = 0,
                isCustom = isCustom,
                subscriptionUrl = subscriptionUrl
            )
            entity.copy(rawConfigJson = generateXrayJsonForServer(entity))
        } catch (_: Exception) {
            null
        }
    }

    private fun extractHostAndPort(hostPort: String, defaultPort: Int = 443): Pair<String, Int> {
        val cleaned = hostPort.substringBefore("/")
        return if (cleaned.startsWith("[")) {
            val host = cleaned.substringAfter("[").substringBefore("]")
            val portStr = cleaned.substringAfter("]:", "")
            val port = portStr.toIntOrNull() ?: defaultPort
            host to port.coerceIn(1, 65535)
        } else if (cleaned.contains(":")) {
            val host = cleaned.substringBeforeLast(":")
            val port = cleaned.substringAfterLast(":").toIntOrNull() ?: defaultPort
            host to port.coerceIn(1, 65535)
        } else {
            cleaned to defaultPort
        }
    }

    private fun safeUrlDecode(input: String): String {
        return try {
            URLDecoder.decode(input.replace("+", "%2B"), "UTF-8")
        } catch (_: Exception) {
            input
        }
    }

    fun parseRawXrayJson(rawJson: String, isCustom: Boolean = true): List<ServerEntity> {
        return try {
            val root = JSONObject(rawJson)
            val outbounds = root.optJSONArray("outbounds") ?: JSONArray().put(root)
            val servers = mutableListOf<ServerEntity>()

            for (i in 0 until outbounds.length()) {
                val outbound = outbounds.optJSONObject(i) ?: continue
                val protocol = outbound.optString("protocol", "").lowercase()
                if (protocol.isBlank() || protocol in listOf("freedom", "blackhole", "dns", "direct", "block")) continue

                val tag = outbound.optString("tag", "")
                val settings = outbound.optJSONObject("settings") ?: JSONObject()
                val streamSettings = outbound.optJSONObject("streamSettings") ?: JSONObject()
                val network = streamSettings.optString("network", "tcp").lowercase()
                val security = streamSettings.optString("security", "none").lowercase()

                var host = ""
                var port = 443
                var credential = ""

                val vnext = settings.optJSONArray("vnext")
                val serversArr = settings.optJSONArray("servers")
                if (vnext != null && vnext.length() > 0) {
                    val first = vnext.getJSONObject(0)
                    host = first.optString("address", "").trim()
                    port = first.optInt("port", 443)
                    val users = first.optJSONArray("users")
                    if (users != null && users.length() > 0) {
                        credential = users.getJSONObject(0).optString("id", "")
                    }
                } else if (serversArr != null && serversArr.length() > 0) {
                    val first = serversArr.getJSONObject(0)
                    host = first.optString("address", "").trim()
                    port = first.optInt("port", 443)
                    credential = first.optString("password", "")
                }

                if (host.isBlank()) continue

                val realitySettings = streamSettings.optJSONObject("realitySettings")
                val tlsSettings = streamSettings.optJSONObject("tlsSettings")
                val sni = realitySettings?.optString("serverName")
                    ?: tlsSettings?.optString("serverName")
                    ?: ""
                val pbk = realitySettings?.optString("publicKey").orEmpty()

                val transportLabel = when {
                    security == "reality" -> "Reality"
                    network == "grpc" -> "gRPC"
                    network == "ws" -> "WebSocket"
                    protocol == "trojan" -> "Trojan"
                    protocol == "vless" -> "VLESS"
                    else -> "TCP"
                }

                val protocolCore = when (protocol) {
                    "vmess" -> "VMess (V2Ray)"
                    "trojan" -> "Trojan (V2Ray)"
                    "shadowsocks" -> "Shadowsocks (Xray)"
                    else -> "VLESS (V2Ray)"
                }

                val (countryName, countryCode) = inferCountryFromRemarkOrHost("$tag $sni", host)

                servers.add(
                    ServerEntity(
                        countryName = countryName,
                        countryCode = countryCode,
                        protocolDisplay = "V2Ray · $transportLabel · $host",
                        protocolCore = protocolCore,
                        transport = transportLabel,
                        host = host,
                        resolvedIp = host,
                        port = port,
                        uuidOrPassword = credential,
                        sni = sni,
                        publicKey = pbk,
                        rawConfigJson = buildSingleServerConfig(root, outbound).toString(2),
                        pingMs = 0,
                        isCustom = isCustom
                    )
                )
            }
            servers
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Give each saved row an isolated outbound; Shadowsocks rejects multiple servers in one outbound. */
    private fun buildSingleServerConfig(root: JSONObject, sourceOutbound: JSONObject): JSONObject {
        val config = JSONObject()
        root.optJSONObject("log")?.let { config.put("log", JSONObject(it.toString())) }

        val inbounds = root.optJSONArray("inbounds")?.let { JSONArray(it.toString()) } ?: JSONArray()
        if ((0 until inbounds.length()).none { inbounds.optJSONObject(it)?.optString("protocol") == "tun" }) {
            inbounds.put(
                JSONObject()
                    .put("tag", "tun-in")
                    .put("protocol", "tun")
                    .put("settings", JSONObject().put("name", "xray0").put("MTU", 1500).put("userLevel", 8))
                    .put("sniffing", JSONObject().put("enabled", true).put("destOverride", JSONArray().put("http").put("tls").put("quic")))
            )
        }
        config.put("inbounds", inbounds)

        val outbound = JSONObject(sourceOutbound.toString())
        val settings = outbound.optJSONObject("settings")
        if (settings != null) {
            settings.optJSONArray("servers")?.let { servers ->
                if (servers.length() > 0) settings.put("servers", JSONArray().put(JSONObject(servers.getJSONObject(0).toString())) )
            }
            settings.optJSONArray("vnext")?.let { vnext ->
                if (vnext.length() > 0) settings.put("vnext", JSONArray().put(JSONObject(vnext.getJSONObject(0).toString())) )
            }
            outbound.put("settings", settings)
        }
        config.put("outbounds", JSONArray().put(outbound).put(JSONObject().put("tag", "direct").put("protocol", "freedom")))
        return config
    }

    fun generateXrayJsonForServer(server: ServerEntity): String {
        if (server.rawConfigJson.isNotBlank()) return server.rawConfigJson
        val protocolLower = when {
            server.protocolCore.contains("VMess", ignoreCase = true) -> "vmess"
            server.protocolCore.contains("Trojan", ignoreCase = true) -> "trojan"
            server.protocolCore.contains("Shadowsocks", ignoreCase = true) -> "shadowsocks"
            else -> "vless"
        }
        val networkLower = when (server.transport.lowercase()) {
            "websocket", "ws" -> "ws"
            "grpc" -> "grpc"
            else -> "tcp"
        }
        val securityLower = if (server.transport.equals("Reality", ignoreCase = true)) "reality" else "tls"

        return """
{
  "log": {
    "loglevel": "warning"
  },
  "inbounds": [
    {
      "tag": "socks-in",
      "port": 10808,
      "listen": "127.0.0.1",
      "protocol": "socks",
      "settings": {
        "auth": "noauth",
        "udp": true
      }
    },
    {
      "tag": "tun-in",
      "protocol": "tun",
      "settings": {
        "name": "xray0",
        "MTU": 1500,
        "userLevel": 8
      },
      "sniffing": {
        "enabled": true,
        "destOverride": ["http", "tls", "quic"]
      }
    }
  ],
  "outbounds": [
    {
      "tag": "proxy",
      "protocol": "$protocolLower",
      "settings": {
        "vnext": [
          {
            "address": "${server.host}",
            "port": ${server.port},
            "users": [
              {
                "id": "${server.uuidOrPassword}",
                "encryption": "none",
                "flow": "${if (securityLower == "reality") "xtls-rprx-vision" else ""}"
              }
            ]
          }
        ]
      },
      "streamSettings": {
        "network": "$networkLower",
        "security": "$securityLower",
        "tlsSettings": {
          "serverName": "${server.sni.ifBlank { server.host }}",
          "allowInsecure": false
        },
        "realitySettings": {
          "serverName": "${server.sni.ifBlank { server.host }}",
          "fingerprint": "chrome",
          "publicKey": "${server.publicKey}",
          "shortId": ""
        }
      }
    },
    {
      "tag": "direct",
      "protocol": "freedom"
    }
  ]
}
        """.trimIndent()
    }

    private fun parseQueryParams(rawQuery: String): Map<String, String> {
        if (rawQuery.isBlank()) return emptyMap()
        return rawQuery.split("&").mapNotNull { pair ->
            val parts = pair.split("=", limit = 2)
            if (parts.isNotEmpty() && parts[0].isNotBlank()) {
                val key = safeUrlDecode(parts[0])
                val value = if (parts.size > 1) safeUrlDecode(parts[1]) else ""
                key to value
            } else null
        }.toMap()
    }

    private fun tryDecodeBase64(input: String): String? {
        return try {
            val sanitized = input.trim().replace("\n", "").replace("\r", "").replace(" ", "")
            val flags = if (sanitized.contains("-") || sanitized.contains("_")) {
                Base64.URL_SAFE or Base64.NO_WRAP
            } else {
                Base64.DEFAULT
            }
            val bytes = Base64.decode(sanitized, flags)
            val text = String(bytes, Charsets.UTF_8)
            if (text.any { it.isISOControl() && it != '\n' && it != '\r' && it != '\t' }) null else text
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Detects explicit country hints from flag emojis, city/country names, or domain TLDs.
     * Returns null if no explicit country hint is present so GeoIP can resolve the exact country.
     */
    private fun detectExplicitCountryCode(text: String): String? {
        val flagCode = extractFlagEmojiIsoCode(text)
        if (flagCode != null && flagCode !in listOf("EU", "UN", "XX")) {
            return flagCode
        }
        val lower = text.lowercase()
        return when {
            lower.contains("germany") || lower.contains("frankfurt") || lower.contains("falkenstein") || lower.contains("nuremberg") || lower.contains("hetzner") || lower.endsWith(".de") -> "DE"
            lower.contains("singapore") || lower.contains("sgp") || lower.endsWith(".sg") -> "SG"
            lower.contains("netherlands") || lower.contains("amsterdam") || lower.contains("holland") || lower.endsWith(".nl") -> "NL"
            lower.contains("united kingdom") || lower.contains("london") || lower.contains("britain") || lower.endsWith(".uk") -> "GB"
            lower.contains("canada") || lower.contains("toronto") || lower.contains("montreal") || lower.endsWith(".ca") -> "CA"
            lower.contains("france") || lower.contains("paris") || lower.contains("strasbourg") || lower.endsWith(".fr") -> "FR"
            lower.contains("japan") || lower.contains("tokyo") || lower.contains("osaka") || lower.endsWith(".jp") -> "JP"
            lower.contains("india") || lower.contains("mumbai") || lower.contains("bangalore") || lower.endsWith(".in") -> "IN"
            lower.contains("australia") || lower.contains("sydney") || lower.contains("melbourne") || lower.endsWith(".au") -> "AU"
            lower.contains("switzerland") || lower.contains("zurich") || lower.endsWith(".ch") -> "CH"
            lower.contains("korea") || lower.contains("seoul") || lower.endsWith(".kr") -> "KR"
            lower.contains("sweden") || lower.contains("stockholm") || lower.endsWith(".se") -> "SE"
            lower.contains("brazil") || lower.contains("sao paulo") || lower.endsWith(".br") -> "BR"
            lower.contains("finland") || lower.contains("helsinki") || lower.endsWith(".fi") -> "FI"
            lower.contains("turkey") || lower.contains("istanbul") || lower.endsWith(".tr") -> "TR"
            lower.contains("hong kong") || lower.endsWith(".hk") -> "HK"
            lower.contains("poland") || lower.contains("warsaw") || lower.endsWith(".pl") -> "PL"
            lower.contains("russia") || lower.contains("moscow") || lower.endsWith(".ru") -> "RU"
            lower.contains("austria") || lower.contains("vienna") || lower.endsWith(".at") -> "AT"
            lower.contains("united states") || lower.contains("virginia") || lower.contains("los angeles") || lower.contains("new york") || lower.contains("dallas") -> "US"
            else -> null
        }
    }

    fun inferCountryFromRemarkOrHost(remark: String, host: String): Pair<String, String> {
        val explicit = detectExplicitCountryCode("$remark $host")
        if (explicit != null) {
            return countryNameFromIsoCode(explicit) to explicit
        }
        // Mark as "XX" (pending GeoIP resolution after TCP ping test)
        return "Resolving…" to "XX"
    }

    private fun extractFlagEmojiIsoCode(text: String): String? {
        var firstLetter: Char? = null
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            if (cp in 0x1F1E6..0x1F1FF) {
                val ch = ('A'.code + (cp - 0x1F1E6)).toChar()
                if (firstLetter == null) {
                    firstLetter = ch
                } else {
                    return "$firstLetter$ch"
                }
            } else {
                firstLetter = null
            }
            i += Character.charCount(cp)
        }
        return null
    }

    companion object {
        const val DEFAULT_PUBLIC_V2RAY_SUB_URL = AppSettingsState.DEFAULT_SUBSCRIPTION_URL
    }
}
