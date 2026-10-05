package com.amdocs.telecom.service;

import com.amdocs.telecom.exception.TelecomException;
import com.amdocs.telecom.model.Complaint;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface ComplaintService {
    Complaint lodgeComplaint(int customerId, String category, String description) throws TelecomException;
    List<Complaint> getCustomerComplaints(int customerId);
    List<Complaint> getAllComplaints();
    Optional<Complaint> getComplaintById(int complaintId);
    Optional<Complaint> getComplaintByNumber(String complaintNumber);
    boolean resolveComplaint(int complaintId, String resolution) throws TelecomException;
    boolean resolveComplaint(int complaintId, String status, String resolution, String resolvedByAdmin) throws TelecomException;
    Map<String, int[]> getComplaintsCountByCity();
    Map<String, int[]> getComplaintsCountByCategory();
    List<Map<String, Object>> getTopCustomersByComplaints(int limit);
    List<Map<String, Object>> getCustomersWithMultipleComplaints(int minComplaints);
}
