package com.amdocs.telecom.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class Bill {
    private String invoiceType = "MONTHLY";
    private String invoiceKey;
    public String getInvoiceType() { return invoiceType; }
    public void setInvoiceType(String value) { invoiceType = value; }
    public String getInvoiceKey() { return invoiceKey; }
    public void setInvoiceKey(String value) { invoiceKey = value; }
    private int billId;
    private String billNumber;
    private int subscriptionId;
    private String billingMonth; // e.g., '2026-08'
    private double planRental;
    private double usageCharges;
    private double taxAmount;
    private double discount;
    private double totalAmount;
    private LocalDate dueDate;
    private String billStatus; // UNPAID, PAID, OVERDUE, CANCELLED
    private LocalDateTime createdAt;

    private int customerId;
    private String customerNumber;
    private String customerName;
    private String mobileNumber;

    public Bill() {}

    public Bill(int billId, String billNumber, int subscriptionId, String billingMonth,
                double planRental, double usageCharges, double taxAmount, double discount,
                double totalAmount, LocalDate dueDate, String billStatus) {
        this.billId = billId;
        this.billNumber = billNumber;
        this.subscriptionId = subscriptionId;
        this.billingMonth = billingMonth;
        this.planRental = planRental;
        this.usageCharges = usageCharges;
        this.taxAmount = taxAmount;
        this.discount = discount;
        this.totalAmount = totalAmount;
        this.dueDate = dueDate;
        this.billStatus = billStatus;
    }

    public int getBillId() { return billId; }
    public void setBillId(int billId) { this.billId = billId; }

    public String getBillNumber() { return billNumber; }
    public void setBillNumber(String billNumber) { this.billNumber = billNumber; }

    public int getSubscriptionId() { return subscriptionId; }
    public void setSubscriptionId(int subscriptionId) { this.subscriptionId = subscriptionId; }

    public String getBillingMonth() { return billingMonth; }
    public void setBillingMonth(String billingMonth) { this.billingMonth = billingMonth; }

    public double getPlanRental() { return planRental; }
    public void setPlanRental(double planRental) { this.planRental = planRental; }

    public double getUsageCharges() { return usageCharges; }
    public void setUsageCharges(double usageCharges) { this.usageCharges = usageCharges; }

    public double getTaxAmount() { return taxAmount; }
    public void setTaxAmount(double taxAmount) { this.taxAmount = taxAmount; }

    public double getDiscount() { return discount; }
    public void setDiscount(double discount) { this.discount = discount; }

    public double getTotalAmount() { return totalAmount; }
    public void setTotalAmount(double totalAmount) { this.totalAmount = totalAmount; }

    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }

    public String getBillStatus() { return billStatus; }
    public void setBillStatus(String billStatus) { this.billStatus = billStatus; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public int getCustomerId() { return customerId; }
    public void setCustomerId(int customerId) { this.customerId = customerId; }

    public String getCustomerNumber() { return customerNumber; }
    public void setCustomerNumber(String customerNumber) { this.customerNumber = customerNumber; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public String getMobileNumber() { return mobileNumber; }
    public void setMobileNumber(String mobileNumber) { this.mobileNumber = mobileNumber; }

    @Override
    public String toString() {
        return String.format("  #%d [%s] Month: %s | Rental: Rs %.2f | Usage: Rs %.2f | Tax: Rs %.2f | Total: Rs %.2f | Due: %s | Status: %s",
                billId, billNumber, billingMonth, planRental, usageCharges, taxAmount, totalAmount, dueDate, billStatus);
    }
}
