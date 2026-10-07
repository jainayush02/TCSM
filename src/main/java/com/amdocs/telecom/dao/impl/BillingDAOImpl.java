package com.amdocs.telecom.dao.impl;

import com.amdocs.telecom.dao.BillingDAO;
import com.amdocs.telecom.model.Bill;
import com.amdocs.telecom.util.DBConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class BillingDAOImpl implements BillingDAO {

    @Override
    public Bill save(Bill bill) throws SQLException {
        String sql = "INSERT INTO bills (bill_number, subscription_id, billing_month, plan_rental, " +
                "usage_charges, tax_amount, discount, total_amount, due_date, bill_status) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, bill.getBillNumber());
            ps.setInt(2, bill.getSubscriptionId());
            ps.setString(3, bill.getBillingMonth());
            ps.setDouble(4, bill.getPlanRental());
            ps.setDouble(5, bill.getUsageCharges());
            ps.setDouble(6, bill.getTaxAmount());
            ps.setDouble(7, bill.getDiscount());
            ps.setDouble(8, bill.getTotalAmount());
            ps.setDate(9, Date.valueOf(bill.getDueDate()));
            ps.setString(10, bill.getBillStatus() != null ? bill.getBillStatus() : "UNPAID");

            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    bill.setBillId(rs.getInt(1));
                }
            }
        }
        return bill;
    }

    @Override
    public Optional<Bill> findById(int billId) throws SQLException {
        String sql = "SELECT b.*, ms.mobile_number, ms.customer_id, c.customer_number, CONCAT(c.first_name, ' ', c.last_name) as customer_name " +
                "FROM bills b " +
                "JOIN mobile_subscriptions ms ON b.subscription_id = ms.subscription_id " +
                "JOIN customers c ON ms.customer_id = c.customer_id " +
                "WHERE b.bill_id = ?";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, billId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToBill(rs));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<Bill> findById(Connection conn, int billId) throws SQLException {
        // Step 1: Lock only the bill row - single-table lock, no join
        String lockSql = "SELECT * FROM bills WHERE bill_id = ? FOR UPDATE";
        Bill bill = null;
        try (PreparedStatement ps = conn.prepareStatement(lockSql)) {
            ps.setInt(1, billId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    bill = new Bill();
                    bill.setBillId(rs.getInt("bill_id"));
                    bill.setBillNumber(rs.getString("bill_number"));
                    bill.setSubscriptionId(rs.getInt("subscription_id"));
                    bill.setBillingMonth(rs.getString("billing_month"));
                    bill.setPlanRental(rs.getDouble("plan_rental"));
                    bill.setUsageCharges(rs.getDouble("usage_charges"));
                    bill.setTaxAmount(rs.getDouble("tax_amount"));
                    bill.setDiscount(rs.getDouble("discount"));
                    bill.setTotalAmount(rs.getDouble("total_amount"));
                    Date dd = rs.getDate("due_date");
                    if (dd != null) bill.setDueDate(dd.toLocalDate());
                    bill.setBillStatus(rs.getString("bill_status"));
                    Timestamp ts = rs.getTimestamp("created_at");
                    if (ts != null) bill.setCreatedAt(ts.toLocalDateTime());
                }
            }
        }

        if (bill == null) {
            return Optional.empty();
        }

        // Step 2: Fetch joined display fields without locking other tables
        String joinSql = "SELECT ms.mobile_number, ms.customer_id, c.customer_number, " +
                "CONCAT(c.first_name, ' ', c.last_name) AS customer_name " +
                "FROM mobile_subscriptions ms " +
                "JOIN customers c ON ms.customer_id = c.customer_id " +
                "WHERE ms.subscription_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(joinSql)) {
            ps.setInt(1, bill.getSubscriptionId());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    bill.setCustomerId(rs.getInt("customer_id"));
                    bill.setCustomerNumber(rs.getString("customer_number"));
                    bill.setCustomerName(rs.getString("customer_name"));
                    bill.setMobileNumber(rs.getString("mobile_number"));
                }
            }
        }

        return Optional.of(bill);
    }

    @Override
    public Optional<Bill> findByNumber(String billNumber) throws SQLException {
        String sql = "SELECT b.*, ms.mobile_number, ms.customer_id, c.customer_number, CONCAT(c.first_name, ' ', c.last_name) as customer_name " +
                "FROM bills b " +
                "JOIN mobile_subscriptions ms ON b.subscription_id = ms.subscription_id " +
                "JOIN customers c ON ms.customer_id = c.customer_id " +
                "WHERE b.bill_number = ?";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, billNumber);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToBill(rs));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<Bill> findBySubscriptionAndMonth(int subscriptionId, String billingMonth) throws SQLException {
        String sql = "SELECT b.*, ms.mobile_number, ms.customer_id, c.customer_number, CONCAT(c.first_name, ' ', c.last_name) as customer_name " +
                "FROM bills b " +
                "JOIN mobile_subscriptions ms ON b.subscription_id = ms.subscription_id " +
                "JOIN customers c ON ms.customer_id = c.customer_id " +
                "WHERE b.subscription_id = ? AND b.billing_month = ?";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, subscriptionId);
            ps.setString(2, billingMonth);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToBill(rs));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public List<Bill> findBySubscriptionId(int subscriptionId) throws SQLException {
        List<Bill> list = new ArrayList<>();
        String sql = "SELECT b.*, ms.mobile_number, ms.customer_id, c.customer_number, CONCAT(c.first_name, ' ', c.last_name) as customer_name " +
                "FROM bills b " +
                "JOIN mobile_subscriptions ms ON b.subscription_id = ms.subscription_id " +
                "JOIN customers c ON ms.customer_id = c.customer_id " +
                "WHERE b.subscription_id = ? ORDER BY b.due_date DESC";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, subscriptionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSetToBill(rs));
                }
            }
        }
        return list;
    }

    @Override
    public List<Bill> findByCustomerId(int customerId) throws SQLException {
        List<Bill> list = new ArrayList<>();
        String sql = "SELECT b.*, ms.mobile_number, ms.customer_id, c.customer_number, CONCAT(c.first_name, ' ', c.last_name) as customer_name " +
                "FROM bills b " +
                "JOIN mobile_subscriptions ms ON b.subscription_id = ms.subscription_id " +
                "JOIN customers c ON ms.customer_id = c.customer_id " +
                "WHERE ms.customer_id = ? ORDER BY b.due_date DESC";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSetToBill(rs));
                }
            }
        }
        return list;
    }

    @Override
    public List<Bill> findUnpaidBills() throws SQLException {
        List<Bill> list = new ArrayList<>();
        String sql = "SELECT b.*, ms.mobile_number, ms.customer_id, c.customer_number, CONCAT(c.first_name, ' ', c.last_name) as customer_name " +
                "FROM bills b " +
                "JOIN mobile_subscriptions ms ON b.subscription_id = ms.subscription_id " +
                "JOIN customers c ON ms.customer_id = c.customer_id " +
                "WHERE b.bill_status IN ('UNPAID', 'OVERDUE') ORDER BY b.due_date ASC";
        try (Connection conn = DBConnection.getInstance().getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapResultSetToBill(rs));
            }
        }
        return list;
    }

    @Override
    public List<Bill> findAll() throws SQLException {
        List<Bill> list = new ArrayList<>();
        String sql = "SELECT b.*, ms.mobile_number, ms.customer_id, c.customer_number, CONCAT(c.first_name, ' ', c.last_name) as customer_name " +
                "FROM bills b " +
                "JOIN mobile_subscriptions ms ON b.subscription_id = ms.subscription_id " +
                "JOIN customers c ON ms.customer_id = c.customer_id " +
                "ORDER BY b.bill_id DESC";
        try (Connection conn = DBConnection.getInstance().getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapResultSetToBill(rs));
            }
        }
        return list;
    }

    @Override
    public boolean updateStatus(int billId, String status) throws SQLException {
        try (Connection conn = DBConnection.getInstance().getConnection()) {
            return updateStatus(conn, billId, status);
        }
    }

    @Override
    public boolean updateStatus(Connection conn, int billId, String status) throws SQLException {
        String sql = "UPDATE bills SET bill_status = ? WHERE bill_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setInt(2, billId);
            return ps.executeUpdate() > 0;
        }
    }

    private Bill mapResultSetToBill(ResultSet rs) throws SQLException {
        Bill b = new Bill();
        b.setBillId(rs.getInt("bill_id"));
        b.setBillNumber(rs.getString("bill_number"));
        b.setSubscriptionId(rs.getInt("subscription_id"));
        b.setBillingMonth(rs.getString("billing_month"));
        b.setPlanRental(rs.getDouble("plan_rental"));
        b.setUsageCharges(rs.getDouble("usage_charges"));
        b.setTaxAmount(rs.getDouble("tax_amount"));
        b.setDiscount(rs.getDouble("discount"));
        b.setTotalAmount(rs.getDouble("total_amount"));

        Date dd = rs.getDate("due_date");
        if (dd != null) b.setDueDate(dd.toLocalDate());

        b.setBillStatus(rs.getString("bill_status"));

        Timestamp ts = rs.getTimestamp("created_at");
        if (ts != null) b.setCreatedAt(ts.toLocalDateTime());

        b.setCustomerId(rs.getInt("customer_id"));
        b.setCustomerNumber(rs.getString("customer_number"));
        b.setCustomerName(rs.getString("customer_name"));
        b.setMobileNumber(rs.getString("mobile_number"));

        return b;
    }
}
