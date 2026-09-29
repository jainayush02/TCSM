package com.amdocs.telecom.dao;

import com.amdocs.telecom.model.UsageRecord;
import com.amdocs.telecom.model.UsageType;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public interface UsageDAO {
    UsageRecord save(UsageRecord usage) throws SQLException;
    int[] saveBatch(List<UsageRecord> usageRecords) throws SQLException;
    List<UsageRecord> findBySubscriptionId(int subscriptionId) throws SQLException;
    List<UsageRecord> findBySubscriptionAndMonth(int subscriptionId, String yearMonth) throws SQLException;
    List<UsageRecord> findAll() throws SQLException;
    Map<UsageType, Double> getUsageSummaryByType(int subscriptionId) throws SQLException;
    double getTotalUsageCharge(int subscriptionId, String yearMonth) throws SQLException;
}
