package com.amdocs.telecom.dao;

import com.amdocs.telecom.model.Payment;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public interface PaymentDAO {
    Payment save(Payment payment) throws SQLException;
    Payment save(java.sql.Connection conn, Payment payment) throws SQLException;
    Optional<Payment> findById(int paymentId) throws SQLException;
    Optional<Payment> findByReference(String reference) throws SQLException;
    List<Payment> findByCustomerId(int customerId) throws SQLException;
    List<Payment> findByBillId(int billId) throws SQLException;
    List<Payment> findAll() throws SQLException;
}
