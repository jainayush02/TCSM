package com.amdocs.telecom.scheduler;

import com.amdocs.telecom.dao.BillingDAO;
import com.amdocs.telecom.dao.impl.BillingDAOImpl;
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
    private final java.util.concurrent.ScheduledExecutorService scheduler=Executors.newSingleThreadScheduledExecutor();
    public void start(long initialDelay,long period) {
        if(initialDelay<0 || period<=0) throw new IllegalArgumentException("Invalid monitor interval.");
        scheduler.scheduleWithFixedDelay(() -> { scanOverdueAccounts(false); suspendDelinquentAccounts(30, false); },initialDelay,period,TimeUnit.SECONDS);
    }
    private final ExecutorService executor;
    private final BillingDAO billingDAO;

    public AccountMonitor() {
        this.executor = Executors.newFixedThreadPool(2);
        this.billingDAO = new BillingDAOImpl();
    }

    public List<String> scanOverdueAccounts() {
        return scanOverdueAccounts(true);
    }

    private List<String> scanOverdueAccounts(boolean interactive) {
        report("[AccountMonitor] Scanning for overdue accounts...", interactive);

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
                        try(java.sql.Connection c=com.amdocs.telecom.util.DBConnection.getInstance().getConnection();
                            java.sql.PreparedStatement p=c.prepareStatement("UPDATE bills SET bill_status='OVERDUE' WHERE bill_id=? AND bill_status='UNPAID'")) {
                            p.setInt(1,bill.getBillId()); count+=p.executeUpdate();
                        }
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
            report("[AccountMonitor] Found " + overdueBills.size() + " overdue bill(s):", interactive);

            for (Bill bill : overdueBills) {
                String summary = String.format("   Bill %s | Customer: %s | Amount: Rs %.2f | Due: %s",
                        bill.getBillNumber(),
                        bill.getCustomerName() != null ? bill.getCustomerName() : "N/A",
                        bill.getTotalAmount(),
                        bill.getDueDate());
                results.add(summary);
                report(summary, interactive);
            }

            // Wait for the status updates to finish.
            Integer markedCount = markFuture.get(15, TimeUnit.SECONDS);
            report("[AccountMonitor] Bills marked as OVERDUE: " + markedCount, interactive);
            results.add("Total marked OVERDUE: " + markedCount);

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "[AccountMonitor] Error during scan", e);
            if (interactive) System.out.println("Account scan failed. Check the application log for details.");
        }

        return results;
    }

    public int suspendDelinquentAccounts(int overdueDaysThreshold) {
        return suspendDelinquentAccounts(overdueDaysThreshold, true);
    }

    private int suspendDelinquentAccounts(int overdueDaysThreshold, boolean interactive) {
        report("[AccountMonitor] Suspending accounts with bills overdue > " + overdueDaysThreshold + " days...", interactive);

        Callable<Integer> suspendTask = () -> {
            List<Bill> unpaid = billingDAO.findUnpaidBills();
            int suspended = 0;
            for (Bill bill : unpaid) {
                if (bill.getDueDate() != null) {
                    long daysOverdue = LocalDate.now().toEpochDay() - bill.getDueDate().toEpochDay();
                    if (daysOverdue > overdueDaysThreshold) {
                        try(java.sql.Connection c=com.amdocs.telecom.util.DBConnection.getInstance().getConnection();
                            java.sql.PreparedStatement p=c.prepareStatement("UPDATE mobile_subscriptions SET status='SUSPENDED',updated_at=CURRENT_TIMESTAMP WHERE subscription_id=? AND status='ACTIVE' AND EXISTS(SELECT 1 FROM bills WHERE bill_id=? AND bill_status IN ('UNPAID','OVERDUE'))")) {
                            p.setInt(1,bill.getSubscriptionId()); p.setInt(2,bill.getBillId());
                            int updated = p.executeUpdate();
                            suspended += updated;
                            if (updated > 0) report("  [OK] Suspended subscription for bill: " + bill.getBillNumber() + " (" + daysOverdue + " days overdue)", interactive);
                        }
                    }
                }
            }
            return suspended;
        };

        try {
            Future<Integer> future = executor.submit(suspendTask);
            int count = future.get(20, TimeUnit.SECONDS);
            report("[AccountMonitor] Total subscriptions suspended: " + count, interactive);
            return count;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "[AccountMonitor] Error suspending accounts", e);
            if (interactive) System.out.println("Account suspension failed. Check the application log for details.");
            return 0;
        }
    }

    private void report(String message, boolean interactive) {
        LOGGER.info(message);
        if (interactive) System.out.println(message);
    }

    public void shutdown() {
        scheduler.shutdownNow();
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
