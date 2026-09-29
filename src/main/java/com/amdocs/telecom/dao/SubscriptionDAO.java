package com.amdocs.telecom.dao;

import com.amdocs.telecom.model.MobileSubscription;
import com.amdocs.telecom.model.SIMCard;
import com.amdocs.telecom.model.SubscriptionHistory;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public interface SubscriptionDAO {
    MobileSubscription saveSubscription(MobileSubscription sub) throws SQLException;
    Optional<MobileSubscription> findById(int subscriptionId) throws SQLException;
    Optional<MobileSubscription> findByNumber(String subscriptionNumber) throws SQLException;
    Optional<MobileSubscription> findByMobileNumber(String mobileNumber) throws SQLException;
    List<MobileSubscription> findByCustomerId(int customerId) throws SQLException;
    List<MobileSubscription> findAll() throws SQLException;
    boolean updateStatus(int subscriptionId, String status) throws SQLException;
    boolean changePlan(int subscriptionId, int newPlanId, String reason, String changedBy) throws SQLException;
    List<SubscriptionHistory> getHistory(int subscriptionId) throws SQLException;

    // SIM operations
    SIMCard saveSIM(SIMCard sim) throws SQLException;
    Optional<SIMCard> findAvailableSIM(String simType) throws SQLException;
    boolean updateSIMStatus(int simId, String status) throws SQLException;
}
