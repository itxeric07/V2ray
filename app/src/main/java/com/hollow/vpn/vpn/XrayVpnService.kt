package com.hollow.vpn.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Base64
import android.util.Log
import androidx.core.app.NotificationCompat
import com.hollow.vpn.MainActivity
import com.hollow.vpn.R
import go.Seq
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray
import java.io.File
import java.security.MessageDigest

/** Android VPN service backed by Xray-core and the native tun2socks bridge. */
class XrayVpnService : VpnService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var vpnInterface: ParcelFileDescriptor? = null
    private var tunnelJob: Job? = null
    private var tun2socksProcess: Process? = null
    private var coreController: CoreController? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISCONNECT) {
            stopVpnTunnel()
            stopSelf()
            return START_NOT_STICKY
        }
        if (prepare(this) != null) {
            fail("VPN permission not granted by Android OS")
            stopSelf()
            return START_NOT_STICKY
        }
        val country = intent?.getStringExtra(EXTRA_SERVER_COUNTRY) ?: "Server"
        val host = intent?.getStringExtra(EXTRA_SERVER_HOST).orEmpty()
        val port = intent?.getIntExtra(EXTRA_SERVER_PORT, 443) ?: 443
        val protocol = intent?.getStringExtra(EXTRA_SERVER_PROTOCOL) ?: "VLESS (V2Ray)"
        val xrayJson = intent?.getStringExtra(EXTRA_XRAY_JSON).orEmpty()
        val enableIpv6 = intent?.getBooleanExtra(EXTRA_ENABLE_IPV6, false) ?: false
        val dnsMode = intent?.getStringExtra(EXTRA_DNS_MODE) ?: "Auto (DoH)"
        val routingMode = intent?.getStringExtra(EXTRA_ROUTING_MODE) ?: "Global"
        if (host.isBlank() || xrayJson.isBlank()) {
            fail("The selected server has no usable Xray configuration")
            stopSelf()
            return START_NOT_STICKY
        }
        startVpnTunnel(country, host, port, protocol, xrayJson, enableIpv6, dnsMode, routingMode)
        return START_NOT_STICKY
    }

    private fun startVpnTunnel(country: String, host: String, port: Int, protocol: String, xrayJson: String, enableIpv6: Boolean, dnsMode: String, routingMode: String) {
        stopVpnTunnel()
        createNotificationChannel()
        try {
            startForeground(NOTIFICATION_ID, buildForegroundNotification(country, host, protocol))
        } catch (e: Exception) {
            fail("Could not start VPN notification: ${e.message}")
            stopSelf()
            return
        }
        tunnelJob = serviceScope.launch {
            try {
                val builder = Builder()
                    .setSession("Hollow VPN - $country ($host)")
                    .setMtu(VPN_MTU)
                    .addAddress("10.26.0.1", 30)
                    .addDnsServer(selectedDns(dnsMode))
                    .addDisallowedApplication(packageName)
                if (!routingMode.contains("Direct Only", ignoreCase = true)) {
                    builder.addRoute("0.0.0.0", 0)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) builder.setMetered(false)
                if (enableIpv6) {
                    builder.addAddress("fd00:26::1", 126)
                    builder.addRoute("::", 0)
                }
                if (routingMode.contains("Direct Only", ignoreCase = true)) {
                    builder.addRoute("10.26.0.0", 30)
                }
                vpnInterface?.close()
                vpnInterface = builder.establish() ?: error("Android OS rejected TUN interface creation")

                initializeXray()
                val core = Libv2ray.newCoreController(CoreCallback())
                // tun2socks owns the Android TUN descriptor; Xray consumes local SOCKS only.
                core.startLoop(xrayJson, 0)
                coreController = core
                startTun2Socks()
                _lastError.value = null
                _isServiceRunning.value = true
                while (isActive && vpnInterface != null) delay(500L)
            } catch (e: Exception) {
                fail("Connection failed: ${e.message ?: "native VPN engine unavailable"}")
                stopVpnTunnel()
                stopSelf()
            }
        }
    }

    private fun initializeXray() {
        Seq.setContext(this)
        val androidId = android.provider.Settings.Secure.getString(contentResolver, android.provider.Settings.Secure.ANDROID_ID).orEmpty()
        val deviceId = Base64.encodeToString(
            MessageDigest.getInstance("SHA-256").digest(androidId.toByteArray()).copyOf(32),
            Base64.NO_PADDING or Base64.URL_SAFE or Base64.NO_WRAP
        )
        val assetPath = (getExternalFilesDir("assets") ?: getDir("assets", MODE_PRIVATE)).absolutePath
        Libv2ray.initCoreEnv(assetPath, deviceId)
    }

    private fun startTun2Socks() {
        val socketPath = File(filesDir, TUN_SOCKET_NAME).absolutePath
        File(socketPath).delete()
        val executable = resolveTun2SocksExecutable()
        check(executable.exists() && executable.canExecute()) {
            "tun2socks is unavailable for ABIs ${Build.SUPPORTED_ABIS.joinToString()}; checked ${executable.absolutePath}"
        }
        val command = listOf(
            executable.absolutePath, "--netif-ipaddr", "10.26.0.2", "--netif-netmask", "255.255.255.252",
            "--socks-server-addr", "127.0.0.1:$SOCKS_PORT", "--tunmtu", VPN_MTU.toString(),
            "--sock-path", socketPath, "--enable-udprelay", "--loglevel", "notice"
        )
        tun2socksProcess = ProcessBuilder(command).directory(filesDir).redirectErrorStream(true).start()
        sendTunFd(socketPath)
    }

    /**
     * Prefer Android's extracted native-library directory. Some OEMs and install
     * paths do not expose manually launched .so executables there, so keep an
     * ABI-specific asset fallback and copy it into app-private storage.
     */
    private fun resolveTun2SocksExecutable(): File {
        val nativeExecutable = File(applicationInfo.nativeLibraryDir, TUN2SOCKS_NAME)
        if (nativeExecutable.exists()) {
            nativeExecutable.setExecutable(true, false)
            return nativeExecutable
        }

        val abi = Build.SUPPORTED_ABIS.firstOrNull { it == "arm64-v8a" || it == "armeabi-v7a" }
            ?: Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
        val target = File(filesDir, "tun2socks/$abi/$TUN2SOCKS_NAME")
        if (!target.exists()) {
            target.parentFile?.mkdirs()
            assets.open("tun2socks/$abi/$TUN2SOCKS_NAME").use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        }
        target.setExecutable(true, false)
        return target
    }

    private fun sendTunFd(socketPath: String) {
        val fd = vpnInterface?.fileDescriptor ?: error("TUN descriptor is unavailable")
        serviceScope.launch {
            repeat(8) { attempt ->
                try {
                    delay(50L shl attempt)
                    LocalSocket().use { socket ->
                        socket.connect(LocalSocketAddress(socketPath, LocalSocketAddress.Namespace.FILESYSTEM))
                        socket.setFileDescriptorsForSend(arrayOf(fd))
                        socket.outputStream.write(0)
                    }
                    return@launch
                } catch (e: Exception) {
                    if (attempt == 7) fail("tun2socks could not receive the VPN interface: ${e.message}")
                }
            }
        }
    }

    private fun selectedDns(mode: String): String = when {
        mode.contains("8.8.8.8") -> "8.8.8.8"
        mode.contains("9.9.9.9") -> "9.9.9.9"
        mode.contains("94.140.14.14") -> "94.140.14.14"
        else -> "1.1.1.1"
    }

    private fun stopVpnTunnel() {
        _isServiceRunning.value = false
        tunnelJob?.cancel()
        tunnelJob = null
        try { tun2socksProcess?.destroy() } catch (_: Exception) {}
        tun2socksProcess = null
        try { coreController?.stopLoop() } catch (_: Exception) {}
        coreController = null
        try { vpnInterface?.close() } catch (_: Exception) {}
        vpnInterface = null
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun fail(message: String) {
        Log.e(TAG, message)
        _lastError.value = message
        _isServiceRunning.value = false
    }

    override fun onRevoke() {
        stopVpnTunnel()
        stopSelf()
        super.onRevoke()
    }

    override fun onDestroy() {
        stopVpnTunnel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Hollow VPN Tunnel", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun buildForegroundNotification(country: String, host: String, protocol: String): Notification {
        val openIntent = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP }, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val disconnectIntent = PendingIntent.getService(this, 1, Intent(this, XrayVpnService::class.java).apply { action = ACTION_DISCONNECT }, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Hollow VPN · $country")
            .setContentText("$protocol · $host")
            .setContentIntent(openIntent)
            .addAction(0, "Disconnect", disconnectIntent)
            .setOngoing(true)
            .build()
    }

    private inner class CoreCallback : CoreCallbackHandler {
        override fun startup(): Long = 0
        override fun shutdown(): Long = 0
        override fun onEmitStatus(level: Long, message: String?): Long = 0
    }

    companion object {
        const val ACTION_CONNECT = "com.hollow.vpn.vpn.ACTION_CONNECT"
        const val ACTION_DISCONNECT = "com.hollow.vpn.vpn.ACTION_DISCONNECT"
        const val EXTRA_SERVER_COUNTRY = "extra_server_country"
        const val EXTRA_SERVER_HOST = "extra_server_host"
        const val EXTRA_SERVER_PORT = "extra_server_port"
        const val EXTRA_SERVER_PROTOCOL = "extra_server_protocol"
        const val EXTRA_XRAY_JSON = "extra_xray_json"
        const val EXTRA_ENABLE_IPV6 = "extra_enable_ipv6"
        const val EXTRA_DNS_MODE = "extra_dns_mode"
        const val EXTRA_ROUTING_MODE = "extra_routing_mode"
        private const val CHANNEL_ID = "v2ray_vpn_tunnel_channel"
        private const val NOTIFICATION_ID = 2048
        private const val VPN_MTU = 1500
        private const val SOCKS_PORT = 10808
        private const val TUN2SOCKS_NAME = "libtun2socks.so"
        private const val TUN_SOCKET_NAME = "tun2socks.sock"
        private const val TAG = "XrayVpnService"
        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()
        private val _lastError = MutableStateFlow<String?>(null)
        val lastError: StateFlow<String?> = _lastError.asStateFlow()
    }
}
