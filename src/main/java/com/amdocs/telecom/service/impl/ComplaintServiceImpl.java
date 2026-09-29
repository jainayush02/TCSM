package com.amdocs.telecom.service.impl;

import com.amdocs.telecom.dao.ComplaintDAO;
import com.amdocs.telecom.factory.DAOFactory;
import com.amdocs.telecom.exception.TelecomException;
import com.amdocs.telecom.model.Complaint;
import com.amdocs.telecom.model.ComplaintCategory;
import com.amdocs.telecom.service.ComplaintService;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class ComplaintServiceImpl implements ComplaintService {

    private final ComplaintDAO complaintDAO;

    public ComplaintServiceImpl() {
        this.complaintDAO = DAOFactory.getComplaintDAO();
    }

    @Override
    public Complaint lodgeComplaint(int customerId, String categoryStr, String description) throws TelecomException {
        if (description == null || description.trim().isEmpty()) {
            throw new TelecomException("Complaint description cannot be empty.");
        }
        ComplaintCategory category;
        try {
            category = ComplaintCategory.valueOf(categoryStr.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new TelecomException("Invalid complaint category. Valid values: BILLING, NETWORK, SIM, PLAN, PAYMENT, OTHER");
        }

        Complaint complaint = new Complaint();
        complaint.setComplaintNumber("CMP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        complaint.setCustomerId(customerId);
        complaint.setCategory(category);
        complaint.setDescription(description.trim());
        complaint.setPriority("MEDIUM");
        complaint.setStatus("OPEN");
        complaint.setCreatedDate(LocalDateTime.now());

        try {
            return complaintDAO.save(complaint);
        } catch (SQLException e) {
            throw new TelecomException("Failed to register complaint: " + e.getMessage());
        }
    }

    @Override
    public List<Complaint> getCustomerComplaints(int customerId) {
        try {
            return complaintDAO.findByCustomerId(customerId);
        } catch (SQLException e) {
            return Collections.emptyList();
        }
    }

    @Override
    public List<Complaint> getAllComplaints() {
        try {
            return complaintDAO.findAll();
        } catch (SQLException e) {
            return Collections.emptyList();
        }
    }

    @Override
    public boolean resolveComplaint(int complaintId, String resolution) throws TelecomException {
        if (resolution == null || resolution.trim().isEmpty()) {
            throw new TelecomException("Resolution details cannot be empty.");
        }
        try {
            return complaintDAO.updateStatusAndResolution(complaintId, "RESOLVED", resolution.trim());
        } catch (SQLException e) {
            throw new TelecomException("Failed to resolve complaint: " + e.getMessage());
        }
    }
}
