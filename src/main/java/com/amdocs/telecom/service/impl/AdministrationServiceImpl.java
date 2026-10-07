package com.amdocs.telecom.service.impl;

import com.amdocs.telecom.dao.impl.*;
import com.amdocs.telecom.exception.TelecomException;
import com.amdocs.telecom.model.*;
import com.amdocs.telecom.service.AdministrationService;
import com.amdocs.telecom.util.*;
import com.amdocs.telecom.validation.ValidationUtil;
import java.sql.*;
import java.util.*;

public class AdministrationServiceImpl implements AdministrationService {
    private void requireAdmin(Administrator admin) throws Exception {
        if(admin==null) throw new TelecomException("Administrator login is required.");
        Administrator stored=new AdminDAOImpl().findByUsername(admin.getUsername()).orElseThrow(() -> new TelecomException("Administrator login is required."));
        if(stored.getAdminId()!=admin.getAdminId() || !"ACTIVE".equals(stored.getAccountStatus())) throw new TelecomException("Active administrator access is required.");
    }
    private void audit(Administrator admin,String entity,int id,String action,String details) throws SQLException {
        AuditLog log=new AuditLog(); log.setEntityName(entity); log.setEntityId(String.valueOf(id));
        log.setAction(action); log.setDetails(details); log.setPerformedBy(admin.getUsername()); new AuditAndNotificationDAOImpl().logAudit(log);
    }
    @Override public void updateCustomer(Administrator admin,Customer customer) throws TelecomException {
        Transactions.run(() -> {
            requireAdmin(admin); new CustomerServiceImpl().updateProfile(customer);
            audit(admin,"CUSTOMER",customer.getCustomerId(),"UPDATED","Customer profile updated"); return null;
        });
    }
    @Override public void setCustomerStatus(Administrator admin,int customerId,String status) throws TelecomException {
        if(!Set.of("ACTIVE","SUSPENDED","INACTIVE").contains(status)) throw new TelecomException("Invalid customer status.");
        Transactions.run(() -> {
            requireAdmin(admin);
            if(!new CustomerDAOImpl().updateAccountStatus(customerId,status)) throw new TelecomException("Customer not found.");
            if(!status.equals("ACTIVE")) try(Connection c=DBConnection.getInstance().getConnection(); PreparedStatement p=c.prepareStatement("UPDATE mobile_subscriptions SET status='SUSPENDED',updated_at=CURRENT_TIMESTAMP WHERE customer_id=? AND status='ACTIVE'")) {
                p.setInt(1,customerId); p.executeUpdate();
            }
            audit(admin,"CUSTOMER",customerId,"STATUS_CHANGED",status); return null;
        });
    }
    @Override public SIMCard addSIM(Administrator admin,SIMCard sim) throws TelecomException {
        if(sim==null || sim.getSimType()==null || !ValidationUtil.isNotEmpty(sim.getSimNumber()) || !sim.getSimNumber().matches("[0-9]{10,30}") || sim.getImsi()==null || !sim.getImsi().matches("[0-9]{10,30}"))
            throw new TelecomException("SIM number and IMSI must contain 10 to 30 digits, with a valid SIM type.");
        return Transactions.run(() -> {
            requireAdmin(admin); sim.setStatus("ACTIVE"); SIMCard saved=new SubscriptionDAOImpl().saveSIM(sim);
            audit(admin,"SIM",saved.getSimId(),"CREATED",saved.getSimNumber()); return saved;
        });
    }
    @Override public void setSIMStatus(Administrator admin,int simId,String status) throws TelecomException {
        if(!Set.of("ACTIVE","INACTIVE").contains(status)) throw new TelecomException("Invalid SIM status.");
        Transactions.run(() -> {
            requireAdmin(admin);
            if(status.equals("INACTIVE") && new SubscriptionDAOImpl().findAll().stream().anyMatch(s -> s.getSimId()==simId && "ACTIVE".equals(s.getStatus())))
                throw new TelecomException("Suspend the active subscription before deactivating its SIM.");
            if(!new SubscriptionDAOImpl().updateSIMStatus(simId,status)) throw new TelecomException("SIM not found.");
            audit(admin,"SIM",simId,"STATUS_CHANGED",status); return null;
        });
    }
    @Override public void setSubscriptionStatus(Administrator admin,int id,String status) throws TelecomException {
        if(!Set.of("ACTIVE","SUSPENDED","INACTIVE").contains(status)) throw new TelecomException("Invalid subscription status.");
        Transactions.run(() -> {
            requireAdmin(admin); SubscriptionDAOImpl dao=new SubscriptionDAOImpl();
            MobileSubscription sub=dao.findById(id).orElseThrow(() -> new TelecomException("Subscription not found."));
            if(status.equals("ACTIVE")) {
                Customer customer=new CustomerDAOImpl().findById(sub.getCustomerId()).orElseThrow();
                if(!"ACTIVE".equals(customer.getAccountStatus())) throw new TelecomException("Reactivate the customer account first.");
                if(dao.findAllSIMs().stream().noneMatch(s -> s.getSimId()==sub.getSimId() && "ACTIVE".equals(s.getStatus()))) throw new TelecomException("SIM is inactive.");
                if(dao.findByCustomerId(sub.getCustomerId()).stream().anyMatch(s -> s.getSubscriptionId()!=id && s.getPlanId()==sub.getPlanId() && "ACTIVE".equals(s.getStatus())))
                    throw new TelecomException("An active subscription already uses this plan.");
            }
            dao.updateStatus(id,status); audit(admin,"SUBSCRIPTION",id,"STATUS_CHANGED",status); return null;
        });
    }
    @Override public void setPlanRules(Administrator admin,int id,boolean allow,int days) throws TelecomException {
        if(days<0 || days>3650) throw new TelecomException("Minimum change period must be between 0 and 3650 days.");
        Transactions.run(() -> {
            requireAdmin(admin);
            try(Connection c=DBConnection.getInstance().getConnection(); PreparedStatement p=c.prepareStatement("UPDATE telecom_plans SET allow_type_change=?,minimum_change_days=?,updated_at=CURRENT_TIMESTAMP WHERE plan_id=?")) {
                p.setBoolean(1,allow); p.setInt(2,days); p.setInt(3,id); if(p.executeUpdate()==0) throw new TelecomException("Plan not found.");
            }
            audit(admin,"PLAN",id,"RULES_CHANGED","Type change: "+allow+", minimum days: "+days); return null;
        });
    }
    @Override public List<UsageRecord> getUsage(Administrator admin,int id,String month) throws TelecomException {
        return Transactions.run(() -> {
            requireAdmin(admin); java.time.YearMonth.parse(month);
            if(new SubscriptionDAOImpl().findById(id).isEmpty()) throw new TelecomException("Subscription not found.");
            return new UsageDAOImpl().findBySubscriptionAndMonth(id,month);
        });
    }
}
