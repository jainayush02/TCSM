# Admin service review

Reviewed on 7 October 2026. Dashboard actions were exercised one at a time through the real console on an isolated H2 database, using the supplied admin credentials. Test changes, including password changes and account locks, did not affect the project's normal database.

Admin login path: Main Menu -> 2. Administrator Portal -> 1. Admin Login -> username `admin` -> password -> CAPTCHA.

Customer login path: Main Menu -> 1. Customer Portal -> 1. Login -> username or email -> password -> CAPTCHA.

## Results

All 23 dashboard actions worked with valid inputs. After fixing the missing-plan status message, the console review passed all 52 checks, including the invalid-ID check.

| Option | Action | Result |
|---|---|---|
| 1 | View active plans | Passed; newly created plans appear and inactive plans disappear |
| 2 | Add plan | Passed; plan saved and subsequently listed |
| 3 | Set plan status | Valid updates passed; missing plan ID correctly reports Plan not found |
| 4 | Configure plan rules | Passed |
| 5 | View customers | Passed |
| 6 | Edit customer / set status | Profile edit and suspended/active changes passed; listing confirms persisted values |
| 7 | View subscriptions | Passed |
| 8 | Set subscription status | Suspension and reactivation passed |
| 9 | View SIM inventory | Passed; added SIM and its status appear |
| 10 | Add SIM / set status | Both passed |
| 11 | Generate monthly bills | Passed |
| 12 | View unpaid bills | Passed |
| 13 | View payments | Empty-list display passed; this console fixture has no payments |
| 14 | Scan overdue accounts | Scan, overdue marking and suspension passed |
| 15 | Process bulk usage | Six records processed successfully |
| 16 | View monthly usage | Passed |
| 17 | Revenue reports | Report display, usage rankings and unpaid customer listing passed |
| 18 | Customers by city | Display and CSV export passed; exported file content checked |
| 19 | Manage complaints | All/open lists, lookup by ticket and ID, resolution and persisted solution passed |
| 20 | Complaint analytics | Display and CSV export passed; exported file content checked |
| 21 | Audit logs | Passed; committed complaint changes appear |
| 22 | Change password | Passed; subsequent authentication uses the changed password |
| 23 | Live activity monitor | Start, new committed password-change event and stop passed |
| 0 | Logout | Passed |

Admin authentication also passed valid login, CAPTCHA, incorrect password rejection, lock after three failures, locked-login rejection, OTP recovery, previous-login timestamp and clean shutdown.

## Issue fixed

`AdminController.togglePlanStatus()` now checks the boolean returned by `PlanDAOImpl.updateStatus()`. When no row matches, the DAO returns false and the controller displays `Plan not found.` Success is displayed only when a plan was updated.

Verified: log in as admin -> option 3 -> plan ID `999999` -> status `INACTIVE`. The screen reports `Plan not found.` Existing plans still update successfully.

The complete admin console review was rerun after the fix: 52 passed, zero failed. Three related plan service checks also passed after recompilation. The broader 23-check H2 and MySQL service reviews below were performed before this controller message fix.

## Evidence

- Console test: `tests/review/admin-all-services-review.py`
- Console results: `target/independent-review/admin-all-services-results.txt`
- Console transcript: `target/independent-review/admin-all-services-output.txt`
- Related H2 service checks: 23 passed, zero failed, in `target/independent-review/results.txt`.
- Related MySQL service checks: 23 passed, zero failed, in `target/independent-review-mysql/mysql-results.txt`.

Run the console review after compiling with `tests/review/run-review.ps1`. Supply the password using `REVIEW_ADMIN_PASSWORD` or the script's password prompt. The test does not save password input in its transcript.
