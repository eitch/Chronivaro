# Chronivaro – Refined Implementation Backlog

See [IMPLEMENTATION_STATUS.md](IMPLEMENTATION_STATUS.md) for the current status.

Use the existing `docs/IMPLEMENTATION_SPECIFICATION.md` as the authoritative source.

The backlog below contains functionality currently identified as missing.

## Working Rules

Implement **one numbered task at a time**.

Before implementing a task:

1. Inspect the existing implementation.
2. Verify that the task is actually missing.
3. Identify existing patterns that should be reused.
4. Identify dependencies on unfinished tasks.
5. Do not implement functionality already present in another form.

If the implementation already satisfies a task, mark it as completed and document where it is implemented instead of rewriting it.

Do not perform unrelated refactoring.

Do not introduce additional requirements not present in the specification.

Business rules and authorisation rules must be enforced in the Core layer where applicable. REST-layer checks alone are insufficient.

**Strolch Privilege Enforcement:**
- **Services:** Strolch's runtime automatically asserts the privilege for a service when it is called via the `ServiceHandler`. You do **not** need to call `tx.assertHasPrivilege(getClass().getName())` in the service's `internalDoService` method. Instead, ensure the service is allowed for the user's role in `PrivilegeRoles.xml`.
- **Searches:** Similarly, `StrolchSearch` automatically performs privilege assertions for the user.
- **Data Access:** Use `tx.assertHasPrivilege(operation, element)` for data-level authorization checks (e.g., checking if a user can edit a specific employee's data).

All changes must compile and all affected tests must pass before continuing.

Use the existing module names from the repository. Do not invent or rename modules.

The backlog is a hypothesis.

The specification defines the required behaviour, and the repository
represents the current implementation.

Always verify a backlog item against both before changing code.

Implement exactly one logical task at a time.

If a task requires changes to more than approximately 8–10 production files
or spans multiple unrelated concepts, stop before implementation and split
the task into smaller backlog items.

Inspect the existing implementation before creating new abstractions.

Reuse established project patterns.

Do not duplicate functionality that already exists.

Do not perform unrelated refactoring.

Do not invent requirements that are not present in the specification.

Enforce business rules and authorisation in the appropriate backend/domain
layer, not only in REST or UI code.

Add or update tests for every behavioural change.

Run the relevant tests before marking a task complete.

If new missing functionality is discovered, add it to the backlog instead of
automatically expanding the current task.

Update the backlog after completing or reclassifying a task.

Stop after completing and verifying the current task.

### Source of Truth

The backlog is a hypothesis. The specification is the requirement and the
repository is the current implementation.

Always verify a backlog item against both the specification and the existing
repository before changing code.

If a backlog item is already fully or partially implemented, do not blindly
implement it again. Update the backlog accordingly.

### Task Size

Implement one logical task at a time.

If a task requires changes to more than approximately 8–10 production files
or spans multiple unrelated concepts, stop before implementation and split
the task into smaller backlog items.

Do not perform unrelated refactoring while implementing a backlog item.

---

## 16. Personal Absence UI

### Goal
Implement the employee-facing absence view using the REST endpoints from Task 12.

Use Vanilla JavaScript and existing UI patterns.

### Functionality

- display personal absences;
- display status;
- create/request an absence where already supported by the backend;
- edit where allowed;
- cancel where allowed;
- display rejection comments where applicable.

A calendar view is optional unless explicitly required by the specification.

### Acceptance Criteria

- No business rules are duplicated in JavaScript.
- Server validation errors are presented to the user.
- Actions unavailable for the current state are not offered.
- Page works with the actual REST API.

---

# Phase 6 – Period Workflow

## 17. Period Auto-Generation

### Goal
Implement logic to automatically generate `TimePeriod` resources for the upcoming month for all active employees, as required by the specification (Section 6.10).

### Work
- Create a background task or a service `GeneratePeriodsService`.
- Iterate through all active employees.
- Create `TimePeriod` resources for the next month if they do not exist.

### Acceptance Criteria
- Authorized users can trigger period generation.
- Periods are correctly associated with employees and the target month.
- Duplicate periods are not created.

---

## 18. Reject Submitted Period

### Goal
Allow an authorised supervisor to reject a submitted period.

### Business Rules

- Only valid submitted periods can be rejected.
- Rejection requires a comment.
- The resulting status must match the specification.
- The action is audited.
- Team scope and privileges are enforced in Core.

### Acceptance Criteria

Cover successful and invalid state transitions, missing comment and authorisation in tests.

---

## 19. Reopen Closed Period

### Goal
Allow authorised HR/administrative users to reopen a closed period.

### Business Rules

- Only appropriate closed periods can be reopened.
- Normal employees and supervisors cannot reopen closed periods unless explicitly allowed.
- Reopening is audited.
- Existing historical entries must not be lost or recreated.

### Acceptance Criteria

Tests cover:

- successful reopening;
- invalid period state;
- unauthorised role;
- audit entry.

---

## 20. Period REST Operations

Expose the Core operations from Tasks 14 and 15 using the existing `PeriodResource`.

Do not duplicate workflow validation in REST.

Add only endpoints required by the specification.

---

# Phase 7 – Administrative Audit Coverage

## 21. Audit Employee Updates

Update `UpdateEmployeeService` to use the existing Chronivaro/Strolch audit mechanism consistently.

### Acceptance Criteria

An actual master-data change produces an audit entry containing enough information to identify:

- acting user;
- affected employee;
- operation;
- changed data according to existing audit conventions.

Avoid audit entries for no-op updates if the existing project convention does so.

---

## 22. Audit Team Updates

Apply the same rules to `UpdateTeamService`.

---

## 23. Audit Schedule Updates

Apply the same rules to `UpdateScheduleService`.

Schedule history must remain consistent with effective-date behaviour defined in the specification.

---

# Phase 8 – Employment Schedule Historization

## 24. Historical Schedule Lookup (DONE)

### Goal
Fix the limitation where only the "current" schedule is retrieved, failing to find the correct version for historical dates (Specification Section 6.2, Rules 139, 140).

### Work
- Update `ScheduleHelper.findScheduleVersion` to retrieve the version active at a specific `LocalDate`.
- Ensure `getTargetMinutes` uses this historical lookup.
- Update `WorkDayHelper.getOrCreateWorkDay` to use the version active on the WorkDay's date.

### Acceptance Criteria
- `findScheduleVersion(tx, employeeId, date)` returns the version active at `date`.
- Calculations for historical days use the target minutes from the version active at that time.

---

## 25. Schedule Overlap Prevention (DONE)

### Goal
Prevent the creation of overlapping `EmploymentScheduleVersion` resources.

### Work
- Update `CreateScheduleService` to validate that new versions do not overlap with existing ones for the same employee.

### Acceptance Criteria
- Service fails with a clear error if an overlap is detected.

---

# Phase 9 – Vacation Account Management

## 26. Automated Vacation Entitlement Engine

**Status: BLOCKED pending specification decisions.** The current specification defines the vacation journal
and entry types, but does not define the entitlement amount, age/seniority rules, carry-over limit, or expiry
date. Do not implement guessed policy. Complete the prerequisite below before implementing the engine.

### Goal
Implement automated booking of yearly vacation entitlement according to the specification (Section 6.7).

### Work
- Create a service or background task to calculate and book yearly entitlement.
- Entitlement must be based on the employee's employment rate and age/seniority if specified.
- Handle vacation expiry dates and carry-over rules.

### Acceptance Criteria
- Employees receive yearly entitlement entries automatically.
- Expiry and carry-over are handled according to business rules.

### Verified prerequisite

- [ ] **26a. Define vacation entitlement policy**: specify the annual entitlement, employment-rate
  calculation, age/seniority bands, carry-over limit, and expiry timing in the specification and configuration.
- Existing journal storage and balance calculation are in `VacationHelper` and
  `AddVacationCorrectionService`; reuse them rather than introducing parallel accounting.

---

## 27. Vacation Account REST Verification

Before implementing UI, verify whether an endpoint already provides:

- current entitlement;
- used vacation;
- planned vacation;
- available balance;
- vacation journal/history.

If data is missing, implement the smallest Core/REST addition necessary.

Do not create a parallel vacation calculation.

### Acceptance Criteria

Values returned by the API come from the existing vacation-account domain logic.

---

## 28. Vacation Account UI

Create the employee-facing vacation view.

Display at least the values explicitly required by the specification.

Where journal entries exist, show:

- date;
- type/reason;
- amount;
- resulting or relevant balance if available.

No balance calculations should be performed independently by JavaScript.

---

# Phase 10 – Supervisor Approvals

## 29. Approval Queue Query

Verify or implement a Core query that returns approval-relevant items for the current supervisor's permitted team(s).

Avoid loading all employees and filtering only in JavaScript.

---

## 30. Approval REST API

Expose pending:

- absences;
- submitted periods;

using the existing REST conventions.

Ensure the authenticated supervisor's scope is applied server-side.

---

## 31. Supervisor Approval UI

Create `ApprovalsView.js` using existing UI conventions.

Support:

- viewing pending requests;
- approval;
- rejection;
- mandatory rejection comment;
- refresh after an operation.

Group by team only if team information already exists and this improves the existing UI pattern.

---

# Phase 11 – Reporting

## 32. Time Balance Report Query

Implement or complete a Core reporting query for Soll/Ist values.

The Core query must produce structured report data independent of output format.

Do not generate CSV directly from persistence/domain objects.

### Acceptance Criteria

The report correctly exposes the values required by the specification for the selected period and employee scope.

Test calculations independently from REST and CSV.

---

## 33. Absence Report Query

Implement the corresponding structured Core query for absence reporting.

Reuse existing absence data and permission scope.

---

## 34. Reporting REST API

Create or complete `ReportResource`.

Implement the endpoints defined by the specification, for example:

```text
GET /reports/time-balance
GET /reports/absences
```

Use the project's established query-parameter conventions for period and employee/team filters.

JSON must be supported.

---

## 35. CSV Serialisation

Add CSV output based on the structured report results from Tasks 28 and 29.

Do not duplicate report calculations in the CSV implementation.

Use the exact column definitions and formatting specified by the Chronivaro specification.

If the specification does not define whether CSV is selected using an `Accept` header or a dedicated endpoint/file suffix, follow the convention already present in the repository rather than inventing a new one.

Add tests for:

- headers;
- escaping;
- separators;
- encoding;
- representative rows.

---

## 36. Reporting UI

Create the reporting view.

Support the filters explicitly required by the specification and allow CSV download.

The UI must consume the report API and must not recreate Soll/Ist calculations.

---

# Phase 12 – Global Configuration REST/UI

## 37. Configuration REST API

Expose the Core configuration from Task 3.

Implement the smallest required API using the project's existing conventions.

Enforce administrative permissions in Core and REST.

---

## 38. Configuration Administration UI

Create an administration view for the configurable values defined in the specification.

Do not expose internal configuration values not intended for users.

Validate input server-side.

---

---


## Verified UI Refinements

- [x] Dashboard working location uses radio-style controls and becomes read-only while working (`chronivaro-web/src/main/webapp/js/pages/DashboardView.js` and `assets/css/style.css`).
- [x] Dashboard shows the active timer's working location from the day-summary response.
- [x] My Times shows each work entry's working location from the existing work-entry response.
- [x] Who is working? shows the active working location from the presence response.
- [x] Who is working? places the active working location beside the status.

# Final Verification

After all backlog tasks are complete:

1. Run the complete Maven test suite.
2. Run `mvn verify` from the project root.
3. Verify that no module contains compilation errors.
4. Search for TODOs introduced during implementation.
5. Compare the completed application against `IMPLEMENTATION_SPECIFICATION.md` again.
6. Produce a new gap analysis.
7. Do **not** automatically implement newly discovered gaps.

The final report must categorise findings as:

- implemented;
- partially implemented;
- missing;
- specification ambiguity.

For each remaining gap, provide exact source locations and a proposed next task.

---

## Dependency-ordered delivery plan — 2026-08-17

This audit is based on `IMPLEMENTATION_SPECIFICATION.md` and repository evidence, not on previous backlog labels. Execute the tasks in order; a task may begin only after its listed prerequisites are complete.

### 1. Resolve product and API decisions

- **Classification:** `SPECIFICATION_AMBIGUITY`
- **Specification:** Sections 6.7.1, 13.2–13.3, 17.3, 21.
- **Current location:** Open decisions are documented in `IMPLEMENTATION_SPECIFICATION.md`; no complete configuration REST/UI exists.
- **Missing behaviour:** Decisions for vacation day minutes, proration rounding, vacation type ID, positive-correction carry-over, endpoint naming/status codes, approver selection, authentication, retention, and scope.
- **Backlog item:** Record approved decisions and translate them into a versioned configuration/API contract.
- **Dependencies:** None.
- **Acceptance criteria:** Every open value has an owner-approved decision or an explicitly deferred scope; endpoint paths/status codes and configurable policy keys are documented without hidden defaults.

### 2. Establish shared REST, correlation, and authorization foundations

- **Classification:** `MISSING`
- **Specification:** Sections 13.1, 14.2, 16.1–16.3, 17.3, 22.
- **Current location:** REST resources under `chronivaro-rest/src/main/java/.../resource`; no complete shared error/OpenAPI/concurrency/pagination contract was found.
- **Missing behaviour:** Standard errors with field errors and correlation ID, optimistic concurrency, pagination, complete ISO contracts, OpenAPI, and request correlation propagation.
- **Backlog item:** Implement shared REST filters, error mapping, version checks, pagination helpers, OpenAPI schemas, and privilege-boundary tests.
- **Dependencies:** Task 1 for final contract decisions.
- **Acceptance criteria:** Existing and new mutable/list endpoints use one documented contract; stale writes fail deterministically; integration tests cover validation, authorization, conflicts, pagination, and error correlation.

### 3. Complete audit infrastructure and access controls

- **Classification:** `PARTIALLY_IMPLEMENTED`
- **Specification:** Sections 5.2, 6.10, 9.3–9.5, 16.3, 19.15.
- **Current location:** `chronivaro-core/src/main/java/.../model/ChronivaroAuditHelper.java` and update services such as `UpdateEmployeeService.java`, `UpdateTeamService.java`, and `UpdateScheduleService.java`.
- **Missing behaviour:** Explicit action, reason, correlation ID, complete state-changing coverage, and restricted audit access.
- **Backlog item:** Extend the audit event model/helper, instrument all required services, and add an authorized audit query.
- **Dependencies:** Task 2 for correlation IDs and API access; existing privilege roles.
- **Acceptance criteria:** Required changes record actor, entity, action, timestamp, old/new values, reason where required, and correlation ID; sensitive values are protected; unauthorized audit reads fail; representative tests pass.

### 4. Complete the period lifecycle

- **Classification:** `PARTIALLY_IMPLEMENTED`
- **Specification:** Sections 6.9, 9.5, 10.1, 13.2.
- **Current location:** `SubmitPeriodService.java`, `ApprovePeriodService.java`, `LockPeriodService.java`, and `chronivaro-rest/.../PeriodResource.java`.
- **Missing behaviour:** Employee period retrieval/submission, rejection, reopen with permission/reason, full transition validation, metadata/snapshots, and approval listing.
- **Backlog item:** Extend Core lifecycle services and REST resources without duplicating business rules in REST.
- **Dependencies:** Tasks 2–3; Task 1 endpoint decisions.
- **Acceptance criteria:** All states and valid/invalid transitions are tested; approval locks; rejection and authorized reopening require the specified metadata; snapshots preserve reproducibility; all period endpoints are documented.

### 5. Implement vacation policy and journal accounting

- **Classification:** `PARTIALLY_IMPLEMENTED` plus `SPECIFICATION_AMBIGUITY`.
- **Specification:** Sections 6.7, 6.7.1, 9.4, 11.3, 13.2.
- **Current location:** `VacationHelper.java`, `AddVacationCorrectionService.java`, and `ApproveAbsenceService.java`; no complete vacation REST/UI implementation.
- **Missing behaviour:** Configurable entitlement/proration, carry-over, usage linkage/reversal, no-negative-balance approval blocking, account endpoints, and journal reporting.
- **Backlog item:** Build one immutable journal engine and expose account/adjustment APIs after Task 1 decisions.
- **Dependencies:** Tasks 1–3; employee schedules; absence approval/status.
- **Acceptance criteria:** Entitlement, carry-over, usage, correction, and balance are reproducible in minutes; insufficient balance blocks approval; entries are immutable and audited; all section 11.3 values are exposed.

### 6. Build scoped supervisor approval queues and UI

- **Classification:** `MISSING`
- **Specification:** Sections 3.2, 9.4–9.5, 12.1, 13.2, 18.2–18.3.
- **Current location:** Approval services exist in `chronivaro-core/.../service`; no approval search/resource/`ApprovalsView.js` was found.
- **Missing behaviour:** Team-scoped pending absence/period queues and approval/rejection UI with required comments and state handling.
- **Backlog item:** Add Core searches, REST endpoints, and `ApprovalsView.js`.
- **Dependencies:** Tasks 2–4; existing absence services and team authorization.
- **Acceptance criteria:** Supervisors see only permitted pending items; Core enforces transitions and rejection comments; UI covers loading, empty, error, keyboard, and success states.

### 7. Add personal absence and vacation UI

- **Classification:** `MISSING`
- **Specification:** Sections 12.1, 18.3, 19.
- **Current location:** Absence operations are in `ChronivaroResource.java` and Core services; no personal absence or vacation page exists.
- **Missing behaviour:** Personal absence list/create/edit/status and vacation account/journal views using real APIs.
- **Backlog item:** Add `AbsencesView.js` and `VacationView.js` with centralized API handling and navigation.
- **Dependencies:** Tasks 2 and 5; existing absence endpoints.
- **Acceptance criteria:** Valid actions and rejection comments/statuses are displayed; no business calculations are duplicated in JavaScript; loading/empty/error and keyboard flows are covered.

### 8. Implement reports and CSV export

- **Classification:** `MISSING`
- **Specification:** Sections 4.1.10, 4.1.12, 4.1.14, 11.1–11.5, 13.2, 18.2.
- **Current location:** `DaySummaryService.java` and `MonthSummaryService.java`; no report resource/query/CSV serializer/page exists.
- **Missing behaviour:** Time-balance, absence, team, and vacation reports; permission-scoped REST; UTF-8/BOM CSV; report UI.
- **Backlog item:** Add Core projections, REST mappings, CSV serialization, and a Vanilla JavaScript reports page.
- **Dependencies:** Tasks 2–7; period and vacation data.
- **Acceptance criteria:** JSON/CSV contain required report fields; filters enforce server-side scope; CSV escaping/encoding/format tests pass; UI supports required filters and states.

### 9. Complete non-functional and acceptance verification

- **Classification:** `MISSING`
- **Specification:** Sections 12.2, 17, 18, 19, 22.
- **Current location:** Existing tests are under `chronivaro-core/src/test` and `chronivaro-rest/src/test`; no complete performance, browser/accessibility, health, metrics, or operational evidence was found.
- **Missing behaviour:** Required DST/core/REST/UI tests, performance measurements, structured observability, health/readiness, retention guidance, and reproducible acceptance evidence.
- **Backlog item:** Execute the verification track and document operational and acceptance results without weakening tests.
- **Dependencies:** Tasks 1–8 and deployment/authentication decisions.
- **Acceptance criteria:** Specification test cases pass; response-time targets are measured; health/readiness, metrics, correlation IDs, retention guidance, responsive/accessibility checks, and JDK 25 `mvn verify` evidence are documented.

## Verified implemented areas not to reimplement

The following were confirmed in source and tests: Maven/module foundation; roles and privilege configuration; localization resources; employee/team/location/schedule/holiday CRUD; historical schedule lookup and overlap prevention; WorkDay and current-day timer lifecycle; same-day and forgotten-timer handling; multiple work blocks and working-location defaults; absence self-service and rejection/cancellation; registration; presence status; and day/month summary calculation. These remain recorded as implemented or partial above only where the specification requires additional behaviour.