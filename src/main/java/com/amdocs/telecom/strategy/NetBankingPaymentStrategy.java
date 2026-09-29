package com.amdocs.telecom.strategy;

import com.amdocs.telecom.exception.TelecomException;

public class NetBankingPaymentStrategy implements PaymentStrategy {
    @Override
    public boolean validateAndProcess(double amount, String customerAccount) throws TelecomException {
        if (amount <= 0) {
            throw new TelecomException("Net Banking payment amount must be greater than zero.");
        }
        return true;
    }

    @Override
    public String getChannelName() {
        return "Direct Net Banking / NEFT";
    }
}
