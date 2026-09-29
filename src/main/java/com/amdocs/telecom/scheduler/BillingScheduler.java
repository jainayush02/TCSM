package com.amdocs.telecom.scheduler;

import com.amdocs.telecom.dao.SubscriptionDAO;
import com.amdocs.telecom.dao.impl.SubscriptionDAOImpl;
import com.amdocs.telecom.model.MobileSubscription;
import com.amdocs.telecom.service.BillingService;
import com.amdocs.telecom.service.impl.BillingServiceImpl;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * BillingScheduler uses ScheduledExecutorService to run billing cycles
 * at fixed intervals. In production, this would run once per month.
 * For demonstration, it can be triggered manually or on a shorter interval.
 */
public class BillingScheduler {

    private static final Logger LOGGER = Logger.getLogger(BillingScheduler.class.getName());
    private final ScheduledExecutorService scheduler;
    private final BillingService billingService;
    private final SubscriptionDAO subscriptionDAO;

    public BillingScheduler() {
        this.scheduler = Executors.newScheduledThreadPool(2);
        this.billingService = new BillingServiceImpl();
        this.subscriptionDAO = new SubscriptionDAOImpl();
    }

    /**
     * Starts the billing scheduler to run periodically.
     * @param initialDelay delay before first execution (seconds)
     * @param period       interval between executions (seconds)
     */
    public void start(long initialDelay, long period) {
        Runnable billingTask = () -> {
            String billingMonth = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
            LOGGER.info("[BillingScheduler] Starting monthly billing cycle for: " + billingMonth);
            System.out.println("\n[BillingScheduler] === Monthly Billing Cycle Started for " + billingMonth + " ===");

            try {
                List<MobileSubscription> activeSubs = subscriptionDAO.findAll();
                int generated = 0;
                int skipped = 0;

                for (MobileSubscription sub : activeSubs) {
                    if (!"ACTIVE".equals(sub.getStatus())) {
                        skipped++;
                        continue;
                    }
                    try {
                        billingService.generateMonthlyBill(sub.getSubscriptionId(), billingMonth);
                        generated++;
                        System.out.println("  [✓] Bill generated for Subscription: " + sub.getSubscriptionNumber());
                    } catch (Exception e) {
                        skipped++;
                        // Bill may already exist for this month
                    }
                }
                System.out.println("[BillingScheduler] Cycle complete. Generated: " + generated + " | Skipped: " + skipped);
                LOGGER.info("[BillingScheduler] Cycle complete. Generated=" + generated + ", Skipped=" + skipped);

            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "[BillingScheduler] Error during billing cycle", e);
            }
        };

        scheduler.scheduleAtFixedRate(billingTask, initialDelay, period, TimeUnit.SECONDS);
        LOGGER.info("[BillingScheduler] Scheduled. Initial delay: " + initialDelay + "s, Period: " + period + "s");
    }

    /**
     * Triggers a single immediate billing run (useful for admin on-demand trigger).
     */
    public void triggerNow() {
        String billingMonth = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        LOGGER.info("[BillingScheduler] Manual trigger for: " + billingMonth);
        System.out.println("\n[BillingScheduler] Manual billing trigger for: " + billingMonth);

        try {
            List<MobileSubscription> activeSubs = subscriptionDAO.findAll();
            int generated = 0;

            for (MobileSubscription sub : activeSubs) {
                if (!"ACTIVE".equals(sub.getStatus())) continue;
                try {
                    billingService.generateMonthlyBill(sub.getSubscriptionId(), billingMonth);
                    generated++;
                    System.out.println("  [✓] Bill generated for: " + sub.getSubscriptionNumber());
                } catch (Exception ignored) {
                    // Already billed
                }
            }
            System.out.println("[BillingScheduler] Manual run complete. Bills generated: " + generated);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error during manual billing", e);
        }
    }

    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        LOGGER.info("[BillingScheduler] Shut down.");
    }
}
