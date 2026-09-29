package com.amdocs.telecom.model;

import java.time.LocalDateTime;

public class AuditLog {
    private int auditId;
    private String entityName;
    private String entityId;
    private String action; // INSERT, UPDATE, DELETE, PAYMENT_TRANSACTION
    private String details;
    private String performedBy;
    private LocalDateTime performedAt;

    public AuditLog() {}

    public AuditLog(int auditId, String entityName, String entityId, String action,
                    String details, String performedBy, LocalDateTime performedAt) {
        this.auditId = auditId;
        this.entityName = entityName;
        this.entityId = entityId;
        this.action = action;
        this.details = details;
        this.performedBy = performedBy;
        this.performedAt = performedAt;
    }

    public int getAuditId() { return auditId; }
    public void setAuditId(int auditId) { this.auditId = auditId; }

    public String getEntityName() { return entityName; }
    public void setEntityName(String entityName) { this.entityName = entityName; }

    public String getEntityId() { return entityId; }
    public void setEntityId(String entityId) { this.entityId = entityId; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }

    public String getPerformedBy() { return performedBy; }
    public void setPerformedBy(String performedBy) { this.performedBy = performedBy; }

    public LocalDateTime getPerformedAt() { return performedAt; }
    public void setPerformedAt(LocalDateTime performedAt) { this.performedAt = performedAt; }

    @Override
    public String toString() {
        return String.format("[%s] Audit: %s (%s) %s by %s - %s",
                performedAt, entityName, entityId, action, performedBy, details);
    }
}
