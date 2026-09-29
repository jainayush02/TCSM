package com.amdocs.telecom.controller;

import com.amdocs.telecom.dto.CustomerRegistrationDTO;
import com.amdocs.telecom.exception.TelecomException;
import com.amdocs.telecom.model.*;
import com.amdocs.telecom.security.CaptchaGenerator;
import com.amdocs.telecom.security.PasswordUtil;
import com.amdocs.telecom.validation.ValidationUtil;
import com.amdocs.telecom.service.*;
import com.amdocs.telecom.service.impl.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

/**
 * CustomerController handles the interactive customer portal menu.
 */
public class CustomerController {

    private final AuthenticationService authService;
    private final CustomerService customerService;
    private final PlanService planService;
    private final SubscriptionService subscriptionService;
    private final BillingService billingService;
    private final PaymentService paymentService;
    private final UsageService usageService;
    private final ComplaintService complaintService;
    private final com.amdocs.telecom.report.ReportGenerator reportGenerator;
    private final Scanner scanner;
    private Customer loggedInCustomer;

    public CustomerController(Scanner scanner) {
        this.scanner = scanner;
        this.authService = new AuthenticationServiceImpl();
        this.customerService = new CustomerServiceImpl();
        this.planService = new PlanServiceImpl();
        this.subscriptionService = new SubscriptionServiceImpl();
        this.billingService = new BillingServiceImpl();
        this.paymentService = new PaymentServiceImpl();
        this.usageService = new UsageServiceImpl();
        this.complaintService = new ComplaintServiceImpl();
        this.reportGenerator = new com.amdocs.telecom.report.ReportGenerator();
    }

    public void showLoginMenu() {
        boolean inPortal = true;
        while (inPortal) {
            System.out.println("\n╔══════════════════════════════════════╗");
            System.out.println("║       CUSTOMER LOGIN PORTAL          ║");
            System.out.println("╠══════════════════════════════════════╣");
            System.out.println("║  1. Login                            ║");
            System.out.println("║  2. Register New Account             ║");
            System.out.println("║  3. Forgot Password                  ║");
            System.out.println("║  4. Back to Main Menu                ║");
            System.out.println("╚══════════════════════════════════════╝");
            System.out.print("Select option: ");
            if (!scanner.hasNextLine()) break;

            int choice = readInt();
            switch (choice) {
                case 1 -> {
                    if (handleLogin(null)) {
                        showCustomerDashboard();
                    }
                }
                case 2 -> handleRegistration();
                case 3 -> handleForgotPassword();
                case 4 -> inPortal = false;
                default -> System.out.println("Invalid option. Please choose 1, 2, 3, or 4.");
            }
        }
    }

    private boolean handleLogin(String presetUsername) {
        System.out.println("\n--- Customer Login ---");
        String username = presetUsername;
        if (username == null || username.isEmpty()) {
            System.out.print("Username or Email (or 'cancel' to return): ");
            username = scanner.nextLine().trim();
            if ("cancel".equalsIgnoreCase(username)) return false;
        } else {
            System.out.println("Username: " + username);
        }

        // Early check: Verify username or email exists in database before asking for password
        if (!customerService.isUserRegistered(username)) {
            System.out.println("❌ User '" + username + "' is not registered. Please register first or check your username/email.");
            return false;
        }

        System.out.print("Password: ");
        String password = scanner.nextLine().trim();
        if ("cancel".equalsIgnoreCase(password)) return false;

        // CAPTCHA verification with 3 retries
        String captchaInput = null;
        String expectedCaptcha = null;
        for (int cAttempt = 1; cAttempt <= 3; cAttempt++) {
            expectedCaptcha = CaptchaGenerator.generateCaptcha();
            System.out.println("CAPTCHA: [" + expectedCaptcha + "]");
            System.out.print("Enter CAPTCHA: ");
            captchaInput = scanner.nextLine().trim();

            if (CaptchaGenerator.validateCaptcha(captchaInput, expectedCaptcha)) {
                break;
            }

            if (cAttempt < 3) {
                System.out.println("⚠️ CAPTCHA did not match. Let's generate a new one (Attempt " + (cAttempt + 1) + " of 3):");
            } else {
                System.out.println("❌ CAPTCHA verification failed 3 times. Returning to menu.");
                return false;
            }
        }

        try {
            loggedInCustomer = authService.login(username, password, expectedCaptcha, captchaInput);
            System.out.println("\n✅ Login successful! Welcome, " + loggedInCustomer.getFullName());
            return true;
        } catch (Exception e) {
            System.out.println("❌ " + e.getMessage());
            return false;
        }
    }

    private void handleRegistration() {
        System.out.println("\n--- New Customer Registration ---");
        System.out.println("(Tips: Type 'cancel' at any prompt to return to menu)\n");

        // 1. First Name
        String firstName = "";
        while (firstName.isEmpty()) {
            System.out.print("First Name: ");
            firstName = scanner.nextLine().trim();
            if ("cancel".equalsIgnoreCase(firstName)) return;
            if (firstName.isEmpty()) {
                System.out.println("⚠️ First name cannot be empty. Please enter your first name.");
            }
        }

        // 2. Last Name
        String lastName = "";
        while (lastName.isEmpty()) {
            System.out.print("Last Name: ");
            lastName = scanner.nextLine().trim();
            if ("cancel".equalsIgnoreCase(lastName)) return;
            if (lastName.isEmpty()) {
                System.out.println("⚠️ Last name cannot be empty. Please enter your last name.");
            }
        }

        // 3. Date of Birth with 3 attempts and age check
        LocalDate dob = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            System.out.print("Date of Birth (yyyy-MM-dd, e.g. 2004-07-02): ");
            String dobStr = scanner.nextLine().trim();
            if ("cancel".equalsIgnoreCase(dobStr)) return;
            try {
                dob = LocalDate.parse(dobStr, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                if (!ValidationUtil.isEligibleAge(dob, 18)) {
                    System.out.println("⚠️ Customer must be at least 18 years old. (Attempt " + attempt + " of 3)");
                    dob = null;
                } else {
                    break;
                }
            } catch (Exception e) {
                System.out.println("⚠️ Invalid date format. Please format as yyyy-MM-dd (e.g. 2000-01-15). (Attempt " + attempt + " of 3)");
            }
            if (attempt == 3) {
                System.out.println("❌ Maximum attempts reached for Date of Birth. Registration cancelled.");
                return;
            }
        }

        // 4. Email with 3 attempts
        String email = "";
        for (int attempt = 1; attempt <= 3; attempt++) {
            System.out.print("Email: ");
            email = scanner.nextLine().trim();
            if ("cancel".equalsIgnoreCase(email)) return;
            if (!ValidationUtil.isValidEmail(email)) {
                System.out.println("⚠️ Invalid email format (example: name@domain.com). (Attempt " + attempt + " of 3)");
            } else {
                break;
            }
            if (attempt == 3) {
                System.out.println("❌ Maximum attempts reached for Email. Registration cancelled.");
                return;
            }
        }

        // 5. Mobile Number with 3 attempts
        String mobile = "";
        for (int attempt = 1; attempt <= 3; attempt++) {
            System.out.print("Mobile Number (10-15 digits): ");
            mobile = scanner.nextLine().trim();
            if ("cancel".equalsIgnoreCase(mobile)) return;
            if (!ValidationUtil.isValidMobile(mobile)) {
                System.out.println("⚠️ Invalid mobile format. Must be 10 to 15 digits (e.g. 9876543210). (Attempt " + attempt + " of 3)");
            } else {
                break;
            }
            if (attempt == 3) {
                System.out.println("❌ Maximum attempts reached for Mobile. Registration cancelled.");
                return;
            }
        }

        // 6. Address, City, Country
        System.out.print("Address: ");
        String address = scanner.nextLine().trim();
        if ("cancel".equalsIgnoreCase(address)) return;
        if (address.isEmpty()) address = "Main Street";

        System.out.print("City: ");
        String city = scanner.nextLine().trim();
        if ("cancel".equalsIgnoreCase(city)) return;
        if (city.isEmpty()) city = "Mumbai";

        System.out.print("Country: ");
        String country = scanner.nextLine().trim();
        if ("cancel".equalsIgnoreCase(country)) return;
        if (country.isEmpty()) country = "India";

        // 7. Username
        String username = "";
        while (username.isEmpty()) {
            System.out.print("Username: ");
            username = scanner.nextLine().trim();
            if ("cancel".equalsIgnoreCase(username)) return;
            if (username.isEmpty()) {
                System.out.println("⚠️ Username cannot be empty.");
            }
        }

        // 8. Password with 3 attempts and detailed feedback
        String password = "";
        for (int attempt = 1; attempt <= 3; attempt++) {
            System.out.print("Password (Min 8 chars, 1 Upper, 1 Lower, 1 Digit, 1 Special @#$%^&+=!): ");
            password = scanner.nextLine().trim();
            if ("cancel".equalsIgnoreCase(password)) return;

            List<String> missing = PasswordUtil.getPasswordMissingRequirements(password);
            if (missing.isEmpty()) {
                break; // Valid password!
            }

            System.out.println("⚠️ Password does not meet requirements:");
            for (String req : missing) {
                System.out.println("   • " + req);
            }
            if (attempt < 3) {
                System.out.println("   Please try again (Attempt " + (attempt + 1) + " of 3):");
            } else {
                System.out.println("❌ Maximum attempts reached for password entry. Registration cancelled.");
                return;
            }
        }

        // Submit Registration
        try {
            CustomerRegistrationDTO dto = new CustomerRegistrationDTO(firstName, lastName, dob,
                    email, mobile, address, city, country, username, password);
            Customer registered = customerService.registerCustomer(dto);
            System.out.println("\n✅ Registration successful!");
            System.out.println("   Customer ID     : " + registered.getCustomerNumber());
            System.out.println("   Name            : " + registered.getFullName());
            System.out.println("   Username        : " + registered.getUsername());

            System.out.print("\nWould you like to log in now? (Y/N): ");
            String ans = scanner.nextLine().trim();
            if ("Y".equalsIgnoreCase(ans)) {
                if (handleLogin(registered.getUsername())) {
                    showCustomerDashboard();
                }
            }
        } catch (Exception e) {
            System.out.println("❌ Registration failed: " + e.getMessage());
        }
    }

    private void handleForgotPassword() {
        System.out.println("\n--- Forgot Password Recovery ---");
        System.out.print("Enter your username (or 'cancel'): ");
        String username = scanner.nextLine().trim();
        if ("cancel".equalsIgnoreCase(username)) return;

        try {
            String otp = authService.initiatePasswordRecovery(username);
            System.out.println("📧 OTP sent (simulated): " + otp);

            // OTP validation with 3 attempts
            String otpInput = "";
            boolean otpValid = false;
            for (int attempt = 1; attempt <= 3; attempt++) {
                System.out.print("Enter OTP: ");
                otpInput = scanner.nextLine().trim();
                if ("cancel".equalsIgnoreCase(otpInput)) return;
                if (otp.equals(otpInput)) {
                    otpValid = true;
                    break;
                }
                if (attempt < 3) {
                    System.out.println("⚠️ Incorrect OTP. Please try again (Attempt " + (attempt + 1) + " of 3):");
                } else {
                    System.out.println("❌ OTP verification failed. Password reset cancelled.");
                    return;
                }
            }

            if (!otpValid) return;

            // New password with complexity validation loop
            String newPassword = "";
            for (int attempt = 1; attempt <= 3; attempt++) {
                System.out.print("Enter new password: ");
                newPassword = scanner.nextLine().trim();
                if ("cancel".equalsIgnoreCase(newPassword)) return;

                List<String> missing = PasswordUtil.getPasswordMissingRequirements(newPassword);
                if (missing.isEmpty()) {
                    break;
                }
                System.out.println("⚠️ Password does not meet requirements:");
                for (String req : missing) {
                    System.out.println("   • " + req);
                }
                if (attempt < 3) {
                    System.out.println("   Please try again (Attempt " + (attempt + 1) + " of 3):");
                } else {
                    System.out.println("❌ Maximum attempts reached. Password reset cancelled.");
                    return;
                }
            }

            boolean success = authService.completePasswordRecovery(username, otpInput, newPassword);
            if (success) {
                System.out.println("✅ Password reset successful! Please login with your new password.");
            } else {
                System.out.println("❌ Password reset failed.");
            }
        } catch (Exception e) {
            System.out.println("❌ " + e.getMessage());
        }
    }

    private void showCustomerDashboard() {
        boolean running = true;
        while (running) {
            System.out.println("\n╔══════════════════════════════════════╗");
            System.out.println("║       CUSTOMER DASHBOARD             ║");
            System.out.println("║  Welcome: " + padRight(loggedInCustomer.getFullName(), 25) + " ║");
            System.out.println("╠══════════════════════════════════════╣");
            System.out.println("║  1.  View My Profile                 ║");
            System.out.println("║  2.  Browse Available Plans          ║");
            System.out.println("║  3.  Search Plans by Name            ║");
            System.out.println("║  4.  Filter Plans by Price           ║");
            System.out.println("║  5.  My Subscriptions                ║");
            System.out.println("║  6.  Subscribe to a Plan             ║");
            System.out.println("║  7.  Change Plan                     ║");
            System.out.println("║  8.  View My Bills                   ║");
            System.out.println("║  9.  Make a Payment                  ║");
            System.out.println("║  10. View Usage History              ║");
            System.out.println("║  11. Raise a Complaint               ║");
            System.out.println("║  12. View Notifications              ║");
            System.out.println("║  13. Logout                          ║");
            System.out.println("╚══════════════════════════════════════╝");
            System.out.print("Select option: ");
            if (!scanner.hasNextLine()) break;

            int choice = readInt();
            switch (choice) {
                case 1 -> viewProfile();
                case 2 -> browsePlans();
                case 3 -> searchPlans();
                case 4 -> filterPlansByPrice();
                case 5 -> viewSubscriptions();
                case 6 -> subscribeToPlan();
                case 7 -> changePlan();
                case 8 -> viewBills();
                case 9 -> makePayment();
                case 10 -> viewUsage();
                case 11 -> raiseComplaint();
                case 12 -> viewNotifications();
                case 13 -> {
                    System.out.println("Logged out. Goodbye!");
                    loggedInCustomer = null;
                    running = false;
                }
                default -> System.out.println("Invalid option.");
            }
        }
    }

    private void viewProfile() {
        Customer c = loggedInCustomer;
        System.out.println("\n┌─── MY PROFILE ──────────────────────┐");
        System.out.println("  Customer No : " + c.getCustomerNumber());
        System.out.println("  Name        : " + c.getFullName());
        System.out.println("  DOB         : " + c.getDateOfBirth());
        System.out.println("  Email       : " + c.getEmail());
        System.out.println("  Mobile      : " + c.getMobileNumber());
        System.out.println("  Address     : " + c.getAddress());
        System.out.println("  City        : " + c.getCity());
        System.out.println("  Country     : " + c.getCountry());
        System.out.println("  Status      : " + c.getAccountStatus());
        System.out.println("  Registered  : " + c.getRegistrationDate());
        System.out.println("└──────────────────────────────────────┘");
    }

    private void browsePlans() {
        System.out.println("\n┌─── AVAILABLE TELECOM PLANS ─────────┐");
        List<TelecomPlan> plans = planService.getAllActivePlans();
        if (plans.isEmpty()) {
            System.out.println("  No active plans available.");
        } else {
            System.out.printf("  %-5s %-10s %-16s %-8s %6s %10s %-10s ₹%s%n",
                    "ID", "Code", "Name", "Type", "Data", "Voice", "Roaming", "Price");
            System.out.println("  " + "-".repeat(85));
            for (TelecomPlan p : plans) {
                System.out.printf("  %-5d %-10s %-16s %-8s %4dGB %10s %-10s ₹%.2f%n",
                        p.getPlanId(), p.getPlanCode(), p.getPlanName(), p.getPlanType(),
                        p.getDataAllowanceGB(), p.getVoiceDisplay(),
                        p.isInternationalRoaming() ? "Yes" : "No", p.getMonthlyRental());
            }
        }
        System.out.println("└──────────────────────────────────────┘");
    }

    private void searchPlans() {
        System.out.print("Enter plan name keyword: ");
        String keyword = scanner.nextLine().trim();
        List<TelecomPlan> results = planService.searchPlansByName(keyword);
        System.out.println("Found " + results.size() + " plan(s):");
        results.forEach(p -> System.out.println("  " + p));
    }

    private void filterPlansByPrice() {
        System.out.print("Enter maximum monthly price (₹): ");
        double max = readDouble();
        List<TelecomPlan> results = planService.filterPlansByMaxPrice(max);
        System.out.println("Plans within ₹" + max + ":");
        results.forEach(p -> System.out.println("  " + p));
    }

    private void viewSubscriptions() {
        List<MobileSubscription> subs = subscriptionService.getCustomerSubscriptions(loggedInCustomer.getCustomerId());
        System.out.println("\n┌─── MY SUBSCRIPTIONS ────────────────┐");
        if (subs.isEmpty()) {
            System.out.println("  No active subscriptions.");
        } else {
            for (MobileSubscription s : subs) {
                System.out.println("  " + s);
            }
        }
        System.out.println("└──────────────────────────────────────┘");
    }

    private void subscribeToPlan() {
        browsePlans();
        System.out.print("Enter Plan ID to subscribe (or 0 to cancel): ");
        int planId = readInt();
        if (planId <= 0) return;

        System.out.println("Select SIM Type:");
        System.out.println("  1. Physical SIM");
        System.out.println("  2. eSIM");
        System.out.print("Enter choice (1 or 2, Default: 1): ");
        String simChoice = scanner.nextLine().trim();
        String simType = ("2".equals(simChoice) || "esim".equalsIgnoreCase(simChoice)) ? "ESIM" : "PHYSICAL_SIM";

        try {
            MobileSubscription sub = subscriptionService.subscribeToPlan(loggedInCustomer.getCustomerId(), planId, simType);
            System.out.println("\n✅ Subscribed successfully!");
            System.out.println("   Subscription No : " + sub.getSubscriptionNumber());
            System.out.println("   Mobile Number   : " + sub.getMobileNumber());
            System.out.println("   SIM Type        : " + simType);
        } catch (TelecomException e) {
            System.out.println("❌ " + e.getMessage());
        }
    }

    private void changePlan() {
        List<MobileSubscription> subs = subscriptionService.getCustomerSubscriptions(loggedInCustomer.getCustomerId());
        System.out.println("\n┌─── MY SUBSCRIPTIONS ────────────────┐");
        if (subs.isEmpty()) {
            System.out.println("  No active subscriptions found.");
            System.out.println("└──────────────────────────────────────┘");
            return;
        }
        for (MobileSubscription s : subs) {
            System.out.println(s);
        }
        System.out.println("└──────────────────────────────────────┘");

        System.out.print("Enter Subscription No (e.g. SUB82339), Mobile No, or ID (or 'cancel'): ");
        String input = scanner.nextLine().trim();
        if ("cancel".equalsIgnoreCase(input) || "0".equals(input) || input.isEmpty()) return;

        MobileSubscription target = subs.stream().filter(s ->
                input.equalsIgnoreCase(s.getSubscriptionNumber())
                || input.equalsIgnoreCase(String.valueOf(s.getSubscriptionId()))
                || input.equalsIgnoreCase(s.getMobileNumber())
                || s.getMobileNumber() != null && s.getMobileNumber().endsWith(input)
        ).findFirst().orElse(null);

        if (target == null) {
            System.out.println("❌ Could not find a subscription matching: " + input);
            return;
        }

        System.out.println("\nSelected: " + target.getSubscriptionNumber() + " (" + target.getMobileNumber() + ") - Current Plan: " + target.getPlanName());
        browsePlans();
        System.out.print("Enter new Plan ID: ");
        int newPlanId = readInt();
        if (newPlanId <= 0) return;

        try {
            boolean success = subscriptionService.changePlan(target.getSubscriptionId(), newPlanId, loggedInCustomer.getUsername());
            if (success) System.out.println("✅ Plan changed successfully for " + target.getSubscriptionNumber() + " (" + target.getMobileNumber() + ")!");
        } catch (TelecomException e) {
            System.out.println("❌ " + e.getMessage());
        }
    }

    private void viewBills() {
        List<Bill> bills = billingService.getCustomerBills(loggedInCustomer.getCustomerId());
        System.out.println("\n┌─── MY BILLS ────────────────────────┐");
        if (bills.isEmpty()) {
            System.out.println("  No bills found.");
        } else {
            for (Bill b : bills) {
                System.out.println("  " + b);
            }
            System.out.print("\n  Would you like to export an official tax invoice to file? (Y/N): ");
            String ans = scanner.nextLine().trim();
            if ("Y".equalsIgnoreCase(ans)) {
                System.out.print("  Enter Bill Number (e.g. BILL-1001) or Bill ID: ");
                String bInput = scanner.nextLine().trim();
                Bill selected = bills.stream().filter(b ->
                        bInput.equalsIgnoreCase(b.getBillNumber())
                        || bInput.equalsIgnoreCase(String.valueOf(b.getBillId()))
                ).findFirst().orElse(null);
                if (selected != null) {
                    try {
                        String filePath = reportGenerator.generateBillInvoiceText(selected, loggedInCustomer, "invoice_" + selected.getBillId() + ".txt");
                        System.out.println("  ✅ Invoice successfully generated at: " + filePath);
                    } catch (Exception e) {
                        System.out.println("  ❌ Failed to generate invoice: " + e.getMessage());
                    }
                } else {
                    System.out.println("  ❌ Bill ID not found in your list.");
                }
            }
        }
        System.out.println("└──────────────────────────────────────┘");
    }

    private void makePayment() {
        List<Bill> bills = billingService.getCustomerBills(loggedInCustomer.getCustomerId());
        if (bills.isEmpty()) {
            System.out.println("\n  No bills to pay.");
            return;
        }

        System.out.println("\n┌─── PENDING BILLS ───────────────────┐");
        boolean hasUnpaid = false;
        for (Bill b : bills) {
            if (!"PAID".equalsIgnoreCase(b.getBillStatus())) {
                System.out.printf("  Bill #%d [%s] - Due: %s - Total: ₹%.2f - Status: %s%n",
                        b.getBillId(), b.getBillNumber(), b.getDueDate(), b.getTotalAmount(), b.getBillStatus());
                hasUnpaid = true;
            }
        }
        if (!hasUnpaid) {
            System.out.println("  All your bills are already PAID! No payments due.");
            System.out.println("└──────────────────────────────────────┘");
            return;
        }
        System.out.println("└──────────────────────────────────────┘");

        System.out.print("Enter Bill Number (e.g. BILL-1001) or Bill ID to pay (or 'cancel'): ");
        String billInput = scanner.nextLine().trim();
        if ("cancel".equalsIgnoreCase(billInput) || "0".equals(billInput) || billInput.isEmpty()) return;

        Bill target = bills.stream().filter(b ->
                billInput.equalsIgnoreCase(b.getBillNumber())
                || billInput.equalsIgnoreCase(String.valueOf(b.getBillId()))
        ).findFirst().orElse(null);

        if (target == null) {
            System.out.println("❌ Bill '" + billInput + "' not found.");
            return;
        }
        if ("PAID".equalsIgnoreCase(target.getBillStatus())) {
            System.out.println("⚠️ This bill is already PAID.");
            return;
        }

        System.out.printf("Amount Due: ₹%.2f%n", target.getTotalAmount());
        System.out.print("Enter Amount to pay [Press Enter for full amount ₹" + target.getTotalAmount() + "]: ");
        String amtStr = scanner.nextLine().trim();
        double amount = amtStr.isEmpty() ? target.getTotalAmount() : 0.0;
        if (!amtStr.isEmpty()) {
            try { amount = Double.parseDouble(amtStr); } catch (Exception e) { amount = target.getTotalAmount(); }
        }

        System.out.println("\nSelect Payment Mode:");
        System.out.println("  1. UPI (Google Pay, PhonePe, Paytm)");
        System.out.println("  2. Credit / Debit Card");
        System.out.println("  3. Net Banking");
        System.out.println("  4. Bank Transfer / NEFT");
        System.out.print("Enter choice (1-4, Default: 1): ");
        String modeChoice = scanner.nextLine().trim();
        String mode = switch (modeChoice) {
            case "2", "card" -> "CARD";
            case "3", "netbanking" -> "NET_BANKING";
            case "4", "transfer" -> "BANK_TRANSFER";
            default -> "UPI";
        };

        try {
            Payment payment = paymentService.processPayment(target.getBillId(), loggedInCustomer.getCustomerId(), amount, mode, loggedInCustomer.getUsername());
            System.out.println("\n✅ Payment successful!");
            System.out.println("   Transaction Ref : " + payment.getTransactionReference());
            System.out.println("   Amount Paid     : ₹" + payment.getAmount());
            System.out.println("   Payment Mode    : " + mode);
        } catch (TelecomException e) {
            System.out.println("❌ " + e.getMessage());
        }
    }

    private void viewUsage() {
        List<UsageRecord> usage = usageService.getCustomerUsageHistory(loggedInCustomer.getCustomerId());
        System.out.println("\n┌─── USAGE HISTORY ───────────────────┐");
        if (usage.isEmpty()) {
            System.out.println("  No usage records found.");
        } else {
            usage.stream().limit(20).forEach(u -> System.out.println("  " + u));
            if (usage.size() > 20) System.out.println("  ... and " + (usage.size() - 20) + " more records.");
        }

        // Show summary using Streams
        List<MobileSubscription> subs = subscriptionService.getCustomerSubscriptions(loggedInCustomer.getCustomerId());
        for (MobileSubscription sub : subs) {
            Map<String, Double> summary = usageService.getUsageSummary(sub.getSubscriptionId());
            if (!summary.isEmpty()) {
                System.out.println("\n  Summary for " + sub.getSubscriptionNumber() + ":");
                summary.forEach((type, qty) -> System.out.printf("    %-10s : %.1f%n", type, qty));
            }
        }
        System.out.println("└──────────────────────────────────────┘");
    }

    private void raiseComplaint() {
        System.out.println("\nSelect Complaint Category:");
        System.out.println("  1. Billing / Invoice Issue");
        System.out.println("  2. Network / Connectivity Issue");
        System.out.println("  3. SIM Card / Activation Issue");
        System.out.println("  4. Tariff Plan / Data Allowance");
        System.out.println("  5. Payment / Transaction Issue");
        System.out.println("  6. Other Inquiries");
        System.out.print("Enter choice (1-6, Default: 1): ");
        String catChoice = scanner.nextLine().trim();
        ComplaintCategory cat = switch (catChoice) {
            case "2" -> ComplaintCategory.NETWORK;
            case "3" -> ComplaintCategory.SIM;
            case "4" -> ComplaintCategory.PLAN;
            case "5" -> ComplaintCategory.PAYMENT;
            case "6" -> ComplaintCategory.OTHER;
            default -> ComplaintCategory.BILLING;
        };

        System.out.print("Describe the issue: ");
        String description = scanner.nextLine().trim();
        if (description.isEmpty()) {
            System.out.println("⚠️ Description cannot be empty.");
            return;
        }

        try {
            Complaint complaint = complaintService.lodgeComplaint(loggedInCustomer.getCustomerId(), cat.name(), description);

            System.out.println("\n✅ Complaint registered successfully!");
            System.out.println("   Ticket Number : " + complaint.getComplaintNumber());
            System.out.println("   Category      : " + complaint.getCategory());
            System.out.println("   Status        : " + complaint.getStatus());
        } catch (Exception e) {
            System.out.println("❌ Error registering complaint: " + e.getMessage());
        }
    }

    private void viewNotifications() {
        try {
            com.amdocs.telecom.dao.AuditAndNotificationDAO dao = new com.amdocs.telecom.dao.impl.AuditAndNotificationDAOImpl();
            List<Notification> notifs = dao.getNotificationsForCustomer(loggedInCustomer.getCustomerId());
            System.out.println("\n┌─── NOTIFICATIONS ───────────────────┐");
            if (notifs.isEmpty()) {
                System.out.println("  No notifications.");
            } else {
                for (Notification n : notifs) {
                    System.out.println("  " + n);
                }
            }
            System.out.println("└──────────────────────────────────────┘");
        } catch (Exception e) {
            System.out.println("❌ Error: " + e.getMessage());
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
