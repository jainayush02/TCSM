package com.amdocs.telecom.model;

import java.time.LocalDateTime;

public class SIMCard {
    private int simId;
    private String simNumber;
    private SimType simType;
    private String imsi;
    private String status; // ACTIVE, SUSPENDED, INACTIVE
    private LocalDateTime createdAt;

    public SIMCard() {}

    public SIMCard(int simId, String simNumber, SimType simType, String imsi, String status, LocalDateTime createdAt) {
        this.simId = simId;
        this.simNumber = simNumber;
        this.simType = simType;
        this.imsi = imsi;
        this.status = status;
        this.createdAt = createdAt;
    }

    public int getSimId() { return simId; }
    public void setSimId(int simId) { this.simId = simId; }

    public String getSimNumber() { return simNumber; }
    public void setSimNumber(String simNumber) { this.simNumber = simNumber; }

    public SimType getSimType() { return simType; }
    public void setSimType(SimType simType) { this.simType = simType; }

    public String getImsi() { return imsi; }
    public void setImsi(String imsi) { this.imsi = imsi; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    @Override
    public String toString() {
        return String.format("SIM: %s [%s] - Status: %s", simNumber, simType, status);
    }
}
