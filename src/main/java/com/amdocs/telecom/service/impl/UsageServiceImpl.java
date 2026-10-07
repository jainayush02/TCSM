package com.amdocs.telecom.service.impl;

import com.amdocs.telecom.dao.SubscriptionDAO;
import com.amdocs.telecom.dao.UsageDAO;
import com.amdocs.telecom.dao.impl.SubscriptionDAOImpl;
import com.amdocs.telecom.dao.impl.UsageDAOImpl;
import com.amdocs.telecom.dao.AuditAndNotificationDAO;
import com.amdocs.telecom.factory.DAOFactory;
import com.amdocs.telecom.model.AuditLog;
import com.amdocs.telecom.model.MobileSubscription;
import com.amdocs.telecom.model.UsageRecord;
import com.amdocs.telecom.model.UsageType;
import com.amdocs.telecom.service.UsageService;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class UsageServiceImpl implements UsageService {

    private static final Logger LOGGER = Logger.getLogger(UsageServiceImpl.class.getName());
    private final UsageDAO usageDAO;
    private final SubscriptionDAO subscriptionDAO;
    private final AuditAndNotificationDAO auditDAO;

    public UsageServiceImpl() {
        this.usageDAO = new UsageDAOImpl();
        this.subscriptionDAO = new SubscriptionDAOImpl();
        this.auditDAO = DAOFactory.getAuditAndNotificationDAO();
    }

    // Propagate failures instead of returning null.
    @Override
    public UsageRecord recordUsage(int subscriptionId, String usageTypeStr, double quantity, String unit) {
        if (!Double.isFinite(quantity) || quantity <= 0) {
            throw new IllegalArgumentException("Usage quantity must be greater than zero.");
        }
        if (unit == null || unit.trim().isEmpty()) {
            throw new IllegalArgumentException("Usage unit is required.");
        }
        try {
            if (usageTypeStr == null) throw new IllegalArgumentException("Usage type is required.");
            UsageType usageType = UsageType.valueOf(usageTypeStr.toUpperCase());
            quantity = com.amdocs.telecom.util.UsageUnits.normalize(usageType,quantity,unit);
            unit = com.amdocs.telecom.util.UsageUnits.unit(usageType);
            MobileSubscription sub = subscriptionDAO.findById(subscriptionId).orElseThrow(() -> new IllegalArgumentException("Subscription not found."));
            if (!"ACTIVE".equals(sub.getStatus())) throw new IllegalArgumentException("Only active subscriptions can record usage.");
            // The demo uses a flat charge; production would use plan rates.
            double charge = 0.0;
            if (usageType == UsageType.ROAMING) charge = quantity * 0.5; // Example charge
            
            UsageRecord record = new UsageRecord();
            record.setSubscriptionId(subscriptionId);
            record.setUsageDate(LocalDateTime.now());
            record.setUsageType(usageType);
            record.setQuantity(quantity);
            record.setUnit(unit);
            record.setCharge(charge);
            
            UsageRecord saved;
            try {
                saved = com.amdocs.telecom.util.Transactions.run(() -> {
                    com.amdocs.telecom.util.Transactions.lockSubscriptions(java.util.List.of(subscriptionId));
                    UsageRecord inserted=usageDAO.save(record);
                    new BillingServiceImpl().reconcileUsage(subscriptionId,java.time.YearMonth.from(record.getUsageDate()).toString());
                    return inserted;
                });
            } catch (com.amdocs.telecom.exception.TelecomException e) { throw new IllegalStateException("Could not record and bill usage.",e); }
            AuditLog audit = new AuditLog();
            audit.setEntityName("USAGE");
            audit.setEntityId(String.valueOf(saved.getUsageId()));
            audit.setAction("RECORDED");
            audit.setDetails(usageType + " usage recorded for subscription " + subscriptionId);
            audit.setPerformedBy(String.valueOf(subscriptionId));
            try {
                auditDAO.logAudit(audit);
            } catch (SQLException auditError) {
                LOGGER.log(Level.WARNING, "Usage recorded but activity logging failed", auditError);
            }
            return saved;
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Invalid usage type: " + usageTypeStr, e);
        } catch (SQLException e) {
            // Throw the original database error instead of returning null.
            LOGGER.log(Level.SEVERE, "Database error recording usage for subscription: " + subscriptionId, e);
            throw new RuntimeException("Failed to record usage due to a database error. Please retry.", e);
        }
    }

    @Override
    public List<UsageRecord> getCustomerUsageHistory(int customerId) {
        List<UsageRecord> allUsage = new ArrayList<>();
        try {
            List<MobileSubscription> subs = subscriptionDAO.findByCustomerId(customerId);
            for (MobileSubscription sub : subs) {
                allUsage.addAll(usageDAO.findBySubscriptionId(sub.getSubscriptionId()));
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error fetching customer usage", e);
            throw new RuntimeException("Failed to fetch customer usage history.", e);
        }
        return allUsage;
    }

    @Override
    public List<UsageRecord> getSubscriptionUsage(int subscriptionId) {
        try {
            return usageDAO.findBySubscriptionId(subscriptionId);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error fetching subscription usage", e);
            throw new RuntimeException("Failed to fetch subscription usage.", e);
        }
    }

    @Override
    public Map<String, Double> getUsageSummary(int subscriptionId) {
        Map<String, Double> result = new LinkedHashMap<>();
        try {
            Map<UsageType, Double> summary = usageDAO.findBySubscriptionId(subscriptionId).stream().collect(java.util.stream.Collectors.groupingBy(UsageRecord::getUsageType,java.util.stream.Collectors.summingDouble(r -> com.amdocs.telecom.util.UsageUnits.normalize(r.getUsageType(),r.getQuantity(),r.getUnit()))));
            for (Map.Entry<UsageType, Double> entry : summary.entrySet()) {
                result.put(entry.getKey().name(), entry.getValue());
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error fetching usage summary", e);
            throw new RuntimeException("Failed to fetch usage summary.", e);
        }
        return result;
    }
}

