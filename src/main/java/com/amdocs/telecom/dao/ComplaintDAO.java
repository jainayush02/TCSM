package com.amdocs.telecom.dao;

import com.amdocs.telecom.model.Complaint;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface ComplaintDAO {
    Complaint save(Complaint complaint) throws SQLException;
    Optional<Complaint> findById(int complaintId) throws SQLException;
    Optional<Complaint> findByNumber(String complaintNumber) throws SQLException;
    List<Complaint> findByCustomerId(int customerId) throws SQLException;
    List<Complaint> findAll() throws SQLException;
    boolean updateStatusAndResolution(int complaintId, String status, String resolution) throws SQLException;
    
    Map<String, int[]> getComplaintsCountByCity() throws SQLException;
    Map<String, int[]> getComplaintsCountByCategory() throws SQLException;
    List<Map<String, Object>> getTopCustomersByComplaints(int limit) throws SQLException;
    List<Map<String, Object>> getCustomersWithMultipleComplaints(int minComplaints) throws SQLException;
}
