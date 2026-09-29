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
 */
public class PaymentNotificationService {

    private static final Logger LOGGER = Logger.getLogger(PaymentNotificationService.class.getName());
    private final BlockingQueue<Notification> notificationQueue;
    private final ExecutorService workerPool;
    private final AuditAndNotificationDAO notificationDAO;
    private volatile boolean running = true;

    public PaymentNotificationService() {
        this.notificationQueue = new LinkedBlockingQueue<>(100);
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
                while (running) {
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
     */
    public void sendNotification(int customerId, String title, String message) {
        Notification notif = new Notification();
        notif.setCustomerId(customerId);
        notif.setTitle(title);
        notif.setMessage(message);
        notif.setStatus("UNREAD");

        boolean added = notificationQueue.offer(notif);
        if (added) {
            LOGGER.info("[PaymentNotificationService] Notification queued for customer " + customerId);
        } else {
            LOGGER.warning("[PaymentNotificationService] Queue is full! Notification dropped for customer " + customerId);
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
