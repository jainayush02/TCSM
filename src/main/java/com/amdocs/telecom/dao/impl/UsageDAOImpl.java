package com.amdocs.telecom.dao.impl;

import com.amdocs.telecom.dao.UsageDAO;
import com.amdocs.telecom.model.UsageRecord;
import com.amdocs.telecom.model.UsageType;
import com.amdocs.telecom.util.DBConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class UsageDAOImpl implements UsageDAO {

    @Override
    public UsageRecord save(UsageRecord usage) throws SQLException {
        String sql = "INSERT INTO usage_records (subscription_id, usage_date, usage_type, quantity, unit, charge) " +
                "VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setInt(1, usage.getSubscriptionId());
            ps.setTimestamp(2, usage.getUsageDate() != null ? Timestamp.valueOf(usage.getUsageDate()) : new Timestamp(System.currentTimeMillis()));
            ps.setString(3, usage.getUsageType().name());
            ps.setDouble(4, usage.getQuantity());
            ps.setString(5, usage.getUnit());
            ps.setDouble(6, usage.getCharge());

            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    usage.setUsageId(rs.getInt(1));
                }
            }
        }
        return usage;
    }

    @Override
    public int[] saveBatch(List<UsageRecord> usageRecords) throws SQLException {
        String sql = "INSERT INTO usage_records (subscription_id, usage_date, usage_type, quantity, unit, charge) " +
                "VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            for (UsageRecord u : usageRecords) {
                ps.setInt(1, u.getSubscriptionId());
                ps.setTimestamp(2, u.getUsageDate() != null ? Timestamp.valueOf(u.getUsageDate()) : new Timestamp(System.currentTimeMillis()));
                ps.setString(3, u.getUsageType().name());
                ps.setDouble(4, u.getQuantity());
                ps.setString(5, u.getUnit());
                ps.setDouble(6, u.getCharge());
                ps.addBatch();
            }
            return ps.executeBatch();
        }
    }

    @Override
    public List<UsageRecord> findBySubscriptionId(int subscriptionId) throws SQLException {
        List<UsageRecord> list = new ArrayList<>();
        String sql = "SELECT * FROM usage_records WHERE subscription_id = ? ORDER BY usage_date DESC";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, subscriptionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSetToUsage(rs));
                }
            }
        }
        return list;
    }

    @Override
    public List<UsageRecord> findBySubscriptionAndMonth(int subscriptionId, String yearMonth) throws SQLException {
        List<UsageRecord> list = new ArrayList<>();
        // The billing month uses yyyy-MM format.
        String sql = "SELECT * FROM usage_records WHERE subscription_id = ? AND " +
                "SUBSTRING(CAST(usage_date AS CHAR), 1, 7) = ? ORDER BY usage_date ASC";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, subscriptionId);
            ps.setString(2, yearMonth);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSetToUsage(rs));
                }
            }
        }
        return list;
    }

    @Override
    public List<UsageRecord> findAll() throws SQLException {
        List<UsageRecord> list = new ArrayList<>();
        String sql = "SELECT * FROM usage_records ORDER BY usage_date DESC";
        try (Connection conn = DBConnection.getInstance().getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapResultSetToUsage(rs));
            }
        }
        return list;
    }

    @Override
    public Map<UsageType, Double> getUsageSummaryByType(int subscriptionId) throws SQLException {
        Map<UsageType, Double> summary = new EnumMap<>(UsageType.class);
        String sql = "SELECT usage_type, SUM(quantity) as total_qty FROM usage_records " +
                "WHERE subscription_id = ? GROUP BY usage_type";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, subscriptionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    summary.put(UsageType.valueOf(rs.getString("usage_type")), rs.getDouble("total_qty"));
                }
            }
        }
        return summary;
    }

    @Override
    public double getTotalUsageCharge(int subscriptionId, String yearMonth) throws SQLException {
        String sql = "SELECT COALESCE(SUM(charge), 0.0) FROM usage_records " +
                "WHERE subscription_id = ? AND SUBSTRING(CAST(usage_date AS CHAR), 1, 7) = ?";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, subscriptionId);
            ps.setString(2, yearMonth);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getDouble(1);
                }
            }
        }
        return 0.0;
    }

    private UsageRecord mapResultSetToUsage(ResultSet rs) throws SQLException {
        UsageRecord u = new UsageRecord();
        u.setUsageId(rs.getInt("usage_id"));
        u.setSubscriptionId(rs.getInt("subscription_id"));
        Timestamp ts = rs.getTimestamp("usage_date");
        if (ts != null) u.setUsageDate(ts.toLocalDateTime());
        u.setUsageType(UsageType.valueOf(rs.getString("usage_type")));
        u.setQuantity(rs.getDouble("quantity"));
        u.setUnit(rs.getString("unit"));
        u.setCharge(rs.getDouble("charge"));
        return u;
    }
}
