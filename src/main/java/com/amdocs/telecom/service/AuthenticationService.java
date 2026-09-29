package com.amdocs.telecom.service;

import com.amdocs.telecom.exception.AuthenticationException;
import com.amdocs.telecom.model.Customer;

public interface AuthenticationService {
    Customer login(String username, String password, String expectedCaptcha, String inputCaptcha) throws AuthenticationException;
    String initiatePasswordRecovery(String username) throws AuthenticationException;
    boolean completePasswordRecovery(String username, String otp, String newPassword) throws AuthenticationException;
    void logout(String username);
}
