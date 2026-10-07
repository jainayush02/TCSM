package com.amdocs.telecom.security;

import java.security.SecureRandom;

public class CaptchaGenerator {

    private static final String CHARACTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int DEFAULT_LENGTH = 6;
    private static final SecureRandom RANDOM = new SecureRandom();

    public static String generateCaptcha() {
        return generateCaptcha(DEFAULT_LENGTH);
    }

    public static String generateCaptcha(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(CHARACTERS.charAt(RANDOM.nextInt(CHARACTERS.length())));
        }
        return sb.toString();
    }

    public static boolean validateCaptcha(String input, String expected) {
        if (input == null || expected == null) return false;
        return input.trim().equalsIgnoreCase(expected.trim());
    }
}
