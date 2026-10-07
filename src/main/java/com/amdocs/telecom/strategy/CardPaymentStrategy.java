package com.amdocs.telecom.strategy;

import com.amdocs.telecom.exception.TelecomException;

public class CardPaymentStrategy implements PaymentStrategy {
    @Override
    public boolean validateAndProcess(double amount, String customerAccount) throws TelecomException {
        if (!Double.isFinite(amount) || amount <= 0) {
            throw new TelecomException("Credit/Debit Card payment amount must be greater than zero.");
        }
        return true;
    }

    @Override
    public String getChannelName() {
        return "Credit/Debit Card Gateway";
    }
}
