# Telecom Customer & Subscription Management System (TCSMS)

The case-study fixes and current test results are documented in [the verification report](reports/case-study-fix-report.md).
Customer menus cover add-ons, monthly usage, profile editing, and plan comparison/filtering. The administrator dashboard includes customer, SIM and subscription management, monthly usage, and plan-change policies. Billing and overdue monitoring run automatically and write to rotating files under `logs/`. Plan changes use prorated adjustment invoices; credits apply to the next monthly invoice.
### Amdocs Preboarding Project Case Study

A centralized, enterprise-grade Java console application for managing telecom customer onboarding, SIM cards, tariff plans, subscriptions, real-time usage tracking, billing cycles, payment processing, complaints, and executive business reports.

---

## 🏛️ System Architecture

The application is structured into a clean, decoupled **Multi-Tier Architecture**:

```
com.amdocs.telecom
├── controller    # Console UI & interactive user workflows (CustomerController, AdminController)
├── service       # Core business logic & Java 8 Stream operations
│   └── impl      # Service implementations (Authentication, Customer, Plan, Billing, etc.)
├── dao           # Data Access Object interfaces
│   └── impl      # JDBC CRUD implementations with SQL transactions & batching
├── model         # Strongly-typed domain models & Enums (Customer, Plan, Bill, Complaint, etc.)
├── dto           # Data Transfer Objects for validation & onboarding
├── exception     # Domain-specific hierarchy (TelecomException, ValidationException, AuthenticationException)
├── factory       # Factory Pattern classes (DAOFactory, ServiceFactory)
├── strategy      # Strategy Pattern for multi-channel payment processing
├── scheduler     # Multithreaded background schedulers & worker pools
├── report        # File handling engine (CSV exports & printable text tax invoices)
├── security      # BCrypt password hashing, CAPTCHA generator, OTP service
├── util          # Database connection manager (Singleton) & validation utilities
└── main          # Application entry point & lifecycle management (MainApplication)
```

---

## 🧩 Design Patterns Implemented

1. **DAO (Data Access Object) Pattern**:
   - Encapsulates database operations behind clean interfaces (`CustomerDAO`, `PlanDAO`, `SubscriptionDAO`, `BillingDAO`, `PaymentDAO`, `UsageDAO`, `ComplaintDAO`, `AuditAndNotificationDAO`, `AdminDAO`).
2. **Singleton Pattern**:
   - `DBConnection.getInstance()` provides a synchronized, single-instance connection manager with automated failover from MySQL to embedded H2.
3. **Factory Pattern**:
   - `DAOFactory`: Centralized creation of DAO instances.
   - `ServiceFactory`: Centralized lifecycle management for business services.
   - `PaymentStrategyFactory`: Dynamic resolution of payment processing strategies based on payment mode.
4. **Strategy Pattern**:
   - `PaymentStrategy` interface with concrete implementations:
     - `UpiPaymentStrategy` (UPI daily limits & validation)
     - `CardPaymentStrategy` (Debit/Credit card processing)
     - `NetBankingPaymentStrategy` (Bank transfer/NEFT channel)
5. **Producer-Consumer / Observer Pattern**:
   - `PaymentNotificationService`: Uses a thread-safe `BlockingQueue` and a dedicated pool of worker threads (`NotificationWorker-1..3`) to process payment receipt dispatches asynchronously.

---

## ⚡ Java 8+ Capabilities Demonstrated

- **Stream API & Collectors**:
  - `Collectors.groupingBy()`: Groups customers by city and subscriptions by plan.
  - `Collectors.summarizingDouble()`: Aggregates revenue metrics (Sum, Average, Min, Max, Count) across billing cycles.
  - Multi-condition filtering and sorting (`Comparator.comparingDouble(...).reversed()`) to determine highest-consuming customers.
- **Functional Interfaces**:
  - `Predicate<T>`: Reusable validation predicates for email format, phone numbers, and age.
  - `Function<T, R>` & `Consumer<T>`: Stream pipelines and audit logging callbacks.
- **`Optional<T>`**:
  - Null-safe returns across all DAO lookups, eliminating `NullPointerException` risks.
- **Java Time API**:
  - Modern `LocalDate` and `LocalDateTime` used throughout billing cycles, due dates, and usage timestamps.

---

## 🔄 Multithreading & Background Processing

- **ScheduledExecutorService**:
  - `BillingScheduler`: Automates periodic monthly bill generation.
  - `AccountMonitor`: Scans for overdue accounts and manages grace periods.
- **ExecutorService & Callable / Future**:
  - `UsageProcessor`: Concurrently processes batches of CDR (Call Detail Record) usage files.
- **Thread Synchronization**:
  - Thread-safe queues (`LinkedBlockingQueue`), atomic variables, and synchronized connection pools.

---

## 🔒 Security & Verification

- **Password Hashing**: Industry-standard **jBCrypt** salt hashing (passwords never stored in plaintext).
- **CAPTCHA Verification**: Real-time mathematical challenge generation to prevent automated attacks.
- **Account Lockout**: Automatic lock after **3 consecutive failed login attempts**.
- **OTP Recovery**: Simulated One-Time Password verification for account password resets.
- **SQL Injection Prevention**: 100% `PreparedStatement` parameter binding across all queries.
- **ACID Transactions**: Full JDBC transaction control (`conn.setAutoCommit(false)`, `conn.commit()`, `conn.rollback()`) for payments and subscription provisioning.

---

## 📁 File Handling & Reports

- **CSV Export**:
  - `reports/revenue_summary.csv`: Monthly bill counts, total collections, and average invoice amounts.
  - `reports/customers_report.csv`: Complete customer directory export.
- **Printable Invoices**:
  - `reports/invoice_<bill_id>.txt`: Formatted tax invoice with breakdown of base rental, usage charges, and 18% GST.

---

## 🚀 How to Build and Run

### Prerequisites
- **Java JDK 17+** (the project is compiled for Java 17 and uses Java 8 Stream/functional APIs).
- *Optional*: MySQL Server running on `localhost:3306` (The system automatically falls back to an embedded H2 database if MySQL is offline).

### Quick Start (Windows)
Double-click `run.bat` or run in terminal:
```bat
run.bat
```

The script will automatically:
1. Verify and download required libraries (`h2-2.2.224.jar`, `mysql-connector-j-8.3.0.jar`, `jbcrypt-0.4.jar`) into `lib/`.
2. Compile all source files into `target/classes`.
3. Copy schema and database scripts.
4. Set console encoding to UTF-8 (`chcp 65001`) for crisp UI rendering.
5. Launch the application.
## Two-Terminal Live Demonstration

The console application can be run in multiple terminals against the same localhost database.

1. Open Terminal 1 and run `run.bat`.
2. Login as administrator and select option `23. Live Activity Monitor`.
3. Open Terminal 2 in the same project folder and run `run.bat` again.
4. Login as a customer and register, subscribe, change plans, record usage, raise a complaint, or make a payment.
5. The administrator terminal displays new activity events from the shared `audit_logs` table every two seconds.

This remains a console-only workflow. When MySQL is unavailable, both processes use the file-backed H2 database configured with `AUTO_SERVER=TRUE`.

The application prints the active database at startup. The included local `.env` file is loaded automatically by `run.bat` and contains the MySQL connection settings. Do not commit `.env`; it is ignored by Git. To use a different password, update `.env` before running the batch file:

PowerShell:

```powershell
$env:TCSMS_DB_USER = "root"
$env:TCSMS_DB_PASSWORD = "<your-local-mysql-password>"
.\run.bat
```

Both terminals must show `Database initialized successfully: MySQL`. If either terminal shows `H2`, it is not connected to the MySQL data visible in Workbench.

---

## 🔑 Demo Test Credentials

### 1. Administrator Portal
- **Username**: `admin`
- **Password**: `admin@123`

### 2. Pre-seeded Customers
| Customer Number | Name | Username | Password | Mobile | City |
|---|---|---|---|---|---|
| `CUST100245` | Arjun Mehta | `arjunm` | `Customer@123` | `9876543210` | Mumbai |
| `CUST100378` | Sarah Wilson | `sarahw` | `Customer@123` | `9876543211` | London |
| `CUST100412` | Omar Hassan | `omarh` | `Customer@123` | `9876543212` | Riyadh |
