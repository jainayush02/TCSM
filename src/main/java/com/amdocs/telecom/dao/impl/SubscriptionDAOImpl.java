package com.amdocs.telecom.dao.impl;

import com.amdocs.telecom.dao.SubscriptionDAO;
import com.amdocs.telecom.model.MobileSubscription;
import com.amdocs.telecom.model.SIMCard;
import com.amdocs.telecom.model.SimType;
import com.amdocs.telecom.model.SubscriptionHistory;
import com.amdocs.telecom.model.SubscriptionType;
import com.amdocs.telecom.util.DBConnection;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SubscriptionDAOImpl implements SubscriptionDAO {

    @Override
    public MobileSubscription saveSubscription(MobileSubscription sub) throws SQLException {
        String sql = "INSERT INTO mobile_subscriptions (subscription_number, customer_id, plan_id, " +
                "mobile_number, sim_id, activation_date, subscription_type, status) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, sub.getSubscriptionNumber());
            ps.setInt(2, sub.getCustomerId());
            ps.setInt(3, sub.getPlanId());
            ps.setString(4, sub.getMobileNumber());
            ps.setInt(5, sub.getSimId());
            ps.setDate(6, Date.valueOf(sub.getActivationDate() != null ? sub.getActivationDate() : LocalDate.now()));
            ps.setString(7, sub.getSubscriptionType().name());
            ps.setString(8, sub.getStatus() != null ? sub.getStatus() : "ACTIVE");

            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    sub.setSubscriptionId(rs.getInt(1));
                }
            }
        }
        return sub;
    }

    @Override
    public Optional<MobileSubscription> findById(int subscriptionId) throws SQLException {
        String sql = "SELECT ms.*, p.plan_code, p.plan_name, p.monthly_rental, s.sim_number, s.sim_type " +
                "FROM mobile_subscriptions ms " +
                "JOIN telecom_plans p ON ms.plan_id = p.plan_id " +
                "JOIN sim_cards s ON ms.sim_id = s.sim_id " +
                "WHERE ms.subscription_id = ?";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, subscriptionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToSub(rs));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<MobileSubscription> findByNumber(String subscriptionNumber) throws SQLException {
        String sql = "SELECT ms.*, p.plan_code, p.plan_name, p.monthly_rental, s.sim_number, s.sim_type " +
                "FROM mobile_subscriptions ms " +
                "JOIN telecom_plans p ON ms.plan_id = p.plan_id " +
                "JOIN sim_cards s ON ms.sim_id = s.sim_id " +
                "WHERE ms.subscription_number = ?";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, subscriptionNumber);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToSub(rs));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<MobileSubscription> findByMobileNumber(String mobileNumber) throws SQLException {
        String sql = "SELECT ms.*, p.plan_code, p.plan_name, p.monthly_rental, s.sim_number, s.sim_type " +
                "FROM mobile_subscriptions ms " +
                "JOIN telecom_plans p ON ms.plan_id = p.plan_id " +
                "JOIN sim_cards s ON ms.sim_id = s.sim_id " +
                "WHERE ms.mobile_number = ?";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, mobileNumber);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToSub(rs));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public List<MobileSubscription> findByCustomerId(int customerId) throws SQLException {
        List<MobileSubscription> list = new ArrayList<>();
        String sql = "SELECT ms.*, p.plan_code, p.plan_name, p.monthly_rental, s.sim_number, s.sim_type " +
                "FROM mobile_subscriptions ms " +
                "JOIN telecom_plans p ON ms.plan_id = p.plan_id " +
                "JOIN sim_cards s ON ms.sim_id = s.sim_id " +
                "WHERE ms.customer_id = ? ORDER BY ms.subscription_id DESC";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSetToSub(rs));
                }
            }
        }
        return list;
    }

    @Override
    public List<MobileSubscription> findAll() throws SQLException {
        List<MobileSubscription> list = new ArrayList<>();
        String sql = "SELECT ms.*, p.plan_code, p.plan_name, p.monthly_rental, s.sim_number, s.sim_type " +
                "FROM mobile_subscriptions ms " +
                "JOIN telecom_plans p ON ms.plan_id = p.plan_id " +
                "JOIN sim_cards s ON ms.sim_id = s.sim_id " +
                "ORDER BY ms.subscription_id DESC";
        try (Connection conn = DBConnection.getInstance().getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapResultSetToSub(rs));
            }
        }
        return list;
    }

    @Override
    public boolean updateStatus(int subscriptionId, String status) throws SQLException {
        String sql = "UPDATE mobile_subscriptions SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE subscription_id = ?";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setInt(2, subscriptionId);
            return ps.executeUpdate() > 0;
        }
    }

    @Override
    public boolean changePlan(int subscriptionId, int newPlanId, String reason, String changedBy) throws SQLException {
        String getOldPlanSql = "SELECT plan_id FROM mobile_subscriptions WHERE subscription_id = ?";
        String updateSubSql = "UPDATE mobile_subscriptions SET plan_id = ?, updated_at = CURRENT_TIMESTAMP WHERE subscription_id = ?";
        String insertHistorySql = "INSERT INTO subscription_history (subscription_id, old_plan_id, new_plan_id, change_reason, changed_by) " +
                "VALUES (?, ?, ?, ?, ?)";

        Connection conn = null;
        try {
            conn = DBConnection.getInstance().getConnection();
            conn.setAutoCommit(false); // Begin JDBC Transaction

            int oldPlanId;
            try (PreparedStatement psOld = conn.prepareStatement(getOldPlanSql)) {
                psOld.setInt(1, subscriptionId);
                try (ResultSet rs = psOld.executeQuery()) {
                    if (rs.next()) {
                        oldPlanId = rs.getInt("plan_id");
                    } else {
                        conn.rollback();
                        return false;
                    }
                }
            }

            try (PreparedStatement psUpdate = conn.prepareStatement(updateSubSql)) {
                psUpdate.setInt(1, newPlanId);
                psUpdate.setInt(2, subscriptionId);
                psUpdate.executeUpdate();
            }

            try (PreparedStatement psHist = conn.prepareStatement(insertHistorySql)) {
                psHist.setInt(1, subscriptionId);
                psHist.setInt(2, oldPlanId);
                psHist.setInt(3, newPlanId);
                psHist.setString(4, reason);
                psHist.setString(5, changedBy);
                psHist.executeUpdate();
            }

            conn.commit(); // Transaction Success
            return true;
        } catch (SQLException e) {
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException ignored) {}
            }
            throw e;
        } finally {
            if (conn != null) {
                try { conn.setAutoCommit(true); conn.close(); } catch (SQLException ignored) {}
            }
        }
    }

    @Override
    public List<SubscriptionHistory> getHistory(int subscriptionId) throws SQLException {
        List<SubscriptionHistory> list = new ArrayList<>();
        String sql = "SELECT sh.*, pOld.plan_name AS old_plan_name, pNew.plan_name AS new_plan_name " +
                "FROM subscription_history sh " +
                "LEFT JOIN telecom_plans pOld ON sh.old_plan_id = pOld.plan_id " +
                "JOIN telecom_plans pNew ON sh.new_plan_id = pNew.plan_id " +
                "WHERE sh.subscription_id = ? ORDER BY sh.change_date DESC";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, subscriptionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    SubscriptionHistory h = new SubscriptionHistory();
                    h.setHistoryId(rs.getInt("history_id"));
                    h.setSubscriptionId(rs.getInt("subscription_id"));
                    int oldId = rs.getInt("old_plan_id");
                    if (!rs.wasNull()) h.setOldPlanId(oldId);
                    h.setNewPlanId(rs.getInt("new_plan_id"));
                    Timestamp cd = rs.getTimestamp("change_date");
                    if (cd != null) h.setChangeDate(cd.toLocalDateTime());
                    h.setChangeReason(rs.getString("change_reason"));
                    h.setChangedBy(rs.getString("changed_by"));
                    h.setOldPlanName(rs.getString("old_plan_name"));
                    h.setNewPlanName(rs.getString("new_plan_name"));
                    list.add(h);
                }
            }
        }
        return list;
    }

    @Override
    public SIMCard saveSIM(SIMCard sim) throws SQLException {
        String sql = "INSERT INTO sim_cards (sim_number, sim_type, imsi, status) VALUES (?, ?, ?, ?)";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, sim.getSimNumber());
            ps.setString(2, sim.getSimType().name());
            ps.setString(3, sim.getImsi());
            ps.setString(4, sim.getStatus() != null ? sim.getStatus() : "ACTIVE");
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) sim.setSimId(rs.getInt(1));
            }
        }
        return sim;
    }

    @Override
    public Optional<SIMCard> findAvailableSIM(String simType) throws SQLException {
        String sql = "SELECT * FROM sim_cards WHERE sim_type = ? AND status = 'ACTIVE' " +
                "AND sim_id NOT IN (SELECT sim_id FROM mobile_subscriptions) LIMIT 1";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, simType);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    SIMCard sim = new SIMCard();
                    sim.setSimId(rs.getInt("sim_id"));
                    sim.setSimNumber(rs.getString("sim_number"));
                    sim.setSimType(SimType.valueOf(rs.getString("sim_type")));
                    sim.setImsi(rs.getString("imsi"));
                    sim.setStatus(rs.getString("status"));
                    return Optional.of(sim);
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public List<SIMCard> findAllSIMs() throws SQLException {
        List<SIMCard> sims = new ArrayList<>();
        String sql = "SELECT * FROM sim_cards ORDER BY sim_id";
        try (Connection conn = DBConnection.getInstance().getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                SIMCard sim = new SIMCard();
                sim.setSimId(rs.getInt("sim_id"));
                sim.setSimNumber(rs.getString("sim_number"));
                sim.setSimType(SimType.valueOf(rs.getString("sim_type")));
                sim.setImsi(rs.getString("imsi"));
                sim.setStatus(rs.getString("status"));
                sims.add(sim);
            }
        }
        return sims;
    }

    @Override
    public boolean updateSIMStatus(int simId, String status) throws SQLException {
        String sql = "UPDATE sim_cards SET status = ? WHERE sim_id = ?";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setInt(2, simId);
            return ps.executeUpdate() > 0;
        }
    }

    private MobileSubscription mapResultSetToSub(ResultSet rs) throws SQLException {
        MobileSubscription sub = new MobileSubscription();
        sub.setSubscriptionId(rs.getInt("subscription_id"));
        sub.setSubscriptionNumber(rs.getString("subscription_number"));
        sub.setCustomerId(rs.getInt("customer_id"));
        sub.setPlanId(rs.getInt("plan_id"));
        sub.setMobileNumber(rs.getString("mobile_number"));
        sub.setSimId(rs.getInt("sim_id"));

        Date act = rs.getDate("activation_date");
        if (act != null) sub.setActivationDate(act.toLocalDate());

        sub.setSubscriptionType(SubscriptionType.valueOf(rs.getString("subscription_type")));
        sub.setStatus(rs.getString("status"));

        sub.setPlanCode(rs.getString("plan_code"));
        sub.setPlanName(rs.getString("plan_name"));
        sub.setMonthlyRental(rs.getDouble("monthly_rental"));
        sub.setSimNumber(rs.getString("sim_number"));
        sub.setSimType(SimType.valueOf(rs.getString("sim_type")));

        return sub;
    }
}
