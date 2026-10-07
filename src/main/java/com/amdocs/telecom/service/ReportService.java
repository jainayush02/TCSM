package com.amdocs.telecom.service;

import com.amdocs.telecom.model.Customer;
import java.util.DoubleSummaryStatistics;
import java.util.List;
import java.util.Map;

public interface ReportService {
    List<Customer> getHighestConsumingCustomers(com.amdocs.telecom.model.UsageType type);
    default List<Customer> getHighestConsumingCustomers() { return getHighestConsumingCustomers(com.amdocs.telecom.model.UsageType.DATA); }
    Map<String, List<Customer>> getCustomersByCity();
    Map<String, DoubleSummaryStatistics> getRevenueSummaryByPlan();
    double getAverageMonthlyRevenuePerCustomer();
    List<Map<String, Object>> getMostSubscribedPlans();
    List<Customer> getCustomersWithUnpaidBills();
    Map<String, Double> getOverallUsageByType();
}
