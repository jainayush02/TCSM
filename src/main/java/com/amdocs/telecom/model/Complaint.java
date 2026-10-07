package com.amdocs.telecom.model;

import java.time.LocalDateTime;

public class Complaint {
    private int complaintId;
    private String complaintNumber;
    private int customerId;
    private Integer subscriptionId;
    private ComplaintCategory category;
    private String description;
    private String priority; // LOW, MEDIUM, HIGH, CRITICAL
    private LocalDateTime createdDate;
    private String status; // OPEN, IN_PROGRESS, RESOLVED, CLOSED
    private String resolution;

    private String customerName;
    private String mobileNumber;
    private String customerCity;
    private String customerNumber;

    public Complaint() {}

    public Complaint(int complaintId, String complaintNumber, int customerId, Integer subscriptionId,
                     ComplaintCategory category, String description, String priority,
                     LocalDateTime createdDate, String status, String resolution) {
        this.complaintId = complaintId;
        this.complaintNumber = complaintNumber;
        this.customerId = customerId;
        this.subscriptionId = subscriptionId;
        this.category = category;
        this.description = description;
        this.priority = priority;
        this.createdDate = createdDate;
        this.status = status;
        this.resolution = resolution;
    }

    public int getComplaintId() { return complaintId; }
    public void setComplaintId(int complaintId) { this.complaintId = complaintId; }

    public String getComplaintNumber() { return complaintNumber; }
    public void setComplaintNumber(String complaintNumber) { this.complaintNumber = complaintNumber; }

    public int getCustomerId() { return customerId; }
    public void setCustomerId(int customerId) { this.customerId = customerId; }

    public Integer getSubscriptionId() { return subscriptionId; }
    public void setSubscriptionId(Integer subscriptionId) { this.subscriptionId = subscriptionId; }

    public ComplaintCategory getCategory() { return category; }
    public void setCategory(ComplaintCategory category) { this.category = category; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public LocalDateTime getCreatedDate() { return createdDate; }
    public void setCreatedDate(LocalDateTime createdDate) { this.createdDate = createdDate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getResolution() { return resolution; }
    public void setResolution(String resolution) { this.resolution = resolution; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public String getMobileNumber() { return mobileNumber; }
    public void setMobileNumber(String mobileNumber) { this.mobileNumber = mobileNumber; }

    public String getCustomerCity() { return customerCity; }
    public void setCustomerCity(String customerCity) { this.customerCity = customerCity; }

    public String getCustomerNumber() { return customerNumber; }
    public void setCustomerNumber(String customerNumber) { this.customerNumber = customerNumber; }

    @Override
    public String toString() {
        return String.format("[%s] Category: %-8s | Priority: %-6s | Status: %-10s | Issue: %s",
                complaintNumber, category, priority, status, description);
    }
}
