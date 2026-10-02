package com.amdocs.telecom.dao.impl;

import com.amdocs.telecom.dao.AdminDAO;
import com.amdocs.telecom.model.Administrator;
import com.amdocs.telecom.util.DBConnection;

import java.sql.*;
import java.util.Optional;

public class AdminDAOImpl implements AdminDAO {

    @Override
    public Optional<Administrator> findByUsername(String username) throws SQLException {
        String sql = "SELECT * FROM administrators WHERE username = ?";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Administrator admin = new Administrator();
                    admin.setAdminId(rs.getInt("admin_id"));
                    admin.setAdminNumber(rs.getString("admin_number"));
                    admin.setUsername(rs.getString("username"));
                    admin.setPasswordHash(rs.getString("password_hash"));
                    admin.setEmail(rs.getString("email"));
                    admin.setFullName(rs.getString("full_name"));
                    admin.setAccountStatus(rs.getString("account_status"));
                    Timestamp ts = rs.getTimestamp("created_at");
                    if (ts != null) admin.setCreatedAt(ts.toLocalDateTime());
                    return Optional.of(admin);
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public boolean updatePassword(int adminId, String newPasswordHash) throws SQLException {
        String sql = "UPDATE administrators SET password_hash = ? WHERE admin_id = ?";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newPasswordHash);
            ps.setInt(2, adminId);
            return ps.executeUpdate() > 0;
        }
    }

    @Override
    public boolean updateAccountStatus(int adminId, String status) throws SQLException {
        String sql = "UPDATE administrators SET account_status = ? WHERE admin_id = ?";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setInt(2, adminId);
            return ps.executeUpdate() > 0;
        }
    }
}
