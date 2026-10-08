# Architectural Concepts - TCSMS

## Telecom Customer and Subscription Management System

This document explains how the TCSMS project works from startup to output. It
also explains the contribution of each major package and the flow of data
through the application.

The application is a Java 17 console-based telecom management system. It uses
controllers for user interaction, services for business rules, DAOs for JDBC
database operations, model classes for domain data, and background schedulers
for recurring work.

---

## 1. High-level architecture

```text
User
 |
 v
MainApplication
 |
 +--> CustomerController
 |       |
 |       v
 |   Customer Services
 |       |
 |       v
 |   Customer DAOs
 |
 +--> AdminController
         |
         v
     Admin Services
         |
         v
     Admin DAOs

Controllers
     |
     v
Service interfaces and implementations
     |
     v
DAO interfaces and JDBC implementations
     |
     v
DBConnection / Transactions
     |
     v
MySQL or file-backed H2 database
```

### Main responsibility of each layer

| Layer | Main responsibility | Example |
|---|---|---|
| Main application | Starts the application and background services | `MainApplication` |
| Controller | Reads console input and displays output | `CustomerController` |
| Service | Applies business rules and coordinates workflows | `PaymentServiceImpl` |
| DAO | Executes SQL and maps rows to objects | `CustomerDAOImpl` |
| Model | Represents business data | `Customer`, `Bill`, `Payment` |
| Utility | Shared technical functionality | `DBConnection`, `Transactions` |
| Scheduler | Runs background and recurring work | `BillingScheduler` |
| Report | Creates CSV, text, PDF and HTML output | `ReportGenerator` |
| Security | Handles passwords, CAPTCHA, OTP and login security | `PasswordUtil`, `OTPService` |

---

## 2. Project package structure

```text
src/main/java/com/amdocs/telecom/
|
+-- controller/
|   +-- CustomerController.java
|   +-- AdminController.java
|
+-- dao/
|   +-- DAO interfaces
|   +-- impl/
|       +-- JDBC DAO implementations
|
+-- dto/
|   +-- CustomerRegistrationDTO.java
|
+-- exception/
|   +-- TelecomException.java
|   +-- AuthenticationException.java
|   +-- ValidationException.java
|
+-- factory/
|   +-- DAOFactory.java
|   +-- ServiceFactory.java
|
+-- main/
|   +-- MainApplication.java
|
+-- model/
|   +-- Customer.java
|   +-- Bill.java
|   +-- Payment.java
|   +-- MobileSubscription.java
|   +-- TelecomPlan.java
|   +-- UsageRecord.java
|
+-- report/
|   +-- ReportGenerator.java
|
+-- scheduler/
|   +-- BillingScheduler.java
|   +-- AccountMonitor.java
|   +-- PaymentNotificationService.java
|   +-- UsageProcessor.java
|   +-- ConsoleActivityMonitor.java
|
+-- security/
|   +-- PasswordUtil.java
|   +-- LoginSecurity.java
|   +-- OTPService.java
|   +-- CaptchaGenerator.java
|
+-- service/
|   +-- Service interfaces
|   +-- impl/
|       +-- Business service implementations
|
+-- strategy/
|   +-- PaymentStrategy.java
|   +-- UpiPaymentStrategy.java
|   +-- CardPaymentStrategy.java
|   +-- NetBankingPaymentStrategy.java
|   +-- PaymentStrategyFactory.java
|
+-- util/
    +-- DBConnection.java
    +-- Transactions.java
    +-- ApplicationLogging.java
    +-- ConsoleMenu.java
    +-- UsageUnits.java
```

---

## 3. Application startup flow

The application starts from `MainApplication.main`.

```text
Application starts
       |
       v
Configure application logging
       |
       v
Initialize DBConnection
       |
       v
Load database properties
       |
       v
Create or migrate database schema
       |
       v
Start notification workers
       |
       v
Start billing scheduler
       |
       v
Start account monitor
       |
       v
Display main console menu
```

### Startup responsibilities

1. `ApplicationLogging.configure()` prepares rotating log files.
2. `DBConnection.getInstance()` initializes the database manager.
3. Database schema and seed data are prepared when required.
4. `PaymentNotificationService` starts notification workers.
5. `BillingScheduler` schedules recurring billing.
6. `AccountMonitor` schedules overdue-account processing.
7. `MainApplication` displays the customer/admin menu.

### Main startup source

**File:** `telecom/main/MainApplication.java`  
**Lines 21-40:**

```java
DBConnection database = DBConnection.getInstance();
System.out.println("Database initialized successfully: "
        + database.getActiveDatabaseName());

notificationService = new PaymentNotificationService();
notificationService.start();

billingScheduler = new BillingScheduler();
billingScheduler.start(60, 3600);

accountMonitor = new AccountMonitor();
accountMonitor.start(60, 3600);

Runtime.getRuntime().addShutdownHook(
        new Thread(MainApplication::stopServices));
```

---

## 4. Main menu flow

```text
MainApplication
       |
       v
Print main menu
       |
       +--> Customer login
       |       |
       |       v
       |   CustomerController
       |
       +--> Administrator login
       |       |
       |       v
       |   AdminController
       |
       +--> Exit
               |
               v
          Stop services
          Close database
```

The main application does not directly contain all business logic. It creates
the appropriate controller and lets that controller manage the selected user
workflow.

---

## 5. Customer flow

### 5.1 Customer registration flow

```text
Customer enters registration details
            |
            v
CustomerController
            |
            v
CustomerRegistrationDTO
            |
            v
CustomerService
            |
            +--> Validate mandatory fields
            +--> Validate age, email and mobile
            +--> Check username/email uniqueness
            +--> Hash password using BCrypt
            +--> Insert customer using CustomerDAO
            +--> Write audit entry
            |
            v
Display registration result
```

### 5.2 Customer login flow

```text
Username and password
          |
          v
CustomerController
          |
          v
AuthenticationService
          |
          +--> Generate and validate CAPTCHA
          +--> Find customer using DAO
          +--> Verify BCrypt password
          +--> Check account status and lockout
          +--> Record login history
          |
          v
Customer dashboard
```

Security-related files:

| File | Contribution |
|---|---|
| `telecom/security/PasswordUtil.java` | Hashes and verifies passwords |
| `telecom/security/LoginSecurity.java` | Tracks failures and lockouts |
| `telecom/security/CaptchaGenerator.java` | Creates login CAPTCHA values |
| `telecom/security/OTPService.java` | Generates and validates recovery OTPs |
| `telecom/service/impl/AuthenticationServiceImpl.java` | Coordinates login rules |
| `telecom/dao/impl/CustomerDAOImpl.java` | Reads customer and login data |

### 5.3 Browse and search plans

```text
Customer chooses plan menu
          |
          v
CustomerController
          |
          v
PlanService
          |
          v
PlanDAO.findAllActive()
          |
          v
PlanServiceImpl
          |
          +--> Filter by name
          +--> Filter by price
          +--> Sort plans
          +--> Compare plans
          |
          v
Display matching plans
```

The service layer keeps filtering and comparison rules outside the controller.
The controller is responsible for reading the search value and displaying the
result.

### 5.4 Subscribe to a plan

```text
Customer selects plan and SIM type
              |
              v
CustomerController
              |
              v
SubscriptionServiceImpl
              |
              v
Transactions.run(...)
              |
              +--> Validate customer and plan
              +--> Create SIM/subscription
              +--> Create subscription history
              +--> Generate initial bill
              +--> Write audit entry
              |
              +--> Commit if all operations succeed
              +--> Roll back if any operation fails
              |
              v
Display subscription result
```

The transaction helper ensures that subscription creation does not leave
partial database records.

### 5.5 Record usage

```text
Customer enters usage
          |
          v
UsageService
          |
          +--> Validate quantity and type
          +--> Normalize unit
          +--> Save usage record
          +--> Write audit entry
          |
          v
Display saved usage
```

Usage units are normalized by `UsageUnits`, for example data to MB and voice
usage to minutes.

### 5.6 Pay a bill

```text
Customer selects bill and payment mode
              |
              v
PaymentServiceImpl
              |
              +--> Verify bill ownership
              +--> Validate amount and bill status
              +--> Select PaymentStrategy
              +--> Validate payment mode
              +--> Start database transaction
              +--> Insert payment
              +--> Update bill status
              +--> Write audit entry
              +--> Commit transaction
              |
              v
PaymentNotificationService
              |
              v
Queue notification for customer
```

Payment strategies:

| File | Responsibility |
|---|---|
| `telecom/strategy/PaymentStrategy.java` | Common payment contract |
| `telecom/strategy/UpiPaymentStrategy.java` | UPI validation behavior |
| `telecom/strategy/CardPaymentStrategy.java` | Card validation behavior |
| `telecom/strategy/NetBankingPaymentStrategy.java` | Net-banking behavior |
| `telecom/strategy/PaymentStrategyFactory.java` | Selects strategy by payment mode |
| `telecom/service/impl/PaymentServiceImpl.java` | Coordinates the payment transaction |

---

## 6. Administrator flow

### 6.1 Administrator login

```text
Admin enters credentials
          |
          v
AdminController
          |
          v
AuthenticationService
          |
          +--> Find administrator
          +--> Verify password
          +--> Check security status
          +--> Record login attempt
          |
          v
Administrator dashboard
```

### 6.2 Administrator dashboard

The administrator controller provides access to:

- Plan management.
- Customer management.
- Subscription and SIM management.
- Billing and payment review.
- Usage review.
- Complaint processing.
- Reports and exports.
- Audit activity monitoring.
- Account suspension.

The controller displays the menu, but business rules remain in service classes.

### 6.3 Generate reports

```text
Admin chooses report
          |
          v
AdminController
          |
          v
ReportServiceImpl
          |
          +--> Read data through DAOs
          +--> Group and calculate statistics
          +--> Call ReportGenerator
          |
          v
reports/
  +-- CSV files
  +-- text invoices
  +-- PDF invoices
  +-- HTML reports
```

`ReportGenerator` handles formatting and writing. It does not own the
business rules used to calculate report values.

---

## 7. Background processing architecture

Background services start with the application and run while the console is
active.

```text
MainApplication
       |
       +--> BillingScheduler
       |       +--> Generate monthly bills
       |       +--> Reconcile usage
       |
       +--> AccountMonitor
       |       +--> Find overdue bills
       |       +--> Mark bills overdue
       |       +--> Suspend delinquent accounts
       |
       +--> PaymentNotificationService
       |       +--> Receive notification
       |       +--> Put notification in queue
       |       +--> Three workers persist notifications
       |
       +--> ConsoleActivityMonitor
               +--> Poll audit records
               +--> Display new activity for administrators
```

### Background service responsibilities

| File | Threading model | Work performed |
|---|---|---|
| `telecom/scheduler/BillingScheduler.java` | Scheduled thread pool | Runs billing at a fixed rate |
| `telecom/scheduler/AccountMonitor.java` | Scheduled executor and worker pool | Checks overdue accounts; suspends delinquent accounts |
| `telecom/scheduler/PaymentNotificationService.java` | Bounded queue and three workers | Persists notifications asynchronously |
| `telecom/scheduler/UsageProcessor.java` | Four-worker executor | Processes usage batches concurrently |
| `telecom/scheduler/ConsoleActivityMonitor.java` | Single scheduled thread | Polls audit activity every two seconds |

### Notification producer-consumer flow

```text
PaymentService
      |
      v
sendNotification(...)
      |
      v
LinkedBlockingQueue
      |
      +--> Worker 1
      +--> Worker 2
      +--> Worker 3
              |
              v
      AuditAndNotificationDAO
              |
              v
      notifications table
```

The queue is bounded. If it remains full after the configured wait, the
service uses synchronous persistence instead of silently dropping the
notification.

---

## 8. Database and JDBC flow

```text
Service
  |
  v
DAO interface
  |
  v
DAO implementation
  |
  v
DBConnection.getInstance().getConnection()
  |
  v
PreparedStatement
  |
  v
ResultSet / update count
  |
  v
Model object or service result
```

### Database responsibilities by file

| File | Contribution |
|---|---|
| `telecom/util/DBConnection.java` | Loads configuration and provides database connections |
| `telecom/util/SchemaMigration.java` | Applies required schema changes |
| `telecom/util/Transactions.java` | Controls commit, rollback and nested savepoints |
| `src/main/resources/schema.sql` | Defines tables, constraints and indexes |
| `src/main/resources/seed.sql` | Provides repeat-safe demo data |
| `telecom/dao/*.java` | Declares persistence contracts |
| `telecom/dao/impl/*.java` | Executes JDBC statements and maps database rows |

### Transaction flow

```text
Transactions.run(work)
        |
        +--> Check for existing transaction
        |
        +--> If outer transaction:
        |       create connection
        |       disable auto-commit
        |       store connection in ThreadLocal
        |
        +--> Execute work callback
        |
        +--> Success: commit
        |
        +--> Failure: rollback
        |
        +--> Remove ThreadLocal connection
```

`ThreadLocal` ensures that nested DAO operations on the same worker thread can
use the current transaction connection.

---

## 9. Output flow

### 9.1 Console output

```text
Controller
   |
   +--> Read Scanner input
   +--> Call service
   +--> Format result
   +--> System.out.println / printf
   |
   v
Console
```

Controllers show menus, prompts, validation messages and business results.

### 9.2 Database output

Successful service operations produce database changes such as:

- New customers.
- New subscriptions.
- Usage records.
- Bills and payments.
- Complaints.
- Audit records.
- Notifications.

### 9.3 File output

```text
ReportService
      |
      v
ReportGenerator
      |
      +--> reports/customers_report.csv
      +--> reports/revenue_summary.csv
      +--> reports/complaint_hotspots_report.csv
      +--> reports/*.txt
      +--> reports/*.pdf
      +--> reports/*.html
```

### 9.4 Log output

```text
ApplicationLogging
      |
      v
logs/tcsms-%g.log
```

Scheduled work writes status and error information to rotating log files. This
prevents background output from interrupting interactive console input.

---

## 10. File contribution map

### Application and user interaction

| File | Contribution |
|---|---|
| `telecom/main/MainApplication.java` | Entry point, startup, menus and shutdown |
| `telecom/controller/CustomerController.java` | Customer prompts and customer menu |
| `telecom/controller/AdminController.java` | Administrator prompts and admin menu |
| `telecom/util/ConsoleMenu.java` | Shared console menu helpers |

### Business services

| File or package | Contribution |
|---|---|
| `telecom/service/AuthenticationService.java` | Authentication contract |
| `telecom/service/CustomerService.java` | Customer operations contract |
| `telecom/service/PlanService.java` | Plan operations contract |
| `telecom/service/SubscriptionService.java` | Subscription operations contract |
| `telecom/service/BillingService.java` | Billing contract |
| `telecom/service/PaymentService.java` | Payment contract |
| `telecom/service/UsageService.java` | Usage contract |
| `telecom/service/impl/` | Business rules and workflow coordination |

### Persistence

| File or package | Contribution |
|---|---|
| `telecom/dao/` | DAO contracts |
| `telecom/dao/impl/` | JDBC implementations |
| `telecom/factory/DAOFactory.java` | Creates DAO objects |
| `telecom/factory/ServiceFactory.java` | Creates service objects |
| `telecom/util/DBConnection.java` | Database connection management |
| `telecom/util/Transactions.java` | Transaction boundaries |

### Domain model

| Package | Contribution |
|---|---|
| `telecom/model/` | Customer, plan, subscription, bill, payment, usage and other domain objects |
| `telecom/dto/` | Data transfer objects for input workflows |
| `telecom/exception/` | Application-specific exception types |

### Cross-cutting functionality

| Package | Contribution |
|---|---|
| `telecom/security/` | Password, CAPTCHA, OTP and lockout behavior |
| `telecom/report/` | CSV, text, PDF and HTML generation |
| `telecom/scheduler/` | Scheduled and concurrent background processing |
| `telecom/strategy/` | Interchangeable payment behavior |
| `telecom/validation/` | Reusable input validation |
| `telecom/util/` | Database, transaction, logging and unit-conversion utilities |

---

## 11. Important design patterns

| Pattern | Project implementation | Why it is useful |
|---|---|---|
| DAO | `CustomerDAO` and `CustomerDAOImpl` | Separates SQL from business logic |
| Factory | `DAOFactory`, `ServiceFactory`, `PaymentStrategyFactory` | Centralizes object creation |
| Strategy | Payment strategy implementations | Makes payment behavior replaceable |
| Singleton/holder | `DBConnection` | Provides one database manager per application |
| Producer-consumer | Notification queue and workers | Separates notification creation from persistence |
| MVC-like layering | Controllers, models, services and DAOs | Separates interaction, rules and data access |
| DTO | `CustomerRegistrationDTO` | Transfers registration input as one object |

---

## 12. Complete example: customer pays a bill

### Step-by-step flow

1. The customer selects “Pay Bill” in `CustomerController`.
2. The controller reads the bill ID, amount and payment mode.
3. `PaymentServiceImpl` verifies that the bill belongs to the customer.
4. The service checks that the amount and bill status are valid.
5. `PaymentStrategyFactory` selects UPI, card or net banking behavior.
6. The payment operation starts inside `Transactions.run`.
7. A payment row is inserted.
8. The bill status is updated to `PAID`.
9. An audit row is written.
10. The transaction commits.
11. A notification is placed in the notification queue.
12. A notification worker persists the notification.
13. The controller displays the result.

### Payment output

```text
Console:
Payment successful

Database:
payments row inserted
bills row updated
audit_logs row inserted
notifications row inserted

Logs:
Payment and notification processing messages
```

If a critical database operation fails, the transaction rolls back so the
payment and bill do not become inconsistent.

---

## 13. Complete example: scheduled billing

```text
Application starts
       |
       v
BillingScheduler.start(60, 3600)
       |
       v
Wait 60 seconds
       |
       v
Run billing task
       |
       +--> Read active subscriptions
       +--> Check whether monthly bill already exists
       +--> Generate missing bills
       +--> Reconcile usage
       +--> Write log messages
       |
       v
Wait 3600 seconds
       |
       v
Repeat while application is running
```

The scheduler runs in the background, so the customer or administrator can
continue using the console.

---

## 14. Shutdown flow

```text
User selects Exit or JVM receives shutdown signal
                    |
                    v
MainApplication.stopServices()
                    |
                    +--> Stop BillingScheduler
                    +--> Stop AccountMonitor
                    +--> Drain/stop notification workers
                    +--> Stop database connection manager
                    |
                    v
Application exits
```

The shutdown hook protects cleanup when the JVM is stopped externally. The
explicit exit path also calls the same service shutdown method.

---

## 15. How to explain the architecture in an interview

Use this answer:

> “This is a layered Java console application. `MainApplication` starts the
> system and background services. Controllers handle console input and output.
> They call service interfaces, while service implementations contain business
> rules and transaction workflows. Services call DAO interfaces, and JDBC DAO
> implementations communicate with MySQL or file-backed H2 through
> `DBConnection`. Model classes represent domain data. Schedulers handle
> billing, overdue accounts and notifications in the background. Reports are
> produced by `ReportGenerator`, and logs are written to rotating files. This
> separation keeps user interaction, business logic, persistence and
> infrastructure responsibilities independent.”

### If asked how output is produced

> “For console output, the controller formats the service result and prints it.
> For persistent output, DAOs execute SQL and update the database. For reports,
> the service calculates the data and `ReportGenerator` writes CSV, text, PDF or
> HTML files. Background services write operational details to rotating log
> files so they do not disturb the console.”

### If asked why this architecture was chosen

> “The layering reduces coupling and makes the application easier to test and
> maintain. A business rule can be changed in a service without rewriting the
> controller or SQL. A DAO can be replaced for another database, and payment
> behavior can be changed through the strategy interface.”

---

## 16. Final checklist

Before explaining the project, remember:

- Start with the high-level layered architecture.
- Explain the request flow from controller to service to DAO.
- Mention that models carry domain data.
- Explain transaction boundaries for important workflows.
- Mention schedulers and notification workers as background work.
- Explain that reports go to `reports/` and logs go to `logs/`.
- Distinguish implemented behavior from future improvements.
- Use the exact source file names when showing code.

All updates in this guide are local documentation changes only. No commit or
Git push is performed.
