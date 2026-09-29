package com.amdocs.telecom.service;

import com.amdocs.telecom.exception.TelecomException;
import com.amdocs.telecom.model.Payment;

public interface PaymentService {
    Payment processPayment(int billId, int customerId, double amount, String paymentModeStr, String processedBy) throws TelecomException;
}
