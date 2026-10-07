package com.amdocs.telecom.service.impl;

import com.amdocs.telecom.dao.ComplaintDAO;
import com.amdocs.telecom.factory.DAOFactory;
import com.amdocs.telecom.exception.TelecomException;
import com.amdocs.telecom.model.AuditLog;
import com.amdocs.telecom.model.Complaint;
import com.amdocs.telecom.model.ComplaintCategory;
import com.amdocs.telecom.model.Notification;
import com.amdocs.telecom.service.ComplaintService;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class ComplaintServiceImpl implements ComplaintService {

    private static final java.util.logging.Logger LOGGER = java.util.logging.Logger
            .getLogger(ComplaintServiceImpl.class.getName());
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
            if (categoryStr == null) throw new IllegalArgumentException();
            category = ComplaintCategory.valueOf(categoryStr.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new TelecomException(
                    "Invalid complaint category. Valid values: BILLING, NETWORK, SIM, PLAN, PAYMENT, OTHER");
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
            LOGGER.log(java.util.logging.Level.SEVERE, "Database error fetching complaints for customer: " + customerId,
                    e);
            throw new RuntimeException("Failed to retrieve customer complaints.", e);
        }
    }

    @Override
    public List<Complaint> getAllComplaints() {
        try {
            return complaintDAO.findAll();
        } catch (SQLException e) {
            LOGGER.log(java.util.logging.Level.SEVERE, "Database error fetching all complaints", e);
            throw new RuntimeException("Failed to retrieve complaints list.", e);
        }
    }

    @Override
    public Optional<Complaint> getComplaintById(int complaintId) {
        try {
            return complaintDAO.findById(complaintId);
        } catch (SQLException e) {
            LOGGER.log(java.util.logging.Level.SEVERE, "Database error fetching complaint ID: " + complaintId, e);
            throw new RuntimeException("Failed to look up complaint.", e);
        }
    }

    @Override
    public Optional<Complaint> getComplaintByNumber(String complaintNumber) {
        try {
            return complaintDAO.findByNumber(complaintNumber);
        } catch (SQLException e) {
            LOGGER.log(java.util.logging.Level.SEVERE, "Database error fetching complaint: " + complaintNumber, e);
            throw new RuntimeException("Failed to look up complaint.", e);
        }
    }

    @Override
    public boolean resolveComplaint(int complaintId, String resolution) throws TelecomException {
        return resolveComplaint(complaintId, "RESOLVED", resolution, "ADMIN");
    }

    @Override
    public boolean resolveComplaint(int complaintId, String status, String resolution, String resolvedByAdmin)
            throws TelecomException {
        if (resolution == null || resolution.trim().isEmpty()) {
            throw new TelecomException("Resolution details cannot be empty.");
        }
        String cleanStatus = (status != null && !status.trim().isEmpty()) ? status.trim().toUpperCase() : "RESOLVED";

        if (!java.util.Set.of("OPEN", "IN_PROGRESS", "RESOLVED", "CLOSED").contains(cleanStatus))
            throw new TelecomException("Invalid complaint status.");

        Optional<Complaint> opt;
        try {
            opt = complaintDAO.findById(complaintId);
        } catch (SQLException e) {
            throw new TelecomException("Failed to look up complaint: " + e.getMessage());
        }

        if (!opt.isPresent()) {
            throw new TelecomException("Complaint ID #" + complaintId + " does not exist.");
        }

        Complaint complaint = opt.get();

        try {
            boolean updated = complaintDAO.updateStatusAndResolution(complaintId, cleanStatus, resolution.trim());
            if (updated) {
                try {
                    Notification notif = new Notification();
                    notif.setCustomerId(complaint.getCustomerId());
                    notif.setTitle("Complaint " + complaint.getComplaintNumber() + " " + cleanStatus);
                    notif.setMessage(String.format("Your %s complaint (%s) is now marked as %s. Solution: %s",
                            complaint.getCategory(), complaint.getComplaintNumber(), cleanStatus, resolution.trim()));
                    notif.setStatus("UNREAD");
                    DAOFactory.getAuditAndNotificationDAO().createNotification(notif);
                } catch (SQLException e) {
                    LOGGER.log(java.util.logging.Level.WARNING,
                            "Unable to create complaint notification", e);
                }

                try {
                    AuditLog audit = new AuditLog();
                    audit.setEntityName("COMPLAINT");
                    audit.setEntityId(String.valueOf(complaintId));
                    audit.setAction("COMPLAINT_" + cleanStatus);
                    audit.setDetails(String.format("Ticket %s (%s) set to %s. Solution: %s",
                            complaint.getComplaintNumber(), complaint.getCategory(), cleanStatus, resolution.trim()));
                    audit.setPerformedBy(resolvedByAdmin != null ? resolvedByAdmin : "ADMIN");
                    DAOFactory.getAuditAndNotificationDAO().logAudit(audit);
                } catch (SQLException e) {
                    LOGGER.log(java.util.logging.Level.WARNING, "Unable to write complaint audit log", e);
                }

                return true;
            }
            return false;
        } catch (SQLException e) {
            throw new TelecomException("Failed to resolve complaint: " + e.getMessage());
        }
    }

    @Override
    public Map<String, int[]> getComplaintsCountByCity() {
        try {
            return complaintDAO.getComplaintsCountByCity();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not load complaint analytics.",e);
        }
    }

    @Override
    public Map<String, int[]> getComplaintsCountByCategory() {
        try {
            return complaintDAO.getComplaintsCountByCategory();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not load complaint analytics.",e);
        }
    }

    @Override
    public List<Map<String, Object>> getTopCustomersByComplaints(int limit) {
        try {
            return complaintDAO.getTopCustomersByComplaints(limit);
        } catch (SQLException e) {
            throw new IllegalStateException("Could not load complaint analytics.",e);
        }
    }

    @Override
    public List<Map<String, Object>> getCustomersWithMultipleComplaints(int minComplaints) {
        try {
            return complaintDAO.getCustomersWithMultipleComplaints(minComplaints);
        } catch (SQLException e) {
            throw new IllegalStateException("Could not load complaint analytics.",e);
        }
    }
}
