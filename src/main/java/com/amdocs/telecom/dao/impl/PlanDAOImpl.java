package com.amdocs.telecom.dao.impl;

import com.amdocs.telecom.dao.PlanDAO;
import com.amdocs.telecom.model.SubscriptionType;
import com.amdocs.telecom.model.TelecomPlan;
import com.amdocs.telecom.util.DBConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class PlanDAOImpl implements PlanDAO {

    @Override
    public TelecomPlan save(TelecomPlan plan) throws SQLException {
        String sql = "INSERT INTO telecom_plans (plan_code, plan_name, plan_type, monthly_rental, " +
                "data_allowance_gb, voice_minutes, sms_allowance, validity_days, international_roaming, status) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, plan.getPlanCode());
            ps.setString(2, plan.getPlanName());
            ps.setString(3, plan.getPlanType().name());
            ps.setDouble(4, plan.getMonthlyRental());
            ps.setInt(5, plan.getDataAllowanceGB());
            ps.setInt(6, plan.getVoiceMinutes());
            ps.setInt(7, plan.getSmsAllowance());
            ps.setInt(8, plan.getValidityDays());
            ps.setBoolean(9, plan.isInternationalRoaming());
            ps.setString(10, plan.getStatus() != null ? plan.getStatus() : "ACTIVE");

            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    plan.setPlanId(rs.getInt(1));
                }
            }
        }
        return plan;
    }

    @Override
    public Optional<TelecomPlan> findById(int planId) throws SQLException {
        String sql = "SELECT * FROM telecom_plans WHERE plan_id = ?";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, planId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToPlan(rs));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<TelecomPlan> findByCode(String planCode) throws SQLException {
        String sql = "SELECT * FROM telecom_plans WHERE plan_code = ?";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, planCode);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToPlan(rs));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public List<TelecomPlan> findAll() throws SQLException {
        List<TelecomPlan> list = new ArrayList<>();
        String sql = "SELECT * FROM telecom_plans ORDER BY monthly_rental ASC";
        try (Connection conn = DBConnection.getInstance().getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapResultSetToPlan(rs));
            }
        }
        return list;
    }

    @Override
    public List<TelecomPlan> findAllActive() throws SQLException {
        List<TelecomPlan> list = new ArrayList<>();
        String sql = "SELECT * FROM telecom_plans WHERE status = 'ACTIVE' ORDER BY monthly_rental ASC";
        try (Connection conn = DBConnection.getInstance().getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapResultSetToPlan(rs));
            }
        }
        return list;
    }

    @Override
    public boolean update(TelecomPlan plan) throws SQLException {
        String sql = "UPDATE telecom_plans SET plan_name = ?, plan_type = ?, monthly_rental = ?, " +
                "data_allowance_gb = ?, voice_minutes = ?, sms_allowance = ?, validity_days = ?, " +
                "international_roaming = ?, status = ?, updated_at = CURRENT_TIMESTAMP WHERE plan_id = ?";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, plan.getPlanName());
            ps.setString(2, plan.getPlanType().name());
            ps.setDouble(3, plan.getMonthlyRental());
            ps.setInt(4, plan.getDataAllowanceGB());
            ps.setInt(5, plan.getVoiceMinutes());
            ps.setInt(6, plan.getSmsAllowance());
            ps.setInt(7, plan.getValidityDays());
            ps.setBoolean(8, plan.isInternationalRoaming());
            ps.setString(9, plan.getStatus());
            ps.setInt(10, plan.getPlanId());
            return ps.executeUpdate() > 0;
        }
    }

    @Override
    public boolean updateStatus(int planId, String status) throws SQLException {
        String sql = "UPDATE telecom_plans SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE plan_id = ?";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setInt(2, planId);
            return ps.executeUpdate() > 0;
        }
    }

    @Override
    public boolean delete(int planId) throws SQLException {
        String sql = "DELETE FROM telecom_plans WHERE plan_id = ?";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, planId);
            return ps.executeUpdate() > 0;
        }
    }

    private TelecomPlan mapResultSetToPlan(ResultSet rs) throws SQLException {
        TelecomPlan plan = new TelecomPlan();
        plan.setPlanId(rs.getInt("plan_id"));
        plan.setPlanCode(rs.getString("plan_code"));
        plan.setPlanName(rs.getString("plan_name"));
        plan.setPlanType(SubscriptionType.valueOf(rs.getString("plan_type")));
        plan.setMonthlyRental(rs.getDouble("monthly_rental"));
        plan.setDataAllowanceGB(rs.getInt("data_allowance_gb"));
        plan.setVoiceMinutes(rs.getInt("voice_minutes"));
        plan.setSmsAllowance(rs.getInt("sms_allowance"));
        plan.setValidityDays(rs.getInt("validity_days"));
        plan.setInternationalRoaming(rs.getBoolean("international_roaming"));
        plan.setStatus(rs.getString("status"));

        Timestamp created = rs.getTimestamp("created_at");
        if (created != null) plan.setCreatedAt(created.toLocalDateTime());
        Timestamp updated = rs.getTimestamp("updated_at");
        if (updated != null) plan.setUpdatedAt(updated.toLocalDateTime());

        return plan;
    }
}
