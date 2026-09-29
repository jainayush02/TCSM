# Java Modern Features Technical Evaluation
**Project:** Telecom Customer & Subscription Management System (TCSMS)  
**Architecture:** Multi-layered Telecom Console & Billing Platform (Java 17+ LTS, JDBC, MySQL/H2)  

---

## 1. Multithreading & Concurrency Architecture

### Why Use Multithreading in Telecom?
In a telecom environment, systems handle massive volumes of concurrent operations:
- Real-time **Call Detail Record (CDR) ingestion** and usage calculations.
- Periodic automated **monthly billing cycles** for thousands of subscribers.
- Non-blocking **payment receipts and SMS notifications**.
- Continuous **delinquency account scans** and service suspensions.

Running these synchronously on the main thread would freeze user interactions, cause UI lockups, and lead to database transaction timeouts. Multithreading offloads heavy jobs into dedicated worker thread pools, providing high throughput, responsiveness, and maximum CPU utilization.

---

### Implementation Details:

| File & Location | Exact Line Numbers | Mechanism / Class | Purpose & Why Used |
| :--- | :--- | :--- | :--- |
| [`UsageProcessor.java`](file:///c:/Users/Admin/OneDrive/Documents/amdocs/src/main/java/com/amdocs/telecom/scheduler/UsageProcessor.java) | **Lines 31, 43–63, 54–56, 89–93** | `ExecutorService`<br>`Executors.newFixedThreadPool(4)`<br>`synchronized (lock)` | **High-Throughput Batch Processing:** Splits bulk usage records into batches and executes them in parallel across 4 worker threads. Uses a `synchronized (lock)` block to ensure thread-safe increments of the processed record counter without race conditions. |
| [`PaymentNotificationService.java`](file:///c:/Users/Admin/OneDrive/Documents/amdocs/src/main/java/com/amdocs/telecom/scheduler/PaymentNotificationService.java) | **Lines 24, 30–33, 38–59, 71** | `BlockingQueue<Notification>`<br>`LinkedBlockingQueue(100)`<br>`workerPool (3 threads)` | **Producer-Consumer Pattern:** When a customer completes a bill payment, notification events are offered to an in-memory queue. 3 worker threads continuously poll the queue (2s timeout) and persist/dispatch notifications asynchronously. |
| [`BillingScheduler.java`](file:///c:/Users/Admin/OneDrive/Documents/amdocs/src/main/java/com/amdocs/telecom/scheduler/BillingScheduler.java) | **Lines 26, 31, 41–76** | `ScheduledExecutorService`<br>`Executors.newScheduledThreadPool(2)`<br>`scheduleAtFixedRate()` | **Automated Cron Scheduling:** Executes the recurring monthly billing cycle at fixed time intervals (`initialDelay`, `period`, `TimeUnit.SECONDS`) on a dedicated thread pool without blocking the console. |
| [`AccountMonitor.java`](file:///c:/Users/Admin/OneDrive/Documents/amdocs/src/main/java/com/amdocs/telecom/scheduler/AccountMonitor.java) | **Lines 35, 48–77, 79, 93** | `Callable<T>`<br>`Future<T>`<br>`future.get(15, TimeUnit.SECONDS)` | **Asynchronous Computation with Return Values:** Simultaneously launches a scan task (`Callable<List<Bill>>`) and a status update task (`Callable<Integer>`). Uses `Future.get()` with a 15-second timeout to prevent deadlocks. |
| [`DBConnection.java`](file:///c:/Users/Admin/OneDrive/Documents/amdocs/src/main/java/com/amdocs/telecom/util/DBConnection.java) | **Lines 33–38** | `public static synchronized DBConnection getInstance()` | **Thread-Safe Singleton:** Prevents race conditions where multiple background worker threads could attempt to instantiate duplicate database connection managers at the same time. |

---

## 2. Functional Interfaces

### What is a Functional Interface & Why Use It?
A **Functional Interface** in Java is an interface with **exactly one abstract method** (SAM - Single Abstract Method).
- **Why use it?** It allows executable code and business rules to be passed as method arguments, stored in variables, or returned from methods. It removes the necessity of writing bulky anonymous inner classes.

---

### Implementation Details:

| Functional Interface | Standard Package | Target File & Line Numbers | Code Snippet / Context | Why Used Here |
| :--- | :--- | :--- | :--- | :--- |
| **`Runnable`** | `java.lang` | [`BillingScheduler.java:42`](file:///c:/Users/Admin/OneDrive/Documents/amdocs/src/main/java/com/amdocs/telecom/scheduler/BillingScheduler.java#L42)<br>[`UsageProcessor.java:49`](file:///c:/Users/Admin/OneDrive/Documents/amdocs/src/main/java/com/amdocs/telecom/scheduler/UsageProcessor.java#L49)<br>[`PaymentNotificationService.java:41`](file:///c:/Users/Admin/OneDrive/Documents/amdocs/src/main/java/com/amdocs/telecom/scheduler/PaymentNotificationService.java#L41) | `Runnable billingTask = () -> { ... };` | Defines a background task (`void run()`) that executes asynchronously without returning a value or throwing checked exceptions. |
| **`Callable<V>`** | `java.util.concurrent` | [`AccountMonitor.java:48, 57, 111`](file:///c:/Users/Admin/OneDrive/Documents/amdocs/src/main/java/com/amdocs/telecom/scheduler/AccountMonitor.java#L48-L60) | `Callable<List<Bill>> overdueScanTask = () -> { ... return list; };` | Unlike `Runnable`, `Callable<V>` computes and returns a typed result (`V call()`) and can throw checked exceptions, enabling `Future<V>` result retrieval. |
| **`Predicate<T>`** | `java.util.function` | [`PlanServiceImpl.java:36, 45, 54`](file:///c:/Users/Admin/OneDrive/Documents/amdocs/src/main/java/com/amdocs/telecom/service/impl/PlanServiceImpl.java#L36-L55) | `Predicate<TelecomPlan> nameContains = p -> p.getPlanName().toLowerCase().contains(keyword.toLowerCase());` | Encapsulates boolean test criteria (`boolean test(T t)`) to dynamically filter tariff plans based on budget, keyword, or data quota. |
| **`Comparator<T>`** | `java.util` | [`PlanServiceImpl.java:63`](file:///c:/Users/Admin/OneDrive/Documents/amdocs/src/main/java/com/amdocs/telecom/service/impl/PlanServiceImpl.java#L63)<br>[`ReportServiceImpl.java:45`](file:///c:/Users/Admin/OneDrive/Documents/amdocs/src/main/java/com/amdocs/telecom/service/impl/ReportServiceImpl.java#L45) | `Comparator<TelecomPlan> priceComparator = Comparator.comparingDouble(TelecomPlan::getMonthlyRental);` | Supplies custom comparison algorithms (`int compare(T o1, T o2)`) for plan pricing and customer total consumption sorting. |
| **`Supplier<T>`** | `java.util.function` | [`PlanServiceImpl.java:78`](file:///c:/Users/Admin/OneDrive/Documents/amdocs/src/main/java/com/amdocs/telecom/service/impl/PlanServiceImpl.java#L78) | `opt.orElseThrow(() -> new TelecomException("Plan not found..."));` | Implements deferred lazy execution (`T get()`); the exception instance is only allocated if the target plan is absent from the database. |
| **`Consumer<T>`** | `java.util.function` | [`CustomerController.java:438, 854`](file:///c:/Users/Admin/OneDrive/Documents/amdocs/src/main/java/com/amdocs/telecom/controller/CustomerController.java#L438) | `results.forEach(p -> System.out.println("  " + p));` | Consumes each object in a list/stream (`void accept(T t)`) to render it to the console interface without returning values. |

---

## 3. Lambda Expressions (`->`)

### What is a Lambda Expression & Why Use It?
A **Lambda Expression** is an anonymous function that implements a functional interface without declaring a formal class.
- **Why use it?** Before Java 8, running a thread or defining a filter required 5 to 7 lines of anonymous inner class code:
  ```java
  // Legacy anonymous class (verbose):
  executor.submit(new Runnable() {
      @Override
      public void run() {
          usageDAO.saveBatch(batch);
      }
  });
  ```
  With lambdas, this is reduced to a clean, readable one-liner:
  ```java
  // Modern lambda expression:
  executor.submit(() -> usageDAO.saveBatch(batch));
  ```

---

### Implementation Details:

| File & Location | Line Numbers | Lambda Syntax Snippet | Target Functional Interface |
| :--- | :--- | :--- | :--- |
| [`UsageProcessor.java`](file:///c:/Users/Admin/OneDrive/Documents/amdocs/src/main/java/com/amdocs/telecom/scheduler/UsageProcessor.java#L49) | **Line 49** | `executor.submit((Runnable) () -> { ... });` | `Runnable` |
| [`PaymentNotificationService.java`](file:///c:/Users/Admin/OneDrive/Documents/amdocs/src/main/java/com/amdocs/telecom/scheduler/PaymentNotificationService.java#L41) | **Line 41** | `workerPool.submit((Runnable) () -> { ... });` | `Runnable` |
| [`AccountMonitor.java`](file:///c:/Users/Admin/OneDrive/Documents/amdocs/src/main/java/com/amdocs/telecom/scheduler/AccountMonitor.java#L48-L60) | **Lines 48, 57, 111** | `Callable<List<Bill>> task = () -> { ... return list; };` | `Callable<List<Bill>>` |
| [`PlanServiceImpl.java`](file:///c:/Users/Admin/OneDrive/Documents/amdocs/src/main/java/com/amdocs/telecom/service/impl/PlanServiceImpl.java#L36-L54) | **Lines 36, 45, 54** | `p -> p.getPlanName().toLowerCase().contains(keyword)`<br>`p -> p.getMonthlyRental() <= maxPrice`<br>`p -> p.getDataAllowanceGB() >= minDataGB` | `Predicate<TelecomPlan>` |
| [`CustomerController.java`](file:///c:/Users/Admin/OneDrive/Documents/amdocs/src/main/java/com/amdocs/telecom/controller/CustomerController.java#L491-L644) | **Lines 491, 532, 601, 641** | `b -> b.getSubscriptionId() == sub.getSubscriptionId()`<br>`s -> input.equalsIgnoreCase(s.getSubscriptionNumber())`<br>`b -> bChoice.equalsIgnoreCase(b.getBillNumber())` | `Predicate<T>` inside `.filter(...)` |
| [`CustomerController.java`](file:///c:/Users/Admin/OneDrive/Documents/amdocs/src/main/java/com/amdocs/telecom/controller/CustomerController.java#L854-L864) | **Lines 854, 864** | `u -> System.out.println("  " + u)`<br>`(type, qty) -> System.out.printf("  %-10s : %.1f%n", type, qty)` | `Consumer<T>` & `BiConsumer<K,V>` |
| [`ReportServiceImpl.java`](file:///c:/Users/Admin/OneDrive/Documents/amdocs/src/main/java/com/amdocs/telecom/service/impl/ReportServiceImpl.java#L45-L48) | **Lines 45–48** | `(c1, c2) -> Double.compare(customerTotals.getOrDefault(...), ...)` | `Comparator<Customer>` |

---

## 4. Stream API (`java.util.stream`)

### What is the Stream API & Why Use It?
The **Stream API** allows processing sequences of elements declaratively through functional pipelines.
- **Why use it?**
  1. **Eliminates Loop Boilerplate:** No index counters, no manual `new ArrayList<>()` creation, and no mutable tracking flags.
  2. **Expressive & Readable:** Expresses *what* you want to accomplish (filter, map, group, sort) rather than micro-managing step-by-step iterations.
  3. **SQL-like In-Memory Aggregations:** Computes groupings, averages, and statistical breakdowns in a single pipeline using `Collectors`.

---

### Implementation Details:

| File & Location | Line Numbers | Stream Pipeline Operations | Telecom Business Function |
| :--- | :--- | :--- | :--- |
| [`ReportServiceImpl.java`](file:///c:/Users/Admin/OneDrive/Documents/amdocs/src/main/java/com/amdocs/telecom/service/impl/ReportServiceImpl.java#L34-L51) | **Lines 34–51** | `allBills.stream().collect(Collectors.groupingBy(..., Collectors.summingDouble(...)))`<br>`allCustomers.stream().sorted(...).limit(10).collect(Collectors.toList())` | **Top Consumers Spend Analytics:** Groups all bills by customer, calculates total spend per subscriber, sorts descending, and returns the top 10 highest-spending customers. |
| [`ReportServiceImpl.java`](file:///c:/Users/Admin/OneDrive/Documents/amdocs/src/main/java/com/amdocs/telecom/service/impl/ReportServiceImpl.java#L63-L64) | **Lines 63–64** | `allCustomers.stream().collect(Collectors.groupingBy(Customer::getCity))` | **Demographic Grouping:** Groups customer records into geographic city clusters (`Map<String, List<Customer>>`). |
| [`ReportServiceImpl.java`](file:///c:/Users/Admin/OneDrive/Documents/amdocs/src/main/java/com/amdocs/telecom/service/impl/ReportServiceImpl.java#L76-L82) | **Lines 76–82** | `allBills.stream().filter(b -> "PAID".equals(...)).collect(Collectors.groupingBy(Bill::getBillingMonth, Collectors.summarizingDouble(Bill::getTotalAmount)))` | **Monthly Revenue Analytics:** Filters paid bills and generates statistical summaries (count, sum, min, max, average) per billing cycle. |
| [`ReportServiceImpl.java`](file:///c:/Users/Admin/OneDrive/Documents/amdocs/src/main/java/com/amdocs/telecom/service/impl/ReportServiceImpl.java#L95-L98) | **Lines 95–98** | `allBills.stream().filter(b -> "PAID".equals(...)).mapToDouble(Bill::getTotalAmount).sum()` | **Net Realized Cashflow:** Maps invoices to a primitive `DoubleStream` to calculate company-wide revenue totals. |
| [`PlanServiceImpl.java`](file:///c:/Users/Admin/OneDrive/Documents/amdocs/src/main/java/com/amdocs/telecom/service/impl/PlanServiceImpl.java#L37-L71) | **Lines 37–40, 46–49, 55–58, 69–71** | `plans.stream().filter(nameContains).collect(Collectors.toList())`<br>`plans.stream().sorted(priceComparator).collect(Collectors.toList())` | **Catalog Filtering & Sorting:** Searches plans by keyword, budget, and data allowance; sorts plans ascending or descending by rental price. |
| [`SubscriptionServiceImpl.java`](file:///c:/Users/Admin/OneDrive/Documents/amdocs/src/main/java/com/amdocs/telecom/service/impl/SubscriptionServiceImpl.java#L48-L49) | **Lines 48–49** | `existingSubs.stream().anyMatch(sub -> sub.getPlanId() == planId && "ACTIVE".equals(sub.getStatus()))` | **Duplicate Subscription Guard:** Uses short-circuit evaluation to prevent a customer from having duplicate active subscriptions of the same plan. |
| [`CustomerController.java`](file:///c:/Users/Admin/OneDrive/Documents/amdocs/src/main/java/com/amdocs/telecom/controller/CustomerController.java#L491-L644) | **Lines 491–494, 532–537, 601–604, 641–646** | `bills.stream().filter(...).findFirst().orElse(null)` | **Real-Time Entity Lookup:** Matches console user input against available subscriptions, bills, or mobile numbers. |
| [`CustomerController.java`](file:///c:/Users/Admin/OneDrive/Documents/amdocs/src/main/java/com/amdocs/telecom/controller/CustomerController.java#L854) | **Line 854** | `usage.stream().limit(20).forEach(u -> System.out.println("  " + u));` | **Pagination / Buffer Guard:** Limits display to top 20 CDR records, preventing console overflow. |
| [`AccountMonitor.java`](file:///c:/Users/Admin/OneDrive/Documents/amdocs/src/main/java/com/amdocs/telecom/scheduler/AccountMonitor.java#L51-L53) | **Lines 51–53** | `unpaid.stream().filter(bill -> bill.getDueDate() != null && bill.getDueDate().isBefore(LocalDate.now())).collect(Collectors.toList())` | **Date-Based Filtering:** Isolates overdue unpaid bills past their due dates. |

---

## 5. Architectural Summary

```
                      ┌────────────────────────────────────────────────────────┐
                      │              JAVA 8+ MODERN CONCEPTS                   │
                      └─────────────────────────┬──────────────────────────────┘
                                                │
             ┌──────────────────┬───────────────┴──────────────┬──────────────────┐
             ▼                  ▼                              ▼                  ▼
      MULTITHREADING     FUNCTIONAL INTERFACES          LAMBDA FUNCTIONS      STREAM API
    ─────────────────   ─────────────────────          ────────────────     ──────────────
    • ExecutorService   • Runnable                     • () -> { ... }      • .filter()
    • ScheduledExecutor • Callable<T>                  • p -> p.getPrice()  • .mapToDouble()
    • ThreadPool (2-4)  • Predicate<T>                 • (c1, c2) -> ...    • .sorted()
    • BlockingQueue     • Comparator<T>                                     • .collect(groupingBy())
    • synchronized lock • Supplier<T>                                       • .anyMatch() / .limit()
                        • Consumer<T>                                       • .forEach()
```
