package com.amdocs.telecom.model;

import java.time.LocalDateTime;

public class SubscriptionHistory {
    private int historyId;
    private int subscriptionId;
    private Integer oldPlanId;
    private int newPlanId;
    private LocalDateTime changeDate;
    private String changeReason;
    private String changedBy;

    private String oldPlanName;
    private String newPlanName;

    public SubscriptionHistory() {}

    public SubscriptionHistory(int historyId, int subscriptionId, Integer oldPlanId, int newPlanId,
                               LocalDateTime changeDate, String changeReason, String changedBy) {
        this.historyId = historyId;
        this.subscriptionId = subscriptionId;
        this.oldPlanId = oldPlanId;
        this.newPlanId = newPlanId;
        this.changeDate = changeDate;
        this.changeReason = changeReason;
        this.changedBy = changedBy;
    }

    public int getHistoryId() { return historyId; }
    public void setHistoryId(int historyId) { this.historyId = historyId; }

    public int getSubscriptionId() { return subscriptionId; }
    public void setSubscriptionId(int subscriptionId) { this.subscriptionId = subscriptionId; }

    public Integer getOldPlanId() { return oldPlanId; }
    public void setOldPlanId(Integer oldPlanId) { this.oldPlanId = oldPlanId; }

    public int getNewPlanId() { return newPlanId; }
    public void setNewPlanId(int newPlanId) { this.newPlanId = newPlanId; }

    public LocalDateTime getChangeDate() { return changeDate; }
    public void setChangeDate(LocalDateTime changeDate) { this.changeDate = changeDate; }

    public String getChangeReason() { return changeReason; }
    public void setChangeReason(String changeReason) { this.changeReason = changeReason; }

    public String getChangedBy() { return changedBy; }
    public void setChangedBy(String changedBy) { this.changedBy = changedBy; }

    public String getOldPlanName() { return oldPlanName; }
    public void setOldPlanName(String oldPlanName) { this.oldPlanName = oldPlanName; }

    public String getNewPlanName() { return newPlanName; }
    public void setNewPlanName(String newPlanName) { this.newPlanName = newPlanName; }

    @Override
    public String toString() {
        return String.format("[%s] Plan changed from '%s' to '%s' | Reason: %s | By: %s",
                changeDate, (oldPlanName != null ? oldPlanName : oldPlanId),
                (newPlanName != null ? newPlanName : newPlanId), changeReason, changedBy);
    }
}
