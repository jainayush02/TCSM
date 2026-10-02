package com.amdocs.telecom.dao;

import com.amdocs.telecom.model.Administrator;
import java.sql.SQLException;
import java.util.Optional;

public interface AdminDAO {
    Optional<Administrator> findByUsername(String username) throws SQLException;
    boolean updatePassword(int adminId, String newPasswordHash) throws SQLException;
    boolean updateAccountStatus(int adminId, String status) throws SQLException;
}
