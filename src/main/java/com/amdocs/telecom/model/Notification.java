package com.amdocs.telecom.model;

import java.time.LocalDateTime;

public class Notification {
    private int notificationId;
    private int customerId;
    private String title;
    private String message;
    private LocalDateTime createdAt;
    private String status; // UNREAD, READ

    public Notification() {}

    public Notification(int notificationId, int customerId, String title, String message,
                        LocalDateTime createdAt, String status) {
        this.notificationId = notificationId;
        this.customerId = customerId;
        this.title = title;
        this.message = message;
        this.createdAt = createdAt;
        this.status = status;
    }

    public int getNotificationId() { return notificationId; }
    public void setNotificationId(int notificationId) { this.notificationId = notificationId; }

    public int getCustomerId() { return customerId; }
    public void setCustomerId(int customerId) { this.customerId = customerId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    @Override
    public String toString() {
        return String.format("[%s] %s: %s (%s)", createdAt, title, message, status);
    }
}
