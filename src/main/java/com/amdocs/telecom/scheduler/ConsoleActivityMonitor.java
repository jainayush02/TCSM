package com.amdocs.telecom.scheduler;

import com.amdocs.telecom.dao.AuditAndNotificationDAO;
import com.amdocs.telecom.factory.DAOFactory;
import com.amdocs.telecom.model.AuditLog;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Polls the shared audit table so an administrator can observe activity
 * performed by other console processes using the same local database.
 */
public class ConsoleActivityMonitor {

    private ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private final AuditAndNotificationDAO auditDAO = DAOFactory.getAuditAndNotificationDAO();
    private volatile int lastAuditId;
    private volatile boolean running;

    public synchronized void start() {
        if (running) {
            return;
        }
        if (executor.isShutdown()) {
            executor = Executors.newSingleThreadScheduledExecutor();
        }
        running = true;
        lastAuditId = getLatestAuditId();
        System.out.println("[Live Monitor] Watching shared localhost activity. Polling every 2 seconds...");
        executor.scheduleAtFixedRate(this::poll, 0, 2, TimeUnit.SECONDS);
    }

    public synchronized void toggle() {
        if (running) {
            stop();
        } else {
            start();
        }
    }

    private int getLatestAuditId() {
        try {
            List<AuditLog> logs = auditDAO.getAuditLogs();
            return logs.stream().mapToInt(AuditLog::getAuditId).max().orElse(0);
        } catch (Exception e) {
            System.out.println("[Live Monitor] Could not read existing activity: " + e.getMessage());
            return 0;
        }
    }

    private void poll() {
        if (!running) {
            return;
        }
        try {
            List<AuditLog> newLogs = auditDAO.getAuditLogs().stream()
                    .filter(log -> log.getAuditId() > lastAuditId)
                    .sorted(Comparator.comparingInt(AuditLog::getAuditId))
                    .collect(java.util.stream.Collectors.toList());

            for (AuditLog log : newLogs) {
                System.out.println("\n[Live Activity] " + log);
                lastAuditId = Math.max(lastAuditId, log.getAuditId());
            }
        } catch (Exception e) {
            System.out.println("[Live Monitor] Database polling failed: " + e.getMessage());
        }
    }

    public synchronized void stop() {
        if (!running) {
            return;
        }
        running = false;
        executor.shutdownNow();
        System.out.println("[Live Monitor] Stopped.");
    }
}
