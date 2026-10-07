package com.amdocs.telecom.service.impl;

import com.amdocs.telecom.dao.AuditAndNotificationDAO;
import com.amdocs.telecom.dao.BillingDAO;
import com.amdocs.telecom.dao.PaymentDAO;
import com.amdocs.telecom.dao.impl.AuditAndNotificationDAOImpl;
import com.amdocs.telecom.dao.impl.BillingDAOImpl;
import com.amdocs.telecom.dao.impl.PaymentDAOImpl;
import com.amdocs.telecom.exception.TelecomException;
import com.amdocs.telecom.model.AuditLog;
import com.amdocs.telecom.model.Bill;
import com.amdocs.telecom.model.Payment;
import com.amdocs.telecom.model.PaymentMode;
import com.amdocs.telecom.scheduler.PaymentNotificationService;
import com.amdocs.telecom.service.PaymentService;
import com.amdocs.telecom.util.DBConnection;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PaymentServiceImpl implements PaymentService {

    private static final Logger LOGGER = Logger.getLogger(PaymentServiceImpl.class.getName());

    private final BillingDAO billingDAO;
    private final PaymentDAO paymentDAO;
    private final AuditAndNotificationDAO auditDAO;
    private final PaymentNotificationService notificationService;

    public PaymentServiceImpl() {
        this(null);
    }

    public PaymentServiceImpl(PaymentNotificationService notificationService) {
        this.billingDAO = new BillingDAOImpl();
        this.paymentDAO = new PaymentDAOImpl();
        this.auditDAO = new AuditAndNotificationDAOImpl();
        this.notificationService = notificationService;
    }

    @Override
    public Payment processPayment(int billId, int customerId, double amount, String paymentModeStr, String processedBy) throws TelecomException {
        // Validate payment mode and execute strategy before acquiring DB lock
        // This keeps the lock short and reduces deadlock risk.
        PaymentMode mode;
        try {
            mode = PaymentMode.valueOf(paymentModeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new TelecomException("Invalid payment mode.");
        }

        com.amdocs.telecom.strategy.PaymentStrategy strategy = com.amdocs.telecom.strategy.PaymentStrategyFactory.getStrategy(mode);
        strategy.validateAndProcess(amount, String.valueOf(customerId));

        Connection conn = null;
        try {
            conn = DBConnection.getInstance().getConnection();
            conn.setAutoCommit(false); // BEGIN JDBC TRANSACTION

            // Lock and validate the bill.
            Optional<Bill> billOpt = billingDAO.findById(conn, billId);
            if (!billOpt.isPresent()) {
                throw new TelecomException("Bill not found.");
            }
            Bill bill = billOpt.get();
            if (bill.getCustomerId() != customerId) {
                throw new TelecomException("You are not authorized to pay this bill.");
            }
            if ("PAID".equals(bill.getBillStatus())) {
                throw new TelecomException("Bill is already paid. Duplicate payment not allowed.");
            }

            BigDecimal paymentAmount = BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP);
            BigDecimal billTotal = BigDecimal.valueOf(bill.getTotalAmount()).setScale(2, RoundingMode.HALF_UP);
            if (paymentAmount.compareTo(billTotal) != 0) {
                throw new TelecomException(String.format(
                        "Payment amount (₹%s) must exactly match the total due (₹%s).",
                        paymentAmount.toPlainString(), billTotal.toPlainString()));
            }

            // Create the payment record.
            String txnRef = "TXN" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            Payment payment = new Payment();
            payment.setTransactionReference(txnRef);
            payment.setBillId(billId);
            payment.setCustomerId(customerId);
            payment.setAmount(amount);
            payment.setPaymentMode(mode);
            payment.setPaymentDate(LocalDateTime.now());
            payment.setPaymentStatus("SUCCESS");

            Payment savedPayment = paymentDAO.save(conn, payment);

            // Update the bill status.
            billingDAO.updateStatus(conn, billId, "PAID");

            // Create the audit record.
            AuditLog audit = new AuditLog();
            audit.setEntityName("PAYMENT");
            audit.setEntityId(String.valueOf(savedPayment.getPaymentId()));
            audit.setAction("TRANSACTION");
            audit.setDetails("Payment of ₹" + paymentAmount.toPlainString() + " processed via " + mode);
            audit.setPerformedBy(processedBy);
            auditDAO.logAudit(conn, audit);

            conn.commit();
            if (notificationService != null) {
                notificationService.sendNotification(customerId, "Payment received",
                        "Payment " + savedPayment.getTransactionReference() + " was received for bill " + bill.getBillNumber() + ".");
            }
            return savedPayment;

        } catch (SQLException | TelecomException e) {
            // Roll back if any step fails.
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    LOGGER.log(Level.SEVERE, "Failed to rollback payment transaction for bill: " + billId, ex);
                }
            }
            throw new TelecomException("Payment processing failed: " + e.getMessage(), e);
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException e) {
                    LOGGER.log(Level.WARNING, "Failed to close connection after payment processing", e);
                }
            }
        }
    }
}
