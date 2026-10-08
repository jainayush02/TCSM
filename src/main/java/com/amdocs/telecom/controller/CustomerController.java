package com.amdocs.telecom.controller;

import com.amdocs.telecom.util.ConsoleMenu;
import com.amdocs.telecom.util.ConsoleTable;

import com.amdocs.telecom.dto.CustomerRegistrationDTO;
import com.amdocs.telecom.exception.TelecomException;
import com.amdocs.telecom.model.*;
import com.amdocs.telecom.security.CaptchaGenerator;
import com.amdocs.telecom.security.PasswordUtil;
import com.amdocs.telecom.validation.ValidationUtil;
import com.amdocs.telecom.service.*;
import com.amdocs.telecom.service.impl.*;
import com.amdocs.telecom.main.MainApplication;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

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
        this.paymentService = new PaymentServiceImpl(MainApplication.getNotificationService());
        this.usageService = new UsageServiceImpl();
        this.complaintService = new ComplaintServiceImpl();
        this.reportGenerator = new com.amdocs.telecom.report.ReportGenerator();
    }

    public void showLoginMenu() {
        boolean inPortal = true;
        while (inPortal) {
            ConsoleMenu.show("CUSTOMER LOGIN PORTAL", null,
                    "1. Login", "2. Register New Account", "3. Forgot Password", "4. Back to Main Menu");
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

        // Check the account before requesting a password.
        if (!customerService.isUserRegistered(username)) {
            System.out.println("[Error] User '" + username + "' is not registered. Please register first or check your username/email.");
            return false;
        }

        System.out.print("Password: ");
        String password = scanner.nextLine().trim();
        if ("cancel".equalsIgnoreCase(password)) return false;

        // Allow three CAPTCHA attempts.
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
                System.out.println("[Warning] CAPTCHA did not match. Let's generate a new one (Attempt " + (cAttempt + 1) + " of 3):");
            } else {
                System.out.println("[Error] CAPTCHA verification failed 3 times. Returning to menu.");
                return false;
            }
        }

        try {
            loggedInCustomer = authService.login(username, password, expectedCaptcha, captchaInput);
            System.out.println("\n[OK] Login successful! Welcome, " + loggedInCustomer.getFullName());
            if (loggedInCustomer.getPreviousLogin()!=null) System.out.println("Last login: " + loggedInCustomer.getPreviousLogin());
            return true;
        } catch (Exception e) {
            System.out.println("[Error] " + e.getMessage());
            return false;
        }
    }

    private void handleRegistration() {
        System.out.println("\n--- New Customer Registration ---");
        System.out.println("(Tips: Type 'cancel' at any prompt to return to menu)\n");

        String firstName = "";
        while (firstName.isEmpty()) {
            System.out.print("First Name: ");
            firstName = scanner.nextLine().trim();
            if ("cancel".equalsIgnoreCase(firstName)) return;
            if (firstName.isEmpty()) {
                System.out.println("[Warning] First name cannot be empty. Please enter your first name.");
            }
        }

        String lastName = "";
        while (lastName.isEmpty()) {
            System.out.print("Last Name: ");
            lastName = scanner.nextLine().trim();
            if ("cancel".equalsIgnoreCase(lastName)) return;
            if (lastName.isEmpty()) {
                System.out.println("[Warning] Last name cannot be empty. Please enter your last name.");
            }
        }

        LocalDate dob = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            System.out.print("Date of Birth (yyyy-MM-dd, e.g. 2004-07-02): ");
            String dobStr = scanner.nextLine().trim();
            if ("cancel".equalsIgnoreCase(dobStr)) return;
            try {
                dob = LocalDate.parse(dobStr, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                if (!ValidationUtil.isEligibleAge(dob, 18)) {
                    System.out.println("[Warning] Customer must be at least 18 years old. (Attempt " + attempt + " of 3)");
                    dob = null;
                } else {
                    break;
                }
            } catch (Exception e) {
                System.out.println("[Warning] Invalid date format. Please format as yyyy-MM-dd (e.g. 2000-01-15). (Attempt " + attempt + " of 3)");
            }
            if (attempt == 3) {
                System.out.println("[Error] Maximum attempts reached for Date of Birth. Registration cancelled.");
                return;
            }
        }

        String email = "";
        for (int attempt = 1; attempt <= 3; attempt++) {
            System.out.print("Email: ");
            email = scanner.nextLine().trim();
            if ("cancel".equalsIgnoreCase(email)) return;
            if (!ValidationUtil.isValidEmail(email)) {
                System.out.println("[Warning] Invalid email format (example: name@domain.com). (Attempt " + attempt + " of 3)");
            } else {
                break;
            }
            if (attempt == 3) {
                System.out.println("[Error] Maximum attempts reached for Email. Registration cancelled.");
                return;
            }
        }

        String mobile = "";
        for (int attempt = 1; attempt <= 3; attempt++) {
            System.out.print("Mobile Number (10-15 digits): ");
            mobile = scanner.nextLine().trim();
            if ("cancel".equalsIgnoreCase(mobile)) return;
            if (!ValidationUtil.isValidMobile(mobile)) {
                System.out.println("[Warning] Invalid mobile format. Must be 10 to 15 digits (e.g. 9876543210). (Attempt " + attempt + " of 3)");
            } else {
                break;
            }
            if (attempt == 3) {
                System.out.println("[Error] Maximum attempts reached for Mobile. Registration cancelled.");
                return;
            }
        }

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

        String username = "";
        while (username.isEmpty()) {
            System.out.print("Username: ");
            username = scanner.nextLine().trim();
            if ("cancel".equalsIgnoreCase(username)) return;
            if (username.isEmpty()) {
                System.out.println("[Warning] Username cannot be empty.");
            }
        }

        String password = "";
        for (int attempt = 1; attempt <= 3; attempt++) {
            System.out.print("Password (Min 8 chars, 1 Upper, 1 Lower, 1 Digit, 1 Special @#$%^&+=!): ");
            password = scanner.nextLine().trim();
            if ("cancel".equalsIgnoreCase(password)) return;

            List<String> missing = PasswordUtil.getPasswordMissingRequirements(password);
            if (missing.isEmpty()) {
                break; // Valid password!
            }

            System.out.println("[Warning] Password does not meet requirements:");
            for (String req : missing) {
                System.out.println("   • " + req);
            }
            if (attempt < 3) {
                System.out.println("   Please try again (Attempt " + (attempt + 1) + " of 3):");
            } else {
                System.out.println("[Error] Maximum attempts reached for password entry. Registration cancelled.");
                return;
            }
        }

        try {
            CustomerRegistrationDTO dto = new CustomerRegistrationDTO(firstName, lastName, dob,
                    email, mobile, address, city, country, username, password);
            Customer registered = customerService.registerCustomer(dto);
            System.out.println("\n[OK] Registration successful!");
            System.out.println("   Customer ID     : " + registered.getCustomerNumber());
            System.out.println("   Name            : " + registered.getFullName());
            System.out.println("   Username        : " + registered.getUsername());

            System.out.print("\nWould you like to log in now (Y/N): ");
            String ans = scanner.nextLine().trim();
            if ("Y".equalsIgnoreCase(ans)) {
                if (handleLogin(registered.getUsername())) {
                    showCustomerDashboard();
                }
            }
        } catch (Exception e) {
            System.out.println("[Error] Registration failed: " + e.getMessage());
        }
    }

    private void handleForgotPassword() {
        System.out.println("\n--- Forgot Password Recovery ---");
        System.out.print("Enter your username (or 'cancel'): ");
        String username = scanner.nextLine().trim();
        if ("cancel".equalsIgnoreCase(username)) return;

        try {
            String otp = authService.initiatePasswordRecovery(username);
            System.out.println(" OTP sent (simulated): " + otp);

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
                    System.out.println("[Warning] Incorrect OTP. Please try again (Attempt " + (attempt + 1) + " of 3):");
                } else {
                    System.out.println("[Error] OTP verification failed. Password reset cancelled.");
                    return;
                }
            }

            if (!otpValid) return;

            String newPassword = "";
            for (int attempt = 1; attempt <= 3; attempt++) {
                System.out.print("Enter new password: ");
                newPassword = scanner.nextLine().trim();
                if ("cancel".equalsIgnoreCase(newPassword)) return;

                List<String> missing = PasswordUtil.getPasswordMissingRequirements(newPassword);
                if (missing.isEmpty()) {
                    break;
                }
                System.out.println("[Warning] Password does not meet requirements:");
                for (String req : missing) {
                    System.out.println("   • " + req);
                }
                if (attempt < 3) {
                    System.out.println("   Please try again (Attempt " + (attempt + 1) + " of 3):");
                } else {
                    System.out.println("[Error] Maximum attempts reached. Password reset cancelled.");
                    return;
                }
            }

            boolean success = authService.completePasswordRecovery(username, otpInput, newPassword);
            if (success) {
                System.out.println("[OK] Password reset successful! Please login with your new password.");
            } else {
                System.out.println("[Error] Password reset failed.");
            }
        } catch (Exception e) {
            System.out.println("[Error] " + e.getMessage());
        }
    }

    private void showCustomerDashboard() {
        boolean running = true;
        while (running) {
            ConsoleMenu.showCompact("CUSTOMER DASHBOARD", "Welcome: " + loggedInCustomer.getFullName(),
                    "1. My Profile", "2. Explore Plans", "3. My Subscriptions",
                    "4. Subscribe to a Plan", "5. Change Plan", "6. Manage Add-ons",
                    "7. View Bills", "8. Make a Payment", "9. Usage",
                    "10. Raise a Complaint", "11. Track Complaints", "12. Notifications",
                    "", "0. Logout");
            System.out.print("Select option: ");
            if (!scanner.hasNextLine()) break;

            int choice = readInt();
            try { switch (choice) {
                case 1 -> profileMenu();
                case 2 -> explorePlans();
                case 3 -> viewSubscriptions();
                case 4 -> subscribeToPlan();
                case 5 -> changePlan();
                case 6 -> manageAddOns();
                case 7 -> viewBills();
                case 8 -> makePayment();
                case 9 -> usageMenu();
                case 10 -> raiseComplaint();
                case 11 -> trackCustomerComplaints();
                case 12 -> viewNotifications();
                case 0 -> {
                    System.out.println("Logged out. Goodbye!");
                    authService.logout(loggedInCustomer.getUsername());
                    loggedInCustomer = null;
                    running = false;
                }
                default -> System.out.println("Invalid option.");
            } } catch (RuntimeException e) { System.out.println("Could not complete this action: " + e.getMessage()); }
        }
    }


    private void manageAddOns() {
        try {
            AddOnService service=new AddOnServiceImpl();
            viewSubscriptions();
            System.out.print("Subscription ID (0 to return): "); int id=readInt(); if(id<=0) return;
            List<AddOn> active = service.getSubscriptionAddOns(id, loggedInCustomer.getCustomerId());
            System.out.println("\nACTIVE ADD-ONS");
            if (active.isEmpty()) System.out.println("  No active add-ons for this subscription.");
            active.forEach(a -> System.out.printf("  #%d  %s | Rs %.2f/month%n", a.id(), a.name(), a.monthlyPrice()));
            System.out.println("\nAVAILABLE ADD-ONS");
            service.getCatalogue().forEach(a -> System.out.printf("%d. %s - Rs %.2f/month%n",a.id(),a.name(),a.monthlyPrice()));
            System.out.print("Add-on ID (0 to return): "); int addon=readInt(); if(addon<=0) return;
            System.out.print("Activate or deactivate (A/D): "); String action=scanner.nextLine().trim();
            if(!action.equalsIgnoreCase("A") && !action.equalsIgnoreCase("D")) { System.out.println("Choose A or D."); return; }
            service.setActive(id,addon,loggedInCustomer.getCustomerId(),action.equalsIgnoreCase("A"));
            System.out.println("Add-on updated. Prorated charges appear in My Bills; credits apply to the next monthly bill.");
        } catch(Exception e) { System.out.println(e.getMessage()); }
    }

    private void monthlyUsage() {
        try {
            System.out.print("Billing month (yyyy-MM): "); String month=scanner.nextLine().trim();
            Map<String,Double> totals=usageService.getCustomerMonthlySummary(loggedInCustomer.getCustomerId(),month);
            totals.forEach((type,value) -> System.out.printf("%s: %.2f %s%n",type,value,com.amdocs.telecom.util.UsageUnits.unit(UsageType.valueOf(type))));
            if(totals.isEmpty()) System.out.println("No usage in this month.");
        } catch(Exception e) { System.out.println("Could not load monthly usage: " + e.getMessage()); }
    }

    private String profileValue(String label,String current) {
        System.out.print(label+" ["+current+"]: "); String value=scanner.nextLine().trim(); return value.isEmpty()?current:value;
    }
    private void updateProfile() {
        try {
            Customer c=customerService.getCustomerById(loggedInCustomer.getCustomerId());
            c.setFirstName(profileValue("First name",c.getFirstName())); c.setLastName(profileValue("Last name",c.getLastName()));
            c.setAddress(profileValue("Address",c.getAddress())); c.setCity(profileValue("City",c.getCity())); c.setCountry(profileValue("Country",c.getCountry()));
            customerService.updateProfile(c); loggedInCustomer=c; System.out.println("Profile updated.");
        } catch(Exception e) { System.out.println(e.getMessage()); }
    }
    private void profileMenu() {
        ConsoleMenu.show("MY PROFILE", null, "1. View Profile", "2. Edit Profile", "0. Back");
        System.out.print("Profile option: ");
        switch (readInt()) {
            case 1 -> viewProfile();
            case 2 -> updateProfile();
            case 0 -> { }
            default -> System.out.println("Invalid option.");
        }
    }

    private void usageMenu() {
        ConsoleMenu.show("USAGE", null, "1. Usage History (all dates)", "2. Monthly Usage Summary", "0. Back");
        System.out.print("Usage option: ");
        switch (readInt()) {
            case 1 -> viewUsage();
            case 2 -> monthlyUsage();
            case 0 -> { }
            default -> System.out.println("Invalid option.");
        }
    }

    private void explorePlans() {
        ConsoleMenu.show("EXPLORE PLANS", null,
                "1. View All Available Plans", "2. Search by Name", "3. Filter by Maximum Price",
                "4. Filter by Price Range", "5. Filter by Minimum Data", "6. Sort by Price",
                "7. Compare Two Plans", "0. Back");
        System.out.print("Plan option: ");
        try {
            List<TelecomPlan> plans;
            switch (readInt()) {
                case 0 -> { return; }
                case 1 -> { browsePlans(); return; }
                case 2 -> { searchPlans(); return; }
                case 3 -> { filterPlansByPrice(); return; }
                case 4 -> {
                    System.out.print("Minimum price: ");
                    double min = Double.parseDouble(scanner.nextLine().trim());
                    System.out.print("Maximum price: ");
                    plans = planService.filterPlansByPriceRange(min, Double.parseDouble(scanner.nextLine().trim()));
                }
                case 5 -> {
                    System.out.print("Minimum GB: ");
                    plans = planService.filterPlansByMinData(readInt());
                }
                case 6 -> {
                    System.out.print("Sort order (A = ascending, D = descending): ");
                    String order = scanner.nextLine().trim();
                    if (!order.equalsIgnoreCase("A") && !order.equalsIgnoreCase("D")) {
                        System.out.println("Choose A or D.");
                        return;
                    }
                    plans = planService.sortPlansByPrice(order.equalsIgnoreCase("A"));
                }
                case 7 -> {
                    browsePlans();
                    System.out.print("First plan ID: ");
                    int first = readInt();
                    System.out.print("Second plan ID: ");
                    plans = planService.comparePlans(first, readInt());
                }
                default -> { System.out.println("Invalid option."); return; }
            }
            if (plans.isEmpty()) System.out.println("No matching plans found.");
            plans.forEach(System.out::println);
        } catch (Exception e) {
            System.out.println("Could not load plans: " + e.getMessage());
        }
    }

    private void viewProfile() {
        Customer c = loggedInCustomer;
        System.out.println("\nMY PROFILE");
        System.out.println("  Customer No : " + c.getCustomerNumber());
        System.out.println("  Name        : " + c.getFullName());
        System.out.println("  DOB         : " + c.getDateOfBirth());
        System.out.println("  Email       : " + c.getEmail());
        System.out.println("  Mobile      : " + c.getMobileNumber());
        System.out.println("  Address     : " + c.getAddress());
        System.out.println("  City        : " + c.getCity());
        System.out.println("  Country     : " + c.getCountry());
        System.out.println("  Status      : " + c.getAccountStatus());
        System.out.println("  Registered  : " + (c.getRegistrationDate() == null ? "N/A"
                : c.getRegistrationDate().format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm"))));
        System.out.println("------------------------------------------------------------");
    }

    private void browsePlans() {
        System.out.println("\nAVAILABLE TELECOM PLANS");
        List<TelecomPlan> plans = planService.getAllActivePlans();
        ConsoleTable table = new ConsoleTable(5, 10, 16, 8, 6, 10, 10, 12);
        if (plans.isEmpty()) {
            System.out.println("  No active plans available.");
        } else {
            table.header("ID", "Code", "Name", "Type", "Data", "Voice", "Roaming", "Price");
            for (TelecomPlan p : plans) {
                table.row(
                        p.getPlanId(), p.getPlanCode(), p.getPlanName(), p.getPlanType(),
                        p.getDataAllowanceGB() + "GB", p.getVoiceDisplay(),
                        p.isInternationalRoaming() ? "Yes" : "No", String.format("Rs %.2f", p.getMonthlyRental()));
            }
        }
        table.separator();
    }

    private void searchPlans() {
        System.out.print("Enter plan name keyword: ");
        String keyword = scanner.nextLine().trim();
        List<TelecomPlan> results = planService.searchPlansByName(keyword);
        System.out.println("Found " + results.size() + " plan(s):");
        results.forEach(p -> System.out.println("  " + p));
    }

    private void filterPlansByPrice() {
        System.out.print("Enter maximum monthly price (Rs ): ");
        double max = readDouble();
        List<TelecomPlan> results = planService.filterPlansByMaxPrice(max);
        System.out.println("Plans within Rs " + max + ":");
        results.forEach(p -> System.out.println("  " + p));
    }

    private void viewSubscriptions() {
        List<MobileSubscription> subs = subscriptionService.getCustomerSubscriptions(loggedInCustomer.getCustomerId());
        System.out.println("\nMY SUBSCRIPTIONS");
        if (subs.isEmpty()) {
            System.out.println("  No active subscriptions.");
        } else {
            for (MobileSubscription s : subs) {
                System.out.println("  " + s);
            }
        }
        System.out.println("------------------------------------------------------------");
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
            System.out.println("\n[OK] Subscription created successfully!");
            System.out.println("   Subscription No : " + sub.getSubscriptionNumber());
            System.out.println("   Mobile Number   : " + sub.getMobileNumber());
            System.out.println("   SIM Type        : " + simType);

            TelecomPlan chosenPlan = null;
            try {
                chosenPlan = planService.getPlanById(planId);
            } catch (Exception ignored) {}
            String planName = (chosenPlan != null ? chosenPlan.getPlanName() + " (" + chosenPlan.getPlanCode() + ")" : "Plan #" + planId);

            List<Bill> bills = billingService.getCustomerBills(loggedInCustomer.getCustomerId());
            Bill newBill = bills.stream()
                    .filter(b -> b.getSubscriptionId() == sub.getSubscriptionId())
                    .findFirst()
                    .orElse(null);

            if (newBill != null) {
                System.out.println("\n--- Initial Bill Generated ---");
                System.out.printf("Bill No: %s | Total Amount: Rs %.2f (Rental: Rs %.2f + 18%% GST: Rs %.2f)%n",
                        newBill.getBillNumber(), newBill.getTotalAmount(), newBill.getPlanRental(), newBill.getTaxAmount());
                System.out.print("Proceed to pay now with UPI/Card to activate your plan (Y/N, Default: Y): ");
                String payNow = scanner.nextLine().trim();
                if (!"N".equalsIgnoreCase(payNow)) {
                    executePaymentWithMethod(newBill, "NEW PLAN PURCHASE / SUBSCRIPTION", planName, sub.getMobileNumber());
                } else {
                    System.out.println("[Warning] Payment pending. You can pay anytime via Option 8 (Make a Payment).");
                    printConsoleReceipt(newBill, null, "NEW PLAN PURCHASE / SUBSCRIPTION (PENDING PAYMENT)", planName, sub.getMobileNumber(), "PENDING");
                    offerDownloadInvoice(newBill, null, planName, sub.getMobileNumber(), "PENDING");
                }
            }
        } catch (TelecomException e) {
            System.out.println("[Error] " + e.getMessage());
        }
    }

    private void changePlan() {
        List<MobileSubscription> subs = subscriptionService.getCustomerSubscriptions(loggedInCustomer.getCustomerId());
        System.out.println("\nMY SUBSCRIPTIONS");
        if (subs.isEmpty()) {
            System.out.println("  No active subscriptions found.");
            System.out.println("------------------------------------------------------------");
            return;
        }
        for (MobileSubscription s : subs) {
            System.out.println(s);
        }
        System.out.println("------------------------------------------------------------");

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
            System.out.println("[Error] Could not find a subscription matching: " + input);
            return;
        }

        System.out.println("\nSelected: " + target.getSubscriptionNumber() + " (" + target.getMobileNumber() + ") - Current Plan: " + target.getPlanName());
        browsePlans();
        System.out.print("Enter new Plan ID: ");
        int newPlanId = readInt();
        if (newPlanId <= 0) return;

        try {
                boolean success = subscriptionService.changePlan(target.getSubscriptionId(), newPlanId,
                    loggedInCustomer.getCustomerId(), loggedInCustomer.getUsername());
            if (success) {
                System.out.println("[OK] Plan changed successfully for " + target.getSubscriptionNumber() + " (" + target.getMobileNumber() + ")!");

                TelecomPlan newPlan = null;
                try {
                    newPlan = planService.getPlanById(newPlanId);
                } catch (Exception ignored) {}
                String newPlanName = (newPlan != null ? newPlan.getPlanName() + " (" + newPlan.getPlanCode() + ")" : "Plan #" + newPlanId);

                try {
                    Bill changeBill = billingService.generatePlanChangeBill(target.getSubscriptionId(), newPlanId);
                    System.out.println("\n--- Plan Change Invoice Generated ---");
                    System.out.printf("Bill No: %s | Total Amount: Rs %.2f (Rental: Rs %.2f + 18%% GST: Rs %.2f)%n",
                            changeBill.getBillNumber(), changeBill.getTotalAmount(), changeBill.getPlanRental(), changeBill.getTaxAmount());
                    if (changeBill.getTotalAmount()==0) {
                        System.out.println("No additional payment is due. Any downgrade credit will apply to your next monthly bill.");
                        return;
                    }
                    System.out.print("Proceed to pay now with UPI/Card (Y/N, Default: Y): ");
                    String payNow = scanner.nextLine().trim();
                    if (!"N".equalsIgnoreCase(payNow)) {
                        executePaymentWithMethod(changeBill, "PLAN CHANGE / UPGRADE", newPlanName, target.getMobileNumber());
                    } else {
                        System.out.println("[Warning] Payment pending. You can pay anytime via Option 8 (Make a Payment).");
                        printConsoleReceipt(changeBill, null, "PLAN CHANGE / UPGRADE (PENDING PAYMENT)", newPlanName, target.getMobileNumber(), "PENDING");
                        offerDownloadInvoice(changeBill, null, newPlanName, target.getMobileNumber(), "PENDING");
                    }
                } catch (TelecomException e) {
                    System.out.println("Billing note: " + e.getMessage());
                }
            }
        } catch (TelecomException e) {
            System.out.println("[Error] " + e.getMessage());
        }
    }

    private void viewBills() {
        List<Bill> bills = billingService.getCustomerBills(loggedInCustomer.getCustomerId());
        System.out.println("\nMY BILLS");
        if (bills.isEmpty()) {
            System.out.println("  No bills found.");
            System.out.println("------------------------------------------------------------");
            return;
        }

        for (Bill b : bills) {
            System.out.println("  " + b);
        }
        System.out.println("------------------------------------------------------------");

        System.out.print("\nEnter Bill Number or ID to view full invoice on console (or 0 to return): ");
        String bChoice = scanner.nextLine().trim();
        if (!bChoice.isEmpty() && !"0".equals(bChoice) && !"cancel".equalsIgnoreCase(bChoice)) {
            Bill selected = bills.stream().filter(b ->
                    bChoice.equalsIgnoreCase(b.getBillNumber())
                    || bChoice.equalsIgnoreCase(String.valueOf(b.getBillId()))
            ).findFirst().orElse(null);
            if (selected != null) {
                printConsoleReceipt(selected, null, "ACCOUNT BILL INVOICE", "Telecom Subscription Plan", selected.getMobileNumber(), selected.getBillStatus());
                offerDownloadInvoice(selected, null, "Telecom Subscription Plan", selected.getMobileNumber(), selected.getBillStatus());
            } else {
                System.out.println("[Error] Bill not found.");
            }
        }
    }

    private void makePayment() {
        List<Bill> bills = billingService.getCustomerBills(loggedInCustomer.getCustomerId());
        if (bills.isEmpty()) {
            System.out.println("\n  No bills to pay.");
            return;
        }

        System.out.println("\nPENDING BILLS");
        boolean hasUnpaid = false;
        for (Bill b : bills) {
            if (!"PAID".equalsIgnoreCase(b.getBillStatus())) {
                System.out.printf("  Bill #%d [%s] - Due: %s - Total: Rs %.2f - Status: %s%n",
                        b.getBillId(), b.getBillNumber(), b.getDueDate(), b.getTotalAmount(), b.getBillStatus());
                hasUnpaid = true;
            }
        }
        if (!hasUnpaid) {
            System.out.println("  All your bills are already PAID! No payments due.");
            System.out.println("------------------------------------------------------------");
            return;
        }
        System.out.println("------------------------------------------------------------");

        System.out.print("Enter Bill Number (e.g. BILL-1001) or Bill ID to pay (or 'cancel'): ");
        String billInput = scanner.nextLine().trim();
        if ("cancel".equalsIgnoreCase(billInput) || "0".equals(billInput) || billInput.isEmpty()) return;

        Bill target = bills.stream().filter(b ->
                billInput.equalsIgnoreCase(b.getBillNumber())
                || billInput.equalsIgnoreCase(String.valueOf(b.getBillId()))
        ).findFirst().orElse(null);

        if (target == null) {
            System.out.println("[Error] Bill '" + billInput + "' not found.");
            return;
        }
        if ("PAID".equalsIgnoreCase(target.getBillStatus())) {
            System.out.println("[Warning] This bill is already PAID.");
            return;
        }

        executePaymentWithMethod(target, "MONTHLY BILL PAYMENT", "Telecom Subscription Plan", target.getMobileNumber());
    }

    private Payment executePaymentWithMethod(Bill target, String transactionType, String planName, String mobileNumber) {
        System.out.println("\nPAYMENT CHECKOUT");
        System.out.printf("  Invoice Number: %s%n", target.getBillNumber());
        System.out.printf("  Total Amount  : Rs %.2f%n", target.getTotalAmount());
        System.out.println("  Select Payment Method:");
        System.out.println("    1. UPI (Google Pay, PhonePe, Paytm, BHIM)");
        System.out.println("    2. Credit / Debit Card (Visa, Mastercard, RuPay)");
        System.out.println("    3. Net Banking");
        System.out.println("    4. Bank Transfer (simulated)");
        System.out.println("    0. Cancel");
        System.out.println("------------------------------------------------------------");
        System.out.print("Enter choice (0-4, Default: 1): ");
        String modeChoice = scanner.nextLine().trim();
        if ("0".equals(modeChoice) || "cancel".equalsIgnoreCase(modeChoice)) return null;
        if (!java.util.Set.of("", "1", "2", "3", "4", "upi", "card", "netbanking", "bank_transfer")
                .contains(modeChoice.toLowerCase(java.util.Locale.ROOT))) {
            System.out.println("[Error] Invalid payment method.");
            return null;
        }

        String paymentMode = "UPI";
        String paymentInstrumentDetail = "UPI";

        if ("2".equals(modeChoice) || "card".equalsIgnoreCase(modeChoice)) {
            paymentMode = "CARD";
            System.out.print("Enter 16-Digit Card Number: ");
            String cardNum = scanner.nextLine().trim().replaceAll("\\s+", "");
            if (cardNum.isEmpty()) cardNum = "4532110098761234";

            System.out.print("Enter Card Expiry (MM/YY, e.g. 10/28): ");
            String expiry = scanner.nextLine().trim();

            System.out.print("Enter CVV (3 digits): ");
            String cvv = scanner.nextLine().trim();

            if (!cardNum.matches("\\d{16}")) {
                System.out.println("[Error] Card number must contain exactly 16 digits.");
                return null;
            }
            if (!expiry.matches("(0[1-9]|1[0-2])/\\d{2}")) {
                System.out.println("[Error] Card expiry must use MM/YY format.");
                return null;
            }
            int expiryYear = 2000 + Integer.parseInt(expiry.substring(3));
            int expiryMonth = Integer.parseInt(expiry.substring(0, 2));
            if (YearMonth.of(expiryYear, expiryMonth).isBefore(YearMonth.now())) {
                System.out.println("[Error] Card has expired.");
                return null;
            }
            if (!cvv.matches("\\d{3,4}")) {
                System.out.println("[Error] CVV must contain 3 or 4 digits.");
                return null;
            }

            String maskedCard = (cardNum.length() >= 4)
                    ? "XXXX-XXXX-XXXX-" + cardNum.substring(cardNum.length() - 4)
                    : "XXXX-XXXX-XXXX-1234";
            paymentInstrumentDetail = "Card (" + maskedCard + ", Exp: " + expiry + ")";

            System.out.println("  Connecting to Card Payment Gateway...");
            System.out.println("  [OK] Card authorization successful!");

        } else if ("3".equals(modeChoice) || "netbanking".equalsIgnoreCase(modeChoice)) {
            paymentMode = "NET_BANKING";
            System.out.println("Select Bank:");
            System.out.println("  1. State Bank of India (SBI)");
            System.out.println("  2. HDFC Bank");
            System.out.println("  3. ICICI Bank");
            System.out.println("  4. Axis Bank");
            System.out.println("  5. Other Bank");
            System.out.print("Enter choice (1-5, Default: 1): ");
            String bankChoice = scanner.nextLine().trim();
            String bankName = switch (bankChoice) {
                case "2" -> "HDFC Bank";
                case "3" -> "ICICI Bank";
                case "4" -> "Axis Bank";
                case "5" -> "Other Net Banking";
                default -> "State Bank of India (SBI)";
            };
            paymentInstrumentDetail = "Net Banking (" + bankName + ")";
            System.out.println("  Connecting to Net Banking Gateway (" + bankName + ")...");
            System.out.println("  [OK] Bank authentication successful!");

        } else if ("4".equals(modeChoice) || "bank_transfer".equalsIgnoreCase(modeChoice)) {
            paymentMode = "BANK_TRANSFER";
            paymentInstrumentDetail = "Bank Transfer (simulated)";
        } else {
            paymentMode = "UPI";
            System.out.print("Enter your UPI ID (e.g. " + loggedInCustomer.getUsername() + "@oksbi / 9876543210@upi): ");
            String upiId = scanner.nextLine().trim();
            if (upiId.isEmpty()) {
                upiId = loggedInCustomer.getUsername() + "@oksbi";
            }
            if (!upiId.matches("[A-Za-z0-9._-]+@[A-Za-z0-9.-]+")) {
                System.out.println("[Error] Invalid UPI ID format.");
                return null;
            }
            paymentInstrumentDetail = "UPI (ID: " + upiId + ")";
            System.out.println("  Sending payment request to UPI ID: " + upiId + " ...");
            System.out.println("  [OK] UPI payment authorized successfully!");
        }

        try {
            Payment payment = paymentService.processPayment(
                    target.getBillId(),
                    loggedInCustomer.getCustomerId(),
                    target.getTotalAmount(),
                    paymentMode,
                    loggedInCustomer.getUsername()
            );

            printConsoleReceipt(target, payment, transactionType, planName, mobileNumber, paymentInstrumentDetail);
            offerDownloadInvoice(target, payment, planName, mobileNumber, paymentInstrumentDetail);
            return payment;

        } catch (TelecomException e) {
            System.out.println("[Error] Payment Failed: " + e.getMessage());
            return null;
        }
    }

    private void offerDownloadInvoice(Bill bill, Payment payment, String planName, String mobileNumber, String paymentDetail) {
        System.out.println("DOWNLOAD INVOICE OPTIONS");
        System.out.println("  1. Download as PDF (.pdf)");
        System.out.println("  2. Download as Printable HTML (.html)");
        System.out.println("  3. Download as Formatted Text (.txt)");
        System.out.println("  4. Download All Formats (PDF + HTML + Text)");
        System.out.println("  5. Skip (Console only)");
        System.out.println("------------------------------------------------------------");
        System.out.print("Select download option (1-5, Default: 5): ");
        String choice = scanner.nextLine().trim();

        if (choice.isEmpty() || "5".equals(choice) || "skip".equalsIgnoreCase(choice)) {
            return;
        }

        String baseName = "Invoice_" + (bill.getBillNumber() != null ? bill.getBillNumber() : "BILL_" + bill.getBillId());
        String payMode = (payment != null) ? payment.getPaymentMode().toString() : bill.getBillStatus();
        String txnRef = (payment != null) ? payment.getTransactionReference() : null;

        try {
            if ("1".equals(choice) || "pdf".equalsIgnoreCase(choice)) {
                String pdfPath = reportGenerator.generateBillInvoicePdf(bill, loggedInCustomer, planName, payMode, txnRef, baseName + ".pdf");
                System.out.println("[OK] PDF Invoice downloaded successfully!");
                System.out.println("    File Path: " + pdfPath);
            } else if ("2".equals(choice) || "html".equalsIgnoreCase(choice)) {
                String htmlPath = reportGenerator.generateBillInvoiceHtml(bill, loggedInCustomer, planName, payMode, txnRef, baseName + ".html");
                System.out.println("[OK] Printable HTML Invoice downloaded successfully!");
                System.out.println("    File Path: " + htmlPath);
            } else if ("3".equals(choice) || "txt".equalsIgnoreCase(choice)) {
                String txtPath = reportGenerator.generateBillInvoiceText(bill, loggedInCustomer, planName, payMode, txnRef, baseName + ".txt");
                System.out.println("[OK] Text Invoice downloaded successfully!");
                System.out.println("    File Path: " + txtPath);
            } else if ("4".equals(choice) || "all".equalsIgnoreCase(choice)) {
                String pdfPath = reportGenerator.generateBillInvoicePdf(bill, loggedInCustomer, planName, payMode, txnRef, baseName + ".pdf");
                String htmlPath = reportGenerator.generateBillInvoiceHtml(bill, loggedInCustomer, planName, payMode, txnRef, baseName + ".html");
                String txtPath = reportGenerator.generateBillInvoiceText(bill, loggedInCustomer, planName, payMode, txnRef, baseName + ".txt");
                System.out.println("[OK] All invoice formats generated successfully!");
                System.out.println("   • PDF  : " + pdfPath);
                System.out.println("   • HTML : " + htmlPath);
                System.out.println("   • Text : " + txtPath);
            }
        } catch (Exception e) {
            System.out.println("[Error] Failed to download invoice: " + e.getMessage());
        }
    }

    private void printConsoleReceipt(Bill bill, Payment payment, String transactionType, String planName, String mobileNumber, String paymentDetail) {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm:ss");
        String currentDateTime = java.time.LocalDateTime.now().format(dtf);

        System.out.println("\n------------------------------------------------------------");
        System.out.println("TELECOM OFFICIAL INVOICE & RECEIPT");
        System.out.println("Powered by Amdocs TCSMS");
        System.out.println("------------------------------------------------------------");
        System.out.printf("Invoice / Bill No : %-55s %n", bill.getBillNumber());
        System.out.printf("Date & Time       : %-55s %n", currentDateTime);
        System.out.printf("Transaction Type  : %-55s %n", transactionType);
        if (payment != null) {
            System.out.printf("Transaction Ref   : %-55s %n", payment.getTransactionReference());
        }
        System.out.println("------------------------------------------------------------");
        System.out.println("CUSTOMER DETAILS");
        System.out.printf("Customer ID       : %-55s %n", loggedInCustomer.getCustomerNumber());
        System.out.printf("Customer Name     : %-55s %n", loggedInCustomer.getFullName());
        System.out.printf("Mobile Number     : %-55s %n", (mobileNumber != null ? mobileNumber : loggedInCustomer.getMobileNumber()));
        System.out.printf("Email Address     : %-55s %n", loggedInCustomer.getEmail());
        System.out.printf("Address / City    : %-55s %n", loggedInCustomer.getAddress() + ", " + loggedInCustomer.getCity());
        System.out.println("------------------------------------------------------------");
        System.out.println("PURCHASE & PLAN DETAILS");
        System.out.printf("Plan Name         : %-55s %n", (planName != null ? planName : "Telecom Plan"));
        System.out.printf("Billing Cycle     : %-55s %n", bill.getBillingMonth());
        System.out.printf("Payment Due Date  : %-55s %n", bill.getDueDate());
        System.out.println("------------------------------------------------------------");
        System.out.println("CHARGES BREAKDOWN");
        System.out.printf("• Plan Monthly Rental                : Rs  %10.2f                       %n", bill.getPlanRental());
        System.out.printf("• Usage / Add-on Charges             : Rs  %10.2f                       %n", bill.getUsageCharges());
        System.out.printf("• Applicable Tax (18%% GST)          : Rs  %10.2f                       %n", bill.getTaxAmount());
        if (bill.getDiscount() > 0) {
            System.out.printf("• Promotional Discount               : -Rs %10.2f                       %n", bill.getDiscount());
        }
        System.out.println("------------------------------------------------------------");
        System.out.printf("NET TOTAL AMOUNT                       : Rs  %10.2f                       %n", bill.getTotalAmount());
        System.out.println("------------------------------------------------------------");
        System.out.println("PAYMENT INFORMATION");
        if (payment != null) {
            System.out.printf("Payment Status    : %-55s %n", "PAID (SUCCESS)");
            System.out.printf("Payment Mode      : %-55s %n", payment.getPaymentMode());
            System.out.printf("Payment Details   : %-55s %n", (paymentDetail != null ? paymentDetail : payment.getPaymentMode().toString()));
            System.out.printf("Amount Paid       : Rs  %-53.2f %n", payment.getAmount());
            System.out.printf("Paid Timestamp    : %-55s %n", (payment.getPaymentDate() != null ? payment.getPaymentDate().format(dtf) : currentDateTime));
        } else {
            String status = "PAID".equalsIgnoreCase(bill.getBillStatus()) ? "PAID" : "UNPAID (Pending Payment)";
            System.out.printf("Payment Status    : %-55s %n", status);
            System.out.printf("Amount Due        : Rs  %-53.2f %n", bill.getTotalAmount());
            System.out.printf("Payment Note      : %-55s %n", "Can be paid via Customer Dashboard -> Make a Payment");
        }
        System.out.println("------------------------------------------------------------");
        System.out.println("       Thank you for choosing Amdocs Telecom! This is an official console e-bill.\n");
    }

    private void viewUsage() {
        List<UsageRecord> usage = usageService.getCustomerUsageHistory(loggedInCustomer.getCustomerId());
        System.out.println("\nUSAGE HISTORY");
        if (usage.isEmpty()) {
            System.out.println("  No usage records found.");
        } else {
            usage.stream().limit(20).forEach(u -> System.out.println("  " + u));
            if (usage.size() > 20) System.out.println("  ... and " + (usage.size() - 20) + " more records.");
        }

        List<MobileSubscription> subs = subscriptionService.getCustomerSubscriptions(loggedInCustomer.getCustomerId());
        for (MobileSubscription sub : subs) {
            Map<String, Double> summary = usageService.getUsageSummary(sub.getSubscriptionId());
            if (!summary.isEmpty()) {
                System.out.println("\n  Summary for " + sub.getSubscriptionNumber() + ":");
                summary.forEach((type, qty) -> System.out.printf("    %-10s : %.1f %s%n", type, qty, com.amdocs.telecom.util.UsageUnits.unit(UsageType.valueOf(type))));
            }
        }
        System.out.println("------------------------------------------------------------");
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
            System.out.println("[Warning] Description cannot be empty.");
            return;
        }

        try {
            Complaint complaint = complaintService.lodgeComplaint(loggedInCustomer.getCustomerId(), cat.name(), description);

            System.out.println("\n[OK] Complaint registered successfully!");
            System.out.println("   Ticket Number : " + complaint.getComplaintNumber());
            System.out.println("   Category      : " + complaint.getCategory());
            System.out.println("   Status        : " + complaint.getStatus());
            System.out.println("[Info] You can track status and view resolution at any time from Dashboard -> '11. Track Complaints'.");
        } catch (Exception e) {
            System.out.println("[Error] Error registering complaint: " + e.getMessage());
        }
    }

    private void trackCustomerComplaints() {
        List<Complaint> list = complaintService.getCustomerComplaints(loggedInCustomer.getCustomerId());
        System.out.println("\nMY COMPLAINTS & SOLUTIONS");
        ConsoleTable table = new ConsoleTable(3, 12, 16, 10, 8, 12, 23);
        if (list.isEmpty()) {
            System.out.println("  You have not raised any complaints yet.");
            System.out.println("  If you need help, please select Option 10 'Raise a Complaint'.");
            table.separator();
            return;
        }

        table.header(
                "#", "Ticket No", "Date Filed", "Category", "Priority", "Status", "Resolution Status");

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        int index = 1;
        for (Complaint c : list) {
            String dateStr = c.getCreatedDate() != null ? c.getCreatedDate().format(dtf) : "N/A";
            String resStatus = (c.getResolution() != null && !c.getResolution().trim().isEmpty()) ?
                    "[OK] Solution Available" : "[Pending] Under Review";
            String statusBadge = switch (c.getStatus()) {
                case "RESOLVED" -> "RESOLVED";
                case "CLOSED" -> "CLOSED";
                case "IN_PROGRESS" -> "IN_PROGRESS";
                default -> "OPEN";
            };
            table.row(
                    index++, c.getComplaintNumber(), dateStr, c.getCategory(), c.getPriority(), statusBadge, resStatus);
        }
        table.separator();

        System.out.print("\nEnter Ticket Number or # (1-" + list.size() + ") to view complete solution details (or 0 to return): ");
        String input = scanner.nextLine().trim();
        if ("0".equals(input) || input.isEmpty() || "cancel".equalsIgnoreCase(input)) return;

        Complaint selected = null;
        try {
            int num = Integer.parseInt(input);
            if (num >= 1 && num <= list.size()) {
                selected = list.get(num - 1);
            }
        } catch (NumberFormatException ignored) {}

        if (selected == null) {
            for (Complaint c : list) {
                if (c.getComplaintNumber().equalsIgnoreCase(input)) {
                    selected = c;
                    break;
                }
            }
        }

        if (selected == null) {
            System.out.println("[Error] Complaint not found for: " + input);
            return;
        }

        printCustomerComplaintCard(selected);
    }

    private void printCustomerComplaintCard(Complaint cp) {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        String dateStr = cp.getCreatedDate() != null ? cp.getCreatedDate().format(dtf) : "N/A";
        String statusSymbol = switch (cp.getStatus()) {
            case "RESOLVED" -> "[OK] RESOLVED (Issue Resolved)";
            case "CLOSED" -> " CLOSED";
            case "IN_PROGRESS" -> " IN_PROGRESS (Investigation in Progress)";
            default -> "[Pending] OPEN (Pending Review)";
        };

        System.out.println("\n------------------------------------------------------------");
        System.out.println("COMPLAINT STATUS & RESOLUTION CARD");
        System.out.println("------------------------------------------------------------");
        System.out.printf("Ticket Number   : %-53s %n", cp.getComplaintNumber());
        System.out.printf("Date Lodged     : %-53s %n", dateStr);
        System.out.printf("Category        : %-53s %n", cp.getCategory());
        System.out.printf("Priority        : %-53s %n", cp.getPriority());
        System.out.printf("Current Status  : %-53s %n", statusSymbol);
        System.out.println("------------------------------------------------------------");
        System.out.println("YOUR ISSUE DESCRIPTION:");
        printWrappedBoxTextCustomer(cp.getDescription(), 70);
        System.out.println("------------------------------------------------------------");
        System.out.println("OFFICIAL RESOLUTION & SOLUTION:");
        if (cp.getResolution() != null && !cp.getResolution().trim().isEmpty()) {
            System.out.println("[OK] Action Taken / Solution:");
            printWrappedBoxTextCustomer(cp.getResolution(), 70);
        } else {
            System.out.println("[Pending] Under Review:");
            System.out.println("Our technical and customer support team is actively investigating your");
            System.out.println("issue. A resolution will be posted here as soon as it is completed.");
        }
        System.out.println("------------------------------------------------------------");
    }

    private void printWrappedBoxTextCustomer(String text, int maxWidth) {
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

    private void viewNotifications() {
        try {
            com.amdocs.telecom.dao.AuditAndNotificationDAO dao = new com.amdocs.telecom.dao.impl.AuditAndNotificationDAOImpl();
            List<Notification> notifs = dao.getNotificationsForCustomer(loggedInCustomer.getCustomerId());
            System.out.println("\nNOTIFICATIONS");
            if (notifs.isEmpty()) {
                System.out.println("  No notifications.");
            } else {
                for (Notification n : notifs) {
                    System.out.println("  " + n);
                }
            }
            System.out.println("------------------------------------------------------------");
        } catch (Exception e) {
            System.out.println("[Error] Error: " + e.getMessage());
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
