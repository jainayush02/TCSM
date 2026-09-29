from pathlib import Path

from reportlab.lib import colors
from reportlab.lib.enums import TA_CENTER
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.lib.units import mm
from reportlab.platypus import (BaseDocTemplate, Frame, PageTemplate, Paragraph,
                                Preformatted, Spacer, Table, TableStyle,
                                KeepTogether, PageBreak)


ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "output" / "pdf" / "Project_Evaluation_Notes.pdf"
OUT.parent.mkdir(parents=True, exist_ok=True)

INK = colors.HexColor("#18252B")
TEAL = colors.HexColor("#087E8B")
PALE = colors.HexColor("#EAF4F4")
MUTED = colors.HexColor("#52636A")
CODE_BG = colors.HexColor("#F1F4F5")

styles = getSampleStyleSheet()
styles.add(ParagraphStyle(name="CoverTitle", parent=styles["Title"], fontName="Helvetica-Bold",
                          fontSize=25, leading=30, textColor=INK, alignment=TA_CENTER, spaceAfter=8))
styles.add(ParagraphStyle(name="Subtitle", parent=styles["Normal"], fontSize=11, leading=16,
                          textColor=MUTED, alignment=TA_CENTER, spaceAfter=15))
styles.add(ParagraphStyle(name="Section", parent=styles["Heading1"], fontName="Helvetica-Bold",
                          fontSize=17, leading=21, textColor=TEAL, spaceBefore=10, spaceAfter=8,
                          keepWithNext=True))
styles.add(ParagraphStyle(name="Subsection", parent=styles["Heading2"], fontName="Helvetica-Bold",
                          fontSize=11.5, leading=15, textColor=INK, spaceBefore=8, spaceAfter=4,
                          keepWithNext=True))
styles.add(ParagraphStyle(name="Body2", parent=styles["BodyText"], fontSize=9.4, leading=13.4,
                          textColor=INK, spaceAfter=5))
styles.add(ParagraphStyle(name="Small", parent=styles["BodyText"], fontSize=8.3, leading=11.2,
                          textColor=MUTED, spaceAfter=3))
styles.add(ParagraphStyle(name="Code2", fontName="Courier", fontSize=8.1, leading=11,
                          backColor=CODE_BG, borderColor=colors.HexColor("#D4DEE1"), borderWidth=.4,
                          borderPadding=7, leftIndent=5, rightIndent=5, spaceBefore=3, spaceAfter=7))
styles.add(ParagraphStyle(name="Callout", parent=styles["BodyText"], fontSize=9.2, leading=13,
                          backColor=PALE, borderColor=TEAL, borderWidth=0, borderPadding=8,
                          leftIndent=5, rightIndent=5, spaceBefore=4, spaceAfter=7))
styles.add(ParagraphStyle(name="Cell", parent=styles["BodyText"], fontSize=8, leading=10.5,
                          textColor=INK))
styles.add(ParagraphStyle(name="CellHead", parent=styles["BodyText"], fontName="Helvetica-Bold",
                          fontSize=8, leading=10.5, textColor=colors.white))


def para(text, style="Body2"):
    return Paragraph(text, styles[style])


def code(text):
    return Preformatted(text.strip("\n"), styles["Code2"])


def bullet(text):
    return para("- " + text)


def page_decor(canvas, doc):
    canvas.saveState()
    w, h = A4
    canvas.setStrokeColor(colors.HexColor("#D8E1E3"))
    canvas.line(17 * mm, 15 * mm, w - 17 * mm, 15 * mm)
    canvas.setFont("Helvetica", 8)
    canvas.setFillColor(MUTED)
    canvas.drawString(17 * mm, 10 * mm, "TCSMS | Java project evaluation notes")
    canvas.drawRightString(w - 17 * mm, 10 * mm, f"Page {doc.page}")
    canvas.restoreState()


doc = BaseDocTemplate(str(OUT), pagesize=A4, leftMargin=18*mm, rightMargin=18*mm,
                      topMargin=17*mm, bottomMargin=21*mm,
                      title="Java Project Evaluation Notes - TCSMS",
                      author="Project code review")
frame = Frame(doc.leftMargin, doc.bottomMargin, doc.width, doc.height, id="main")
doc.addPageTemplates(PageTemplate(id="pages", frames=frame, onPage=page_decor))
story = []

story += [Spacer(1, 17*mm), para("JAVA PROJECT EVALUATION", "CoverTitle"),
          para("Telecom Customer &amp; Subscription Management System (TCSMS)", "Subtitle"),
          para("Study notes for explaining multithreading, functional interfaces, lambda expressions, Stream API, and OOP with evidence from the project source.", "Callout"),
          Spacer(1, 4*mm), para("Quick introduction", "Section"),
          para("This Java console application manages telecom customers, plans, subscriptions, usage, billing, payments, complaints, and reports. The code separates console workflows, business services, database access (DAO), domain models, and background schedulers. The examples below are taken from the current files in <b>src/main/java</b>.")]

story += [para("One-minute evaluation answer", "Subsection"),
          para("“My project uses Java concurrency for background work such as processing usage batches, scheduling billing, scanning overdue bills, and handling notifications. Lambdas provide compact implementations of Runnable, Callable, Predicate, and Comparator. Functional interfaces define the expected behavior, and lambdas supply that behavior. The Stream API processes in-memory lists for filtering, sorting, grouping, and totals. OOP appears through encapsulated model fields, service and DAO abstractions, payment strategy implementations, and specialized exceptions.”", "Callout"),
          para("Keep the distinction clear: a stream pipeline is not automatically multithreaded. The shown calls use stream(), which is sequential; concurrency is provided separately by ExecutorService and ScheduledExecutorService.", "Small"),
          para("1. Multithreading and concurrency", "Section"),
          para("Multithreading lets a program make progress on more than one task at a time. Java executors manage worker threads so the program can submit tasks without manually creating a Thread for every job. It is useful for independent or time-consuming work, especially background database and notification tasks.", "Body2"),
          para("Where it appears in this project", "Subsection")]

rows = [["Project location", "What happens and why"],
        ["UsageProcessor.java: 30-32, 39-63", "A fixed pool of four workers accepts one Runnable per usage batch (49-62). The shared processed count is protected by synchronized (54-56, 89-92). This lets independent batches write concurrently while protecting the counter."],
        ["PaymentNotificationService.java: 24-32, 38-58, 64-76", "Three workers poll a bounded BlockingQueue and process notifications. Payment flow can enqueue work quickly; workers perform it in the background. A full queue is handled explicitly (71-76)."],
        ["BillingScheduler.java: 26-32, 41-75", "A ScheduledExecutorService runs a billing task repeatedly with scheduleAtFixedRate (74). This automates billing without requiring an administrator to start each run manually."],
        ["AccountMonitor.java: 30-37, 44-80, 91-102", "A two-thread pool runs Callable tasks and retrieves results through Future.get with a timeout. Callable returns a value; Future represents the pending result."],
        ["DBConnection.java: 33", "The synchronized singleton accessor serializes instance creation. This protects creation of the shared connection manager, not every database operation."]]
table = Table([[para(c, "CellHead") if r == 0 else para(c, "Cell") for c in row] for r, row in enumerate(rows)],
              colWidths=[52*mm, 118*mm], repeatRows=1, hAlign="LEFT")
table.setStyle(TableStyle([("BACKGROUND", (0,0), (-1,0), TEAL), ("BACKGROUND", (0,1), (-1,-1), colors.white),
                           ("GRID", (0,0), (-1,-1), .35, colors.HexColor("#CCD6D9")),
                           ("VALIGN", (0,0), (-1,-1), "TOP"), ("LEFTPADDING", (0,0), (-1,-1), 6),
                           ("RIGHTPADDING", (0,0), (-1,-1), 6), ("TOPPADDING", (0,0), (-1,-1), 5),
                           ("BOTTOMPADDING", (0,0), (-1,-1), 5)]))
story.append(table)
story += [para("2. Functional interfaces", "Section"),
          para("A functional interface has exactly one abstract method (SAM: single abstract method). It is a target type for a lambda. It may also contain default or static methods. Runnable and Callable are concurrency interfaces; Predicate, Comparator, and Supplier express reusable behavior.", "Body2"),
          para("Project examples", "Subsection")]
rows = [["Type", "Abstract method idea", "Project evidence and purpose"],
        ["Runnable", "void run()", "UsageProcessor.java:49 and PaymentNotificationService.java:41. Runs work without returning a result."],
        ["Callable&lt;T&gt;", "T call()", "AccountMonitor.java:48, 57, 111. Tasks return overdue bills or a count; the executor provides a Future."],
        ["Predicate<T>", "boolean test(T)", "PlanServiceImpl.java:36, 45, 54. Encapsulates conditions such as plan name contains a keyword or price is within budget."],
        ["Comparator<T>", "int compare(T,T)", "PlanServiceImpl.java:63 and ReportServiceImpl.java:45-48. Defines ordering for prices or customer totals."],
        ["Supplier<T>", "T get()", "PlanServiceImpl.java:78 uses the Supplier overload of orElseThrow; the exception is created only when no plan is found."]]
table = Table([[para(c, "CellHead") if r == 0 else para(c, "Cell") for c in row] for r, row in enumerate(rows)],
              colWidths=[26*mm, 37*mm, 107*mm], repeatRows=1)
table.setStyle(TableStyle([("BACKGROUND", (0,0), (-1,0), TEAL), ("GRID", (0,0), (-1,-1), .35, colors.HexColor("#CCD6D9")),
                           ("VALIGN", (0,0), (-1,-1), "TOP"), ("LEFTPADDING", (0,0), (-1,-1), 5),
                           ("RIGHTPADDING", (0,0), (-1,-1), 5), ("TOPPADDING", (0,0), (-1,-1), 5),
                           ("BOTTOMPADDING", (0,0), (-1,-1), 5)]))
story.append(table)
story += [para("Important interview distinction", "Subsection"),
          para("PaymentStrategy.java:8-10 is an ordinary interface, not a functional interface: it declares two abstract methods. It is used for polymorphism and the Strategy pattern. CustomerService.java is also a regular service contract. Do not claim these can be implemented by one lambda.", "Callout"),
          para("3. Lambda expressions", "Section"),
          para("A lambda is a short expression or block of code that provides the implementation for a functional interface. The arrow separates parameters from the behavior: parameters -> body. Its type is usually inferred from the method argument or variable declaration.", "Body2"),
          para("Examples from the code", "Subsection"),
          code("// Runnable: no result\nexecutor.submit(() -> usageDAO.saveBatch(batch));\n\n// Predicate: true when this plan matches\nPredicate<TelecomPlan> withinBudget = p -> p.getMonthlyRental() <= maxPrice;\n\n// Callable: computes and returns a value\nCallable<Integer> task = () -> countOverdueBills();\n\n// Comparator logic inside ReportServiceImpl\n(c1, c2) -> Double.compare(totalFor(c2), totalFor(c1))"),
          para("Actual examples: UsageProcessor.java:49-62; PlanServiceImpl.java:36, 45, 54; AccountMonitor.java:48-53; ReportServiceImpl.java:45-48. Lambdas reduce anonymous-class boilerplate and make the rule passed to an API visible at the call site.")]

story += [PageBreak(), para("4. Stream API", "Section"),
          para("A Stream is a pipeline for processing items from a source such as a List. Intermediate operations such as filter, map, and sorted describe transformations; a terminal operation such as collect, sum, or forEach runs the pipeline and produces an outcome. Streams do not store the items and do not inherently create threads.", "Body2"),
          para("Pipeline example from plan search", "Subsection"),
          code("plans.stream()                 // get a stream from the list\n     .filter(withinBudget)     // keep affordable plans\n     .collect(Collectors.toList()); // make a result list"),
          para("Project examples and their business meaning", "Subsection")]
rows = [["Location", "Operation", "Meaning"],
        ["PlanServiceImpl.java:36-39", "filter + collect", "Return plans whose name matches the search."],
        ["PlanServiceImpl.java:63-71", "sorted + collect", "Sort plans by monthly rental, ascending or descending."],
        ["SubscriptionServiceImpl.java:48-49", "anyMatch", "Short-circuit check for an existing active subscription to the same plan."],
        ["ReportServiceImpl.java:34-50", "groupingBy + summingDouble; sorted + limit", "Sum bill values per customer, order customers by total, return top ten."],
        ["ReportServiceImpl.java:63-64", "groupingBy", "Build a map from city to customers."],
        ["ReportServiceImpl.java:76-81", "filter + groupingBy + summarizingDouble", "Summarize paid bill amounts by billing month (count, sum, min, max, average)."],
        ["ReportServiceImpl.java:95-98", "filter + mapToDouble + sum", "Add paid bill amounts to calculate total revenue."]]
table = Table([[para(c, "CellHead") if r == 0 else para(c, "Cell") for c in row] for r, row in enumerate(rows)],
              colWidths=[44*mm, 53*mm, 73*mm], repeatRows=1)
table.setStyle(TableStyle([("BACKGROUND", (0,0), (-1,0), TEAL), ("GRID", (0,0), (-1,-1), .35, colors.HexColor("#CCD6D9")),
                           ("VALIGN", (0,0), (-1,-1), "TOP"), ("LEFTPADDING", (0,0), (-1,-1), 5),
                           ("RIGHTPADDING", (0,0), (-1,-1), 5), ("TOPPADDING", (0,0), (-1,-1), 5),
                           ("BOTTOMPADDING", (0,0), (-1,-1), 5)]))
story.append(table)

story += [para("5. OOP concepts used", "Section"),
          para("Object-oriented programming models a system as objects that combine state (data) and behavior (methods). These are clear examples to explain in this project:", "Body2")]
rows = [["Concept", "Meaning in plain language", "Project evidence"],
        ["Encapsulation", "Keep an object's state inside it and expose access through methods.", "Customer.java:6-20 declares fields private; getters/setters begin at 44. A Customer object groups customer data and operations such as getFullName (56)."],
        ["Abstraction", "Expose a contract while hiding implementation details.", "CustomerService.java:9-15 defines operations; CustomerServiceImpl.java:18 implements business rules. CustomerDAO is a persistence contract implemented by CustomerDAOImpl."],
        ["Inheritance", "A specialized class receives behavior/type from a parent class.", "ValidationException.java:3 extends TelecomException.java:3; the specialized validation error is still a TelecomException."],
        ["Polymorphism", "Code can use a shared parent/interface while a concrete type supplies behavior.", "PaymentStrategy.java defines common payment operations; UpiPaymentStrategy.java:5-20 implements them. Card and net-banking strategies provide alternate implementations. Factory selects one in PaymentStrategyFactory.java:13-26."],
        ["Composition / dependency", "An object uses other objects to do its work.", "CustomerServiceImpl.java:20-23 holds a CustomerDAO dependency and delegates persistence to it. This separates business rules from SQL."]]
table = Table([[para(c, "CellHead") if r == 0 else para(c, "Cell") for c in row] for r, row in enumerate(rows)],
              colWidths=[29*mm, 57*mm, 84*mm], repeatRows=1)
table.setStyle(TableStyle([("BACKGROUND", (0,0), (-1,0), TEAL), ("GRID", (0,0), (-1,-1), .35, colors.HexColor("#CCD6D9")),
                           ("VALIGN", (0,0), (-1,-1), "TOP"), ("LEFTPADDING", (0,0), (-1,-1), 5),
                           ("RIGHTPADDING", (0,0), (-1,-1), 5), ("TOPPADDING", (0,0), (-1,-1), 5),
                           ("BOTTOMPADDING", (0,0), (-1,-1), 5)]))
story.append(table)
story += [PageBreak(), para("6. Short viva questions", "Section"),
          para("Q: Is a lambda the same thing as a functional interface?", "Subsection"),
          para("A: No. The interface defines one abstract method; the lambda is one concise implementation of that method."),
          para("Q: Does stream() run in parallel?", "Subsection"),
          para("A: No. stream() is sequential. parallelStream() or another concurrency mechanism is needed for parallel work; this project uses executors for background tasks."),
          para("Q: Why use Callable instead of Runnable?", "Subsection"),
          para("A: Callable returns a result and may throw checked exceptions. Runnable has a void run() method and does not return a task result."),
          para("Q: Where is synchronization needed?", "Subsection"),
          para("A: UsageProcessor protects updates and reads of the shared totalProcessed counter with the same lock. Otherwise multiple worker threads could overwrite each other's increments."),
          para("Q: Why use payment strategies?", "Subsection"),
          para("A: Each payment channel has its own validation and processing behavior. The caller can program to PaymentStrategy and choose a concrete channel without mixing every channel's rules into one large conditional."),
          para("Revision note", "Subsection"),
          para("Line references were checked against the Java files in this workspace when these notes were generated. Line numbers may shift if the source is edited. The existing reports/Java_Modern_Features_Evaluation.md was not used as authority for claims; examples were verified in source.", "Small")]

doc.build(story)
print(OUT)
