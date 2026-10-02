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

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public class PaymentServiceImpl implements PaymentService {

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
        Connection conn = null;
        try {
            conn = DBConnection.getInstance().getConnection();
            conn.setAutoCommit(false); // BEGIN JDBC TRANSACTION

            // 1. Validate Bill
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

            // 2. Validate Amount
            if (Double.compare(amount, bill.getTotalAmount()) != 0) {
                throw new TelecomException("Payment amount must exactly match the total due (₹" + bill.getTotalAmount() + ").");
            }

            // 3. Create Payment via Strategy Pattern
            PaymentMode mode;
            try {
                mode = PaymentMode.valueOf(paymentModeStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new TelecomException("Invalid payment mode.");
            }

            // Execute Strategy
            com.amdocs.telecom.strategy.PaymentStrategy strategy = com.amdocs.telecom.strategy.PaymentStrategyFactory.getStrategy(mode);
            strategy.validateAndProcess(amount, String.valueOf(customerId));

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

            // 4. Update Bill Status
            billingDAO.updateStatus(conn, billId, "PAID");

            // 5. Create Audit Record
            AuditLog audit = new AuditLog();
            audit.setEntityName("PAYMENT");
            audit.setEntityId(String.valueOf(savedPayment.getPaymentId()));
            audit.setAction("TRANSACTION");
            audit.setDetails("Payment of ₹" + amount + " processed via " + mode);
            audit.setPerformedBy(processedBy);
            auditDAO.logAudit(conn, audit);

            // COMMIT
            conn.commit();
            if (notificationService != null) {
                notificationService.sendNotification(customerId, "Payment received",
                        "Payment " + savedPayment.getTransactionReference() + " was received for bill " + bill.getBillNumber() + ".");
            }
            return savedPayment;

        } catch (SQLException | TelecomException e) {
            // ROLLBACK on Failure
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    // Ignore
                }
            }
            throw new TelecomException("Payment processing failed: " + e.getMessage());
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException e) {
                    // Ignore
                }
            }
        }
    }
}
