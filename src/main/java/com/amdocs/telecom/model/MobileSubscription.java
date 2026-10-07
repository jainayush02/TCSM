package com.amdocs.telecom.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class MobileSubscription {
    private int subscriptionId;
    private String subscriptionNumber;
    private int customerId;
    private int planId;
    private String mobileNumber;
    private int simId;
    private LocalDate activationDate;
    private SubscriptionType subscriptionType; // PREPAID, POSTPAID
    private String status; // ACTIVE, SUSPENDED, TERMINATED
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private String planCode;
    private String planName;
    private double monthlyRental;
    private String simNumber;
    private SimType simType;

    public MobileSubscription() {}

    public MobileSubscription(int subscriptionId, String subscriptionNumber, int customerId,
                              int planId, String mobileNumber, int simId, LocalDate activationDate,
                              SubscriptionType subscriptionType, String status) {
        this.subscriptionId = subscriptionId;
        this.subscriptionNumber = subscriptionNumber;
        this.customerId = customerId;
        this.planId = planId;
        this.mobileNumber = mobileNumber;
        this.simId = simId;
        this.activationDate = activationDate;
        this.subscriptionType = subscriptionType;
        this.status = status;
    }

    public int getSubscriptionId() { return subscriptionId; }
    public void setSubscriptionId(int subscriptionId) { this.subscriptionId = subscriptionId; }

    public String getSubscriptionNumber() { return subscriptionNumber; }
    public void setSubscriptionNumber(String subscriptionNumber) { this.subscriptionNumber = subscriptionNumber; }

    public int getCustomerId() { return customerId; }
    public void setCustomerId(int customerId) { this.customerId = customerId; }

    public int getPlanId() { return planId; }
    public void setPlanId(int planId) { this.planId = planId; }

    public String getMobileNumber() { return mobileNumber; }
    public void setMobileNumber(String mobileNumber) { this.mobileNumber = mobileNumber; }

    public int getSimId() { return simId; }
    public void setSimId(int simId) { this.simId = simId; }

    public LocalDate getActivationDate() { return activationDate; }
    public void setActivationDate(LocalDate activationDate) { this.activationDate = activationDate; }

    public SubscriptionType getSubscriptionType() { return subscriptionType; }
    public void setSubscriptionType(SubscriptionType subscriptionType) { this.subscriptionType = subscriptionType; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public String getPlanCode() { return planCode; }
    public void setPlanCode(String planCode) { this.planCode = planCode; }

    public String getPlanName() { return planName; }
    public void setPlanName(String planName) { this.planName = planName; }

    public double getMonthlyRental() { return monthlyRental; }
    public void setMonthlyRental(double monthlyRental) { this.monthlyRental = monthlyRental; }

    public String getSimNumber() { return simNumber; }
    public void setSimNumber(String simNumber) { this.simNumber = simNumber; }

    public SimType getSimType() { return simType; }
    public void setSimType(SimType simType) { this.simType = simType; }

    @Override
    public String toString() {
        return String.format("  #%d [%s] Mobile: %s | Plan: %s | Type: %s | Status: %s",
                subscriptionId, subscriptionNumber, mobileNumber, (planName != null ? planName : ("ID " + planId)),
                subscriptionType, status);
    }
}
