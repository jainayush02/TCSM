package com.amdocs.telecom.scheduler;

import com.amdocs.telecom.dao.BillingDAO;
import com.amdocs.telecom.dao.SubscriptionDAO;
import com.amdocs.telecom.dao.impl.BillingDAOImpl;
import com.amdocs.telecom.dao.impl.SubscriptionDAOImpl;
import com.amdocs.telecom.model.Bill;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class AccountMonitor {

    private static final Logger LOGGER = Logger.getLogger(AccountMonitor.class.getName());
    private final ExecutorService executor;
    private final BillingDAO billingDAO;
    private final SubscriptionDAO subscriptionDAO;

    public AccountMonitor() {
        this.executor = Executors.newFixedThreadPool(2);
        this.billingDAO = new BillingDAOImpl();
        this.subscriptionDAO = new SubscriptionDAOImpl();
    }

    public List<String> scanOverdueAccounts() {
        System.out.println("\n[AccountMonitor] Scanning for overdue accounts...");

        Callable<List<Bill>> overdueScanTask = () -> {
            LOGGER.info("[AccountMonitor-Task1] Scanning unpaid bills...");
            List<Bill> unpaid = billingDAO.findUnpaidBills();
            return unpaid.stream()
                    .filter(bill -> bill.getDueDate() != null && bill.getDueDate().isBefore(LocalDate.now()))
                    .collect(Collectors.toList());
        };

        Callable<Integer> markOverdueTask = () -> {
            LOGGER.info("[AccountMonitor-Task2] Marking overdue bills...");
            List<Bill> unpaid = billingDAO.findUnpaidBills();
            int count = 0;
            for (Bill bill : unpaid) {
                if (bill.getDueDate() != null && bill.getDueDate().isBefore(LocalDate.now())) {
                    if ("UNPAID".equals(bill.getBillStatus())) {
                        billingDAO.updateStatus(bill.getBillId(), "OVERDUE");
                        count++;
                    }
                }
            }
            return count;
        };

        List<String> results = new ArrayList<>();

        try {
            Future<List<Bill>> overdueFuture = executor.submit(overdueScanTask);
            Future<Integer> markFuture = executor.submit(markOverdueTask);

            // Wait for the scan to finish.
            List<Bill> overdueBills = overdueFuture.get(15, TimeUnit.SECONDS);
            System.out.println("[AccountMonitor] Found " + overdueBills.size() + " overdue bill(s):");

            for (Bill bill : overdueBills) {
                String summary = String.format("  ⚠ Bill %s | Customer: %s | Amount: ₹%.2f | Due: %s",
                        bill.getBillNumber(),
                        bill.getCustomerName() != null ? bill.getCustomerName() : "N/A",
                        bill.getTotalAmount(),
                        bill.getDueDate());
                results.add(summary);
                System.out.println(summary);
            }

            // Wait for the status updates to finish.
            Integer markedCount = markFuture.get(15, TimeUnit.SECONDS);
            System.out.println("[AccountMonitor] Bills marked as OVERDUE: " + markedCount);
            results.add("Total marked OVERDUE: " + markedCount);

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "[AccountMonitor] Error during scan", e);
            System.out.println("[AccountMonitor] Error: " + e.getMessage());
        }

        return results;
    }

    public int suspendDelinquentAccounts(int overdueDaysThreshold) {
        System.out.println("[AccountMonitor] Suspending accounts with bills overdue > " + overdueDaysThreshold + " days...");

        Callable<Integer> suspendTask = () -> {
            List<Bill> unpaid = billingDAO.findUnpaidBills();
            int suspended = 0;
            for (Bill bill : unpaid) {
                if (bill.getDueDate() != null) {
                    long daysOverdue = LocalDate.now().toEpochDay() - bill.getDueDate().toEpochDay();
                    if (daysOverdue > overdueDaysThreshold) {
                        subscriptionDAO.updateStatus(bill.getSubscriptionId(), "SUSPENDED");
                        suspended++;
                        System.out.println("  [✗] Suspended subscription for bill: " + bill.getBillNumber() + " (" + daysOverdue + " days overdue)");
                    }
                }
            }
            return suspended;
        };

        try {
            Future<Integer> future = executor.submit(suspendTask);
            int count = future.get(20, TimeUnit.SECONDS);
            System.out.println("[AccountMonitor] Total subscriptions suspended: " + count);
            return count;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "[AccountMonitor] Error suspending accounts", e);
            return 0;
        }
    }

    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        LOGGER.info("[AccountMonitor] Shut down.");
    }
}
