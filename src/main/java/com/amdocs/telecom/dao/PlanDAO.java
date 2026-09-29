package com.amdocs.telecom.dao;

import com.amdocs.telecom.model.TelecomPlan;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public interface PlanDAO {
    TelecomPlan save(TelecomPlan plan) throws SQLException;
    Optional<TelecomPlan> findById(int planId) throws SQLException;
    Optional<TelecomPlan> findByCode(String planCode) throws SQLException;
    List<TelecomPlan> findAll() throws SQLException;
    List<TelecomPlan> findAllActive() throws SQLException;
    boolean update(TelecomPlan plan) throws SQLException;
    boolean updateStatus(int planId, String status) throws SQLException;
    boolean delete(int planId) throws SQLException;
}
