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
        String sql = "SELECT cp.*, CONCAT(c.first_name, ' ', c.last_name) as customer_name, " +
                "c.city as customer_city, c.customer_number, ms.mobile_number " +
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
        String sql = "SELECT cp.*, CONCAT(c.first_name, ' ', c.last_name) as customer_name, " +
                "c.city as customer_city, c.customer_number, ms.mobile_number " +
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
        String sql = "SELECT cp.*, CONCAT(c.first_name, ' ', c.last_name) as customer_name, " +
                "c.city as customer_city, c.customer_number, ms.mobile_number " +
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
        String sql = "SELECT cp.*, CONCAT(c.first_name, ' ', c.last_name) as customer_name, " +
                "c.city as customer_city, c.customer_number, ms.mobile_number " +
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

    @Override
    public java.util.Map<String, int[]> getComplaintsCountByCity() throws SQLException {
        java.util.Map<String, int[]> stats = new java.util.LinkedHashMap<>();
        String sql = "SELECT c.city, " +
                "COUNT(cp.complaint_id) as total_count, " +
                "SUM(CASE WHEN cp.status = 'OPEN' THEN 1 ELSE 0 END) as open_count, " +
                "SUM(CASE WHEN cp.status = 'IN_PROGRESS' THEN 1 ELSE 0 END) as in_progress_count, " +
                "SUM(CASE WHEN cp.status = 'RESOLVED' OR cp.status = 'CLOSED' THEN 1 ELSE 0 END) as resolved_count " +
                "FROM complaints cp " +
                "JOIN customers c ON cp.customer_id = c.customer_id " +
                "GROUP BY c.city " +
                "ORDER BY total_count DESC, c.city ASC";
        try (Connection conn = DBConnection.getInstance().getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String city = rs.getString("city");
                if (city == null || city.trim().isEmpty()) city = "Unknown";
                int total = rs.getInt("total_count");
                int open = rs.getInt("open_count");
                int inProg = rs.getInt("in_progress_count");
                int resolved = rs.getInt("resolved_count");
                stats.put(city, new int[]{total, open, inProg, resolved});
            }
        }
        return stats;
    }

    @Override
    public java.util.Map<String, int[]> getComplaintsCountByCategory() throws SQLException {
        java.util.Map<String, int[]> stats = new java.util.LinkedHashMap<>();
        String sql = "SELECT cp.category, " +
                "COUNT(cp.complaint_id) as total_count, " +
                "SUM(CASE WHEN cp.status = 'OPEN' THEN 1 ELSE 0 END) as open_count, " +
                "SUM(CASE WHEN cp.status = 'IN_PROGRESS' THEN 1 ELSE 0 END) as in_progress_count, " +
                "SUM(CASE WHEN cp.status = 'RESOLVED' OR cp.status = 'CLOSED' THEN 1 ELSE 0 END) as resolved_count " +
                "FROM complaints cp " +
                "GROUP BY cp.category " +
                "ORDER BY total_count DESC";
        try (Connection conn = DBConnection.getInstance().getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String cat = rs.getString("category");
                int total = rs.getInt("total_count");
                int open = rs.getInt("open_count");
                int inProg = rs.getInt("in_progress_count");
                int resolved = rs.getInt("resolved_count");
                stats.put(cat, new int[]{total, open, inProg, resolved});
            }
        }
        return stats;
    }

    @Override
    public List<java.util.Map<String, Object>> getTopCustomersByComplaints(int limit) throws SQLException {
        List<java.util.Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT c.customer_id, c.customer_number, CONCAT(c.first_name, ' ', c.last_name) as customer_name, " +
                "c.city, c.mobile_number, " +
                "COUNT(cp.complaint_id) as total_count, " +
                "SUM(CASE WHEN cp.status = 'OPEN' OR cp.status = 'IN_PROGRESS' THEN 1 ELSE 0 END) as pending_count, " +
                "SUM(CASE WHEN cp.status = 'RESOLVED' OR cp.status = 'CLOSED' THEN 1 ELSE 0 END) as resolved_count " +
                "FROM complaints cp " +
                "JOIN customers c ON cp.customer_id = c.customer_id " +
                "GROUP BY c.customer_id, c.customer_number, c.first_name, c.last_name, c.city, c.mobile_number " +
                "ORDER BY total_count DESC " +
                "LIMIT ?";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit > 0 ? limit : 10);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    java.util.Map<String, Object> map = new java.util.HashMap<>();
                    map.put("customerId", rs.getInt("customer_id"));
                    map.put("customerNumber", rs.getString("customer_number"));
                    map.put("customerName", rs.getString("customer_name"));
                    map.put("city", rs.getString("city"));
                    map.put("mobileNumber", rs.getString("mobile_number"));
                    map.put("totalCount", rs.getInt("total_count"));
                    map.put("pendingCount", rs.getInt("pending_count"));
                    map.put("resolvedCount", rs.getInt("resolved_count"));
                    list.add(map);
                }
            }
        }
        return list;
    }

    @Override
    public List<java.util.Map<String, Object>> getCustomersWithMultipleComplaints(int minComplaints) throws SQLException {
        List<java.util.Map<String, Object>> list = new ArrayList<>();
        // Group and aggregate complaint data for the report.
        String sql = "SELECT c.customer_id, c.customer_number, CONCAT(c.first_name, ' ', c.last_name) as customer_name, " +
                "c.city, c.mobile_number, COUNT(cp.complaint_id) as complaint_count " +
                "FROM customers c " +
                "JOIN complaints cp ON c.customer_id = cp.customer_id " +
                "GROUP BY c.customer_id, c.customer_number, c.first_name, c.last_name, c.city, c.mobile_number " +
                "HAVING COUNT(cp.complaint_id) >= ? " +
                "ORDER BY complaint_count DESC";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, minComplaints);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    java.util.Map<String, Object> map = new java.util.HashMap<>();
                    map.put("customerId", rs.getInt("customer_id"));
                    map.put("customerNumber", rs.getString("customer_number"));
                    map.put("customerName", rs.getString("customer_name"));
                    map.put("city", rs.getString("city"));
                    map.put("mobileNumber", rs.getString("mobile_number"));
                    map.put("complaintCount", rs.getInt("complaint_count"));
                    list.add(map);
                }
            }
        }
        return list;
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
        
        try {
            cp.setCustomerName(rs.getString("customer_name"));
        } catch (SQLException ignored) {}
        try {
            cp.setCustomerCity(rs.getString("customer_city"));
        } catch (SQLException ignored) {}
        try {
            cp.setCustomerNumber(rs.getString("customer_number"));
        } catch (SQLException ignored) {}
        try {
            cp.setMobileNumber(rs.getString("mobile_number"));
        } catch (SQLException ignored) {}

        return cp;
    }
}
