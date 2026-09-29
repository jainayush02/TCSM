package com.amdocs.telecom.model;

import java.time.LocalDateTime;

public class TelecomPlan {
    private int planId;
    private String planCode;
    private String planName;
    private SubscriptionType planType;
    private double monthlyRental;
    private int dataAllowanceGB;
    private int voiceMinutes; // -1 represents Unlimited
    private int smsAllowance;
    private int validityDays;
    private boolean internationalRoaming;
    private String status; // ACTIVE, INACTIVE
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public TelecomPlan() {}

    public TelecomPlan(int planId, String planCode, String planName, SubscriptionType planType,
                       double monthlyRental, int dataAllowanceGB, int voiceMinutes,
                       int smsAllowance, int validityDays, boolean internationalRoaming,
                       String status, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.planId = planId;
        this.planCode = planCode;
        this.planName = planName;
        this.planType = planType;
        this.monthlyRental = monthlyRental;
        this.dataAllowanceGB = dataAllowanceGB;
        this.voiceMinutes = voiceMinutes;
        this.smsAllowance = smsAllowance;
        this.validityDays = validityDays;
        this.internationalRoaming = internationalRoaming;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public int getPlanId() { return planId; }
    public void setPlanId(int planId) { this.planId = planId; }

    public String getPlanCode() { return planCode; }
    public void setPlanCode(String planCode) { this.planCode = planCode; }

    public String getPlanName() { return planName; }
    public void setPlanName(String planName) { this.planName = planName; }

    public SubscriptionType getPlanType() { return planType; }
    public void setPlanType(SubscriptionType planType) { this.planType = planType; }

    public double getMonthlyRental() { return monthlyRental; }
    public void setMonthlyRental(double monthlyRental) { this.monthlyRental = monthlyRental; }

    public int getDataAllowanceGB() { return dataAllowanceGB; }
    public void setDataAllowanceGB(int dataAllowanceGB) { this.dataAllowanceGB = dataAllowanceGB; }

    public int getVoiceMinutes() { return voiceMinutes; }
    public void setVoiceMinutes(int voiceMinutes) { this.voiceMinutes = voiceMinutes; }

    public int getSmsAllowance() { return smsAllowance; }
    public void setSmsAllowance(int smsAllowance) { this.smsAllowance = smsAllowance; }

    public int getValidityDays() { return validityDays; }
    public void setValidityDays(int validityDays) { this.validityDays = validityDays; }

    public boolean isInternationalRoaming() { return internationalRoaming; }
    public void setInternationalRoaming(boolean internationalRoaming) { this.internationalRoaming = internationalRoaming; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public String getVoiceDisplay() {
        return voiceMinutes == -1 ? "Unlimited" : voiceMinutes + " min";
    }

    @Override
    public String toString() {
        return String.format("%-10s | %-16s | %-8s | %3d GB | %-10s | ₹%7.2f | Roaming: %s | %s",
                planCode, planName, planType, dataAllowanceGB, getVoiceDisplay(), monthlyRental,
                (internationalRoaming ? "Yes" : "No"), status);
    }
}
