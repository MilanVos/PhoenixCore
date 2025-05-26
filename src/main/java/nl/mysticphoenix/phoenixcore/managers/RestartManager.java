package nl.mysticphoenix.phoenixcore.managers;

import nl.mysticphoenix.phoenixcore.PhoenixCore;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.scheduler.BukkitTask;

public class RestartManager {

    private final PhoenixCore plugin;
    private BukkitTask restartTask;
    private boolean restartInProgress = false;
    private int timeLeft;

    public RestartManager(PhoenixCore plugin) {
        this.plugin = plugin;
    }

    public boolean isRestartInProgress() {
        return restartInProgress;
    }

    public int getTimeLeft() {
        return timeLeft;
    }

    public void startRestart(int seconds) {
        if (restartInProgress) {
            return;
        }

        restartInProgress = true;
        timeLeft = seconds;

        Bukkit.broadcastMessage(ChatColor.RED + "Server will restart in " + formatTime(seconds) + "!");

        restartTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            timeLeft--;

            if (timeLeft <= 0) {
                Bukkit.broadcastMessage(ChatColor.RED + "Server is restarting now!");
                cancelRestart();

                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "restart");
                }, 20L);

            } else if (timeLeft <= 5 || timeLeft == 10 || timeLeft == 30 ||
                      timeLeft == 60 || timeLeft == 300 || timeLeft == 600) {
                Bukkit.broadcastMessage(ChatColor.RED + "Server will restart in " + formatTime(timeLeft) + "!");
            }

        }, 20L, 20L);
    }

    public void cancelRestart() {
        if (restartTask != null) {
            restartTask.cancel();
            restartTask = null;
        }
        restartInProgress = false;
    }

    private String formatTime(int seconds) {
        if (seconds < 60) {
            return seconds + " second" + (seconds == 1 ? "" : "s");
        } else if (seconds < 3600) {
            int minutes = seconds / 60;
            return minutes + " minute" + (minutes == 1 ? "" : "s");
        } else {
            int hours = seconds / 3600;
            int minutes = (seconds % 3600) / 60;
            return hours + " hour" + (hours == 1 ? "" : "s") +
                  (minutes > 0 ? " and " + minutes + " minute" + (minutes == 1 ? "" : "s") : "");
        }
    }
}