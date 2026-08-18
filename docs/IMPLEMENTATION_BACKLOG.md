# Chronivaro – Implementation Backlog

Audit basis: 2026-08-18. `IMPLEMENTATION_SPECIFICATION.md` is authoritative for requirements; the repository is authoritative for implementation status. The previous backlog was historical guidance only.

See [IMPLEMENTATION_STATUS.md](IMPLEMENTATION_STATUS.md) for the current status.

Use `docs/IMPLEMENTATION_SPECIFICATION.md` as the authoritative source for
required behaviour.

The repository represents the current implementation.

The backlog below is a hypothesis about functionality that is still missing.

## Working Rules

Implement **one numbered backlog task at a time**.

Before making changes:

1. Read the relevant part of the specification.
2. Inspect the existing implementation.
3. Verify that the backlog task is actually missing.
4. Identify existing project patterns that should be reused.
5. Identify dependencies on unfinished backlog tasks.
6. Check whether the required behaviour already exists in another form.

If a task is already fully implemented, mark it as completed and document
where it is implemented instead of rewriting it.

If a task is partially implemented, update or split the backlog item so that
only the genuinely missing behaviour remains.

### Source of Truth

The specification defines the required behaviour.

The repository defines the current implementation.

The backlog does **not** define requirements; it records the current hypothesis
about missing work.

Always verify a backlog item against both the specification and the repository
before changing code.

Do not introduce requirements that are not present in the specification.

Implementation details such as libraries, application servers, deployment
mechanisms, or internal architecture are not requirements unless explicitly
specified. Prefer the existing architecture and established project patterns
unless a backlog task specifically requires an architectural change.

### Scope

Implement exactly **one logical task at a time**.

Do not perform unrelated refactoring.

Do not automatically expand the current task when additional missing
functionality is discovered. Add the newly discovered work to the backlog
instead.

Use the existing module names from the repository. Do not invent or rename
modules unless explicitly required by the specification or backlog task.

### Task Size

If a task:

- requires changes to more than approximately 8–10 production files, or
- spans multiple unrelated concepts,

stop before implementation and split it into smaller numbered backlog tasks.

The file-count threshold is a heuristic. A cohesive change may legitimately
touch several files, but a task should remain understandable, reviewable, and
independently verifiable.

### Implementation

Inspect the existing implementation before creating new abstractions.

Reuse established project patterns.

Do not duplicate functionality that already exists.

Enforce business rules and authorisation in the appropriate Core/domain layer
where applicable. REST-layer or UI-layer checks alone are insufficient.

### Strolch Privilege Enforcement

- **Services:** Strolch automatically asserts the privilege for a service when
  it is invoked through the `ServiceHandler`. Do not add
  `tx.assertHasPrivilege(getClass().getName())` to `internalDoService`.
  Instead, ensure that the service is permitted for the appropriate role in
  `PrivilegeRoles.xml`.

- **Searches:** `StrolchSearch` automatically performs the corresponding
  privilege assertion for the current user.

- **Data access:** Use `tx.assertHasPrivilege(operation, element)` when
  data-level authorisation is required, for example when determining whether
  a user may modify a specific employee's data.

### Tests and Completion

Add or update tests for every behavioural change.

Run the relevant tests after implementation.

All changed code must compile and all affected tests must pass before a task
is marked complete.

When running tests, only run them in the Chronivaro directory, not its parent.

After completing or reclassifying a task:

1. Update the backlog.
2. Update `IMPLEMENTATION_STATUS.md` where applicable.
3. Record any newly discovered missing functionality as separate backlog tasks.
4. Stop.

Do not continue with the next numbered task automatically.

## Verified implementation status

### Implemented — no backlog task

- The parent POM, JDK 25, Strolch BOM, and `chronivaro-core`, `chronivaro-rest`, and `chronivaro-web` modules exist in `Chronivaro/pom.xml`.
- Core model and service foundations exist for employees, teams, locations, schedules, holiday calendars, work days/entries, absence types, absences, and periods.
- Historical schedule lookup, schedule overlap prevention, timer lifecycle, midnight splitting, forgotten-timer capping, multiple blocks, and working-location defaults/overrides are implemented and tested.
- Absence self-service/workflow, registration, presence, holiday-calendar CRUD, role configuration, and day/month summaries are implemented in Core, REST, and the existing UI.

### Partial or missing — represented by tasks below

- Period lifecycle lacks rejection, reopening with reason, complete transitions, snapshots, lookup, and employee/approval endpoints.
- Vacation accounting lacks the automated entitlement engine, configuration, usage linkage, and complete reporting/UI; corrections and balance lookup exist. The specification now fixes the calculation rules: 25 annual full-time days, 480 minutes per day, employment/part-time proration, commercial rounding to whole minutes, unlimited carry-over, positive corrections included in carry-over, oldest-balance usage, and no negative vacation balances.
- Audit has basic element/parameter/value/user data but lacks action, reason, correlation ID, complete coverage, and authorized access.
- Administration lacks global configuration REST/UI; UI lacks personal absence/vacation, approvals, reports, and period pages.
- Missing platform concerns include shared REST errors/correlation IDs, concurrency, pagination, OpenAPI, reports/CSV, approval queues, observability, health/readiness, and final acceptance evidence.
- There is no `chronivaro-app`, embedded Jetty, executable artifact, same-server frontend serving, or standalone lifecycle verification.

## Specification decisions required before implementation

### 1. Resolve remaining open product and API decisions

- **Status:** `SPECIFICATION_AMBIGUITY`
- **Specification:** Sections 13.2–13.3, 17.3, and 22. Vacation calculation rules in section 6.7.1 are already decided and are not part of this task.
- **Decide:** vacation absence type ID, approval paths/status codes, approver selection, cancellation and illness rules, overtime carry-over, authentication target, retention, and multi-entity/time-zone scope.
- **Acceptance:** Every remaining value is owner-approved or explicitly deferred; endpoint paths, status codes, and policy keys have no hidden defaults. The decision record does not override the fixed section 6.7.1 vacation rules.
- **Dependencies:** None.

## Dependency-ordered implementation tasks

### 2. Establish shared REST contracts

- **Status:** `MISSING`
- **Scope:** Add common errors with field errors and correlation IDs, ISO date/time rules, pagination helpers, optimistic concurrency, and request correlation propagation.
- **Acceptance:** Existing and new mutable/list endpoints use one documented contract; validation, authorization, stale-write, pagination, and correlation integration tests pass.
- **Dependencies:** Task 1.

### 3. Document the REST contract

- **Status:** `MISSING`
- **Scope:** Add OpenAPI documentation for implemented and planned `/rest/chronivaro/v1` resources, errors, pagination, concurrency, authorization, and status codes.
- **Acceptance:** Documentation matches executable routes and DTOs and has a focused contract check.
- **Dependencies:** Tasks 1–2.

### 4. Complete audit metadata and access

- **Status:** `PARTIAL`
- **Scope:** Extend the audit helper and relevant Core mutations with action, reason, correlation ID, and complete service coverage; define retention/deletion and add a privilege-protected audit query/view.
- **Acceptance:** Work, absence, vacation, period, administration, and configuration mutations are traceable; unauthorized reads fail; fields, access, and retention are tested.
- **Dependencies:** Tasks 1–2.

### 5. Finish the period lifecycle in Core

- **Status:** `PARTIAL`
- **Scope:** Implement lookup, submit/reject/reopen-with-reason, transition validation, snapshots, remaining services, warnings, and rollback using existing transaction patterns.
- **Acceptance:** Valid transitions succeed, invalid transitions do not mutate, reopening requires a reason, snapshots preserve history, and Core tests cover errors and rollback.
- **Dependencies:** Tasks 1, 2, and 4.

### 6. Expose personal and period workflow REST endpoints

- **Status:** `PARTIAL`
- **Scope:** Complete employee period/status endpoints and agreed approval routes while preserving existing REST contracts and HTTP semantics.
- **Acceptance:** Employee, supervisor, and administrator access is enforced server-side; integration tests cover transitions, conflicts, forbidden access, and standard errors.
- **Dependencies:** Tasks 2, 3, and 5.

### 7. Implement configurable vacation entitlement policy

- **Status:** `MISSING`
- **Scope:** Implement the section 6.7.1 entitlement, proration, year-boundary, carry-over, oldest-balance usage, and no-negative-balance rules as configurable Core logic; use Task 1 only for the vacation type identifier if it remains unresolved.
- **Acceptance:** The 25-day/480-minute defaults, configurable values, employment-period and part-time proration, commercial whole-minute rounding, unlimited carry-over, positive-correction carry-over, oldest-balance consumption, and insufficient-balance blocking are reproducible and covered by boundary tests; changes are audited.
- **Dependencies:** Tasks 1 and 4.

### 8. Complete the immutable vacation journal

- **Status:** `PARTIAL`
- **Scope:** Link approved usage to the journal, enforce corrections and the fixed no-negative-balance rule, and complete account/year lookup and audit behaviour.
- **Acceptance:** Entries are append-only, usage consumes the oldest available balance, usage and corrections reconcile deterministically, insufficient balance is rejected, and rollback/audit tests pass.
- **Dependencies:** Tasks 4 and 7.

### 9. Complete vacation and absence REST surfaces

- **Status:** `PARTIAL`
- **Scope:** Complete `/me/absences`, vacation-account, and related status routes, DTOs, authorization, pagination, and error/concurrency handling.
- **Acceptance:** Documented routes return calculated values; invalid dates/statuses and unauthorized cross-user access are rejected.
- **Dependencies:** Tasks 2, 3, 6, and 8.

### 10. Build supervisor approval queues

- **Status:** `MISSING`
- **Scope:** Add scoped Core searches/services and REST resources for absence and submitted-period approval queues using the decided approver rules.
- **Acceptance:** Results are scope-limited and paginated; transitions are atomic and audited; queue and authorization tests pass.
- **Dependencies:** Tasks 2, 4, 5, 6, and 9.

### 11. Add personal workflow and approval UI

- **Status:** `MISSING`
- **Scope:** Add Vanilla JS views and API clients for personal absences, vacation accounts, period workflow, and supervisor approvals.
- **Acceptance:** Loading, empty, success, error, and accessibility states are covered; server authorization remains authoritative; browser integration tests cover main workflows.
- **Dependencies:** Tasks 6, 9, and 10.

### 12. Implement structured reports and CSV export

- **Status:** `MISSING`
- **Scope:** Add Core report queries/services, REST resources, stable CSV serialization, and UI for day/month/time-balance and absence reports.
- **Acceptance:** Reports use calculated values, respect authorization/date filters, produce deterministic CSV, and have Core/REST/UI tests.
- **Dependencies:** Tasks 3, 5, 8, and 9.

### 13. Complete global configuration administration

- **Status:** `PARTIAL`
- **Scope:** Expose supported Core configuration through authorized REST and UI with validation and audit metadata.
- **Acceptance:** Administrators can update only supported values; invalid values fail consistently and changes are audited.
- **Dependencies:** Tasks 2, 3, 4, and 7.

### 14. Create `chronivaro-app` and executable packaging

- **Status:** `MISSING`
- **Scope:** Add the application module and executable artifact with application-owned configuration/startup contracts. Keep Jetty out of Core and REST; remove Tomcat as a runtime requirement.
- **Acceptance:** The parent builds four modules and the documented artifact launches with `java -jar`.
- **Dependencies:** Tasks 2–3 and the existing modules.

### 15. Implement embedded Jetty lifecycle and configuration

- **Status:** `MISSING`
- **Scope:** In `chronivaro-app`, start/stop embedded Eclipse Jetty with configurable enablement, bind address/port, optional context path, and frontend resource location; coordinate signal shutdown with Strolch cleanup.
- **Acceptance:** Startup, bind failure, controlled shutdown, and resource release are deterministic and tested; no external Jetty XML or Tomcat is required.
- **Dependencies:** Task 14.

### 16. Integrate Jersey and serve the frontend from Jetty

- **Status:** `MISSING`
- **Scope:** Register existing JAX-RS resources under `/rest/chronivaro/v1` and serve `chronivaro-web` at `/`, including `/assets/...`, from the same server without Jetty APIs in REST.
- **Acceptance:** Existing REST contracts remain unchanged; HTTP smoke tests reach `/` and REST from one server; separation rules are verified.
- **Dependencies:** Tasks 3, 14, and 15.

### 17. Add standalone and non-functional verification

- **Status:** `MISSING`
- **Scope:** Add application integration tests and operational documentation for executable startup, frontend/REST reachability, bind failure, shutdown, health/readiness, structured logs, metrics, performance, accessibility, and reproducible JDK 25 acceptance runs.
- **Acceptance:** `java -jar chronivaro.jar` (or documented equivalent) starts without Tomcat; lifecycle/HTTP smoke tests pass and operational limitations are documented.
- **Dependencies:** Tasks 4, 11–13, and 14–16.

## Explicitly removed or not applicable

- Do not recreate implemented timer, schedule, absence, presence, holiday, registration, role, or summary functionality.
- Do not add Tomcat migration tasks or preserve WAR deployment as a required runtime path. Remove or make remaining WAR configuration non-essential only within Tasks 14–16.
- Do not invent remaining product/API decisions; implement the clarified vacation rules in section 6.7.1 directly.
