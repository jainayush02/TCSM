package com.amdocs.telecom.strategy;

import com.amdocs.telecom.exception.TelecomException;

public class NetBankingPaymentStrategy implements PaymentStrategy {
    @Override
    public boolean validateAndProcess(double amount, String customerAccount) throws TelecomException {
        if (!Double.isFinite(amount) || amount <= 0) {
            throw new TelecomException("Net Banking payment amount must be greater than zero.");
        }
        return true;
    }

    @Override
    public String getChannelName() {
        return "Direct Net Banking / NEFT";
    }
}
