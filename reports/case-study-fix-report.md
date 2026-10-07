# Case-study compliance and verification

Reviewed on 7 October 2026 against all 17 pages of `1786363275685-Amdocs PreBorading Batch Case study 1.pdf` and the current source tree.

## Verdict

The project covers the required business workflows, Java features, JDBC operations, database structure, and background processes for the console case study. Two remaining console gaps found during this audit were corrected: bank transfer is selectable at checkout, and the administrator reports display highest-usage customers and customers with unpaid bills.

This assessment concerns the case-study console application. Payment gateways and OTP delivery are simulations. The runtime is Java 17; the required Java 8 APIs and language features are demonstrated, but the project does not target a Java 8 runtime. Optional views, stored procedures, database functions and triggers are not required by the PDF and are not claimed as implemented.

## Verification evidence

| Check | Result | Scope |
|---|---|---|
| Complete service/database suite on H2 | 64/64 passed | Fresh complete rerun, including all audit-specific scenarios |
| Complete service/database suite on MySQL | 64/64 passed | Fresh complete rerun, including payments, concurrency, migrations and all services |
| Checks after console presentation changes | 6/6 passed on H2 | Plan exploration, report content, billing scheduler and manual/scheduled account monitoring |
| Checks after background output fix | 5/5 passed on H2 and 5/5 on MySQL | Scheduled billing, overdue suspension and notification persistence assert no console output; manual billing and monitoring still work |
| Registration during scheduled jobs | 3/3 passed | Real application prompt remained quiet for 65 seconds, both scheduled jobs finished in the log, cancellation and shutdown succeeded |
| Customer console | 26/26 passed | Login, menus, plan exploration, profile, usage, add-ons, bank transfer, invalid payment mode, duplicate payment and logout |
| Administrator console | 25/25 passed | Management, usage/reports, suspension/reactivation, lock after three failures, blocked login, OTP recovery, previous login timestamp and logout |
| Complete admin dashboard review | 52/52 passed | All 23 actions, complaint handling and exports, live activity, password changes and recovery; missing plan ID correctly rejected after controller fix |
| Existing `telecom_db` structure | 17 tables verified | Live JDBC metadata; every table has a primary key, 16 foreign-key relationships, unique constraints, indexes and relevant status/timestamp columns |
| Current `run.bat` | Passed | Sources compiled, existing MySQL initialized, main menu opened, application exited cleanly |
| Named classes, packages and model fields | All present | Checked against the PDF class lists and every listed field in the eight detailed model specifications |

Service scenarios use isolated databases. The MySQL runner creates a uniquely named `telecom_review_*` database, validates its name before resetting fixtures, and drops it after testing. Console scenarios use an isolated H2 database. The schema inspection of `telecom_db` is read-only. The application launch applies normal compatible startup migrations; no customer, bill or payment deletion was performed.

The complete suite was rerun on both databases: all 64 scenarios passed on each. The console scripts verified 51 scenarios in total.

Scheduled billing, account monitoring and notification workers now log to `logs/tcsms-0.log` instead of printing into input prompts. Logs rotate across three files of about 1 MB each. Manual admin billing and monitoring actions still display their results. Both console scripts were rerun after this change, passing all 51 checks.

## PDF requirements and evidence

| PDF section | Required items | Current evidence and result |
|---|---|---|
| 1. Business scenario | Customers, profiles, mobile numbers, SIMs, plans, subscriptions, usage, billing, payments, complaints, notifications; customer/admin roles | Models, 17 related tables, role-specific controllers and service implementations cover each domain. Service and console checks pass. |
| 2. Application login | Username/password, CAPTCHA, three failures, temporary lock, OTP recovery, history, previous timestamp, logout | `AuthenticationServiceImpl`, `AdminController`, `LoginSecurity`, `CaptchaGenerator`, `OTPService`, `PasswordUtil` and `login_history`. Customer lock expiry/reset and role-separated history are tested; admin console tests exercise three failures, locked login and OTP recovery. Registration/recovery are available through the corresponding portal menus. The illustrative PDF menu is organized into portals rather than copied verbatim. |
| 3. Registration | All 14 listed customer fields; unique email/mobile; complexity; minimum age; mandatory fields | `Customer`, `CustomerRegistrationDTO`, `CustomerServiceImpl` and `customers`. IDs/numbers/date/status/hash are assigned by the application/database. Required input, uniqueness and 18-year minimum age checks pass. BCrypt passwords and configured complexity checks are implemented. |
| 4. Mobile and SIM | Multiple connections, all nine subscription fields, physical SIM/eSIM, prepaid/postpaid, status | `MobileSubscription`, `SIMCard`, enums, joined DAO reads and SIM inventory. Subscription has independent plan/SIM foreign keys; mobile number and SIM allocation are unique. Provisioning, ownership and rollback checks pass. SIM details are normalized in `sim_cards` and joined into subscription models. |
| 5. Plan catalogue | All 11 plan fields; administrator maintains plans; search, price/data filters, price sorting, comparison; Lambda/Comparator/Stream | `TelecomPlan`, `PlanDAOImpl`, `PlanServiceImpl`, admin plan actions and customer Explore Plans. Every exploration action is exercised through the console; numeric/status validation and missing-plan checks are implemented. |
| 6. Subscriptions | Subscribe, upgrade/downgrade, permitted prepaid/postpaid change, add-on activation/deactivation, all seven history fields; inactive/duplicate/restriction rules | `SubscriptionServiceImpl`, `AdministrationServiceImpl`, `AddOnServiceImpl`, `subscription_history` and plan policy columns. Tests cover duplicate active plans, inactive plans, ownership, history, minimum change period, type-change permission, add-on lifecycle and transaction rollback. |
| 7. Usage | All seven fields; VOICE/SMS/DATA/ROAMING; total data/voice/SMS, monthly/type usage, highest-consuming customers using streams | `UsageRecord`, `UsageServiceImpl`, `UsageUnits`, `UsageDAOImpl`, `ReportServiceImpl`. Tests cover month boundaries, GB/MB normalization, summary totals, invalid quantities, typed history and usage ranking independent of bill value. Highest DATA consumers are displayed in admin reports. |
| 8. Billing | All 11 bill fields, monthly bills, rental/usage/tax/discount/total/due date/status | `Bill`, `BillingServiceImpl`, `BillingDAOImpl`, scheduler and invoice screens. Tests check amount calculation, monthly duplicate rejection, month validation, usage reconciliation, prorated changes, credits and paid-bill adjustments. Historical duplicate invoices retain distinct legacy keys and payment references. |
| 9. Payments | All eight fields; UPI/CARD/NET_BANKING/BANK_TRANSFER; bill/amount validation, payment insert, bill status, audit, commit; rollback; no duplicate payment | `Payment`, strategies/factory, `PaymentServiceImpl` and payment/billing/audit DAOs. All four modes pass service checks. Invalid amount/owner/mode leaves no writes; forced audit failure rolls back payment and bill; simultaneous requests create exactly one payment. Bank transfer, invalid checkout mode and duplicate payment are also tested through the customer console. |
| 10. Administrator | Manage customers/plans/SIMs/subscriptions, view usage/payments, generate bills, suspend/reactivate, complaints and reports | `AdminController` and `AdministrationServiceImpl`. Console and service tests exercise profile changes, SIM insertion, policy settings, monthly usage, billing, overdue suspension, reactivation and administrative access checks. Read/report routes have source and test evidence. |
| 11. Complaints | All ten fields; BILLING/NETWORK/SIM/PLAN/PAYMENT/OTHER | `Complaint`, `ComplaintCategory`, `ComplaintServiceImpl`, `ComplaintDAOImpl` and complaint screens. Registration, lifecycle validation, resolution, notification and aggregate analytics are checked. Subscription link is optional; demo connection-specific complaints use a real foreign key. |
| 12. Java 8 reports | Highest usage, city grouping, price range, most subscribed plans, monthly revenue, type usage, unpaid customers, monthly ARPU | `PlanServiceImpl` and `ReportServiceImpl`; service tests check each calculation, including normalization and multiple-month ARPU. Admin reports now expose the previously hidden usage ranking and unpaid-customer list. |
| 12. Java 8 constructs | Predicate, Function, Consumer, Supplier, Comparator, Optional, groupingBy, summarizingDouble | `PlanServiceImpl` uses all four functional interfaces, Comparator and Optional; `ReportServiceImpl` uses groupingBy and summarizingDouble. Lambdas/method references are used throughout. `SubscriptionService` demonstrates default and static interface methods. |
| 13. Background work | BillingScheduler, PaymentNotificationService, UsageProcessor, AccountMonitor; executor/scheduled executor, Runnable, Callable, Future, synchronization | All four classes are present and used. Scheduled monthly billing and scheduled overdue suspension run in tests. Notification queue drains on shutdown; usage batches persist/count correctly. Futures/Callables run account scans; synchronized usage counters and BlockingQueue notification workers are present. |
| 14. Suggested classes | All listed models, services, DAOs, security/database/report helpers | Automated inventory confirmed every named class/interface and required package is present. The additional add-on and management classes implement the subscription/admin requirements. |

## Common technical requirements

| Requirement | Evidence |
|---|---|
| Normalized relational database | Separate customer, plan, SIM, subscription, usage, invoice, payment, complaint and history tables. `subscription_add_ons` is a junction table; customer/plan/SIM data is joined rather than duplicated. |
| Primary/foreign keys, unique constraints, NOT NULL, indexes, statuses, timestamps | `schema.sql`, `SchemaMigration` and read-only metadata from the existing MySQL database. All 17 primary keys are present; customer email/mobile/username, SIM identifiers, subscription identifiers, invoice keys and transaction references have uniqueness protection. Required columns have NOT NULL constraints. |
| SELECT, INSERT, UPDATE, DELETE | DAO queries and writes; plan deletion and security-state cleanup demonstrate DELETE. Customer/security/payment/provisioning checks exercise CRUD. |
| JOIN, LEFT JOIN, GROUP BY, HAVING, ORDER BY, subquery, CASE, aggregates, date functions | Subscription/bill/complaint joined reads; complaint analytics uses GROUP BY/HAVING/conditional SUM/COUNT; SIM selection, plan type change and login history use subqueries; migrations use CASE; seed/updates use CURRENT_TIMESTAMP and date predicates. Analytics checks execute the relevant SQL on both databases. |
| Java classes/objects, encapsulation, inheritance, abstraction, polymorphism, interfaces, enums, generics, collections | Private model fields and accessors; exception inheritance; DAO/service/strategy interfaces with concrete implementations; enum-based modes/types; typed lists/maps, generic transaction work and runtime-selected payment strategies. Sources compile for release 17. |
| Java 8 language/API features | Lambdas, functional interfaces, stream operations, Optional, method references, default/static interface methods. Sources and calculation tests demonstrate these directly. |
| JDBC connection, prepared statements, ResultSet, batches, transactions, commit/rollback/savepoint | DAOs and pooled connections; bulk usage batches; payment rollback/concurrency tests; subscription transaction failure tests; nested transaction savepoint rollback check. |
| Thread/Runnable, ExecutorService, Callable/Future, scheduled executor, synchronization, BlockingQueue | Four background classes, execution/shutdown tests, synchronized usage counter and notification queue. |
| Security | BCrypt, CAPTCHA, role-scoped expiring single-use OTPs with three retry limit, persisted temporary account locks, role-specific login history, customer ownership checks, administrator checks and bound JDBC parameters. |
| CSV export, text reports, configuration, logging | `ReportGenerator` customer/revenue/complaint CSVs and text invoices; CSV/text content assertions; `db.properties` plus local environment configuration; java.util.logging in services/workers. |
| At least three design patterns | DAO interfaces/implementations, Factory (`DAOFactory`, `ServiceFactory`, `PaymentStrategyFactory`), Strategy (`PaymentStrategy` implementations) and Singleton (`DBConnection` holder). Four demonstrated patterns. |

## Running and navigation

```powershell
.\run.bat
powershell -NoProfile -ExecutionPolicy Bypass -File tests/review/run-review.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File tests/review/run-review.ps1 -MySql
python tests/review/customer-console-review.py
python tests/review/admin-console-review.py
python tests/review/background-console-review.py
```

Console tests prompt for the isolated demo credentials unless supplied through their `REVIEW_*` environment variables. Tests do not change credentials in the normal database.

- Customer: **1. My Profile**, **2. Explore Plans**, **9. Usage**, **8. Make a Payment**; checkout option **4. Bank Transfer**.
- Administrator: **8. Set Subscription Status** supports `ACTIVE` reactivation; **17. Revenue Reports** includes usage ranking and unpaid customers; **23. Live Activity Monitor** watches shared audit activity.
- Both dashboards use **0. Logout**. Main menu **3. Exit** shuts down workers and the connection pool.

Fresh full test results are written under `target/independent-review` or `target/independent-review-mysql`. Filtered runs overwrite the corresponding result file with only that subset. Full-suite counts above refer to the complete final reruns. The subsequent UI changes only affect displayed text and formatting; both console scripts and six related service/scheduler checks were rerun afterward.

## Console presentation review

Both dashboards use aligned two-column menus to reduce scrolling. All original action numbers and workflows are retained. Add-ons display names and monthly prices instead of Java record representations. Profile registration dates use a readable date/time format. Console prices use `Rs` and status messages use plain text, avoiding unsupported currency/emoji glyphs on Windows.
