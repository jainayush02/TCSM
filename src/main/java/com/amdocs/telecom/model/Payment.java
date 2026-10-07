package com.amdocs.telecom.model;

import java.time.LocalDateTime;

public class Payment {
    private int paymentId;
    private String transactionReference;
    private int billId;
    private int customerId;
    private double amount;
    private PaymentMode paymentMode;
    private LocalDateTime paymentDate;
    private String paymentStatus; // SUCCESS, FAILED, PENDING

    public Payment() {}

    public Payment(int paymentId, String transactionReference, int billId, int customerId,
                   double amount, PaymentMode paymentMode, LocalDateTime paymentDate, String paymentStatus) {
        this.paymentId = paymentId;
        this.transactionReference = transactionReference;
        this.billId = billId;
        this.customerId = customerId;
        this.amount = amount;
        this.paymentMode = paymentMode;
        this.paymentDate = paymentDate;
        this.paymentStatus = paymentStatus;
    }

    public int getPaymentId() { return paymentId; }
    public void setPaymentId(int paymentId) { this.paymentId = paymentId; }

    public String getTransactionReference() { return transactionReference; }
    public void setTransactionReference(String transactionReference) { this.transactionReference = transactionReference; }

    public int getBillId() { return billId; }
    public void setBillId(int billId) { this.billId = billId; }

    public int getCustomerId() { return customerId; }
    public void setCustomerId(int customerId) { this.customerId = customerId; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }

    public PaymentMode getPaymentMode() { return paymentMode; }
    public void setPaymentMode(PaymentMode paymentMode) { this.paymentMode = paymentMode; }

    public LocalDateTime getPaymentDate() { return paymentDate; }
    public void setPaymentDate(LocalDateTime paymentDate) { this.paymentDate = paymentDate; }

    public String getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; }

    @Override
    public String toString() {
        return String.format("[%s] Ref: %s | Bill ID: %d | Amount: Rs %.2f | Mode: %s | Status: %s",
                paymentDate, transactionReference, billId, amount, paymentMode, paymentStatus);
    }
}
