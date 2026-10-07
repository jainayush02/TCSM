package com.amdocs.telecom.service;

import com.amdocs.telecom.model.UsageRecord;
import java.util.List;
import java.util.Map;

public interface UsageService {
    default Map<String,Double> getCustomerMonthlySummary(int customerId,String billingMonth) {
        java.time.YearMonth month=java.time.YearMonth.parse(billingMonth);
        return getCustomerUsageHistory(customerId).stream()
                .filter(r -> java.time.YearMonth.from(r.getUsageDate()).equals(month))
                .collect(java.util.stream.Collectors.groupingBy(r -> r.getUsageType().name(),
                        java.util.LinkedHashMap::new,java.util.stream.Collectors.summingDouble(r -> com.amdocs.telecom.util.UsageUnits.normalize(r.getUsageType(),r.getQuantity(),r.getUnit()))));
    }
    UsageRecord recordUsage(int subscriptionId, String usageType, double quantity, String unit);
    List<UsageRecord> getCustomerUsageHistory(int customerId);
    List<UsageRecord> getSubscriptionUsage(int subscriptionId);
    Map<String, Double> getUsageSummary(int subscriptionId); // Example: VOICE -> 150.0, DATA -> 2.5
}
