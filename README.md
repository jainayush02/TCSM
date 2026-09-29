# Telecom Customer & Subscription Management System (TCSMS)
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
- **Java JDK 17+** (JDK 21 or 24 recommended).
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

---

## 🔑 Demo Test Credentials

### 1. Administrator Portal
- **Username**: `admin`
- **Password**: `admin123`

### 2. Pre-seeded Customers
| Customer Number | Name | Username | Password | Mobile | City |
|---|---|---|---|---|---|
| `CUST100245` | Arjun Mehta | `arjun_m` | `Pass@123` | `+91-9876543210` | Mumbai |
| `CUST100378` | Sarah Wilson | `sarah_w` | `Pass@123` | `+44-7700900123` | London |
| `CUST100412` | Omar Hassan | `omar_h` | `Pass@123` | `+966-501234567` | Riyadh |
