package nl.mysticphoenix.phoenixcore.models;

import java.util.Date;
import java.util.UUID;

public class Punishment {

    private int id;
    private UUID targetUUID;
    private String targetName;
    private UUID issuerUUID;
    private String issuerName;
    private PunishmentType type;
    private String reason;
    private Date issueDate;
    private Date expiryDate;
    private boolean active;

    public Punishment(UUID targetUUID, String targetName, UUID issuerUUID, String issuerName,
                     PunishmentType type, String reason, Date expiryDate) {
        this.targetUUID = targetUUID;
        this.targetName = targetName;
        this.issuerUUID = issuerUUID;
        this.issuerName = issuerName;
        this.type = type;
        this.reason = reason;
        this.issueDate = new Date();
        this.expiryDate = expiryDate;
        this.active = true;
    }

    public Punishment(int id, UUID targetUUID, String targetName, UUID issuerUUID, String issuerName,
                     PunishmentType type, String reason, Date issueDate, Date expiryDate, boolean active) {
        this.id = id;
        this.targetUUID = targetUUID;
        this.targetName = targetName;
        this.issuerUUID = issuerUUID;
        this.issuerName = issuerName;
        this.type = type;
        this.reason = reason;
        this.issueDate = issueDate;
        this.expiryDate = expiryDate;
        this.active = active;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public UUID getTargetUUID() {
        return targetUUID;
    }

    public String getTargetName() {
        return targetName;
    }

    public UUID getIssuerUUID() {
        return issuerUUID;
    }

    public String getIssuerName() {
        return issuerName;
    }

    public PunishmentType getType() {
        return type;
    }

    public String getReason() {
        return reason;
    }

    public Date getIssueDate() {
        return issueDate;
    }

    public Date getExpiryDate() {
        return expiryDate;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isPermanent() {
        return expiryDate == null;
    }

    public boolean isExpired() {
        if (isPermanent()) {
            return false;
        }
        return new Date().after(expiryDate);
    }

    public long getDuration() {
        if (isPermanent()) {
            return -1;
        }
        return expiryDate.getTime() - issueDate.getTime();
    }

    public long getTimeRemaining() {
        if (isPermanent()) {
            return -1;
        }
        return Math.max(0, expiryDate.getTime() - new Date().getTime());
    }
}