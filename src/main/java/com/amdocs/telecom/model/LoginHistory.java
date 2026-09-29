package com.amdocs.telecom.model;

import java.time.LocalDateTime;

public class LoginHistory {
    private int loginId;
    private String username;
    private String userRole; // CUSTOMER / ADMIN
    private LocalDateTime loginTimestamp;
    private String ipAddress;
    private String status; // SUCCESS / FAILED

    public LoginHistory() {}

    public LoginHistory(int loginId, String username, String userRole,
                        LocalDateTime loginTimestamp, String ipAddress, String status) {
        this.loginId = loginId;
        this.username = username;
        this.userRole = userRole;
        this.loginTimestamp = loginTimestamp;
        this.ipAddress = ipAddress;
        this.status = status;
    }

    public int getLoginId() { return loginId; }
    public void setLoginId(int loginId) { this.loginId = loginId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getUserRole() { return userRole; }
    public void setUserRole(String userRole) { this.userRole = userRole; }

    public LocalDateTime getLoginTimestamp() { return loginTimestamp; }
    public void setLoginTimestamp(LocalDateTime loginTimestamp) { this.loginTimestamp = loginTimestamp; }

    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    @Override
    public String toString() {
        return String.format("[%s] User: %-12s | Role: %-8s | IP: %-12s | Status: %s",
                loginTimestamp, username, userRole, ipAddress, status);
    }
}
