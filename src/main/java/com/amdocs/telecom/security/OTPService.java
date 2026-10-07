package com.amdocs.telecom.security;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class OTPService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int EXPIRY_MINUTES = 5;

    private static class OtpEntry {
        String otp;
        LocalDateTime expiryTime;
        int attempts;

        OtpEntry(String otp, LocalDateTime expiryTime) {
            this.otp = otp;
            this.expiryTime = expiryTime;
        }
    }

    private static final Map<String, OtpEntry> otpStorage = new ConcurrentHashMap<>();

    public static String generateOtp(String key) {
        int number = 100000 + RANDOM.nextInt(900000);
        String otp = String.valueOf(number);
        otpStorage.put(key.toLowerCase(), new OtpEntry(otp, LocalDateTime.now().plusMinutes(EXPIRY_MINUTES)));
        return otp;
    }

    public static boolean verifyOtp(String key, String enteredOtp) {
        if (key == null || enteredOtp == null) return false;
        String normalized=key.toLowerCase(java.util.Locale.ROOT);
        java.util.concurrent.atomic.AtomicBoolean valid=new java.util.concurrent.atomic.AtomicBoolean();
        otpStorage.computeIfPresent(normalized,(k,entry) -> {
            if(LocalDateTime.now().isAfter(entry.expiryTime) || entry.attempts>=3) return null;
            if(entry.otp.equals(enteredOtp.trim())) { valid.set(true); return null; }
            entry.attempts++;
            return entry.attempts>=3?null:entry;
        });
        return valid.get();
    }
}
