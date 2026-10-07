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
password = os.environ.get("REVIEW_ADMIN_PASSWORD") or getpass.getpass("Admin password: ")
username = os.environ.get("REVIEW_ADMIN_USERNAME", "admin")
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
    until("Admin Username (or 'cancel'): ")
    send(username)
    until("Admin Password: ")
    send(secret)
    challenge = until("Enter CAPTCHA: ")
    send(re.search(r"CAPTCHA: \[([^]]+)\]", challenge).group(1))
    return until("Select option: ")

def verify(name, condition):
    results.append(f"{'PASS' if condition else 'FAIL'} | {name}")
    print(results[-1])

try:
    until("Select option: ")
    send(2)
    until("Select option: ")
    response = login(password)
    verify("Admin supplied credential + CAPTCHA + dashboard", "Admin login successful!" in response and "ADMINISTRATOR DASHBOARD" in response)
    menu_rows = [line for line in response.splitlines() if line.startswith(("|", "+"))]
    verify("Admin menu has aligned borders and one subscription status action",
           bool(menu_rows) and len(menu_rows) <= 28 and all(len(line) == 64 for line in menu_rows)
           and "8. Set Subscription Status" in response and "0. Logout" in response
           and "Reactivate Subscription" not in response)
    if "ADMINISTRATOR DASHBOARD" not in response:
        raise RuntimeError("Admin login failed; actions not executed")
    for option, expected in [(1, "PLAN-101"), (5, "CUST100245"), (7, "SUB10001"),
                             (12, "INV-2026-08-10245"), (13, "No payments found"), (9, "Total SIMs: 5"),
                             (11, "Bills generated: 1")]:
        send(option)
        response = until("Select option: ")
        verify(f"Admin dashboard option {option}", expected in response and "Error:" not in response)
    send(14)
    until("Suspend accounts overdue > 30 days? (yes/no): ")
    send("yes")
    response = until("Select option: ")
    verify("Admin overdue scan and suspension", "Total subscriptions suspended: 1" in response)
    send(8)
    until("Subscription ID (0 to return): ")
    send(1)
    until("Status (ACTIVE/SUSPENDED/INACTIVE): ")
    send("ACTIVE")
    response = until("Select option: ")
    verify("Admin subscription reactivation", "Subscription updated." in response)
    send(6)
    until("Customer ID (0 to return): ")
    send(1)
    until("Customer action: ")
    send(1)
    for label in ["First name", "Last name", "Address", "City", "Country"]:
        until(label + " [")
        until("]: ")
        send("Pune" if label == "City" else "")
    response = until("Select option: ")
    verify("Admin customer profile update", "Customer updated." in response)
    send(10)
    until("SIM action: ")
    send(1)
    until("SIM number: ")
    send("89910012345678999")
    until("IMSI: ")
    send("404010123456799")
    until("SIM type (ESIM/PHYSICAL_SIM): ")
    send("ESIM")
    response = until("Select option: ")
    verify("Admin adds SIM inventory", "SIM added." in response)
    for status in ["SUSPENDED", "ACTIVE"]:
        send(8)
        until("Subscription ID (0 to return): ")
        send(1)
        until("Status (ACTIVE/SUSPENDED/INACTIVE): ")
        send(status)
        response = until("Select option: ")
        verify("Admin manages subscription " + status, "Subscription updated." in response)
    send(16)
    until("Subscription ID: ")
    send(1)
    until("Month (yyyy-MM): ")
    send("2026-08")
    response = until("Select option: ")
    verify("Admin views monthly usage", "DATA" in response)
    send(4)
    until("Plan ID: ")
    send(2)
    until("Allow prepaid/postpaid changes? (Y/N): ")
    send("Y")
    until("Minimum days before another change: ")
    send(0)
    response = until("Select option: ")
    verify("Admin configures plan rules", "Plan rules updated." in response)
    send(17)
    response = until("Select option: ")
    verify("Admin sees highest usage and unpaid customer reports",
           "HIGHEST DATA USAGE CUSTOMERS" in response and "CUSTOMERS WITH UNPAID BILLS" in response
           and "CUST100245" in response and "2048.00 MB" in response)
    send(0)
    response = until("Select option: ")
    verify("Admin logout", "Admin logged out." in response)
    response = login("deliberately-wrong-review-password")
    verify("Admin invalid password", "Invalid admin credentials" in response and "ADMINISTRATOR DASHBOARD" not in response)
    login("deliberately-wrong-review-password")
    response = login("deliberately-wrong-review-password")
    verify("Admin locks after three failures", "Maximum 3 failed attempts" in response)
    response = login(password)
    verify("Locked admin cannot log in", "Admin account is locked" in response)
    send(2)
    until("Enter your Admin Username (or 'cancel'): ")
    send(username)
    response = until("Enter OTP: ")
    send(re.search(r"\(simulated\): (\d{6})", response).group(1))
    until("Enter new password (or 'cancel'): ")
    recovered_password = "ReviewAdmin@123"
    send(recovered_password)
    response = until("Select option: ")
    verify("Admin OTP recovery resets password and unlocks", "Admin password reset successfully!" in response)
    response = login(recovered_password)
    verify("Recovered admin can log in with previous timestamp",
           "ADMINISTRATOR DASHBOARD" in response and "Last Login Timestamp:" in response)
    send(0)
    until("Select option: ")
    send(3)
    until("Select option: ")
    send(3)
    until("Goodbye!")
    proc.wait(timeout=15)
    verify("Application clean shutdown", proc.returncode == 0)
finally:
    if proc.poll() is None:
        proc.kill()
        proc.wait(timeout=5)
    (build / "admin-console-results.txt").write_text("\n".join(results) + "\n", encoding="utf-8")
    # stdin, including credentials, is never copied into the transcript.
    (build / "admin-console-output.txt").write_text("".join(transcript), encoding="utf-8")

raise SystemExit(1 if any(r.startswith("FAIL") for r in results) else 0)
