package com.amdocs.telecom.main;

import com.amdocs.telecom.controller.AdminController;
import com.amdocs.telecom.controller.CustomerController;
import com.amdocs.telecom.scheduler.PaymentNotificationService;
import com.amdocs.telecom.util.DBConnection;

import java.util.Scanner;

/**
 * Starts the application, database, background services, and console menu.
 */
public class MainApplication {

    private static PaymentNotificationService notificationService;

    public static void main(String[] args) {
        // Keep background logging from taking over the console
        java.util.logging.LogManager.getLogManager().reset();
        java.util.logging.Logger rootLogger = java.util.logging.Logger.getLogger("");
        rootLogger.setLevel(java.util.logging.Level.WARNING);

        // Set up the database and seed data
        DBConnection database = DBConnection.getInstance();
        System.out.println("Database initialized successfully: " + database.getActiveDatabaseName());

        // Start payment notifications
        notificationService = new PaymentNotificationService();
        notificationService.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("JVM shutdown intercepted. Stopping background services...");
            if (notificationService != null) {
                notificationService.shutdown();
            }
        }));

        try (Scanner scanner = new Scanner(System.in)) {
            boolean running = true;

            while (running) {
                printMainMenu();
                if (!scanner.hasNextLine()) {
                    System.out.println("\nExiting TCSMS...");
                    notificationService.shutdown();
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
                        notificationService.shutdown();
                        System.out.println("Thank you for using TCSMS. Goodbye!");
                    }
                    default -> System.out.println("Invalid option. Please try again.");
                }
            }
        }
    }

    private static void printMainMenu() {
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════╗");
        System.out.println("║                                              ║");
        System.out.println("║   TELECOM CUSTOMER & SUBSCRIPTION            ║");
        System.out.println("║       MANAGEMENT SYSTEM (TCSMS)              ║");
        System.out.println("║                                              ║");
        System.out.println("║           Powered by Amdocs                  ║");
        System.out.println("║                                              ║");
        System.out.println("╠══════════════════════════════════════════════╣");
        System.out.println("║                                              ║");
        System.out.println("║   1.  Customer Portal                        ║");
        System.out.println("║   2.  Administrator Portal                   ║");
        System.out.println("║   3.  Exit                                   ║");
        System.out.println("║                                              ║");
        System.out.println("╚══════════════════════════════════════════════╝");
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
