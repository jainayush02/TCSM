package com.amdocs.telecom.service.impl;

import com.amdocs.telecom.dao.CustomerDAO;
import com.amdocs.telecom.dao.impl.CustomerDAOImpl;
import com.amdocs.telecom.dao.AuditAndNotificationDAO;
import com.amdocs.telecom.factory.DAOFactory;
import com.amdocs.telecom.exception.AuthenticationException;
import com.amdocs.telecom.model.AuditLog;
import com.amdocs.telecom.model.Customer;
import com.amdocs.telecom.security.CaptchaGenerator;
import com.amdocs.telecom.security.OTPService;
import com.amdocs.telecom.security.PasswordUtil;
import com.amdocs.telecom.service.AuthenticationService;

import java.sql.SQLException;
import java.util.Optional;

public class AuthenticationServiceImpl implements AuthenticationService {

    private final CustomerDAO customerDAO;
    private final AuditAndNotificationDAO auditDAO;

    public AuthenticationServiceImpl() {
        this.customerDAO = new CustomerDAOImpl();
        this.auditDAO = DAOFactory.getAuditAndNotificationDAO();
    }

    @Override
    public Customer login(String username, String password, String expectedCaptcha, String inputCaptcha) throws AuthenticationException {
        try {
            if (!CaptchaGenerator.validateCaptcha(inputCaptcha, expectedCaptcha)) {
                throw new AuthenticationException("Invalid CAPTCHA.");
            }

            Optional<Customer> optionalCustomer = customerDAO.findByUsernameOrEmail(username);
            if (!optionalCustomer.isPresent()) {
                customerDAO.logLoginAttempt(username, "CUSTOMER", "127.0.0.1", "FAILED");
                logActivity(username, "LOGIN_FAILED", "Unknown customer login attempt");
                throw new AuthenticationException("Invalid credentials.");
            }

            Customer customer = optionalCustomer.get();
            username = customer.getUsername();

            if ("LOCKED".equals(customer.getAccountStatus()) && com.amdocs.telecom.security.LoginSecurity.unlockIfExpired(username,"CUSTOMER"))
                customer.setAccountStatus("ACTIVE");
            if ("LOCKED".equals(customer.getAccountStatus())) {
                logActivity(username, "LOGIN_BLOCKED", "Customer account is locked");
                throw new AuthenticationException("Account is temporarily locked due to multiple failed attempts. Try again in 30 minutes or reset your password.");
            }

            if (!"ACTIVE".equals(customer.getAccountStatus())) throw new AuthenticationException("This account is suspended or inactive. Contact an administrator.");

            if (!PasswordUtil.verifyPassword(password, customer.getPasswordHash())) {
                customerDAO.logLoginAttempt(username, "CUSTOMER", "127.0.0.1", "FAILED");
                logActivity(username, "LOGIN_FAILED", "Invalid customer password");
                
                int failedAttempts = customerDAO.getRecentFailedLoginAttempts(username, 30); // check last 30 mins
                if (failedAttempts >= 3) {
                    customerDAO.updateAccountStatus(customer.getCustomerId(), "LOCKED");
                    com.amdocs.telecom.security.LoginSecurity.lock(username,"CUSTOMER");
                    throw new AuthenticationException("Maximum 3 failed attempts reached. Account locked.");
                }
                throw new AuthenticationException("Invalid credentials. Failed attempts: " + failedAttempts + "/3");
            }

            customer.setPreviousLogin(customerDAO.getLastLoginTimestamp(username).orElse(null));
            customerDAO.logLoginAttempt(username, "CUSTOMER", "127.0.0.1", "SUCCESS");
            com.amdocs.telecom.security.LoginSecurity.clear(username,"CUSTOMER");
            logActivity(username, "LOGIN_SUCCESS", "Customer login successful");
            
            return customer;

        } catch (SQLException e) {
            throw new AuthenticationException("Database error during login: " + e.getMessage());
        }
    }

    @Override
    public String initiatePasswordRecovery(String username) throws AuthenticationException {
        try {
            Optional<Customer> opt = customerDAO.findByUsername(username);
            if (!opt.isPresent()) {
                throw new AuthenticationException("User not found.");
            }
            // The console displays the OTP; production would deliver it out of band.
            return OTPService.generateOtp("CUSTOMER:"+username);
        } catch (SQLException e) {
            throw new AuthenticationException("Database error: " + e.getMessage());
        }
    }

    @Override
    public boolean completePasswordRecovery(String username, String otp, String newPassword) throws AuthenticationException {
        try {
            if (!PasswordUtil.isValidPassword(newPassword)) throw new AuthenticationException("Password does not meet complexity requirements.");
            if (!OTPService.verifyOtp("CUSTOMER:"+username, otp)) {
                throw new AuthenticationException("Invalid or expired OTP.");
            }

            if (!PasswordUtil.isValidPassword(newPassword)) {
                throw new AuthenticationException("Password does not meet complexity requirements.");
            }

            Optional<Customer> opt = customerDAO.findByUsername(username);
            if (opt.isPresent()) {
                Customer customer = opt.get();
                String hashed = PasswordUtil.hashPassword(newPassword);
                try {
                    com.amdocs.telecom.util.Transactions.run(() -> {
                        customerDAO.updatePassword(customer.getCustomerId(), hashed);
                        if ("LOCKED".equals(customer.getAccountStatus())) customerDAO.updateAccountStatus(customer.getCustomerId(), "ACTIVE");
                        customerDAO.logLoginAttempt(username,"CUSTOMER","127.0.0.1","RESET");
                        com.amdocs.telecom.security.LoginSecurity.clear(username,"CUSTOMER");
                        return null;
                    });
                } catch (com.amdocs.telecom.exception.TelecomException e) { throw new AuthenticationException(e.getMessage()); }
                return true;
            }
            return false;
        } catch (SQLException e) {
            throw new AuthenticationException("Database error: " + e.getMessage());
        }
    }

    @Override
    public void logout(String username) {
        try { customerDAO.logLoginAttempt(username,"CUSTOMER","127.0.0.1","LOGOUT"); }
        catch (SQLException e) { throw new IllegalStateException("Could not record logout.",e); }
    }

    private void logActivity(String username, String action, String details) {
        try {
            AuditLog audit = new AuditLog();
            audit.setEntityName("AUTHENTICATION");
            audit.setEntityId(username);
            audit.setAction(action);
            audit.setDetails(details);
            audit.setPerformedBy(username);
            auditDAO.logAudit(audit);
        } catch (SQLException ignored) {
        }
    }
}
