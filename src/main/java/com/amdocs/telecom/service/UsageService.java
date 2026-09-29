package com.amdocs.telecom.service;

import com.amdocs.telecom.model.UsageRecord;
import java.util.List;
import java.util.Map;

public interface UsageService {
    UsageRecord recordUsage(int subscriptionId, String usageType, double quantity, String unit);
    List<UsageRecord> getCustomerUsageHistory(int customerId);
    List<UsageRecord> getSubscriptionUsage(int subscriptionId);
    Map<String, Double> getUsageSummary(int subscriptionId); // Example: VOICE -> 150.0, DATA -> 2.5
}
