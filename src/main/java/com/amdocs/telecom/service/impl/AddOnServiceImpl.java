package com.amdocs.telecom.service.impl;

import com.amdocs.telecom.dao.impl.*;
import com.amdocs.telecom.exception.TelecomException;
import com.amdocs.telecom.model.*;
import com.amdocs.telecom.service.AddOnService;
import com.amdocs.telecom.util.*;
import java.math.*;
import java.sql.*;
import java.time.*;
import java.util.*;

public class AddOnServiceImpl implements AddOnService {
    private final AddOnDAOImpl addons=new AddOnDAOImpl();
    private MobileSubscription owned(int id,int customer) throws Exception {
        MobileSubscription sub=new SubscriptionDAOImpl().findById(id).orElseThrow(() -> new TelecomException("Subscription not found."));
        if(sub.getCustomerId()!=customer) throw new TelecomException("You do not own this subscription.");
        return sub;
    }
    @Override public List<AddOn> getCatalogue() throws TelecomException {
        try { return addons.findActive(); } catch(SQLException e) { throw new TelecomException("Could not load add-ons.",e); }
    }
    @Override public List<AddOn> getSubscriptionAddOns(int id,int customer) throws TelecomException {
        try { owned(id,customer); return addons.findBySubscription(id); }
        catch(TelecomException e) { throw e; } catch(Exception e) { throw new TelecomException("Could not load subscription add-ons.",e); }
    }
    @Override public void setActive(int id,int addOnId,int customer,boolean active) throws TelecomException {
        Transactions.run(() -> {
            try(Connection c=DBConnection.getInstance().getConnection(); PreparedStatement lock=c.prepareStatement("SELECT subscription_id FROM mobile_subscriptions WHERE subscription_id=? FOR UPDATE")) {
                lock.setInt(1,id); try(ResultSet r=lock.executeQuery()) { if(!r.next()) throw new TelecomException("Subscription not found."); }
            }
            MobileSubscription sub=owned(id,customer);
            if(!"ACTIVE".equals(sub.getStatus())) throw new TelecomException("Only active subscriptions can manage add-ons.");
            AddOn addon=addons.findActive().stream().filter(a -> a.id()==addOnId).findFirst().orElseThrow(() -> new TelecomException("Add-on not available."));
            boolean enabled=addons.findBySubscription(id).stream().anyMatch(a -> a.id()==addOnId);
            if(enabled==active) throw new TelecomException(active?"Add-on is already active.":"Add-on is not active.");
            try(Connection c=DBConnection.getInstance().getConnection(); PreparedStatement update=c.prepareStatement("UPDATE subscription_add_ons SET status=?,activated_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE subscription_id=? AND add_on_id=?")) {
                update.setString(1,active?"ACTIVE":"INACTIVE"); update.setInt(2,id); update.setInt(3,addOnId);
                if(update.executeUpdate()==0) try(PreparedStatement insert=c.prepareStatement("INSERT INTO subscription_add_ons(subscription_id,add_on_id,status) VALUES(?,?,'ACTIVE')")) {
                    insert.setInt(1,id); insert.setInt(2,addOnId); insert.executeUpdate();
                }
            }
            LocalDate now=LocalDate.now(); YearMonth month=YearMonth.from(now);
            BigDecimal price=addon.monthlyPrice().multiply(BigDecimal.valueOf(month.lengthOfMonth()-now.getDayOfMonth()+1))
                    .divide(BigDecimal.valueOf(month.lengthOfMonth()),2,RoundingMode.HALF_UP);
            new BillingServiceImpl().adjustAddOn(id,active?price:price.negate(),"ADDON:"+UUID.randomUUID());
            AuditLog log=new AuditLog(); log.setEntityName("ADD_ON"); log.setEntityId(id+":"+addOnId);
            log.setAction(active?"ACTIVATED":"DEACTIVATED"); log.setDetails(addon.code()); log.setPerformedBy(String.valueOf(customer));
            new AuditAndNotificationDAOImpl().logAudit(log);
            return null;
        });
    }
}
