"""Exercise the real console on the isolated review classpath. No credentials are saved."""
import getpass
import os
from pathlib import Path
import queue
import re
import subprocess
import threading
import time

root = Path(__file__).resolve().parents[2]
build = root / "target" / "independent-review"
password = os.environ.get("REVIEW_CUSTOMER_PASSWORD") or getpass.getpass("Customer password: ")
username = os.environ.get("REVIEW_CUSTOMER_USERNAME", "arjunm")
proc = subprocess.Popen(
    ["java", "-Dfile.encoding=UTF-8", "-cp", f"{build / 'classes'};{root / 'lib' / '*'}",
     "com.amdocs.telecom.main.MainApplication"],
    cwd=build, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
    encoding="utf-8", errors="replace", bufsize=0,
)
output = queue.Queue()
transcript = []
results = []

def collect():
    while char := proc.stdout.read(1):
        output.put(char)
    output.put(None)

threading.Thread(target=collect, daemon=True).start()

def until(token, timeout=15):
    buffer = ""
    deadline = time.monotonic() + timeout
    while token not in buffer:
        remaining = deadline - time.monotonic()
        if remaining <= 0:
            raise TimeoutError(f"Timed out waiting for {token!r}; tail: {buffer[-250:]}")
        char = output.get(timeout=remaining)
        if char is None:
            raise RuntimeError(f"Console exited before {token!r}: {buffer[-250:]}")
        buffer += char
        transcript.append(char)
    return buffer

def send(value):
    proc.stdin.write(str(value) + "\n")
    proc.stdin.flush()

def login(secret):
    send(1)
    until("Username or Email (or 'cancel' to return): ")
    send(username)
    until("Password: ")
    send(secret)
    challenge = until("Enter CAPTCHA: ")
    send(re.search(r"CAPTCHA: \[([^]]+)\]", challenge).group(1))
    return until("Select option: ")

def verify(name, condition):
    results.append(f"{'PASS' if condition else 'FAIL'} | {name}")
    print(results[-1])

try:
    until("Select option: ")
    send(1)
    until("Select option: ")
    response=login(password)
    verify("Customer seeded login, CAPTCHA and dashboard", "Login successful!" in response and "CUSTOMER DASHBOARD" in response)
    menu_rows = [line for line in response.splitlines() if line.startswith(("|", "+"))]
    verify("Customer menu has aligned borders and consolidated actions",
           bool(menu_rows) and len(menu_rows) <= 16 and all(len(line) == 64 for line in menu_rows)
           and "2. Explore Plans" in response and "0. Logout" in response
           and "Search Plans by Name" not in response and "Compare and Filter Plans" not in response)
    if "CUSTOMER DASHBOARD" not in response:
        raise RuntimeError("Customer login failed")
    for option,submenu,expected in [(1,1,"CUST100245"),(2,1,"PLAN-101"),(3,None,"SUB10001"),(7,None,"INV-2026-08-10245"),(9,1,"DATA"),(12,None,"No notifications.")]:
        send(option)
        response=""
        if submenu is not None:
            until({1:"Profile option: ",2:"Plan option: ",9:"Usage option: "}[option])
            send(submenu)
        if option==7:
            response=until("Enter Bill Number or ID to view full invoice on console (or 0 to return): ")
            send(0)
        response+=until("Select option: ")
        verify(f"Customer dashboard option {option}",expected in response)
    for action in ["A","D"]:
        send(6)
        until("Subscription ID (0 to return): ")
        send(1)
        until("Add-on ID (0 to return): ")
        send(1)
        until("Activate or deactivate (A/D): ")
        send(action)
        response=until("Select option: ")
        verify("Customer add-on " + action,"Add-on updated." in response)
    send(9)
    until("Usage option: ")
    send(2)
    until("Billing month (yyyy-MM): ")
    send("2026-08")
    response=until("Select option: ")
    verify("Customer monthly usage and units","DATA: 2048.00 MB" in response)
    send(1)
    until("Profile option: ")
    send(2)
    for label in ["First name","Last name","Address","City","Country"]:
        until(label+" [")
        until("]: ")
        send("Pune" if label=="City" else "")
    response=until("Select option: ")
    verify("Customer profile editing","Profile updated." in response)
    send(2)
    until("Plan option: ")
    send(7)
    until("First plan ID: ")
    send(1)
    until("Second plan ID: ")
    send(2)
    response=until("Select option: ")
    verify("Customer compares plans","PLAN-101" in response and "PLAN-102" in response)
    for selection, prompts, values, expected in [
        (2, ["Enter plan name keyword: "], ["Premium"], "5G Premium"),
        (3, ["Enter maximum monthly price ("], ["700"], "PLAN-102"),
        (4, ["Minimum price: ", "Maximum price: "], ["600", "1000"], "PLAN-101"),
        (5, ["Minimum GB: "], ["100"], "PLAN-103"),
        (6, ["Sort order (A = ascending, D = descending): "], ["A"], "PLAN-104"),
        (0, [], [], "CUSTOMER DASHBOARD"),
        (99, [], [], "Invalid option."),
    ]:
        send(2)
        until("Plan option: ")
        send(selection)
        for prompt, value in zip(prompts, values):
            until(prompt)
            send(value)
        response = until("Select option: ")
        verify(f"Plan explorer action {selection}", expected in response)
    send(8)
    until("Enter Bill Number (e.g. BILL-1001) or Bill ID to pay (or 'cancel'): ")
    send(1)
    until("Enter choice (0-4, Default: 1): ")
    send(99)
    response = until("Select option: ")
    verify("Invalid checkout method is rejected", "Invalid payment method." in response)
    send(8)
    until("Enter Bill Number (e.g. BILL-1001) or Bill ID to pay (or 'cancel'): ")
    send(1)
    until("Enter choice (0-4, Default: 1): ")
    send(4)
    receipt = until("Select download option (1-5, Default: 5): ")
    send(5)
    response = until("Select option: ")
    verify("Customer pays by bank transfer", "BANK_TRANSFER" in receipt and "PAID (SUCCESS)" in receipt)
    send(8)
    until("Enter Bill Number (e.g. BILL-1001) or Bill ID to pay (or 'cancel'): ")
    send(1)
    response = until("Select option: ")
    verify("Customer cannot pay the same bill twice", "already PAID" in response)
    send(0)
    response=until("Select option: ")
    verify("Customer logout","Logged out. Goodbye!" in response)
    response=login(password)
    verify("Customer previous login timestamp","Last login:" in response)
    send(0)
    until("Select option: ")
    send(4)
    until("Select option: ")
    send(3)
    until("Goodbye!")
    proc.wait(timeout=15)
    verify("Customer application clean shutdown",proc.returncode==0)
finally:
    if proc.poll() is None:
        proc.kill()
        proc.wait(timeout=5)
    (build / "customer-console-results.txt").write_text("\n".join(results)+"\n",encoding="utf-8")
    (build / "customer-console-output.txt").write_text("".join(transcript),encoding="utf-8")

raise SystemExit(1 if any(r.startswith("FAIL") for r in results) else 0)
