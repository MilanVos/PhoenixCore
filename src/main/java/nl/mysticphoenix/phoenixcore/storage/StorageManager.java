package nl.mysticphoenix.phoenixcore.storage;

import nl.mysticphoenix.phoenixcore.PhoenixCore;
import nl.mysticphoenix.phoenixcore.models.Punishment;
import nl.mysticphoenix.phoenixcore.models.PunishmentType;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

public class StorageManager {

    private final PhoenixCore plugin;
    private final File punishmentsFile;
    private YamlConfiguration punishmentsConfig;
    private int nextId = 1;

    public StorageManager(PhoenixCore plugin) {
        this.plugin = plugin;
        this.punishmentsFile = new File(plugin.getDataFolder(), "punishments.yml");

        if (!punishmentsFile.exists()) {
            plugin.saveResource("punishments.yml", false);
        }

        punishmentsConfig = YamlConfiguration.loadConfiguration(punishmentsFile);

        if (punishmentsConfig.contains("nextId")) {
            nextId = punishmentsConfig.getInt("nextId");
        }
    }

    public void savePunishment(Punishment punishment) {
        if (punishment.getId() == 0) {
            punishment.setId(nextId++);
        }

        String path = "punishments." + punishment.getId();
        punishmentsConfig.set(path + ".targetUUID", punishment.getTargetUUID().toString());
        punishmentsConfig.set(path + ".targetName", punishment.getTargetName());
        punishmentsConfig.set(path + ".issuerUUID", punishment.getIssuerUUID().toString());
        punishmentsConfig.set(path + ".issuerName", punishment.getIssuerName());
        punishmentsConfig.set(path + ".type", punishment.getType().name());
        punishmentsConfig.set(path + ".reason", punishment.getReason());
        punishmentsConfig.set(path + ".issueDate", punishment.getIssueDate().getTime());

        if (punishment.getExpiryDate() != null) {
            punishmentsConfig.set(path + ".expiryDate", punishment.getExpiryDate().getTime());
        } else {
            punishmentsConfig.set(path + ".expiryDate", null);
        }

        punishmentsConfig.set(path + ".active", punishment.isActive());
        punishmentsConfig.set("nextId", nextId);

        try {
            punishmentsConfig.save(punishmentsFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save punishment: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public List<Punishment> getAllPunishments() {
        List<Punishment> punishments = new ArrayList<>();

        if (!punishmentsConfig.contains("punishments")) {
            return punishments;
        }

        for (String idStr : punishmentsConfig.getConfigurationSection("punishments").getKeys(false)) {
            int id = Integer.parseInt(idStr);
            String path = "punishments." + id;

            UUID targetUUID = UUID.fromString(punishmentsConfig.getString(path + ".targetUUID"));
            String targetName = punishmentsConfig.getString(path + ".targetName");
            UUID issuerUUID = UUID.fromString(punishmentsConfig.getString(path + ".issuerUUID"));
            String issuerName = punishmentsConfig.getString(path + ".issuerName");
            PunishmentType type = PunishmentType.valueOf(punishmentsConfig.getString(path + ".type"));
            String reason = punishmentsConfig.getString(path + ".reason");
            Date issueDate = new Date(punishmentsConfig.getLong(path + ".issueDate"));

            Date expiryDate = null;
            if (punishmentsConfig.contains(path + ".expiryDate") &&
                punishmentsConfig.get(path + ".expiryDate") != null) {
                expiryDate = new Date(punishmentsConfig.getLong(path + ".expiryDate"));
            }

            boolean active = punishmentsConfig.getBoolean(path + ".active");

            Punishment punishment = new Punishment(id, targetUUID, targetName, issuerUUID, issuerName,
                                                 type, reason, issueDate, expiryDate, active);
            punishments.add(punishment);
        }

        return punishments;
    }

    public List<Punishment> getActivePunishments(UUID playerUUID) {
        List<Punishment> punishments = new ArrayList<>();

        for (Punishment punishment : getAllPunishments()) {
            if (punishment.getTargetUUID().equals(playerUUID) &&
                punishment.isActive() &&
                !punishment.isExpired()) {
                punishments.add(punishment);
            }
        }

        return punishments;
    }

    public List<Punishment> getPlayerPunishmentHistory(UUID playerUUID) {
        List<Punishment> punishments = new ArrayList<>();

        for (Punishment punishment : getAllPunishments()) {
            if (punishment.getTargetUUID().equals(playerUUID)) {
                punishments.add(punishment);
            }
        }

        punishments.sort((p1, p2) -> p2.getIssueDate().compareTo(p1.getIssueDate()));

        return punishments;
    }

    public void deactivatePunishment(int id) {
        String path = "punishments." + id;

        if (punishmentsConfig.contains(path)) {
            punishmentsConfig.set(path + ".active", false);

            try {
                punishmentsConfig.save(punishmentsFile);
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to deactivate punishment: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    public void close() {
        try {
            punishmentsConfig.save(punishmentsFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save punishments on shutdown: " + e.getMessage());
            e.printStackTrace();
        }
    }
}