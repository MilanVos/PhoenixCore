package nl.mysticphoenix.phoenixcore;

import org.bukkit.plugin.java.JavaPlugin;

public final class PhoenixCore extends JavaPlugin {

    private static PhoenixCore instance;

    @Override
    public void onEnable() {
        instance = this;

        getLogger().info("PhoenixCore has been enabled!");
    }

    @Override
    public void onDisable() {

        getLogger().info("PhoenixCore has been disabled!");
    }

    public static PhoenixCore getInstance() {
        return instance;
    }

}
