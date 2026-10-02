package com.amdocs.telecom.model;

import java.time.LocalDateTime;

public class Administrator {
    private int adminId;
    private String adminNumber;
    private String username;
    private String passwordHash;
    private String email;
    private String fullName;
    private String accountStatus;
    private LocalDateTime createdAt;

    public Administrator() {}

    public Administrator(int adminId, String adminNumber, String username, String passwordHash,
                         String email, String fullName, LocalDateTime createdAt) {
        this.adminId = adminId;
        this.adminNumber = adminNumber;
        this.username = username;
        this.passwordHash = passwordHash;
        this.email = email;
        this.fullName = fullName;
        this.createdAt = createdAt;
    }

    public int getAdminId() { return adminId; }
    public void setAdminId(int adminId) { this.adminId = adminId; }

    public String getAdminNumber() { return adminNumber; }
    public void setAdminNumber(String adminNumber) { this.adminNumber = adminNumber; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getAccountStatus() { return accountStatus; }
    public void setAccountStatus(String accountStatus) { this.accountStatus = accountStatus; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    @Override
    public String toString() {
        return String.format("[%s] Admin: %s (%s)", adminNumber, fullName, username);
    }
}
