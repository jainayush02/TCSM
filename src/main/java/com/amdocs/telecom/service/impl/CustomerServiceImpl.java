package com.amdocs.telecom.service.impl;

import com.amdocs.telecom.dao.CustomerDAO;
import com.amdocs.telecom.dao.impl.CustomerDAOImpl;
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

    public CustomerServiceImpl() {
        this.customerDAO = new CustomerDAOImpl();
    }

    @Override
    public Customer registerCustomer(CustomerRegistrationDTO dto) throws ValidationException {
        // Business Rules Validation
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
            customer.setCustomerNumber("CUST" + (100000 + (int)(Math.random() * 900000))); // Generate unique CUST number
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

            return customerDAO.save(customer);

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error during registration", e);
            throw new ValidationException("System error during registration. Please try again.");
        }
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
            return List.of();
        }
    }

    @Override
    public boolean isUsernameRegistered(String username) {
        try {
            return customerDAO.findByUsername(username).isPresent();
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error checking username", e);
            return false;
        }
    }

    @Override
    public boolean isUserRegistered(String usernameOrEmail) {
        try {
            return customerDAO.findByUsernameOrEmail(usernameOrEmail).isPresent();
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error checking username or email", e);
            return false;
        }
    }
}
