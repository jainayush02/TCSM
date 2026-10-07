package com.amdocs.telecom.security;

import com.amdocs.telecom.util.DBConnection;
import java.sql.*;
import java.time.LocalDateTime;

public final class LoginSecurity {
    private LoginSecurity() {}

    public static boolean unlockIfExpired(String username, String role) throws SQLException {
        try (Connection c = DBConnection.getInstance().getConnection();
             PreparedStatement p = c.prepareStatement("SELECT locked_until FROM account_security WHERE username=? AND user_role=?")) {
            p.setString(1, username); p.setString(2, role);
            try (ResultSet r = p.executeQuery()) {
                if (!r.next() || r.getTimestamp(1) == null || r.getTimestamp(1).toLocalDateTime().isAfter(LocalDateTime.now())) return false;
            }
            String table = role.equals("ADMIN") ? "administrators" : "customers";
            try (PreparedStatement update = c.prepareStatement("UPDATE " + table + " SET account_status='ACTIVE' WHERE username=? AND account_status='LOCKED'")) {
                update.setString(1, username); update.executeUpdate();
            }
            clear(username, role);
            return true;
        }
    }

    public static void lock(String username, String role) throws SQLException {
        try (Connection c = DBConnection.getInstance().getConnection()) {
            try (PreparedStatement p = c.prepareStatement("DELETE FROM account_security WHERE username=? AND user_role=?")) {
                p.setString(1, username); p.setString(2, role); p.executeUpdate();
            }
            try (PreparedStatement p = c.prepareStatement("INSERT INTO account_security(username,user_role,locked_until) VALUES(?,?,?)")) {
                p.setString(1, username); p.setString(2, role); p.setTimestamp(3, Timestamp.valueOf(LocalDateTime.now().plusMinutes(30))); p.executeUpdate();
            }
        }
    }

    public static void clear(String username, String role) throws SQLException {
        try (Connection c = DBConnection.getInstance().getConnection();
             PreparedStatement p = c.prepareStatement("DELETE FROM account_security WHERE username=? AND user_role=?")) {
            p.setString(1, username); p.setString(2, role); p.executeUpdate();
        }
    }
}
