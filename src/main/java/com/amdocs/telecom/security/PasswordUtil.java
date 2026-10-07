package com.amdocs.telecom.security;

import org.mindrot.jbcrypt.BCrypt;
import java.util.regex.Pattern;

public class PasswordUtil {

    private static final String PASSWORD_PATTERN =
            "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=!])(?=\\S+$).{8,}$";

    private static final Pattern PATTERN = Pattern.compile(PASSWORD_PATTERN);

    public static boolean isValidPassword(String password) {
        if (password == null) return false;
        return PATTERN.matcher(password).matches();
    }

    public static java.util.List<String> getPasswordMissingRequirements(String password) {
        java.util.List<String> missing = new java.util.ArrayList<>();
        if (password == null || password.length() < 8) {
            missing.add("Must be at least 8 characters long");
        }
        if (password == null || !Pattern.compile("[A-Z]").matcher(password).find()) {
            missing.add("Must contain at least 1 uppercase letter (A-Z)");
        }
        if (password == null || !Pattern.compile("[a-z]").matcher(password).find()) {
            missing.add("Must contain at least 1 lowercase letter (a-z)");
        }
        if (password == null || !Pattern.compile("[0-9]").matcher(password).find()) {
            missing.add("Must contain at least 1 digit (0-9)");
        }
        if (password == null || !Pattern.compile("[@#$%^&+=!]").matcher(password).find()) {
            missing.add("Must contain at least 1 special character (@#$%^&+=!)");
        }
        if (password != null && password.contains(" ")) {
            missing.add("Must not contain any spaces");
        }
        return missing;
    }

    public static String hashPassword(String plainPassword) {
        return BCrypt.hashpw(plainPassword, BCrypt.gensalt(10));
    }

    public static boolean verifyPassword(String plainPassword, String hashedPassword) {
        if (plainPassword == null || hashedPassword == null) return false;
        try {
            return BCrypt.checkpw(plainPassword, hashedPassword);
        } catch (Exception e) {
            return false;
        }
    }
}
