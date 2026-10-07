import com.amdocs.telecom.dao.impl.*;
import com.amdocs.telecom.dto.CustomerRegistrationDTO;
import com.amdocs.telecom.exception.*;
import com.amdocs.telecom.model.*;
import com.amdocs.telecom.report.ReportGenerator;
import com.amdocs.telecom.scheduler.*;
import com.amdocs.telecom.security.PasswordUtil;
import com.amdocs.telecom.service.impl.*;
import com.amdocs.telecom.util.DBConnection;
import org.h2.tools.RunScript;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

/** Service and database regression checks. */
public class IndependentReview {
    interface Scenario { void run() throws Exception; }
    static int passed, failed;
    static final List<String> results = new ArrayList<>();
    static final String MONTH = YearMonth.now().toString();
    static final String PASSWORD = "ReviewPass@123";
    static final String HASH = PasswordUtil.hashPassword(PASSWORD);
    static final String MYSQL_DATABASE = System.getenv("REVIEW_MYSQL_DATABASE");
    static Connection connection() throws SQLException { return DBConnection.getInstance().getConnection(); }
    static void sql(String sql) throws SQLException {
        try (Connection c = connection(); Statement s = c.createStatement()) { s.execute(sql); }
    }
    static double number(String sql) throws SQLException {
        try (Connection c = connection(); Statement s = c.createStatement(); ResultSet r = s.executeQuery(sql)) {
            r.next(); return r.getDouble(1);
        }
    }
    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    static void near(double expected, double actual) {
        check(Double.isFinite(actual) && Math.abs(expected - actual) < .001, "Expected " + expected + ", actual " + actual);
    }
    static void rejected(Scenario s) throws Exception {
        try { s.run(); } catch (TelecomException | IllegalArgumentException expected) { return; }
        throw new AssertionError("Invalid operation was accepted");
    }
    static void withoutConsoleOutput(Scenario scenario) throws Exception {
        PrintStream original = System.out;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (PrintStream capture = new PrintStream(output, true, StandardCharsets.UTF_8)) {
            System.setOut(capture);
            try { scenario.run(); } finally { System.setOut(original); }
        }
        check(output.size() == 0, "Background service interrupted console input: " + output.toString(StandardCharsets.UTF_8));
    }
    static void reset() throws Exception {
        try (Connection c = connection()) {
            boolean mysql=c.getMetaData().getDatabaseProductName().equalsIgnoreCase("MySQL");
            check(mysql ? MYSQL_DATABASE!=null && MYSQL_DATABASE.matches("telecom_review_[a-f0-9]{12}") && MYSQL_DATABASE.equals(c.getCatalog())
                    : c.getMetaData().getURL().startsWith("jdbc:h2:mem:independent_review"), "Unsafe database URL");
            if(mysql) {
                List<String> tables=new ArrayList<>();
                try(ResultSet rows=c.getMetaData().getTables(c.getCatalog(),null,"%",new String[]{"TABLE"})) {
                    while(rows.next()) tables.add(rows.getString("TABLE_NAME"));
                }
                try(Statement s=c.createStatement()) {
                    s.execute("SET FOREIGN_KEY_CHECKS=0");
                    try { for(String table:tables) s.execute("DROP TABLE `"+table.replace("`","``")+"`"); }
                    finally { s.execute("SET FOREIGN_KEY_CHECKS=1"); }
                }
            } else try (Statement s = c.createStatement()) { s.execute("DROP ALL OBJECTS"); }
            for (String resource : List.of("schema.sql", "seed.sql")) {
                try (Reader r = new InputStreamReader(Objects.requireNonNull(
                        IndependentReview.class.getResourceAsStream("/" + resource)), StandardCharsets.UTF_8)) {
                    RunScript.execute(c, r);
                }
                if(resource.equals("schema.sql")) com.amdocs.telecom.util.SchemaMigration.apply(c);
            }
        }
    }
    static void test(String name, Scenario s) {
        String filter=System.getenv("REVIEW_TEST_FILTER");
        if(filter!=null && !name.matches(".*("+filter+").*")) return;
        try {
            reset(); s.run(); passed++; results.add("PASS | " + name);
        } catch (Throwable e) {
            failed++; results.add("FAIL | " + name + " | " + e.getClass().getSimpleName() + ": " +
                    String.valueOf(e.getMessage()).replace('\n', ' ').replace('\r', ' '));
        }
        System.out.println(results.get(results.size()-1));
    }
    static CustomerRegistrationDTO dto() {
        return new CustomerRegistrationDTO("Review", "Tester", LocalDate.of(1995,1,1),
                "review@example.com", "9123456789", "1 Test Road", "Pune", "India", "reviewuser", PASSWORD);
    }
    static AuthenticationServiceImpl auth() { return new AuthenticationServiceImpl(); }
    static void knownPassword() throws Exception { sql("UPDATE customers SET password_hash='" + HASH + "' WHERE customer_id=1"); }
    static void extraSubscription(int customer, int plan) throws Exception {
        sql("INSERT INTO mobile_subscriptions(subscription_number,customer_id,plan_id,mobile_number,sim_id,activation_date,subscription_type,status) " +
                "VALUES ('SUB-REVIEW',"+customer+","+plan+",'9123000000',2,CURRENT_DATE,'POSTPAID','ACTIVE')");
    }
    static void usage(int sub, String type, double qty, String unit, double charge, String month) throws Exception {
        sql("INSERT INTO usage_records(subscription_id,usage_date,usage_type,quantity,unit,charge) VALUES(" +
                sub+",'"+month+"-10 12:00:00','"+type+"',"+qty+",'"+unit+"',"+charge+")");
    }
    public static void main(String[] args) throws Exception {
        if(MYSQL_DATABASE!=null) try(Connection c=connection()) {
            check(MYSQL_DATABASE.matches("telecom_review_[a-f0-9]{12}") && MYSQL_DATABASE.equals(c.getCatalog()),"Unsafe MySQL test database");
        }
        test("Authentication: documented customer seed login", () ->
                check(auth().login("arjunm", "Customer@123", "7", "7").getCustomerId()==1, "Wrong customer"));
        test("Authentication: fresh registered customer login and history", () -> {
            Customer c = new CustomerServiceImpl().registerCustomer(dto());
            check(auth().login("reviewuser", PASSWORD, "7", "7").getCustomerId()==c.getCustomerId(), "Wrong customer");
            check(new CustomerDAOImpl().getLastLoginTimestamp("reviewuser").isPresent(), "No login timestamp");
        });
        test("Authentication: invalid CAPTCHA rejected", () -> rejected(() -> auth().login("arjunm", PASSWORD, "7", "8")));
        test("Authentication: three username failures lock account", () -> {
            for(int i=0;i<3;i++) rejected(() -> auth().login("arjunm", "wrong", "7", "7"));
            check("LOCKED".equals(new CustomerDAOImpl().findById(1).orElseThrow().getAccountStatus()), "Account not locked");
        });
        test("Authentication: username/email failures share account lockout", () -> {
            rejected(() -> auth().login("arjunm", "wrong", "7", "7"));
            rejected(() -> auth().login("arjunm", "wrong", "7", "7"));
            rejected(() -> auth().login("arjun.mehta@example.com", "wrong", "7", "7"));
            check("LOCKED".equals(new CustomerDAOImpl().findById(1).orElseThrow().getAccountStatus()), "Three failures across aliases leave account ACTIVE");
        });
        test("Authentication: suspended customer must stay blocked", () -> {
            knownPassword(); sql("UPDATE customers SET account_status='SUSPENDED' WHERE customer_id=1");
            rejected(() -> auth().login("arjunm", PASSWORD, "7", "7"));
        });
        test("Authentication: OTP reset succeeds, rejects reuse, unlocks", () -> {
            sql("UPDATE customers SET account_status='LOCKED' WHERE customer_id=1");
            String otp=auth().initiatePasswordRecovery("arjunm");
            rejected(() -> auth().completePasswordRecovery("arjunm", "invalid", PASSWORD));
            check(auth().completePasswordRecovery("arjunm", otp, PASSWORD), "Reset failed");
            rejected(() -> auth().completePasswordRecovery("arjunm", otp, PASSWORD));
            check(auth().login("arjunm", PASSWORD,"7","7").getCustomerId()==1, "Reset login failed");
        });
        test("Customer: duplicate email/mobile and underage rejected", () -> {
            CustomerServiceImpl service=new CustomerServiceImpl();
            CustomerRegistrationDTO d=dto(); d.setEmail("arjun.mehta@example.com"); rejected(() -> service.registerCustomer(d));
            d.setEmail("review@example.com"); d.setMobileNumber("9876543210"); rejected(() -> service.registerCustomer(d));
            d.setMobileNumber("9123456789"); d.setDateOfBirth(LocalDate.now().minusYears(10)); rejected(() -> service.registerCustomer(d));
        });
        test("Customer: blank username/address/city/country rejected", () -> {
            CustomerRegistrationDTO d=dto(); d.setUsername(" "); d.setAddress(""); d.setCity(""); d.setCountry("");
            rejected(() -> new CustomerServiceImpl().registerCustomer(d));
        });
        test("Plan: search, filters, sort, compare and missing ID", () -> {
            PlanServiceImpl p=new PlanServiceImpl();
            check(p.searchPlansByName("5G").size()==2,"Search mismatch");
            check(p.filterPlansByPriceRange(600,1000).size()==2,"Price filter mismatch");
            check(p.filterPlansByMinData(100).size()==2,"Data filter mismatch");
            check(p.sortPlansByPrice(true).get(0).getPlanId()==4,"Sort mismatch");
            check(p.comparePlans(1,2).size()==2,"Compare mismatch");
            rejected(() -> p.getPlanById(999));
        });
        test("Subscription: provisioning, initial bill and history", () -> {
            SubscriptionServiceImpl s=new SubscriptionServiceImpl();
            MobileSubscription sub=s.subscribeToPlan(2,2,"PHYSICAL_SIM");
            check(new BillingDAOImpl().findBySubscriptionAndMonth(sub.getSubscriptionId(),MONTH).isPresent(),"No initial bill");
            check(s.changePlan(sub.getSubscriptionId(),1,2,"sarahw"),"Change failed");
            check(s.getSubscriptionHistory(sub.getSubscriptionId()).size()==1,"No history");
        });
        test("Subscription: duplicate direct signup/inactive/unauthorized rejected", () -> {
            SubscriptionServiceImpl s=new SubscriptionServiceImpl();
            rejected(() -> s.subscribeToPlan(1,2,"PHYSICAL_SIM"));
            sql("UPDATE telecom_plans SET status='INACTIVE' WHERE plan_id=3");
            rejected(() -> s.subscribeToPlan(1,3,"PHYSICAL_SIM"));
            rejected(() -> s.changePlan(1,1,2,"sarahw"));
        });
        test("Subscription: plan change cannot duplicate another active plan", () -> {
            extraSubscription(1,1);
            rejected(() -> new SubscriptionServiceImpl().changePlan(1,1,1,"arjunm"));
        });
        test("Billing: exact monthly usage and tax", () -> {
            sql("DELETE FROM usage_records"); usage(1,"ROAMING",20,"MB",10,MONTH);
            Bill b=new BillingServiceImpl().generateMonthlyBill(1,MONTH);
            near(10,b.getUsageCharges()); near(127.62,b.getTaxAmount()); near(836.62,b.getTotalAmount());
        });
        test("Billing: duplicate monthly invoice rejected", () -> {
            new BillingServiceImpl().generateMonthlyBill(1,MONTH);
            rejected(() -> new BillingServiceImpl().generateMonthlyBill(1,MONTH));
        });
        test("Billing: plan-change invoice works after monthly invoice", () -> {
            new BillingServiceImpl().generateMonthlyBill(1,MONTH);
            new SubscriptionServiceImpl().changePlan(1,1,1,"arjunm");
            new BillingServiceImpl().generatePlanChangeBill(1,1);
        });
        test("Billing: malformed billing month rejected", () -> rejected(() -> new BillingServiceImpl().generateMonthlyBill(1,"bad-month")));
        test("Usage: valid record, history, invalid quantity/type", () -> {
            UsageServiceImpl u=new UsageServiceImpl();
            UsageRecord r=u.recordUsage(1,"DATA",2,"GB");
            check(r.getUsageId()>0 && u.getSubscriptionUsage(1).size()==5,"Usage missing");
            rejected(() -> u.recordUsage(1,"DATA",-1,"GB"));
            try { u.recordUsage(1,"UNKNOWN",1,"GB"); throw new AssertionError("Invalid type accepted"); }
            catch(RuntimeException expected) { check(expected.getMessage().contains("Invalid usage type"),"Wrong error"); }
        });
        test("Usage: monthly DAO returns records in selected month", () -> {
            sql("DELETE FROM usage_records"); usage(1,"DATA",10,"GB",0,MONTH);
            check(new UsageDAOImpl().findBySubscriptionAndMonth(1,MONTH).size()==1,"Monthly usage query lost matching record");
        });
        test("Usage: summary normalizes 1 GB plus 1024 MB", () -> {
            sql("DELETE FROM usage_records");
            UsageServiceImpl u=new UsageServiceImpl(); u.recordUsage(1,"DATA",1,"GB"); u.recordUsage(1,"DATA",1024,"MB");
            double total=u.getUsageSummary(1).get("DATA");
            check(total==2 || total==2048,"Mixed units summed as raw quantities: "+total);
        });
        test("Usage: NaN quantity rejected", () -> rejected(() -> new UsageServiceImpl().recordUsage(1,"DATA",Double.NaN,"GB")));
        for(String mode:List.of("UPI","CARD","NET_BANKING","BANK_TRANSFER")) {
            test("Payment: "+mode+" commits payment, bill, audit; duplicate rejected", () -> {
                PaymentServiceImpl p=new PaymentServiceImpl();
                Payment saved=p.processPayment(1,1,1152.42,mode,"arjunm");
                check(saved.getPaymentId()>0,"No payment ID");
                check("PAID".equals(new BillingDAOImpl().findById(1).orElseThrow().getBillStatus()),"Bill not paid");
                near(1,number("SELECT COUNT(*) FROM audit_logs WHERE entity_name='PAYMENT'"));
                rejected(() -> p.processPayment(1,1,1152.42,mode,"arjunm"));
                near(1,number("SELECT COUNT(*) FROM payments"));
            });
        }
        test("Payment: invalid amount, owner, mode rejected without writes", () -> {
            PaymentServiceImpl p=new PaymentServiceImpl();
            rejected(() -> p.processPayment(1,1,1,"UPI","arjunm"));
            rejected(() -> p.processPayment(1,2,1152.42,"UPI","sarahw"));
            rejected(() -> p.processPayment(1,1,1152.42,"INVALID","arjunm"));
            near(0,number("SELECT COUNT(*) FROM payments"));
        });
        test("Payment: audit failure rolls back payment and bill status", () -> {
            rejected(() -> new PaymentServiceImpl().processPayment(1,1,1152.42,"UPI",null));
            near(0,number("SELECT COUNT(*) FROM payments"));
            check("UNPAID".equals(new BillingDAOImpl().findById(1).orElseThrow().getBillStatus()),"Bill update survived rollback");
        });
        test("Payment: simultaneous attempts produce exactly one payment", () -> {
            ExecutorService pool=Executors.newFixedThreadPool(2); CountDownLatch start=new CountDownLatch(1);
            try {
                Callable<Boolean> task=() -> { start.await(); try { new PaymentServiceImpl().processPayment(1,1,1152.42,"UPI","arjunm"); return true; } catch(TelecomException e) { return false; } };
                Future<Boolean> a=pool.submit(task), b=pool.submit(task); start.countDown();
                boolean first=a.get(10,TimeUnit.SECONDS), second=b.get(10,TimeUnit.SECONDS);
                check(first != second,"Expected exactly one successful payer"); near(1,number("SELECT COUNT(*) FROM payments"));
            } finally { pool.shutdownNow(); }
        });
        test("Complaint: create, resolve, notification, invalid inputs", () -> {
            ComplaintServiceImpl c=new ComplaintServiceImpl(); Complaint issue=c.lodgeComplaint(1,"NETWORK","Test complaint");
            check(c.resolveComplaint(issue.getComplaintId(),"Fixed"),"Resolution failed");
            check("RESOLVED".equals(c.getComplaintById(issue.getComplaintId()).orElseThrow().getStatus()),"Wrong status");
            near(1,number("SELECT COUNT(*) FROM notifications"));
            rejected(() -> c.lodgeComplaint(1,"INVALID","Test")); rejected(() -> c.lodgeComplaint(1,"NETWORK"," "));
        });
        test("Complaint: invalid lifecycle status rejected", () -> {
            ComplaintServiceImpl c=new ComplaintServiceImpl();
            rejected(() -> c.resolveComplaint(1,"BANANA","Test resolution","admin"));
        });
        test("Complaint: analytics execute grouping, conditional aggregates and HAVING", () -> {
            ComplaintServiceImpl service=new ComplaintServiceImpl();
            near(3,service.getComplaintsCountByCity().get("Mumbai")[0]);
            near(2,service.getComplaintsCountByCategory().get("NETWORK")[0]);
            List<Map<String,Object>> top=service.getTopCustomersByComplaints(10);
            check(((Number)top.get(0).get("customerId")).intValue()==1,"Wrong top complainant");
            near(3,((Number)top.get(0).get("totalCount")).doubleValue());
            near(1,service.getCustomersWithMultipleComplaints(2).size());
            near(0,service.getCustomersWithMultipleComplaints(4).size());
        });
        test("Report: city, unpaid customer, monthly collected revenue", () -> {
            ReportServiceImpl r=new ReportServiceImpl();
            check(r.getCustomersByCity().size()==3,"City grouping incorrect");
            check(r.getCustomersWithUnpaidBills().size()==1,"Unpaid report incorrect");
            new PaymentServiceImpl().processPayment(1,1,1152.42,"UPI","arjunm");
            near(1152.42,r.getRevenueSummaryByPlan().get("2026-08").getSum());
        });
        test("Report: highest usage ranks usage rather than bill value", () -> {
            extraSubscription(2,2); sql("DELETE FROM usage_records");
            usage(1,"DATA",1,"GB",0,MONTH); usage(2,"DATA",100,"GB",0,MONTH);
            check(new ReportServiceImpl().getHighestConsumingCustomers().get(0).getCustomerId()==2,"Customer with low usage but larger bill ranked first");
        });
        test("Report: monthly ARPU does not sum multiple months", () -> {
            sql("UPDATE bills SET total_amount=300,bill_status='PAID' WHERE bill_id=1");
            sql("INSERT INTO bills(bill_number,subscription_id,billing_month,plan_rental,usage_charges,tax_amount,total_amount,due_date,bill_status) VALUES('REVIEW-BILL',1,'2026-09',300,0,0,300,'2026-09-20','PAID')");
            near(100,new ReportServiceImpl().getAverageMonthlyRevenuePerCustomer());
        });
        test("Reports: CSV and text invoice contain expected data", () -> {
            ReportGenerator r=new ReportGenerator();
            Path csv=Path.of(r.exportCustomersToCsv(new CustomerServiceImpl().getAllCustomers(),"review-customers.csv"));
            check(Files.readString(csv).contains("CUST100245"),"CSV missing customer");
            Path invoice=Path.of(r.generateBillInvoiceText(new BillingDAOImpl().findById(1).orElseThrow(),
                    new CustomerDAOImpl().findById(1).orElseThrow(),"review-invoice.txt"));
            String content=Files.readString(invoice); check(content.contains("INV-2026-08-10245") && content.contains("1152.42"),"Invoice incorrect");
        });
        test("Notification: queue persists all items on shutdown", () -> {
            withoutConsoleOutput(() -> {
            PaymentNotificationService n=new PaymentNotificationService(); n.start();
            try { for(int i=0;i<6;i++) n.sendNotification(1,"Review "+i,"Test"); } finally { n.shutdown(); }
            near(6,number("SELECT COUNT(*) FROM notifications"));
            });
        });
        test("UsageProcessor: six records persist and count matches", () -> {
            UsageProcessor p=new UsageProcessor();
            try { p.processBulkUsage(UsageProcessor.generateSampleUsageRecords(1,6),2); } finally { p.shutdown(); }
            near(6,p.getTotalProcessed()); near(10,number("SELECT COUNT(*) FROM usage_records"));
        });
        test("BillingScheduler: trigger generates fresh active-subscription bill", () -> {
            BillingScheduler b=new BillingScheduler(); try { b.triggerNow(); } finally { b.shutdown(); }
            check(new BillingDAOImpl().findBySubscriptionAndMonth(1,MONTH).isPresent(),"No monthly bill");
        });
        test("AccountMonitor: overdue and suspension effects persist", () -> {
            sql("UPDATE bills SET due_date='"+LocalDate.now().minusDays(40)+"'");
            AccountMonitor a=new AccountMonitor();
            try { a.scanOverdueAccounts(); a.suspendDelinquentAccounts(30); } finally { a.shutdown(); }
            check("OVERDUE".equals(new BillingDAOImpl().findById(1).orElseThrow().getBillStatus()),"Bill not overdue");
            check("SUSPENDED".equals(new SubscriptionDAOImpl().findById(1).orElseThrow().getStatus()),"Subscription not suspended");
        });
        test("Database: reinitialization does not duplicate usage seed", () -> {
            double before=number("SELECT COUNT(*) FROM usage_records"); DBConnection.testAndInitializeDatabase();
            near(before,number("SELECT COUNT(*) FROM usage_records"));
        });
        test("Database: closing uncommitted connection must not commit", () -> {
            try(Connection c=connection()) {
                c.setAutoCommit(false);
                try(Statement s=c.createStatement()) { s.executeUpdate("UPDATE customers SET city='Uncommitted' WHERE customer_id=1"); }
            }
            check(!"Uncommitted".equals(new CustomerDAOImpl().findById(1).orElseThrow().getCity()),"Connection.close committed pending transaction");
        });
        test("Database: nested failure rolls back to savepoint and preserves outer work", () -> {
            com.amdocs.telecom.util.Transactions.run(() -> {
                sql("UPDATE customers SET city='Outer change' WHERE customer_id=1");
                rejected(() -> com.amdocs.telecom.util.Transactions.run(() -> {
                    sql("UPDATE customers SET city='Inner change' WHERE customer_id=1");
                    throw new TelecomException("Reject inner work");
                }));
                near(1,number("SELECT COUNT(*) FROM customers WHERE customer_id=1 AND city='Outer change'"));
                return null;
            });
            near(1,number("SELECT COUNT(*) FROM customers WHERE customer_id=1 AND city='Outer change'"));
        });
        test("Add-ons: activation, duplicate prevention, deactivation and credit", () -> {
            AddOnServiceImpl service=new AddOnServiceImpl();
            check(service.getCatalogue().size()==2,"Catalogue missing");
            service.setActive(1,1,1,true);
            check(service.getSubscriptionAddOns(1,1).size()==1,"Add-on missing");
            rejected(() -> service.setActive(1,1,1,true));
            near(1,number("SELECT COUNT(*) FROM bills WHERE invoice_type='ADD_ON'"));
            service.setActive(1,1,1,false);
            check(service.getSubscriptionAddOns(1,1).isEmpty(),"Add-on still active");
            check(new BillingServiceImpl().getCredit(1).signum()>0,"Deactivation credit missing");
        });
        test("Add-ons: ownership and suspended subscription enforced", () -> {
            AddOnServiceImpl service=new AddOnServiceImpl();
            rejected(() -> service.setActive(1,1,2,true));
            sql("UPDATE mobile_subscriptions SET status='SUSPENDED' WHERE subscription_id=1");
            rejected(() -> service.setActive(1,1,1,true));
        });
        test("Billing: later usage updates unpaid monthly invoice", () -> {
            new BillingServiceImpl().generateMonthlyBill(1,MONTH);
            new UsageServiceImpl().recordUsage(1,"ROAMING",20,"MB");
            Bill bill=new BillingDAOImpl().findBySubscriptionAndMonth(1,MONTH).orElseThrow();
            near(10,bill.getUsageCharges()); near(836.62,bill.getTotalAmount());
        });
        test("Billing: usage after payment creates an adjustment exactly once", () -> {
            Bill bill=new BillingServiceImpl().generateMonthlyBill(1,MONTH);
            new PaymentServiceImpl().processPayment(bill.getBillId(),1,bill.getTotalAmount(),"UPI","arjunm");
            new UsageServiceImpl().recordUsage(1,"ROAMING",20,"MB");
            near(1,number("SELECT COUNT(*) FROM bills WHERE invoice_type='USAGE'"));
            near(11.8,number("SELECT SUM(total_amount) FROM bills WHERE invoice_type='USAGE'"));
            new BillingServiceImpl().reconcileUsage(1,MONTH);
            near(1,number("SELECT COUNT(*) FROM bills WHERE invoice_type='USAGE'"));
        });
        test("Billing: plan adjustment is prorated and idempotent", () -> {
            new BillingServiceImpl().generateMonthlyBill(1,MONTH);
            new SubscriptionServiceImpl().changePlan(1,1,1,"arjunm");
            Bill adjustment=new BillingServiceImpl().generatePlanChangeBill(1,1);
            double expected=java.math.BigDecimal.valueOf(300).multiply(java.math.BigDecimal.valueOf(YearMonth.now().lengthOfMonth()-LocalDate.now().getDayOfMonth()+1))
                    .divide(java.math.BigDecimal.valueOf(YearMonth.now().lengthOfMonth()),2,java.math.RoundingMode.HALF_UP).doubleValue();
            near(expected,adjustment.getPlanRental());
            near(1,number("SELECT COUNT(*) FROM bills WHERE invoice_type='PLAN_CHANGE'"));
        });
        test("Billing: downgrade credit is consumed by the next invoice", () -> {
            sql("UPDATE mobile_subscriptions SET plan_id=1 WHERE subscription_id=1");
            new BillingServiceImpl().generateMonthlyBill(1,MONTH);
            new SubscriptionServiceImpl().changePlan(1,2,1,"arjunm");
            java.math.BigDecimal credit=new BillingServiceImpl().getCredit(1);
            check(credit.signum()>0,"No downgrade credit");
            Bill next=new BillingServiceImpl().generateMonthlyBill(1,YearMonth.now().plusMonths(1).toString());
            near(credit.doubleValue(),next.getDiscount()); near(0,new BillingServiceImpl().getCredit(1).doubleValue());
        });
        test("Subscription: initial billing failure rolls back provisioning", () -> {
            sql("ALTER TABLE bills ADD CONSTRAINT review_fail CHECK(bill_number='INV-2026-08-10245')");
            rejected(() -> new SubscriptionServiceImpl().subscribeToPlan(2,2,"PHYSICAL_SIM"));
            near(1,number("SELECT COUNT(*) FROM mobile_subscriptions"));
        });
        test("Subscription: adjustment failure rolls back plan and history", () -> {
            sql("ALTER TABLE bills ADD CONSTRAINT review_fail CHECK(bill_number='INV-2026-08-10245')");
            rejected(() -> new SubscriptionServiceImpl().changePlan(1,1,1,"arjunm"));
            near(2,number("SELECT plan_id FROM mobile_subscriptions WHERE subscription_id=1"));
            near(0,number("SELECT COUNT(*) FROM subscription_history"));
        });
        test("Admin management: profile, SIM, suspension, reactivation and access", () -> {
            AdministrationServiceImpl service=new AdministrationServiceImpl();
            Administrator admin=new AdminDAOImpl().findByUsername("admin").orElseThrow();
            Customer customer=new CustomerDAOImpl().findById(1).orElseThrow(); customer.setCity("Pune");
            service.updateCustomer(admin,customer); check("Pune".equals(new CustomerDAOImpl().findById(1).orElseThrow().getCity()),"Profile unchanged");
            SIMCard sim=new SIMCard(); sim.setSimNumber("89910012345678999"); sim.setImsi("404010123456799"); sim.setSimType(SimType.ESIM);
            service.addSIM(admin,sim); service.setSIMStatus(admin,sim.getSimId(),"INACTIVE");
            service.setSubscriptionStatus(admin,1,"SUSPENDED"); service.setSubscriptionStatus(admin,1,"ACTIVE");
            rejected(() -> service.setCustomerStatus(null,1,"ACTIVE"));
            rejected(() -> service.setCustomerStatus(admin,1,"BANANA"));
        });
        test("Plan policy: permitted prepaid/postpaid change updates type", () -> {
            AdministrationServiceImpl service=new AdministrationServiceImpl(); Administrator admin=new AdminDAOImpl().findByUsername("admin").orElseThrow();
            service.setPlanRules(admin,2,true,0); service.setPlanRules(admin,4,true,0);
            new SubscriptionServiceImpl().changePlan(1,4,1,"arjunm");
            check(new SubscriptionDAOImpl().findById(1).orElseThrow().getSubscriptionType()==SubscriptionType.PREPAID,"Subscription type stale");
        });
        test("Plan policy: minimum change period enforced", () -> {
            AdministrationServiceImpl service=new AdministrationServiceImpl(); Administrator admin=new AdminDAOImpl().findByUsername("admin").orElseThrow();
            service.setPlanRules(admin,2,false,3650);
            rejected(() -> new SubscriptionServiceImpl().changePlan(1,1,1,"arjunm"));
        });
        test("Authentication: expired temporary lock unlocks the customer", () -> {
            knownPassword(); sql("UPDATE customers SET account_status='LOCKED' WHERE customer_id=1");
            sql("INSERT INTO account_security(username,user_role,locked_until) VALUES('arjunm','CUSTOMER','2000-01-01 00:00:00')");
            check(auth().login("arjunm",PASSWORD,"7","7").getAccountStatus().equals("ACTIVE"),"Expired lock remained");
        });
        test("Authentication: admin/customer histories are isolated", () -> {
            CustomerDAOImpl dao=new CustomerDAOImpl();
            dao.logLoginAttempt("admin","CUSTOMER","127.0.0.1","FAILED");
            near(0,dao.getRecentFailedLoginAttempts("admin","ADMIN",30));
        });
        test("Usage: monthly summary filters dates and normalizes units", () -> {
            sql("DELETE FROM usage_records"); usage(1,"DATA",1,"GB",0,MONTH); usage(1,"DATA",1024,"MB",0,MONTH);
            usage(1,"DATA",10,"GB",0,YearMonth.now().minusMonths(1).toString());
            near(2048,new UsageServiceImpl().getCustomerMonthlySummary(1,MONTH).get("DATA"));
        });
        test("UsageProcessor: zero batch size rejected", () -> {
            UsageProcessor processor=new UsageProcessor();
            try { rejected(() -> processor.processBulkUsage(List.of(),0)); } finally { processor.shutdown(); }
        });
        test("Database: legacy bill index and hash migrate without overwriting changed passwords", () -> {
            boolean mysql=DBConnection.getInstance().getActiveDatabaseName().equalsIgnoreCase("MySQL");
            sql(mysql?"DROP INDEX uq_invoice_key ON bills":"DROP INDEX uq_invoice_key");
            sql("ALTER TABLE bills DROP COLUMN invoice_key"); sql("ALTER TABLE bills DROP COLUMN invoice_type");
            sql("CREATE UNIQUE INDEX uq_bill_subscription_month ON bills(subscription_id,billing_month)");
            sql("UPDATE customers SET password_hash='$2a$10$wT0H7eFj/y55hX8Q8eLgU.8jWsqR6k1bH4x7f3Y6k9Q1qZ8xPzPWe' WHERE customer_id=1");
            sql("UPDATE customers SET password_hash='"+HASH+"' WHERE customer_id=2");
            DBConnection.testAndInitializeDatabase();
            try(Connection c=connection()) { com.amdocs.telecom.util.SchemaMigration.apply(c); }
            check(PasswordUtil.verifyPassword("Customer@123",new CustomerDAOImpl().findById(1).orElseThrow().getPasswordHash()),"Legacy seed hash unchanged");
            check(PasswordUtil.verifyPassword(PASSWORD,new CustomerDAOImpl().findById(2).orElseThrow().getPasswordHash()),"Changed password overwritten");
            new BillingServiceImpl().generateMonthlyBill(1,MONTH); new SubscriptionServiceImpl().changePlan(1,1,1,"arjunm");
        });
        test("Database: seed preserves renamed subscriptions and invoices", () -> {
            sql("UPDATE mobile_subscriptions SET subscription_number='RENAMED-SUB' WHERE subscription_id=1");
            sql("UPDATE bills SET bill_number='RENAMED-BILL' WHERE bill_id=1");
            double billCount=number("SELECT COUNT(*) FROM bills");
            double subscriptionCount=number("SELECT COUNT(*) FROM mobile_subscriptions");
            DBConnection.testAndInitializeDatabase();
            DBConnection.testAndInitializeDatabase();
            near(billCount,number("SELECT COUNT(*) FROM bills"));
            near(subscriptionCount,number("SELECT COUNT(*) FROM mobile_subscriptions"));
            near(1,number("SELECT COUNT(*) FROM mobile_subscriptions WHERE subscription_number='RENAMED-SUB'"));
            near(1,number("SELECT COUNT(*) FROM bills WHERE bill_number='RENAMED-BILL'"));
        });
        test("Database: seed handles missing first subscription and an assigned demo SIM", () -> {
            sql("DELETE FROM mobile_subscriptions WHERE subscription_id=1");
            sql("INSERT INTO mobile_subscriptions(subscription_id,subscription_number,customer_id,plan_id,mobile_number,sim_id,activation_date,subscription_type,status) " +
                    "VALUES (101,'LIVE-SUB',1,2,'9123999999',1,CURRENT_DATE,'POSTPAID','ACTIVE')");
            DBConnection.testAndInitializeDatabase();
            near(1,number("SELECT COUNT(*) FROM mobile_subscriptions"));
            near(0,number("SELECT COUNT(*) FROM bills"));
            near(0,number("SELECT COUNT(*) FROM usage_records"));
            sql("UPDATE mobile_subscriptions SET subscription_number='SUB10001' WHERE subscription_id=101");
            DBConnection.testAndInitializeDatabase();
            DBConnection.testAndInitializeDatabase();
            near(1,number("SELECT COUNT(*) FROM bills WHERE subscription_id=101 AND invoice_key='MONTHLY:101:2026-08'"));
            near(4,number("SELECT COUNT(*) FROM usage_records WHERE subscription_id=101"));
        });
        for (boolean existingIndex : new boolean[]{true, false}) {
            test("Database: duplicate legacy invoices migrate with " + (existingIndex ? "existing" : "missing") + " unique index", () -> {
                if (!existingIndex) {
                    boolean mysql=DBConnection.getInstance().getActiveDatabaseName().equalsIgnoreCase("MySQL");
                    sql(mysql ? "DROP INDEX uq_invoice_key ON bills" : "DROP INDEX uq_invoice_key");
                }
                sql("UPDATE bills SET invoice_key=CONCAT('MONTHLY:',subscription_id,':',billing_month) WHERE bill_id=1");
                for (int i=0; i<2; i++) {
                    sql("INSERT INTO bills(bill_number,subscription_id,billing_month,plan_rental,usage_charges,tax_amount,total_amount,due_date) " +
                            "SELECT 'LEGACY-DUP-" + i + "',subscription_id,billing_month,plan_rental,usage_charges,tax_amount,total_amount,due_date FROM bills WHERE bill_id=1");
                }
                if (!existingIndex) {
                    sql("UPDATE bills SET invoice_key=(SELECT k FROM (SELECT invoice_key AS k FROM bills WHERE bill_id=1) saved_key) WHERE bill_number LIKE 'LEGACY-DUP-%'");
                }
                double billCount=number("SELECT COUNT(*) FROM bills");
                double paymentCount=number("SELECT COUNT(*) FROM payments");
                try (Connection c=connection()) {
                    com.amdocs.telecom.util.SchemaMigration.apply(c);
                    com.amdocs.telecom.util.SchemaMigration.apply(c);
                }
                near(billCount,number("SELECT COUNT(*) FROM bills"));
                near(billCount,number("SELECT COUNT(DISTINCT invoice_key) FROM bills"));
                near(paymentCount,number("SELECT COUNT(*) FROM payments"));
                near(2,number("SELECT COUNT(*) FROM bills WHERE bill_number LIKE 'LEGACY-DUP-%' AND invoice_key LIKE 'LEGACY:%'"));
                near(1,number("SELECT COUNT(*) FROM bills WHERE bill_id=1 AND invoice_key=CONCAT('MONTHLY:',subscription_id,':',billing_month)"));
            });
        }
        test("BillingScheduler: scheduled background execution generates a bill", () -> {
            withoutConsoleOutput(() -> {
            BillingScheduler scheduler=new BillingScheduler(); scheduler.start(0,3600);
            try {
                long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
                while(new BillingDAOImpl().findBySubscriptionAndMonth(1,MONTH).isEmpty() && System.nanoTime()<deadline) Thread.sleep(50);
                check(new BillingDAOImpl().findBySubscriptionAndMonth(1,MONTH).isPresent(),"Scheduled billing did not run");
            } finally { scheduler.shutdown(); }
            });
        });
        test("AccountMonitor: scheduled scan suspends delinquent subscription", () -> {
            withoutConsoleOutput(() -> {
            sql("UPDATE bills SET due_date='2020-01-01',bill_status='UNPAID' WHERE bill_id=1");
            AccountMonitor monitor=new AccountMonitor();
            monitor.start(0,3600);
            try {
                long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
                while(number("SELECT COUNT(*) FROM mobile_subscriptions WHERE subscription_id=1 AND status='SUSPENDED'")==0
                        && System.nanoTime()<deadline) Thread.sleep(50);
                near(1,number("SELECT COUNT(*) FROM mobile_subscriptions WHERE subscription_id=1 AND status='SUSPENDED'"));
                near(1,number("SELECT COUNT(*) FROM bills WHERE bill_id=1 AND bill_status='OVERDUE'"));
            } finally { monitor.shutdown(); }
            });
        });
        results.add("TOTAL | "+passed+" passed | "+failed+" failed | "+(passed+failed)+" scenarios");
        Files.write(Path.of(MYSQL_DATABASE==null?"results.txt":"mysql-results.txt"),results,StandardCharsets.UTF_8);
        System.out.println(results.get(results.size()-1));
        if(MYSQL_DATABASE!=null) try(Connection c=connection(); Statement s=c.createStatement()) {
            check(MYSQL_DATABASE.matches("telecom_review_[a-f0-9]{12}") && MYSQL_DATABASE.equals(c.getCatalog()),"Unsafe database cleanup");
            s.execute("DROP DATABASE `"+MYSQL_DATABASE+"`");
        }
        DBConnection.getInstance().shutdown(); System.exit(failed==0 ? 0 : 1);
    }
}
