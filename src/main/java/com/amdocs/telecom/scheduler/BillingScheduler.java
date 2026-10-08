package com.amdocs.telecom.scheduler;

import com.amdocs.telecom.dao.SubscriptionDAO;
import com.amdocs.telecom.dao.impl.SubscriptionDAOImpl;
import com.amdocs.telecom.model.MobileSubscription;
import com.amdocs.telecom.exception.TelecomException;
import com.amdocs.telecom.service.BillingService;
import com.amdocs.telecom.service.impl.BillingServiceImpl;

import java.time.LocalDate;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

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

    public void start(long initialDelay, long period) {
        Runnable billingTask = () -> runBilling(false);

        scheduler.scheduleAtFixedRate(billingTask, initialDelay, period, TimeUnit.SECONDS);
        LOGGER.info(() -> "[BillingScheduler] Scheduled. Initial delay: " + initialDelay + "s, Period: " + period + "s");
    }

    public void triggerNow() {
        runBilling(true);
    }

    private void runBilling(boolean interactive) {
        String billingMonth = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        String runType = interactive ? "Manual" : "Scheduled";
        report("[BillingScheduler] " + runType + " billing trigger for: " + billingMonth, interactive);

        try {
            List<MobileSubscription> activeSubs = subscriptionDAO.findAll();
            int generated = 0;

            for (MobileSubscription sub : activeSubs) {
                if (!"ACTIVE".equals(sub.getStatus())) continue;
                try {
                    com.amdocs.telecom.dao.impl.BillingDAOImpl dao=new com.amdocs.telecom.dao.impl.BillingDAOImpl();
                    if(dao.findBySubscriptionAndMonth(sub.getSubscriptionId(),billingMonth).isPresent()) {
                        ((BillingServiceImpl)billingService).reconcileUsage(sub.getSubscriptionId(),billingMonth);
                        continue;
                    }
                    billingService.generateMonthlyBill(sub.getSubscriptionId(), billingMonth);
                    generated++;
                    report("  [OK] Bill generated for: " + sub.getSubscriptionNumber(), interactive);
                } catch (SQLException | TelecomException | RuntimeException e) {
                    LOGGER.log(Level.WARNING, e, () -> "Could not bill subscription " + sub.getSubscriptionId());
                }
            }
            for(com.amdocs.telecom.model.Bill bill:new com.amdocs.telecom.dao.impl.BillingDAOImpl().findAll()) {
                if("MONTHLY".equals(bill.getInvoiceType()) && !"CANCELLED".equals(bill.getBillStatus()))
                    ((BillingServiceImpl)billingService).reconcileUsage(bill.getSubscriptionId(),bill.getBillingMonth());
            }
            report("[BillingScheduler] " + runType + " run complete. Bills generated: " + generated, interactive);
        } catch (SQLException | TelecomException | RuntimeException e) {
            LOGGER.log(Level.SEVERE, e, () -> "Error during " + runType.toLowerCase(java.util.Locale.ROOT) + " billing");
            if (interactive) System.out.println("Billing failed. Check the application log for details.");
        }
    }

    private void report(String message, boolean interactive) {
        LOGGER.info(message);
        if (interactive) System.out.println(message);
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
