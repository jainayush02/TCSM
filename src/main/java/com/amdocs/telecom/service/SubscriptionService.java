package com.amdocs.telecom.service;

import com.amdocs.telecom.exception.TelecomException;
import com.amdocs.telecom.model.MobileSubscription;
import com.amdocs.telecom.model.SubscriptionHistory;

import java.util.List;

public interface SubscriptionService {
    MobileSubscription subscribeToPlan(int customerId, int planId, String simTypeStr) throws TelecomException;
    boolean changePlan(int subscriptionId, int newPlanId, int customerId, String changedBy) throws TelecomException;
    List<MobileSubscription> getCustomerSubscriptions(int customerId);
    List<SubscriptionHistory> getSubscriptionHistory(int subscriptionId) throws TelecomException;
}
