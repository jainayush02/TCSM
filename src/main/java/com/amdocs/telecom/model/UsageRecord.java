package com.amdocs.telecom.model;

import java.time.LocalDateTime;

public class UsageRecord {
    private int usageId;
    private int subscriptionId;
    private LocalDateTime usageDate;
    private UsageType usageType;
    private double quantity;
    private String unit; // Minutes, SMS Count, GB, MB
    private double charge;

    private String mobileNumber;

    public UsageRecord() {}

    public UsageRecord(int usageId, int subscriptionId, LocalDateTime usageDate,
                       UsageType usageType, double quantity, String unit, double charge) {
        this.usageId = usageId;
        this.subscriptionId = subscriptionId;
        this.usageDate = usageDate;
        this.usageType = usageType;
        this.quantity = quantity;
        this.unit = unit;
        this.charge = charge;
    }

    public int getUsageId() { return usageId; }
    public void setUsageId(int usageId) { this.usageId = usageId; }

    public int getSubscriptionId() { return subscriptionId; }
    public void setSubscriptionId(int subscriptionId) { this.subscriptionId = subscriptionId; }

    public LocalDateTime getUsageDate() { return usageDate; }
    public void setUsageDate(LocalDateTime usageDate) { this.usageDate = usageDate; }

    public UsageType getUsageType() { return usageType; }
    public void setUsageType(UsageType usageType) { this.usageType = usageType; }

    public double getQuantity() { return quantity; }
    public void setQuantity(double quantity) { this.quantity = quantity; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public double getCharge() { return charge; }
    public void setCharge(double charge) { this.charge = charge; }

    public String getMobileNumber() { return mobileNumber; }
    public void setMobileNumber(String mobileNumber) { this.mobileNumber = mobileNumber; }

    @Override
    public String toString() {
        return String.format("[%s] Type: %-7s | Quantity: %6.1f %-7s | Charge: ₹%6.2f",
                usageDate, usageType, quantity, unit, charge);
    }
}
