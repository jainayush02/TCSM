# Concepts Understanding - TCSMS Interview Guide

This guide explains the Java concepts used in the Telecom Customer and
Subscription Management System (TCSMS), why they are used, and where to show
them during an interview.

> The source references use one-based line numbers from the current Java files.
> Recheck line numbers after future source changes.

## PDF-friendly layout

This document is intentionally formatted for PDF conversion:

- Code examples are wrapped into short lines to avoid horizontal overflow.
- Major sections use page-break markers where supported by the converter.
- Tables are kept compact so they remain readable on an A4 page.
- Use a converter with code wrapping enabled. For Pandoc, use:

```text
pandoc "conceps understanding.md" -o "conceps understanding.pdf" ^
  --pdf-engine=xelatex -V geometry:margin=0.75in
```

If the converter does not support page-break markers, the document will still
remain readable as normal Markdown.

<style>
@media print {
  h1, h2 { break-before: page; }
  h1:first-child { break-before: auto; }
  pre { white-space: pre-wrap; overflow-wrap: anywhere; font-size: 9pt; }
  table { font-size: 9pt; }
}
</style>

<!-- pagebreak -->

## 1. What is multithreading?

### Interview answer

Multithreading is the execution of multiple independent tasks concurrently
inside one process. In Java, an executor manages reusable worker threads, so
the application does not need to create a new raw `Thread` for every task.
When threads share data, synchronization or thread-safe collections are
required.

### How this project uses it

- `UsageProcessor` processes usage batches using four worker threads.
- `PaymentNotificationService` uses three workers to persist queued
  notifications.
- `BillingScheduler` and `AccountMonitor` run recurring background work.
- `BlockingQueue` safely transfers notifications between producers and workers.
- `synchronized` protects the shared processed-record count.
- `shutdown()` and `awaitTermination()` stop workers cleanly.

The benefit is that scheduled and batch work does not block the interactive
customer/admin console.

## 2. What is file handling?

### Interview answer

File handling means creating, reading, writing and closing files or
directories. Java provides streams and writers for file I/O. I use
try-with-resources so resources are closed automatically even if an exception
occurs.

### How this project uses it

`ReportGenerator` creates the `reports` directory and writes customer/revenue
CSV files and text invoices as UTF-8. `ApplicationLogging` writes background
activity to rotating log files, so scheduled work does not interrupt console
input.

The project uses:

- `BufferedWriter` for efficient character output.
- `FileOutputStream` as the file destination.
- `OutputStreamWriter` with UTF-8 encoding.
- Try-with-resources for automatic cleanup.

## 3. What is a functional interface?

### Interview answer

A functional interface has exactly one abstract method. It is also called a
SAM interface, meaning Single Abstract Method. It can be implemented with a
lambda expression or method reference. `@FunctionalInterface` lets the
compiler verify that the interface remains functional.

### How this project uses it

`Transactions.Work<T>` represents a database operation that can return a value
and throw an exception. `Transactions.run` controls the transaction,
commit and rollback, while the caller supplies the business operation as a
lambda. This separates transaction management from business logic.

Other functional interfaces used include:

- `Predicate<T>` for plan filtering.
- `Function<T, R>` for creating plan summaries.
- `Consumer<T>` for audit messages.
- `Callable<T>` for tasks that return results.
- `Runnable` for tasks with no return value.

## 4. What is the Stream API?

### Interview answer

The Stream API processes a sequence of data through a pipeline. Operations
such as `filter`, `map` and `sorted` are intermediate operations. Operations
such as `collect`, `sum` and `forEach` are terminal operations that execute the
pipeline.

A stream does not store data and normally does not change the original
collection. Also, a normal stream is sequential; it is not automatically
multithreaded.

### How this project uses it

- Plan searches filter plans and collect matching results.
- Usage history is filtered by month, grouped by usage type and summed.
- Active subscriptions are filtered using a method reference.

The project uses explicit executors for actual concurrency instead of using
parallel streams for every operation.

## 5. What is a lambda expression?

### Interview answer

A lambda expression is a concise implementation of a functional interface. Its
basic syntax is:

```java
parameters -> expression
parameters -> { statements; }
```

A lambda passes behavior as a value without requiring a separate implementation
class.

### How this project uses it

- A plan-search lambda checks whether a plan name contains a keyword.
- A transaction lambda supplies the database operation to `Transactions.run`.
- Scheduler and executor lambdas define work to run later.
- `Function` and `Consumer` lambdas create and log plan comparison summaries.

## 6. How these concepts work together

When an administrator processes usage:

1. `UsageProcessor` divides records into batches.
2. A lambda defines each batch task.
3. The task is submitted as a `Runnable` to an `ExecutorService`.
4. Multiple worker threads process batches concurrently.
5. A `synchronized` block updates the shared total safely.
6. `Transactions.run` receives a functional-interface callback and commits or
   rolls back the database operation.
7. Logging records the outcome in a file without disturbing the console.

This is a strong interview example because it explains the concepts in one
real workflow.

## 7. OOP concepts used in the project

| Concept | Project example |
|---|---|
| Encapsulation | `Customer` keeps fields private and exposes controlled access through methods. |
| Abstraction | DAO, service and payment-strategy interfaces define contracts. |
| Inheritance | Custom exceptions extend `TelecomException`. |
| Polymorphism | A `PaymentStrategy` reference can hold UPI, card or net-banking behavior. |
| Composition | Services contain DAOs, queues and executors as collaborators. |
| Method overriding | Concrete DAO/service classes implement interface methods. |
| Factory pattern | `PaymentStrategyFactory` selects a strategy for a payment mode. |

## 8. Exact source files, line numbers and code

Use this section when the interviewer asks, “Show me where you used it.”
Each entry gives the filename, exact line number and the actual code statement.
Long source statements are visually wrapped below for PDF readability; the
line numbers still refer to the original Java source.

<!-- pagebreak -->

### Multithreading

**File:** `telecom/scheduler/UsageProcessor.java`  
**Line 26:**

```java
this.executor = Executors.newFixedThreadPool(4);
```

This creates four reusable worker threads.

**File:** `telecom/scheduler/UsageProcessor.java`  
**Line 47:**

```java
tasks.add(executor.submit((Runnable) () -> {
```

This submits a lambda task to the executor.

**File:** `telecom/scheduler/UsageProcessor.java`  
**Line 57:**

```java
synchronized (lock) {
```

This protects the shared result count from concurrent updates.

**File:** `telecom/scheduler/PaymentNotificationService.java`  
**Lines 27-28:**

```java
this.notificationQueue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);
this.workerPool = Executors.newFixedThreadPool(3);
```

This creates a thread-safe queue and three notification workers.

**File:** `telecom/scheduler/AccountMonitor.java`  
**Lines 22 and 25:**

```java
private final ScheduledExecutorService scheduler =
        Executors.newSingleThreadScheduledExecutor();

scheduler.scheduleWithFixedDelay(
        () -> {
            scanOverdueAccounts(false);
            suspendDelinquentAccounts(30, false);
        },
        initialDelay,
        period,
        TimeUnit.SECONDS);
```

This runs overdue-account monitoring repeatedly in the background.

**File:** `telecom/scheduler/BillingScheduler.java`  
**Lines 26 and 34:**

```java
this.scheduler = Executors.newScheduledThreadPool(2);
scheduler.scheduleAtFixedRate(billingTask, initialDelay, period, TimeUnit.SECONDS);
```

This schedules recurring billing work.

**File:** `telecom/main/MainApplication.java`  
**Line 40:**

```java
Runtime.getRuntime().addShutdownHook(new Thread(MainApplication::stopServices));
```

This registers a thread that stops services when the JVM shuts down.

### File handling

**File:** `telecom/report/ReportGenerator.java`  
**Lines 35-36:**

```java
try (BufferedWriter writer = new BufferedWriter(
        new OutputStreamWriter(new FileOutputStream(targetPath), StandardCharsets.UTF_8))) {
```

This opens a UTF-8 file writer and automatically closes it with
try-with-resources.

**File:** `telecom/report/ReportGenerator.java`  
**Line 28:**

```java
File dir = new File(DEFAULT_REPORT_DIR);
```

This represents the reports directory before it is created or checked.

**File:** `telecom/report/ReportGenerator.java`  
**Line 36:**

```java
new OutputStreamWriter(new FileOutputStream(targetPath), StandardCharsets.UTF_8)
```

This writes generated CSV and invoice text to a file using UTF-8.

**File:** `telecom/util/ApplicationLogging.java`  
**Line 22:**

```java
FileHandler handler = new FileHandler("logs/tcsms-%g.log", 1_000_000, 3, true);
```

This writes rotating application logs to files.

### Functional interface

**File:** `telecom/util/Transactions.java`  
**Lines 14-15:**

```java
@FunctionalInterface
public interface Work<T> { T run() throws Exception; }
```

This is a custom functional interface with one abstract method.

**File:** `telecom/util/Transactions.java`  
**Line 40:**

```java
public static <T> T run(Work<T> work) throws TelecomException {
```

This accepts a `Work<T>` callback and runs it inside transaction management.

### Lambda expression

**File:** `telecom/service/impl/PlanServiceImpl.java`  
**Line 40:**

```java
Predicate<TelecomPlan> nameContains =
        p -> p.getPlanName()
                .toLowerCase()
                .contains(keyword.toLowerCase());
```

This lambda checks whether a plan name contains the search keyword.

**File:** `telecom/service/impl/PlanServiceImpl.java`  
**Lines 106-107:**

```java
Function<TelecomPlan, String> summaryFunc =
        p -> String.format(
                "%s (₹%.2f, %dGB)",
                p.getPlanName(),
                p.getMonthlyRental(),
                p.getDataAllowanceGB());

Consumer<String> auditConsumer =
        msg -> Logger.getLogger(PlanServiceImpl.class.getName()).info(msg);
```

These lambdas create a plan summary and consume an audit message.

**File:** `telecom/util/Transactions.java`  
**Line 31:**

```java
(proxy, method, args) -> {
```

This lambda implements the invocation handler used by a dynamic proxy.

### Stream API

**File:** `telecom/service/impl/PlanServiceImpl.java`  
**Lines 41-43:**

```java
return plans.stream()
        .filter(nameContains)
        .collect(Collectors.toList());
```

This stream filters plans and collects the matching plans into a list.

**File:** `telecom/service/UsageService.java`  
**Lines 10-13:**

```java
return getCustomerUsageHistory(customerId).stream()
        .filter(r -> YearMonth.from(r.getUsageDate()).equals(month))
        .collect(Collectors.groupingBy(
                r -> r.getUsageType().name(),
                LinkedHashMap::new,
                Collectors.summingDouble(r ->
                        UsageUnits.normalize(
                                r.getUsageType(),
                                r.getQuantity(),
                                r.getUnit()))));
```

This filters usage by month, groups it by usage type and sums normalized
quantities.

**File:** `telecom/service/SubscriptionService.java`  
**Line 11:**

```java
default List<MobileSubscription> getActiveSubscriptions(int customerId) {
    return getCustomerSubscriptions(customerId)
            .stream()
            .filter(SubscriptionService::isActive)
            .collect(Collectors.toList());
}
```

This uses a method reference and stream filtering to return active
subscriptions.

### OOP concepts

**File:** `telecom/model/Customer.java`  
**Lines 6-10:**

```java
private String previousLogin;
private int customerId;
private String customerNumber;
private String firstName;
```

Private fields demonstrate encapsulation.

**File:** `telecom/strategy/PaymentStrategy.java`  
**Lines 5-7:**

```java
public interface PaymentStrategy {
    boolean validateAndProcess(double amount, String customerAccount) throws TelecomException;
    String getChannelName();
}
```

This interface demonstrates abstraction and enables polymorphism.

**File:** `telecom/strategy/PaymentStrategyFactory.java`  
**Lines 14-22:**

```java
case UPI:
    return new UpiPaymentStrategy();
case CARD:
    return new CardPaymentStrategy();
```

The factory selects a concrete payment strategy at runtime.

**File:** `telecom/exception/AuthenticationException.java`  
**Line 3:**

```java
public class AuthenticationException extends TelecomException {
```

This demonstrates inheritance through a specialized exception.

<!-- pagebreak -->

## 9. Other Java interview concepts

### 9.1 What is an enum?

An enum represents a fixed set of named constants. It is safer than passing
arbitrary strings when only specific values are valid.

**File:** `telecom/model/PaymentMode.java`  
**Lines 3-8:**

```java
public enum PaymentMode {
    UPI,
    CARD,
    NET_BANKING,
    BANK_TRANSFER
}
```

TCSMS uses this enum to restrict payment modes to supported values.

### 9.2 What is a record?

A record is a compact Java data carrier. Java automatically provides a
constructor, accessors, `equals`, `hashCode` and `toString`.

**File:** `telecom/model/AddOn.java`  
**Line 5:**

```java
public record AddOn(int id, String code, String name, BigDecimal monthlyPrice, String status) {}
```

TCSMS uses a record for add-on catalogue data that mainly needs to carry values.

### 9.3 What is exception handling?

Exception handling manages abnormal situations without crashing the whole
application. Java uses `try`, `catch`, `finally`, `throw` and `throws`.

**File:** `telecom/service/impl/PlanServiceImpl.java`  
**Lines 92-100:**

```java
@Override
public TelecomPlan getPlanById(int planId) throws TelecomException {
    Supplier<TelecomException> notFoundSupplier =
            () -> new TelecomException("Plan not found with ID: " + planId);
    try {
        Optional<TelecomPlan> opt = planDAO.findById(planId);
        return opt.orElseThrow(notFoundSupplier);
    } catch (SQLException e) {
        throw new TelecomException("Database error: " + e.getMessage());
    }
}
```

The database exception is caught and converted into an application-level
exception with the original cause preserved.

### 9.4 What is a checked exception?

A checked exception is verified by the compiler. The method must catch it or
declare it with `throws`. It is useful for failures that callers are expected
to handle, such as database or business-operation failures.

**File:** `telecom/exception/TelecomException.java`  
**Line 3:**

```java
public class TelecomException extends Exception {
```

`TelecomException` is a checked exception because it extends `Exception`.

### 9.5 What are generics?

Generics provide compile-time type safety and allow reusable classes and
methods without unnecessary casts.

**File:** `telecom/util/Transactions.java`  
**Line 15:**

```java
public interface Work<T> { T run() throws Exception; }
```

`T` allows the same transaction helper to return different result types.

**File:** `telecom/util/Transactions.java`  
**Line 40:**

```java
public static <T> T run(Work<T> work) throws TelecomException {
```

The method returns the same generic type produced by the supplied work.

### 9.6 What is `Optional`?

`Optional<T>` represents a value that may or may not exist. It makes a
not-found result explicit and helps avoid accidental `null` dereferencing.

**File:** `telecom/service/impl/PlanServiceImpl.java`  
**Lines 92, 95-96:**

```java
Optional<TelecomPlan> opt = planDAO.findById(planId);
return opt.orElseThrow(notFoundSupplier);
```

The service returns the plan when present and throws a meaningful exception
when it is absent.

### 9.7 What is try-with-resources?

Try-with-resources automatically closes objects that implement
`AutoCloseable`, such as files, database connections and prepared statements.

**File:** `telecom/report/ReportGenerator.java`  
**Lines 35-36:**

```java
try (BufferedWriter writer = new BufferedWriter(
        new OutputStreamWriter(new FileOutputStream(targetPath), StandardCharsets.UTF_8))) {
```

The writer is closed automatically after the report is written, including when
an exception occurs.

### 9.8 What is `static`?

`static` members belong to the class rather than to an individual object.
They are suitable for shared constants and utility methods.

**File:** `telecom/report/ReportGenerator.java`  
**Line 19:**

```java
private static final String DEFAULT_REPORT_DIR = "reports";
```

This constant is shared by all `ReportGenerator` objects and cannot be
reassigned.

### 9.9 What is `final`?

`final` prevents reassignment of a variable, overriding of a method, or
extension of a class, depending on where it is used. A final object reference
cannot point to another object, although the object itself may still be
mutable.

**File:** `telecom/scheduler/PaymentNotificationService.java`  
**Line 22:**

```java
private final BlockingQueue<Notification> notificationQueue;
```

The service keeps the same queue reference throughout its lifecycle.

### 9.10 What is constructor overloading?

Constructor overloading means defining multiple constructors with different
parameter lists. It provides different ways to initialize an object.

**File:** `telecom/exception/TelecomException.java`  
**Lines 7-12:**

```java
public TelecomException(String message) {
    super(message);
}

public TelecomException(String message, Throwable cause) {
    super(message, cause);
}
```

The exception can be created with only a message or with a message and the
original cause.

### 9.11 What is method overriding?

Method overriding occurs when a child class or implementation supplies its own
version of a method declared by a parent class or interface. It enables runtime
polymorphism.

**File:** `telecom/service/impl/PlanServiceImpl.java`  
**Lines 36-37:**

```java
@Override
public TelecomPlan getPlanById(int planId) throws TelecomException {
```

`PlanServiceImpl` provides the concrete implementation of its service
contract.

### 9.12 What is method overloading?

Method overloading uses the same method name with different parameter lists.
The compiler selects the correct method based on the arguments.

**File:** `telecom/report/ReportGenerator.java`  
**Lines 81-90:**

```java
public String generateBillInvoiceText(Bill bill, Customer customer, String fileName) throws Exception {
    return generateBillInvoiceText(bill, customer, null, null, null, fileName);
}

public String generateBillInvoiceText(
        Bill bill,
        Customer customer,
        String planName,
        String paymentMode,
        String txnRef,
        String fileName) throws Exception {
```

Both methods generate a text invoice, but one accepts optional payment details.

### 9.13 What is a collection?

A collection stores and manages groups of objects. Common interfaces include
`List`, `Set` and `Map`.

**File:** `telecom/service/UsageService.java`  
**Lines 10-13:**

```java
return getCustomerUsageHistory(customerId).stream()
        .filter(r -> YearMonth.from(r.getUsageDate()).equals(month))
        .collect(Collectors.groupingBy(
                r -> r.getUsageType().name(),
                LinkedHashMap::new,
                Collectors.summingDouble(r ->
                        UsageUnits.normalize(
                                r.getUsageType(),
                                r.getQuantity(),
                                r.getUnit()))));
```

This uses a list of usage records and collects the result into a
`LinkedHashMap` grouped by usage type.

### 9.14 What is dependency injection?

Dependency injection means providing an object with the collaborators it needs
instead of making it find or create every dependency internally. It improves
testability and reduces coupling.

**File:** `telecom/controller/CustomerController.java`  
**Lines 24, 36 and 38:**

```java
private final AuthenticationService authService;

public CustomerController(Scanner scanner) {
    this.authService = new AuthenticationServiceImpl();
```

The controller stores collaborators as fields and receives its console input
through its constructor. In a larger production version, the services and DAOs
could also be passed through constructors by a dependency-injection container.

### 9.15 What is immutability?

An immutable object cannot be changed after it is created. Immutability makes
objects easier to share safely between threads and reduces unexpected state
changes.

**File:** `telecom/model/AddOn.java`  
**Line 5:**

```java
public record AddOn(int id, String code, String name, BigDecimal monthlyPrice, String status) {}
```

The record gives the add-on object final components and no setters, making the
data carrier effectively immutable when its component values are immutable.

### 9.16 What is JDBC `PreparedStatement`?

`PreparedStatement` represents a parameterized SQL statement. It improves
security by separating SQL structure from user-provided values and can be
reused for repeated execution.

**File:** `telecom/scheduler/AccountMonitor.java`  
**Lines 57-59:**

```java
try (Connection c = DBConnection.getInstance().getConnection();
     PreparedStatement p = c.prepareStatement(
             "UPDATE bills SET bill_status='OVERDUE' "
                     + "WHERE bill_id=? AND bill_status='UNPAID'")) {
```

The `?` placeholder is filled with a typed value before execution.

<!-- pagebreak -->

## 10. Complete OOP interview concepts

### 10.1 What is a class and what is an object?

A class is a blueprint that defines state and behavior. An object is a
runtime instance of that class.

**File:** `telecom/report/ReportGenerator.java`  
**Lines 17 and 21:**

```java
public class ReportGenerator {

    public ReportGenerator() {
```

`ReportGenerator` is the class, and every object created with `new
ReportGenerator()` is an instance of that class.

### 10.2 What is encapsulation?

Encapsulation means keeping data private inside a class and exposing controlled
operations through methods. It protects an object's state from uncontrolled
changes.

**File:** `telecom/model/Customer.java`  
**Lines 6-10:**

```java
private String previousLogin;
private int customerId;
private String customerNumber;
private String firstName;
```

Customer data is private and is accessed through getters and setters instead of
being publicly exposed.

### 10.3 What is abstraction?

Abstraction exposes what an object can do while hiding how it does it. In Java,
interfaces are a common way to create abstraction.

**File:** `telecom/strategy/PaymentStrategy.java`  
**Lines 5-7:**

```java
public interface PaymentStrategy {
    boolean validateAndProcess(double amount, String customerAccount) throws TelecomException;
    String getChannelName();
}
```

Payment services depend on this contract, not on the internal details of each
payment channel.

### 10.4 What is inheritance?

Inheritance allows a child class to reuse or specialize members of a parent
class. It represents an “is-a” relationship.

**File:** `telecom/exception/AuthenticationException.java`  
**Line 3:**

```java
public class AuthenticationException extends TelecomException {
```

`AuthenticationException` is a specialized kind of `TelecomException`.

### 10.5 What are the types of inheritance?

In Java, inheritance can be single, multilevel and hierarchical through
classes. Multiple inheritance of classes is not supported because it can create
the diamond problem. Similar multiple behavior is achieved through
interfaces.

TCSMS uses single inheritance for custom exceptions:

**File:** `telecom/exception/ValidationException.java`  
**Line 3:**

```java
public class ValidationException extends TelecomException {
```

TCSMS does not use an abstract class hierarchy for domain services; it uses
interfaces and concrete implementations instead.

### 10.6 What is polymorphism?

Polymorphism means one interface or parent reference can represent different
concrete implementations. The same method call can produce implementation-
specific behavior.

**File:** `telecom/strategy/PaymentStrategyFactory.java`  
**Lines 14-22:**

```java
case UPI:
    return new UpiPaymentStrategy();
case CARD:
    return new CardPaymentStrategy();
case NET_BANKING:
case BANK_TRANSFER:
    return new NetBankingPaymentStrategy();
```

The factory returns the common `PaymentStrategy` type while selecting a
different implementation at runtime.

### 10.7 What is compile-time polymorphism?

Compile-time polymorphism is method overloading. The compiler chooses the
method based on the number or types of parameters.

**File:** `telecom/report/ReportGenerator.java`  
**Lines 81-85:**

```java
public String generateBillInvoiceText(Bill bill, Customer customer, String fileName) throws Exception {
    return generateBillInvoiceText(bill, customer, null, null, null, fileName);
}
```

This overload delegates to another version of the same method with additional
invoice details.

### 10.8 What is runtime polymorphism?

Runtime polymorphism is method overriding. The JVM selects the implementation
based on the actual object, not just the reference type.

**File:** `telecom/service/impl/PlanServiceImpl.java`  
**Lines 92-93:**

```java
@Override
public TelecomPlan getPlanById(int planId) throws TelecomException {
```

The concrete service implementation provides the behavior promised by its
service interface.

### 10.9 What is an interface?

An interface is a contract containing operations that implementing classes
agree to provide. It supports abstraction and loose coupling.

**File:** `telecom/service/SubscriptionService.java`  
**Lines 9-16:**

```java
public interface SubscriptionService {
    static boolean isActive(MobileSubscription subscription) {
        return "ACTIVE".equals(subscription.getStatus());
    }

    default List<MobileSubscription> getActiveSubscriptions(int customerId) {
        return getCustomerSubscriptions(customerId)
                .stream()
                .filter(SubscriptionService::isActive)
                .collect(Collectors.toList());
    }

    MobileSubscription subscribeToPlan(int customerId, int planId, String simTypeStr) throws TelecomException;
```

This interface contains abstract operations plus reusable static and default
behavior.

### 10.10 What is an abstract class?

An abstract class is a class that cannot be instantiated directly. It can
contain both implemented methods and abstract methods. It is useful when
related classes share state or common implementation.

TCSMS does not currently use an abstract class for its service or model
hierarchy. It uses interfaces such as `SubscriptionService` and
`PaymentStrategy`. In an interview, explain this distinction instead of
claiming that the project uses an abstract class.

### 10.11 What is a constructor?

A constructor initializes an object when it is created. It has the same name as
the class and has no return type.

**File:** `telecom/report/ReportGenerator.java`  
**Lines 21-23:**

```java
public ReportGenerator() {
    ensureReportDirectoryExists();
}
```

Creating a report generator also ensures that the output directory exists.

### 10.12 What are `this` and `super`?

`this` refers to the current object. It distinguishes an instance field from a
parameter with the same name. `super` refers to the parent class and is used to
call a parent constructor or method.

**File:** `telecom/dto/CustomerRegistrationDTO.java`  
**Line 22:**

```java
this.firstName = firstName;
```

The left side is the current object's field and the right side is the
constructor parameter.

**File:** `telecom/exception/AuthenticationException.java`  
**Line 7:**

```java
super(message);
```

This calls the parent exception constructor.

### 10.13 What is composition?

Composition is a strong “has-a” relationship where one object owns its
collaborators and usually manages their lifecycle.

**File:** `telecom/scheduler/PaymentNotificationService.java`  
**Lines 21-23:**

```java
private final BlockingQueue<Notification> notificationQueue;
private final ExecutorService workerPool;
private final AuditAndNotificationDAO notificationDAO;
```

The notification service is composed of a queue, worker pool and DAO.

### 10.14 What is aggregation?

Aggregation is a weaker “has-a” relationship. The contained object can exist
independently of the container. For example, a service can use a DAO that is
also usable by another service.

**File:** `telecom/scheduler/BillingScheduler.java`  
**Lines 21-23:**

```java
private final ScheduledExecutorService scheduler;
private final BillingService billingService;
private final SubscriptionDAO subscriptionDAO;
```

The scheduler collaborates with billing and subscription abstractions; those
collaborators represent separate responsibilities.

### 10.15 What is association?

Association is a general relationship in which one class knows about or uses
another class. It does not necessarily imply ownership.

**File:** `telecom/service/impl/PlanServiceImpl.java`  
**Line 18:**

```java
private final PlanDAO planDAO;
```

The plan service is associated with the DAO to retrieve plan data.

### 10.16 What is coupling and cohesion?

Coupling is the level of dependency between classes. Lower coupling is usually
better because changes are easier to isolate. Cohesion is how closely related
the responsibilities within one class are. Higher cohesion is usually better.

**File:** `telecom/service/impl/PlanServiceImpl.java`  
**Line 18:**

```java
private final PlanDAO planDAO;
```

Depending on the `PlanDAO` abstraction rather than embedding SQL in the
service reduces coupling and keeps plan business logic cohesive.

### 10.17 What are access modifiers?

Access modifiers control visibility:

- `private`: available only inside the declaring class.
- package-private: available inside the same package.
- `protected`: available to the package and subclasses.
- `public`: available from any package.

**File:** `telecom/report/ReportGenerator.java`  
**Lines 19 and 25:**

```java
private static final String DEFAULT_REPORT_DIR = "reports";
private void ensureReportDirectoryExists() {
```

Both the constant and helper method are hidden from external callers.

### 10.18 What is the difference between an object reference and an object?

An object is the instance stored in memory. A reference is the variable that
points to that object. Multiple references can point to the same object.

**File:** `telecom/scheduler/PaymentNotificationService.java`  
**Line 22:**

```java
private final ExecutorService workerPool;
```

`workerPool` is a reference whose value is assigned to an executor object in
the constructor.

### 10.19 What is the difference between an interface and an abstract class?

An interface primarily defines a contract and supports multiple interface
implementations. An abstract class can share state and implemented behavior,
but a class can extend only one class. TCSMS uses interfaces for DAO, service
and strategy boundaries because those boundaries need replaceable
implementations and loose coupling.

### 10.20 What are SOLID principles?

SOLID is a group of design principles:

- **S - Single Responsibility:** one class should have one main reason to
  change.
- **O - Open/Closed:** extend behavior without modifying stable behavior.
- **L - Liskov Substitution:** implementations should honor their contracts.
- **I - Interface Segregation:** prefer focused interfaces.
- **D - Dependency Inversion:** depend on abstractions rather than concrete
  implementations.

**File:** `telecom/strategy/PaymentStrategy.java`  
**Lines 5-7:**

```java
public interface PaymentStrategy {
    boolean validateAndProcess(double amount, String customerAccount) throws TelecomException;
    String getChannelName();
}
```

This is an example of abstraction, interface segregation and dependency
inversion. Payment services can use the strategy contract without depending on
one particular payment channel.

<!-- pagebreak -->

## 11. Final interview reminder

When showing the project, use this order:

1. State the definition in one sentence.
2. Explain why the project needs the concept.
3. Show the shortened file path.
4. Point to the exact line number.
5. Read the code statement and explain its result.

All updates in this guide are local documentation changes only. No commit or
Git push is performed.

## 9. Closing statement for the interviewer

“These concepts are implemented in my project, not only theoretical. I use
executors for background work, file writers for reports and logs, functional
interfaces and lambdas for reusable callbacks, streams for collection
processing, and OOP abstractions such as services, DAOs and payment strategies
to keep the application maintainable.”
