package com.amdocs.telecom.controller;

import com.amdocs.telecom.util.ConsoleMenu;

import com.amdocs.telecom.dao.AdminDAO;
import com.amdocs.telecom.dao.AuditAndNotificationDAO;
import com.amdocs.telecom.dao.impl.AdminDAOImpl;
import com.amdocs.telecom.dao.impl.AuditAndNotificationDAOImpl;
import com.amdocs.telecom.dao.impl.CustomerDAOImpl;
import com.amdocs.telecom.model.*;
import com.amdocs.telecom.scheduler.AccountMonitor;
import com.amdocs.telecom.scheduler.BillingScheduler;
import com.amdocs.telecom.scheduler.ConsoleActivityMonitor;
import com.amdocs.telecom.scheduler.UsageProcessor;
import com.amdocs.telecom.security.CaptchaGenerator;
import com.amdocs.telecom.security.PasswordUtil;
import com.amdocs.telecom.service.*;
import com.amdocs.telecom.service.impl.*;

import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.DoubleSummaryStatistics;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;

public class AdminController {

    private final AdminDAO adminDAO;
    private final PlanService planService;
    private final CustomerService customerService;
    private final BillingService billingService;
    private final ReportService reportService;
    private final ComplaintService complaintService;
    private final AuditAndNotificationDAO auditDAO;
    private final com.amdocs.telecom.report.ReportGenerator reportGenerator;
    private final Scanner scanner;
    private final ConsoleActivityMonitor activityMonitor;
    private Administrator loggedInAdmin;

    public AdminController(Scanner scanner) {
        this.scanner = scanner;
        this.adminDAO = new AdminDAOImpl();
        this.planService = new PlanServiceImpl();
        this.customerService = new CustomerServiceImpl();
        this.billingService = new BillingServiceImpl();
        this.reportService = new ReportServiceImpl();
        this.complaintService = new ComplaintServiceImpl();
        this.auditDAO = new AuditAndNotificationDAOImpl();
        this.reportGenerator = new com.amdocs.telecom.report.ReportGenerator();
        this.activityMonitor = new ConsoleActivityMonitor();
    }

    public void showLoginMenu() {
        boolean inPortal = true;
        while (inPortal) {
            ConsoleMenu.show("ADMINISTRATOR LOGIN PORTAL", null,
                    "1. Admin Login", "2. Forgot Password", "3. Back to Main Menu");
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

        // Check the account before requesting a password.
        try {
            if (!adminDAO.findByUsername(username).isPresent()) {
                System.out.println("[Error] Admin username '" + username + "' is not registered. Please check your username.");
                return false;
            }
        } catch (SQLException e) {
            System.out.println("[Error] Database error while verifying username: " + e.getMessage());
            return false;
        }

        System.out.print("Admin Password: ");
        String password = scanner.nextLine().trim();
        if ("cancel".equalsIgnoreCase(password)) return false;

        // Allow three CAPTCHA attempts.
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
                System.out.println("[Warning] CAPTCHA did not match. Generating new CAPTCHA (Attempt " + (cAttempt + 1) + " of 3):");
            } else {
                System.out.println("[Error] CAPTCHA verification failed. Returning to menu.");
                return false;
            }
        }

        try {
            Optional<Administrator> opt = adminDAO.findByUsername(username);
            if(opt.isPresent() && "LOCKED".equals(opt.get().getAccountStatus()) && com.amdocs.telecom.security.LoginSecurity.unlockIfExpired(username,"ADMIN"))
                opt=adminDAO.findByUsername(username);
            if (opt.isPresent() && "LOCKED".equals(opt.get().getAccountStatus())) {
                System.out.println("[Error] Admin account is locked. Use password recovery to unlock it.");
                return false;
            }
            if(opt.isPresent() && !"ACTIVE".equals(opt.get().getAccountStatus())) { System.out.println("Administrator account is inactive."); return false; }
            if (opt.isPresent() && PasswordUtil.verifyPassword(password, opt.get().getPasswordHash())) {
                loggedInAdmin = opt.get();
                adminDAO.updateAccountStatus(loggedInAdmin.getAdminId(), "ACTIVE");
                
                // Show the previous login before recording this one.
                Optional<String> lastLogin = new CustomerDAOImpl().getLastLoginTimestamp(username,"ADMIN");
                System.out.println("\n[OK] Admin login successful! Welcome, " + loggedInAdmin.getFullName());
                lastLogin.ifPresent(ts -> System.out.println("   Last Login Timestamp: " + ts));

                new CustomerDAOImpl().logLoginAttempt(username, "ADMIN", "127.0.0.1", "SUCCESS");
                return true;
            } else {
                new CustomerDAOImpl().logLoginAttempt(username, "ADMIN", "127.0.0.1", "FAILED");
                if (opt.isPresent()) {
                    int failures = new CustomerDAOImpl().getRecentFailedLoginAttempts(username,"ADMIN",30);
                    if (failures >= 3) {
                        adminDAO.updateAccountStatus(opt.get().getAdminId(), "LOCKED");
                        com.amdocs.telecom.security.LoginSecurity.lock(username,"ADMIN");
                        System.out.println("[Error] Maximum 3 failed attempts reached. Admin account locked.");
                        return false;
                    }
                }
                System.out.println("[Error] Invalid admin credentials.");
                return false;
            }
        } catch (SQLException e) {
            System.out.println("[Error] Database error: " + e.getMessage());
            return false;
        }
    }

    private void showAdminDashboard() {
        boolean running = true;
        while (running) {
            ConsoleMenu.showCompact("ADMINISTRATOR DASHBOARD", "Admin: " + loggedInAdmin.getFullName(),
                    "PLANS", "1. View Active Plans", "2. Add Plan", "3. Set Plan Status", "4. Configure Plan Rules",
                    "", "CUSTOMERS AND SUBSCRIPTIONS", "5. View Customers", "6. Edit Customer / Set Status",
                    "7. View Subscriptions", "8. Set Subscription Status", "9. View SIM Inventory", "10. Add SIM / Set Status",
                    "", "BILLING AND USAGE", "11. Generate Monthly Bills", "12. View Unpaid Bills", "13. View Payments",
                    "14. Scan Overdue Accounts", "15. Process Bulk Usage", "16. View Monthly Usage",
                    "", "REPORTS AND SUPPORT", "17. Revenue Reports", "18. Customers by City",
                    "19. Manage Complaints", "20. Complaint Analytics", "21. Audit Logs",
                    "", "ACCOUNT", "22. Change My Password", "23. Live Activity Monitor", "0. Logout");
            System.out.print("Select option: ");
            if (!scanner.hasNextLine()) break;

            int choice = readInt();
            try { switch (choice) {
                case 1 -> viewAllPlans();
                case 2 -> addNewPlan();
                case 3 -> togglePlanStatus();
                case 4 -> configurePlanRules();
                case 5 -> viewAllCustomers();
                case 6 -> manageCustomer();
                case 7 -> viewAllSubscriptions();
                case 8 -> manageSubscription();
                case 9 -> viewSimInventory();
                case 10 -> manageSIM();
                case 11 -> generateBillingCycle();
                case 12 -> viewUnpaidBills();
                case 13 -> viewAllPayments();
                case 14 -> scanOverdueAccounts();
                case 15 -> processBulkUsage();
                case 16 -> viewMonthlyUsage();
                case 17 -> showRevenueReports();
                case 18 -> showCustomerDistribution();
                case 19 -> manageComplaints();
                case 20 -> showComplaintHotspotsAndAnalytics();
                case 21 -> viewAuditLogs();
                case 22 -> handleAdminChangePassword();
                case 23 -> toggleLiveActivityMonitor();
                case 0 -> {
                    try { new CustomerDAOImpl().logLoginAttempt(loggedInAdmin.getUsername(),"ADMIN","127.0.0.1","LOGOUT"); } catch(SQLException e) { System.out.println("Could not record logout."); }
                    System.out.println("Admin logged out.");
                    activityMonitor.stop();
                    loggedInAdmin = null;
                    running = false;
                }
                default -> System.out.println("Invalid option.");
            } } catch(RuntimeException e) { System.out.println("Could not complete this action: " + e.getMessage()); }
        }
    }


    private final AdministrationService management=new AdministrationServiceImpl();
    private String value(String label,String current) {
        System.out.print(label+" ["+current+"]: "); String value=scanner.nextLine().trim(); return value.isEmpty()?current:value;
    }
    private void manageCustomer() {
        try {
            System.out.print("Customer ID (0 to return): "); int id=readInt(); if(id<=0) return;
            Customer c=customerService.getCustomerById(id);
            ConsoleMenu.show("MANAGE CUSTOMER", null, "1. Edit Profile", "2. Set Status", "0. Back");
            System.out.print("Customer action: "); int option=readInt();
            if(option==1) {
                c.setFirstName(value("First name",c.getFirstName())); c.setLastName(value("Last name",c.getLastName()));
                c.setAddress(value("Address",c.getAddress())); c.setCity(value("City",c.getCity())); c.setCountry(value("Country",c.getCountry()));
                management.updateCustomer(loggedInAdmin,c);
            } else if(option==2) {
                System.out.print("Status (ACTIVE/SUSPENDED/INACTIVE): "); management.setCustomerStatus(loggedInAdmin,id,scanner.nextLine().trim().toUpperCase());
            } else return;
            System.out.println("Customer updated.");
        } catch(Exception e) { System.out.println(e.getMessage()); }
    }
    private void manageSIM() {
        try {
            ConsoleMenu.show("MANAGE SIM", null, "1. Add SIM", "2. Set Status", "0. Back");
            System.out.print("SIM action: "); int option=readInt();
            if(option==1) {
                SIMCard sim=new SIMCard();
                System.out.print("SIM number: "); sim.setSimNumber(scanner.nextLine().trim());
                System.out.print("IMSI: "); sim.setImsi(scanner.nextLine().trim());
                System.out.print("SIM type (ESIM/PHYSICAL_SIM): "); sim.setSimType(SimType.valueOf(scanner.nextLine().trim().toUpperCase()));
                management.addSIM(loggedInAdmin,sim); System.out.println("SIM added.");
            } else if(option==2) {
                System.out.print("SIM ID: "); int id=readInt(); System.out.print("Status (ACTIVE/INACTIVE): ");
                management.setSIMStatus(loggedInAdmin,id,scanner.nextLine().trim().toUpperCase()); System.out.println("SIM updated.");
            }
        } catch(Exception e) { System.out.println(e.getMessage()); }
    }
    private void manageSubscription() {
        try {
            System.out.print("Subscription ID (0 to return): "); int id=readInt(); if(id<=0) return;
            System.out.print("Status (ACTIVE/SUSPENDED/INACTIVE): "); management.setSubscriptionStatus(loggedInAdmin,id,scanner.nextLine().trim().toUpperCase());
            System.out.println("Subscription updated.");
        } catch(Exception e) { System.out.println(e.getMessage()); }
    }
    private void viewMonthlyUsage() {
        try {
            System.out.print("Subscription ID: "); int id=readInt(); System.out.print("Month (yyyy-MM): ");
            management.getUsage(loggedInAdmin,id,scanner.nextLine().trim()).forEach(System.out::println);
        } catch(Exception e) { System.out.println(e.getMessage()); }
    }
    private void configurePlanRules() {
        try {
            System.out.print("Plan ID: "); int id=readInt(); System.out.print("Allow prepaid/postpaid changes? (Y/N): "); boolean allow=scanner.nextLine().trim().equalsIgnoreCase("Y");
            System.out.print("Minimum days before another change: "); management.setPlanRules(loggedInAdmin,id,allow,readInt()); System.out.println("Plan rules updated.");
        } catch(Exception e) { System.out.println(e.getMessage()); }
    }

    private void viewAllPlans() {
        List<TelecomPlan> plans = planService.getAllActivePlans();
        System.out.println("\nALL ACTIVE TELECOM PLANS");
        System.out.printf("  %-4s %-10s %-16s %-8s %6s %10s Rs %-8s %s%n",
                "ID", "Code", "Name", "Type", "Data", "Voice", "Price", "Status");
        System.out.println("  " + "-".repeat(85));
        for (TelecomPlan p : plans) {
            System.out.printf("  %-4d %-10s %-16s %-8s %4dGB %10s Rs %-8.2f %s%n",
                    p.getPlanId(), p.getPlanCode(), p.getPlanName(), p.getPlanType(),
                    p.getDataAllowanceGB(), p.getVoiceDisplay(), p.getMonthlyRental(), p.getStatus());
        }
        System.out.println("------------------------------------------------------------");
    }

    private void addNewPlan() {
        System.out.println("\n--- Add New Telecom Plan ---");
        System.out.print("Plan Code (e.g. PLAN-105): ");
        String code = scanner.nextLine().trim();
        System.out.print("Plan Name: ");
        String name = scanner.nextLine().trim();
        System.out.print("Plan Type (PREPAID / POSTPAID): ");
        String type = scanner.nextLine().trim().toUpperCase();
        System.out.print("Monthly Rental (Rs ): ");
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
            System.out.println("[OK] Plan created: " + plan.getPlanCode() + " (ID: " + plan.getPlanId() + ")");
        } catch (Exception e) {
            System.out.println("[Error] Error: " + e.getMessage());
        }
    }

    private void togglePlanStatus() {
        viewAllPlans();
        System.out.print("Enter Plan ID: ");
        int planId = readInt();
        System.out.print("New Status (ACTIVE / INACTIVE): ");
        String status = scanner.nextLine().trim().toUpperCase();
        try {
            boolean updated = new com.amdocs.telecom.dao.impl.PlanDAOImpl().updateStatus(planId, status);
            if (updated) {
                System.out.println("[OK] Plan status updated to: " + status);
            } else {
                System.out.println("[Error] Plan not found.");
            }
        } catch (Exception e) {
            System.out.println("[Error] Error: " + e.getMessage());
        }
    }

    private void viewAllCustomers() {
        List<Customer> customers = customerService.getAllCustomers();
        System.out.println("\nALL CUSTOMERS");
        System.out.printf("  %-12s %-20s %-25s %-15s %s%n", "Cust No", "Name", "Email", "City", "Status");
        System.out.println("  " + "-".repeat(85));
        for (Customer c : customers) {
            System.out.printf("  %-12s %-20s %-25s %-15s %s%n",
                    c.getCustomerNumber(), c.getFullName(), c.getEmail(), c.getCity(), c.getAccountStatus());
        }
        System.out.println("  Total customers: " + customers.size());
        System.out.println("------------------------------------------------------------");
    }

    private void viewAllSubscriptions() {
        try {
            List<MobileSubscription> subs = new com.amdocs.telecom.dao.impl.SubscriptionDAOImpl().findAll();
            System.out.println("\nALL SUBSCRIPTIONS");
            for (MobileSubscription s : subs) {
                System.out.println("  " + s);
            }
            System.out.println("  Total: " + subs.size());
            System.out.println("------------------------------------------------------------");
        } catch (SQLException e) {
            System.out.println("[Error] Error: " + e.getMessage());
        }
    }

    private void viewAllPayments() {
        try {
            List<Payment> payments = new com.amdocs.telecom.dao.impl.PaymentDAOImpl().findAll();
            System.out.println("\nALL PAYMENTS");
            if (payments.isEmpty()) {
                System.out.println("  No payments found.");
            } else {
                for (Payment payment : payments) {
                    System.out.printf("  #%d | %s | Bill: %d | Customer: %d | Rs %.2f | %s | %s%n",
                            payment.getPaymentId(), payment.getTransactionReference(), payment.getBillId(),
                            payment.getCustomerId(), payment.getAmount(), payment.getPaymentMode(),
                            payment.getPaymentStatus());
                }
            }
            System.out.println("------------------------------------------------------------");
        } catch (SQLException e) {
            System.out.println("[Error] Error: " + e.getMessage());
        }
    }

    private void viewSimInventory() {
        try {
            List<SIMCard> sims = new com.amdocs.telecom.dao.impl.SubscriptionDAOImpl().findAllSIMs();
            System.out.println("\nSIM INVENTORY");
            for (SIMCard sim : sims) {
                System.out.printf("  #%d | %s | Type: %s | IMSI: %s | Status: %s%n",
                        sim.getSimId(), sim.getSimNumber(), sim.getSimType(), sim.getImsi(), sim.getStatus());
            }
            System.out.println("  Total SIMs: " + sims.size());
            System.out.println("------------------------------------------------------------");
        } catch (SQLException e) {
            System.out.println("[Error] Error: " + e.getMessage());
        }
    }

    private void toggleLiveActivityMonitor() {
        activityMonitor.toggle();
    }

    private void generateBillingCycle() {
        System.out.println("\n[Admin] Triggering billing cycle...");
        BillingScheduler scheduler = new BillingScheduler();
        scheduler.triggerNow();
        scheduler.shutdown();
    }

    private void viewUnpaidBills() {
        List<Bill> unpaid = billingService.getUnpaidBills();
        System.out.println("\nUNPAID / OVERDUE BILLS");
        if (unpaid.isEmpty()) {
            System.out.println("  All bills are paid!");
        } else {
            for (Bill b : unpaid) {
                System.out.println("  " + b);
            }
        }
        System.out.println("------------------------------------------------------------");
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

        processor.shutdown();
    }

    private void showRevenueReports() {
        System.out.println("\nREVENUE REPORTS & ANALYTICS");

        Map<String, DoubleSummaryStatistics> revenue = reportService.getRevenueSummaryByPlan();
        if (revenue.isEmpty()) {
            System.out.println("  No revenue data available yet.");
        } else {
            System.out.printf("  %-12s %8s %12s %12s %12s%n", "Month", "Count", "Sum(Rs )", "Avg(Rs )", "Max(Rs )");
            System.out.println("  " + "-".repeat(60));
            revenue.forEach((month, stats) ->
                    System.out.printf("  %-12s %8d %12.2f %12.2f %12.2f%n",
                            month, stats.getCount(), stats.getSum(), stats.getAverage(), stats.getMax()));
        }

        double avgPerCustomer = reportService.getAverageMonthlyRevenuePerCustomer();
        System.out.printf("%n  Average Monthly Revenue per Customer: Rs %.2f%n", avgPerCustomer);

        // Summarize the most subscribed plans with streams.
        List<Map<String, Object>> topPlans = reportService.getMostSubscribedPlans();
        if (!topPlans.isEmpty()) {
            System.out.println("\n   MOST SUBSCRIBED PLANS:");
            System.out.printf("  %-10s %-20s %-12s %s%n", "Code", "Name", "Monthly(Rs )", "Subscribers");
            System.out.println("  " + "-".repeat(55));
            for (Map<String, Object> p : topPlans) {
                System.out.printf("  %-10s %-20s Rs %-11.2f %d%n",
                        p.get("planCode"),
                        String.valueOf(p.get("planName")),
                        (Double) p.get("monthlyRental"),
                        (Long) p.get("subscriberCount"));
            }
        }

        // Summarize usage by type with streams.
        Map<String, Double> usageByType = reportService.getOverallUsageByType();
        if (!usageByType.isEmpty()) {
            System.out.println("\n   TOTAL TELECOM USAGE BY TYPE:");
            usageByType.forEach((type, qty) -> System.out.printf("    %-10s : %.2f %s%n", type, qty,
                    com.amdocs.telecom.util.UsageUnits.unit(UsageType.valueOf(type))));
        }

        System.out.println("\n  HIGHEST DATA USAGE CUSTOMERS:");
        List<Customer> highestUsage = reportService.getHighestConsumingCustomers(UsageType.DATA);
        if (highestUsage.isEmpty()) System.out.println("  No data usage recorded.");
        highestUsage.forEach(c -> System.out.printf("  %s | %s%n", c.getCustomerNumber(), c.getFullName()));

        System.out.println("\n  CUSTOMERS WITH UNPAID BILLS:");
        List<Customer> unpaidCustomers = reportService.getCustomersWithUnpaidBills();
        if (unpaidCustomers.isEmpty()) System.out.println("  No customers with unpaid bills.");
        unpaidCustomers.forEach(c -> System.out.printf("  %s | %s%n", c.getCustomerNumber(), c.getFullName()));

        System.out.println("------------------------------------------------------------");

        if (!revenue.isEmpty()) {
            System.out.print("Export revenue report to CSV (Y/N): ");
            String ans = scanner.nextLine().trim();
            if ("Y".equalsIgnoreCase(ans)) {
                try {
                    String path = reportGenerator.exportRevenueSummaryToCsv(revenue, "revenue_summary.csv");
                    System.out.println("  [OK] Revenue report exported to: " + path);
                } catch (Exception e) {
                    System.out.println("  [Error] Export failed: " + e.getMessage());
                }
            }
        }
    }

    private void showCustomerDistribution() {
        System.out.println("\nCUSTOMER DISTRIBUTION BY CITY");
        Map<String, List<Customer>> cityMap = reportService.getCustomersByCity();
        if (cityMap.isEmpty()) {
            System.out.println("  No customer data.");
        } else {
            cityMap.forEach((city, customers) -> {
                System.out.println("   " + city + " (" + customers.size() + " customers):");
                customers.forEach(c -> System.out.println("      → " + c.getFullName() + " [" + c.getCustomerNumber() + "]"));
            });

            System.out.print("\n  Export full customer list to CSV (Y/N): ");
            String ans = scanner.nextLine().trim();
            if ("Y".equalsIgnoreCase(ans)) {
                try {
                    List<Customer> allCustomers = customerService.getAllCustomers();
                    String path = reportGenerator.exportCustomersToCsv(allCustomers, "customers_report.csv");
                    System.out.println("  [OK] Customer list exported to: " + path);
                } catch (Exception e) {
                    System.out.println("  [Error] Export failed: " + e.getMessage());
                }
            }
        }
        System.out.println("------------------------------------------------------------");
    }

    private void manageComplaints() {
        boolean inMenu = true;
        while (inMenu) {
            ConsoleMenu.show("CUSTOMER COMPLAINT MANAGEMENT", null,
                    "1. View All Complaints", "2. View Open Complaints", "3. View Complaint Details",
                    "4. Resolve / Update a Complaint", "5. Back to Admin Dashboard");
            System.out.print("Select option: ");
            if (!scanner.hasNextLine()) break;

            int choice = readInt();
            switch (choice) {
                case 1 -> listComplaints(null);
                case 2 -> listComplaints("OPEN");
                case 3 -> viewSingleComplaintDetails();
                case 4 -> handleResolveComplaint();
                case 5 -> inMenu = false;
                default -> System.out.println("Invalid option. Please choose 1 to 5.");
            }
        }
    }

    private void listComplaints(String filterStatus) {
        List<Complaint> all = complaintService.getAllComplaints();
        List<Complaint> list = (filterStatus != null) ?
                all.stream().filter(c -> filterStatus.equalsIgnoreCase(c.getStatus())).toList() : all;

        String title = (filterStatus != null) ? "PENDING / OPEN CUSTOMER COMPLAINTS" : "ALL CUSTOMER COMPLAINTS";
        System.out.println("\n------------------------------------------------------------" + title + " " + "------------------------------------------------------------".repeat(Math.max(0, 85 - title.length())) + "------------------------------------------------------------");
        if (list.isEmpty()) {
            System.out.println("  No complaints found matching criteria.");
            System.out.println("------------------------------------------------------------" + "------------------------------------------------------------".repeat(90) + "------------------------------------------------------------");
            return;
        }

        System.out.printf("  %-4s %-12s %-18s %-12s %-10s %-8s %-11s %s%n",
                "ID", "Ticket No", "Customer Name", "City", "Category", "Priority", "Status", "Date Lodged");
        System.out.println("  " + "-".repeat(90));

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        for (Complaint c : list) {
            String dateStr = c.getCreatedDate() != null ? c.getCreatedDate().format(dtf) : "N/A";
            String custName = c.getCustomerName() != null ? c.getCustomerName() : "Cust #" + c.getCustomerId();
            String city = c.getCustomerCity() != null ? c.getCustomerCity() : "-";
            System.out.printf("  %-4d %-12s %-18s %-12s %-10s %-8s %-11s %s%n",
                    c.getComplaintId(),
                    c.getComplaintNumber(),
                    custName.length() > 18 ? custName.substring(0, 18) : custName,
                    city.length() > 12 ? city.substring(0, 12) : city,
                    c.getCategory(),
                    c.getPriority(),
                    c.getStatus(),
                    dateStr);
        }
        System.out.println("------------------------------------------------------------" + "------------------------------------------------------------".repeat(90) + "------------------------------------------------------------");

        long openCount = all.stream().filter(c -> "OPEN".equalsIgnoreCase(c.getStatus())).count();
        long inProgCount = all.stream().filter(c -> "IN_PROGRESS".equalsIgnoreCase(c.getStatus())).count();
        long resolvedCount = all.stream().filter(c -> "RESOLVED".equalsIgnoreCase(c.getStatus()) || "CLOSED".equalsIgnoreCase(c.getStatus())).count();
        System.out.printf("  Summary: Total: %d | Pending/Open: %d | In Progress: %d | Resolved: %d%n",
                all.size(), openCount, inProgCount, resolvedCount);
    }

    private void viewSingleComplaintDetails() {
        System.out.print("\nEnter Complaint ID or Ticket Number (or 'cancel'): ");
        String query = scanner.nextLine().trim();
        if ("cancel".equalsIgnoreCase(query) || query.isEmpty()) return;

        Optional<Complaint> opt = findComplaintByIdOrNumber(query);
        if (!opt.isPresent()) {
            System.out.println("[Error] Complaint not found for: " + query);
            return;
        }

        printComplaintDetailsCard(opt.get());
    }

    private void handleResolveComplaint() {
        System.out.print("\nEnter Complaint ID or Ticket Number to Resolve (or 'cancel'): ");
        String query = scanner.nextLine().trim();
        if ("cancel".equalsIgnoreCase(query) || query.isEmpty()) return;

        Optional<Complaint> opt = findComplaintByIdOrNumber(query);
        if (!opt.isPresent()) {
            System.out.println("[Error] Complaint not found for: " + query);
            return;
        }

        Complaint cp = opt.get();
        printComplaintDetailsCard(cp);

        System.out.println("\nSelect Resolution Status:");
        System.out.println("  1. RESOLVED (Issue fixed, solution applied)");
        System.out.println("  2. IN_PROGRESS (Investigation ongoing, engineer dispatched)");
        System.out.println("  3. CLOSED (Complaint settled and closed)");
        System.out.print("Choose status (1-3, Default: 1): ");
        String sChoice = scanner.nextLine().trim();
        String newStatus = switch (sChoice) {
            case "2" -> "IN_PROGRESS";
            case "3" -> "CLOSED";
            default -> "RESOLVED";
        };

        System.out.println("Enter Solution / Resolution Action Taken (Required):");
        System.out.print("Solution: ");
        String resolution = scanner.nextLine().trim();
        if (resolution.isEmpty()) {
            System.out.println("[Error] Resolution description cannot be empty. Action cancelled.");
            return;
        }

        try {
            boolean success = complaintService.resolveComplaint(cp.getComplaintId(), newStatus, resolution, loggedInAdmin.getUsername());
            if (success) {
                System.out.println("\n[OK] Complaint [" + cp.getComplaintNumber() + "] successfully updated to " + newStatus + "!");
                System.out.println("   Solution Logged : " + resolution);
                System.out.println("   Customer Alert  : Notification automatically delivered to customer portal.");
            } else {
                System.out.println("[Error] Failed to update complaint status in database.");
            }
        } catch (Exception e) {
            System.out.println("[Error] Error: " + e.getMessage());
        }
    }

    private Optional<Complaint> findComplaintByIdOrNumber(String query) {
        try {
            int id = Integer.parseInt(query);
            Optional<Complaint> byId = complaintService.getComplaintById(id);
            if (byId.isPresent()) return byId;
        } catch (NumberFormatException ignored) {}
        return complaintService.getComplaintByNumber(query.toUpperCase());
    }

    private void printComplaintDetailsCard(Complaint cp) {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        String dateStr = cp.getCreatedDate() != null ? cp.getCreatedDate().format(dtf) : "N/A";
        String statusSymbol = switch (cp.getStatus()) {
            case "RESOLVED" -> "[OK] RESOLVED";
            case "CLOSED" -> " CLOSED";
            case "IN_PROGRESS" -> " IN_PROGRESS";
            default -> "[Pending] OPEN";
        };

        System.out.println("\n------------------------------------------------------------");
        System.out.println("COMPLAINT & RESOLUTION DOSSIER");
        System.out.println("------------------------------------------------------------");
        System.out.printf("Complaint ID    : %-53d %n", cp.getComplaintId());
        System.out.printf("Ticket Number   : %-53s %n", cp.getComplaintNumber());
        System.out.printf("Date Lodged     : %-53s %n", dateStr);
        System.out.printf("Customer Name   : %-53s %n", (cp.getCustomerName() != null ? cp.getCustomerName() : "Cust #" + cp.getCustomerId()));
        if (cp.getCustomerNumber() != null) {
            System.out.printf("Customer Number : %-53s %n", cp.getCustomerNumber());
        }
        System.out.printf("Location / City : %-53s %n", (cp.getCustomerCity() != null ? cp.getCustomerCity() : "N/A"));
        System.out.printf("Mobile Number   : %-53s %n", (cp.getMobileNumber() != null ? cp.getMobileNumber() : "N/A"));
        System.out.printf("Category        : %-53s %n", cp.getCategory());
        System.out.printf("Priority        : %-53s %n", cp.getPriority());
        System.out.printf("Current Status  : %-53s %n", statusSymbol);
        System.out.println("------------------------------------------------------------");
        System.out.println("CUSTOMER ISSUE DESCRIPTION:");
        printWrappedBoxText(cp.getDescription(), 70);
        System.out.println("------------------------------------------------------------");
        System.out.println("OFFICIAL SOLUTION & ACTION TAKEN:");
        if (cp.getResolution() != null && !cp.getResolution().trim().isEmpty()) {
            printWrappedBoxText(cp.getResolution(), 70);
        } else {
            System.out.println("[No resolution recorded yet. Use Option 4 to resolve this complaint.]");
        }
        System.out.println("------------------------------------------------------------");
    }

    private void printWrappedBoxText(String text, int maxWidth) {
        if (text == null || text.trim().isEmpty()) {
            System.out.println("(None)");
            return;
        }
        String[] words = text.split("\\s+");
        StringBuilder currentLine = new StringBuilder();
        for (String w : words) {
            if (currentLine.length() + w.length() + 1 > maxWidth) {
                System.out.printf("%-70s %n", currentLine.toString());
                currentLine.setLength(0);
            }
            if (currentLine.length() > 0) currentLine.append(" ");
            currentLine.append(w);
        }
        if (currentLine.length() > 0) {
            System.out.printf("%-70s %n", currentLine.toString());
        }
    }

    private void showComplaintHotspotsAndAnalytics() {
        System.out.println("\nCOMPLAINT HOTSPOTS & ANALYTICS");
        System.out.println("  Intelligence Report: Where Complaints Are Highest & Actionable Hotspots");
        System.out.println("  " + "-".repeat(76));

        Map<String, int[]> cityStats = complaintService.getComplaintsCountByCity();
        Map<String, int[]> catStats = complaintService.getComplaintsCountByCategory();
        List<Map<String, Object>> topCustomers = complaintService.getTopCustomersByComplaints(10);

        int grandTotal = cityStats.values().stream().mapToInt(a -> a[0]).sum();
        int totalOpen = cityStats.values().stream().mapToInt(a -> a[1]).sum();
        int totalInProg = cityStats.values().stream().mapToInt(a -> a[2]).sum();
        int totalResolved = cityStats.values().stream().mapToInt(a -> a[3]).sum();
        double resolutionRate = grandTotal > 0 ? ((double) totalResolved / grandTotal) * 100.0 : 0.0;

        System.out.printf("   Overall System Metrics:%n");
        System.out.printf("     • Total Complaints Filed : %d%n", grandTotal);
        System.out.printf("     • Pending / Open Issues  : %d%n", totalOpen);
        System.out.printf("     • In Progress Issues     : %d%n", totalInProg);
        System.out.printf("     • Resolved Issues        : %d%n", totalResolved);
        System.out.printf("     • Overall Resolution Rate: %.1f%%%n%n", resolutionRate);

        if (grandTotal == 0) {
            System.out.println("  No customer complaints recorded in the system yet.");
            System.out.println("------------------------------------------------------------");
            return;
        }

        System.out.println("   1. COMPLAINT HOTSPOTS BY CITY / LOCATION (Where complaints are highest):");
        System.out.printf("  %-4s %-16s %7s %6s %8s %8s %8s   %s%n",
                "Rank", "City / Area", "Total", "Open", "In-Prog", "Resolved", "Share %", "Hotspot Level");
        System.out.println("  " + "-".repeat(76));

        int rank = 1;
        for (Map.Entry<String, int[]> entry : cityStats.entrySet()) {
            String city = entry.getKey();
            int[] c = entry.getValue();
            double share = grandTotal > 0 ? ((double) c[0] / grandTotal) * 100.0 : 0.0;

            String hotspotBadge;
            if (share >= 35.0 || c[0] >= 5) {
                hotspotBadge = " HIGH HOTSPOT (Attention Required)";
            } else if (share >= 20.0 || c[0] >= 3) {
                hotspotBadge = "[Warning] MODERATE HOTSPOT";
            } else {
                hotspotBadge = " NORMAL";
            }

            System.out.printf("  %-4d %-16s %7d %6d %8d %8d %7.1f%%   %s%n",
                    rank++, city, c[0], c[1], c[2], c[3], share, hotspotBadge);
        }

        System.out.println("\n   2. COMPLAINT DISTRIBUTION BY PROBLEM CATEGORY:");
        System.out.printf("  %-16s %7s %6s %8s %8s %8s%n",
                "Category", "Total", "Open", "In-Prog", "Resolved", "Share %");
        System.out.println("  " + "-".repeat(60));

        for (Map.Entry<String, int[]> entry : catStats.entrySet()) {
            String cat = entry.getKey();
            int[] c = entry.getValue();
            double share = grandTotal > 0 ? ((double) c[0] / grandTotal) * 100.0 : 0.0;
            System.out.printf("  %-16s %7d %6d %8d %8d %7.1f%%%n",
                    cat, c[0], c[1], c[2], c[3], share);
        }

        System.out.println("\n   3. TOP COMPLAINANT CUSTOMERS (Customers raising the most complaints):");
        System.out.printf("  %-12s %-18s %-12s %-12s %6s %8s %8s%n",
                "Cust No", "Customer Name", "City", "Mobile", "Total", "Pending", "Resolved");
        System.out.println("  " + "-".repeat(76));

        for (Map<String, Object> map : topCustomers) {
            String custNo = String.valueOf(map.get("customerNumber"));
            String name = String.valueOf(map.get("customerName"));
            String city = String.valueOf(map.get("city"));
            String mob = String.valueOf(map.get("mobileNumber"));
            int tot = (Integer) map.get("totalCount");
            int pen = (Integer) map.get("pendingCount");
            int res = (Integer) map.get("resolvedCount");

            System.out.printf("  %-12s %-18s %-12s %-12s %6d %8d %8d%n",
                    custNo,
                    name.length() > 18 ? name.substring(0, 18) : name,
                    city.length() > 12 ? city.substring(0, 12) : city,
                    mob, tot, pen, res);
        }

        List<Map<String, Object>> multiComplaints = complaintService.getCustomersWithMultipleComplaints(2);
        if (!multiComplaints.isEmpty()) {
            System.out.println("\n   4. FREQUENT COMPLAINANTS (SQL HAVING >= 2 Complaints Filter):");
            System.out.printf("  %-12s %-20s %-14s %-14s %s%n", "Cust No", "Name", "City", "Mobile", "Complaints");
            System.out.println("  " + "-".repeat(68));
            for (Map<String, Object> map : multiComplaints) {
                System.out.printf("  %-12s %-20s %-14s %-14s %d%n",
                        map.get("customerNumber"),
                        map.get("customerName"),
                        map.get("city"),
                        map.get("mobileNumber"),
                        (Integer) map.get("complaintCount"));
            }
        }

        System.out.println("------------------------------------------------------------");

        System.out.print("Export Complaint Hotspot & Analytics Report to CSV (Y/N): ");
        String ans = scanner.nextLine().trim();
        if ("Y".equalsIgnoreCase(ans)) {
            try {
                String path = reportGenerator.exportComplaintHotspotsToCsv(cityStats, catStats, topCustomers, "complaint_hotspots_report.csv");
                System.out.println("  [OK] Complaint hotspot report exported successfully to: " + path);
            } catch (Exception e) {
                System.out.println("  [Error] Export failed: " + e.getMessage());
            }
        }
    }

    private void viewAuditLogs() {
        try {
            List<AuditLog> logs = auditDAO.getAuditLogs();
            System.out.println("\nAUDIT LOGS");
            if (logs.isEmpty()) {
                System.out.println("  No audit records.");
            } else {
                for (AuditLog log : logs) {
                    System.out.println("  " + log);
                }
            }
            System.out.println("------------------------------------------------------------");
        } catch (Exception e) {
            System.out.println("[Error] Error: " + e.getMessage());
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
                System.out.println("[Error] Admin username not found.");
                return;
            }

            Administrator admin = opt.get();
            String otp = com.amdocs.telecom.security.OTPService.generateOtp("ADMIN:"+username);
            System.out.println(" OTP sent to registered email [" + admin.getEmail() + "] (simulated): " + otp);

            System.out.print("Enter OTP: ");
            String enteredOtp = scanner.nextLine().trim();
            if (!com.amdocs.telecom.security.OTPService.verifyOtp("ADMIN:"+username, enteredOtp)) {
                System.out.println("[Error] Invalid or expired OTP.");
                return;
            }

            for (int attempt = 1; attempt <= 3; attempt++) {
                System.out.print("Enter new password (or 'cancel'): ");
                String newPass = scanner.nextLine().trim();
                if ("cancel".equalsIgnoreCase(newPass)) return;

                List<String> missing = PasswordUtil.getPasswordMissingRequirements(newPass);
                if (!missing.isEmpty()) {
                    System.out.println("[Warning] Password does not meet requirements:");
                    missing.forEach(m -> System.out.println("   • " + m));
                    if (attempt == 3) {
                        System.out.println("[Error] Maximum attempts reached. Password reset cancelled.");
                        return;
                    }
                    continue;
                }

                String hashed = PasswordUtil.hashPassword(newPass);
                boolean updated = adminDAO.updatePassword(admin.getAdminId(), hashed);
                if (updated) {
                    if("LOCKED".equals(admin.getAccountStatus())) adminDAO.updateAccountStatus(admin.getAdminId(), "ACTIVE");
                    com.amdocs.telecom.security.LoginSecurity.clear(username,"ADMIN");
                    new CustomerDAOImpl().logLoginAttempt(username,"ADMIN","127.0.0.1","RESET");
                    System.out.println("[OK] Admin password reset successfully! Please login with your new password.");
                    AuditLog audit = new AuditLog();
                    audit.setEntityName("ADMIN");
                    audit.setEntityId(String.valueOf(admin.getAdminId()));
                    audit.setAction("PASSWORD_RESET");
                    audit.setDetails("Admin password reset via OTP recovery");
                    audit.setPerformedBy(username);
                    auditDAO.logAudit(audit);
                } else {
                    System.out.println("[Error] Failed to update password in database.");
                }
                return;
            }
        } catch (SQLException e) {
            System.out.println("[Error] Database error: " + e.getMessage());
        }
    }

    private void handleAdminChangePassword() {
        System.out.println("\n--- Change Administrator Password ---");
        System.out.print("Enter Current Password (or 'cancel'): ");
        String currentPass = scanner.nextLine().trim();
        if ("cancel".equalsIgnoreCase(currentPass) || currentPass.isEmpty()) return;

        if (!PasswordUtil.verifyPassword(currentPass, loggedInAdmin.getPasswordHash())) {
            System.out.println("[Error] Current password does not match.");
            return;
        }

        for (int attempt = 1; attempt <= 3; attempt++) {
            System.out.print("Enter New Password (or 'cancel'): ");
            String newPass = scanner.nextLine().trim();
            if ("cancel".equalsIgnoreCase(newPass)) return;

            if (currentPass.equals(newPass)) {
                System.out.println("[Warning] New password cannot be the same as current password.");
                continue;
            }

            List<String> missing = PasswordUtil.getPasswordMissingRequirements(newPass);
            if (!missing.isEmpty()) {
                System.out.println("[Warning] Password does not meet requirements:");
                missing.forEach(m -> System.out.println("   • " + m));
                if (attempt == 3) {
                    System.out.println("[Error] Maximum attempts reached. Password change cancelled.");
                    return;
                }
                continue;
            }

            System.out.print("Confirm New Password: ");
            String confirmPass = scanner.nextLine().trim();
            if (!newPass.equals(confirmPass)) {
                System.out.println("[Error] Passwords do not match. Please try again.");
                continue;
            }

            try {
                String hashed = PasswordUtil.hashPassword(newPass);
                boolean updated = adminDAO.updatePassword(loggedInAdmin.getAdminId(), hashed);
                if (updated) {
                    loggedInAdmin.setPasswordHash(hashed);
                    System.out.println("[OK] Administrator password changed successfully!");

                    AuditLog audit = new AuditLog();
                    audit.setEntityName("ADMIN");
                    audit.setEntityId(String.valueOf(loggedInAdmin.getAdminId()));
                    audit.setAction("PASSWORD_CHANGE");
                    audit.setDetails("Admin changed password while logged in");
                    audit.setPerformedBy(loggedInAdmin.getUsername());
                    auditDAO.logAudit(audit);
                } else {
                    System.out.println("[Error] Failed to update password in database.");
                }
                return;
            } catch (SQLException e) {
                System.out.println("[Error] Database error: " + e.getMessage());
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

}
