package com.amdocs.telecom.strategy;

import com.amdocs.telecom.exception.TelecomException;
import com.amdocs.telecom.model.PaymentMode;

public class PaymentStrategyFactory {

    private PaymentStrategyFactory() {}

    public static PaymentStrategy getStrategy(PaymentMode mode) throws TelecomException {
        if (mode == null) {
            throw new TelecomException("Payment mode cannot be null.");
        }
        return switch (mode) {
            case UPI -> new UpiPaymentStrategy();
            case CARD -> new CardPaymentStrategy();
            case NET_BANKING, BANK_TRANSFER -> new NetBankingPaymentStrategy();
        };
    }
}
