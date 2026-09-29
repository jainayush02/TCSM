package com.amdocs.telecom.dao.impl;

import com.amdocs.telecom.dao.AuditAndNotificationDAO;
import com.amdocs.telecom.model.AuditLog;
import com.amdocs.telecom.model.Notification;
import com.amdocs.telecom.util.DBConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class AuditAndNotificationDAOImpl implements AuditAndNotificationDAO {

    @Override
    public void logAudit(AuditLog log) throws SQLException {
        try (Connection conn = DBConnection.getInstance().getConnection()) {
            logAudit(conn, log);
        }
    }

    @Override
    public void logAudit(Connection conn, AuditLog log) throws SQLException {
        String sql = "INSERT INTO audit_logs (entity_name, entity_id, action, details, performed_by) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, log.getEntityName());
            ps.setString(2, log.getEntityId());
            ps.setString(3, log.getAction());
            ps.setString(4, log.getDetails());
            ps.setString(5, log.getPerformedBy());
            ps.executeUpdate();
        }
    }

    @Override
    public List<AuditLog> getAuditLogs() throws SQLException {
        List<AuditLog> list = new ArrayList<>();
        String sql = "SELECT * FROM audit_logs ORDER BY performed_at DESC LIMIT 100";
        try (Connection conn = DBConnection.getInstance().getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                AuditLog a = new AuditLog();
                a.setAuditId(rs.getInt("audit_id"));
                a.setEntityName(rs.getString("entity_name"));
                a.setEntityId(rs.getString("entity_id"));
                a.setAction(rs.getString("action"));
                a.setDetails(rs.getString("details"));
                a.setPerformedBy(rs.getString("performed_by"));
                Timestamp ts = rs.getTimestamp("performed_at");
                if (ts != null) a.setPerformedAt(ts.toLocalDateTime());
                list.add(a);
            }
        }
        return list;
    }

    @Override
    public void createNotification(Notification notif) throws SQLException {
        String sql = "INSERT INTO notifications (customer_id, title, message, status) VALUES (?, ?, ?, ?)";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, notif.getCustomerId());
            ps.setString(2, notif.getTitle());
            ps.setString(3, notif.getMessage());
            ps.setString(4, notif.getStatus() != null ? notif.getStatus() : "UNREAD");
            ps.executeUpdate();
        }
    }

    @Override
    public List<Notification> getNotificationsForCustomer(int customerId) throws SQLException {
        List<Notification> list = new ArrayList<>();
        String sql = "SELECT * FROM notifications WHERE customer_id = ? ORDER BY created_at DESC";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Notification n = new Notification();
                    n.setNotificationId(rs.getInt("notification_id"));
                    n.setCustomerId(rs.getInt("customer_id"));
                    n.setTitle(rs.getString("title"));
                    n.setMessage(rs.getString("message"));
                    Timestamp ts = rs.getTimestamp("created_at");
                    if (ts != null) n.setCreatedAt(ts.toLocalDateTime());
                    n.setStatus(rs.getString("status"));
                    list.add(n);
                }
            }
        }
        return list;
    }

    @Override
    public boolean markNotificationRead(int notificationId) throws SQLException {
        String sql = "UPDATE notifications SET status = 'READ' WHERE notification_id = ?";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, notificationId);
            return ps.executeUpdate() > 0;
        }
    }
}
