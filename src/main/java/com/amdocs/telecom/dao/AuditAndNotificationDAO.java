package com.amdocs.telecom.dao;

import com.amdocs.telecom.model.AuditLog;
import com.amdocs.telecom.model.Notification;
import java.sql.SQLException;
import java.util.List;

public interface AuditAndNotificationDAO {
    void logAudit(AuditLog log) throws SQLException;
    void logAudit(java.sql.Connection conn, AuditLog log) throws SQLException;
    List<AuditLog> getAuditLogs() throws SQLException;
    void createNotification(Notification notif) throws SQLException;
    List<Notification> getNotificationsForCustomer(int customerId) throws SQLException;
    boolean markNotificationRead(int notificationId) throws SQLException;
}
