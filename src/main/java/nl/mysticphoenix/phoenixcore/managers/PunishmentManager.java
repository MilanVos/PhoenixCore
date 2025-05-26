package nl.mysticphoenix.phoenixcore.managers;

import nl.mysticphoenix.phoenixcore.PhoenixCore;
import nl.mysticphoenix.phoenixcore.models.Punishment;
import nl.mysticphoenix.phoenixcore.models.PunishmentType;
import nl.mysticphoenix.phoenixcore.storage.StorageManager;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.*;

public class PunishmentManager {

    private final PhoenixCore plugin;
    private final StorageManager storageManager;
    private final Map<UUID, List<Punishment>> activePunishments;

    public PunishmentManager(PhoenixCore plugin) {
        this.plugin = plugin;
        this.storageManager = plugin.getStorageManager();
        this.activePunishments = new HashMap<>();

        loadActivePunishments();

        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::checkExpiredPunishments, 20L * 60L, 20L * 60L);
    }

    private void loadActivePunishments() {
        for (Player player : Bukkit.getOnlinePlayers()) {
        }
    }

    public void loadPlayerPunishments(UUID playerUUID) {
        List<Punishment> punishments = storageManager.getActivePunishments(playerUUID);
        activePunishments.put(playerUUID, punishments);

        for (Punishment punishment : punishments) {
            if (punishment.getType() == PunishmentType.MUTE && !punishment.isExpired()) {
                plugin.getLogger().info("Loaded active mute for player " + playerUUID);
            }
        }
    }

    public Punishment punishPlayer(UUID targetUUID, String targetName, UUID issuerUUID, String issuerName,
                                  PunishmentType type, String reason, long durationMillis) {
        Date expiryDate = null;
        if (durationMillis > 0) {
            expiryDate = new Date(System.currentTimeMillis() + durationMillis);
        }

        Punishment punishment = new Punishment(targetUUID, targetName, issuerUUID, issuerName,
                                              type, reason, expiryDate);

        storageManager.savePunishment(punishment);

        applyPunishment(punishment);

        if (Bukkit.getPlayer(targetUUID) != null) {
            if (!activePunishments.containsKey(targetUUID)) {
                activePunishments.put(targetUUID, new ArrayList<>());
            }
            activePunishments.get(targetUUID).add(punishment);
        }

        broadcastPunishment(punishment);

        return punishment;
    }

    private void applyPunishment(Punishment punishment) {
        switch (punishment.getType()) {
            case WARNING:
                Player targetPlayer = Bukkit.getPlayer(punishment.getTargetUUID());
                if (targetPlayer != null) {
                    targetPlayer.sendMessage(ChatColor.RED + "=== WARNING ===");
                    targetPlayer.sendMessage(ChatColor.RED + "You have been warned by " + punishment.getIssuerName());
                    targetPlayer.sendMessage(ChatColor.RED + "Reason: " + punishment.getReason());
                    targetPlayer.sendMessage(ChatColor.RED + "===============");
                }
                break;

            case MUTE:
                // Mute is handled in the chat event
                break;

            case KICK:
                targetPlayer = Bukkit.getPlayer(punishment.getTargetUUID());
                if (targetPlayer != null) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        targetPlayer.kickPlayer(formatKickMessage(punishment));
                    });
                }
                break;

            case BAN:
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (punishment.isPermanent()) {
                        Bukkit.getBanList(BanList.Type.NAME).addBan(
                            punishment.getTargetName(),
                            punishment.getReason(),
                            null,
                            punishment.getIssuerName()
                        );
                    } else {
                        Bukkit.getBanList(BanList.Type.NAME).addBan(
                            punishment.getTargetName(),
                            punishment.getReason(),
                            punishment.getExpiryDate(),
                            punishment.getIssuerName()
                        );
                    }

                    Player playerToKick = Bukkit.getPlayer(punishment.getTargetUUID());
                    if (playerToKick != null) {
                        playerToKick.kickPlayer(formatBanMessage(punishment));
                    }
                });                break;

            case IP_BAN:
                targetPlayer = Bukkit.getPlayer(punishment.getTargetUUID());
                if (targetPlayer != null) {
                    final String ip = targetPlayer.getAddress().getAddress().getHostAddress();

                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (punishment.isPermanent()) {
                            Bukkit.getBanList(BanList.Type.IP).addBan(
                                ip,
                                punishment.getReason(),
                                null,
                                punishment.getIssuerName()
                            );
                        } else {
                            Bukkit.getBanList(BanList.Type.IP).addBan(
                                ip,
                                punishment.getReason(),
                                punishment.getExpiryDate(),
                                punishment.getIssuerName()
                            );
                        }

                        targetPlayer.kickPlayer(formatBanMessage(punishment));
                    });
                }
                break;
        }
    }

    public boolean isMuted(UUID playerUUID) {
        if (!activePunishments.containsKey(playerUUID)) {
            return false;
        }

        for (Punishment punishment : activePunishments.get(playerUUID)) {
            if (punishment.getType() == PunishmentType.MUTE && punishment.isActive() && !punishment.isExpired()) {
                return true;
            }
        }

        return false;
    }

    public Punishment getActiveMute(UUID playerUUID) {
        if (!activePunishments.containsKey(playerUUID)) {
            return null;
        }

        for (Punishment punishment : activePunishments.get(playerUUID)) {
            if (punishment.getType() == PunishmentType.MUTE && punishment.isActive() && !punishment.isExpired()) {
                return punishment;
            }
        }

        return null;
    }

    public void removePunishment(int punishmentId, UUID removerUUID, String removerName) {
        for (List<Punishment> playerPunishments : activePunishments.values()) {
            for (Punishment punishment : playerPunishments) {
                if (punishment.getId() == punishmentId) {
                    punishment.setActive(false);
                    storageManager.deactivatePunishment(punishmentId);

                    if (punishment.getType() == PunishmentType.BAN) {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            Bukkit.getBanList(BanList.Type.NAME).pardon(punishment.getTargetName());
                        });
                    } else if (punishment.getType() == PunishmentType.IP_BAN) {
                    }

                    Bukkit.broadcastMessage(ChatColor.GREEN + punishment.getTargetName() + "'s " +
                                           punishment.getType().getDisplayName().toLowerCase() +
                                           " has been removed by " + removerName);
                    return;
                }
            }
        }
    }

    private void checkExpiredPunishments() {
        Date now = new Date();

        for (UUID playerUUID : activePunishments.keySet()) {
            List<Punishment> playerPunishments = activePunishments.get(playerUUID);
            Iterator<Punishment> iterator = playerPunishments.iterator();

            while (iterator.hasNext()) {
                Punishment punishment = iterator.next();

                if (punishment.isActive() && !punishment.isPermanent() && punishment.getExpiryDate().before(now)) {
                    punishment.setActive(false);
                    storageManager.deactivatePunishment(punishment.getId());
                    iterator.remove();

                    if (punishment.getType() == PunishmentType.BAN) {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            Bukkit.getBanList(BanList.Type.NAME).pardon(punishment.getTargetName());
                        });
                    }

                    Player player = Bukkit.getPlayer(playerUUID);
                    if (player != null) {
                        player.sendMessage(ChatColor.GREEN + "Your " + punishment.getType().getDisplayName().toLowerCase() +
                                          " has expired.");
                    }
                }
            }
        }
    }

    private void broadcastPunishment(Punishment punishment) {
        String message = ChatColor.RED + punishment.getTargetName() + " has been " +
                        getPunishmentVerb(punishment.getType()) + " by " + punishment.getIssuerName();

        if (punishment.getReason() != null && !punishment.getReason().isEmpty()) {
            message += " for: " + punishment.getReason();
        }

        if (!punishment.isPermanent()) {
            message += " (" + formatDuration(punishment.getDuration()) + ")";
        }

        Bukkit.broadcastMessage(message);
    }

    public String getPunishmentVerb(PunishmentType type) {
        switch (type) {
            case WARNING: return "warned";
            case MUTE: return "muted";
            case KICK: return "kicked";
            case BAN: return "banned";
            case IP_BAN: return "IP banned";
            default: return "punished";
        }
    }

    private String formatKickMessage(Punishment punishment) {
        StringBuilder message = new StringBuilder();
        message.append(ChatColor.RED).append("You have been kicked by ").append(punishment.getIssuerName()).append("\n");

        if (punishment.getReason() != null && !punishment.getReason().isEmpty()) {
            message.append(ChatColor.RED).append("Reason: ").append(punishment.getReason()).append("\n");
        }

        return message.toString();
    }

    private String formatBanMessage(Punishment punishment) {
        StringBuilder message = new StringBuilder();
        message.append(ChatColor.RED).append("You have been banned by ").append(punishment.getIssuerName()).append("\n");

        if (punishment.getReason() != null && !punishment.getReason().isEmpty()) {
            message.append(ChatColor.RED).append("Reason: ").append(punishment.getReason()).append("\n");
        }

        if (!punishment.isPermanent()) {
            message.append(ChatColor.RED).append("Duration: ").append(formatDuration(punishment.getDuration())).append("\n");
            message.append(ChatColor.RED).append("Expires: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(punishment.getExpiryDate())).append("\n");
        } else {
            message.append(ChatColor.RED).append("Duration: Permanent").append("\n");
        }

        return message.toString();
    }

    public static String formatDuration(long millis) {
        if (millis < 0) {
            return "Permanent";
        }

        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) {
            return days + " day" + (days == 1 ? "" : "s");
        } else if (hours > 0) {
            return hours + " hour" + (hours == 1 ? "" : "s");
        } else if (minutes > 0) {
            return minutes + " minute" + (minutes == 1 ? "" : "s");
        } else {
            return seconds + " second" + (seconds == 1 ? "" : "s");
        }
    }
}