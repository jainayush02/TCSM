package com.amdocs.telecom.dao.impl;

import com.amdocs.telecom.dao.ComplaintDAO;
import com.amdocs.telecom.model.Complaint;
import com.amdocs.telecom.model.ComplaintCategory;
import com.amdocs.telecom.util.DBConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ComplaintDAOImpl implements ComplaintDAO {

    @Override
    public Complaint save(Complaint complaint) throws SQLException {
        String sql = "INSERT INTO complaints (complaint_number, customer_id, subscription_id, " +
                "category, description, priority, status, resolution) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, complaint.getComplaintNumber());
            ps.setInt(2, complaint.getCustomerId());
            if (complaint.getSubscriptionId() != null) ps.setInt(3, complaint.getSubscriptionId());
            else ps.setNull(3, Types.INTEGER);
            ps.setString(4, complaint.getCategory().name());
            ps.setString(5, complaint.getDescription());
            ps.setString(6, complaint.getPriority() != null ? complaint.getPriority() : "MEDIUM");
            ps.setString(7, complaint.getStatus() != null ? complaint.getStatus() : "OPEN");
            ps.setString(8, complaint.getResolution());

            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    complaint.setComplaintId(rs.getInt(1));
                }
            }
        }
        return complaint;
    }

    @Override
    public Optional<Complaint> findById(int complaintId) throws SQLException {
        String sql = "SELECT cp.*, CONCAT(c.first_name, ' ', c.last_name) as customer_name, ms.mobile_number " +
                "FROM complaints cp " +
                "JOIN customers c ON cp.customer_id = c.customer_id " +
                "LEFT JOIN mobile_subscriptions ms ON cp.subscription_id = ms.subscription_id " +
                "WHERE cp.complaint_id = ?";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, complaintId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToComplaint(rs));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<Complaint> findByNumber(String complaintNumber) throws SQLException {
        String sql = "SELECT cp.*, CONCAT(c.first_name, ' ', c.last_name) as customer_name, ms.mobile_number " +
                "FROM complaints cp " +
                "JOIN customers c ON cp.customer_id = c.customer_id " +
                "LEFT JOIN mobile_subscriptions ms ON cp.subscription_id = ms.subscription_id " +
                "WHERE cp.complaint_number = ?";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, complaintNumber);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToComplaint(rs));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public List<Complaint> findByCustomerId(int customerId) throws SQLException {
        List<Complaint> list = new ArrayList<>();
        String sql = "SELECT cp.*, CONCAT(c.first_name, ' ', c.last_name) as customer_name, ms.mobile_number " +
                "FROM complaints cp " +
                "JOIN customers c ON cp.customer_id = c.customer_id " +
                "LEFT JOIN mobile_subscriptions ms ON cp.subscription_id = ms.subscription_id " +
                "WHERE cp.customer_id = ? ORDER BY cp.created_date DESC";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSetToComplaint(rs));
                }
            }
        }
        return list;
    }

    @Override
    public List<Complaint> findAll() throws SQLException {
        List<Complaint> list = new ArrayList<>();
        String sql = "SELECT cp.*, CONCAT(c.first_name, ' ', c.last_name) as customer_name, ms.mobile_number " +
                "FROM complaints cp " +
                "JOIN customers c ON cp.customer_id = c.customer_id " +
                "LEFT JOIN mobile_subscriptions ms ON cp.subscription_id = ms.subscription_id " +
                "ORDER BY cp.complaint_id DESC";
        try (Connection conn = DBConnection.getInstance().getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapResultSetToComplaint(rs));
            }
        }
        return list;
    }

    @Override
    public boolean updateStatusAndResolution(int complaintId, String status, String resolution) throws SQLException {
        String sql = "UPDATE complaints SET status = ?, resolution = ? WHERE complaint_id = ?";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setString(2, resolution);
            ps.setInt(3, complaintId);
            return ps.executeUpdate() > 0;
        }
    }

    private Complaint mapResultSetToComplaint(ResultSet rs) throws SQLException {
        Complaint cp = new Complaint();
        cp.setComplaintId(rs.getInt("complaint_id"));
        cp.setComplaintNumber(rs.getString("complaint_number"));
        cp.setCustomerId(rs.getInt("customer_id"));

        int subId = rs.getInt("subscription_id");
        if (!rs.wasNull()) cp.setSubscriptionId(subId);

        cp.setCategory(ComplaintCategory.valueOf(rs.getString("category")));
        cp.setDescription(rs.getString("description"));
        cp.setPriority(rs.getString("priority"));

        Timestamp cd = rs.getTimestamp("created_date");
        if (cd != null) cp.setCreatedDate(cd.toLocalDateTime());

        cp.setStatus(rs.getString("status"));
        cp.setResolution(rs.getString("resolution"));
        cp.setCustomerName(rs.getString("customer_name"));
        cp.setMobileNumber(rs.getString("mobile_number"));

        return cp;
    }
}
