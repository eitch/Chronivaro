# Chronivaro Implementation Status

Audit date: 2026-08-23. `IMPLEMENTATION_SPECIFICATION.md` is authoritative; the repository is authoritative for current implementation state.

---

## Implemented Baseline

- **Architecture & Deployment (Sections 4.1, 14, 15.1–15.9, 16, 20.1):** 4-module Maven reactor (`chronivaro-core`, `chronivaro-rest`, `chronivaro-web`, `chronivaro-app`), JDK 25, embedded Eclipse Jetty 12 lifecycle, static frontend web asset delivery at `/`, Jersey JAX-RS REST integration under `/rest/chronivaro/v1`, executable standalone fat-JAR (`chronivaro.jar`).
- **Master Data & Registration (Sections 6.1, 6.8, 9.6, 13.7):** `Employee`, `Team`, `Location`, `EmploymentScheduleVersion`, `HolidayCalendar`, Strolch user creation with `SET_PASSWORD` challenge, and token-based initial password setting.
- **Time Tracking Foundation (Sections 6.3, 6.4, 6.4.1, 7.1–7.4, 9.1, 9.2, 9.3):** WorkDay/WorkEntry model, dynamic target time calculation, multi-interval start/stop timer with optional comment persistence, midnight 24:00 splitting, forgotten timer auto-capping to daily target, weekly working location defaults, historical schedule version resolution by entry date, duration validation, morning/afternoon location uniqueness, employee shorten-only restrictions (preventing start-time modifications or duration extensions), administrative full correction/deletion endpoints, and `MyTimesView` inline adjust dialog.
- **Absence Management & Default Types (Sections 4.1 #6, 6.5, 6.5.1, 6.6, 9.4, 10.1, 13.2):** Preconfigured 10 standard absence types bootstrapped in `Model.xml` (`VACATION`, `ILLNESS`, `ACCIDENT`, `MILITARY_CIVIL_DEFENSE`, `DOCTOR_APPOINTMENT`, `TRAINING`, `PARENTAL_LEAVE`, `UNPAID_LEAVE`, `OVERTIME_COMPENSATION`, `OTHER`), `commentRequired` and `visibleOnPublicStatus` metadata, comment enforcement, duration type validation, and full `DRAFT` status / explicit submission and cancellation workflow.
- **Vacation Journal Immutability & Year-End Carry-Over (Sections 6.7, 6.7.1, 7.5, 11.3):** Automatic calculation and crediting of pro-rated annual vacation entitlement on employee creation; automated `CORRECTION` entries upon schedule employment rate or `exitDate` updates while preserving record immutability; automated year-end carry-over service transferring unexpired balances as `CARRY_OVER` entries with FIFO consumption.
- **Period Calculation Snapshots & Balance Carry-Forward (Sections 6.9, 11.2, 11.6.2):** Month summaries return immutable `calculationSnapshot` for approved and locked periods; `initialBalance` accurately carries forward prior month closing balance; monthly summary categorizes paid absences, unpaid absences, vacation usage, and holiday credits; supervisor period approval inspection endpoint (`GET /approvals/periods/{id}`) and modal in `ApprovalsView` providing full monthly drill-down inspection and approval/rejection actions.
- **Reporting & Exports (Sections 11.1–11.5, 12.1–12.2, 13.8, 17, 18.6):** Core calculation engines, Web UI report viewers, deterministic RFC 4180 CSV exports, and server-side native OpenPDF export generator with streaming REST endpoints (`/reports/month`, `/reports/vacation`, `/reports/absences`).
- **Presence, Audit & System Operations (Sections 8, 11, 12, 13.2, 13.6, 19, 20):** Binary presence indicators with privacy masking, comprehensive audit trail recording entity lifecycle events and retention purge service (`AuditEventSearch`, `AuditLogsResource`), health/readiness probes, and structured logging.
- **Audit-Log Web UI & Detailed Inspection (Sections 4.1 #13, 6.10, 9.10, 12.1 #8, 12.8, 19.3, 20 #16):** Full administration navigation view (`AuditLogView.js`), REST API client (`AuditLogApi.js`), multi-field filtering (`from`, `to`, `entityType`, `entityId`, `username`, `action`), responsive paginated table, detail inspection modal rendering correlation IDs, before/after snapshot values, reasons, and parameters, and complete German (Swiss German) and English translations.
- **User Management for Pure Users & System User Protection (Sections 3.6, 6.1.1, 9.7, 10.5, 12.1 #8, 13.2):** Pure Strolch user lifecycle services (`CreateUserService`, `UpdateUserService`, `InitiateUserRegistrationService`), REST API (`/admin/users`, `/admin/users/{id}/register`), `UsersView` in Web UI supporting role assignment (Admin, HR, Supervisor, Employee) and password initialization challenges; system users (`UserState.SYSTEM`) are protected and excluded from administration views and challenge initiation.
- **Non-Destructive User Deletion and Employee Deactivation (Sections 6.1, 6.1.1, 9.8, 10.5, 13.2, 20 #15):** Non-destructive user deletion service (`RemoveUserService`), REST API (`DELETE /admin/users/{id}`), UI deletion action with confirmation dialogs in `UsersView.js`; deleting a user linked to an employee sets `Employee.active = false` without deleting any historical bookings (`WorkDay`, `WorkEntry`, `Absence`, `VacationAccountEntry`, `TimePeriod`, `EmploymentSchedule`); physical deletion of employees with historical bookings is blocked in `RemoveEmployeeService`; all user deletions and employee deactivations are recorded in the immutable audit log.
- **Employee Reactivation Workflow (Sections 6.1, 6.1.1, 9.9, 13.2):** Employee reactivation service (`ReactivateEmployeeService`), REST API (`POST /admin/employees/{id}/reactivate`), UI reactivation action with confirmation dialogs in `EmployeesView.js`; reactivates inactive employees (`active = true`), re-creates/restores associated Strolch user accounts with appropriate role assignments, allows initiating password setup challenges (`Usage.SET_PASSWORD`), and records reactivation in the audit log.
- **Navigation & Header Logout Integration (Section 12.1 #0):** Standalone logout button removed from the main header action bar and integrated into the user dropdown info menu with icon and dedicated styling; automatically closes open dropdown menus on logout and triggers clean session termination.
- **Reports & CSV Export – Hierarchical Employee Selection and Month Date Picker (Sections 11.4, 12.1 #7):** Hierarchical team-first employee selection (team dropdown filters employee dropdown with full employee names and personal numbers) for authorized roles (Supervisor, HR, Admin), HTML5 date/month picker controls for Day, Month, Vacation, Team, and Absence reports, team selection dropdown for Team Monthly Overview, and complete Swiss German and English translations with 100% key parity.
- **Localization & Branding (Sections 4.2, 6.11, 12.3, 16, 18, 18.5):** Global company branding (name/logo), default language configuration, client-side i18n engine with German (Swiss German) and English translations across all views, and automated key parity verification.
- **Vacation Overview – Vacation Initialization on Reactivation, Display Info, and Booking Type Localization (Sections 6.7, 9.9, 11.3, 12.1 #4):** Verified and automated annual vacation entitlement initialization upon employee reactivation (`ReactivateEmployeeService`); user-friendly employee identification display (username and personnel number) across vacation DTOs and web views; booking type formatting and defensive localization fallback preventing `enums.vacationEntryType.undefined` across vacation overview tables and reports.
- **Team Monthly Overview – Role-Based Visibility, Team Dropdown, and Date Picker (Sections 11.4, 12.1 #7):** Team Monthly Overview visibility restricted to authorized supervisory and administrative roles (Supervisor, HR, Admin) and hidden from employee-only users; team selection via dropdown and report period selection via date/month picker; client and server-side authorization checks and comprehensive UI test coverage.

---

## Incomplete Requirements / Active Backlog Tasks

The following prioritized backlog task (Task 5) addresses recently identified usability issues and UI layout improvements:

- **Task 5: System Configuration – Company Logo Image Upload and Settings Layout (Sections 6.11, 12.1 #8):** Support image file upload (storage and serving) for company logo; center the settings container layout and position descriptions below titles.

---

## Specification Ambiguities Clarified

1. **Working Location Half-Day Split Boundary (Sections 6.4 & 6.4.1):** Cutoff between `MORNING` and `AFTERNOON` defined as 12:30 (or schedule midpoint).
2. **REST Route Naming Discrepancies (Sections 13.2 & 13.8):** Query parameters (`?format=pdf`), `Accept` headers, and route aliases are supported.
3. **Negative Vacation Balances (Section 6.7.1 Rule 13):** Strictly prohibited; approvals exceeding available entitlement are blocked.
4. **Mid-Month Employee Starts & Target Hours (Sections 6.2, 7.1):** Target minutes for days prior to `entryDate` are 0; calendar renders pre-entry days as inactive without missing booking warnings.
5. **Vacation Granting Timing (Section 6.7.1):** Full entitlement is granted upfront per January 1st (or pro-rated per `entryDate`) as `ENTITLEMENT` journal records; schedule changes post `CORRECTION` adjustments.
6. **REST Administrative Endpoints URI Structure (Sections 13.1 & 13.2):** Administrative endpoints are grouped under `/rest/chronivaro/v1/admin/*`.
7. **Employee Inactivation vs Physical Deletion (Sections 6.1, 9.8, 10.5, 13.2):** Employee records are never physically deleted; user deletion performs soft employee deactivation.

---

## Verification Basis

- Comprehensive repository inspection across `chronivaro-core`, `chronivaro-rest`, `chronivaro-web`, `chronivaro-app`, configuration templates, and documentation.
- All existing reactor test suites passing cleanly (`mvn clean test`).
