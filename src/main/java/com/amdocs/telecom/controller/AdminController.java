package com.amdocs.telecom.controller;

import com.amdocs.telecom.dao.AdminDAO;
import com.amdocs.telecom.dao.AuditAndNotificationDAO;
import com.amdocs.telecom.dao.impl.AdminDAOImpl;
import com.amdocs.telecom.dao.impl.AuditAndNotificationDAOImpl;
import com.amdocs.telecom.dao.impl.CustomerDAOImpl;
import com.amdocs.telecom.model.*;
import com.amdocs.telecom.scheduler.AccountMonitor;
import com.amdocs.telecom.scheduler.BillingScheduler;
import com.amdocs.telecom.scheduler.UsageProcessor;
import com.amdocs.telecom.security.CaptchaGenerator;
import com.amdocs.telecom.security.PasswordUtil;
import com.amdocs.telecom.service.*;
import com.amdocs.telecom.service.impl.*;

import java.sql.SQLException;
import java.util.DoubleSummaryStatistics;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;

/**
 * AdminController handles the telecom administrator portal menu.
 */
public class AdminController {

    private final AdminDAO adminDAO;
    private final PlanService planService;
    private final CustomerService customerService;
    private final BillingService billingService;
    private final ReportService reportService;
    private final AuditAndNotificationDAO auditDAO;
    private final com.amdocs.telecom.report.ReportGenerator reportGenerator;
    private final Scanner scanner;
    private Administrator loggedInAdmin;

    public AdminController(Scanner scanner) {
        this.scanner = scanner;
        this.adminDAO = new AdminDAOImpl();
        this.planService = new PlanServiceImpl();
        this.customerService = new CustomerServiceImpl();
        this.billingService = new BillingServiceImpl();
        this.reportService = new ReportServiceImpl();
        this.auditDAO = new AuditAndNotificationDAOImpl();
        this.reportGenerator = new com.amdocs.telecom.report.ReportGenerator();
    }

    public void showLoginMenu() {
        boolean inPortal = true;
        while (inPortal) {
            System.out.println("\n╔══════════════════════════════════════╗");
            System.out.println("║     ADMINISTRATOR LOGIN PORTAL       ║");
            System.out.println("╠══════════════════════════════════════╣");
            System.out.println("║  1. Admin Login                      ║");
            System.out.println("║  2. Forgot Password (OTP Recovery)   ║");
            System.out.println("║  3. Back to Main Menu                ║");
            System.out.println("╚══════════════════════════════════════╝");
            System.out.print("Select option: ");
            if (!scanner.hasNextLine()) break;

            int choice = readInt();
            switch (choice) {
                case 1 -> {
                    if (handleAdminLogin()) {
                        showAdminDashboard();
                    }
                }
                case 2 -> handleAdminForgotPassword();
                case 3 -> inPortal = false;
                default -> System.out.println("Invalid option. Please choose 1, 2, or 3.");
            }
        }
    }

    private boolean handleAdminLogin() {
        System.out.println("\n--- Administrator Login ---");
        System.out.print("Admin Username (or 'cancel'): ");
        String username = scanner.nextLine().trim();
        if ("cancel".equalsIgnoreCase(username)) return false;

        // Early check: Verify admin username exists in database before asking for password
        try {
            if (!adminDAO.findByUsername(username).isPresent()) {
                System.out.println("❌ Admin username '" + username + "' is not registered. Please check your username.");
                return false;
            }
        } catch (SQLException e) {
            System.out.println("❌ Database error while verifying username: " + e.getMessage());
            return false;
        }

        System.out.print("Admin Password: ");
        String password = scanner.nextLine().trim();
        if ("cancel".equalsIgnoreCase(password)) return false;

        // CAPTCHA verification with 3 retries
        String captchaInput = null;
        for (int cAttempt = 1; cAttempt <= 3; cAttempt++) {
            String captcha = CaptchaGenerator.generateCaptcha();
            System.out.println("CAPTCHA: [" + captcha + "]");
            System.out.print("Enter CAPTCHA: ");
            captchaInput = scanner.nextLine().trim();

            if (CaptchaGenerator.validateCaptcha(captchaInput, captcha)) {
                break;
            }

            if (cAttempt < 3) {
                System.out.println("⚠️ CAPTCHA did not match. Generating new CAPTCHA (Attempt " + (cAttempt + 1) + " of 3):");
            } else {
                System.out.println("❌ CAPTCHA verification failed. Returning to menu.");
                return false;
            }
        }

        try {
            Optional<Administrator> opt = adminDAO.findByUsername(username);
            if (opt.isPresent() && PasswordUtil.verifyPassword(password, opt.get().getPasswordHash())) {
                loggedInAdmin = opt.get();
                System.out.println("\n✅ Admin login successful! Welcome, " + loggedInAdmin.getFullName());

                // Log login
                new CustomerDAOImpl().logLoginAttempt(username, "ADMIN", "127.0.0.1", "SUCCESS");
                return true;
            } else {
                new CustomerDAOImpl().logLoginAttempt(username, "ADMIN", "127.0.0.1", "FAILED");
                System.out.println("❌ Invalid admin credentials.");
                return false;
            }
        } catch (SQLException e) {
            System.out.println("❌ Database error: " + e.getMessage());
            return false;
        }
    }

    private void showAdminDashboard() {
        boolean running = true;
        while (running) {
            System.out.println("\n╔══════════════════════════════════════╗");
            System.out.println("║      ADMINISTRATOR DASHBOARD         ║");
            System.out.println("║  Admin: " + padRight(loggedInAdmin.getFullName(), 27) + " ║");
            System.out.println("╠══════════════════════════════════════╣");
            System.out.println("║  1.  View All Plans                  ║");
            System.out.println("║  2.  Add New Plan                    ║");
            System.out.println("║  3.  Activate/Deactivate Plan        ║");
            System.out.println("║  4.  View All Customers              ║");
            System.out.println("║  5.  View All Subscriptions          ║");
            System.out.println("║  6.  Generate Billing Cycle          ║");
            System.out.println("║  7.  View Unpaid Bills               ║");
            System.out.println("║  8.  Scan Overdue Accounts           ║");
            System.out.println("║  9.  Process Bulk Usage              ║");
            System.out.println("║  10. Revenue Reports                 ║");
            System.out.println("║  11. Customer Distribution by City   ║");
            System.out.println("║  12. View Audit Logs                 ║");
            System.out.println("║  13. Change My Password              ║");
            System.out.println("║  14. Logout                          ║");
            System.out.println("╚══════════════════════════════════════╝");
            System.out.print("Select option: ");
            if (!scanner.hasNextLine()) break;

            int choice = readInt();
            switch (choice) {
                case 1  -> viewAllPlans();
                case 2  -> addNewPlan();
                case 3  -> togglePlanStatus();
                case 4  -> viewAllCustomers();
                case 5  -> viewAllSubscriptions();
                case 6  -> generateBillingCycle();
                case 7  -> viewUnpaidBills();
                case 8  -> scanOverdueAccounts();
                case 9  -> processBulkUsage();
                case 10 -> showRevenueReports();
                case 11 -> showCustomerDistribution();
                case 12 -> viewAuditLogs();
                case 13 -> handleAdminChangePassword();
                case 14 -> {
                    System.out.println("Admin logged out.");
                    loggedInAdmin = null;
                    running = false;
                }
                default -> System.out.println("Invalid option.");
            }
        }
    }

    private void viewAllPlans() {
        List<TelecomPlan> plans = planService.getAllActivePlans();
        System.out.println("\n┌─── ALL ACTIVE TELECOM PLANS ────────┐");
        System.out.printf("  %-4s %-10s %-16s %-8s %6s %10s ₹%-8s %s%n",
                "ID", "Code", "Name", "Type", "Data", "Voice", "Price", "Status");
        System.out.println("  " + "-".repeat(85));
        for (TelecomPlan p : plans) {
            System.out.printf("  %-4d %-10s %-16s %-8s %4dGB %10s ₹%-8.2f %s%n",
                    p.getPlanId(), p.getPlanCode(), p.getPlanName(), p.getPlanType(),
                    p.getDataAllowanceGB(), p.getVoiceDisplay(), p.getMonthlyRental(), p.getStatus());
        }
        System.out.println("└──────────────────────────────────────┘");
    }

    private void addNewPlan() {
        System.out.println("\n--- Add New Telecom Plan ---");
        System.out.print("Plan Code (e.g. PLAN-105): ");
        String code = scanner.nextLine().trim();
        System.out.print("Plan Name: ");
        String name = scanner.nextLine().trim();
        System.out.print("Plan Type (PREPAID / POSTPAID): ");
        String type = scanner.nextLine().trim().toUpperCase();
        System.out.print("Monthly Rental (₹): ");
        double rental = readDouble();
        System.out.print("Data Allowance (GB): ");
        int data = readInt();
        System.out.print("Voice Minutes (-1 for Unlimited): ");
        int voice = readInt();
        System.out.print("SMS Allowance: ");
        int sms = readInt();
        System.out.print("Validity (Days): ");
        int validity = readInt();
        System.out.print("International Roaming (true/false): ");
        boolean roaming = Boolean.parseBoolean(scanner.nextLine().trim());

        try {
            TelecomPlan plan = new TelecomPlan();
            plan.setPlanCode(code);
            plan.setPlanName(name);
            plan.setPlanType(SubscriptionType.valueOf(type));
            plan.setMonthlyRental(rental);
            plan.setDataAllowanceGB(data);
            plan.setVoiceMinutes(voice);
            plan.setSmsAllowance(sms);
            plan.setValidityDays(validity);
            plan.setInternationalRoaming(roaming);
            plan.setStatus("ACTIVE");

            new com.amdocs.telecom.dao.impl.PlanDAOImpl().save(plan);
            System.out.println("✅ Plan created: " + plan.getPlanCode() + " (ID: " + plan.getPlanId() + ")");
        } catch (Exception e) {
            System.out.println("❌ Error: " + e.getMessage());
        }
    }

    private void togglePlanStatus() {
        viewAllPlans();
        System.out.print("Enter Plan ID: ");
        int planId = readInt();
        System.out.print("New Status (ACTIVE / INACTIVE): ");
        String status = scanner.nextLine().trim().toUpperCase();
        try {
            new com.amdocs.telecom.dao.impl.PlanDAOImpl().updateStatus(planId, status);
            System.out.println("✅ Plan status updated to: " + status);
        } catch (Exception e) {
            System.out.println("❌ Error: " + e.getMessage());
        }
    }

    private void viewAllCustomers() {
        List<Customer> customers = customerService.getAllCustomers();
        System.out.println("\n┌─── ALL CUSTOMERS ───────────────────┐");
        System.out.printf("  %-12s %-20s %-25s %-15s %s%n", "Cust No", "Name", "Email", "City", "Status");
        System.out.println("  " + "-".repeat(85));
        for (Customer c : customers) {
            System.out.printf("  %-12s %-20s %-25s %-15s %s%n",
                    c.getCustomerNumber(), c.getFullName(), c.getEmail(), c.getCity(), c.getAccountStatus());
        }
        System.out.println("  Total customers: " + customers.size());
        System.out.println("└──────────────────────────────────────┘");
    }

    private void viewAllSubscriptions() {
        try {
            List<MobileSubscription> subs = new com.amdocs.telecom.dao.impl.SubscriptionDAOImpl().findAll();
            System.out.println("\n┌─── ALL SUBSCRIPTIONS ───────────────┐");
            for (MobileSubscription s : subs) {
                System.out.println("  " + s);
            }
            System.out.println("  Total: " + subs.size());
            System.out.println("└──────────────────────────────────────┘");
        } catch (SQLException e) {
            System.out.println("❌ Error: " + e.getMessage());
        }
    }

    private void generateBillingCycle() {
        System.out.println("\n[Admin] Triggering billing cycle...");
        BillingScheduler scheduler = new BillingScheduler();
        scheduler.triggerNow();
        scheduler.shutdown();
    }

    private void viewUnpaidBills() {
        List<Bill> unpaid = billingService.getUnpaidBills();
        System.out.println("\n┌─── UNPAID / OVERDUE BILLS ──────────┐");
        if (unpaid.isEmpty()) {
            System.out.println("  All bills are paid!");
        } else {
            for (Bill b : unpaid) {
                System.out.println("  " + b);
            }
        }
        System.out.println("└──────────────────────────────────────┘");
    }

    private void scanOverdueAccounts() {
        AccountMonitor monitor = new AccountMonitor();
        monitor.scanOverdueAccounts();
        System.out.print("Suspend accounts overdue > 30 days? (yes/no): ");
        String ans = scanner.nextLine().trim().toLowerCase();
        if ("yes".equals(ans)) {
            monitor.suspendDelinquentAccounts(30);
        }
        monitor.shutdown();
    }

    private void processBulkUsage() {
        System.out.print("Enter Subscription ID for bulk usage: ");
        int subId = readInt();
        System.out.print("How many sample usage records to generate? ");
        int count = readInt();

        List<UsageRecord> records = UsageProcessor.generateSampleUsageRecords(subId, count);
        UsageProcessor processor = new UsageProcessor();
        processor.processBulkUsage(records, Math.max(count / 4, 10));

        // Wait a moment for threads to finish
        try { Thread.sleep(3000); } catch (InterruptedException ignored) {}
        processor.shutdown();
    }

    private void showRevenueReports() {
        System.out.println("\n┌─── REVENUE REPORTS ─────────────────┐");

        Map<String, DoubleSummaryStatistics> revenue = reportService.getRevenueSummaryByPlan();
        if (revenue.isEmpty()) {
            System.out.println("  No revenue data available yet.");
        } else {
            System.out.printf("  %-12s %8s %12s %12s %12s%n", "Month", "Count", "Sum(₹)", "Avg(₹)", "Max(₹)");
            System.out.println("  " + "-".repeat(60));
            revenue.forEach((month, stats) ->
                    System.out.printf("  %-12s %8d %12.2f %12.2f %12.2f%n",
                            month, stats.getCount(), stats.getSum(), stats.getAverage(), stats.getMax()));
        }

        double avgPerCustomer = reportService.getAverageMonthlyRevenuePerCustomer();
        System.out.printf("%n  Average Monthly Revenue per Customer: ₹%.2f%n", avgPerCustomer);
        System.out.println("└──────────────────────────────────────┘");

        if (!revenue.isEmpty()) {
            System.out.print("  Export revenue report to CSV? (Y/N): ");
            String ans = scanner.nextLine().trim();
            if ("Y".equalsIgnoreCase(ans)) {
                try {
                    String path = reportGenerator.exportRevenueSummaryToCsv(revenue, "revenue_summary.csv");
                    System.out.println("  ✅ Revenue report exported to: " + path);
                } catch (Exception e) {
                    System.out.println("  ❌ Export failed: " + e.getMessage());
                }
            }
        }
    }

    private void showCustomerDistribution() {
        System.out.println("\n┌─── CUSTOMER DISTRIBUTION BY CITY ───┐");
        Map<String, List<Customer>> cityMap = reportService.getCustomersByCity();
        if (cityMap.isEmpty()) {
            System.out.println("  No customer data.");
        } else {
            cityMap.forEach((city, customers) -> {
                System.out.println("  📍 " + city + " (" + customers.size() + " customers):");
                customers.forEach(c -> System.out.println("      → " + c.getFullName() + " [" + c.getCustomerNumber() + "]"));
            });

            System.out.print("\n  Export full customer list to CSV? (Y/N): ");
            String ans = scanner.nextLine().trim();
            if ("Y".equalsIgnoreCase(ans)) {
                try {
                    List<Customer> allCustomers = customerService.getAllCustomers();
                    String path = reportGenerator.exportCustomersToCsv(allCustomers, "customers_report.csv");
                    System.out.println("  ✅ Customer list exported to: " + path);
                } catch (Exception e) {
                    System.out.println("  ❌ Export failed: " + e.getMessage());
                }
            }
        }
        System.out.println("└──────────────────────────────────────┘");
    }

    private void viewAuditLogs() {
        try {
            List<AuditLog> logs = auditDAO.getAuditLogs();
            System.out.println("\n┌─── AUDIT LOGS ──────────────────────┐");
            if (logs.isEmpty()) {
                System.out.println("  No audit records.");
            } else {
                for (AuditLog log : logs) {
                    System.out.println("  " + log);
                }
            }
            System.out.println("└──────────────────────────────────────┘");
        } catch (Exception e) {
            System.out.println("❌ Error: " + e.getMessage());
        }
    }

    private void handleAdminForgotPassword() {
        System.out.println("\n--- Admin Forgot Password (OTP Recovery) ---");
        System.out.print("Enter your Admin Username (or 'cancel'): ");
        String username = scanner.nextLine().trim();
        if ("cancel".equalsIgnoreCase(username) || username.isEmpty()) return;

        try {
            Optional<Administrator> opt = adminDAO.findByUsername(username);
            if (!opt.isPresent()) {
                System.out.println("❌ Admin username not found.");
                return;
            }

            Administrator admin = opt.get();
            String otp = com.amdocs.telecom.security.OTPService.generateOtp(username);
            System.out.println("📧 OTP sent to registered email [" + admin.getEmail() + "] (simulated): " + otp);

            System.out.print("Enter OTP: ");
            String enteredOtp = scanner.nextLine().trim();
            if (!com.amdocs.telecom.security.OTPService.verifyOtp(username, enteredOtp)) {
                System.out.println("❌ Invalid or expired OTP.");
                return;
            }

            // Read new password with validation
            for (int attempt = 1; attempt <= 3; attempt++) {
                System.out.print("Enter new password (or 'cancel'): ");
                String newPass = scanner.nextLine().trim();
                if ("cancel".equalsIgnoreCase(newPass)) return;

                List<String> missing = PasswordUtil.getPasswordMissingRequirements(newPass);
                if (!missing.isEmpty()) {
                    System.out.println("⚠️ Password does not meet requirements:");
                    missing.forEach(m -> System.out.println("   • " + m));
                    if (attempt == 3) {
                        System.out.println("❌ Maximum attempts reached. Password reset cancelled.");
                        return;
                    }
                    continue;
                }

                String hashed = PasswordUtil.hashPassword(newPass);
                boolean updated = adminDAO.updatePassword(admin.getAdminId(), hashed);
                if (updated) {
                    System.out.println("✅ Admin password reset successfully! Please login with your new password.");
                    // Audit log
                    AuditLog audit = new AuditLog();
                    audit.setEntityName("ADMIN");
                    audit.setEntityId(String.valueOf(admin.getAdminId()));
                    audit.setAction("PASSWORD_RESET");
                    audit.setDetails("Admin password reset via OTP recovery");
                    audit.setPerformedBy(username);
                    auditDAO.logAudit(audit);
                } else {
                    System.out.println("❌ Failed to update password in database.");
                }
                return;
            }
        } catch (SQLException e) {
            System.out.println("❌ Database error: " + e.getMessage());
        }
    }

    private void handleAdminChangePassword() {
        System.out.println("\n--- Change Administrator Password ---");
        System.out.print("Enter Current Password (or 'cancel'): ");
        String currentPass = scanner.nextLine().trim();
        if ("cancel".equalsIgnoreCase(currentPass) || currentPass.isEmpty()) return;

        if (!PasswordUtil.verifyPassword(currentPass, loggedInAdmin.getPasswordHash())) {
            System.out.println("❌ Current password does not match.");
            return;
        }

        for (int attempt = 1; attempt <= 3; attempt++) {
            System.out.print("Enter New Password (or 'cancel'): ");
            String newPass = scanner.nextLine().trim();
            if ("cancel".equalsIgnoreCase(newPass)) return;

            if (currentPass.equals(newPass)) {
                System.out.println("⚠️ New password cannot be the same as current password.");
                continue;
            }

            List<String> missing = PasswordUtil.getPasswordMissingRequirements(newPass);
            if (!missing.isEmpty()) {
                System.out.println("⚠️ Password does not meet requirements:");
                missing.forEach(m -> System.out.println("   • " + m));
                if (attempt == 3) {
                    System.out.println("❌ Maximum attempts reached. Password change cancelled.");
                    return;
                }
                continue;
            }

            System.out.print("Confirm New Password: ");
            String confirmPass = scanner.nextLine().trim();
            if (!newPass.equals(confirmPass)) {
                System.out.println("❌ Passwords do not match. Please try again.");
                continue;
            }

            try {
                String hashed = PasswordUtil.hashPassword(newPass);
                boolean updated = adminDAO.updatePassword(loggedInAdmin.getAdminId(), hashed);
                if (updated) {
                    loggedInAdmin.setPasswordHash(hashed);
                    System.out.println("✅ Administrator password changed successfully!");

                    // Audit log
                    AuditLog audit = new AuditLog();
                    audit.setEntityName("ADMIN");
                    audit.setEntityId(String.valueOf(loggedInAdmin.getAdminId()));
                    audit.setAction("PASSWORD_CHANGE");
                    audit.setDetails("Admin changed password while logged in");
                    audit.setPerformedBy(loggedInAdmin.getUsername());
                    auditDAO.logAudit(audit);
                } else {
                    System.out.println("❌ Failed to update password in database.");
                }
                return;
            } catch (SQLException e) {
                System.out.println("❌ Database error: " + e.getMessage());
                return;
            }
        }
    }

    private int readInt() {
        try {
            if (!scanner.hasNextLine()) return -1;
            String line = scanner.nextLine().trim();
            return Integer.parseInt(line);
        } catch (Exception e) {
            return -1;
        }
    }

    private double readDouble() {
        try {
            if (!scanner.hasNextLine()) return 0.0;
            String line = scanner.nextLine().trim();
            return Double.parseDouble(line);
        } catch (Exception e) {
            return 0.0;
        }
    }

    private String padRight(String s, int n) {
        if (s == null) s = "";
        return String.format("%-" + n + "s", s.length() > n ? s.substring(0, n) : s);
    }
}
