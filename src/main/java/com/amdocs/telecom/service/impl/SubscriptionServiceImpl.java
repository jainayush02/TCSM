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
        try {
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
            String subNumber = "SUB" + (10000 + (int)(Math.random() * 90000));

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
            audit.setDetails("Subscription " + created.getSubscriptionNumber() + " created for customer " + customerId);
            audit.setPerformedBy(String.valueOf(customerId));
            try {
                auditDAO.logAudit(audit);
            } catch (SQLException auditError) {
                java.util.logging.Logger.getLogger(SubscriptionServiceImpl.class.getName())
                        .warning("Subscription created but activity logging failed: " + auditError.getMessage());
            }
            return created;
        } catch (SQLException e) {
            throw new TelecomException("Database error: " + e.getMessage());
        }
    }

    @Override
    public boolean changePlan(int subscriptionId, int newPlanId, int customerId, String changedBy) throws TelecomException {
        try {
            Optional<MobileSubscription> subOpt = subscriptionDAO.findById(subscriptionId);
            if (!subOpt.isPresent()) {
                throw new TelecomException("Subscription not found.");
            }
            MobileSubscription sub = subOpt.get();

            if (sub.getCustomerId() != customerId) {
                throw new TelecomException("You are not authorized to change this subscription.");
            }

            if (sub.getPlanId() == newPlanId) {
                throw new TelecomException("You are already subscribed to this plan.");
            }

            Optional<TelecomPlan> newPlanOpt = planDAO.findById(newPlanId);
            if (!newPlanOpt.isPresent() || !"ACTIVE".equals(newPlanOpt.get().getStatus())) {
                throw new TelecomException("The selected plan is invalid or inactive.");
            }

            TelecomPlan newPlan = newPlanOpt.get();
            if (sub.getSubscriptionType() != newPlan.getPlanType()) {
                throw new TelecomException("Prepaid and postpaid plan types cannot be switched during a plan change.");
            }

            boolean changed = subscriptionDAO.changePlan(subscriptionId, newPlanId, "Customer Request", changedBy);
            if (changed) {
                AuditLog audit = new AuditLog();
                audit.setEntityName("SUBSCRIPTION");
                audit.setEntityId(String.valueOf(subscriptionId));
                audit.setAction("PLAN_CHANGED");
                audit.setDetails("Plan changed to " + newPlan.getPlanCode());
                audit.setPerformedBy(changedBy);
                try {
                    auditDAO.logAudit(audit);
                } catch (SQLException auditError) {
                    java.util.logging.Logger.getLogger(SubscriptionServiceImpl.class.getName())
                            .warning("Plan changed but activity logging failed: " + auditError.getMessage());
                }
            }
            return changed;
        } catch (SQLException e) {
            throw new TelecomException("Database error: " + e.getMessage());
        }
    }

    @Override
    public List<MobileSubscription> getCustomerSubscriptions(int customerId) {
        try {
            return subscriptionDAO.findByCustomerId(customerId);
        } catch (SQLException e) {
            return List.of();
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
