package com.amdocs.telecom.strategy;

import com.amdocs.telecom.exception.TelecomException;

public class UpiPaymentStrategy implements PaymentStrategy {
    @Override
    public boolean validateAndProcess(double amount, String customerAccount) throws TelecomException {
        if (amount <= 0) {
            throw new TelecomException("UPI transaction amount must be greater than zero.");
        }
        // Apply the UPI per-transaction limit.
        if (amount > 100000) {
            throw new TelecomException("UPI transaction limit exceeded (Max: ₹100,000 per transaction).");
        }
        return true;
    }

    @Override
    public String getChannelName() {
        return "Unified Payments Interface (UPI)";
    }
}
