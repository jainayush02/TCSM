package com.amdocs.telecom.service.impl;

import com.amdocs.telecom.dao.CustomerDAO;
import com.amdocs.telecom.dao.impl.CustomerDAOImpl;
import com.amdocs.telecom.dao.AuditAndNotificationDAO;
import com.amdocs.telecom.factory.DAOFactory;
import com.amdocs.telecom.model.AuditLog;
import com.amdocs.telecom.dto.CustomerRegistrationDTO;
import com.amdocs.telecom.exception.ValidationException;
import com.amdocs.telecom.model.Customer;
import com.amdocs.telecom.security.PasswordUtil;
import com.amdocs.telecom.service.CustomerService;
import com.amdocs.telecom.validation.ValidationUtil;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CustomerServiceImpl implements CustomerService {
    private static final Logger LOGGER = Logger.getLogger(CustomerServiceImpl.class.getName());
    private final CustomerDAO customerDAO;
    private final AuditAndNotificationDAO auditDAO;

    public CustomerServiceImpl() {
        this.customerDAO = new CustomerDAOImpl();
        this.auditDAO = DAOFactory.getAuditAndNotificationDAO();
    }

    @Override
    public Customer registerCustomer(CustomerRegistrationDTO dto) throws ValidationException {
        if (dto == null) throw new ValidationException("Registration details are required.");
        if (!ValidationUtil.isNotEmpty(dto.getUsername()) || !ValidationUtil.isNotEmpty(dto.getAddress())
                || !ValidationUtil.isNotEmpty(dto.getCity()) || !ValidationUtil.isNotEmpty(dto.getCountry()))
            throw new ValidationException("Username, address, city and country are required.");
        dto.setUsername(dto.getUsername().trim());
        dto.setEmail(dto.getEmail() == null ? null : dto.getEmail().trim().toLowerCase(java.util.Locale.ROOT));
        dto.setMobileNumber(dto.getMobileNumber() == null ? null : dto.getMobileNumber().trim());
        if (!dto.getUsername().matches("[A-Za-z0-9_.-]{3,50}")) throw new ValidationException("Username must contain 3 to 50 letters, numbers, dots, dashes or underscores.");
        if (!ValidationUtil.isNotEmpty(dto.getFirstName()) || !ValidationUtil.isNotEmpty(dto.getLastName())) {
            throw new ValidationException("First Name and Last Name are mandatory.");
        }
        if (!ValidationUtil.isEligibleAge(dto.getDateOfBirth(), 18)) {
            throw new ValidationException("Customer must be at least 18 years old.");
        }
        if (!ValidationUtil.isValidEmail(dto.getEmail())) {
            throw new ValidationException("Invalid email format.");
        }
        if (!ValidationUtil.isValidMobile(dto.getMobileNumber())) {
            throw new ValidationException("Invalid mobile number format.");
        }
        if (!PasswordUtil.isValidPassword(dto.getPassword())) {
            throw new ValidationException("Password does not meet complexity requirements.");
        }

        try {
            if (customerDAO.findByUsername(dto.getUsername()).isPresent()) {
                throw new ValidationException("Username is already taken.");
            }
            if (customerDAO.findByEmail(dto.getEmail()).isPresent()) {
                throw new ValidationException("Email is already registered.");
            }
            if (customerDAO.findByMobileNumber(dto.getMobileNumber()).isPresent()) {
                throw new ValidationException("Mobile number is already registered.");
            }

            Customer customer = new Customer();
            customer.setCustomerNumber("CUST" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16)); // Generate unique CUST number
            customer.setFirstName(dto.getFirstName());
            customer.setLastName(dto.getLastName());
            customer.setDateOfBirth(dto.getDateOfBirth());
            customer.setEmail(dto.getEmail());
            customer.setMobileNumber(dto.getMobileNumber());
            customer.setAddress(dto.getAddress());
            customer.setCity(dto.getCity());
            customer.setCountry(dto.getCountry());
            customer.setUsername(dto.getUsername());
            customer.setPasswordHash(PasswordUtil.hashPassword(dto.getPassword()));
            customer.setAccountStatus("ACTIVE");

            Customer saved = customerDAO.save(customer);
            AuditLog audit = new AuditLog();
            audit.setEntityName("CUSTOMER");
            audit.setEntityId(String.valueOf(saved.getCustomerId()));
            audit.setAction("REGISTERED");
            audit.setDetails("Customer account registered: " + saved.getCustomerNumber());
            audit.setPerformedBy(saved.getUsername());
            try {
                auditDAO.logAudit(audit);
            } catch (SQLException auditError) {
                LOGGER.log(Level.WARNING, "Customer registered but activity logging failed", auditError);
            }
            return saved;

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error during registration", e);
            throw new ValidationException("System error during registration. Please try again.");
        }
    }

    @Override
    public void updateProfile(Customer customer) throws ValidationException {
        if (customer == null || !ValidationUtil.isNotEmpty(customer.getFirstName()) || !ValidationUtil.isNotEmpty(customer.getLastName())
                || !ValidationUtil.isNotEmpty(customer.getAddress()) || !ValidationUtil.isNotEmpty(customer.getCity())
                || !ValidationUtil.isNotEmpty(customer.getCountry()) || !ValidationUtil.isEligibleAge(customer.getDateOfBirth(),18))
            throw new ValidationException("Name, address, city, country and an eligible date of birth are required.");
        try {
            if (!customerDAO.update(customer)) throw new ValidationException("Customer not found.");
        } catch (SQLException e) { throw new ValidationException("Could not update customer profile: " + e.getMessage()); }
    }

    @Override
    public Customer getCustomerById(int customerId) throws ValidationException {
        try {
            Optional<Customer> customer = customerDAO.findById(customerId);
            if (!customer.isPresent()) {
                throw new ValidationException("Customer not found.");
            }
            return customer.get();
        } catch (SQLException e) {
            throw new ValidationException("Database error: " + e.getMessage());
        }
    }

    @Override
    public List<Customer> getAllCustomers() {
        try {
            return customerDAO.findAll();
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error fetching all customers", e);
            throw new RuntimeException("Failed to retrieve customer list due to a database error.", e);
        }
    }

    // Treat database errors as possible duplicate registrations.
    @Override
    public boolean isUsernameRegistered(String username) {
        try {
            return customerDAO.findByUsername(username).isPresent();
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error checking username availability: " + username, e);
            return true; // Fail-safe: assume taken to prevent duplicate registration
        }
    }

    // Treat database errors as possible duplicates.
    @Override
    public boolean isUserRegistered(String usernameOrEmail) {
        try {
            return customerDAO.findByUsernameOrEmail(usernameOrEmail).isPresent();
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error checking username or email: " + usernameOrEmail, e);
            return true; // Fail-safe: assume registered to prevent inconsistency
        }
    }
}
