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
- **User Management for Pure Users & System User Protection (Sections 3.6, 6.1.1, 9.7, 10.5, 12.1 #8, 13.2):** Pure Strolch user lifecycle services (`CreateUserService`, `UpdateUserService`, `InitiateUserRegistrationService`), REST API (`/admin/users`, `/admin/users/{id}/register`), `UsersView` in Web UI supporting role assignment (Admin, HR, Supervisor, Employee) and password initialization challenges; system users (`UserState.SYSTEM`) are protected and excluded from administration views and challenge initiation.
- **Localization & Branding (Sections 4.2, 6.11, 12.3, 16, 18, 18.5):** Global company branding (name/logo), default language configuration, client-side i18n engine with German (Swiss German) and English translations across all views, and automated key parity verification.

---

## Prioritized Implementation Backlog

### Task 1: Audit Log UI, Navigation, Filter Controls, and Detail Modal
- **Classification:** `PARTIALLY_IMPLEMENTED`
- **Specification Reference:**
  - Section 4.1, Item 13 (*Audit-Log einsehen*)
  - Section 6.10 (*AuditLog*)
  - Section 9.10 (*Einsichtnahme in das Audit-Log*)
  - Section 12.1, Item 8 (*Audit-Log-Ansicht*)
  - Section 12.8 (*Administration – Audit-Log*)
  - Section 19.3 (*Lieferobjekte – Web-Oberfläche*)
  - Section 20, Item 16 (*Fachliche Akzeptanzkriterien*)
- **Current Implementation Location:**
  - `chronivaro-core`: `ch.atexxi.chronivaro.core.search.AuditEventSearch`
  - `chronivaro-rest`: `ch.atexxi.chronivaro.rest.resource.AuditLogsResource` (`GET /rest/chronivaro/v1/admin/audit-logs`)
  - `chronivaro-rest`: `ch.atexxi.chronivaro.rest.dto.AuditLogDto`
  - `chronivaro-web`: Missing UI view and navigation entry
- **Missing Behaviour:**
  - `chronivaro-web` lacks a dedicated view (`AuditLogView.js`), an administration navigation entry in `index.html`, and routing in `app.js`.
  - Filter controls for date range (`from`, `to`), `entityType`, `entityId`, `username`, and `action` need to be implemented.
  - A paginated table displaying audit entries (timestamp, user, action, entity type, entity ID, summary) is missing.
  - A detail inspection modal showing before/after property changes, justification, and correlation ID is missing.
  - Internationalization keys (DE/EN) for audit log elements need to be added to `locales/de.json` and `locales/en.json`.
- **Scope & Modules:**
  - `chronivaro-web`: `js/pages/AuditLogView.js`, `js/api/AuditLogApi.js`, `index.html`, `js/app.js`, `locales/de.json`, `locales/en.json`.
- **Dependencies:**
  - `AuditLogsResource` (already implemented and available at `/rest/chronivaro/v1/admin/audit-logs`).
- **Acceptance Criteria:**
  1. An "Audit Log" navigation item is available in the Administration menu for users with the `Administrator` role.
  2. The view provides filter inputs for `from` date, `to` date, `entityType`, `entityId`, `username`, and `action`.
  3. Query results are rendered in a responsive, paginated table showing timestamp, actor username, action, entity type, entity ID, and summary.
  4. Clicking on a row or detail button opens a modal displaying full audit details including correlation ID, client IP, before/after snapshots, and change justification.
  5. All UI labels, table headers, filter placeholders, and modal texts are fully localized in German and English.

---

### Task 2: Non-Destructive User Deletion and Employee Deactivation
- **Classification:** `PARTIALLY_IMPLEMENTED`
- **Specification Reference:**
  - Section 6.1 (*Employee – Regeln*)
  - Section 6.1.1 (*Verknüpfung zwischen Strolch-Benutzern und Mitarbeitern*)
  - Section 9.8 (*Löschen von Benutzern und Mitarbeiterdeaktivierung*)
  - Section 10.5 (*Datensicherheit und Zugriffsschutz*)
  - Section 13.2 (*Administration Endpunkte: DELETE /users/{id}*)
  - Section 20, Item 15 (*Fachliche Akzeptanzkriterien*)
- **Current Implementation Location:**
  - `chronivaro-core`: `ch.atexxi.chronivaro.core.service.RemoveEmployeeService`
  - `chronivaro-rest`: `ch.atexxi.chronivaro.rest.resource.UserResource`, `ch.atexxi.chronivaro.rest.resource.EmployeeResource`
  - `chronivaro-web`: `js/pages/UsersView.js`, `js/pages/EmployeesView.js`
- **Missing Behaviour:**
  - `UserResource` lacks a `DELETE /rest/chronivaro/v1/admin/users/{id}` endpoint.
  - `RemoveEmployeeService` currently physically cascade-deletes the `Employee` resource and all associated child entities (`WorkDay`, `WorkEntry`, `Absence`, `VacationAccountEntry`, `TimePeriod`, `EmploymentSchedule`).
  - Required behaviour per Sections 6.1 & 9.8: `Employee` resources must never be physically deleted. Deleting a user linked to an employee must remove the Strolch user login while setting `Employee.active = false` and retaining all historical records. Deleting a pure user simply removes the Strolch user account.
  - `RemoveEmployeeService` must prevent physical deletion when historical data exists or perform soft deactivation (`active = false`).
  - User deletion and employee deactivation must be recorded in the audit log (`AUDIT_ACTION_REMOVE`, `AUDIT_ACTION_DEACTIVATE`).
- **Scope & Modules:**
  - `chronivaro-core`: `RemoveUserService` (or updated `RemoveEmployeeService`), audit logging.
  - `chronivaro-rest`: `UserResource` (`DELETE /admin/users/{id}`), `EmployeeResource`.
  - `chronivaro-web`: User deletion action in `UsersView.js` and `UserApi.js`, status display.
  - Tests: `UserServiceTest`, `UserResourceTest`, `EmployeeResourceTest`.
- **Dependencies:**
  - `PrivilegeHandler`, `ChronivaroAuditHelper`.
- **Acceptance Criteria:**
  1. `DELETE /rest/chronivaro/v1/admin/users/{id}` removes a pure Strolch user account.
  2. If the deleted user is linked to an `Employee`, the Strolch user account is deleted and the linked `Employee` resource is marked as `active = false` without deleting any historical bookings (`WorkDay`, `WorkEntry`, `Absence`, `VacationAccountEntry`, `TimePeriod`, `EmploymentSchedule`).
  3. Physical deletion of employees with existing historical records is blocked or converted to soft deactivation.
  4. Actions are logged to the Audit Log with appropriate action types (`AUDIT_ACTION_REMOVE`, `AUDIT_ACTION_DEACTIVATE`).
  5. The Users and Employees administration views reflect the changes and deactivated states.

---

### Task 3: Employee Reactivation Workflow
- **Classification:** `MISSING`
- **Specification Reference:**
  - Section 6.1 (*Employee – Regeln*)
  - Section 6.1.1 (*Verknüpfung zwischen Strolch-Benutzern und Mitarbeitern*)
  - Section 9.9 (*Reaktivierung von Mitarbeitern*)
  - Section 13.2 (*Administration: POST /employees/{id}/reactivate*)
- **Current Implementation Location:**
  - No service, REST endpoint, or UI button currently exists.
- **Missing Behaviour:**
  - Missing `ReactivateEmployeeService` in `chronivaro-core`.
  - Missing `POST /rest/chronivaro/v1/admin/employees/{id}/reactivate` in `EmployeeResource`.
  - Missing "Reactivate" action button in `EmployeesView.js` for inactive employees.
  - When reactivated: `Employee.active` is set to `true`, a new Strolch user account is recreated with the employee's username, email, name, and default role (`Employee`), and an administrator can initiate a password registration challenge (`Usage.SET_PASSWORD`).
  - Reactivation is recorded in the Audit Log (`AUDIT_ACTION_UPDATE` / `AUDIT_ACTION_ACTIVATE`).
- **Scope & Modules:**
  - `chronivaro-core`: `ReactivateEmployeeService`, `ReactivateEmployeeCommand`.
  - `chronivaro-rest`: `EmployeeResource` (`POST /admin/employees/{id}/reactivate`).
  - `chronivaro-web`: `EmployeesView.js`, `EmployeeApi.js`, i18n locales.
  - Tests: Unit and REST integration tests in `chronivaro-core` and `chronivaro-rest`.
- **Dependencies:**
  - Task 2 (Non-Destructive User Deletion and Employee Deactivation), `PrivilegeHandler`, `InitiateUserRegistrationService`.
- **Acceptance Criteria:**
  1. `POST /rest/chronivaro/v1/admin/employees/{id}/reactivate` successfully reactivates an inactive employee (`active = true`).
  2. A new Strolch user is created for the reactivated employee with appropriate role assignments.
  3. A password setup challenge can be initiated for the reactivated user.
  4. Reactivation is logged in the Audit Log.
  5. `EmployeesView.js` displays a "Reactivate" button for inactive employees and updates the employee's status upon success.

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
