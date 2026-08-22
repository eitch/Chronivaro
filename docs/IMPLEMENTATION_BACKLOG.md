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
- **Presence, Audit & System Operations (Sections 8, 11, 12, 13.2, 13.6, 19, 20):** Binary presence indicators with privacy masking, comprehensive audit trail recording entity lifecycle events and retention purge service, health/readiness probes, and structured logging.
- **User Management for Pure System Users (Sections 3.6, 6.1.1, 9.7, 12.1 #8, 13.2):** Pure Strolch user lifecycle services (`CreateUserService`, `UpdateUserService`, `InitiateUserRegistrationService`), REST API (`/admin/users`, `/admin/users/{id}/register`), and `UsersView` in Web UI supporting role assignment (Admin, HR, Supervisor, Employee) and password initialization challenge for non-employee users.
- **Localization & Branding (Sections 4.2, 6.11, 12.3, 16, 18, 18.5):** Global company branding (name/logo), default language configuration, client-side i18n engine with German (Swiss German) and English translations across all views, and automated key parity verification.

---

## Prioritized Implementation Backlog

All prioritized backlog tasks (Tasks 9 through 13) have been fully implemented, tested, and verified. No open or pending implementation tasks remain for the current milestone.

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
