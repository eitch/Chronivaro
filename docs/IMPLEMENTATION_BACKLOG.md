# Chronivaro – Implementation Backlog

Audit basis: 2026-08-21. `IMPLEMENTATION_SPECIFICATION.md` is authoritative for requirements; the repository is authoritative for implementation status.

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
- **Localization & Branding (Sections 4.2, 6.11, 12.3, 16, 18, 18.5):** Global company branding (name/logo), default language configuration, client-side i18n engine with German (Swiss German) and English translations across all views, and automated key parity verification.

---

## Prioritized Implementation Backlog

### Task 2: Reports & CSV Export – Hierarchical Employee Selection and Month Date Picker

- **Goal:** Replace manual text inputs in reports and CSV export with intuitive selection controls (team-first employee selection and date pickers).
- **Scope:**
  - Update `ReportsView.js` / report filter components to allow authorized roles (Supervisor, HR, Admin) to filter employees hierarchically: first select a team from a dropdown, then select an employee within that team from a dropdown (instead of manually entering an employee ID).
  - Add date/month picker controls for the Month Report (replacing manual text field input of date/period).
  - Retain existing query parameter bindings and CSV/PDF export API compatibility.
  - Update relevant frontend tests.

---

### Task 3: Vacation Overview – Vacation Initialization on Reactivation, Display Info, and Booking Type Localization

- **Goal:** Resolve missing/undefined vacation account values, display user-friendly identification, and fix booking type rendering in the Vacation Overview.
- **Scope:**
  - **Vacation Account Initialization:** Ensure `ReactivateEmployeeService` (and any related employee initialization paths in `chronivaro-core`) verifies and initializes the vacation account / entitlement entries for the active year upon reactivation so that reactivated employees do not have undefined/empty balances.
  - **Employee Identification:** Update the vacation overview page (`VacationView.js`) and DTOs to display employee username and/or personnel number rather than raw internal UUID/ID.
  - **Booking Type Formatting & Localization:** Fix the booking type display in the vacation account journal table so it renders properly localized labels (e.g. `Entitlement`, `Carry Over`, `Usage`, `Correction`, `Expiry`) instead of falling back to missing keys like `enums.vacationEntryType.undefined`. Ensure defensive handling for null/undefined entry types in both web frontend and i18n bundles (`de.json`, `en.json`).
  - Add/update unit and integration tests in `chronivaro-core` and `chronivaro-web`.

---

### Task 4: Team Monthly Overview – Role-Based Visibility, Team Dropdown, and Date Picker

- **Goal:** Restrict Team Monthly Overview to authorized roles and replace manual ID/date text inputs with dropdown and date picker controls.
- **Scope:**
  - **Access Restriction:** Ensure the Team Monthly Overview section/view is only visible and accessible to roles with appropriate supervisory/administrative permissions (Supervisor, HR, Admin), and hidden for users holding only the `Employee` role.
  - **Team Selection Dropdown:** Replace the manual team ID text input with a searchable/selectable dropdown of available teams.
  - **Date Picker:** Replace the text input for the report date/month with a date/month picker.
  - Update authorization checks in REST / UI and add tests verifying role visibility and selection behavior.

---

### Task 5: System Configuration – Company Logo Image Upload and Settings Layout

- **Goal:** Enable image file upload for the company logo in system administration and improve the settings view layout.
- **Scope:**
  - **Logo Image Upload:**
    - Extend configuration management in `chronivaro-core` and `chronivaro-rest` (`ConfigurationResource.java`, `UpdateConfigurationService.java` or dedicated logo upload endpoint) to accept image file uploads (e.g., PNG/JPEG/SVG or Base64 data URI), validate file size and MIME type, and store/serve the company logo.
    - Update `ConfigurationView.js` in `chronivaro-web` to provide an image file upload input with preview and remove options.
  - **Settings Layout Alignment:**
    - Center the settings container (`div`) on the page for a balanced, focused appearance.
    - Position section description texts directly below section titles rather than misaligned side-by-side or unformatted positions.
  - Add backend and frontend tests for logo upload, validation, and configuration retrieval.

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
