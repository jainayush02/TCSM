package com.amdocs.telecom.validation;

import java.time.LocalDate;
import java.time.Period;
import java.util.regex.Pattern;

public class ValidationUtil {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,6}$");

    private static final Pattern PHONE_PATTERN =
            Pattern.compile("^\\+?[0-9]{10,15}$");

    public static boolean isValidEmail(String email) {
        if (email == null) return false;
        return EMAIL_PATTERN.matcher(email.trim()).matches();
    }

    public static boolean isValidMobile(String mobile) {
        if (mobile == null) return false;
        return PHONE_PATTERN.matcher(mobile.trim()).matches();
    }

    /**
     * Checks if customer satisfies minimum age requirements (e.g., 18 years old).
     */
    public static boolean isEligibleAge(LocalDate dob, int minAge) {
        if (dob == null) return false;
        return Period.between(dob, LocalDate.now()).getYears() >= minAge;
    }

    public static boolean isNotEmpty(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
