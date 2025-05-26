package nl.mysticphoenix.phoenixcore.models;

public enum PunishmentType {
    WARNING("Warning"),
    MUTE("Mute"),
    KICK("Kick"),
    BAN("Ban"),
    IP_BAN("IP Ban");

    private final String displayName;

    PunishmentType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static PunishmentType fromString(String text) {
        for (PunishmentType type : PunishmentType.values()) {
            if (type.name().equalsIgnoreCase(text) || type.getDisplayName().equalsIgnoreCase(text)) {
                return type;
            }
        }
        return null;
    }
}