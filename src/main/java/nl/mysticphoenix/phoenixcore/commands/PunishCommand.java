package nl.mysticphoenix.phoenixcore.commands;

import nl.mysticphoenix.phoenixcore.PhoenixCore;
import nl.mysticphoenix.phoenixcore.managers.PunishmentManager;
import nl.mysticphoenix.phoenixcore.models.Punishment;
import nl.mysticphoenix.phoenixcore.models.PunishmentType;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public class PunishCommand implements CommandExecutor, TabCompleter {

    private final PhoenixCore plugin;
    private final PunishmentManager punishmentManager;

    public PunishCommand(PhoenixCore plugin) {
        this.plugin = plugin;
        this.punishmentManager = plugin.getPunishmentManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("phoenixcore.punish")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }

        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("history")) {
            return handleHistoryCommand(sender, args);
        } else if (args[0].equalsIgnoreCase("remove")) {
            return handleRemoveCommand(sender, args);
        } else if (args[0].equalsIgnoreCase("info")) {
            return handleInfoCommand(sender, args);
        }

        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /punish <player> <type> <duration> [reason]");
            return true;
        }

        String targetName = args[0];
        OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(targetName);

        if (!targetPlayer.hasPlayedBefore() && !targetPlayer.isOnline()) {
            sender.sendMessage(ChatColor.RED + "Player not found: " + targetName);
            return true;
        }

        PunishmentType type = PunishmentType.fromString(args[1]);
        if (type == null) {
            sender.sendMessage(ChatColor.RED + "Invalid punishment type: " + args[1]);
            sender.sendMessage(ChatColor.RED + "Valid types: " + Arrays.stream(PunishmentType.values())
                              .map(PunishmentType::name)
                              .collect(Collectors.joining(", ")));
            return true;
        }

        if (!sender.hasPermission("phoenixcore.punish." + type.name().toLowerCase())) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to issue this type of punishment.");
            return true;
        }

        long durationMillis = parseDuration(args[2]);

        String reason = "";
        if (args.length > 3) {
            StringBuilder reasonBuilder = new StringBuilder();
            for (int i = 3; i < args.length; i++) {
                reasonBuilder.append(args[i]).append(" ");
            }
            reason = reasonBuilder.toString().trim();
        }

        UUID issuerUUID;
        String issuerName;

        if (sender instanceof Player) {
            Player issuer = (Player) sender;
            issuerUUID = issuer.getUniqueId();
            issuerName = issuer.getName();
        } else {
            issuerUUID = new UUID(0, 0);
            issuerName = "Console";
        }

        Punishment punishment = punishmentManager.punishPlayer(
            targetPlayer.getUniqueId(),
            targetPlayer.getName(),
            issuerUUID,
            issuerName,
            type,
            reason,
            durationMillis
        );

        sender.sendMessage(ChatColor.GREEN + "Successfully " + punishmentManager.getPunishmentVerb(type) + " " +
                          targetPlayer.getName() + (reason.isEmpty() ? "" : " for: " + reason));

        return true;
    }

    private boolean handleHistoryCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /punish history <player>");
            return true;
        }

        String targetName = args[1];
        OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(targetName);

        if (!targetPlayer.hasPlayedBefore() && !targetPlayer.isOnline()) {
            sender.sendMessage(ChatColor.RED + "Player not found: " + targetName);
            return true;
        }

        List<Punishment> history = plugin.getStorageManager().getPlayerPunishmentHistory(targetPlayer.getUniqueId());

        if (history.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + targetPlayer.getName() + " has no punishment history.");
            return true;
        }

        sender.sendMessage(ChatColor.GOLD + "=== Punishment History for " + targetPlayer.getName() + " ===");

        for (Punishment punishment : history) {
            String status = punishment.isActive() ?
                (punishment.isExpired() ? ChatColor.GRAY + "[EXPIRED]" : ChatColor.GREEN + "[ACTIVE]") :
                ChatColor.RED + "[REMOVED]";

            sender.sendMessage(
                ChatColor.YELLOW + "#" + punishment.getId() + " " + status + " " +
                ChatColor.YELLOW + punishment.getType().getDisplayName() +
                ChatColor.WHITE + " by " + punishment.getIssuerName() +
                ChatColor.WHITE + " on " + new Date(punishment.getIssueDate().getTime()).toString() +
                (punishment.isPermanent() ? " (Permanent)" : " (" + PunishmentManager.formatDuration(punishment.getDuration()) + ")")
            );

            if (!punishment.getReason().isEmpty()) {
                sender.sendMessage(ChatColor.GRAY + "  Reason: " + punishment.getReason());
            }
        }

        return true;
    }

    private boolean handleRemoveCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /punish remove <id>");
            return true;
        }

        if (!sender.hasPermission("phoenixcore.punish.remove")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to remove punishments.");
            return true;
        }

        int id;
        try {
            id = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Invalid punishment ID: " + args[1]);
            return true;
        }

        UUID removerUUID;
        String removerName;

        if (sender instanceof Player) {
            Player remover = (Player) sender;
            removerUUID = remover.getUniqueId();
            removerName = remover.getName();
        } else {
            removerUUID = new UUID(0, 0);
            removerName = "Console";
        }

        punishmentManager.removePunishment(id, removerUUID, removerName);
        sender.sendMessage(ChatColor.GREEN + "Punishment #" + id + " has been removed.");

        return true;
    }

    private boolean handleInfoCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /punish info <id>");
            return true;
        }

        int id;
        try {
            id = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Invalid punishment ID: " + args[1]);
            return true;
        }


        List<Punishment> allPunishments = plugin.getStorageManager().getAllPunishments();

        for (Punishment punishment : allPunishments) {
            if (punishment.getId() == id) {
                sender.sendMessage(ChatColor.GOLD + "=== Punishment #" + id + " ===");
                sender.sendMessage(ChatColor.YELLOW + "Type: " + punishment.getType().getDisplayName());
                sender.sendMessage(ChatColor.YELLOW + "Target: " + punishment.getTargetName() + " (" + punishment.getTargetUUID() + ")");
                sender.sendMessage(ChatColor.YELLOW + "Issuer: " + punishment.getIssuerName() + " (" + punishment.getIssuerUUID() + ")");
                sender.sendMessage(ChatColor.YELLOW + "Reason: " + (punishment.getReason().isEmpty() ? "None" : punishment.getReason()));
                sender.sendMessage(ChatColor.YELLOW + "Issue Date: " + new Date(punishment.getIssueDate().getTime()).toString());

                if (punishment.isPermanent()) {
                    sender.sendMessage(ChatColor.YELLOW + "Duration: Permanent");
                } else {
                    sender.sendMessage(ChatColor.YELLOW + "Duration: " + PunishmentManager.formatDuration(punishment.getDuration()));
                    sender.sendMessage(ChatColor.YELLOW + "Expiry Date: " + new Date(punishment.getExpiryDate().getTime()).toString());
                }

                sender.sendMessage(ChatColor.YELLOW + "Status: " +
                                  (punishment.isActive() ?
                                   (punishment.isExpired() ? "Expired" : "Active") :
                                   "Removed"));

                return true;
            }
        }

        sender.sendMessage(ChatColor.RED + "Punishment not found with ID: " + id);
        return true;
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== Punish Command Help ===");
        sender.sendMessage(ChatColor.YELLOW + "/punish <player> <type> <duration> [reason]" + ChatColor.WHITE + " - Punish a player");
        sender.sendMessage(ChatColor.YELLOW + "/punish history <player>" + ChatColor.WHITE + " - View a player's punishment history");
        sender.sendMessage(ChatColor.YELLOW + "/punish info <id>" + ChatColor.WHITE + " - View detailed information about a punishment");

        sender.sendMessage(ChatColor.GOLD + "=== Punishment Types ===");
        for (PunishmentType type : PunishmentType.values()) {
            if (sender.hasPermission("phoenixcore.punish." + type.name().toLowerCase())) {
                sender.sendMessage(ChatColor.YELLOW + type.name() + ChatColor.WHITE + " - " + type.getDisplayName());
            }
        }

        sender.sendMessage(ChatColor.GOLD + "=== Duration Format ===");
        sender.sendMessage(ChatColor.WHITE + "Use 'permanent' or one of these formats:");
        sender.sendMessage(ChatColor.WHITE + "30s = 30 seconds");
        sender.sendMessage(ChatColor.WHITE + "5m = 5 minutes");
        sender.sendMessage(ChatColor.WHITE + "2h = 2 hours");
        sender.sendMessage(ChatColor.WHITE + "1d = 1 day");
        sender.sendMessage(ChatColor.WHITE + "1w = 1 week");
        sender.sendMessage(ChatColor.WHITE + "1mo = 1 month (30 days)");
        sender.sendMessage(ChatColor.WHITE + "1y = 1 year (365 days)");
    }

    private long parseDuration(String durationStr) {
        if (durationStr.equalsIgnoreCase("permanent") || durationStr.equalsIgnoreCase("perm")) {
            return -1;
        }

        long duration = 0;

        try {
            if (durationStr.endsWith("s")) {
                duration = Long.parseLong(durationStr.substring(0, durationStr.length() - 1)) * 1000;
            } else if (durationStr.endsWith("m")) {
                duration = Long.parseLong(durationStr.substring(0, durationStr.length() - 1)) * 60 * 1000;
            } else if (durationStr.endsWith("h")) {
                duration = Long.parseLong(durationStr.substring(0, durationStr.length() - 1)) * 60 * 60 * 1000;
            } else if (durationStr.endsWith("d")) {
                duration = Long.parseLong(durationStr.substring(0, durationStr.length() - 1)) * 24 * 60 * 60 * 1000;
            } else if (durationStr.endsWith("w")) {
                duration = Long.parseLong(durationStr.substring(0, durationStr.length() - 1)) * 7 * 24 * 60 * 60 * 1000;
            } else if (durationStr.endsWith("mo")) {
                duration = Long.parseLong(durationStr.substring(0, durationStr.length() - 2)) * 30 * 24 * 60 * 60 * 1000;
            } else if (durationStr.endsWith("y")) {
                duration = Long.parseLong(durationStr.substring(0, durationStr.length() - 1)) * 365 * 24 * 60 * 60 * 1000;
            } else {
                duration = Long.parseLong(durationStr) * 1000;
            }
        } catch (NumberFormatException e) {
            return -1;
        }

        return duration;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subcommands = Arrays.asList("history", "remove", "info");

            for (String subcommand : subcommands) {
                if (subcommand.startsWith(args[0].toLowerCase())) {
                    completions.add(subcommand);
                }
            }

            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase().startsWith(args[0].toLowerCase())) {
                    completions.add(player.getName());
                }
            }

            return completions;
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("history")) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                        completions.add(player.getName());
                    }
                }
            } else if (!args[0].equalsIgnoreCase("remove") && !args[0].equalsIgnoreCase("info")) {
                for (PunishmentType type : PunishmentType.values()) {
                    if (type.name().toLowerCase().startsWith(args[1].toLowerCase()) &&
                        sender.hasPermission("phoenixcore.punish." + type.name().toLowerCase())) {
                        completions.add(type.name());
                    }
                }
            }

            return completions;
        } else if (args.length == 3 && !args[0].equalsIgnoreCase("history") &&
                  !args[0].equalsIgnoreCase("remove") && !args[0].equalsIgnoreCase("info")) {
            List<String> durations = Arrays.asList("permanent", "30s", "1m", "5m", "15m", "30m", "1h", "3h", "6h", "12h", "1d", "3d", "1w", "2w", "1mo", "3mo", "6mo", "1y");

            for (String duration : durations) {
                if (duration.startsWith(args[2].toLowerCase())) {
                    completions.add(duration);
                }
            }

            return completions;
        }

        return completions;
    }
}