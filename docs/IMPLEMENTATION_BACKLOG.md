# Chronivaro – Implementation Backlog

Audit basis: 2026-08-28. `IMPLEMENTATION_SPECIFICATION.md` is authoritative for requirements; the repository is authoritative for implementation status.

See [IMPLEMENTATION_STATUS.md](IMPLEMENTATION_STATUS.md) for the summary classification.

Use `docs/IMPLEMENTATION_SPECIFICATION.md` as the authoritative source for required behaviour.

The repository represents the current implementation. The backlog below represents the prioritized, actionable tasks for all missing and partially implemented requirements identified during repository audit.

---

## Working Rules

Implement **one numbered backlog task at a time**.

Before making changes:

1. Read the relevant part of the specification.
2. Inspect the existing implementation.
3. Verify that the backlog task is actually missing.
4. Identify existing project patterns that should be reused.
5. Identify dependencies on unfinished backlog tasks.
6. Check whether the required behaviour already exists in another form.

If a task is already fully implemented, mark it as completed and document where it is implemented instead of rewriting it.

If a task is partially implemented, update or split the backlog item so that only the genuinely missing behaviour remains.

### Source of Truth

The specification defines the required behaviour.

The repository defines the current implementation.

The backlog does **not** define requirements; it records the prioritized, actionable tasks for missing and incomplete work.

Always verify a backlog item against both the specification and the repository before changing code.

Do not introduce requirements that are not present in the specification.

Implementation details such as libraries, application servers, deployment mechanisms, or internal architecture are not requirements unless explicitly specified. Prefer the existing architecture and established project patterns unless a backlog task specifically requires an architectural change.

### Scope

Implement exactly **one logical task at a time**.

Do not perform unrelated refactoring.

Do not automatically expand the current task when additional missing functionality is discovered. Add the newly discovered work to the backlog instead.

Use the existing module names from the repository (`chronivaro-core`, `chronivaro-rest`, `chronivaro-web`, `chronivaro-app`). Do not invent or rename modules unless explicitly required by the specification or backlog task.

### Task Size

If a task:

- requires changes to more than approximately 8–10 production files, or
- spans multiple unrelated concepts,

stop before implementation and split it into smaller numbered backlog tasks.

The file-count threshold is a heuristic. A cohesive change may legitimately touch several files, but a task should remain understandable, reviewable, and independently verifiable.

### Implementation

Inspect the existing implementation before creating new abstractions.

Reuse established project patterns.

Do not duplicate functionality that already exists.

Enforce business rules and authorisation in the appropriate Core/domain layer where applicable. REST-layer or UI-layer checks alone are insufficient.

### Strolch Privilege Enforcement

- **Services:** Strolch automatically asserts the privilege for a service when it is invoked through the `ServiceHandler`. Do not add `tx.assertHasPrivilege(getClass().getName())` to `internalDoService`. Instead, ensure that the service is permitted for the appropriate role in `PrivilegeRoles.xml`.
- **Searches:** `StrolchSearch` automatically performs the corresponding privilege assertion for the current user.
- **Data access:** Use `tx.assertHasPrivilege(operation, element)` when data-level authorisation is required, for example when determining whether a user may modify a specific employee's data.

### Tests and Completion

Add or update tests for every behavioural change.

Run the relevant tests after implementation.

All changed code must compile and all affected tests must pass before a task is marked complete.

When running tests, only run them in the Chronivaro directory, not its parent.

After completing or reclassifying a task:

1. Update the backlog.
2. Update `IMPLEMENTATION_STATUS.md` where applicable.
3. Record any newly discovered missing functionality as separate backlog tasks.
4. Stop.

Do not continue with the next numbered task automatically.

---

## Specification Clarifications & Ambiguities

### A. Working Location Half-Day Split Boundary (Sections 6.4 & 6.4.1)
- **Clarification:** The specification requires that a workday may have at most one working location in the morning (`MORNING`) and one in the afternoon (`AFTERNOON`). The boundary between morning and afternoon is defined as 12:30 (or the midpoint of scheduled daily hours if explicitly calculated). Bookings spanning across morning and afternoon must be split into separate entries if the working location changes.

### B. REST Content Negotiation for Reports (Sections 13.2 & 13.8)
- **Clarification:** Standard endpoints under `/rest/chronivaro/v1/reports/{type}` support format selection via `?format=json`, `?format=csv`, and `?format=pdf` query parameters, as well as HTTP `Accept` header content negotiation (`application/json`, `text/csv`, `application/pdf`). URL extensions (e.g. `/reports/month.pdf`) are supported as route aliases where required.

### C. Negative Vacation Balances (Section 6.7.1 Rule 13 & Section 22 Item 1)
- **Clarification:** Negative vacation balances remain strictly prohibited per Rule 13. Vacation requests that exceed available credited vacation entitlement must be rejected during approval.

### D. Mid-Month Employee Starts & Target Hours (Sections 6.2, 7.1)
- **Clarification:** Target minutes for days prior to `entryDate` are 0; calendar renders pre-entry days as inactive without missing booking warnings.

### E. Vacation Granting Timing (Section 6.7.1)
- **Clarification:** Full entitlement is granted upfront per January 1st (or pro-rated per `entryDate`) as `ENTITLEMENT` journal records; schedule changes post `CORRECTION` adjustments.

### F. REST Administrative Endpoints URI Structure (Sections 13.1 & 13.2)
- **Clarification:** Administrative endpoints are grouped under `/rest/chronivaro/v1/admin/*` (e.g. `/admin/users`, `/admin/audit-logs`, `/admin/absence-types`, `/admin/locations`, `/admin/holiday-calendars`, `/admin/configuration`) for clear JAX-RS path-based role enforcement and privilege mapping.

### G. Employee Inactivation vs Physical Deletion (Sections 6.1, 9.8, 10.5, 13.2)
- **Clarification:** In accordance with Section 6.1 and 9.8, `Employee` records must never be physically deleted once created in order to maintain historical audit trails, time records, and vacation balances. User deletion for linked employees performs a soft deactivation (`active = false`), and `DELETE /employees/{id}` is treated as soft deactivation. Full reactivation is handled via `POST /employees/{id}/reactivate`.

### H. Work Entry Modifications, Highlighting, and Creator Attribution (Sections 3.1–3.3, 6.4, 9.3, 11, 12.1 #2, 13.2, 20)
- **Clarification:** Employees can modify their own work entries (start time, end time, working location, comment) within open/unlocked periods. Supervisors (for assigned team members) and HR / Administrators (organization-wide) must be able to view, manually create (`POST`), fully edit (`PUT` start, end, location, comment), and delete (`DELETE`) work entries for employees within open/unlocked periods. All supervisor/HR and employee modifications generate structured, immutable audit log events recording old/new values, reasons, and the acting user.
- **Highlighting & Creator Attribution:** All modified (subsequently edited) and manually created work entries (`source = MANUAL` or modified status) must be visually highlighted in the UI (e.g. badges or distinct styling in Day, Week, and Month views, as well as Approvals inspection and Reports). If an entry was not created by the employee (e.g. manually added by a supervisor or HR/Administrator), the UI and reports must explicitly display by whom (`createdBy`) it was created.

---

## Verified Implementation Baseline

The following foundational areas are verified as fully implemented in the repository:

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
- **System Configuration – Company Logo Image Upload and Settings Layout (Sections 6.11, 12.1 #8):** Image file upload (PNG/JPEG/SVG/GIF/WebP data URI validation, Base64 size limits, and dedicated upload/delete/serve endpoints), image preview and removal controls in `ConfigurationView.js`, centered settings container layout (`.configuration-container`), description texts formatted directly below section titles, and full test coverage across core, rest, and web modules.
- **HR and Supervisor Employee Work Entry Management (Sections 3.2, 3.3, 6.4, 9.3, 12.1 #2, 13.2, 20):** Core services (`AddWorkEntryService`, `CorrectWorkEntryService`, `RemoveWorkEntryService`) extended to allow supervisors acting on assigned team members (`assertCanManageEmployee`) and HR/Admins organization-wide; REST endpoints (`GET /employees/{id}/work-entries`, `POST /employees/{id}/work-entries`, `PUT /admin/work-entries/{id}`, `DELETE /admin/work-entries/{id}`) with role-based scoping; Web UI (`MyTimesView.js` and `WorkEntryApi.js`) with team/employee dropdowns for managers, add/edit/delete modals, and complete Swiss German and English translations with 100% key parity.
- **Work Entry Employee Modifications, Highlighting of Modified/Manual Entries, and Creator Attribution (Sections 3.1–3.3, 4.1 #5, 6.4, 9.3, 11, 12.1 #2, 13.2, 20 #3):** Enabled full work entry editing for employees in open periods via `CorrectWorkEntryService` and `PUT /me/work-entries/{id}` with same-day and non-overlap validations; exposed `source`, `createdBy`, and `modified` in `WorkEntryDto`, `WorkEntryRangeDto`, and `WorkEntryRange`; visual badges/highlighting for manual and modified entries across `MyTimesView.js`, `ApprovalsView.js`, and `ReportsView.js`; creator attribution (`createdBy`) displayed whenever entries are created on behalf of the employee; complete Swiss German and English translations with 100% key parity and comprehensive unit/REST/UI tests.
- **Employee Self-Profile API & View (Sections 3.1, 6.1, 12.1 #0, 13.2, 20 #2):** REST endpoints `GET /rest/chronivaro/v1/me/profile` returning linked `EmployeeDto` with resolved team, location, timezone, join/exit dates, active status, and `GET /rest/chronivaro/v1/me/schedules` returning employment schedule history; web UI `ProfileView.js` (`#profile`) accessible from the top user dropdown menu displaying user account details, role badges, employee master data, current schedule breakdown with daily target hours, workload percentage calculation, and schedule version history; complete Swiss German and English translations with 100% key parity and automated REST/UI tests.
- **User Language Persistence & Frontend Sync (Sections 4.2.1, 4.2.2, 14.1, 18.5, 20.1 #4):** Implemented `UpdateUserLanguageService` in `chronivaro-core` and REST endpoint `POST /rest/chronivaro/v1/auth/language` in `chronivaro-rest` allowing authenticated users to persistently update their language on their Strolch `UserRep`; wired `I18n.js` and `LoginView.js` in `chronivaro-web` to synchronize explicit language choices directly to the backend profile while retaining local browser storage caching; verified with comprehensive unit, REST, and UI tests.
- **Employee Self-Service Work Entry Deletion (Sections 14.2, 15.2, 20 #4):** Core service `RemoveWorkEntryService` updated to allow employees deleting their own work entries in open periods (`isSelf`); REST endpoint `DELETE /rest/chronivaro/v1/me/work-entries/{id}` implemented in `ChronivaroResource` with optimistic concurrency validation via `If-Match`; `WorkEntryApi.js` updated with `deleteWorkEntry` / `deleteMyWorkEntry`; `MyTimesView.js` wired with deletion action buttons and confirmation dialog for employees on their work entries; `PrivilegeRoles.xml` updated across all runtime and test configurations; covered by unit tests in `WorkEntryServiceTest`, REST integration tests in `ChronivaroResourceTest`, and web asset tests in `WebWorkEntryModificationUiTest`.
- **Vacation Report Metric Initialization & Zero Defaulting for New Employees (Sections 6.7, 11.3):** Guarded against null, uninitialized, or missing balance fields in vacation summaries, DTO mapping, CSV and PDF report export helpers, and web report rendering (`ReportsView.js`); ensured all metrics default to clean numeric zeros (0 days / 0:00 h) without displaying undefined text; covered by comprehensive core, REST, and UI test suites.
- **Enhanced Onboarding Registration Email with Direct URL and Configurable Server Base URL (Sections 6.11, 9.6, 12.1 #8):** Added `serverBaseUrl` parameter to global configuration model (`ChronivaroConstants`, `Templates.xml`, `Model.xml`), DTOs, and REST API (`ConfigurationResource`); implemented `ChronivaroUserChallengeHandler` providing friendly, localized onboarding emails (German/English) containing personalized greetings, direct registration links (`${serverBaseUrl}/#complete-registration?user=${username}&token=${token}`), and fallback registration codes; updated `CompleteRegistrationView.js` to auto-populate username and challenge token from URL query parameters; added `serverBaseUrl` field, validation, hint, and translations in `ConfigurationView.js` and `i18n` dictionaries; covered by comprehensive core, REST, and UI test suites.
- **Report Summaries Horizontal Single-Row Layout (Sections 11.1–11.5, 12.1 #7):** Summary metrics/cards across all report views (`DayReport`, `MonthReport`, `VacationReport`, `TeamReport`, `AbsenceReport` in `ReportsView.js` as well as `ApprovalsView.js` period inspection) styled with single flexbox row layout (`.summary-grid`, `.report-summary-grid` with `display: flex; flex-direction: row; flex-wrap: wrap; gap: 1rem;` and `.summary-card` flex growth with min-width), preventing vertical column stacking and ensuring consistent single-row layout with responsive wrapping; verified with automated UI tests in `WebReportsUiTest`.
- **Multi-Column / Row Layout with Spacing for Presence / Who Is Working Dashboard (Sections 8, 12.1 #5):** Updated `PresenceView.js` to render employee cards in dedicated `.presence-cards-grid` containers within each team group, and configured responsive flex-row wrapping layout (`display: flex; flex-direction: row; flex-wrap: wrap; gap: 1.5rem;`) and distinct card dimensions/margins (`flex: 1 1 280px; min-width: 260px; max-width: 380px;`) in `style.css`; verified with automated UI tests in `WebPresenceUiTest.java`.
- **Entity Deletion and Action Confirmation Dialogs with Human-Readable Names (Section 12.2):** Updated confirmation dialog prompts across admin views (`TeamsView.js`, `LocationsView.js`, `AbsenceTypesView.js`, `EmployeesView.js`, `ScheduleTemplatesView.js`, `HolidayCalendarsView.js`, `UsersView.js`) to look up and format human-readable entity names (`name`, `firstname lastname`, `username`) instead of technical IDs; updated German (Swiss German) and English translations with `{name}` placeholders and 100% key parity; covered by automated UI tests in `WebConfirmationDialogsUiTest.java`.
- **Modal Dialog Scrolling & Viewport Overflow Handling for All Dialogs (Section 12.2):** Configured modal dialog containers (`.modal-content` with `max-height: 90vh; overflow-y: auto;` in `style.css` and `.notification-dialog-body` with `overflow-y: auto;`) ensuring all input dialogs (such as Add Employee, Edit Schedule, and all administration/time-tracking modals) scroll vertically within short browser viewports while keeping all form controls and action buttons accessible; verified with automated UI tests in `WebModalDialogsUiTest.java`.
- **Presence Dashboard – Forgotten Timer Indicator, Uniform Card Width, and Name Truncation (Sections 8, 12.1 #5):** Added backend detection of forgotten timers from previous days (`isPreviousDayTimer`, `timerStartDate`) in `PresenceService` and `PresenceDto`; updated `PresenceView.js` to render warning indicators (`.timer-warning-icon`, `status-danger`) with interactive notification tooltips consistent with the personal dashboard; styled all presence cards with fixed, uniform width (`flex: 0 0 280px; width: 280px; max-width: 280px;`) and added text ellipsis truncation with tooltip title attributes for long employee names; verified with unit tests in `PresenceServiceTest` and UI tests in `WebPresenceUiTest`.

---

## Prioritized Implementation Backlog

### Task 1: Supervisor and HR Work Time Editing – Midnight / Next-Day End Time Support
- **Priority:** High / Bug Fix & Enhancement
- **Scope:** `chronivaro-core`, `chronivaro-rest`, `chronivaro-web`
- **Description:**
  - When a supervisor or HR edits an employee's work time, add an option to indicate that the employee worked past midnight.
  - In the editing dialog, display the end date as the next day and allow setting the end time on that next day.
  - Core service must handle automatic splitting at 24:00 across the respective `WorkDay` records.

---

### Task 2: Date Format Setting to DD-MM-YYYY
- **Priority:** Medium / Bug Fix
- **Scope:** `chronivaro-web` (and report generators where applicable)
- **Description:**
  - Ensure date formatting in the UI is consistently set to `DD-MM-YYYY` (e.g. European/Swiss standard display) rather than `MM-DD-YYYY`.
  - Verify that date inputs, table columns, card displays, and tooltips follow `DD-MM-YYYY`.

---

### Task 3: Vacation Recalculation Comments and Enrollment Guard
- **Priority:** High / Bug Fix & Investigation
- **Scope:** `chronivaro-core`, `chronivaro-rest`, `chronivaro-web`
- **Description:**
  - Investigate why an employee enrolled on 01.01.2026 with a global 25-day entitlement showed 20d (160h) and why a recalculation happened at the same time an employee was enrolled.
  - Prevent concurrent/redundant recalculations during employee enrollment so that initial entitlement is cleanly recorded as `ENTITLEMENT`.
  - Add descriptive, detailed comments to all automated vacation recalculations and corrections explaining why the recalculation occurred.

---

### Task 4: Vacation Journal Created Date Column
- **Priority:** Medium / Bug Fix
- **Scope:** `chronivaro-core`, `chronivaro-rest`, `chronivaro-web`
- **Description:**
  - Vacation Journal Transactions currently display two date columns (e.g. effective date / period) but lack the timestamp of when the journal entry was created (`createdAt`).
  - Add `createdAt` to vacation journal models, DTOs, API responses, and the Vacation view table in the frontend.

---

### Task 5: Manual Vacation Correction Entry for HR and Supervisors
- **Priority:** High / Feature / Bug Fix
- **Scope:** `chronivaro-core`, `chronivaro-rest`, `chronivaro-web`
- **Description:**
  - Allow HR and Supervisors to add a manual vacation correction entry (`CORRECTION`) with positive or negative number of days/hours.
  - Require a mandatory comment explaining the reason for the correction.
  - Ensure the correction is saved as an immutable journal entry with author attribution and audit logging.

---

### Task 6: On-Call Periods Management & Office Hours (Rufbereitschaft / Pikettdienst)
- **Priority:** High / New Feature Request
- **Scope:** `chronivaro-core`, `chronivaro-rest`, `chronivaro-web`
- **Description:**
  - **Data Model & Configuration:** Support configurable on-call periods (`OnCallPeriod`) for employees (e.g. whole week, weekend) managed by HR/Supervisors.
  - **Office Hours Concept:** Introduce global/configurable office hours to delineate standard business hours from off-duty hours.
  - **Timer & Work Entry On-Call Flagging:** When an employee works outside office hours during an active on-call period, allow the employee (when stopping timer) or supervisor/HR (when editing work entry) to specify whether the work was on-call duty (`isOnCall = true`) or regular overtime.
  - **Reports & Summaries:**
    - Display on-call active indicators/icons on Day and Month reports.
    - Separately calculate and display work entry summaries for off-duty / on-call times.
    - Provide a dedicated On-Call Report (configured periods + on-call work entries).

---

## Explicitly Excluded / Post-MVP Out of Scope

The following items are defined in Section 4.3 as post-MVP expansions and are not part of the active implementation backlog:

- Project, customer, order, or activity tracking
- Night, weekend, or holiday wage surcharges
- Native Excel (.xlsx) export
- Absence attachments and medical certificate uploads
- Automated notifications and email reminders
- Calendar integration (iCal/Exchange)
- Legacy time-tracking system import utilities
- Offline-capable mobile client
