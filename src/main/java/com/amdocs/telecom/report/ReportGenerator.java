package com.amdocs.telecom.report;

import com.amdocs.telecom.model.Bill;
import com.amdocs.telecom.model.Customer;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.DoubleSummaryStatistics;
import java.util.List;
import java.util.Map;

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

    public String exportCustomersToCsv(List<Customer> customers, String fileName) throws Exception {
        ensureReportDirectoryExists();
        String targetPath = DEFAULT_REPORT_DIR + File.separator + fileName;
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(targetPath), StandardCharsets.UTF_8))) {
            
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

    public String exportRevenueSummaryToCsv(Map<String, DoubleSummaryStatistics> revenueData, String fileName) throws Exception {
        ensureReportDirectoryExists();
        String targetPath = DEFAULT_REPORT_DIR + File.separator + fileName;
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(targetPath), StandardCharsets.UTF_8))) {
            
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

    public String generateBillInvoiceText(Bill bill, Customer customer, String fileName) throws Exception {
        return generateBillInvoiceText(bill, customer, null, null, null, fileName);
    }

    public String generateBillInvoiceText(Bill bill, Customer customer, String planName, String paymentMode, String txnRef, String fileName) throws Exception {
        ensureReportDirectoryExists();
        String targetPath = DEFAULT_REPORT_DIR + File.separator + fileName;
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm");

        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(targetPath), StandardCharsets.UTF_8))) {
            
            writer.write("========================================================================\n");
            writer.write("                     AMDOCS TELECOM SERVICES LIMITED                    \n");
            writer.write("                           OFFICIAL TAX INVOICE                         \n");
            writer.write("========================================================================\n");
            writer.write(String.format(" Invoice Number : %-25s   Generated: %s\n", 
                    bill.getBillNumber() != null ? bill.getBillNumber() : "INV-" + bill.getBillId(), 
                    java.time.LocalDateTime.now().format(dtf)));
            writer.write(String.format(" Billing Month  : %s\n", bill.getBillingMonth()));
            writer.write(String.format(" Due Date       : %s\n", bill.getDueDate()));
            writer.write(String.format(" Status         : %s\n", bill.getBillStatus()));
            if (txnRef != null && !txnRef.isEmpty()) {
                writer.write(String.format(" Txn Reference  : %s\n", txnRef));
            }
            if (paymentMode != null && !paymentMode.isEmpty()) {
                writer.write(String.format(" Payment Mode   : %s\n", paymentMode));
            }
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
            if (planName != null && !planName.isEmpty()) {
                writer.write(String.format(" Plan Name      : %s\n", planName));
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

    public String generateBillInvoicePdf(Bill bill, Customer customer, String planName, String paymentMode, String txnRef, String fileName) throws Exception {
        ensureReportDirectoryExists();
        String targetPath = DEFAULT_REPORT_DIR + File.separator + fileName;
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm");
        String genDate = java.time.LocalDateTime.now().format(dtf);

        StringBuilder sb = new StringBuilder();

        sb.append("0.05 0.15 0.35 rg\n"); // Navy blue
        sb.append("40 705 532 55 re f\n");

        sb.append("1 1 1 rg\n");
        sb.append("BT\n");
        sb.append("/F2 16 Tf\n");
        sb.append("55 738 Td\n");
        sb.append("(AMDOCS TELECOM SERVICES LIMITED) Tj\n");
        sb.append("/F1 10 Tf\n");
        sb.append("0 -16 Td\n");
        sb.append("(OFFICIAL TAX INVOICE & PAYMENT RECEIPT) Tj\n");
        sb.append("ET\n");

        boolean isPaid = "PAID".equalsIgnoreCase(bill.getBillStatus());
        if (isPaid) {
            sb.append("0.1 0.6 0.2 rg\n"); // Green
            sb.append("460 715 100 35 re f\n");
            sb.append("1 1 1 rg\n");
            sb.append("BT\n/F2 14 Tf\n488 728 Td\n(PAID) Tj\nET\n");
        } else {
            sb.append("0.8 0.2 0.1 rg\n"); // Red
            sb.append("460 715 100 35 re f\n");
            sb.append("1 1 1 rg\n");
            sb.append("BT\n/F2 13 Tf\n478 728 Td\n(UNPAID) Tj\nET\n");
        }

        sb.append("0.95 0.95 0.97 rg\n"); // Light gray background
        sb.append("40 620 532 75 re f\n");
        sb.append("0.8 0.8 0.85 RG\n"); // Border
        sb.append("1 w\n40 620 532 75 re S\n");

        sb.append("0 0 0 rg\n");
        sb.append("BT\n");
        sb.append("/F2 11 Tf\n55 675 Td\n(Invoice Details) Tj\n");
        sb.append("/F1 10 Tf\n");
        sb.append("0 -15 Td\n");
        sb.append(String.format("(Invoice Number: %s) Tj\n", escapePdf(bill.getBillNumber() != null ? bill.getBillNumber() : "INV-" + bill.getBillId())));
        sb.append("0 -13 Td\n");
        sb.append(String.format("(Billing Month: %s   |   Generated: %s) Tj\n", escapePdf(bill.getBillingMonth()), escapePdf(genDate)));
        sb.append("0 -13 Td\n");
        sb.append(String.format("(Due Date: %s   |   Status: %s) Tj\n", escapePdf(String.valueOf(bill.getDueDate())), escapePdf(bill.getBillStatus())));
        sb.append("ET\n");

        sb.append("0.95 0.95 0.97 rg\n");
        sb.append("40 510 532 100 re f\n");
        sb.append("0.8 0.8 0.85 RG\n");
        sb.append("40 510 532 100 re S\n");

        sb.append("0 0 0 rg\n");
        sb.append("BT\n");
        sb.append("/F2 11 Tf\n55 590 Td\n(Customer & Subscription Information) Tj\n");
        sb.append("/F1 10 Tf\n");
        sb.append("0 -15 Td\n");
        if (customer != null) {
            sb.append(String.format("(Customer Name : %s   |   Customer ID: %s) Tj\n", escapePdf(customer.getFullName()), escapePdf(customer.getCustomerNumber())));
            sb.append("0 -13 Td\n");
            sb.append(String.format("(Mobile Number : %s   |   Email: %s) Tj\n", escapePdf(customer.getMobileNumber()), escapePdf(customer.getEmail())));
            sb.append("0 -13 Td\n");
            sb.append(String.format("(City / Country: %s, %s) Tj\n", escapePdf(customer.getCity()), escapePdf(customer.getCountry())));
        } else {
            sb.append(String.format("(Customer ID: #%d) Tj\n", bill.getCustomerId()));
            sb.append("0 -13 Td\n");
        }
        sb.append("0 -13 Td\n");
        sb.append(String.format("(Subscribed Plan: %s) Tj\n", escapePdf(planName != null ? planName : "Telecom Plan")));
        sb.append("ET\n");

        sb.append("0.05 0.15 0.35 rg\n");
        sb.append("40 460 532 24 re f\n"); // Table header
        sb.append("1 1 1 rg\n");
        sb.append("BT\n/F2 10 Tf\n55 468 Td\n(Description) Tj\n380 0 Td\n(Amount \\(INR\\)) Tj\nET\n");

        sb.append("0 0 0 rg\n");
        sb.append("BT\n/F1 10 Tf\n");
        sb.append("55 438 Td\n(1. Monthly Plan Base Rental) Tj\n380 0 Td\n");
        sb.append(String.format("(INR %10.2f) Tj\n", bill.getPlanRental()));
        sb.append("-380 -18 Td\n(2. Usage, Voice & Roaming Charges) Tj\n380 0 Td\n");
        sb.append(String.format("(INR %10.2f) Tj\n", bill.getUsageCharges()));
        sb.append("-380 -18 Td\n(3. Goods & Services Tax \\(18%% GST\\)) Tj\n380 0 Td\n");
        sb.append(String.format("(INR %10.2f) Tj\n", bill.getTaxAmount()));
        if (bill.getDiscount() > 0) {
            sb.append("-380 -18 Td\n(4. Promotional Credit / Discount) Tj\n380 0 Td\n");
            sb.append(String.format("(-INR %10.2f) Tj\n", bill.getDiscount()));
        }
        sb.append("ET\n");

        sb.append("0.7 0.7 0.7 RG\n1 w\n40 360 532 0 re S\n");

        sb.append("0.9 0.95 1.0 rg\n");
        sb.append("40 325 532 30 re f\n");
        sb.append("0.05 0.15 0.35 RG\n2 w\n40 325 532 30 re S\n");

        sb.append("0.05 0.15 0.35 rg\n");
        sb.append("BT\n/F2 12 Tf\n55 335 Td\n(TOTAL NET PAYABLE AMOUNT) Tj\n360 0 Td\n");
        sb.append(String.format("(INR %10.2f) Tj\n", bill.getTotalAmount()));
        sb.append("ET\n");

        sb.append("0 0 0 rg\n");
        sb.append("BT\n/F2 11 Tf\n55 295 Td\n(Payment Details) Tj\n/F1 10 Tf\n");
        if (isPaid) {
            sb.append("0 -15 Td\n");
            sb.append(String.format("(Payment Status    : PAID \\(SUCCESS\\)) Tj\n"));
            if (paymentMode != null && !paymentMode.isEmpty()) {
                sb.append("0 -13 Td\n");
                sb.append(String.format("(Payment Channel   : %s) Tj\n", escapePdf(paymentMode)));
            }
            if (txnRef != null && !txnRef.isEmpty()) {
                sb.append("0 -13 Td\n");
                sb.append(String.format("(Transaction Ref   : %s) Tj\n", escapePdf(txnRef)));
            }
        } else {
            sb.append("0 -15 Td\n");
            sb.append("(Payment Status    : UNPAID - Pending Customer Payment) Tj\n");
            sb.append("0 -13 Td\n");
            sb.append(String.format("(Please pay on or before %s via UPI, Card, or Net Banking) Tj\n", escapePdf(String.valueOf(bill.getDueDate()))));
        }
        sb.append("ET\n");

        sb.append("0.5 0.5 0.5 rg\n");
        sb.append("BT\n/F1 9 Tf\n");
        sb.append("55 80 Td\n");
        sb.append("(Amdocs Telecom Solutions | support@amdocs-telecom.com | Customer Care: 1800-123-456) Tj\n");
        sb.append("0 -12 Td\n");
        sb.append("(This is a computer-generated tax invoice. No signature required.) Tj\n");
        sb.append("ET\n");

        byte[] streamBytes = sb.toString().getBytes(StandardCharsets.ISO_8859_1);
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        List<Long> offsets = new ArrayList<>();
        offsets.add(0L);

        baos.write("%PDF-1.4\n".getBytes(StandardCharsets.US_ASCII));

        offsets.add((long) baos.size());
        baos.write("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n".getBytes(StandardCharsets.US_ASCII));

        offsets.add((long) baos.size());
        baos.write("2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n".getBytes(StandardCharsets.US_ASCII));

        offsets.add((long) baos.size());
        baos.write("3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R /Resources << /Font << /F1 5 0 R /F2 6 0 R >> >> >>\nendobj\n".getBytes(StandardCharsets.US_ASCII));

        offsets.add((long) baos.size());
        baos.write(String.format("4 0 obj\n<< /Length %d >>\nstream\n", streamBytes.length).getBytes(StandardCharsets.US_ASCII));
        baos.write(streamBytes);
        baos.write("\nendstream\nendobj\n".getBytes(StandardCharsets.US_ASCII));

        offsets.add((long) baos.size());
        baos.write("5 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>\nendobj\n".getBytes(StandardCharsets.US_ASCII));

        offsets.add((long) baos.size());
        baos.write("6 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold >>\nendobj\n".getBytes(StandardCharsets.US_ASCII));

        long xrefOffset = baos.size();
        baos.write(String.format("xref\n0 %d\n0000000000 65535 f \n", offsets.size()).getBytes(StandardCharsets.US_ASCII));
        for (int i = 1; i < offsets.size(); i++) {
            baos.write(String.format("%010d 00000 n \n", offsets.get(i)).getBytes(StandardCharsets.US_ASCII));
        }

        baos.write(String.format("trailer\n<< /Size %d /Root 1 0 R >>\nstartxref\n%d\n%%%%EOF\n", offsets.size(), xrefOffset).getBytes(StandardCharsets.US_ASCII));

        try (FileOutputStream fos = new FileOutputStream(targetPath)) {
            fos.write(baos.toByteArray());
        }

        return new File(targetPath).getAbsolutePath();
    }

    public String generateBillInvoiceHtml(Bill bill, Customer customer, String planName, String paymentMode, String txnRef, String fileName) throws Exception {
        ensureReportDirectoryExists();
        String targetPath = DEFAULT_REPORT_DIR + File.separator + fileName;
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm");
        String genDate = java.time.LocalDateTime.now().format(dtf);
        boolean isPaid = "PAID".equalsIgnoreCase(bill.getBillStatus());

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n");
        html.append("<meta charset=\"UTF-8\">\n<title>Invoice - ").append(bill.getBillNumber()).append("</title>\n");
        html.append("<style>\n");
        html.append("body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background: #f4f6f9; margin: 0; padding: 20px; color: #333; }\n");
        html.append(".container { max-width: 800px; margin: 0 auto; background: #fff; border-radius: 8px; box-shadow: 0 4px 15px rgba(0,0,0,0.1); overflow: hidden; }\n");
        html.append(".header { background: linear-gradient(135deg, #0A2540 0%, #1A4674 100%); color: #fff; padding: 30px; display: flex; justify-content: space-between; align-items: center; }\n");
        html.append(".header h1 { margin: 0; font-size: 22px; letter-spacing: 0.5px; }\n");
        html.append(".header p { margin: 5px 0 0; opacity: 0.85; font-size: 13px; }\n");
        html.append(".badge { padding: 6px 14px; border-radius: 20px; font-weight: bold; font-size: 13px; }\n");
        html.append(".badge-paid { background: #28a745; color: #fff; }\n");
        html.append(".badge-unpaid { background: #dc3545; color: #fff; }\n");
        html.append(".content { padding: 30px; }\n");
        html.append(".grid { display: grid; grid-template-columns: 1fr 1fr; gap: 20px; margin-bottom: 25px; }\n");
        html.append(".card { background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 6px; padding: 15px; }\n");
        html.append(".card h3 { margin-top: 0; font-size: 14px; color: #0A2540; border-bottom: 1px solid #e2e8f0; padding-bottom: 8px; }\n");
        html.append(".card p { margin: 6px 0; font-size: 13px; }\n");
        html.append("table { width: 100%; border-collapse: collapse; margin: 20px 0; }\n");
        html.append("th { background: #0A2540; color: #fff; text-align: left; padding: 10px 12px; font-size: 13px; }\n");
        html.append("td { padding: 10px 12px; border-bottom: 1px solid #e2e8f0; font-size: 13px; }\n");
        html.append("tr:nth-child(even) { background: #f8fafc; }\n");
        html.append(".total-row { font-size: 15px; font-weight: bold; background: #eef2f7 !important; color: #0A2540; }\n");
        html.append(".footer { background: #f8fafc; border-top: 1px solid #e2e8f0; padding: 20px 30px; font-size: 12px; color: #64748b; text-align: center; }\n");
        html.append(".print-btn { display: inline-block; background: #0A2540; color: #fff; padding: 10px 20px; border-radius: 5px; text-decoration: none; cursor: pointer; border: none; font-weight: bold; margin-bottom: 15px; }\n");
        html.append("@media print { .no-print { display: none; } body { padding: 0; background: #fff; } .container { box-shadow: none; border-radius: 0; } }\n");
        html.append("</style>\n</head>\n<body>\n");
        html.append("<div style=\"max-width: 800px; margin: 0 auto 10px auto; text-align: right;\" class=\"no-print\">\n");
        html.append("  <button class=\"print-btn\" onclick=\"window.print()\">🖨️ Print / Save as PDF</button>\n");
        html.append("</div>\n");
        html.append("<div class=\"container\">\n");
        html.append("  <div class=\"header\">\n");
        html.append("    <div>\n      <h1>AMDOCS TELECOM SERVICES LIMITED</h1>\n      <p>Official Tax Invoice & Payment Receipt</p>\n    </div>\n");
        html.append("    <div>\n      <span class=\"badge ").append(isPaid ? "badge-paid" : "badge-unpaid").append("\">").append(bill.getBillStatus()).append("</span>\n    </div>\n");
        html.append("  </div>\n");
        html.append("  <div class=\"content\">\n");
        html.append("    <div class=\"grid\">\n");
        html.append("      <div class=\"card\">\n        <h3>Invoice Details</h3>\n");
        html.append("        <p><strong>Invoice No:</strong> ").append(bill.getBillNumber()).append("</p>\n");
        html.append("        <p><strong>Billing Month:</strong> ").append(bill.getBillingMonth()).append("</p>\n");
        html.append("        <p><strong>Generated On:</strong> ").append(genDate).append("</p>\n");
        html.append("        <p><strong>Due Date:</strong> ").append(bill.getDueDate()).append("</p>\n");
        html.append("      </div>\n");
        html.append("      <div class=\"card\">\n        <h3>Customer Information</h3>\n");
        if (customer != null) {
            html.append("        <p><strong>Name:</strong> ").append(customer.getFullName()).append("</p>\n");
            html.append("        <p><strong>Customer ID:</strong> ").append(customer.getCustomerNumber()).append("</p>\n");
            html.append("        <p><strong>Mobile:</strong> ").append(customer.getMobileNumber()).append("</p>\n");
            html.append("        <p><strong>Email:</strong> ").append(customer.getEmail()).append("</p>\n");
        }
        if (planName != null) {
            html.append("        <p><strong>Plan:</strong> ").append(planName).append("</p>\n");
        }
        html.append("      </div>\n");
        html.append("    </div>\n");
        html.append("    <table>\n");
        html.append("      <thead><tr><th>Description</th><th style=\"text-align: right;\">Amount (INR)</th></tr></thead>\n");
        html.append("      <tbody>\n");
        html.append("        <tr><td>Monthly Plan Base Rental</td><td style=\"text-align: right;\">₹").append(String.format("%.2f", bill.getPlanRental())).append("</td></tr>\n");
        html.append("        <tr><td>Usage & Roaming Charges</td><td style=\"text-align: right;\">₹").append(String.format("%.2f", bill.getUsageCharges())).append("</td></tr>\n");
        html.append("        <tr><td>Goods & Services Tax (18% GST)</td><td style=\"text-align: right;\">₹").append(String.format("%.2f", bill.getTaxAmount())).append("</td></tr>\n");
        if (bill.getDiscount() > 0) {
            html.append("        <tr><td>Promotional Discount</td><td style=\"text-align: right;\">-₹").append(String.format("%.2f", bill.getDiscount())).append("</td></tr>\n");
        }
        html.append("        <tr class=\"total-row\"><td><strong>TOTAL NET PAYABLE</strong></td><td style=\"text-align: right;\"><strong>₹").append(String.format("%.2f", bill.getTotalAmount())).append("</strong></td></tr>\n");
        html.append("      </tbody>\n    </table>\n");
        html.append("    <div class=\"card\" style=\"margin-top: 15px;\">\n");
        html.append("      <h3>Payment Details</h3>\n");
        if (isPaid) {
            html.append("      <p><strong>Status:</strong> <span style=\"color:#28a745; font-weight:bold;\">PAID (SUCCESS)</span></p>\n");
            if (paymentMode != null) html.append("      <p><strong>Channel:</strong> ").append(paymentMode).append("</p>\n");
            if (txnRef != null) html.append("      <p><strong>Transaction Ref:</strong> ").append(txnRef).append("</p>\n");
        } else {
            html.append("      <p><strong>Status:</strong> <span style=\"color:#dc3545; font-weight:bold;\">UNPAID</span></p>\n");
            html.append("      <p>Please pay on or before ").append(bill.getDueDate()).append(" via UPI or Card in the Customer Dashboard.</p>\n");
        }
        html.append("    </div>\n");
        html.append("  </div>\n");
        html.append("  <div class=\"footer\">\n");
        html.append("    <p>Amdocs Telecom Solutions | support@amdocs-telecom.com | Helpline: 1800-123-456</p>\n");
        html.append("    <p>This is a computer-generated tax invoice. No physical signature required.</p>\n");
        html.append("  </div>\n</div>\n</body>\n</html>");

        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(targetPath), StandardCharsets.UTF_8))) {
            writer.write(html.toString());
        }

        return new File(targetPath).getAbsolutePath();
    }

    public String exportComplaintHotspotsToCsv(Map<String, int[]> cityStats, Map<String, int[]> catStats, List<Map<String, Object>> topCustomers, String fileName) throws Exception {
        ensureReportDirectoryExists();
        String targetPath = DEFAULT_REPORT_DIR + File.separator + fileName;
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(targetPath), StandardCharsets.UTF_8))) {

            writer.write("# AMDOCS TCSMS - COMPLAINT HOTSPOTS & ANALYTICS REPORT\n\n");

            writer.write("--- COMPLAINTS BY LOCATION / CITY ---\n");
            writer.write("City,TotalComplaints,Open,InProgress,Resolved\n");
            for (Map.Entry<String, int[]> entry : cityStats.entrySet()) {
                int[] counts = entry.getValue();
                writer.write(String.format("\"%s\",%d,%d,%d,%d\n", entry.getKey(), counts[0], counts[1], counts[2], counts[3]));
            }

            writer.write("\n--- COMPLAINTS BY CATEGORY ---\n");
            writer.write("Category,TotalComplaints,Open,InProgress,Resolved\n");
            for (Map.Entry<String, int[]> entry : catStats.entrySet()) {
                int[] counts = entry.getValue();
                writer.write(String.format("\"%s\",%d,%d,%d,%d\n", entry.getKey(), counts[0], counts[1], counts[2], counts[3]));
            }

            writer.write("\n--- TOP COMPLAINANT CUSTOMERS ---\n");
            writer.write("CustomerId,CustomerNumber,FullName,City,MobileNumber,TotalComplaints,Pending,Resolved\n");
            for (Map<String, Object> map : topCustomers) {
                writer.write(String.format("%s,\"%s\",\"%s\",\"%s\",\"%s\",%s,%s,%s\n",
                        map.get("customerId"),
                        map.get("customerNumber"),
                        map.get("customerName"),
                        map.get("city"),
                        map.get("mobileNumber"),
                        map.get("totalCount"),
                        map.get("pendingCount"),
                        map.get("resolvedCount")));
            }
        }
        return new File(targetPath).getAbsolutePath();
    }

    private static String escapePdf(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
    }
}
