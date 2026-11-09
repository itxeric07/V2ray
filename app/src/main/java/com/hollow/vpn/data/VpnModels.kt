package com.hollow.vpn.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "vpn_servers")
data class ServerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val countryName: String,
    val countryCode: String,
    val protocolDisplay: String, // e.g., "V2Ray · Reality · 185.216.34.12"
    val protocolCore: String, // e.g., "VLESS (V2Ray)", "VMess (V2Ray)", "Trojan (V2Ray)"
    val transport: String, // "Reality", "VLESS", "Trojan", "gRPC", "WebSocket", "TCP"
    val host: String,
    val resolvedIp: String = "",
    val port: Int,
    val uuidOrPassword: String,
    val sni: String = "",
    val publicKey: String = "",
    val rawConfigJson: String = "",
    val shareUri: String = "",
    val pingMs: Int,
    val isRecommendedTop15: Boolean = true,
    val rankOrder: Int = 99,
    val isSelected: Boolean = false,
    val isCustom: Boolean = false,
    val subscriptionUrl: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "vpn_subscriptions")
data class SubscriptionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val name: String = "Custom Subscription",
    val autoUpdate: Boolean = true,
    val updateIntervalHours: Int = 1,
    val replaceExisting: Boolean = true,
    val lastFetchedAt: Long = System.currentTimeMillis(),
    val serverCount: Int = 0
)

enum class ServerFilter(val label: String) {
    ALL("All"),
    RECOMMENDED("Recommended"),
    LOW_PING("Low Ping"),
    HIGH_SPEED("High Speed")
}

data class AppSettingsState(
    val autoConfigure: Boolean = true,
    val updateIntervalLabel: String = "1 hour",
    val protocolPreference: String = "Auto (V2Ray)",
    val routingMode: String = "Global",
    val dnsMode: String = "Auto (DoH)",
    val tunMode: Boolean = true,
    val ipv6Enabled: Boolean = false,
    val startOnAppOpen: Boolean = false,
    val hideFromRecentApps: Boolean = false,
    val subAutoUpdate: Boolean = true,
    val subUpdateInterval: String = "1 hour",
    val subReplaceExisting: Boolean = true,
    val activeSubscriptionUrl: String = DEFAULT_SUBSCRIPTION_URL
) {
    companion object {
        const val DEFAULT_SUBSCRIPTION_URL =
            "https://raw.githubusercontent.com/mirrorrepomagic/free-v2ray-public-list/refs/heads/main/V2Ray-Config-By-EbraSha.txt"
    }
}

class VpnPreferencesStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("v2ray_vpn_prefs", Context.MODE_PRIVATE)

    fun loadSettings(): AppSettingsState {
        return AppSettingsState(
            autoConfigure = prefs.getBoolean("auto_configure", true),
            updateIntervalLabel = prefs.getString("update_interval", "1 hour") ?: "1 hour",
            protocolPreference = prefs.getString("protocol_pref", "Auto (V2Ray)") ?: "Auto (V2Ray)",
            routingMode = prefs.getString("routing_mode", "Global") ?: "Global",
            dnsMode = prefs.getString("dns_mode", "Auto (DoH)") ?: "Auto (DoH)",
            tunMode = prefs.getBoolean("tun_mode", true),
            ipv6Enabled = prefs.getBoolean("ipv6_enabled", false),
            startOnAppOpen = prefs.getBoolean("start_on_open_v2", false),
            hideFromRecentApps = prefs.getBoolean("hide_recents", false),
            subAutoUpdate = prefs.getBoolean("sub_auto_update", true),
            subUpdateInterval = prefs.getString("sub_update_interval", "1 hour") ?: "1 hour",
            subReplaceExisting = prefs.getBoolean("sub_replace_existing", true),
            activeSubscriptionUrl = prefs.getString(
                "active_sub_url",
                AppSettingsState.DEFAULT_SUBSCRIPTION_URL
            ) ?: AppSettingsState.DEFAULT_SUBSCRIPTION_URL
        )
    }

    fun saveSettings(state: AppSettingsState) {
        prefs.edit()
            .putBoolean("auto_configure", state.autoConfigure)
            .putString("update_interval", state.updateIntervalLabel)
            .putString("protocol_pref", state.protocolPreference)
            .putString("routing_mode", state.routingMode)
            .putString("dns_mode", state.dnsMode)
            .putBoolean("tun_mode", state.tunMode)
            .putBoolean("ipv6_enabled", state.ipv6Enabled)
            .putBoolean("start_on_open_v2", state.startOnAppOpen)
            .putBoolean("hide_recents", state.hideFromRecentApps)
            .putBoolean("sub_auto_update", state.subAutoUpdate)
            .putString("sub_update_interval", state.subUpdateInterval)
            .putBoolean("sub_replace_existing", state.subReplaceExisting)
            .putString("active_sub_url", state.activeSubscriptionUrl)
            .apply()
    }
}

@Dao
interface VpnDao {
    @Query("SELECT * FROM vpn_servers ORDER BY isSelected DESC, pingMs ASC")
    fun observeAllServers(): Flow<List<ServerEntity>>

    @Query("SELECT * FROM vpn_servers ORDER BY pingMs ASC")
    suspend fun getAllServersOnce(): List<ServerEntity>

    @Query("SELECT * FROM vpn_servers WHERE isSelected = 1 LIMIT 1")
    fun observeSelectedServer(): Flow<ServerEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertServers(servers: List<ServerEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertServer(server: ServerEntity): Long

    @Update
    suspend fun updateServer(server: ServerEntity)

    @Query("UPDATE vpn_servers SET isSelected = 0")
    suspend fun clearSelection()

    @Query("UPDATE vpn_servers SET isSelected = 1 WHERE id = :serverId")
    suspend fun markSelected(serverId: Long)

    @Query("DELETE FROM vpn_servers WHERE id = :serverId")
    suspend fun deleteServerById(serverId: Long)

    @Query("DELETE FROM vpn_servers WHERE isCustom = 0")
    suspend fun deleteFetchedServers()

    @Query("DELETE FROM vpn_servers")
    suspend fun deleteAllServers()

    @Query("SELECT * FROM vpn_subscriptions ORDER BY lastFetchedAt DESC")
    fun observeSubscriptions(): Flow<List<SubscriptionEntity>>

    @Query("SELECT * FROM vpn_subscriptions ORDER BY lastFetchedAt DESC")
    suspend fun getSubscriptionsOnce(): List<SubscriptionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubscription(subscription: SubscriptionEntity): Long

    @Query("DELETE FROM vpn_subscriptions")
    suspend fun deleteAllSubscriptions()
}

@Database(
    entities = [ServerEntity::class, SubscriptionEntity::class],
    version = 6,
    exportSchema = false
)
abstract class VpnDatabase : RoomDatabase() {
    abstract fun vpnDao(): VpnDao

    companion object {
        @Volatile
        private var INSTANCE: VpnDatabase? = null

        fun getInstance(context: Context): VpnDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    VpnDatabase::class.java,
                    "v2ray_xray_vpn.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
