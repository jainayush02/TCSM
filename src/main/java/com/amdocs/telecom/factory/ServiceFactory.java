package com.amdocs.telecom.factory;

import com.amdocs.telecom.service.*;
import com.amdocs.telecom.service.impl.*;

/**
 * Factory Design Pattern for instantiating Business Services.
 * Provides centralized service creation and lifecycle management.
 */
public class ServiceFactory {

    private ServiceFactory() {}

    public static AuthenticationService getAuthenticationService() {
        return new AuthenticationServiceImpl();
    }

    public static CustomerService getCustomerService() {
        return new CustomerServiceImpl();
    }

    public static PlanService getPlanService() {
        return new PlanServiceImpl();
    }

    public static SubscriptionService getSubscriptionService() {
        return new SubscriptionServiceImpl();
    }

    public static BillingService getBillingService() {
        return new BillingServiceImpl();
    }

    public static PaymentService getPaymentService() {
        return new PaymentServiceImpl();
    }

    public static UsageService getUsageService() {
        return new UsageServiceImpl();
    }

    public static ComplaintService getComplaintService() {
        return new ComplaintServiceImpl();
    }

    public static ReportService getReportService() {
        return new ReportServiceImpl();
    }
}
