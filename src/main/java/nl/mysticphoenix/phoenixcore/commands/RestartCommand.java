package nl.mysticphoenix.phoenixcore.commands;

import nl.mysticphoenix.phoenixcore.PhoenixCore;
import nl.mysticphoenix.phoenixcore.managers.RestartManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class RestartCommand implements CommandExecutor {

    private final PhoenixCore plugin;
    private final RestartManager restartManager;

    public RestartCommand(PhoenixCore plugin) {
        this.plugin = plugin;
        this.restartManager = plugin.getRestartManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("phoenixcore.restart")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(ChatColor.RED + "Usage: /restart <seconds|cancel>");
            return true;
        }

        if (args[0].equalsIgnoreCase("cancel")) {
            if (!restartManager.isRestartInProgress()) {
                sender.sendMessage(ChatColor.RED + "There is no restart in progress.");
                return true;
            }

            restartManager.cancelRestart();
            sender.sendMessage(ChatColor.GREEN + "Restart countdown has been cancelled.");
            return true;
        }

        try {
            int seconds = Integer.parseInt(args[0]);
            if (seconds < 5) {
                sender.sendMessage(ChatColor.RED + "Restart time must be at least 5 seconds.");
                return true;
            }

            restartManager.startRestart(seconds);
            sender.sendMessage(ChatColor.GREEN + "Restart countdown started for " + seconds + " seconds.");

        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Invalid time format. Please use a number in seconds.");
        }

        return true;
    }
}