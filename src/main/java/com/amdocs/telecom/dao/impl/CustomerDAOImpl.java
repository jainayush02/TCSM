package com.amdocs.telecom.dao.impl;

import com.amdocs.telecom.dao.CustomerDAO;
import com.amdocs.telecom.model.Customer;
import com.amdocs.telecom.util.DBConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class CustomerDAOImpl implements CustomerDAO {

    @Override
    public Customer save(Customer customer) throws SQLException {
        String sql = "INSERT INTO customers (customer_number, first_name, last_name, date_of_birth, " +
                "email, mobile_number, address, city, country, username, password_hash, account_status) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, customer.getCustomerNumber());
            ps.setString(2, customer.getFirstName());
            ps.setString(3, customer.getLastName());
            ps.setDate(4, Date.valueOf(customer.getDateOfBirth()));
            ps.setString(5, customer.getEmail());
            ps.setString(6, customer.getMobileNumber());
            ps.setString(7, customer.getAddress());
            ps.setString(8, customer.getCity());
            ps.setString(9, customer.getCountry());
            ps.setString(10, customer.getUsername());
            ps.setString(11, customer.getPasswordHash());
            ps.setString(12, customer.getAccountStatus() != null ? customer.getAccountStatus() : "ACTIVE");

            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    customer.setCustomerId(rs.getInt(1));
                }
            }
        }
        return customer;
    }

    @Override
    public Optional<Customer> findById(int customerId) throws SQLException {
        String sql = "SELECT * FROM customers WHERE customer_id = ?";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToCustomer(rs));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<Customer> findByUsername(String username) throws SQLException {
        String sql = "SELECT * FROM customers WHERE username = ?";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToCustomer(rs));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<Customer> findByEmail(String email) throws SQLException {
        String sql = "SELECT * FROM customers WHERE email = ?";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToCustomer(rs));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<Customer> findByUsernameOrEmail(String identifier) throws SQLException {
        String sql = "SELECT * FROM customers WHERE username = ? OR email = ?";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, identifier);
            ps.setString(2, identifier);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToCustomer(rs));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<Customer> findByMobileNumber(String mobileNumber) throws SQLException {
        String sql = "SELECT * FROM customers WHERE mobile_number = ?";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, mobileNumber);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToCustomer(rs));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public List<Customer> findAll() throws SQLException {
        List<Customer> list = new ArrayList<>();
        String sql = "SELECT * FROM customers ORDER BY customer_id ASC";
        try (Connection conn = DBConnection.getInstance().getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapResultSetToCustomer(rs));
            }
        }
        return list;
    }

    @Override
    public boolean update(Customer customer) throws SQLException {
        String sql = "UPDATE customers SET first_name = ?, last_name = ?, date_of_birth = ?, " +
                "address = ?, city = ?, country = ?, updated_at=CURRENT_TIMESTAMP WHERE customer_id = ?";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, customer.getFirstName());
            ps.setString(2, customer.getLastName());
            ps.setDate(3, Date.valueOf(customer.getDateOfBirth()));
            ps.setString(4, customer.getAddress());
            ps.setString(5, customer.getCity());
            ps.setString(6, customer.getCountry());
            ps.setInt(7, customer.getCustomerId());
            return ps.executeUpdate() > 0;
        }
    }

    @Override
    public boolean updatePassword(int customerId, String newPasswordHash) throws SQLException {
        String sql = "UPDATE customers SET password_hash = ? WHERE customer_id = ?";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newPasswordHash);
            ps.setInt(2, customerId);
            return ps.executeUpdate() > 0;
        }
    }

    @Override
    public boolean updateAccountStatus(int customerId, String status) throws SQLException {
        String sql = "UPDATE customers SET account_status = ? WHERE customer_id = ?";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setInt(2, customerId);
            return ps.executeUpdate() > 0;
        }
    }

    @Override
    public void logLoginAttempt(String username, String role, String ip, String status) throws SQLException {
        String sql = "INSERT INTO login_history (username, user_role, ip_address, status) VALUES (?, ?, ?, ?)";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, role);
            ps.setString(3, ip);
            ps.setString(4, status);
            ps.executeUpdate();
        }
    }

    @Override
    public int getRecentFailedLoginAttempts(String username, int withinMinutes) throws SQLException {
        return getRecentFailedLoginAttempts(username, "CUSTOMER", withinMinutes);
    }

    @Override
    public int getRecentFailedLoginAttempts(String username, String role, int withinMinutes) throws SQLException {
        String sql = "SELECT COUNT(*) FROM login_history WHERE username=? AND user_role=? AND status='FAILED' " +
                "AND login_timestamp >= ? AND login_id > COALESCE((SELECT MAX(login_id) FROM login_history " +
                "WHERE username=? AND user_role=? AND status IN ('SUCCESS','RESET')),0)";
        try (Connection c=DBConnection.getInstance().getConnection(); PreparedStatement p=c.prepareStatement(sql)) {
            p.setString(1,username); p.setString(2,role);
            p.setTimestamp(3,Timestamp.valueOf(java.time.LocalDateTime.now().minusMinutes(withinMinutes)));
            p.setString(4,username); p.setString(5,role);
            try(ResultSet r=p.executeQuery()) { r.next(); return r.getInt(1); }
        }
    }

    @Override
    public Optional<String> getLastLoginTimestamp(String username) throws SQLException {
        return getLastLoginTimestamp(username,"CUSTOMER");
    }

    @Override
    public Optional<String> getLastLoginTimestamp(String username, String role) throws SQLException {
        try(Connection c=DBConnection.getInstance().getConnection(); PreparedStatement p=c.prepareStatement(
                "SELECT login_timestamp FROM login_history WHERE username=? AND user_role=? AND status='SUCCESS' ORDER BY login_id DESC LIMIT 1")) {
            p.setString(1,username); p.setString(2,role);
            try(ResultSet r=p.executeQuery()) { return r.next()?Optional.of(r.getTimestamp(1).toString()):Optional.empty(); }
        }
    }

    private Customer mapResultSetToCustomer(ResultSet rs) throws SQLException {
        Customer customer = new Customer();
        customer.setCustomerId(rs.getInt("customer_id"));
        customer.setCustomerNumber(rs.getString("customer_number"));
        customer.setFirstName(rs.getString("first_name"));
        customer.setLastName(rs.getString("last_name"));

        Date dob = rs.getDate("date_of_birth");
        if (dob != null) customer.setDateOfBirth(dob.toLocalDate());

        customer.setEmail(rs.getString("email"));
        customer.setMobileNumber(rs.getString("mobile_number"));
        customer.setAddress(rs.getString("address"));
        customer.setCity(rs.getString("city"));
        customer.setCountry(rs.getString("country"));
        customer.setUsername(rs.getString("username"));
        customer.setPasswordHash(rs.getString("password_hash"));

        Timestamp reg = rs.getTimestamp("registration_date");
        if (reg != null) customer.setRegistrationDate(reg.toLocalDateTime());

        customer.setAccountStatus(rs.getString("account_status"));
        return customer;
    }
}
