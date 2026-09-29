package com.amdocs.telecom.strategy;

import com.amdocs.telecom.exception.TelecomException;

/**
 * Strategy Pattern Interface for processing different payment channels.
 */
public interface PaymentStrategy {
    boolean validateAndProcess(double amount, String customerAccount) throws TelecomException;
    String getChannelName();
}
