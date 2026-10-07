package com.amdocs.telecom.service.impl;

import com.amdocs.telecom.dao.PlanDAO;
import com.amdocs.telecom.dao.SubscriptionDAO;
import com.amdocs.telecom.dao.impl.PlanDAOImpl;
import com.amdocs.telecom.dao.impl.SubscriptionDAOImpl;
import com.amdocs.telecom.dao.AuditAndNotificationDAO;
import com.amdocs.telecom.factory.DAOFactory;
import com.amdocs.telecom.model.AuditLog;
import com.amdocs.telecom.exception.TelecomException;
import com.amdocs.telecom.model.MobileSubscription;
import com.amdocs.telecom.model.SIMCard;
import com.amdocs.telecom.model.SubscriptionHistory;
import com.amdocs.telecom.model.TelecomPlan;
import com.amdocs.telecom.service.SubscriptionService;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.amdocs.telecom.service.BillingService;
import java.time.format.DateTimeFormatter;

public class SubscriptionServiceImpl implements SubscriptionService {

    private final SubscriptionDAO subscriptionDAO;
    private final PlanDAO planDAO;
    private final BillingService billingService;
    private final AuditAndNotificationDAO auditDAO;

    public SubscriptionServiceImpl() {
        this.subscriptionDAO = new SubscriptionDAOImpl();
        this.planDAO = new PlanDAOImpl();
        this.billingService = new BillingServiceImpl();
        this.auditDAO = DAOFactory.getAuditAndNotificationDAO();
    }

    @Override
    public MobileSubscription subscribeToPlan(int customerId, int planId, String simTypeStr) throws TelecomException {
        return com.amdocs.telecom.util.Transactions.run(() -> subscribeToPlanInternal(customerId, planId, simTypeStr));
    }

    private MobileSubscription subscribeToPlanInternal(int customerId, int planId, String simTypeStr) throws TelecomException {
        try {
            lockCustomer(customerId);
            if (simTypeStr == null) throw new TelecomException("SIM type is required.");
            try { simTypeStr = com.amdocs.telecom.model.SimType.valueOf(simTypeStr.trim().toUpperCase()).name(); }
            catch (IllegalArgumentException e) { throw new TelecomException("Invalid SIM type."); }
            Optional<TelecomPlan> planOpt = planDAO.findById(planId);
            if (!planOpt.isPresent()) {
                throw new TelecomException("Invalid Plan ID.");
            }
            TelecomPlan plan = planOpt.get();
            if (!"ACTIVE".equals(plan.getStatus())) {
                throw new TelecomException("Business Rule: Inactive plans cannot be selected.");
            }

            List<MobileSubscription> existingSubs = subscriptionDAO.findByCustomerId(customerId);
            boolean alreadySubscribed = existingSubs.stream()
                    .anyMatch(sub -> sub.getPlanId() == planId && "ACTIVE".equals(sub.getStatus()));
            if (alreadySubscribed) {
                throw new TelecomException("Business Rule: A customer cannot subscribe to the same plan twice simultaneously.");
            }

            Optional<SIMCard> simOpt = subscriptionDAO.findAvailableSIM(simTypeStr);
            if (!simOpt.isPresent()) {
                throw new TelecomException("No available " + simTypeStr + " SIM cards. Please contact administrator.");
            }
            SIMCard sim = simOpt.get();

            String newMobileNumber = "+91-987" + (1000000 + (int)(Math.random() * 9000000));
            String subNumber = "SUB" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0,16);

            MobileSubscription sub = new MobileSubscription();
            sub.setSubscriptionNumber(subNumber);
            sub.setCustomerId(customerId);
            sub.setPlanId(planId);
            sub.setMobileNumber(newMobileNumber);
            sub.setSimId(sim.getSimId());
            sub.setActivationDate(LocalDate.now());
            sub.setSubscriptionType(plan.getPlanType());
            sub.setStatus("ACTIVE");

            MobileSubscription created = subscriptionDAO.saveSubscription(sub);

            try {
                String billingMonth = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
                billingService.generateMonthlyBill(created.getSubscriptionId(), billingMonth);
            } catch (TelecomException e) {
                throw new TelecomException("Subscription was created, but its initial bill could not be generated: " + e.getMessage());
            }

            AuditLog audit = new AuditLog();
            audit.setEntityName("SUBSCRIPTION");
            audit.setEntityId(String.valueOf(created.getSubscriptionId()));
            audit.setAction("CREATED");
            audit.setDetails(String.format("Subscription %s created for customer %d", created.getSubscriptionNumber(), customerId));
            audit.setPerformedBy(String.valueOf(customerId));
            try {
                auditDAO.logAudit(audit);
            } catch (SQLException auditError) {
                java.util.logging.Logger.getLogger(SubscriptionServiceImpl.class.getName())
                        .log(java.util.logging.Level.WARNING,
                            "Subscription created but activity logging failed: {0}",
                            auditError.getMessage());
            }
            return created;
        } catch (SQLException e) {
            throw new TelecomException("Database error: " + e.getMessage());
        }
    }

    @Override
    public boolean changePlan(int subscriptionId, int newPlanId, int customerId, String changedBy) throws TelecomException {
        return com.amdocs.telecom.util.Transactions.run(() -> changePlanInternal(subscriptionId, newPlanId, customerId, changedBy));
    }

    private boolean changePlanInternal(int subscriptionId, int newPlanId, int customerId, String changedBy) throws TelecomException {
        try {
            lockCustomer(customerId);
            Optional<MobileSubscription> subOpt = subscriptionDAO.findById(subscriptionId);
            if (!subOpt.isPresent()) {
                throw new TelecomException("Subscription not found.");
            }
            MobileSubscription sub = subOpt.get();

            if (sub.getCustomerId() != customerId) {
                throw new TelecomException("You are not authorized to change this subscription.");
            }

            if (!"ACTIVE".equals(sub.getStatus())) throw new TelecomException("Only active subscriptions can change plans.");
            if (sub.getPlanId() == newPlanId) {
                throw new TelecomException("You are already subscribed to this plan.");
            }

            Optional<TelecomPlan> newPlanOpt = planDAO.findById(newPlanId);
            if (!newPlanOpt.isPresent() || !"ACTIVE".equals(newPlanOpt.get().getStatus())) {
                throw new TelecomException("The selected plan is invalid or inactive.");
            }

            TelecomPlan newPlan = newPlanOpt.get();
            TelecomPlan oldPlan = planDAO.findById(sub.getPlanId()).orElseThrow();
            if (sub.getSubscriptionType() != newPlan.getPlanType() && !(oldPlan.isAllowTypeChange() && newPlan.isAllowTypeChange())) {
                throw new TelecomException("Prepaid and postpaid plan types cannot be switched during a plan change.");
            }

            if (subscriptionDAO.findByCustomerId(customerId).stream().anyMatch(s -> s.getSubscriptionId()!=subscriptionId && s.getPlanId()==newPlanId && "ACTIVE".equals(s.getStatus())))
                throw new TelecomException("You already have an active subscription to this plan.");
            java.time.LocalDate lastChange = subscriptionDAO.getHistory(subscriptionId).stream().findFirst()
                    .map(h -> h.getChangeDate().toLocalDate()).orElse(sub.getActivationDate());
            if (java.time.temporal.ChronoUnit.DAYS.between(lastChange,java.time.LocalDate.now()) < oldPlan.getMinimumChangeDays())
                throw new TelecomException("This plan's minimum change period has not elapsed.");
            boolean changed = subscriptionDAO.changePlan(subscriptionId, newPlanId, "Customer Request", changedBy);
            if (changed) {
                billingService.generatePlanChangeBill(subscriptionId,newPlanId);
                AuditLog audit = new AuditLog();
                audit.setEntityName("SUBSCRIPTION");
                audit.setEntityId(String.valueOf(subscriptionId));
                audit.setAction("PLAN_CHANGED");
                audit.setDetails(String.format("Plan changed to %s", newPlan.getPlanCode()));
                audit.setPerformedBy(changedBy);
                try {
                    auditDAO.logAudit(audit);
                } catch (SQLException auditError) {
                    java.util.logging.Logger.getLogger(SubscriptionServiceImpl.class.getName())
                                .log(java.util.logging.Level.WARNING,
                                    "Plan changed but activity logging failed: {0}",
                                    auditError.getMessage());
                }
            }
            return changed;
        } catch (SQLException e) {
            throw new TelecomException("Database error: " + e.getMessage());
        }
    }

    private void lockCustomer(int id) throws SQLException, TelecomException {
        try (java.sql.Connection c=com.amdocs.telecom.util.DBConnection.getInstance().getConnection();
             java.sql.PreparedStatement p=c.prepareStatement("SELECT account_status FROM customers WHERE customer_id=? FOR UPDATE")) {
            p.setInt(1,id); try(java.sql.ResultSet r=p.executeQuery()) {
                if (!r.next() || !"ACTIVE".equals(r.getString(1))) throw new TelecomException("An active customer account is required.");
            }
        }
    }

    @Override
    public List<MobileSubscription> getCustomerSubscriptions(int customerId) {
        try {
            return subscriptionDAO.findByCustomerId(customerId);
        } catch (SQLException e) {
            throw new IllegalStateException("Database operation failed.", e);
        }
    }

    @Override
    public List<SubscriptionHistory> getSubscriptionHistory(int subscriptionId) throws TelecomException {
        try {
            return subscriptionDAO.getHistory(subscriptionId);
        } catch (SQLException e) {
            throw new TelecomException("Database error: " + e.getMessage());
        }
    }
}
