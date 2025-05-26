package nl.mysticphoenix.phoenixcore.listeners;

import nl.mysticphoenix.phoenixcore.PhoenixCore;
import nl.mysticphoenix.phoenixcore.managers.PunishmentManager;
import nl.mysticphoenix.phoenixcore.models.Punishment;
import nl.mysticphoenix.phoenixcore.models.PunishmentType;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.text.SimpleDateFormat;
import java.util.List;

public class PunishmentListener implements Listener {

    private final PhoenixCore plugin;
    private final PunishmentManager punishmentManager;

    public PunishmentListener(PhoenixCore plugin) {
        this.plugin = plugin;
        this.punishmentManager = plugin.getPunishmentManager();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        punishmentManager.loadPlayerPunishments(player.getUniqueId());

        List<Punishment> activePunishments = plugin.getStorageManager().getActivePunishments(player.getUniqueId());

        for (Punishment punishment : activePunishments) {
            if (punishment.getType() == PunishmentType.WARNING && !punishment.isExpired()) {
                player.sendMessage(ChatColor.RED + "=== WARNING ===");
                player.sendMessage(ChatColor.RED + "You have been warned by " + punishment.getIssuerName());
                player.sendMessage(ChatColor.RED + "Reason: " + punishment.getReason());

                if (!punishment.isPermanent()) {
                    player.sendMessage(ChatColor.RED + "This warning will expire in: " +
                                     PunishmentManager.formatDuration(punishment.getTimeRemaining()));
                }

                player.sendMessage(ChatColor.RED + "===============");
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();

        if (punishmentManager.isMuted(player.getUniqueId())) {
            event.setCancelled(true);

            Punishment mute = punishmentManager.getActiveMute(player.getUniqueId());

            if (mute != null) {
                player.sendMessage(ChatColor.RED + "You are muted and cannot chat.");
                player.sendMessage(ChatColor.RED + "Reason: " + mute.getReason());

                if (!mute.isPermanent()) {
                    player.sendMessage(ChatColor.RED + "Your mute will expire in: " +
                                     PunishmentManager.formatDuration(mute.getTimeRemaining()));
                    player.sendMessage(ChatColor.RED + "Expiry date: " +
                                     new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(mute.getExpiryDate()));
                } else {
                    player.sendMessage(ChatColor.RED + "Your mute is permanent.");
                }
            }
        }
    }
}