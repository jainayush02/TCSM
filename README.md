# Telecom Customer and Subscription Management System
## Current Project Interview Preparation Guide

**Reviewed:** 7 October 2026

**Project:** TCSMS, a Java console application

**Use:** Current explanation of the project, interview revision, code walkthrough, and question practice.

> This guide describes the checked source in `src/main/java` and `src/main/resources`, the review scripts in `tests/review`, and reports in `reports`. The PDF that inspired the original guide and old compiled outputs are not treated as evidence of current behavior. Features that remain simulations or limitations are called out explicitly.

---

## Contents

1. [Project overview](#1-project-overview)
2. [Architecture and request flow](#2-architecture-and-request-flow)
3. [Build, startup, and project layout](#3-build-startup-and-project-layout)
4. [Feature walkthrough](#4-feature-walkthrough)
5. [Important classes and packages](#5-important-classes-and-packages)
6. [Database design](#6-database-design)
7. [JDBC, transactions, and concurrency](#7-jdbc-transactions-and-concurrency)
8. [Payments and billing](#8-payments-and-billing)
9. [Authentication and security](#9-authentication-and-security)
10. [Java, OOP, and functional programming](#10-java-oop-and-functional-programming)
11. [Collections and complexity](#11-collections-and-complexity)
12. [File handling, reports, and logging](#12-file-handling-reports-and-logging)
13. [Design patterns and SOLID](#13-design-patterns-and-solid)
14. [Testing and verified behavior](#14-testing-and-verified-behavior)
15. [Limitations and future work](#15-limitations-and-future-work)
16. [Interview answers](#16-interview-answers)
17. [Interview question bank](#17-interview-question-bank)
18. [Revision sheet and source index](#18-revision-sheet-and-source-index)
19. [Concepts with exact source references](#19-concepts-with-exact-source-references)

---

## 1. Project overview

TCSMS is a layered, interactive Java application for customer onboarding and telecom subscription administration. Customers can manage profiles, browse plans, subscribe, change plans, record and view usage, manage add-ons, pay bills, submit complaints, and read notifications. Administrators can manage plans, customers, subscriptions and SIMs; generate bills; review payments, usage and overdue accounts; process complaints; view analytics and audit entries; and monitor activity.

Data is stored using JDBC in MySQL, with file-backed H2 as the fallback configured by the application. The source includes a database connection reuse pool, migration logic, and repeat-safe demo seeding.

| Area | Current implementation |
|---|---|
| User interface | Java console, with customer and administrator portals |
| Runtime | Java 17; Java 8 APIs and concepts are demonstrated, but the app does not target a Java 8 runtime |
| Persistence | JDBC DAOs, MySQL or file-backed H2 |
| Domain | Customers, administrators, plans, SIMs, subscriptions, add-ons, usage, bills, payments, complaints, notifications and audit events |
| Scheduled work | Monthly billing and overdue monitoring start with the application; notification and usage workers use executors |
| Payment provider | Payment strategy validation is simulated; no external gateway is connected |
| Password recovery delivery | OTP generation and verification are implemented; displaying the OTP is simulated rather than a real email/SMS service |
| Web service | No REST API or browser-facing server is implemented |

### Core behaviors worth explaining

- Customer registration validates mandatory data, age, password strength and unique identifiers before storing a BCrypt password hash.
- Login uses CAPTCHA, tracks login attempts, locks accounts temporarily after three failures, and records history by account role.
- Subscription creation, its initial bill, and plan changes run under transaction management. Plan changes also add history and a prorated adjustment.
- Usage units are normalized to canonical units. For example, data is stored in MB and voice in minutes.
- Payments validate ownership and amount and commit the payment, bill state, and audit record atomically.
- Monthly invoices use a natural invoice key to prevent duplicate billing. Historical invoices with conflicting legacy identifiers receive distinct legacy keys during migration.
- Scheduled work writes to rotating log files rather than interrupting an interactive prompt.

---

## 2. Architecture and request flow

```text
Customer or administrator
            |
            v
     MainApplication
            |
            +---- CustomerController / AdminController
                             |
                             v
                     Service interfaces
                             |
                             v
                   Service implementations
                    /        |         \
             validation  transactions  reports
                    \        |         /
                             v
                         DAO contracts
                             |
                             v
                    JDBC DAO implementations
                             |
                             v
           DBConnection, connection reuse, JDBC driver
                    /                    \
                 MySQL             file-backed H2
```

The controllers interact with the console and orchestrate a user action. Service implementations own business rules and use DAOs to read and change persistent records. DAOs map between SQL rows and model objects. `DBConnection` loads configuration, initializes the schema, selects the database, and provides JDBC connections.

The main application starts independent background services:

```text
MainApplication
  +-- BillingScheduler -------- scheduled bill generation and usage reconciliation
  +-- AccountMonitor ---------- overdue bill marking and delinquent suspension
  +-- PaymentNotificationService -- queue and three persistence workers
  +-- ConsoleActivityMonitor ------ admin-requested audit polling
  +-- UsageProcessor -------------- admin-triggered concurrent usage batches
```

The main menu dispatches to customer or administrator login. After successful authentication, the controller shows the corresponding dashboard. On exit, schedulers/workers stop and the database connection pool closes.

The code uses manual object construction in several controllers and services. It has DAO and service interfaces, but no framework-managed dependency injection.

---

## 3. Build, startup, and project layout

### Run and test

From PowerShell at the project root:

```powershell
.\run.bat
```

The batch script compiles Java sources against jars in `lib`, copies resources, and starts `MainApplication`. The project also contains `pom.xml` configured for Java release 17 with Maven compiler and exec plugins.

Review scripts use isolated H2 fixtures by default. The MySQL runner creates and drops a uniquely named review database; it does not use the normal application database for test fixtures.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tests/review/run-review.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File tests/review/run-review.ps1 -MySql
python tests/review/customer-console-review.py
$env:REVIEW_ADMIN_PASSWORD = 'your-review-admin-password'
python tests/review/admin-all-services-review.py
python tests/review/background-console-review.py
```

The console scripts prompt for credentials when the corresponding `REVIEW_*` environment variable is not set. Do not put working credentials into source, documentation intended for publication, or committed test transcripts.

### Main files

| Path | Purpose |
|---|---|
| `pom.xml` | Maven coordinates, dependencies and Java 17 compiler setup |
| `run.bat` | Windows compile and launch script |
| `src/main/resources/db.properties` | JDBC drivers, connection URLs and non-secret defaults |
| `src/main/resources/schema.sql` | Table, constraint and index definitions |
| `src/main/resources/seed.sql` | Demo data and built-in catalogue entries |
| `src/main/java/com/amdocs/telecom/main/MainApplication.java` | Initialization, menu, service startup and shutdown |
| `src/main/java/com/amdocs/telecom/controller/` | Customer and administrator console workflows |
| `src/main/java/com/amdocs/telecom/service/` | Business service contracts and implementations |
| `src/main/java/com/amdocs/telecom/dao/` | Persistence contracts and JDBC implementations |
| `src/main/java/com/amdocs/telecom/model/` | Domain objects and enums |
| `src/main/java/com/amdocs/telecom/util/Transactions.java` | Shared JDBC transaction, nested savepoint and lock handling |
| `src/main/java/com/amdocs/telecom/util/SchemaMigration.java` | Compatible database upgrades and invoice-key migration |
| `src/main/java/com/amdocs/telecom/security/` | BCrypt helper, CAPTCHA, OTP and persisted account locks |
| `src/main/java/com/amdocs/telecom/scheduler/` | Billing, account, notification, usage and audit workers |
| `src/main/java/com/amdocs/telecom/report/ReportGenerator.java` | CSV exports and text invoices |
| `tests/review/` | Isolated service and real-console regression checks |
| `reports/case-study-fix-report.md` | Case-study audit and verification matrix |
| `reports/admin-service-review.md` | Sequential admin dashboard review and test findings |

### Package map

| Package | Responsibility |
|---|---|
| `main` | Application startup, menu and lifecycle |
| `controller` | Console presentation and user workflows |
| `service`, `service.impl` | Business contracts and use cases |
| `dao`, `dao.impl` | Persistence contracts and JDBC operations |
| `model`, `dto` | Database/domain data, enumerations and registration input |
| `factory` | DAO and service construction helpers |
| `strategy` | Payment strategy interface and payment-mode implementations |
| `scheduler` | Background executors and scheduled work |
| `security`, `validation` | Authentication support and input validation |
| `report` | CSV and invoice file generation |
| `util` | Database, transactions, schema, console layout, units and logging |
| `exception` | Application exception hierarchy |

---

## 4. Feature walkthrough

### Customer path

At the first menu choose **1. Customer Portal**. Customers can log in, register, or recover a password. The dashboard combines related actions and displays each action once:

| Dashboard option | Capability |
|---:|---|
| 1 | View profile |
| 2 | Explore and compare plans; search, filter and sort catalogue |
| 3 | View subscriptions |
| 4 | Subscribe to an active plan and choose SIM type |
| 5 | Change plan subject to policy and billing rules |
| 6 | Activate or deactivate subscription add-ons |
| 7 | View bills and invoice details |
| 8 | Pay an unpaid bill |
| 9 | View usage history and monthly summary |
| 10 | Raise a complaint |
| 11 | Track complaints and resolutions |
| 12 | View notifications |
| 0 | Log out |

Profile editing, registration and the plan explorer are accessible through their relevant flows. Console menus use aligned borders and plain `Rs` currency labels for Windows terminal compatibility.

### Administrator path

At the first menu choose **2. Administrator Portal**, then **1. Admin Login**. The dashboard groups the functions without duplicate status actions:

| Options | Group | Capabilities |
|---|---|---|
| 1-4 | Plans | View and add plans, set plan status, configure change rules |
| 5-10 | Customers and subscriptions | View and edit customers, view subscriptions, set subscription status, view/add SIMs and change SIM status |
| 11-16 | Billing and usage | Generate monthly bills, view unpaid bills/payments, scan overdue accounts, process bulk usage, view monthly usage |
| 17-21 | Reports and support | Revenue/usage reports, city distribution, complaint management/analytics, audit logs |
| 22-23 | Account | Change admin password and toggle live audit activity |
| 0 | Account | Log out |

### Plan policy

Plans include an `allow_type_change` flag and a `minimum_change_days` value. A plan change requires an active subscription, an active target plan, permission for any prepaid/postpaid switch, an elapsed minimum change period, and no duplicate active subscription to the same plan. These checks are enforced by service logic and database constraints.

### Add-ons

The catalogue is stored in `add_on_services`; customer selections are stored in `subscription_add_ons`. The composite key prevents a duplicate subscription/add-on pair. The service verifies ownership and active subscription state, locks the subscription, changes add-on state, calculates a prorated adjustment, and records an audit event in a transaction. Deactivation credits are applied to billing credit; monthly invoices consume available credit.

### Complaints and notifications

Customers can submit and track complaints. Administrators list all/open complaints, view by ID or ticket, set a resolution state with required resolution text, and inspect aggregate analytics. Complaint resolution attempts a customer notification and creates audit history. Notification persistence is performed through the notification DAO and background service for payment notifications.

---

## 5. Important classes and packages

| Class or interface | What to explain in an interview |
|---|---|
| `MainApplication` | Database startup; logging configuration; scheduler and worker lifecycle; top-level menu and shutdown |
| `CustomerController` | Registration, login, plan discovery, subscription, payment, usage, complaints, add-ons and notifications |
| `AdminController` | Admin authentication and management, bills, reports, complaints and live audit monitor |
| `AdministrationServiceImpl` | Checks active admin authority and transactionally changes customer, SIM, subscription and plan-policy data |
| `AuthenticationServiceImpl` | Password, CAPTCHA, account state, failed attempts, role-aware history and account recovery workflow |
| `LoginSecurity` | Persists role-specific lock expiry and unlocks eligible accounts |
| `OTPService` | Generates an expiring one-time value, limits verification attempts and removes a consumed/expired OTP |
| `CustomerServiceImpl` | Customer registration, validation, uniqueness and password hashing |
| `PlanServiceImpl` | Searches, filters, sorts, compares and retrieves plans using streams and functional interfaces |
| `SubscriptionServiceImpl` | Active-plan subscription, SIM allocation, plan policy, history, audit and billing in transactions |
| `AddOnServiceImpl` | Ownership-checked, transactionally billed add-on changes |
| `BillingServiceImpl` | Monthly invoices, prorated plan/add-on adjustments, credits and usage reconciliation |
| `PaymentServiceImpl` | Payment validation, payment-mode strategy, atomic writes and notification enqueueing |
| `UsageServiceImpl` | Validates units and active subscription; records usage and reconciles open bills |
| `ComplaintServiceImpl` | Complaint creation, lifecycle, resolution notifications and audit events |
| `ReportServiceImpl` | Paid revenue, customer/city results, plan subscriptions, usage ranking, unpaid customers and ARPU |
| `DBConnection` | Loads DB configuration, selects MySQL/H2, initializes schema/seed and reuses JDBC connections |
| `Transactions` | Thread-local transaction context, nested savepoints, commit/rollback and deterministic subscription locks |
| `SchemaMigration` | Compatibility migrations, deterministic invoice keys, legacy duplicate preservation and password-safe seed upgrades |
| `UsageUnits` | Validates and converts data, roaming, voice and SMS units to canonical units |
| `ConsoleMenu` | Shared fixed-width, aligned console menu rendering |
| `ApplicationLogging` | Rotating JUL file logging with a console fallback if file logging cannot initialize |
| `ReportGenerator` | Writes customer/revenue/complaint CSV and text invoice files |
| `PaymentNotificationService` | Bounded producer-consumer queue, three workers, synchronous persistence fallback and graceful drain |
| `UsageProcessor` | Four-worker batch inserts, subscription locking, billing reconciliation and synchronized count |
| `BillingScheduler` | Scheduled and manual billing; background output goes to logs, requested admin run prints details |
| `AccountMonitor` | `Callable` overdue scanning/marking, timeout handling and delinquent account suspension |
| `ConsoleActivityMonitor` | Admin-controlled polling for new audit-log entries |

### DAO and service pairs

The major domains use interfaces under `dao` and `service` with JDBC/business implementations under `dao.impl` and `service.impl`.

| Domain | DAO | Service |
|---|---|---|
| Customer | `CustomerDAO` / `CustomerDAOImpl` | `CustomerService` / `CustomerServiceImpl` |
| Admin | `AdminDAO` / `AdminDAOImpl` | `AdministrationService` / `AdministrationServiceImpl` |
| Plans | `PlanDAO` / `PlanDAOImpl` | `PlanService` / `PlanServiceImpl` |
| Subscription/SIM | `SubscriptionDAO` / `SubscriptionDAOImpl` | `SubscriptionService` / `SubscriptionServiceImpl` |
| Add-ons | `AddOnDAO` / `AddOnDAOImpl` | `AddOnService` / `AddOnServiceImpl` |
| Bills | `BillingDAO` / `BillingDAOImpl` | `BillingService` / `BillingServiceImpl` |
| Payments | `PaymentDAO` / `PaymentDAOImpl` | `PaymentService` / `PaymentServiceImpl` |
| Usage | `UsageDAO` / `UsageDAOImpl` | `UsageService` / `UsageServiceImpl` |
| Complaints | `ComplaintDAO` / `ComplaintDAOImpl` | `ComplaintService` / `ComplaintServiceImpl` |
| Reports | Data is read through domain DAOs | `ReportService` / `ReportServiceImpl` |
| Audit/notifications | `AuditAndNotificationDAO` / `AuditAndNotificationDAOImpl` | Services and workers call the DAO |

---

## 6. Database design

`schema.sql` defines **17 application tables**. The normal project MySQL database was previously checked through JDBC metadata and contained these 17 tables. Each has a primary key; the schema also defines foreign keys, unique keys, indexes, status fields and timestamps.

| Table | Purpose |
|---|---|
| `customers` | Customer profile, account state and password hash |
| `administrators` | Administrator account and state |
| `telecom_plans` | Plan pricing, allowances, type-change policy and status |
| `sim_cards` | Physical/eSIM inventory and allocation state |
| `mobile_subscriptions` | Customer-to-plan/SIM service subscription |
| `subscription_history` | Plan changes, reason, actor and timestamp |
| `usage_records` | Dated usage type, quantity, unit and charge |
| `bills` | Monthly and adjustment invoices, amounts, key and status |
| `payments` | Payment transaction reference, amount, mode, date and status |
| `complaints` | Customer issue, category, priority, status and resolution |
| `login_history` | Role-aware successful/failed login and logout events |
| `audit_logs` | Administrative and business activity history |
| `notifications` | Customer notification message and read state |
| `account_security` | Role-specific temporary account lock expiry |
| `add_on_services` | Add-on catalogue and recurring price |
| `subscription_add_ons` | Subscription/add-on junction and activation state |
| `billing_credits` | Credit available for a subscription's future monthly bill |

Key relationships include customer to subscriptions/bills/payments/complaints, plan and SIM to subscriptions, subscription to usage/history/bills/add-ons/credits, and bill to payments. Deletion behavior is selected by relationship: subscription usage/history can cascade, historical plan references can become null where appropriate, and payment references restrict destructive bill/customer deletion.

The schema uses unique keys for usernames, email/mobile, SIM numbers/IMSI, subscription/mobile identifiers, transaction references, bill numbers, and invoice identities. Monthly invoice identity is subscription plus billing month; adjustment invoices use their own idempotency key. Migration logic gives old duplicate invoices distinct `LEGACY` keys instead of deleting history.

### SQL examples worth discussing

**Conditional update**: update a bill only if it remains unpaid. This avoids a stale read changing a bill that another operation has already paid.

**Join**: subscriptions are combined with customer, plan, and SIM data when presenting a complete subscription.

**Aggregate**: monthly summaries group bill and usage values; complaint analytics uses grouped counts and conditional status totals.

**Row lock**: payment and subscription operations use `SELECT ... FOR UPDATE` inside a transaction to coordinate concurrent changes.

**Subquery**: availability and status changes use `EXISTS`/subqueries to apply conditions in the database.

Use `PreparedStatement` bind parameters for data values. A table/column name cannot be supplied as a bind parameter; dynamic identifiers must instead come from a closed allow-list.

---

## 7. JDBC, transactions, and concurrency

### Connection lifecycle

`DBConnection` uses a holder-based singleton. It tests and initializes the configured database, loading `db.properties`; `TCSMS_DB_USER` and `TCSMS_DB_PASSWORD` can supply MySQL credentials through environment variables. It tries configured MySQL first and uses file-backed H2 when the optional MySQL connection cannot be established. If `db.required=true`, a missing required MySQL connection fails rather than silently switching databases.

JDBC physical connections are reused in a bounded queue. `getConnection()` returns a wrapper: closing the wrapper rolls back unfinished work, restores auto-commit and returns the physical connection to the idle queue, or closes it if the idle queue is full. Shutdown closes idle physical connections. This is a small project-specific pool, not HikariCP or a production pool library.

### Transaction helper

`Transactions.run(work)` opens one connection for the outer operation, disables auto-commit, installs the transaction connection in a `ThreadLocal`, runs the work, then commits or rolls back. DAO calls made inside that operation use the same transaction connection through `DBConnection.getConnection()`.

Nested `Transactions.run` calls reuse the existing connection and create a JDBC savepoint. If nested work fails, it rolls back to that savepoint and rethrows a `TelecomException`. The outer transaction decides whether the whole operation commits or rolls back.

Representative flow:

```text
Transactions.run
  -> acquire connection; auto-commit off
  -> set ThreadLocal transaction connection
  -> lock customer/subscription rows as needed
  -> DAO and service calls share this connection
  -> commit on success, rollback on failure
  -> clear ThreadLocal and return connection to pool
```

`lockSubscriptions` sorts the IDs before acquiring `FOR UPDATE` locks. Consistent lock ordering reduces deadlock risk when an operation affects multiple subscriptions.

### Concurrency components

| Component | Concurrency mechanism | Work |
|---|---|---|
| `PaymentNotificationService` | Bounded `LinkedBlockingQueue`, three workers, volatile lifecycle state | Persist payment notifications; wait briefly for queue space then persist synchronously if full; drain on shutdown |
| `UsageProcessor` | Four-thread executor, JDBC batch operations, synchronized counter | Process sample usage in batches and reconcile related bills |
| `AccountMonitor` | Scheduled executor plus two task workers; `Callable`, `Future.get` and timeouts | Find overdue bills, mark statuses and suspend subscriptions beyond the configured threshold |
| `BillingScheduler` | Scheduled executor with fixed-rate tasks | Generate or reconcile monthly bills |
| `ConsoleActivityMonitor` | Single scheduled executor and volatile run state | Poll audit logs while an administrator requests it |
| `OTPService` | `ConcurrentHashMap.computeIfPresent` | Atomically verify, expire, limit attempts and consume an OTP within one JVM |

The main console's scheduled billing and account monitor call quiet background paths. Their progress goes to `logs/tcsms-%g.log`. Explicit admin actions remain interactive and display their result. The live monitor is separately requested by the administrator and prints new audit events to that admin console.

**Concurrency trade-off:** local executors and the in-memory notification queue are scoped to one process. They are adequate for a console case-study application, not a durable distributed job service.

---

## 8. Payments and billing

### Payment sequence

1. Reject non-finite or non-positive amounts and invalid/missing payment mode.
2. Ask `PaymentStrategyFactory` for the strategy and simulate its mode-specific validation.
3. Acquire a JDBC connection and disable auto-commit.
4. Load and lock the bill; verify it exists, belongs to the customer, remains unpaid and has exactly the requested amount.
5. Save payment, change bill status to `PAID`, and add payment audit data on that connection.
6. Commit the database transaction. Roll back on SQL or application failure.
7. After commit, enqueue a notification when a notification service is available.

The customer checkout offers UPI, card, net banking and bank transfer. `BANK_TRANSFER` currently routes through `NetBankingPaymentStrategy`. The gateway interaction is simulated; payment records and bill/audit changes are real database actions.

### Billing sequence

`BillingScheduler` iterates active subscriptions and creates the current month's monthly invoice once. `invoice_key` provides idempotency. Monthly charges include applicable rental, add-ons, usage, tax, discount/credit, total and due date. New usage reconciles the current month's open invoice. Paid invoices are not silently rewritten: later charges can use a separate adjustment invoice.

Plan changes calculate a prorated difference for the remaining days in the billing period. A downgrade becomes a tax-adjusted credit for a future invoice; an upgrade can result in an adjustment invoice. Adjustment keys prevent duplicate plan-charge creation for the same history record.

Add-on activation and deactivation calculate a prorated charge or credit and record an add-on adjustment. Monthly billing consumes available account credit up to the invoice value.

### Currency precision

SQL monetary columns use `DECIMAL(18,2)`. Billing internals use `BigDecimal` and explicit two-place rounding. For compatibility with existing models and some service APIs, values are still converted through Java `double` at boundaries. For a production money-critical application, migrate monetary model/API fields to `BigDecimal` end-to-end and thoroughly migrate/test stored records.

---

## 9. Authentication and security

### Passwords

`PasswordUtil` uses BCrypt hashing/verification. The current password rule requires at least eight characters, an uppercase letter, a lowercase letter, a digit and one allowed special character; whitespace is rejected. Stored customer/admin fields contain password hashes, not submitted plain-text passwords.

### CAPTCHA and login history

Login and password recovery flows use a generated CAPTCHA. Customer and admin sign-ins record role-scoped history, including failed and successful attempts, and show a previous-login timestamp when available. Password validation before requesting a password may reveal whether a username exists; consistent responses/timing are a possible production improvement.

### Lockout and recovery

Three recent failed login attempts cause a 30-minute lock. `account_security` stores the expiry by `(username, user_role)`. A subsequent login checks expiry and reactivates an eligible account. Password recovery issues a role-scoped, five-minute OTP; verification is single-use with a three-attempt maximum. After successful reset, recovery clears the lock and updates the password hash.

**Interview honesty:** this is not an email or SMS delivery integration. The demonstration prints the OTP to the console. Never describe it as secure real-world delivery.

OTP values are stored in a static `ConcurrentHashMap`, so expiry and verification apply within this running JVM. A restart loses pending OTPs and multiple application instances do not share them. Production recovery should deliver over a verified channel and store rate-limited challenges durably or in a shared store.

### Authorization

Customer-owned operations compare customer IDs to subscription/bill/complaint ownership. Administration service methods require a persisted active administrator identity. SQL values use parameter binding.

Avoid pasting passwords into a shared screen capture or committing them to Git. Keep database secrets in local environment variables or a local ignored `.env` file; the example checked into source should not be treated as a secure secret store.

---

## 10. Java, OOP, and functional programming

### OOP concepts shown in the code

| Concept | Project example |
|---|---|
| Class and object | Construct a `Bill`, `Customer`, DAO or service implementation |
| Encapsulation | Domain classes keep fields private and expose methods/accessors |
| Inheritance | `AuthenticationException` extends `TelecomException`; `ValidationException` is also in the application exception hierarchy |
| Polymorphism | Controllers/services hold service or DAO interface references; payment mode selects an implementation of `PaymentStrategy` |
| Abstraction | Service, DAO and strategy interfaces define caller-facing behavior |
| Composition | A controller/service holds collaborators; `MainApplication` manages scheduler/worker lifecycles |
| Enum | `UsageType`, `PaymentMode`, `SubscriptionType`, `SimType`, complaint category |
| Generic types | `List<Customer>`, `Optional<Bill>`, `Map<String,Double>` and `Transactions.Work<T>` |
| Record | `AddOn` is a Java record containing ID, code, name, `BigDecimal` price and status |
| Static/default methods | `SubscriptionService` defines active-subscription helpers |
| Overriding | Implementations provide interface operations; model types can override `toString` |

An application abstract base class is not a notable part of this design. Do not invent a class example if asked about inheritance; explain the exception hierarchy instead.

### Functional interfaces

| Type | Example |
|---|---|
| `Predicate<T>` | Plan price/name/data filtering; unpaid-bill filtering |
| `Function<T,R>` | Mapping a `TelecomPlan` to its key/summary in reports |
| `Consumer<T>` | Logging a generated plan-comparison summary |
| `Supplier<T>` | Providing a missing-plan exception or empty report value |
| `Runnable` | Billing, worker and usage processing tasks |
| `Callable<T>` | Overdue scan and account update that return values |
| Custom SAM | `Transactions.Work<T>` has one `run` method and permits checked exceptions |

`Function`, `Consumer` and `Supplier` are real named usages in report/plan services; `Runnable` and `Callable` are used for executor tasks. Java's `forEach` also accepts a consumer lambda. Functional interfaces let behavior travel as a value, making stream transformations and executor work composable.

### Lambdas and method references

Examples include predicates in plan filters, sort keys in `Comparator.comparingDouble`, `Runnable`/`Callable` task bodies, and `System.out::println`/model accessors passed to collection operations. A captured local variable must be final or effectively final.

### Streams

The plan service uses `filter`, `sorted` and `collect`. The report service uses `groupingBy`, `summingDouble`, `summarizingDouble`, `toMap`, `sorted`, and `limit`. Streams provide a declarative pipeline; intermediate operations are lazy until a terminal operation consumes them.

Example from the report design, simplified to show the idea:

```java
Map<Integer, Double> totalByCustomer = usageRecords.stream()
    .filter(record -> record.getUsageType() == requestedType)
    .collect(Collectors.groupingBy(
        customerForSubscription,
        Collectors.summingDouble(UsageRecord::getQuantity)));
```

The actual implementation resolves subscription owners and normalizes units before ranking. It uses a sequential stream; no parallel stream is needed for these report-sized collections.

### Java 8 concepts versus Java 17 runtime

The project demonstrates Java 8 APIs and syntax such as streams, lambdas, `Optional`, `java.time`, `Predicate`, and interface default/static methods. The configured build release is 17 and the code uses newer Java features such as records, switch expressions and `Stream.toList()`. Describe it as a **Java 17 application that demonstrates Java 8 features**, not as a Java 8 compatible application.

---

## 11. Collections and complexity

| Collection | Use |
|---|---|
| `ArrayList` / `List` | DAOs, services, report rows, batches |
| `Map` / `LinkedHashMap` | Totals and structured report fields; insertion order where useful |
| `Set` / `HashSet` | Unique identifiers/customer IDs and membership checks |
| `TreeSet` | Sort subscription IDs before acquiring locks |
| `Optional<T>` | Express a DAO lookup that may find no row |
| `ConcurrentHashMap` | Concurrent OTP entries |
| `BlockingQueue` / `LinkedBlockingQueue` | Thread-safe notification handoff with bounded capacity |
| `DoubleSummaryStatistics` | Count, sum, min, max and average for grouped reports |

Typical costs: an `ArrayList` index read is O(1), a linear search/filter is O(n), sorting is O(n log n), and hash map/set lookup is average O(1) (worst-case can be O(n)). `BlockingQueue` supplies safe producer/consumer coordination and timed blocking behavior that a plain list does not provide.

---

## 12. File handling, reports, and logging

File handling is implemented for multiple purposes:

- `ReportGenerator` exports customer, revenue, and complaint analytics as UTF-8 CSV.
- Invoice details can be written as text files.
- `DBConnection` reads classpath configuration and schema/seed SQL resources.
- `ApplicationLogging` creates a `logs` directory and rotating JUL files named `tcsms-%g.log` (three files, approximately 1 MB each).
- Scheduled service details go to the log files so timer output does not interrupt customer registration, login, or other console input.
- The administrator can choose to export revenue, customer-city, and complaint analytics from the relevant screens.

Writers, streams, connections and statements generally use try-with-resources. CSV text quoting handles embedded double quotes for the customer name field. Treat user-controlled values as data: escape all values when producing HTML and ensure CSV fields containing quotes, commas, or newlines are consistently quoted. Confirm output file permissions and prevent untrusted filenames/path traversal if filenames become user controlled.

Report files are written into the project `reports` directory (some historical methods use an output directory). Runtime logs and generated build/test data are ignored by Git. The PDF preparation material is Markdown so it can be converted separately.

---

## 13. Design patterns and SOLID

### Patterns present

| Pattern | Project example | Practical explanation |
|---|---|---|
| DAO | `CustomerDAO` / `CustomerDAOImpl`, and other domain pairs | Keeps JDBC operations behind persistence contracts |
| Factory | `DAOFactory`, `ServiceFactory`, `PaymentStrategyFactory` | Centralizes creation or selection for callers that use the factory |
| Strategy | `PaymentStrategy` with UPI, card and net-banking behavior | Selects payment-mode behavior at runtime; bank transfer uses net banking strategy |
| Singleton | `DBConnection` holder | Provides one database manager/connection-pool owner per JVM |
| Producer-consumer | Notification queue and workers | Separates notification submission from persistence work |
| MVC-like separation | Controllers, model and services/DAOs | Separates the console interaction from business and data operations, though controllers are large |
| DTO | `CustomerRegistrationDTO` | Carries input fields into registration validation/service logic |

`ConsoleActivityMonitor` periodically polls committed audit data. Describe it as scheduled polling or Observer-like behavior, not a formal push-based Observer implementation. Factories exist, but some classes still directly construct other concrete classes; the design is not consistently dependency-injected.

### SOLID discussion

- **Single Responsibility:** services and DAOs provide separation, but the two console controllers have broad responsibilities spanning prompts, navigation, formatting and orchestration.
- **Open/Closed:** strategy implementations separate payment behavior, though the factory's mode selection must be updated when a new mode is added.
- **Liskov Substitution:** implementations should preserve the behavior promised by the service/DAO contracts. Error-to-null/empty conversions would make substitution difficult; many critical flows now throw instead.
- **Interface Segregation:** domain-specific DAO and service interfaces offer useful boundaries; inspect whether each client needs every operation before enlarging an interface.
- **Dependency Inversion:** interfaces exist, but direct `new` calls and static DB access limit inversion. Constructor-injected collaborators and test doubles would improve isolation.

### Dependency injection

There is no Spring/Guice container. Some service constructors accept collaborators (for example the optional notification service in `PaymentServiceImpl`), but many use direct construction. If asked how to improve testability, describe constructor injection of service, DAO, clock and notification interfaces.

---

## 14. Testing and verified behavior

The repo includes executable review scripts rather than JUnit/Mockito tests. `IndependentReview.java` builds isolated fixtures; the MySQL runner uses a uniquely named review database and checks the database name before cleanup. Console scripts exercise the actual `MainApplication` with an isolated H2 classpath. They supply credentials by prompt/environment and keep input out of transcripts.

### Recorded verification results

| Review | Result | Coverage |
|---|---:|---|
| Full H2 service/database review | 64/64 passed | Authentication, customer, plans, subscription, billing, usage, payments, complaints, reports, migration and concurrency behaviors |
| Full MySQL service/database review | 64/64 passed | The same major persistence/business areas on MySQL |
| Customer console | 26/26 passed | Authentication, menus, plans, profile, add-ons, usage, bank transfer, payment errors and logout |
| Sequential full admin console | 52/52 passed | All 23 dashboard actions, reports/exports, complaint lifecycle, live activity, password changes/recovery and account lockouts |
| Quiet background registration review | 3/3 passed | Real registration prompt stays quiet for 65 seconds while scheduled billing and overdue monitoring complete |
| Post-admin-controller plan review | 3/3 passed | Plan search/filter/compare and plan policy behaviors after the missing-ID feedback fix |

The complete service suite totals above were run before the final narrow plan-status feedback fix. After that UI/controller change, all 52 admin console checks and the three plan-related service checks passed. The admin workflow test inspects the isolated review database; running it does not alter the normal user database or saved project credentials.

### Key regression cases

- Duplicate monthly invoices are prevented; seed reruns do not multiply demo subscriptions or usage.
- Existing/renamed invoice data survives migrations without deleting historical invoices.
- Unsupported payment, insufficient funds/invalid amount, ownership errors, already-paid bill, or audit failure does not leave partial payment writes.
- Concurrent payment attempts for one bill result in only one payment.
- Subscription creation, plan history, initial billing and plan adjustment rollback on injected failures.
- Plan-change credits are applied once; add-on activation rejects duplicates and wrong ownership.
- Usage and billing normalizes GB/MB, respects month boundaries, and does not accept NaN/nonpositive quantities.
- Three bad passwords lock an account; an expired lock can clear; admin/customer history and OTP keys are separate.
- Scheduled work generates its database effects without writing to the active console input stream.
- CSV and invoice outputs contain the expected records and amounts.

For exact scenario output, inspect `target/independent-review/results.txt`, `target/independent-review-mysql/mysql-results.txt` and the relevant console result files after running a review script. Filtered test runs overwrite their result file with only that subset.

---

## 15. Limitations and future work

Be clear about what the project does and what it does not do:

1. **No HTTP/API layer.** It is an interactive console application; it has no REST controllers, web server or browser UI.
2. **No real payment gateway.** Mode strategies simulate validation. They do not submit payment to a bank, UPI or card network.
3. **No real OTP delivery.** The OTP is simulated at the console. Move recovery to verified e-mail/SMS plus persistent, rate-limited challenge state for production.
4. **Money boundaries remain `double`.** SQL values use `DECIMAL` and billing internals use `BigDecimal`, but model/service boundaries are not fully migrated to decimal types.
5. **H2 fallback and file permissions need deployment policy.** Production deployment should explicitly require the intended database, control secrets externally, and back up data.
6. **Notification queue is in memory.** Synchronous database fallback handles queue saturation and shutdown drains queued items, but process termination or simultaneous application instances do not provide a durable distributed outbox/retry protocol.
7. **OTP map is local to one JVM.** Pending OTPs are lost at restart and cannot be shared among server processes.
8. **Console controllers are broad.** Separating input/output presenters from use-case orchestration would make them easier to maintain and test.
9. **Manual construction remains common.** Add constructor injection and test doubles for services/DAOs, database access and clock.
10. **JDBC SQL portability is bounded.** `AUTO_INCREMENT`, `FOR UPDATE`, timestamp behavior and migration DDL are tested for the chosen MySQL/H2 modes, not every DB engine.
11. **Some failure contracts are not uniform.** Critical payment, billing, subscription and usage write paths propagate errors; remaining query/services should consistently distinguish empty data from database errors.
12. **Report metrics require definitions.** “Monthly ARPU” divides paid revenue by all customers and the distinct paid billing periods, so interpret it as project-defined average paid monthly revenue per registered customer.
13. **Identifiers are UUID-derived strings.** UUIDs reduce collisions greatly, while database unique constraints remain the final guard. Customer mobile-number generation should also retry/handle a unique collision if it becomes operational rather than demo data.
14. **User-controlled output must remain escaped.** Escape all generated HTML, and quote CSV values consistently, especially if report fields may contain line breaks or delimiter characters.
15. **Role and audit policy can be expanded.** Current role checks and audit entries meet console workflow needs; production requires more formal roles, least-privilege DB credentials, secret rotation and a retention policy.
16. **Tests are custom runners.** They are runnable and isolated, but adopting JUnit and CI workflows would make per-test reporting and continuous regression checks easier.

Optional views, procedures, functions and triggers are not claimed as implemented. Explain only a feature supported by source code and the described review evidence.

---

## 16. Interview answers

### 30-second project explanation

“TCSMS is a Java 17 console application for telecom subscriptions. It has customer and admin workflows for authentication, plans, subscriptions, SIMs, usage, invoices, payments and complaints. Its layered service/DAO architecture uses JDBC with MySQL and H2 support. A few useful end-to-end examples are transactionally provisioning a subscription with its first bill, recording usage and reconciling billing, and validating a payment then atomically updating its payment, bill and audit records. Scheduled services handle billing and overdue accounts; the review scripts test the services and real console flows.”

### One-minute architecture explanation

“The console entry point starts the database, logging and background workers. Controllers collect input and invoke service interfaces. Services own validation and business policies and delegate persistence to DAO interfaces backed by JDBC. `Transactions` shares a connection across an outer operation and uses savepoints for nested work. Billing and subscription services use these boundaries to commit related changes together. Payments use a strategy chosen from the payment mode. A bounded queue handles notification persistence asynchronously. Tests use isolated H2 or uniquely named MySQL review databases, so the service and console workflows can be exercised without changing the normal database.”

### Why use DAO and service layers?

“The DAO owns SQL and row mapping; the service owns business rules and use cases. This keeps a controller focused on console interaction and gives the service one place to enforce ownership, plan and billing rules. The app still directly constructs some implementations, so constructor injection is a practical improvement.”

### How does a payment stay atomic?

“The service opens one connection, disables auto-commit, validates and locks the bill, verifies ownership and total, inserts the payment, changes the bill to paid, writes the audit record and commits. Any failure rolls the transaction back. Notification enqueueing happens after commit so a queued receipt cannot claim an uncommitted payment.”

### How do subscription changes stay consistent?

“An outer `Transactions.run` establishes the thread-local connection and transaction. Nested billing/service work reuses it and receives a savepoint. Subscription creation, initial billing and related database writes either commit together or roll back. Plan changes validate policy, record history, create a prorated adjustment or credit, and audit the outcome.”

### How do scheduled jobs avoid corrupting the console?

“The scheduler invokes noninteractive run methods that record progress through Java logging configured to rotating files. Admin-triggered operations invoke interactive paths that print summaries. The registration review holds a real prompt open through both scheduled job runs and verifies it remains quiet.”

### Why normalize usage units?

“A single canonical storage unit makes totals comparable. `UsageUnits` turns GB/MB/KB into MB, minutes/seconds into minutes and SMS into whole counts. Validation rejects unit/type mismatches, non-finite quantities and nonpositive usage.”

### What did you fix after the case-study audit?

“The console presentation now groups related actions and avoids duplicate status operations. Bank transfer is selectable and routed to the net-banking strategy. Admin reports expose highest actual usage and customers with unpaid bills. Scheduled jobs are quiet while the user types. Review tests found a missing-plan status action incorrectly reported success; the controller now checks the DAO update count and reports ‘Plan not found.’”

### What would you improve for production?

“I would move OTP delivery to a verified channel and store challenges durably, move money types to `BigDecimal` end-to-end, use an outbox for durable notifications, inject interfaces instead of constructing services directly, use managed secrets and database least privilege, and run the tests in CI. The current application is a console case study rather than a production payment or web platform.”

---

## 17. Interview question bank

### Basic questions

**What is TCSMS?**

A console system for telecom plans, customers, subscriptions, usage, bills, payments, complaints and administration.

**What language/runtime does it use?**

Java 17. It demonstrates Java 8 APIs, but records, switch expressions and other post-Java-8 syntax mean it does not target Java 8.

**Why use JDBC?**

The project has explicit SQL and row mapping, which makes the schema and persistence flow visible for the case study. The trade-off is that connection and SQL details require more code than an ORM.

**What is a DAO?**

A data access object wraps persistence operations for a domain. A service can ask `BillingDAO` to find/save bills without embedding that SQL in its console controller.

**What is a primary key versus a foreign key?**

A primary key uniquely identifies a row. A foreign key references a key in another table and enforces a relationship, such as a subscription's customer or plan.

**What is a prepared statement?**

A parameterized SQL statement. Values are supplied separately, which prevents user data from becoming executable SQL and lets the driver handle type conversion.

**What does BCrypt do?**

It stores a salted, intentionally expensive password hash so the database does not store the submitted password in plain text.

**Why are models and enums useful?**

Models group domain fields into typed objects. Enums constrain values like payment mode and usage type to a known set.

### Intermediate questions

**How do prepaid/postpaid plan changes work?**

The service checks the current and target plan rules, active state, duplicates and waiting period. If the type changes, both relevant plan policies must permit it. The change and its history/adjustment share a transaction.

**What is the role of `Optional`?**

It makes a potentially missing DAO lookup explicit. Callers can handle absence or throw a domain error instead of assuming a row exists.

**What is the difference between `map` and `filter` in a stream?**

`map` transforms each element; `filter` retains only elements satisfying a predicate.

**Why are executor services used?**

They manage worker threads and task queues. The notification service and usage processor can process independent work without the caller manually creating a thread for every task.

**Why is `Callable` different from `Runnable`?**

`Callable<T>` returns a value and may throw a checked exception; a `Runnable` does not return a result. `AccountMonitor` uses `Future` results from callables.

**What happens if payment audit insertion fails?**

The payment transaction rolls back so neither a completed payment nor a paid-bill status is left without its required audit event.

**Why add a unique invoice key?**

It protects monthly bill idempotency at the database level. If a scheduler retries, an already-generated subscription/month invoice is recognized rather than duplicated.

**Why are credits stored separately?**

A downgrade or add-on reversal can leave an amount to apply to a later bill. `billing_credits` lets monthly invoicing consume that balance rather than losing or double-applying it.

**How is a customer's ownership checked?**

Service operations compare the authenticated customer identity with the customer linked to the subscription, bill or complaint. An opaque ID alone is not authorization.

### Advanced questions

**Does `DBConnection` mean one shared JDBC connection?**

No. It is a singleton manager for a bounded set of reusable physical connections. Each `getConnection()` returns a wrapper; transaction context ensures related DAO calls on one thread reuse the same physical transaction connection.

**Why use `ThreadLocal` transaction context?**

Nested service/DAO calls on the same request thread need the outer connection without adding a connection parameter to every public method. Other threads do not accidentally share that connection.

**Why create a savepoint for nested transactions?**

A nested service can undo its partial work if it fails while allowing the caller to decide what happens to earlier outer work. If the failure propagates, the outer transaction can still roll everything back.

**Why use `FOR UPDATE`?**

It obtains a database row lock so another transaction cannot concurrently make a conflicting change to the bill or subscription while the current transaction validates and updates it.

**Why sort subscription IDs before locking?**

Consistent ordering helps prevent two transactions from each holding one lock while waiting for the other in the opposite order.

**Does a successful queue enqueue mean a customer received an SMS?**

No. It means a notification was accepted for persistence. The project stores a notification row; it does not connect to an SMS gateway.

**Is this a REST API or Spring Boot app?**

No. It has a Java console UI and JDBC layers; no HTTP controller, web server or Spring dependency was found.

**What production concern remains in OTP handling?**

The demo displays the OTP locally, and its storage is process-local. Production recovery needs verified delivery, rate limiting, durable/shared expiring storage and operational audit/monitoring.

**Are all money calculations fully decimal?**

No. SQL uses `DECIMAL` and billing internals use `BigDecimal`, but some models and API boundaries still use `double`. A full precision migration is future work.

**Does the notification queue guarantee delivery after a crash?**

No. The queue exists only in memory. Workers drain it on normal shutdown, and a full queue falls back to synchronous database persistence, but process termination can lose work that was only queued.

**How are unique UUID-derived identifiers protected?**

UUID fragments make collisions very unlikely. Database unique constraints are the final guard; a production system should retry and report cleanly if a collision occurs.

**Why are tests using isolated databases?**

It makes fixtures repeatable and prevents test writes from changing the normal customer/admin records. The MySQL script creates a unique review database, validates its name before cleanup, and drops only that test database.

### Tricky follow-ups

**What if billing runs twice at once?**

Transaction/row locking and unique invoice keys prevent a second monthly invoice from being committed for the same subscription and period. A duplicate-invoice exception should be handled as an already-completed/idempotent outcome in any future retry orchestration.

**What if an account monitor finds a bill but another action pays it?**

Updates include eligible status predicates, and subscription/billing work uses database transactions and locks. This reduces stale-state updates; multi-process production scheduling still needs carefully defined isolation and retry behavior.

**Are reports called monthly ARPU in the strict industry sense?**

The project metric is paid revenue divided by total customers and the number of distinct paid billing months. Be precise about that definition instead of claiming it uses only paying customers.

**Is `ConsoleActivityMonitor` a true Observer?**

It is periodic database polling. It is Observer-like in purpose, but there is no direct push/subscription event channel.

**Is `newFixedThreadPool` always the correct choice?**

It bounds concurrent worker count, which suits a small local workload. Thread-pool size, queue capacity, backpressure, metrics and database connection capacity must be load-tested for production.

**Why not use `parallelStream()` for every report?**

Parallelism can cost more than it saves for small in-memory lists and can complicate database-bound workloads. The project uses ordinary streams and explicit executor tasks where batching/concurrency is part of the workflow.

---

## 18. Revision sheet and source index

### One-line answers

| Topic | Short answer |
|---|---|
| App type | Java 17 console application; no REST server |
| Persistence | JDBC with DAOs; configured MySQL and file-backed H2 fallback |
| Database size | 17 tables in the current project schema |
| Main patterns | DAO, Factory, Strategy, Singleton, producer-consumer and MVC-like layering |
| Payment atomicity | One JDBC transaction covers payment, bill state and audit; notification follows commit |
| Subscription atomicity | Thread-local shared connection with nested savepoints covers provisioning, billing and plan changes |
| Money | SQL `DECIMAL`, billing `BigDecimal`; Java models still have some `double` boundaries |
| Payment integration | Strategy validation is simulated; no real gateway |
| OTP delivery | Simulated console output; no real mail/SMS sender |
| Background output | Rotating file logs keep scheduled jobs out of interactive prompts |
| Automated verification | Custom executable review scripts; 64 service scenarios per DB, plus real-console checks |
| Current runtime target | Java 17, not Java 8 |

### Interview anchors by topic

| If asked about... | Start with... |
|---|---|
| A workflow | “Let me walk through the payment/subscription flow and show which records share a transaction.” |
| A bug fix | “The admin plan-status action checked the DAO update result so a missing plan now returns a not-found message.” |
| Testing | “The service runners use isolated H2/MySQL databases, then scripts drive the real console one prompt at a time.” |
| Security | “Passwords are BCrypt hashed; login attempts are role-scoped; the OTP is still only simulated at the console.” |
| Threading | “A bounded queue separates payment notification persistence from the caller; scheduled jobs log quietly while the console waits for input.” |
| Limitations | “It is a case-study console app. The gateway, OTP delivery and durable cross-process notification service are not production integrations.” |

### Selected source index

| Subject | Source |
|---|---|
| Application start and shutdown | `src/main/java/com/amdocs/telecom/main/MainApplication.java` |
| Customer workflow | `src/main/java/com/amdocs/telecom/controller/CustomerController.java` |
| Admin workflow | `src/main/java/com/amdocs/telecom/controller/AdminController.java` |
| Transaction management | `src/main/java/com/amdocs/telecom/util/Transactions.java` |
| JDBC manager and reuse pool | `src/main/java/com/amdocs/telecom/util/DBConnection.java` |
| Migration and repeat-safe seeds | `src/main/java/com/amdocs/telecom/util/SchemaMigration.java`, `src/main/resources/seed.sql` |
| Table definitions | `src/main/resources/schema.sql` |
| Payment and billing | `src/main/java/com/amdocs/telecom/service/impl/PaymentServiceImpl.java`, `BillingServiceImpl.java` |
| Subscription and add-on flows | `src/main/java/com/amdocs/telecom/service/impl/SubscriptionServiceImpl.java`, `AddOnServiceImpl.java` |
| Usage units and service | `src/main/java/com/amdocs/telecom/util/UsageUnits.java`, `src/main/java/com/amdocs/telecom/service/impl/UsageServiceImpl.java` |
| Authentication security | `src/main/java/com/amdocs/telecom/security/PasswordUtil.java`, `OTPService.java`, `LoginSecurity.java` |
| Reports and file output | `src/main/java/com/amdocs/telecom/service/impl/ReportServiceImpl.java`, `src/main/java/com/amdocs/telecom/report/ReportGenerator.java` |
| Schedulers and workers | `src/main/java/com/amdocs/telecom/scheduler/` |
| Test runner | `tests/review/IndependentReview.java`, `tests/review/run-review.ps1` |
| Console checks | `tests/review/customer-console-review.py`, `admin-all-services-review.py`, `background-console-review.py` |
| Case-study audit | `reports/case-study-fix-report.md` |

### Database table quick recall

`customers`, `administrators`, `telecom_plans`, `sim_cards`, `mobile_subscriptions`, `subscription_history`, `usage_records`, `bills`, `payments`, `complaints`, `login_history`, `audit_logs`, `notifications`, `account_security`, `add_on_services`, `subscription_add_ons`, and `billing_credits`.

### Preparation reminder

Explain what the code actually does. State which work is committed atomically, which background task is scheduled, which notification is only persisted, which gateway is simulated, and what remains a production improvement. Use the review output and source files as evidence; do not describe a proposed improvement as implemented behavior.

---


## 19. Concepts with exact source references

**Source snapshot:** 7 October 2026. Line numbers below are one-based and point to actual statements/declarations, rather than only import lines. They refer to the current Java files, not to README lines. Source edits can shift them; recheck the references after changing code.

Each entry explains the concept, why it is useful in this application, and a representative source location. Java paths start at `src/main/java/com/amdocs/telecom/`. The linked labels include the subfolder, filename and exact line number; the link target contains the full repository-relative path.

### How to use this section in an interview

Explain the concept in one sentence, identify the project operation it enables, then open the cited line. For example: “A Future represents the pending result of a worker task. AccountMonitor waits for its overdue-bill scan through a timed Future.get, so it can use the returned bills and detect a timeout.”

### Multithreading and coordination

| Concept | Meaning | Why we used it / project benefit | Source file and code line |
|---|---|---|---|
| ExecutorService / thread pool | Reuses a controlled number of worker threads for submitted tasks. | Process usage batches concurrently with four workers rather than constructing a new thread for each batch. | [scheduler/UsageProcessor.java:26](src/main/java/com/amdocs/telecom/scheduler/UsageProcessor.java#L26) |
| Runnable and task submission | A task with no return value is handed to an executor. | Runs notification consumers separately from the customer payment workflow. | [scheduler/PaymentNotificationService.java:35](src/main/java/com/amdocs/telecom/scheduler/PaymentNotificationService.java#L35) |
| ScheduledExecutorService | Runs a task periodically after an initial delay. | Automatically generates/reconciles bills while the application is open. | [scheduler/BillingScheduler.java:34](src/main/java/com/amdocs/telecom/scheduler/BillingScheduler.java#L34) |
| Fixed-delay scheduling | Waits the configured delay after the previous scheduled invocation finishes. | Avoids immediately repeating the account scan when a scan takes time. | [scheduler/AccountMonitor.java:25](src/main/java/com/amdocs/telecom/scheduler/AccountMonitor.java#L25) |
| Callable<T> | Represents a task that returns a result and can throw an exception. | An overdue scan returns its bill list; a marking task returns its count. | [scheduler/AccountMonitor.java:42](src/main/java/com/amdocs/telecom/scheduler/AccountMonitor.java#L42) |
| Future and timeout | Represents a submitted task result; timed get bounds the caller wait. | Waits for the scan result instead of assuming the worker has completed. | [scheduler/AccountMonitor.java:74](src/main/java/com/amdocs/telecom/scheduler/AccountMonitor.java#L74) |
| synchronized block | Allows one thread at a time to access a protected critical section. | Protects the shared processed count from lost increments by concurrent usage workers. | [scheduler/UsageProcessor.java:57](src/main/java/com/amdocs/telecom/scheduler/UsageProcessor.java#L57) |
| volatile visibility | Makes updated field values visible to other threads; it does not make compound operations atomic. | Notification workers can observe the shutdown flag written by the lifecycle thread. | [scheduler/PaymentNotificationService.java:24](src/main/java/com/amdocs/telecom/scheduler/PaymentNotificationService.java#L24) |
| BlockingQueue / producer-consumer | Provides safe bounded handoff between producer and consumer threads. | Queues notifications while three consumers persist them; capacity limits queued memory. | [scheduler/PaymentNotificationService.java:27](src/main/java/com/amdocs/telecom/scheduler/PaymentNotificationService.java#L27) |
| Timed offer / backpressure | Waits briefly for room in a bounded queue. | Uses a 500 ms enqueue timeout before synchronous database persistence if the queue is full. | [scheduler/PaymentNotificationService.java:66](src/main/java/com/amdocs/telecom/scheduler/PaymentNotificationService.java#L66) |
| Graceful worker drain | Consumers continue until both shutdown is requested and pending work is empty. | Processes queued notifications during a normal shutdown within the termination timeout. | [scheduler/PaymentNotificationService.java:38](src/main/java/com/amdocs/telecom/scheduler/PaymentNotificationService.java#L38) |
| Executor lifecycle | Stops accepting new work and waits for active tasks before forcing cancellation. | Releases scheduler resources when the application exits. | [scheduler/BillingScheduler.java:83](src/main/java/com/amdocs/telecom/scheduler/BillingScheduler.java#L83) |
| ConcurrentHashMap atomic update | Updates a map entry atomically through computeIfPresent. | Checks OTP expiry/attempts and consumes a successful OTP in one per-entry operation. | [security/OTPService.java:37](src/main/java/com/amdocs/telecom/security/OTPService.java#L37) |
| AtomicBoolean | Provides an atomic compare-and-set operation. | Ensures the main shutdown path performs service shutdown once even if multiple exit paths call it. | [main/MainApplication.java:20](src/main/java/com/amdocs/telecom/main/MainApplication.java#L20) |
| ThreadLocal | Stores a value separately for each thread. | Shares one transaction connection among nested DAO calls on the same request thread. | [util/Transactions.java:10](src/main/java/com/amdocs/telecom/util/Transactions.java#L10) |
| Deterministic lock order | Acquires database locks in a consistent sorted order. | Reduces deadlock risk when usage work touches multiple subscriptions. | [util/Transactions.java:19](src/main/java/com/amdocs/telecom/util/Transactions.java#L19) |

### File handling and resources

| Concept | Meaning | Why we used it / project benefit | Source file and code line |
|---|---|---|---|
| File and directories | Represents filesystem paths and creates an output directory. | Ensures exported report files have an existing reports directory. | [report/ReportGenerator.java:28](src/main/java/com/amdocs/telecom/report/ReportGenerator.java#L28) |
| BufferedWriter | Buffers character output before sending it to the underlying stream. | Writes report rows efficiently instead of issuing a low-level write for every character. | [report/ReportGenerator.java:35](src/main/java/com/amdocs/telecom/report/ReportGenerator.java#L35) |
| FileOutputStream | Writes bytes to a filesystem file. | Provides the file destination for generated CSV and invoice output. | [report/ReportGenerator.java:36](src/main/java/com/amdocs/telecom/report/ReportGenerator.java#L36) |
| OutputStreamWriter / UTF-8 | Encodes Java character data into bytes with an explicit charset. | Makes CSV output predictable across machines and supports customer text beyond ASCII. | [report/ReportGenerator.java:36](src/main/java/com/amdocs/telecom/report/ReportGenerator.java#L36) |
| Classpath InputStream | Opens a packaged resource as a byte stream. | Reads database configuration from db.properties without hard-coding it into business classes. | [util/DBConnection.java:41](src/main/java/com/amdocs/telecom/util/DBConnection.java#L41) |
| BufferedReader / InputStreamReader | Converts resource bytes to characters and reads lines. | Loads schema/seed SQL statements for database startup. | [util/DBConnection.java:122](src/main/java/com/amdocs/telecom/util/DBConnection.java#L122) |
| Try-with-resources | Automatically closes AutoCloseable resources on success or failure. | Closes the CSV writer even if building or writing a report throws an exception. | [report/ReportGenerator.java:35](src/main/java/com/amdocs/telecom/report/ReportGenerator.java#L35) |
| NIO Files / Path | Uses Java filesystem APIs to create directories. | Creates the runtime logs directory before attaching the log file handler. | [util/ApplicationLogging.java:21](src/main/java/com/amdocs/telecom/util/ApplicationLogging.java#L21) |
| Rotating FileHandler | Writes log records to files with size-based rotation. | Keeps scheduled progress/errors off console prompts and limits each log generation size. | [util/ApplicationLogging.java:22](src/main/java/com/amdocs/telecom/util/ApplicationLogging.java#L22) |
| ByteArrayOutputStream | Builds binary output in memory before writing the result. | Assembles the existing basic PDF invoice bytes and writes the completed document to disk. | [report/ReportGenerator.java:278](src/main/java/com/amdocs/telecom/report/ReportGenerator.java#L278) |

### OOP, types and object relationships

| Concept | Meaning | Why we used it / project benefit | Source file and code line |
|---|---|---|---|
| Classes and objects | A class defines data/behavior; new creates an object instance. | Creates a payment domain object with this transaction's bill, customer, amount and mode. | [service/impl/PaymentServiceImpl.java:92](src/main/java/com/amdocs/telecom/service/impl/PaymentServiceImpl.java#L92) |
| Encapsulation / private fields | Keeps internal state behind the class's public methods. | Customer state is exposed through domain accessors rather than public data fields. | [model/Customer.java:10](src/main/java/com/amdocs/telecom/model/Customer.java#L10) |
| Constructors | Initialize a new instance before it is used. | Supports assembling a Customer from its known domain fields. | [model/Customer.java:27](src/main/java/com/amdocs/telecom/model/Customer.java#L27) |
| this | Refers to the current object and distinguishes a field from a same-named parameter. | Assigns the constructor argument to the Customer instance field. | [model/Customer.java:31](src/main/java/com/amdocs/telecom/model/Customer.java#L31) |
| Inheritance | A subclass extends a parent type. | Authentication failures can be handled specifically or as general TelecomException failures. | [exception/AuthenticationException.java:3](src/main/java/com/amdocs/telecom/exception/AuthenticationException.java#L3) |
| super | Calls a superclass constructor or method. | Passes an authentication error message to the common exception parent. | [exception/AuthenticationException.java:7](src/main/java/com/amdocs/telecom/exception/AuthenticationException.java#L7) |
| Interfaces / abstraction | Defines a behavior contract that callers can depend on. | Subscription operations expose business use cases without exposing their JDBC details. | [service/SubscriptionService.java:9](src/main/java/com/amdocs/telecom/service/SubscriptionService.java#L9) |
| Runtime polymorphism | An interface reference invokes the selected concrete implementation. | The same validateAndProcess call invokes UPI, card or net-banking behavior according to the chosen mode. | [service/impl/PaymentServiceImpl.java:62](src/main/java/com/amdocs/telecom/service/impl/PaymentServiceImpl.java#L62) |
| Composition | An object owns or holds collaborators used to perform its job. | The notification service holds a queue and executor whose lifecycle it manages. | [scheduler/PaymentNotificationService.java:22](src/main/java/com/amdocs/telecom/scheduler/PaymentNotificationService.java#L22) |
| Method overriding | Provides an implementation of a parent/interface method. | The JDBC wrapper implements Connection.close to reset/return the physical connection rather than always closing it. | [util/DBConnection.java:174](src/main/java/com/amdocs/telecom/util/DBConnection.java#L174) |
| Method overloading | Uses one method name with different parameter lists. | BillingDAOImpl offers lookup with an acquired connection or a caller-provided transaction connection. | [dao/impl/BillingDAOImpl.java:65](src/main/java/com/amdocs/telecom/dao/impl/BillingDAOImpl.java#L65) |
| Enums | Constrains a field or argument to a named set of valid values. | PaymentMode identifies supported checkout modes instead of arbitrary payment strings throughout the domain. | [model/PaymentMode.java:3](src/main/java/com/amdocs/telecom/model/PaymentMode.java#L3) |
| Records | Declares a compact data carrier with generated accessors, equality and representation. | AddOn represents catalogue data using components including a BigDecimal monthly price. | [model/AddOn.java:5](src/main/java/com/amdocs/telecom/model/AddOn.java#L5) |
| Generics | Uses type parameters for reusable code with compile-time type checking. | Work<T> and run return the operation's result type while using shared transaction behavior. | [util/Transactions.java:40](src/main/java/com/amdocs/telecom/util/Transactions.java#L40) |
| final reference | Prevents a field reference from being reassigned after initialization. | Keeps the notification service linked to its worker pool; the pool object still changes lifecycle state. | [scheduler/PaymentNotificationService.java:22](src/main/java/com/amdocs/telecom/scheduler/PaymentNotificationService.java#L22) |
| Static utility method | Belongs to a type rather than an individual object. | SubscriptionService.isActive provides a reusable status predicate. | [service/SubscriptionService.java:10](src/main/java/com/amdocs/telecom/service/SubscriptionService.java#L10) |
| Default interface method | Provides reusable behavior in the interface while retaining abstract operations. | Builds active subscriptions on top of the implementation's getCustomerSubscriptions result. | [service/SubscriptionService.java:11](src/main/java/com/amdocs/telecom/service/SubscriptionService.java#L11) |
| Singleton / holder | Provides one manager instance per class loader through JVM initialization. | Centralizes database setup and the idle connection pool for this process. | [util/DBConnection.java:36](src/main/java/com/amdocs/telecom/util/DBConnection.java#L36) |
| Factory | Centralizes creation of an implementation behind a helper. | DAOFactory creates the customer JDBC implementation for callers using that factory. | [factory/DAOFactory.java:11](src/main/java/com/amdocs/telecom/factory/DAOFactory.java#L11) |
| Strategy | Selects interchangeable behavior through a common contract. | PaymentStrategyFactory maps the payment mode to the processing strategy. | [strategy/PaymentStrategyFactory.java:14](src/main/java/com/amdocs/telecom/strategy/PaymentStrategyFactory.java#L14) |

### Functional concepts and interactions

| Concept | Meaning | Why we used it / project benefit | Source file and code line |
|---|---|---|---|
| Functional interface / SAM | An interface with one abstract method can be implemented by a lambda. | Transactions.Work<T> accepts a business operation that can return a result or throw an exception. | [util/Transactions.java:14](src/main/java/com/amdocs/telecom/util/Transactions.java#L14) |
| Lambda expression | Expresses a behavior as a value without declaring a separate implementation class. | Builds a plan-name match condition from the user's keyword. | [service/impl/PlanServiceImpl.java:40](src/main/java/com/amdocs/telecom/service/impl/PlanServiceImpl.java#L40) |
| Predicate<T> | Accepts a value and returns a boolean through test. | Filters catalogue entries to plans matching a name or price/data condition. | [service/impl/PlanServiceImpl.java:42](src/main/java/com/amdocs/telecom/service/impl/PlanServiceImpl.java#L42) |
| Predicate composition | Combines existing boolean rules using and/or/negate. | Requires a plan price to satisfy both the lower and upper bounds. | [service/impl/PlanServiceImpl.java:87](src/main/java/com/amdocs/telecom/service/impl/PlanServiceImpl.java#L87) |
| Function<T,R> | Maps one input to a return value through apply. | Turns a TelecomPlan into a readable comparison summary. | [service/impl/PlanServiceImpl.java:107](src/main/java/com/amdocs/telecom/service/impl/PlanServiceImpl.java#L107) |
| Consumer<T> | Accepts a value with a side effect and no return value through accept. | Writes the function-generated comparison summary to the logger. | [service/impl/PlanServiceImpl.java:108](src/main/java/com/amdocs/telecom/service/impl/PlanServiceImpl.java#L108) |
| Supplier<T> | Provides a value without an input through get. | Creates a missing-plan exception only when Optional needs it. | [service/impl/PlanServiceImpl.java:93](src/main/java/com/amdocs/telecom/service/impl/PlanServiceImpl.java#L93) |
| Method reference | Refers to an existing method as functional behavior. | Extracts monthly rental for a plan price comparator. | [service/impl/PlanServiceImpl.java:69](src/main/java/com/amdocs/telecom/service/impl/PlanServiceImpl.java#L69) |
| Comparator and reversal | Defines sort ordering for values. | Orders price lists in ascending or descending order requested by the customer. | [service/impl/PlanServiceImpl.java:72](src/main/java/com/amdocs/telecom/service/impl/PlanServiceImpl.java#L72) |
| Stream pipeline | Applies intermediate transformations and a terminal operation to a sequence. | Runs the match predicate against each plan and collects the resulting list. | [service/impl/PlanServiceImpl.java:42](src/main/java/com/amdocs/telecom/service/impl/PlanServiceImpl.java#L42) |
| groupingBy | Partitions stream values into groups keyed by an extracted value. | Builds customer lists for the city-distribution report. | [service/impl/ReportServiceImpl.java:67](src/main/java/com/amdocs/telecom/service/impl/ReportServiceImpl.java#L67) |
| summarizingDouble | Collects count, sum, average, minimum and maximum. | Summarizes paid invoice amounts for the report's grouped billing months. | [service/impl/ReportServiceImpl.java:82](src/main/java/com/amdocs/telecom/service/impl/ReportServiceImpl.java#L82) |
| Function.identity / toMap | Maps each value to itself when building a keyed lookup. | Builds a plan-ID-to-plan map for reporting subscriber counts. | [service/impl/ReportServiceImpl.java:117](src/main/java/com/amdocs/telecom/service/impl/ReportServiceImpl.java#L117) |
| Optional and lazy exception | Makes missing data explicit and invokes a supplier only when absent. | Returns the requested plan or throws the prepared domain error instead of returning null. | [service/impl/PlanServiceImpl.java:96](src/main/java/com/amdocs/telecom/service/impl/PlanServiceImpl.java#L96) |
| Effectively final captured value | A lambda captures a local variable that is not reassigned. | Each bulk-usage task uses the batch number corresponding to the batch that was submitted. | [scheduler/UsageProcessor.java:45](src/main/java/com/amdocs/telecom/scheduler/UsageProcessor.java#L45) |
| Callback execution | The receiver decides when to run the behavior supplied by the caller. | Transactions executes business work inside commit/rollback management. | [util/Transactions.java:46](src/main/java/com/amdocs/telecom/util/Transactions.java#L46) |

### Collections and aggregation

| Concept | Meaning | Why we used it / project benefit | Source file and code line |
|---|---|---|---|
| ArrayList / List | Stores an ordered sequence with indexed access. | Builds a typed list of usage records before dividing it into worker batches. | [scheduler/UsageProcessor.java:74](src/main/java/com/amdocs/telecom/scheduler/UsageProcessor.java#L74) |
| LinkedHashMap | Stores key/value pairs while retaining insertion order. | Builds report fields in a stable order for presentation. | [service/impl/ReportServiceImpl.java:131](src/main/java/com/amdocs/telecom/service/impl/ReportServiceImpl.java#L131) |
| Set | Stores unique values for membership checks. | Collects customers with unpaid bills once even if each customer has multiple bills. | [service/impl/ReportServiceImpl.java:160](src/main/java/com/amdocs/telecom/service/impl/ReportServiceImpl.java#L160) |
| toMap / keyed lookup | Builds a map by extracting keys and values from records. | Resolves a usage record's subscription to its owning customer for the ranking report. | [service/impl/ReportServiceImpl.java:52](src/main/java/com/amdocs/telecom/service/impl/ReportServiceImpl.java#L52) |
| summingDouble | Aggregates numeric values while collecting a stream. | Adds normalized usage by customer, so GB and MB values contribute comparable quantities. | [service/impl/ReportServiceImpl.java:54](src/main/java/com/amdocs/telecom/service/impl/ReportServiceImpl.java#L54) |
| limit | Bounds the number of elements passed downstream. | Displays at most ten customers after sorting by usage. | [service/impl/ReportServiceImpl.java:57](src/main/java/com/amdocs/telecom/service/impl/ReportServiceImpl.java#L57) |
| StringBuilder | Accumulates text without constructing a new string for each append. | Assembles multiline SQL statements before executing the schema/seed script. | [util/DBConnection.java:123](src/main/java/com/amdocs/telecom/util/DBConnection.java#L123) |

### JDBC, errors, precision and other concepts

| Concept | Meaning | Why we used it / project benefit | Source file and code line |
|---|---|---|---|
| PreparedStatement | Separates an SQL operation from bound parameter values. | Looks up and locks a bill using its bound ID while avoiding string-interpolated input. | [dao/impl/BillingDAOImpl.java:69](src/main/java/com/amdocs/telecom/dao/impl/BillingDAOImpl.java#L69) |
| FOR UPDATE / row locking | Locks selected rows for an eligible database transaction. | Serializes conflicting payment attempts on the same bill. | [dao/impl/BillingDAOImpl.java:67](src/main/java/com/amdocs/telecom/dao/impl/BillingDAOImpl.java#L67) |
| Commit | Makes the transaction's completed database changes persistent. | Commits payment, bill status and required audit entry together. | [service/impl/PaymentServiceImpl.java:115](src/main/java/com/amdocs/telecom/service/impl/PaymentServiceImpl.java#L115) |
| Rollback | Undoes pending changes made by the transaction. | Leaves the bill/payment unchanged if a write or validation fails before commit. | [service/impl/PaymentServiceImpl.java:126](src/main/java/com/amdocs/telecom/service/impl/PaymentServiceImpl.java#L126) |
| Savepoint | Marks a partial rollback location inside an existing transaction. | Nested service work can undo its own changes while retaining the outer transaction context. | [util/Transactions.java:45](src/main/java/com/amdocs/telecom/util/Transactions.java#L45) |
| Dynamic proxy | Intercepts interface method calls while delegating others. | Lets inner DAOs close their wrapper without closing the connection owned by the outer transaction. | [util/Transactions.java:30](src/main/java/com/amdocs/telecom/util/Transactions.java#L30) |
| Checked custom exception | Requires calling code to handle or declare a domain failure. | TelecomException carries business/database failure information across service boundaries. | [exception/TelecomException.java:3](src/main/java/com/amdocs/telecom/exception/TelecomException.java#L3) |
| Exception cause chaining | Retains the original failure when constructing a higher-level exception. | Keeps diagnostic information when a transaction-level error is exposed to a service caller. | [exception/TelecomException.java:11](src/main/java/com/amdocs/telecom/exception/TelecomException.java#L11) |
| BigDecimal / explicit rounding | Represents decimal amounts and specifies rounding policy. | Rounds billing money to two places; SQL uses DECIMAL while some Java boundaries still use double. | [service/impl/BillingServiceImpl.java:20](src/main/java/com/amdocs/telecom/service/impl/BillingServiceImpl.java#L20) |
| java.time / YearMonth | Represents a calendar billing period without a time-of-day component. | Checks the billing-month format and calculates days used for proration. | [service/impl/BillingServiceImpl.java:24](src/main/java/com/amdocs/telecom/service/impl/BillingServiceImpl.java#L24) |
| UUID | Produces identifiers with a very low random collision probability. | Generates transaction references; database uniqueness is still the final guard. | [service/impl/PaymentServiceImpl.java:91](src/main/java/com/amdocs/telecom/service/impl/PaymentServiceImpl.java#L91) |
| Regular expression validation | Checks text against a defined pattern. | Tests password complexity before hashing/persisting a new password. | [security/PasswordUtil.java:15](src/main/java/com/amdocs/telecom/security/PasswordUtil.java#L15) |
| BCrypt hashing | Uses a salted, deliberately costly password hash. | Stores a hash and verifies a supplied password without keeping that password in plain text. | [security/PasswordUtil.java:42](src/main/java/com/amdocs/telecom/security/PasswordUtil.java#L42) |

### Functional interaction: how the pieces work together

“Functional interaction” is explained here as the way lambdas, functional interfaces and the APIs consuming them cooperate in a real operation. It is not a separate Java language feature.

#### 1. Predicate feeds a stream filter

The plan service declares a `Predicate<TelecomPlan>` for a keyword match, supplies it to `Stream.filter`, and collects the matching plans. The predicate describes the condition; the stream applies it to each plan.

[service/impl/PlanServiceImpl.java:40](src/main/java/com/amdocs/telecom/service/impl/PlanServiceImpl.java#L40); [service/impl/PlanServiceImpl.java:42](src/main/java/com/amdocs/telecom/service/impl/PlanServiceImpl.java#L42); [service/impl/PlanServiceImpl.java:43](src/main/java/com/amdocs/telecom/service/impl/PlanServiceImpl.java#L43).

#### 2. Function produces a value; Consumer uses that value

`comparePlans` declares a `Function<TelecomPlan,String>` that creates summary text. It calls `apply` for each plan, builds a combined comparison message, then passes that message to `Consumer<String>.accept` for logging. The function performs a transformation; the consumer performs a side effect.

[service/impl/PlanServiceImpl.java:107](src/main/java/com/amdocs/telecom/service/impl/PlanServiceImpl.java#L107); [service/impl/PlanServiceImpl.java:108](src/main/java/com/amdocs/telecom/service/impl/PlanServiceImpl.java#L108); [service/impl/PlanServiceImpl.java:109](src/main/java/com/amdocs/telecom/service/impl/PlanServiceImpl.java#L109).

#### 3. Supplier works with Optional

`getPlanById` prepares a `Supplier<TelecomException>`. The DAO returns an `Optional<TelecomPlan>`; `orElseThrow` calls the supplier when the plan is absent. When the plan exists, it returns the plan without constructing the exception.

[service/impl/PlanServiceImpl.java:93](src/main/java/com/amdocs/telecom/service/impl/PlanServiceImpl.java#L93); [service/impl/PlanServiceImpl.java:96](src/main/java/com/amdocs/telecom/service/impl/PlanServiceImpl.java#L96).

#### 4. Method reference supplies a comparator key

`TelecomPlan::getMonthlyRental` supplies the key function to `Comparator.comparingDouble`. The comparator is optionally reversed, supplied to `Stream.sorted`, and the ordered result is collected. The method reference reuses an existing accessor instead of writing a new class.

[service/impl/PlanServiceImpl.java:69](src/main/java/com/amdocs/telecom/service/impl/PlanServiceImpl.java#L69); [service/impl/PlanServiceImpl.java:76](src/main/java/com/amdocs/telecom/service/impl/PlanServiceImpl.java#L76).

#### 5. A transaction callback supplies work; the helper supplies lifecycle

`subscribeToPlan` passes a lambda to `Transactions.run`. Its target interface is `Work<T>`. The helper runs that callback on the transaction connection and owns commit/rollback. This separates the use-case operation from transaction lifecycle code. ThreadLocal state stays on the executing thread; it is not automatically propagated into executor worker threads.

[service/impl/SubscriptionServiceImpl.java:41](src/main/java/com/amdocs/telecom/service/impl/SubscriptionServiceImpl.java#L41); [util/Transactions.java:15](src/main/java/com/amdocs/telecom/util/Transactions.java#L15); [util/Transactions.java:46](src/main/java/com/amdocs/telecom/util/Transactions.java#L46).

#### 6. Callable returns data through Future

AccountMonitor creates a callable that finds overdue bills, submits it to the executor, then obtains its result through timed `Future.get`. The callable defines the calculation; the executor schedules it; the future connects the caller to its result or failure.

[scheduler/AccountMonitor.java:42](src/main/java/com/amdocs/telecom/scheduler/AccountMonitor.java#L42); [scheduler/AccountMonitor.java:70](src/main/java/com/amdocs/telecom/scheduler/AccountMonitor.java#L70); [scheduler/AccountMonitor.java:74](src/main/java/com/amdocs/telecom/scheduler/AccountMonitor.java#L74).

### Concepts to distinguish carefully

- `volatile` provides visibility; it does not make `counter++` atomic. The usage counter uses `synchronized` instead.
- A `final` reference cannot be reassigned; its referenced mutable object can still change.
- A `ConcurrentHashMap` supports concurrent operations; compound logic must use an atomic map operation or additional coordination. OTP verification uses `computeIfPresent`.
- A `ThreadLocal` connection coordinates calls on one thread. Each executor task that needs a transaction establishes its own transaction.
- A record is a compact data carrier. Its component references are final, but records are not automatically deeply immutable for arbitrary component types.
- A connection pool and a worker thread pool solve different problems: one reuses database connections; the other schedules computational/I/O tasks.
- The notification queue has a bound and normal shutdown drains it within a timeout. This does not guarantee delivery after a process crash.
- Scheduled billing checks the current billing month on a recurring hourly schedule. It is not a cron job configured to run only once a month.
- SQL monetary columns and billing arithmetic use decimal types, but some model/API fields remain `double`; do not claim a full end-to-end decimal migration.
