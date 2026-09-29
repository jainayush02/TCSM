package com.amdocs.telecom.report;

import com.amdocs.telecom.model.Bill;
import com.amdocs.telecom.model.Customer;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.DoubleSummaryStatistics;
import java.util.List;
import java.util.Map;

/**
 * ReportGenerator handles file operations including CSV data exports
 * and formatted text invoices as required by the case study specification.
 */
public class ReportGenerator {

    private static final String DEFAULT_REPORT_DIR = "reports";

    public ReportGenerator() {
        ensureReportDirectoryExists();
    }

    private void ensureReportDirectoryExists() {
        File dir = new File(DEFAULT_REPORT_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    /**
     * Exports customer records to a CSV file.
     */
    public String exportCustomersToCsv(List<Customer> customers, String fileName) throws Exception {
        ensureReportDirectoryExists();
        String targetPath = DEFAULT_REPORT_DIR + File.separator + fileName;
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(targetPath), StandardCharsets.UTF_8))) {
            
            // CSV Header
            writer.write("CustomerId,CustomerNumber,FullName,Email,MobileNumber,City,AccountStatus,RegistrationDate\n");
            
            for (Customer c : customers) {
                String line = String.format("%d,\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"\n",
                        c.getCustomerId(),
                        c.getCustomerNumber(),
                        c.getFullName().replace("\"", "\"\""),
                        c.getEmail(),
                        c.getMobileNumber(),
                        c.getCity(),
                        c.getAccountStatus(),
                        c.getRegistrationDate() != null ? c.getRegistrationDate().toString() : ""
                );
                writer.write(line);
            }
        }
        return new File(targetPath).getAbsolutePath();
    }

    /**
     * Exports monthly revenue summary statistics to a CSV file.
     */
    public String exportRevenueSummaryToCsv(Map<String, DoubleSummaryStatistics> revenueData, String fileName) throws Exception {
        ensureReportDirectoryExists();
        String targetPath = DEFAULT_REPORT_DIR + File.separator + fileName;
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(targetPath), StandardCharsets.UTF_8))) {
            
            // CSV Header
            writer.write("BillingMonth,TotalBills,TotalRevenue,AverageBillAmount,MinBillAmount,MaxBillAmount\n");
            
            for (Map.Entry<String, DoubleSummaryStatistics> entry : revenueData.entrySet()) {
                DoubleSummaryStatistics stats = entry.getValue();
                String line = String.format("\"%s\",%d,%.2f,%.2f,%.2f,%.2f\n",
                        entry.getKey(),
                        stats.getCount(),
                        stats.getSum(),
                        stats.getAverage(),
                        stats.getMin(),
                        stats.getMax()
                );
                writer.write(line);
            }
        }
        return new File(targetPath).getAbsolutePath();
    }

    /**
     * Generates a printable text format invoice for a customer bill.
     */
    public String generateBillInvoiceText(Bill bill, Customer customer, String fileName) throws Exception {
        ensureReportDirectoryExists();
        String targetPath = DEFAULT_REPORT_DIR + File.separator + fileName;
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm");

        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(targetPath), StandardCharsets.UTF_8))) {
            
            writer.write("========================================================================\n");
            writer.write("                     AMDOCS TELECOM SERVICES LIMITED                    \n");
            writer.write("                           OFFICIAL TAX INVOICE                         \n");
            writer.write("========================================================================\n");
            writer.write(String.format(" Invoice Number : %-20s   Generated: %s\n", 
                    bill.getBillNumber() != null ? bill.getBillNumber() : "INV-" + bill.getBillId(), 
                    java.time.LocalDateTime.now().format(dtf)));
            writer.write(String.format(" Billing Month  : %s\n", bill.getBillingMonth()));
            writer.write(String.format(" Due Date       : %s\n", bill.getDueDate()));
            writer.write(String.format(" Status         : %s\n", bill.getBillStatus()));
            writer.write("------------------------------------------------------------------------\n");
            writer.write(" CUSTOMER DETAILS:\n");
            if (customer != null) {
                writer.write(String.format(" Name           : %s\n", customer.getFullName()));
                writer.write(String.format(" Customer ID    : %s\n", customer.getCustomerNumber()));
                writer.write(String.format(" Mobile         : %s\n", customer.getMobileNumber()));
                writer.write(String.format(" Email          : %s\n", customer.getEmail()));
                writer.write(String.format(" City           : %s, %s\n", customer.getCity(), customer.getCountry()));
            } else {
                writer.write(String.format(" Customer ID    : #%d\n", bill.getCustomerId()));
            }
            writer.write("------------------------------------------------------------------------\n");
            writer.write(" CHARGES BREAKDOWN:\n");
            writer.write(String.format(" 1. Plan Base Rental Amount                 :  INR %10.2f\n", bill.getPlanRental()));
            writer.write(String.format(" 2. Discount / Prorated Credit              : -INR %10.2f\n", bill.getDiscount()));
            writer.write(String.format(" 3. Data & Voice Excess Usage Charges       :  INR %10.2f\n", bill.getUsageCharges()));
            writer.write(String.format(" 4. Goods & Services Tax (GST 18%%)          :  INR %10.2f\n", bill.getTaxAmount()));
            writer.write("------------------------------------------------------------------------\n");
            writer.write(String.format(" TOTAL AMOUNT PAYABLE                       :  INR %10.2f\n", bill.getTotalAmount()));
            writer.write("========================================================================\n");
            writer.write(" Payment Terms: Please pay on or before due date to avoid service interruption.\n");
            writer.write(" For support, reach out to support@amdocs-telecom.com or call 1800-123-456.\n");
            writer.write("========================================================================\n");
        }
        return new File(targetPath).getAbsolutePath();
    }
}
