package com.amdocs.telecom.scheduler;

import com.amdocs.telecom.dao.UsageDAO;
import com.amdocs.telecom.dao.impl.UsageDAOImpl;
import com.amdocs.telecom.model.UsageRecord;
import com.amdocs.telecom.model.UsageType;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class UsageProcessor {

    private static final Logger LOGGER = Logger.getLogger(UsageProcessor.class.getName());
    private final ExecutorService executor;
    private final UsageDAO usageDAO;
    private int totalProcessed = 0;
    private final Object lock = new Object();

    public UsageProcessor() {
        this.executor = Executors.newFixedThreadPool(4);
        this.usageDAO = new UsageDAOImpl();
    }

    public void processBulkUsage(List<UsageRecord> records, int batchSize) {
        if(records==null || batchSize<=0) throw new IllegalArgumentException("Records and a positive batch size are required.");
        for(UsageRecord record:records) {
            if(record==null || !Double.isFinite(record.getCharge()) || record.getCharge()<0) throw new IllegalArgumentException("Invalid usage record.");
            record.setQuantity(com.amdocs.telecom.util.UsageUnits.normalize(record.getUsageType(),record.getQuantity(),record.getUnit()));
            record.setUnit(com.amdocs.telecom.util.UsageUnits.unit(record.getUsageType()));
        }
        java.util.List<java.util.concurrent.Future<?>> tasks=new java.util.ArrayList<>();
        System.out.println("\n[UsageProcessor] Processing " + records.size() + " usage records in batches of " + batchSize + "...");
        LOGGER.info("[UsageProcessor] Starting bulk processing of " + records.size() + " records");

        List<List<UsageRecord>> batches = partitionList(records, batchSize);
        
        for (int i = 0; i < batches.size(); i++) {
            final List<UsageRecord> batch = batches.get(i);
            final int batchNumber = i + 1;

            tasks.add(executor.submit((Runnable) () -> {
                try {
                    int[] result = com.amdocs.telecom.util.Transactions.run(() -> {
                        com.amdocs.telecom.util.Transactions.lockSubscriptions(batch.stream().map(UsageRecord::getSubscriptionId).collect(java.util.stream.Collectors.toSet()));
                        int[] inserted=usageDAO.saveBatch(batch);
                        for(UsageRecord record:batch) new com.amdocs.telecom.service.impl.BillingServiceImpl().reconcileUsage(record.getSubscriptionId(),java.time.YearMonth.from(record.getUsageDate()).toString());
                        return inserted;
                    });
                    int count = (int)java.util.Arrays.stream(result).filter(value -> value>0 || value==java.sql.Statement.SUCCESS_NO_INFO).count();
                    
                    synchronized (lock) {
                        totalProcessed += count;
                    }
                    System.out.println("  [UsageProcessor] Batch #" + batchNumber + " completed: " + count + " records inserted.");
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "[UsageProcessor] Batch #" + batchNumber + " failed", e);
                    throw new IllegalStateException("Usage batch failed.",e);
                }
            }));
        }
        for(java.util.concurrent.Future<?> task:tasks) {
            try { task.get(30,TimeUnit.SECONDS); }
            catch(Exception e) { tasks.forEach(f -> f.cancel(true)); throw new IllegalStateException("Bulk usage processing failed.",e); }
        }
    }

    public static List<UsageRecord> generateSampleUsageRecords(int subscriptionId, int count) {
        List<UsageRecord> records = new ArrayList<>();
        UsageType[] types = UsageType.values();
        String[] units = {"Minutes", "Count", "MB", "MB"};

        for (int i = 0; i < count; i++) {
            UsageRecord r = new UsageRecord();
            r.setSubscriptionId(subscriptionId);
            r.setUsageDate(LocalDateTime.now().minusHours(i));
            
            int typeIdx = i % types.length;
            r.setUsageType(types[typeIdx]);
            r.setUnit(units[typeIdx]);
            r.setQuantity(types[typeIdx]==UsageType.SMS?10+(int)(Math.random()*100):10+(Math.random()*100));
            r.setCharge(r.getQuantity() * 0.05);
            records.add(r);
        }
        return records;
    }

    public int getTotalProcessed() {
        synchronized (lock) {
            return totalProcessed;
        }
    }

    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        System.out.println("[UsageProcessor] Total records processed: " + getTotalProcessed());
        LOGGER.info("[UsageProcessor] Shut down. Total processed: " + getTotalProcessed());
    }

    private <T> List<List<T>> partitionList(List<T> list, int size) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            partitions.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return partitions;
    }
}
