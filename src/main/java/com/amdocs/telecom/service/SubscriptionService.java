package com.amdocs.telecom.service;

import com.amdocs.telecom.exception.TelecomException;
import com.amdocs.telecom.model.MobileSubscription;
import com.amdocs.telecom.model.SubscriptionHistory;

import java.util.List;

public interface SubscriptionService {
    static boolean isActive(MobileSubscription subscription) { return "ACTIVE".equals(subscription.getStatus()); }
    default List<MobileSubscription> getActiveSubscriptions(int customerId) { return getCustomerSubscriptions(customerId).stream().filter(SubscriptionService::isActive).collect(java.util.stream.Collectors.toList()); }
    MobileSubscription subscribeToPlan(int customerId, int planId, String simTypeStr) throws TelecomException;
    boolean changePlan(int subscriptionId, int newPlanId, int customerId, String changedBy) throws TelecomException;
    List<MobileSubscription> getCustomerSubscriptions(int customerId);
    List<SubscriptionHistory> getSubscriptionHistory(int subscriptionId) throws TelecomException;
}
