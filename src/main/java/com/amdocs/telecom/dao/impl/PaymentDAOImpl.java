package com.amdocs.telecom.dao.impl;

import com.amdocs.telecom.dao.PaymentDAO;
import com.amdocs.telecom.model.Payment;
import com.amdocs.telecom.model.PaymentMode;
import com.amdocs.telecom.util.DBConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class PaymentDAOImpl implements PaymentDAO {

    @Override
    public Payment save(Payment payment) throws SQLException {
        try (Connection conn = DBConnection.getInstance().getConnection()) {
            return save(conn, payment);
        }
    }

    @Override
    public Payment save(Connection conn, Payment payment) throws SQLException {
        String sql = "INSERT INTO payments (transaction_reference, bill_id, customer_id, " +
                "amount, payment_mode, payment_date, payment_status) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, payment.getTransactionReference());
            ps.setInt(2, payment.getBillId());
            ps.setInt(3, payment.getCustomerId());
            ps.setDouble(4, payment.getAmount());
            ps.setString(5, payment.getPaymentMode().name());
            ps.setTimestamp(6, payment.getPaymentDate() != null ? Timestamp.valueOf(payment.getPaymentDate()) : new Timestamp(System.currentTimeMillis()));
            ps.setString(7, payment.getPaymentStatus());

            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    payment.setPaymentId(rs.getInt(1));
                }
            }
        }
        return payment;
    }

    @Override
    public Optional<Payment> findById(int paymentId) throws SQLException {
        String sql = "SELECT * FROM payments WHERE payment_id = ?";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, paymentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToPayment(rs));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<Payment> findByReference(String reference) throws SQLException {
        String sql = "SELECT * FROM payments WHERE transaction_reference = ?";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, reference);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToPayment(rs));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public List<Payment> findByCustomerId(int customerId) throws SQLException {
        List<Payment> list = new ArrayList<>();
        String sql = "SELECT * FROM payments WHERE customer_id = ? ORDER BY payment_date DESC";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSetToPayment(rs));
                }
            }
        }
        return list;
    }

    @Override
    public List<Payment> findByBillId(int billId) throws SQLException {
        List<Payment> list = new ArrayList<>();
        String sql = "SELECT * FROM payments WHERE bill_id = ? ORDER BY payment_date DESC";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, billId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSetToPayment(rs));
                }
            }
        }
        return list;
    }

    @Override
    public List<Payment> findAll() throws SQLException {
        List<Payment> list = new ArrayList<>();
        String sql = "SELECT * FROM payments ORDER BY payment_date DESC";
        try (Connection conn = DBConnection.getInstance().getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapResultSetToPayment(rs));
            }
        }
        return list;
    }

    private Payment mapResultSetToPayment(ResultSet rs) throws SQLException {
        Payment p = new Payment();
        p.setPaymentId(rs.getInt("payment_id"));
        p.setTransactionReference(rs.getString("transaction_reference"));
        p.setBillId(rs.getInt("bill_id"));
        p.setCustomerId(rs.getInt("customer_id"));
        p.setAmount(rs.getDouble("amount"));
        p.setPaymentMode(PaymentMode.valueOf(rs.getString("payment_mode")));
        Timestamp ts = rs.getTimestamp("payment_date");
        if (ts != null) p.setPaymentDate(ts.toLocalDateTime());
        p.setPaymentStatus(rs.getString("payment_status"));
        return p;
    }
}
