package com.amdocs.telecom.main;

import com.amdocs.telecom.util.ConsoleMenu;

import com.amdocs.telecom.controller.AdminController;
import com.amdocs.telecom.controller.CustomerController;
import com.amdocs.telecom.scheduler.PaymentNotificationService;
import com.amdocs.telecom.util.DBConnection;

import java.util.Scanner;

public class MainApplication {

    private static PaymentNotificationService notificationService;
    private static final java.util.concurrent.atomic.AtomicBoolean stopped=new java.util.concurrent.atomic.AtomicBoolean();
    private static com.amdocs.telecom.scheduler.BillingScheduler billingScheduler;
    private static com.amdocs.telecom.scheduler.AccountMonitor accountMonitor;

    private static void stopServices() {
        if(!stopped.compareAndSet(false,true)) return;
        if(billingScheduler!=null) billingScheduler.shutdown();
        if(accountMonitor!=null) accountMonitor.shutdown();
        if(notificationService!=null) notificationService.shutdown();
        DBConnection.getInstance().shutdown();
    }

    public static void main(String[] args) {
        com.amdocs.telecom.util.ApplicationLogging.configure();

        DBConnection database = DBConnection.getInstance();
        System.out.println("Database initialized successfully: " + database.getActiveDatabaseName());

        notificationService = new PaymentNotificationService();
        notificationService.start();

        billingScheduler=new com.amdocs.telecom.scheduler.BillingScheduler();
        billingScheduler.start(60,3600);
        accountMonitor=new com.amdocs.telecom.scheduler.AccountMonitor();
        accountMonitor.start(60,3600);
        Runtime.getRuntime().addShutdownHook(new Thread(MainApplication::stopServices));

        try (Scanner scanner = new Scanner(System.in)) {
            boolean running = true;

            while (running) {
                printMainMenu();
                if (!scanner.hasNextLine()) {
                    System.out.println("\nExiting TCSMS...");
                    stopServices();
                    break;
                }
                int choice = readInt(scanner);

                switch (choice) {
                    case 1 -> {
                        CustomerController customerController = new CustomerController(scanner);
                        customerController.showLoginMenu();
                    }
                    case 2 -> {
                        AdminController adminController = new AdminController(scanner);
                        adminController.showLoginMenu();
                    }
                    case 3 -> {
                        running = false;
                        System.out.println("\nShutting down services...");
                        stopServices();
                        System.out.println("Thank you for using TCSMS. Goodbye!");
                    }
                    default -> System.out.println("Invalid option. Please try again.");
                }
            }
        } finally { stopServices(); }
    }

    private static void printMainMenu() {
        ConsoleMenu.show("TELECOM MANAGEMENT SYSTEM (TCSMS)", "Amdocs Telecom",
                "1. Customer Portal", "2. Administrator Portal", "3. Exit");
        System.out.print("Select option: ");
    }

    private static int readInt(Scanner scanner) {
        try {
            if (!scanner.hasNextLine()) return -1;
            String line = scanner.nextLine().trim();
            return Integer.parseInt(line);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public static PaymentNotificationService getNotificationService() {
        return notificationService;
    }
}
