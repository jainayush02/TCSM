package com.amdocs.telecom.service.impl;

import com.amdocs.telecom.dao.ComplaintDAO;
import com.amdocs.telecom.factory.DAOFactory;
import com.amdocs.telecom.exception.TelecomException;
import com.amdocs.telecom.model.AuditLog;
import com.amdocs.telecom.model.Complaint;
import com.amdocs.telecom.model.ComplaintCategory;
import com.amdocs.telecom.model.Customer;
import com.amdocs.telecom.model.Notification;
import com.amdocs.telecom.service.ComplaintService;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Collections;
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
        ensureSampleComplaintsIfEmpty();
    }

    private void ensureSampleComplaintsIfEmpty() {
        try {
            List<Complaint> existing = complaintDAO.findAll();
            if (existing.isEmpty()) {
                List<Customer> customers = DAOFactory.getCustomerDAO().findAll();
                if (!customers.isEmpty()) {
                    Customer c1 = customers.get(0);
                    Customer c2 = customers.size() > 1 ? customers.get(1) : c1;
                    Customer c3 = customers.size() > 2 ? customers.get(2) : c1;

                    // Seed complaints across categories and cities.
                    Complaint s1 = new Complaint();
                    s1.setComplaintNumber("CMP-1001A1");
                    s1.setCustomerId(c1.getCustomerId());
                    s1.setCategory(ComplaintCategory.NETWORK);
                    s1.setDescription("Slow 5G internet speeds and call drops during peak hours in South Mumbai.");
                    s1.setPriority("HIGH");
                    s1.setStatus("RESOLVED");
                    s1.setResolution(
                            "Network cell tower 4B optimized and antenna reoriented. Speeds verified above 150 Mbps.");
                    s1.setCreatedDate(LocalDateTime.now().minusDays(3));
                    complaintDAO.save(s1);

                    Complaint s2 = new Complaint();
                    s2.setComplaintNumber("CMP-1002B2");
                    s2.setCustomerId(c1.getCustomerId());
                    s2.setCategory(ComplaintCategory.BILLING);
                    s2.setDescription("Charged extra INR 170 for roaming when roaming was included in 5G plan.");
                    s2.setPriority("MEDIUM");
                    s2.setStatus("OPEN");
                    s2.setCreatedDate(LocalDateTime.now().minusDays(2));
                    complaintDAO.save(s2);

                    Complaint s3 = new Complaint();
                    s3.setComplaintNumber("CMP-1003C3");
                    s3.setCustomerId(c2.getCustomerId());
                    s3.setCategory(ComplaintCategory.NETWORK);
                    s3.setDescription("Cannot connect to 4G LTE while traveling on underground commute routes.");
                    s3.setPriority("LOW");
                    s3.setStatus("OPEN");
                    s3.setCreatedDate(LocalDateTime.now().minusDays(1));
                    complaintDAO.save(s3);

                    Complaint s4 = new Complaint();
                    s4.setComplaintNumber("CMP-1004D4");
                    s4.setCustomerId(c3.getCustomerId());
                    s4.setCategory(ComplaintCategory.SIM);
                    s4.setDescription("eSIM profile download QR code failed scanning twice.");
                    s4.setPriority("HIGH");
                    s4.setStatus("RESOLVED");
                    s4.setResolution(
                            "Fresh eSIM activation QR profile regenerated and emailed to customer. Profile successfully installed.");
                    s4.setCreatedDate(LocalDateTime.now().minusDays(4));
                    complaintDAO.save(s4);

                    Complaint s5 = new Complaint();
                    s5.setComplaintNumber("CMP-1005E5");
                    s5.setCustomerId(c1.getCustomerId());
                    s5.setCategory(ComplaintCategory.PAYMENT);
                    s5.setDescription("UPI transaction deducted twice during bill payment.");
                    s5.setPriority("CRITICAL");
                    s5.setStatus("RESOLVED");
                    s5.setResolution(
                            "Duplicate transaction verified. Automatic refund initiated to original bank account via payment gateway.");
                    s5.setCreatedDate(LocalDateTime.now().minusDays(5));
                    complaintDAO.save(s5);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(java.util.logging.Level.WARNING, "Unable to seed sample complaints", e);
        }
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
            return Collections.emptyMap();
        }
    }

    @Override
    public Map<String, int[]> getComplaintsCountByCategory() {
        try {
            return complaintDAO.getComplaintsCountByCategory();
        } catch (SQLException e) {
            return Collections.emptyMap();
        }
    }

    @Override
    public List<Map<String, Object>> getTopCustomersByComplaints(int limit) {
        try {
            return complaintDAO.getTopCustomersByComplaints(limit);
        } catch (SQLException e) {
            return Collections.emptyList();
        }
    }

    @Override
    public List<Map<String, Object>> getCustomersWithMultipleComplaints(int minComplaints) {
        try {
            return complaintDAO.getCustomersWithMultipleComplaints(minComplaints);
        } catch (SQLException e) {
            return Collections.emptyList();
        }
    }
}
