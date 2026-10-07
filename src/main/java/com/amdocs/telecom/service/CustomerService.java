package com.amdocs.telecom.service;

import com.amdocs.telecom.dto.CustomerRegistrationDTO;
import com.amdocs.telecom.exception.ValidationException;
import com.amdocs.telecom.model.Customer;

import java.util.List;

public interface CustomerService {
    Customer registerCustomer(CustomerRegistrationDTO dto) throws ValidationException;
    Customer getCustomerById(int customerId) throws ValidationException;
    List<Customer> getAllCustomers();
    boolean isUsernameRegistered(String username);
    boolean isUserRegistered(String usernameOrEmail);
    void updateProfile(Customer customer) throws ValidationException;
}
