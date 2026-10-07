package com.amdocs.telecom.strategy;

import com.amdocs.telecom.exception.TelecomException;

public interface PaymentStrategy {
    boolean validateAndProcess(double amount, String customerAccount) throws TelecomException;
    String getChannelName();
}
