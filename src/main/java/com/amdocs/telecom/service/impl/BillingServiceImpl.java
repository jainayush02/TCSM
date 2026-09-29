package com.amdocs.telecom.service.impl;

import com.amdocs.telecom.dao.BillingDAO;
import com.amdocs.telecom.dao.SubscriptionDAO;
import com.amdocs.telecom.dao.UsageDAO;
import com.amdocs.telecom.dao.impl.BillingDAOImpl;
import com.amdocs.telecom.dao.impl.SubscriptionDAOImpl;
import com.amdocs.telecom.dao.impl.UsageDAOImpl;
import com.amdocs.telecom.exception.TelecomException;
import com.amdocs.telecom.model.Bill;
import com.amdocs.telecom.model.MobileSubscription;
import com.amdocs.telecom.service.BillingService;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.amdocs.telecom.dao.PlanDAO;
import com.amdocs.telecom.dao.impl.PlanDAOImpl;
import com.amdocs.telecom.model.TelecomPlan;
import java.time.format.DateTimeFormatter;

public class BillingServiceImpl implements BillingService {

    private final BillingDAO billingDAO;
    private final SubscriptionDAO subscriptionDAO;
    private final UsageDAO usageDAO;
    private final PlanDAO planDAO;

    public BillingServiceImpl() {
        this.billingDAO = new BillingDAOImpl();
        this.subscriptionDAO = new SubscriptionDAOImpl();
        this.usageDAO = new UsageDAOImpl();
        this.planDAO = new PlanDAOImpl();
    }

    @Override
    public Bill generateMonthlyBill(int subscriptionId, String billingMonth) throws TelecomException {
        try {
            // Check if already billed
            Optional<Bill> existing = billingDAO.findBySubscriptionAndMonth(subscriptionId, billingMonth);
            if (existing.isPresent()) {
                throw new TelecomException("Bill already generated for this month.");
            }

            Optional<MobileSubscription> subOpt = subscriptionDAO.findById(subscriptionId);
            if (!subOpt.isPresent()) {
                throw new TelecomException("Subscription not found.");
            }
            MobileSubscription sub = subOpt.get();

            double usageCharges = usageDAO.getTotalUsageCharge(subscriptionId, billingMonth);
            double planRental = sub.getMonthlyRental();
            
            // Simple tax calculation (e.g., 18% GST)
            double taxAmount = (planRental + usageCharges) * 0.18;
            double discount = 0.0;
            double totalAmount = planRental + usageCharges + taxAmount - discount;

            String billNumber = "INV-" + billingMonth + "-" + (1000 + (int)(Math.random()*9000)) + "-" + subscriptionId;

            Bill bill = new Bill();
            bill.setBillNumber(billNumber);
            bill.setSubscriptionId(subscriptionId);
            bill.setBillingMonth(billingMonth);
            bill.setPlanRental(planRental);
            bill.setUsageCharges(usageCharges);
            bill.setTaxAmount(taxAmount);
            bill.setDiscount(discount);
            bill.setTotalAmount(totalAmount);
            bill.setDueDate(LocalDate.now().plusDays(15));
            bill.setBillStatus("UNPAID");

            return billingDAO.save(bill);

        } catch (SQLException e) {
            throw new TelecomException("Database error during bill generation: " + e.getMessage());
        }
    }

    @Override
    public Bill generatePlanChangeBill(int subscriptionId, int newPlanId) throws TelecomException {
        try {
            Optional<TelecomPlan> planOpt = planDAO.findById(newPlanId);
            if (!planOpt.isPresent()) {
                throw new TelecomException("Plan not found.");
            }
            TelecomPlan plan = planOpt.get();

            double planRental = plan.getMonthlyRental();
            double taxAmount = planRental * 0.18;
            double totalAmount = planRental + taxAmount;
            String billingMonth = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
            String billNumber = "INV-CHG-" + billingMonth + "-" + (1000 + (int)(Math.random() * 9000)) + "-" + subscriptionId;

            Bill bill = new Bill();
            bill.setBillNumber(billNumber);
            bill.setSubscriptionId(subscriptionId);
            bill.setBillingMonth(billingMonth);
            bill.setPlanRental(planRental);
            bill.setUsageCharges(0.0);
            bill.setTaxAmount(taxAmount);
            bill.setDiscount(0.0);
            bill.setTotalAmount(totalAmount);
            bill.setDueDate(LocalDate.now().plusDays(15));
            bill.setBillStatus("UNPAID");

            return billingDAO.save(bill);
        } catch (SQLException e) {
            throw new TelecomException("Database error during plan change bill generation: " + e.getMessage());
        }
    }

    @Override
    public List<Bill> getCustomerBills(int customerId) {
        try {
            return billingDAO.findByCustomerId(customerId);
        } catch (SQLException e) {
            return List.of();
        }
    }

    @Override
    public List<Bill> getUnpaidBills() {
        try {
            return billingDAO.findUnpaidBills();
        } catch (SQLException e) {
            return List.of();
        }
    }
}
