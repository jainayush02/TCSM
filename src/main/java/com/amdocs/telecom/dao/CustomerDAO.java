package com.amdocs.telecom.dao;

import com.amdocs.telecom.model.Customer;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public interface CustomerDAO {
    Customer save(Customer customer) throws SQLException;
    Optional<Customer> findById(int customerId) throws SQLException;
    Optional<Customer> findByUsername(String username) throws SQLException;
    Optional<Customer> findByEmail(String email) throws SQLException;
    Optional<Customer> findByUsernameOrEmail(String identifier) throws SQLException;
    Optional<Customer> findByMobileNumber(String mobileNumber) throws SQLException;
    List<Customer> findAll() throws SQLException;
    boolean update(Customer customer) throws SQLException;
    boolean updatePassword(int customerId, String newPasswordHash) throws SQLException;
    boolean updateAccountStatus(int customerId, String status) throws SQLException;
    void logLoginAttempt(String username, String role, String ip, String status) throws SQLException;
    int getRecentFailedLoginAttempts(String username, String role, int withinMinutes) throws SQLException;
    Optional<String> getLastLoginTimestamp(String username, String role) throws SQLException;
    int getRecentFailedLoginAttempts(String username, int withinMinutes) throws SQLException;
    Optional<String> getLastLoginTimestamp(String username) throws SQLException;
}
