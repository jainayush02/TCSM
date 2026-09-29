package com.amdocs.telecom.service;

import com.amdocs.telecom.exception.TelecomException;
import com.amdocs.telecom.model.Complaint;

import java.util.List;

public interface ComplaintService {
    Complaint lodgeComplaint(int customerId, String category, String description) throws TelecomException;
    List<Complaint> getCustomerComplaints(int customerId);
    List<Complaint> getAllComplaints();
    boolean resolveComplaint(int complaintId, String resolution) throws TelecomException;
}
