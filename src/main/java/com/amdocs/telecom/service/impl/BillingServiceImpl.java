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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.amdocs.telecom.dao.PlanDAO;
import com.amdocs.telecom.dao.impl.PlanDAOImpl;
import com.amdocs.telecom.model.TelecomPlan;
import java.time.format.DateTimeFormatter;

public class BillingServiceImpl implements BillingService {

    private static final Logger LOGGER = Logger.getLogger(BillingServiceImpl.class.getName());
    private static final BigDecimal TAX_RATE = new BigDecimal("0.18");

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
            // Skip bills that already exist.
            Optional<Bill> existing = billingDAO.findBySubscriptionAndMonth(subscriptionId, billingMonth);
            if (existing.isPresent()) {
                throw new TelecomException("Bill already generated for this month.");
            }

            Optional<MobileSubscription> subOpt = subscriptionDAO.findById(subscriptionId);
            if (!subOpt.isPresent()) {
                throw new TelecomException("Subscription not found.");
            }
            MobileSubscription sub = subOpt.get();

            // Use BigDecimal to avoid rounding errors in tax and totals.
            BigDecimal usageChargesBD = BigDecimal.valueOf(usageDAO.getTotalUsageCharge(subscriptionId, billingMonth))
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal planRentalBD = BigDecimal.valueOf(sub.getMonthlyRental())
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal taxableSubtotal = planRentalBD.add(usageChargesBD);
            BigDecimal taxAmountBD = taxableSubtotal.multiply(TAX_RATE).setScale(2, RoundingMode.HALF_UP);
            BigDecimal discountBD = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            BigDecimal totalAmountBD = taxableSubtotal.add(taxAmountBD).subtract(discountBD)
                    .setScale(2, RoundingMode.HALF_UP);

            double planRental = planRentalBD.doubleValue();
            double usageCharges = usageChargesBD.doubleValue();
            double taxAmount = taxAmountBD.doubleValue();
            double discount = discountBD.doubleValue();
            double totalAmount = totalAmountBD.doubleValue();

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

            // Use BigDecimal for financial calculations.
            BigDecimal planRentalBD = BigDecimal.valueOf(plan.getMonthlyRental()).setScale(2, RoundingMode.HALF_UP);
            BigDecimal taxAmountBD = planRentalBD.multiply(TAX_RATE).setScale(2, RoundingMode.HALF_UP);
            BigDecimal totalAmountBD = planRentalBD.add(taxAmountBD).setScale(2, RoundingMode.HALF_UP);

            double planRental = planRentalBD.doubleValue();
            double taxAmount = taxAmountBD.doubleValue();
            double totalAmount = totalAmountBD.doubleValue();
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

    // Let database errors reach the caller.
    @Override
    public List<Bill> getCustomerBills(int customerId) {
        try {
            return billingDAO.findByCustomerId(customerId);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error fetching bills for customer: " + customerId, e);
            throw new RuntimeException("Failed to retrieve customer bills due to a database error.", e);
        }
    }

    @Override
    public List<Bill> getUnpaidBills() {
        try {
            return billingDAO.findUnpaidBills();
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error fetching unpaid bills", e);
            throw new RuntimeException("Failed to retrieve unpaid bills due to a database error.", e);
        }
    }
}
