package com.amdocs.telecom.factory;

import com.amdocs.telecom.dao.*;
import com.amdocs.telecom.dao.impl.*;

/**
 * Factory Design Pattern for instantiating Data Access Objects (DAOs).
 * Decouples client components from concrete DAO implementation classes.
 */
public class DAOFactory {

    private DAOFactory() {}

    public static CustomerDAO getCustomerDAO() {
        return new CustomerDAOImpl();
    }

    public static PlanDAO getPlanDAO() {
        return new PlanDAOImpl();
    }

    public static SubscriptionDAO getSubscriptionDAO() {
        return new SubscriptionDAOImpl();
    }

    public static BillingDAO getBillingDAO() {
        return new BillingDAOImpl();
    }

    public static PaymentDAO getPaymentDAO() {
        return new PaymentDAOImpl();
    }

    public static UsageDAO getUsageDAO() {
        return new UsageDAOImpl();
    }

    public static ComplaintDAO getComplaintDAO() {
        return new ComplaintDAOImpl();
    }

    public static AuditAndNotificationDAO getAuditAndNotificationDAO() {
        return new AuditAndNotificationDAOImpl();
    }

    public static AdminDAO getAdminDAO() {
        return new AdminDAOImpl();
    }
}
