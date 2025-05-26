package nl.mysticphoenix.phoenixcore;

import nl.mysticphoenix.phoenixcore.commands.PunishCommand;
import nl.mysticphoenix.phoenixcore.listeners.PunishmentListener;
import nl.mysticphoenix.phoenixcore.managers.PunishmentManager;
import nl.mysticphoenix.phoenixcore.storage.StorageManager;
import nl.mysticphoenix.phoenixcore.vpn.VPNGuard;
import org.bukkit.plugin.java.JavaPlugin;

public final class PhoenixCore extends JavaPlugin {

    private static PhoenixCore instance;
    private StorageManager storageManager;
    private PunishmentManager punishmentManager;
    private VPNGuard vpnGuard;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();

        this.storageManager = new StorageManager(this);

        this.punishmentManager = new PunishmentManager(this);

        this.vpnGuard = new VPNGuard(this);

        getCommand("punish").setExecutor(new PunishCommand(this));
        getCommand("punish").setTabCompleter(new PunishCommand(this));

        getServer().getPluginManager().registerEvents(new PunishmentListener(this), this);

        getLogger().info("PhoenixCore has been enabled!");
    }

    @Override
    public void onDisable() {
        if (storageManager != null) {
            storageManager.close();
        }

        getLogger().info("PhoenixCore has been disabled!");
    }

    public static PhoenixCore getInstance() {
        return instance;
    }

    public StorageManager getStorageManager() {
        return storageManager;
    }

    public PunishmentManager getPunishmentManager() {
        return punishmentManager;
    }

    public VPNGuard getVpnGuard() {
        return vpnGuard;
    }
    }