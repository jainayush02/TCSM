package com.amdocs.telecom.scheduler;

import com.amdocs.telecom.dao.AuditAndNotificationDAO;
import com.amdocs.telecom.dao.impl.AuditAndNotificationDAOImpl;
import com.amdocs.telecom.model.Notification;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * PaymentNotificationService uses a BlockingQueue and worker threads
 * to process payment notifications asynchronously.
 * 
 * Demonstrates: BlockingQueue, ExecutorService, Runnable, producer-consumer pattern.
 *
 * FIX #3: Increased queue capacity, added timed back-pressure with synchronous fallback
 * to prevent silent notification loss under queue saturation.
 */
public class PaymentNotificationService {

    private static final Logger LOGGER = Logger.getLogger(PaymentNotificationService.class.getName());
    private static final int QUEUE_CAPACITY = 1000; // FIX #3: Increased from 100 to handle burst loads
    private static final long ENQUEUE_TIMEOUT_MS = 500; // FIX #3: Back-pressure timeout before fallback

    private final BlockingQueue<Notification> notificationQueue;
    private final ExecutorService workerPool;
    private final AuditAndNotificationDAO notificationDAO;
    private volatile boolean running = true;

    public PaymentNotificationService() {
        this.notificationQueue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);
        this.workerPool = Executors.newFixedThreadPool(3);
        this.notificationDAO = new AuditAndNotificationDAOImpl();
    }

    /**
     * Starts the consumer worker threads that continuously poll the queue.
     */
    public void start() {
        for (int i = 0; i < 3; i++) {
            final int workerId = i + 1;
            workerPool.submit((Runnable) () -> {
                LOGGER.info("[NotificationWorker-" + workerId + "] Started.");
                // FIX #3: Continue draining queue after shutdown signal to prevent message loss
                while (running || !notificationQueue.isEmpty()) {
                    try {
                        // Blocks until a notification is available, with 2-second timeout
                        Notification notif = notificationQueue.poll(2, TimeUnit.SECONDS);
                        if (notif != null) {
                            processNotification(notif, workerId);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                LOGGER.info("[NotificationWorker-" + workerId + "] Stopped.");
            });
        }
        LOGGER.info("[PaymentNotificationService] Started with 3 worker threads.");
    }

    /**
     * Producer method: enqueues a notification for async processing.
     *
     * FIX #3: Uses timed offer() with back-pressure. If queue remains saturated
     * after timeout, falls back to synchronous DB persistence to guarantee zero message loss.
     */
    public void sendNotification(int customerId, String title, String message) {
        Notification notif = new Notification();
        notif.setCustomerId(customerId);
        notif.setTitle(title);
        notif.setMessage(message);
        notif.setStatus("UNREAD");

        boolean enqueued = false;
        try {
            // FIX #3: Timed back-pressure — wait up to 500ms for queue space
            enqueued = notificationQueue.offer(notif, ENQUEUE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.warning("[PaymentNotificationService] Thread interrupted while enqueueing notification for customer: " + customerId);
        }

        if (enqueued) {
            LOGGER.info("[PaymentNotificationService] Notification queued for customer " + customerId);
        } else {
            // FIX #3: BACK-PRESSURE FALLBACK — persist synchronously instead of dropping
            LOGGER.severe("[PaymentNotificationService] Queue saturated! Executing synchronous fallback for customer: " + customerId);
            executeEmergencyPersistence(notif);
        }
    }

    /**
     * FIX #3: Emergency synchronous persistence — guarantees notification is saved
     * even when the async queue is full, preventing silent message loss.
     */
    private void executeEmergencyPersistence(Notification notif) {
        try {
            notificationDAO.createNotification(notif);
            LOGGER.info("[EmergencyFallback] Successfully persisted notification directly to DB for customer " + notif.getCustomerId());
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "[CRITICAL] Emergency DB persistence also failed for customer " + notif.getCustomerId()
                    + ". Notification data: title='" + notif.getTitle() + "', message='" + notif.getMessage() + "'", ex);
        }
    }

    private void processNotification(Notification notif, int workerId) {
        try {
            notificationDAO.createNotification(notif);
            System.out.println("  [NotificationWorker-" + workerId + "] Sent: \"" + notif.getTitle() + "\" to Customer ID " + notif.getCustomerId());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "[NotificationWorker-" + workerId + "] Failed to persist notification", e);
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
