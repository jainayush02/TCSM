package com.amdocs.telecom.strategy;

import com.amdocs.telecom.exception.TelecomException;
import com.amdocs.telecom.model.PaymentMode;

/**
 * Factory Pattern providing the appropriate PaymentStrategy based on selected payment mode.
 */
public class PaymentStrategyFactory {

    private PaymentStrategyFactory() {}

    public static PaymentStrategy getStrategy(PaymentMode mode) throws TelecomException {
        if (mode == null) {
            throw new TelecomException("Payment mode cannot be null.");
        }
        switch (mode) {
            case UPI:
                return new UpiPaymentStrategy();
            case CARD:
                return new CardPaymentStrategy();
            case NET_BANKING:
            case BANK_TRANSFER:
                return new NetBankingPaymentStrategy();
            default:
                throw new TelecomException("Unsupported payment mode: " + mode);
        }
    }
}
