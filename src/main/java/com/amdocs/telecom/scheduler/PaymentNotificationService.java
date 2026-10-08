package com.amdocs.telecom.scheduler;

import com.amdocs.telecom.dao.AuditAndNotificationDAO;
import com.amdocs.telecom.dao.impl.AuditAndNotificationDAOImpl;
import com.amdocs.telecom.model.Notification;
import java.sql.SQLException;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PaymentNotificationService {

    private static final Logger LOGGER = Logger.getLogger(PaymentNotificationService.class.getName());
    private static final int QUEUE_CAPACITY = 1000;
    private static final long ENQUEUE_TIMEOUT_MS = 500;

    private final BlockingQueue<Notification> notificationQueue;
    private final ExecutorService workerPool;
    private final AuditAndNotificationDAO notificationDAO;
    private volatile boolean running = true;

    public PaymentNotificationService() {
        this.notificationQueue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);
        this.workerPool = Executors.newFixedThreadPool(3);
        this.notificationDAO = new AuditAndNotificationDAOImpl();
    }

    public void start() {
        for (int i = 0; i < 3; i++) {
            final int workerId = i + 1;
            workerPool.submit((Runnable) () -> {
                LOGGER.info(() -> "[NotificationWorker-" + workerId + "] Started.");
                // Drain the queue before stopping so queued notifications are not lost.
                while (running || !notificationQueue.isEmpty()) {
                    try {
                        Notification notif = notificationQueue.poll(2, TimeUnit.SECONDS);
                        if (notif != null) {
                            processNotification(notif, workerId);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                LOGGER.info(() -> "[NotificationWorker-" + workerId + "] Stopped.");
            });
        }
        LOGGER.info("[PaymentNotificationService] Started with 3 worker threads.");
    }

    public void sendNotification(int customerId, String title, String message) {
        Notification notif = new Notification();
        notif.setCustomerId(customerId);
        notif.setTitle(title);
        notif.setMessage(message);
        notif.setStatus("UNREAD");

        if (!running) { executeEmergencyPersistence(notif); return; }
        boolean enqueued = false;
        try {
            // Wait up to 500 ms for queue space.
            enqueued = notificationQueue.offer(notif, ENQUEUE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.warning(() -> "[PaymentNotificationService] Thread interrupted while enqueueing notification for customer: " + customerId);
        }

        if (enqueued) {
            LOGGER.info(() -> "[PaymentNotificationService] Notification queued for customer " + customerId);
        } else {
            // Persist synchronously instead of dropping the notification.
            LOGGER.severe(() -> "[PaymentNotificationService] Queue saturated! Executing synchronous fallback for customer: " + customerId);
            executeEmergencyPersistence(notif);
        }
    }

    private void executeEmergencyPersistence(Notification notif) {
        try {
            notificationDAO.createNotification(notif);
            LOGGER.info(() -> "[EmergencyFallback] Successfully persisted notification directly to DB for customer " + notif.getCustomerId());
        } catch (SQLException | RuntimeException ex) {
            LOGGER.log(Level.SEVERE, ex, () -> "[CRITICAL] Emergency DB persistence also failed for customer " + notif.getCustomerId()
                    + ". Notification data: title='" + notif.getTitle() + "', message='" + notif.getMessage() + "'");
        }
    }

    private void processNotification(Notification notif, int workerId) {
        try {
            notificationDAO.createNotification(notif);
            LOGGER.info(() -> "[NotificationWorker-" + workerId + "] Notification saved for Customer ID " + notif.getCustomerId());
        } catch (SQLException | RuntimeException e) {
            LOGGER.log(Level.SEVERE, e, () -> "[NotificationWorker-" + workerId + "] Failed to persist notification");
        }
    }

    public void shutdown() {
        running = false;
        workerPool.shutdown();
        try {
            if (!workerPool.awaitTermination(10, TimeUnit.SECONDS)) {
                workerPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            workerPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
        LOGGER.info("[PaymentNotificationService] Shut down.");
    }
}
