package com.amdocs.telecom.dao;

import com.amdocs.telecom.model.Bill;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public interface BillingDAO {
    Bill save(Bill bill) throws SQLException;
    Optional<Bill> findById(int billId) throws SQLException;
    Optional<Bill> findById(java.sql.Connection conn, int billId) throws SQLException;
    Optional<Bill> findByNumber(String billNumber) throws SQLException;
    Optional<Bill> findBySubscriptionAndMonth(int subscriptionId, String billingMonth) throws SQLException;
    List<Bill> findBySubscriptionId(int subscriptionId) throws SQLException;
    List<Bill> findByCustomerId(int customerId) throws SQLException;
    List<Bill> findUnpaidBills() throws SQLException;
    List<Bill> findAll() throws SQLException;
    boolean updateStatus(int billId, String status) throws SQLException;
    boolean updateStatus(java.sql.Connection conn, int billId, String status) throws SQLException;
}
