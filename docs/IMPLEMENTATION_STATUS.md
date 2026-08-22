# Chronivaro Implementation Status

Audit date: 2026-08-21. `IMPLEMENTATION_SPECIFICATION.md` is authoritative; the repository is authoritative for current implementation state.

---

## Implemented Baseline

- **Architecture & Deployment (Sections 4.1, 14, 15.1–15.9, 16, 20.1):** 4-module Maven reactor (`chronivaro-core`, `chronivaro-rest`, `chronivaro-web`, `chronivaro-app`), JDK 25, embedded Eclipse Jetty 12 lifecycle, static frontend web asset delivery at `/`, Jersey JAX-RS REST integration under `/rest/chronivaro/v1`, executable standalone fat-JAR (`chronivaro.jar`).
- **Master Data & Registration (Sections 6.1, 6.8, 9.6, 13.7):** `Employee`, `Team`, `Location`, `EmploymentScheduleVersion`, `HolidayCalendar`, Strolch user creation with `SET_PASSWORD` challenge, and token-based initial password setting.
- **Time Tracking Foundation (Sections 6.3, 6.4, 6.4.1, 7.1–7.4, 9.1, 9.2):** WorkDay/WorkEntry model, dynamic target time calculation, multi-interval start/stop timer, midnight 24:00 splitting, forgotten timer auto-capping to daily target, weekly working location defaults, historical schedule version resolution by entry date, duration validation, and morning/afternoon location uniqueness.
- **Absence Management & Default Types (Sections 4.1 #6, 6.5, 6.5.1, 6.6, 9.4, 10.1, 13.2):** Preconfigured 10 standard absence types bootstrapped in `Model.xml` (`VACATION`, `ILLNESS`, `ACCIDENT`, `MILITARY_CIVIL_DEFENSE`, `DOCTOR_APPOINTMENT`, `TRAINING`, `PARENTAL_LEAVE`, `UNPAID_LEAVE`, `OVERTIME_COMPENSATION`, `OTHER`), `commentRequired` and `visibleOnPublicStatus` metadata, comment enforcement, duration type validation, and full `DRAFT` status / explicit submission and cancellation workflow.
- **Vacation Journal Immutability & Year-End Carry-Over (Sections 6.7, 6.7.1, 7.5, 11.3):** Automatic calculation and crediting of pro-rated annual vacation entitlement on employee creation; automated `CORRECTION` entries upon schedule employment rate or `exitDate` updates while preserving record immutability; automated year-end carry-over service transferring unexpired balances as `CARRY_OVER` entries with FIFO consumption.
- **Period Calculation Snapshots & Balance Carry-Forward (Sections 6.9, 11.2, 11.6.2):** Month summaries return immutable `calculationSnapshot` for approved and locked periods; `initialBalance` accurately carries forward prior month closing balance; monthly summary categorizes paid absences, unpaid absences, vacation usage, and holiday credits.
- **Reporting & Exports (Sections 11.1–11.5, 12.1–12.2, 13.8, 17, 18.6):** Core calculation engines, Web UI report viewers, deterministic RFC 4180 CSV exports, and server-side native OpenPDF export generator with streaming REST endpoints (`/reports/month`, `/reports/vacation`, `/reports/absences`).
- **Presence, Audit & System Operations (Sections 8, 11, 12, 13.2, 13.6, 19, 20):** Binary presence indicators with privacy masking, comprehensive audit trail recording entity lifecycle events and retention purge service, health/readiness probes, and structured logging.
- **Localization & Branding (Sections 4.2, 6.11, 12.3, 16, 18, 18.5):** Global company branding (name/logo), default language configuration, client-side i18n engine with German (Swiss German) and English translations across all views, and automated key parity verification.

---

## Incomplete Requirements / Active Backlog Tasks

The following requirements from `docs/IMPLEMENTATION_SPECIFICATION.md` represent incomplete or missing behaviour identified during repository re-audit:

1. **WorkEntry Comments, Shorten-Only Restrictions & MyTimes UI (Task 11):**
   - *Classification:* `PARTIALLY_IMPLEMENTED`
   - *Specification Reference:* Section 4.1 (#4, #5), Section 6.4, Section 9.1, Section 9.3, Section 12.1 (#1, #2), Section 13.2
   - *Status:* `OPEN`
   - *Missing Behaviour:* `StopTimerService` does not persist optional comments; `CorrectWorkEntryService` / `ChronivaroResource` allows regular employees to move start times or extend durations instead of restricting them strictly to shortening `end` times and updating comments; `MyTimesView.js` lacks comment editing and time shortening controls.

2. **Supervisor Detailed Monthly Period Inspection View & Endpoint (Task 12):**
   - *Classification:* `PARTIALLY_IMPLEMENTED`
   - *Specification Reference:* Section 4.1 (#11), Section 9.5, Section 12.1 (#6), Section 13.2
   - *Status:* `OPEN`
   - *Missing Behaviour:* `ApprovalsResource` lacks `GET /approvals/periods/{id}` for loading the detailed monthly report breakdown; `ApprovalsView.js` lacks an inspection dialog/modal that displays the full monthly report and allows direct approval/rejection with comments.

3. **User Management for Pure System Users (Non-Employees) (Task 13):**
   - *Classification:* `MISSING`
   - *Specification Reference:* Section 3.6, Section 6.1.1, Section 9.7, Section 12.1 (#8), Section 13.2
   - *Status:* `OPEN`
   - *Missing Behaviour:* No domain services (`CreateUserService`, `UpdateUserService`, `InitiateUserRegistrationService`), REST API (`/users`), or UI under Administration for managing Strolch users without linked `Employee` records.

---

## Specification Ambiguities Clarified

1. **Working Location Half-Day Split Boundary (Sections 6.4 & 6.4.1):** Cutoff between `MORNING` and `AFTERNOON` defined as 12:30 (or schedule midpoint).
2. **REST Route Naming Discrepancies (Sections 13.2 & 13.8):** Query parameters (`?format=pdf`), `Accept` headers, and route aliases are supported.
3. **Negative Vacation Balances (Section 6.7.1 Rule 13):** Strictly prohibited; approvals exceeding available entitlement are blocked.
4. **Mid-Month Employee Starts & Target Hours (Sections 6.2, 7.1):** Target minutes for days prior to `entryDate` are 0; calendar renders pre-entry days as inactive without missing booking warnings.
5. **Vacation Granting Timing (Section 6.7.1):** Full entitlement is granted upfront per January 1st (or pro-rated per `entryDate`) as `ENTITLEMENT` journal records; schedule changes post `CORRECTION` adjustments.

---

## Verification Basis

- Comprehensive repository inspection across `chronivaro-core`, `chronivaro-rest`, `chronivaro-web`, `chronivaro-app`, configuration templates, and documentation.
- All existing reactor test suites passing cleanly (`mvn clean test`).
