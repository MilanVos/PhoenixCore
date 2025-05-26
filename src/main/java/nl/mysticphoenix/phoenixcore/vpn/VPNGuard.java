package nl.mysticphoenix.phoenixcore.vpn;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import nl.mysticphoenix.phoenixcore.PhoenixCore;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class VPNGuard implements Listener {

    private final PhoenixCore plugin;
    private final Map<String, VPNCheckResult> ipCache = new HashMap<>();
    private final Map<UUID, String> playerIpMap = new HashMap<>();
    private final String apiKey;
    private final boolean enabled;
    private final boolean bypassWithPermission;
    private final String kickMessage;
    private final long cacheTime;
    private final boolean logResults;
    private final boolean asyncCheck;

    public VPNGuard(PhoenixCore plugin) {
        this.plugin = plugin;

        FileConfiguration config = plugin.getConfig();
        this.enabled = config.getBoolean("vpnguard.enabled", true);
        this.apiKey = config.getString("vpnguard.api-key", "");
        this.bypassWithPermission = config.getBoolean("vpnguard.bypass-with-permission", true);
        this.kickMessage = ChatColor.translateAlternateColorCodes('&',
                config.getString("vpnguard.kick-message",
                        "&cVPN/Proxy detected!\n&cPlease disable your VPN/Proxy and try again."));
        this.cacheTime = config.getLong("vpnguard.cache-time", 120) * 60 * 1000;
        this.logResults = config.getBoolean("vpnguard.log-results", true);
        this.asyncCheck = config.getBoolean("vpnguard.async-check", true);

        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::cleanupCache, 20 * 60, 20 * 60);

        plugin.getLogger().info("VPNGuard initialized. VPN protection is " + (enabled ? "enabled" : "disabled"));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
        if (!enabled) return;

        String ip = event.getAddress().getHostAddress();
        UUID uuid = event.getUniqueId();
        String name = event.getName();

        playerIpMap.put(uuid, ip);


        VPNCheckResult cachedResult = ipCache.get(ip);
        if (cachedResult != null && !cachedResult.isExpired()) {
            if (cachedResult.isVpnDetected()) {
                if (logResults) {
                    plugin.getLogger().warning("VPN detected for player " + name + " (IP: " + ip + ") - Using cached result");
                }
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, kickMessage);
            }
            return;
        }

        if (asyncCheck) {
            return;
        }

        try {
            VPNCheckResult result = checkIpSync(ip);
            ipCache.put(ip, result);

            if (result.isVpnDetected()) {
                if (logResults) {
                    plugin.getLogger().warning("VPN detected for player " + name + " (IP: " + ip + ")");
                }
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, kickMessage);
            }
        } catch (IOException e) {
            plugin.getLogger().severe("Error checking VPN for IP " + ip + ": " + e.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!enabled || !asyncCheck) return;

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (bypassWithPermission && player.hasPermission("phoenixcore.vpnguard.bypass")) {
            if (logResults) {
                plugin.getLogger().info("Player " + player.getName() + " bypassed VPN check due to permission");
            }
            return;
        }

        String ip = playerIpMap.get(uuid);
        if (ip == null) {
            ip = player.getAddress().getAddress().getHostAddress();
            playerIpMap.put(uuid, ip);
        }

        VPNCheckResult cachedResult = ipCache.get(ip);
        if (cachedResult != null && !cachedResult.isExpired()) {
            if (cachedResult.isVpnDetected()) {
                if (logResults) {
                    plugin.getLogger().warning("VPN detected for player " + player.getName() + " (IP: " + ip + ") - Using cached result");
                }
                player.kickPlayer(kickMessage);
            }
            return;
        }

        final String finalIp = ip;
        final Player finalPlayer = player;
        checkIpAsync(finalIp).thenAccept(result -> {
            ipCache.put(finalIp, result);

            if (result.isVpnDetected()) {
                if (logResults) {
                    plugin.getLogger().warning("VPN detected for player " + finalPlayer.getName() + " (IP: " + finalIp + ")");
                }

                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (finalPlayer.isOnline()) {
                        finalPlayer.kickPlayer(kickMessage);
                    }
                });
            }
        }).exceptionally(e -> {            plugin.getLogger().severe("Error checking VPN for IP " + finalIp + ": " + e.getMessage());
            return null;
        });
    }

    private VPNCheckResult checkIpSync(String ip) throws IOException {
        URL url = new URL("http://v2.api.iphub.info/ip/" + ip);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("X-Key", apiKey);

        int responseCode = connection.getResponseCode();

        if (responseCode == 200) {
            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String inputLine;
            StringBuilder response = new StringBuilder();

            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();

            JsonObject jsonResponse = JsonParser.parseString(response.toString()).getAsJsonObject();
            int blockValue = jsonResponse.get("block").getAsInt();
            String countryCode = jsonResponse.get("countryCode").getAsString();
            String isp = jsonResponse.get("isp").getAsString();

            // block: 0 = not a proxy/VPN, 1 = possibly a proxy/VPN, 2 = confirmed proxy/VPN
            boolean isVpn = blockValue == 1 || blockValue == 2;

            return new VPNCheckResult(ip, isVpn, countryCode, isp, System.currentTimeMillis() + cacheTime);
        } else {
            throw new IOException("API request failed with response code: " + responseCode);
        }
    }

    private CompletableFuture<VPNCheckResult> checkIpAsync(String ip) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return checkIpSync(ip);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private void cleanupCache() {
        long now = System.currentTimeMillis();
        ipCache.entrySet().removeIf(entry -> entry.getValue().getExpiryTime() < now);
    }

    // Inner class to store VPN check results
    private static class VPNCheckResult {
        private final String ip;
        private final boolean vpnDetected;
        private final String countryCode;
        private final String isp;
        private final long expiryTime;

        public VPNCheckResult(String ip, boolean vpnDetected, String countryCode, String isp, long expiryTime) {
            this.ip = ip;
            this.vpnDetected = vpnDetected;
            this.countryCode = countryCode;
            this.isp = isp;
            this.expiryTime = expiryTime;
        }

        public String getIp() {
            return ip;
        }

        public boolean isVpnDetected() {
            return vpnDetected;
        }

        public String getCountryCode() {
            return countryCode;
        }

        public String getIsp() {
            return isp;
        }

        public long getExpiryTime() {
            return expiryTime;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() > expiryTime;
        }
    }
}