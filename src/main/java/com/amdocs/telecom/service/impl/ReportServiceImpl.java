package com.amdocs.telecom.service.impl;

import com.amdocs.telecom.dao.BillingDAO;
import com.amdocs.telecom.dao.CustomerDAO;
import com.amdocs.telecom.dao.PlanDAO;
import com.amdocs.telecom.dao.SubscriptionDAO;
import com.amdocs.telecom.dao.UsageDAO;
import com.amdocs.telecom.dao.impl.BillingDAOImpl;
import com.amdocs.telecom.dao.impl.CustomerDAOImpl;
import com.amdocs.telecom.dao.impl.PlanDAOImpl;
import com.amdocs.telecom.dao.impl.SubscriptionDAOImpl;
import com.amdocs.telecom.dao.impl.UsageDAOImpl;
import com.amdocs.telecom.model.Bill;
import com.amdocs.telecom.model.Customer;
import com.amdocs.telecom.model.MobileSubscription;
import com.amdocs.telecom.model.TelecomPlan;
import com.amdocs.telecom.model.UsageRecord;
import com.amdocs.telecom.service.ReportService;

import java.sql.SQLException;
import java.util.Collections;
import java.util.DoubleSummaryStatistics;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class ReportServiceImpl implements ReportService {

    private final CustomerDAO customerDAO;
    private final BillingDAO billingDAO;
    private final SubscriptionDAO subscriptionDAO;
    private final PlanDAO planDAO;
    private final UsageDAO usageDAO;

    public ReportServiceImpl() {
        this.customerDAO = new CustomerDAOImpl();
        this.billingDAO = new BillingDAOImpl();
        this.subscriptionDAO = new SubscriptionDAOImpl();
        this.planDAO = new PlanDAOImpl();
        this.usageDAO = new UsageDAOImpl();
    }

    @Override
    public List<Customer> getHighestConsumingCustomers() {
        // Find customers with the highest total bills.
        try {
            List<Bill> allBills = billingDAO.findAll();
            
            // Group bills by customer and total the amounts.
            Map<Integer, Double> customerTotals = allBills.stream()
                    .collect(Collectors.groupingBy(
                            Bill::getCustomerId,
                            Collectors.summingDouble(Bill::getTotalAmount)
                    ));

            List<Customer> allCustomers = customerDAO.findAll();
            
            // Sort customers by total bill amount, highest first.
            return allCustomers.stream()
                    .sorted((c1, c2) -> Double.compare(
                            customerTotals.getOrDefault(c2.getCustomerId(), 0.0),
                            customerTotals.getOrDefault(c1.getCustomerId(), 0.0)
                    ))
                    .limit(10) // Top 10
                    .collect(Collectors.toList());

        } catch (SQLException e) {
            return List.of();
        }
    }

    @Override
    public Map<String, List<Customer>> getCustomersByCity() {
        try {
            List<Customer> allCustomers = customerDAO.findAll();
            
            return allCustomers.stream()
                    .collect(Collectors.groupingBy(Customer::getCity));
        } catch (SQLException e) {
            return Map.of();
        }
    }

    @Override
    public Map<String, DoubleSummaryStatistics> getRevenueSummaryByPlan() {
        try {
            List<Bill> allBills = billingDAO.findAll();
            
            return allBills.stream()
                    .filter(b -> "PAID".equals(b.getBillStatus())) // only count paid bills as revenue
                    .collect(Collectors.groupingBy(
                            Bill::getBillingMonth, // Grouping by month
                            Collectors.summarizingDouble(Bill::getTotalAmount)
                    ));
        } catch (SQLException e) {
            return Map.of();
        }
    }

    @Override
    public double getAverageMonthlyRevenuePerCustomer() {
        try {
            List<Bill> allBills = billingDAO.findAll();
            List<Customer> allCustomers = customerDAO.findAll();
            
            if (allCustomers.isEmpty()) return 0.0;

            double totalRevenue = allBills.stream()
                    .filter(b -> "PAID".equals(b.getBillStatus()))
                    .mapToDouble(Bill::getTotalAmount)
                    .sum();

            return totalRevenue / allCustomers.size();
        } catch (SQLException e) {
            return 0.0;
        }
    }

    @Override
    public List<Map<String, Object>> getMostSubscribedPlans() {
        try {
            List<MobileSubscription> subscriptions = subscriptionDAO.findAll();
            List<TelecomPlan> plans = planDAO.findAll();

            Function<TelecomPlan, Integer> planKeyMapper = TelecomPlan::getPlanId;
            Map<Integer, TelecomPlan> planMap = plans.stream()
                    .collect(Collectors.toMap(planKeyMapper, Function.identity(), (p1, p2) -> p1));

            // Group subscriptions by plan and count them.
            Map<Integer, Long> subscriberCounts = subscriptions.stream()
                    .collect(Collectors.groupingBy(MobileSubscription::getPlanId, Collectors.counting()));

            // Use an empty list when there are no subscriptions.
            Supplier<List<Map<String, Object>>> emptyListSupplier = Collections::emptyList;
            if (subscriberCounts.isEmpty()) return emptyListSupplier.get();

            // Sort by subscriber count and collect the results.
            return subscriberCounts.entrySet().stream()
                    .sorted(Map.Entry.<Integer, Long>comparingByValue().reversed())
                    .map(entry -> {
                        Map<String, Object> map = new LinkedHashMap<>();
                        TelecomPlan plan = planMap.get(entry.getKey());
                        map.put("planId", entry.getKey());
                        map.put("planCode", plan != null ? plan.getPlanCode() : "N/A");
                        map.put("planName", plan != null ? plan.getPlanName() : "Unknown");
                        map.put("subscriberCount", entry.getValue());
                        map.put("monthlyRental", plan != null ? plan.getMonthlyRental() : 0.0);
                        return map;
                    })
                    .collect(Collectors.toList());
        } catch (SQLException e) {
            return List.of();
        }
    }

    @Override
    public List<Customer> getCustomersWithUnpaidBills() {
        try {
            List<Bill> allBills = billingDAO.findAll();
            List<Customer> allCustomers = customerDAO.findAll();

            // Filter unpaid and overdue bills.
            Predicate<Bill> isUnpaidOrOverdue = b -> "UNPAID".equalsIgnoreCase(b.getBillStatus())
                    || "OVERDUE".equalsIgnoreCase(b.getBillStatus());

            // Collect customer IDs for unpaid bills.
            Set<Integer> unpaidCustomerIds = allBills.stream()
                    .filter(isUnpaidOrOverdue)
                    .map(Bill::getCustomerId)
                    .collect(Collectors.toSet());

            // Filter customers with unpaid bills.
            Predicate<Customer> hasUnpaidBill = c -> unpaidCustomerIds.contains(c.getCustomerId());

            return allCustomers.stream()
                    .filter(hasUnpaidBill)
                    .collect(Collectors.toList());
        } catch (SQLException e) {
            return List.of();
        }
    }

    @Override
    public Map<String, Double> getOverallUsageByType() {
        try {
            List<UsageRecord> records = usageDAO.findAll();

            // Group usage by type and sum the quantities.
            return records.stream()
                    .collect(Collectors.groupingBy(
                            r -> r.getUsageType().name(),
                            LinkedHashMap::new,
                            Collectors.summingDouble(UsageRecord::getQuantity)
                    ));
        } catch (SQLException e) {
            return Map.of();
        }
    }
}
