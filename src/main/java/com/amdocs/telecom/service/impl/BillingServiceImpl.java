package com.amdocs.telecom.service.impl;

import com.amdocs.telecom.dao.impl.*;
import com.amdocs.telecom.exception.TelecomException;
import com.amdocs.telecom.model.*;
import com.amdocs.telecom.service.BillingService;
import com.amdocs.telecom.util.*;
import java.math.*;
import java.sql.*;
import java.time.*;
import java.util.*;

public class BillingServiceImpl implements BillingService {
    private static final BigDecimal TAX = new BigDecimal("0.18");
    private final BillingDAOImpl bills = new BillingDAOImpl();
    private final SubscriptionDAOImpl subscriptions = new SubscriptionDAOImpl();
    private final UsageDAOImpl usage = new UsageDAOImpl();
    private final PlanDAOImpl plans = new PlanDAOImpl();

    private static BigDecimal money(double value) { return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP); }

    private YearMonth month(String value) throws TelecomException {
        try {
            YearMonth result = YearMonth.parse(value);
            if (!result.toString().equals(value)) throw new IllegalArgumentException();
            return result;
        } catch (Exception e) { throw new TelecomException("Billing month must use yyyy-MM."); }
    }

    private void lock(int subscriptionId) throws SQLException, TelecomException {
        try (Connection c = DBConnection.getInstance().getConnection();
             PreparedStatement p = c.prepareStatement("SELECT subscription_id FROM mobile_subscriptions WHERE subscription_id=? FOR UPDATE")) {
            p.setInt(1, subscriptionId);
            try (ResultSet r = p.executeQuery()) { if (!r.next()) throw new TelecomException("Subscription not found."); }
        }
    }

    private Optional<Bill> findKey(int subscriptionId, String key) throws SQLException {
        return bills.findBySubscriptionId(subscriptionId).stream().filter(b -> key.equals(b.getInvoiceKey())).findFirst();
    }

    private Bill invoice(int subscriptionId, String billingMonth, String type, String key,
                         BigDecimal rental, BigDecimal charges, BigDecimal discount) throws SQLException {
        Bill bill = new Bill();
        bill.setBillNumber("INV-" + type + "-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase());
        bill.setSubscriptionId(subscriptionId); bill.setBillingMonth(billingMonth);
        bill.setInvoiceType(type); bill.setInvoiceKey(key);
        bill.setPlanRental(rental.doubleValue()); bill.setUsageCharges(charges.doubleValue());
        bill.setTaxAmount(rental.add(charges).multiply(TAX).setScale(2,RoundingMode.HALF_UP).doubleValue());
        bill.setDiscount(discount.doubleValue());
        bill.setTotalAmount(rental.add(charges).add(money(bill.getTaxAmount())).subtract(discount).max(BigDecimal.ZERO).doubleValue());
        bill.setDueDate(LocalDate.now().plusDays(15));
        bill.setBillStatus(bill.getTotalAmount() == 0 ? "PAID" : "UNPAID");
        return bills.save(bill);
    }

    @Override
    public Bill generateMonthlyBill(int subscriptionId, String billingMonth) throws TelecomException {
        YearMonth period = month(billingMonth);
        return Transactions.run(() -> {
            lock(subscriptionId);
            if (bills.findBySubscriptionAndMonth(subscriptionId,billingMonth).isPresent())
                throw new TelecomException("Bill already generated for this month.");
            MobileSubscription sub = subscriptions.findById(subscriptionId).orElseThrow(() -> new TelecomException("Subscription not found."));
            if (period.atEndOfMonth().isBefore(sub.getActivationDate())) throw new TelecomException("Cannot bill before activation.");
            BigDecimal rental = money(sub.getMonthlyRental());
            if (YearMonth.from(sub.getActivationDate()).equals(period))
                rental = rental.multiply(BigDecimal.valueOf(period.lengthOfMonth()-sub.getActivationDate().getDayOfMonth()+1))
                        .divide(BigDecimal.valueOf(period.lengthOfMonth()),2,RoundingMode.HALF_UP);
            try (Connection c = DBConnection.getInstance().getConnection();
                 PreparedStatement p = c.prepareStatement("SELECT COALESCE(SUM(a.monthly_price),0) FROM subscription_add_ons sa JOIN add_on_services a ON a.add_on_id=sa.add_on_id WHERE sa.subscription_id=? AND sa.status='ACTIVE' AND a.status='ACTIVE'")) {
                p.setInt(1,subscriptionId);
                try (ResultSet r=p.executeQuery()) { r.next(); rental=rental.add(r.getBigDecimal(1)); }
            }
            BigDecimal charges = money(usage.getTotalUsageCharge(subscriptionId,billingMonth));
            BigDecimal total = rental.add(charges).multiply(BigDecimal.ONE.add(TAX)).setScale(2,RoundingMode.HALF_UP);
            BigDecimal discount = consumeCredit(subscriptionId,total);
            return invoice(subscriptionId,billingMonth,"MONTHLY","MONTHLY:"+subscriptionId+":"+billingMonth,rental,charges,discount);
        });
    }

    private BigDecimal consumeCredit(int id, BigDecimal maximum) throws SQLException {
        try (Connection c=DBConnection.getInstance().getConnection();
             PreparedStatement p=c.prepareStatement("SELECT amount FROM billing_credits WHERE subscription_id=? FOR UPDATE")) {
            p.setInt(1,id);
            try (ResultSet r=p.executeQuery()) {
                if (!r.next()) return money(0);
                BigDecimal used=r.getBigDecimal(1).min(maximum);
                try (PreparedStatement update=c.prepareStatement("UPDATE billing_credits SET amount=amount-?,updated_at=CURRENT_TIMESTAMP WHERE subscription_id=?")) {
                    update.setBigDecimal(1,used); update.setInt(2,id); update.executeUpdate();
                }
                return used;
            }
        }
    }

    private void credit(int id, BigDecimal amount) throws SQLException {
        try (Connection c=DBConnection.getInstance().getConnection();
             PreparedStatement update=c.prepareStatement("UPDATE billing_credits SET amount=amount+?,updated_at=CURRENT_TIMESTAMP WHERE subscription_id=?")) {
            update.setBigDecimal(1,amount); update.setInt(2,id);
            if (update.executeUpdate()==0) try (PreparedStatement insert=c.prepareStatement("INSERT INTO billing_credits(subscription_id,amount) VALUES(?,?)")) {
                insert.setInt(1,id); insert.setBigDecimal(2,amount); insert.executeUpdate();
            }
        }
    }

    @Override
    public Bill generatePlanChangeBill(int subscriptionId, int newPlanId) throws TelecomException {
        return Transactions.run(() -> {
            lock(subscriptionId);
            MobileSubscription sub=subscriptions.findById(subscriptionId).orElseThrow();
            if (sub.getPlanId()!=newPlanId) throw new TelecomException("Change the subscription plan before generating its adjustment.");
            SubscriptionHistory history=subscriptions.getHistory(subscriptionId).stream().findFirst()
                    .orElseThrow(() -> new TelecomException("No plan change to bill."));
            if (history.getNewPlanId()!=newPlanId) throw new TelecomException("Plan change history does not match.");
            String key="PLAN:"+history.getHistoryId();
            Optional<Bill> existing=findKey(subscriptionId,key);
            if (existing.isPresent()) return existing.get();
            TelecomPlan old=plans.findById(history.getOldPlanId()).orElseThrow();
            TelecomPlan next=plans.findById(newPlanId).orElseThrow();
            LocalDate date=history.getChangeDate().toLocalDate(); YearMonth period=YearMonth.from(date);
            BigDecimal difference=money(next.getMonthlyRental()).subtract(money(old.getMonthlyRental()))
                    .multiply(BigDecimal.valueOf(period.lengthOfMonth()-date.getDayOfMonth()+1))
                    .divide(BigDecimal.valueOf(period.lengthOfMonth()),2,RoundingMode.HALF_UP);
            if (difference.signum()<0) {
                credit(subscriptionId,difference.abs().multiply(BigDecimal.ONE.add(TAX)).setScale(2,RoundingMode.HALF_UP));
                difference=money(0);
            }
            return invoice(subscriptionId,period.toString(),"PLAN_CHANGE",key,difference,money(0),money(0));
        });
    }

    public void reconcileUsage(int subscriptionId, String billingMonth) throws TelecomException {
        month(billingMonth);
        Transactions.run(() -> {
            lock(subscriptionId);
            if (bills.findBySubscriptionAndMonth(subscriptionId,billingMonth).isEmpty()) return null;
            List<Bill> periodBills=bills.findBySubscriptionId(subscriptionId).stream()
                    .filter(b -> billingMonth.equals(b.getBillingMonth()) && !"CANCELLED".equals(b.getBillStatus())).toList();
            BigDecimal recorded=money(usage.getTotalUsageCharge(subscriptionId,billingMonth));
            BigDecimal billed=money(periodBills.stream().mapToDouble(Bill::getUsageCharges).sum());
            BigDecimal delta=recorded.subtract(billed);
            if (delta.signum()<=0) return null;
            Optional<Bill> open=periodBills.stream().filter(b -> !"PAID".equals(b.getBillStatus())
                    && (b.getInvoiceType().equals("MONTHLY") || b.getInvoiceType().equals("USAGE"))).findFirst();
            if (open.isPresent()) {
                Bill current;
                try (Connection c=DBConnection.getInstance().getConnection()) {
                    current=bills.findById(c,open.get().getBillId()).orElseThrow();
                }
                if ("PAID".equals(current.getBillStatus())) {
                    invoice(subscriptionId,billingMonth,"USAGE","USAGE:"+UUID.randomUUID(),money(0),delta,money(0));
                } else {
                    BigDecimal charges=money(current.getUsageCharges()).add(delta);
                    BigDecimal tax=money(current.getPlanRental()).add(charges).multiply(TAX).setScale(2,RoundingMode.HALF_UP);
                    try (Connection c=DBConnection.getInstance().getConnection();
                         PreparedStatement p=c.prepareStatement("UPDATE bills SET usage_charges=?,tax_amount=?,total_amount=? WHERE bill_id=?")) {
                        p.setBigDecimal(1,charges); p.setBigDecimal(2,tax);
                        p.setBigDecimal(3,money(current.getPlanRental()).add(charges).add(tax).subtract(money(current.getDiscount())));
                        p.setInt(4,current.getBillId()); p.executeUpdate();
                    }
                }
            } else invoice(subscriptionId,billingMonth,"USAGE","USAGE:"+UUID.randomUUID(),money(0),delta,money(0));
            return null;
        });
    }

    public Bill adjustAddOn(int subscriptionId, BigDecimal amount, String key) throws TelecomException {
        return Transactions.run(() -> {
            lock(subscriptionId);
            Optional<Bill> existing=findKey(subscriptionId,key);
            if(existing.isPresent()) return existing.get();
            BigDecimal charge=amount;
            if(amount.signum()<0) {
                credit(subscriptionId,amount.abs().multiply(BigDecimal.ONE.add(TAX)).setScale(2,RoundingMode.HALF_UP));
                charge=money(0);
            }
            return invoice(subscriptionId,YearMonth.now().toString(),"ADD_ON",key,charge,money(0),money(0));
        });
    }

    public BigDecimal getCredit(int subscriptionId) throws TelecomException {
        try (Connection c=DBConnection.getInstance().getConnection(); PreparedStatement p=c.prepareStatement("SELECT amount FROM billing_credits WHERE subscription_id=?")) {
            p.setInt(1,subscriptionId); try(ResultSet r=p.executeQuery()) { return r.next()?r.getBigDecimal(1):money(0); }
        } catch(SQLException e) { throw new TelecomException("Could not read billing credit.",e); }
    }

    @Override public List<Bill> getCustomerBills(int customerId) {
        try { return bills.findByCustomerId(customerId); } catch(SQLException e) { throw new IllegalStateException("Could not retrieve bills.",e); }
    }
    @Override public List<Bill> getUnpaidBills() {
        try { return bills.findUnpaidBills(); } catch(SQLException e) { throw new IllegalStateException("Could not retrieve unpaid bills.",e); }
    }
}
