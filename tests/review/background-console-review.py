"""Keep registration open while the real scheduled jobs run on the review database."""
from pathlib import Path
import queue
import subprocess
import threading
import time
import uuid

root = Path(__file__).resolve().parents[2]
build = root / "target" / "independent-review"
work = build / ("background-console-" + uuid.uuid4().hex[:8])
work.mkdir()
proc = subprocess.Popen(
    ["java", "-Dfile.encoding=UTF-8", "-cp", f"{build / 'classes'};{root / 'lib' / '*'}",
     "com.amdocs.telecom.main.MainApplication"],
    cwd=work, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
    encoding="utf-8", errors="replace", bufsize=0,
)
output = queue.Queue()
transcript = []

def collect():
    while char := proc.stdout.read(1):
        output.put(char)
    output.put(None)

def until(token):
    text = ""
    deadline = time.monotonic() + 15
    while token not in text:
        char = output.get(timeout=max(.01, deadline - time.monotonic()))
        assert char is not None, "Application exited before the prompt"
        transcript.append(char)
        text += char

reader = threading.Thread(target=collect, daemon=True)
reader.start()
try:
    until("Select option: ")
    proc.stdin.write("1\n2\nMohit\nGirase\n2004-09-09\nmohit.review@example.com\n")
    proc.stdin.flush()
    until("Mobile Number (10-15 digits): ")
    try:
        unexpected = output.get(timeout=65)
    except queue.Empty:
        pass
    else:
        raise AssertionError(f"Registration prompt was interrupted: {unexpected!r}")
    logs = "\n".join(path.read_text(encoding="utf-8") for path in Path(work, "logs").glob("*.log"))
    assert "Scheduled run complete. Bills generated:" in logs, "Scheduled billing did not finish"
    assert "Total subscriptions suspended:" in logs, "Account monitoring did not finish"
    proc.stdin.write("cancel\n4\n3\n")
    proc.stdin.flush()
    until("Thank you for using TCSMS. Goodbye!")
    assert proc.wait(timeout=15) == 0, "Application did not exit cleanly"
    Path(build, "background-console-output.txt").write_text("".join(transcript), encoding="utf-8")
    print("PASS | Registration stays quiet for 65 seconds while both scheduled jobs finish")
    print("PASS | Scheduled activity is recorded in the application log")
    print("PASS | Registration can be cancelled and the application exits normally")
finally:
    if proc.poll() is None:
        proc.kill()
        proc.wait()
    reader.join(timeout=5)
    proc.stdin.close()
    proc.stdout.close()
