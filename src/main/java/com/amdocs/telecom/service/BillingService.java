package com.amdocs.telecom.service;

import com.amdocs.telecom.exception.TelecomException;
import com.amdocs.telecom.model.Bill;
import java.util.List;

public interface BillingService {
    Bill generateMonthlyBill(int subscriptionId, String billingMonth) throws TelecomException;
    Bill generatePlanChangeBill(int subscriptionId, int newPlanId) throws TelecomException;
    List<Bill> getCustomerBills(int customerId);
    List<Bill> getUnpaidBills();
}
