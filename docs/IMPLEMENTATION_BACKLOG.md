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

# Phase 4a – Working Location

## 11a. Working Location Domain Model

Add working-location data to `WorkEntry` and define employee weekly defaults.

### Work
- Support the locations `HOME_OFFICE`, `OFFICE`, and `CUSTOMER`.
- Store the location on every work entry so morning and afternoon entries can have different locations.
- Model `HALF_DAY` and `FULL_DAY` defaults, including the applicable half-day part.
- Allow at most one default per weekday and day part.

### Acceptance Criteria
- Existing work entries remain valid through an explicit migration/default strategy.
- A workday can represent home office in the morning and customer work in the afternoon without overlapping entries.
- Weekly defaults can be created, changed, and removed independently for each weekday.

## 11b. Dashboard Working Location Selection

Extend the dashboard start/stop workflow to select the working location for the current workday.

### Work
- Prefill the dashboard from the employee's weekly default when opening a new workday.
- Allow the employee to override or clear the prefilled location before starting work.
- Support changing location between the morning and afternoon by creating or updating separate work-entry blocks.
- Ensure the selected location is persisted with the affected work entry and shown in the dashboard.

### Acceptance Criteria
- Starting work with no weekly default requires or clearly prompts for a location.
- A full-day default can be overridden for one day without changing the recurring weekly default.
- A half-day location change does not alter the other half of the workday.

## 11c. Working Location Validation and Queries

Validate and expose working-location data consistently across time-entry services and dashboard queries.

### Acceptance Criteria
- Invalid locations and invalid half-day/full-day combinations are rejected.
- Overlapping work entries with different locations remain prohibited.
- Existing time calculations are unchanged; location is descriptive metadata and does not affect worked minutes.
- Relevant service, REST, and dashboard tests cover defaults, overrides, full-day entries, and morning/afternoon changes.

---

# Phase 1 – Foundations

## 1. Business Roles and Permissions

### Goal

Implement the roles required by the specification:

- `Employee`
- `Supervisor`
- `HR`
- `Administrator`
- `Leseberechtigter Benutzer`, if required by the specification

### Work

Inspect the current privilege model and `runtime/config/PrivilegeRoles.xml`.

Add only missing roles and privileges.

Define privileges granularly enough to distinguish at least:

- own employee data;
- own time entries;
- own absences;
- team data;
- approval actions;
- reporting;
- employee/master-data administration;
- system configuration;
- reopening closed periods.

Do not rely solely on UI visibility to enforce permissions.

### Acceptance Criteria

- Required roles exist.
- Each relevant Core service verifies the necessary privilege.
- Employee self-service cannot access another employee's protected data.
- Supervisor functionality is limited to the supervisor's permitted employees/team.
- HR/administrator functionality follows the specification.
- Existing role functionality is not broken.

### Verification

Add or update permission-related tests where practical.

---

## 2. Localisation Resources

### Goal

Centralise user-visible strings and domain labels required by the application.

### Work

Create or complete:

```text
ChronivaroMessages.properties
ChronivaroMessages_de_CH.properties
```

Use German (Switzerland), not Swiss-German dialect.

Use `ss`, never `ß`.

Cover only strings currently required by implemented functionality.

Do not move arbitrary internal/logging strings into resource bundles unless required.

### Acceptance Criteria

- Missing user-visible status and enum labels can be localised.
- German (Switzerland) translations exist.
- No duplicate localisation mechanism is introduced.

---

## 3. Global Configuration

### Goal

Complete the Core representation and services for global Chronivaro configuration before exposing it through REST/UI.

### Work

Inspect the existing configuration model first.

Implement only missing settings defined in the specification, such as:

- global default target working time;
- other global defaults explicitly defined by the specification.

Employee-specific overrides must continue to take precedence.

Do not invent configuration options.

### Acceptance Criteria

- Global defaults can be read.
- Authorised users can modify configurable values.
- Employee overrides continue to work.
- Changes are audited if required by the specification.
- Invalid configuration is rejected by Core validation.

---

# Phase 2 – Absence Workflow

## 4. Reject Absence

### Goal

Allow an authorised supervisor to reject a submitted absence.

### Preconditions

Existing absence submission and approval functionality must be understood before implementation.

### Business Rules

- Only valid source states may be rejected.
- A rejection comment is mandatory.
- The state becomes `REJECTED`.
- The action is audited.
- Authorisation is enforced in Core.
- The supervisor may only act on employees within their permitted scope.

### Acceptance Criteria

Tests must cover at least:

- successful rejection;
- missing comment;
- invalid source state;
- unauthorised user;
- supervisor acting on an employee outside their permitted scope.

### Verification

Implemented in `RejectAbsenceService`. Added tests in `AbsenceServiceTest`.

---

## 5. Cancel Own Absence (✓ DONE)

### Goal

Allow an employee to cancel their own absence according to the specification.

### Business Rules

- The user may only cancel their own absence.
- Cancellation is only possible while allowed by the specification.
- Past absences cannot be cancelled if the specification forbids this.
- Approved absences transition to `CANCELLED` where specified.
- Vacation balance/journal effects must remain consistent.
- The change is audited.

### Acceptance Criteria

Tests must cover:

- own future absence;
- another employee's absence;
- past absence;
- approved absence;
- invalid status transition;
- vacation-account side effects where applicable.

### Verification

Implemented in `CancelAbsenceService`. Added tests in `AbsenceServiceTest`. Balanced checked and verified.

---

# Phase 3 – WorkDay & Timer Refactoring

## 6. Implement WorkDay Domain Entity (✓ DONE)

### Goal
Implement the `WorkDay` entity as described in the specification to improve performance and data handling.

### Work
- Create `WorkDay` resource template. (Implemented in `Templates.xml`)
- Add `currentWorkDayId` to `Employee` resource. (Implemented in `Templates.xml`)
- Update `WorkEntry` to reference `WorkDay`. (Added `workDay` relation to `WorkEntry`)

### Business Rules
- A `WorkDay` is created for each calendar day a user works. (To be handled in Task 7)
- It references the active `EmploymentScheduleVersion`. (Implemented in `WorkDay` template)
- It serves as a container for all `WorkEntry` objects of that day. (WorkEntry now points to WorkDay)

### Acceptance Criteria
- `WorkDay` can be created and persisted. (Verified in `WorkDayTest`)
- `Employee` correctly references the current `WorkDay`. (Verified in `WorkDayTest`)
- `WorkEntry` is correctly associated with a `WorkDay`. (Verified in `WorkDayTest`)

### Verification
Implemented in `Templates.xml` and `ChronivaroConstants.java`. Added verification test `WorkDayTest`. Fixed pre-existing `CompleteRegistrationTest` failure.

---

## 7. Refactor Timer Logic for WorkDay (✓ DONE)

### Goal
Update the start/stop timer logic to use the new `WorkDay` entity.

### Work
- Update `StartTimerService` to: (✓ DONE)
  - Check if `currentWorkDayId` exists and matches today's date.
  - Create a new `WorkDay` if necessary, capturing the current schedule.
  - Update `Employee.currentWorkDayId`.
  - Create `WorkEntry` within the context of the `WorkDay`.
- Update `StopTimerService` to find the active entry via the `WorkDay`. (✓ DONE)

### Acceptance Criteria
- Starting the timer for a new day creates a `WorkDay`. (Verified in `TimerWorkDayTest`)
- Starting the timer for the same day reuses the existing `WorkDay`. (Verified in `TimerWorkDayTest`)
- No full scans of all `WorkEntry` objects are needed to find active entries. (Optimized in `WorkEntryHelper` and timer services)

### Verification
Refactored `StartTimerService`, `StopTimerService`, and `WorkEntryHelper`. Created `WorkDayHelper`. Added integration test `TimerWorkDayTest`.

---

## 8. Ensure WorkEntries are on the same day (✓ DONE)

### Goal
Update `StopTimerService` and `UpdateWorkEntryService` to ensure `WorkEntry` objects do not span multiple days, as required by the specification. Handle forgotten timers by capping them at the time needed to reach the daily target.

### Work
- Update `StopTimerService` logic: (✓ DONE)
  - If stop time is on the same day as start time: proceed as normal.
  - If stop time is on the next day (midnight split):
    - Set current entry end to 24:00 of start day.
    - Create a new `WorkDay` for the next day if it doesn't exist.
    - Create a new `WorkEntry` on the next `WorkDay` starting at 00:00 and ending at the original stop time.
  - If stop time is more than one day after start time (forgotten timer):
    - Calculate remaining time to reach the day's target (Sollzeit).
    - Set current entry end to `Startzeit + max(0, Sollzeit - bisherige_Istzeit)`.
    - Ensure end time does not exceed 24:00.
    - Add comment "Timer vergessen - auf Sollzeit begrenzt".
    - Remaining time is lost.
- Update `UpdateWorkEntryService` and `CreateWorkEntryService` to validate that start and end are on the same day. (✓ DONE - services are named `CorrectWorkEntryService` and `AddWorkEntryService`)
- Update `WorkEntry` validation in Core to enforce same-day constraint. (✓ DONE - implemented in `WorkEntryHelper.validateNoOverlap`)

### Acceptance Criteria
- Timer stops past midnight automatically split into two entries. (Verified in `WorkEntrySameDayTest`)
- Forgotten timers (multi-day) are capped at the time the daily target is reached. (Verified in `WorkEntrySameDayTest`)
- Manual entries spanning multiple days are rejected by validation. (Verified in `WorkEntrySameDayTest`)
- Unit tests cover all carry-over and forgotten timer scenarios. (Implemented in `WorkEntrySameDayTest`)

### Verification
Refactored `StopTimerService` and `WorkEntryHelper`. Updated `WorkEntrySameDayTest` with comprehensive cases for midnight splits and forgotten timer capping logic. Verified that `AddWorkEntryService` and `CorrectWorkEntryService` correctly enforce the same-day constraint through `WorkEntryHelper`.

---

# Phase 4 – Presence Status

## 9. Presence Status Core Query [✓ DONE]

Before implementing the dashboard, verify that the backend can determine the presence state required by the specification.

Reuse existing work-entry and absence information.

Define the output using the presence states already specified by Chronivaro.

Do not invent additional privacy settings or status values.

### Acceptance Criteria

Tests cover representative cases such as:

- currently working;
- absence;
- scheduled non-working day;
- ambiguous/no data if such a state exists in the specification.

---

## 10. Presence Status REST Endpoint [✓ DONE]

Expose the presence query through a read-only REST endpoint.

Return only information the authenticated user is permitted to see.

---

## 11. Presence Status UI [✓ DONE]

Implement the "Who is working?" view.

Use the exact status semantics defined by the backend.

The frontend must not infer presence independently from raw time entries.

---

# Phase 5 – User Management & Registration

## 12. Implement InitiateEmployeeRegistrationService (✓ DONE)

### Goal
Implement a service to initiate the password set process for an employee.

### Business Rules
- Find the linked Strolch user using `userId` and `username` from the employee resource.
- Use `PrivilegeHandler.initiateChallengeFor(Usage.SET_PASSWORD, username, source)` to trigger the registration challenge.
- The service should be restricted to administrators.
- The action should be audited.

### Acceptance Criteria
- Challenge is successfully initiated for a valid employee.
- Fails if the employee has no linked user.
- Fails if the user is not found in Strolch.
- Fails if the acting user lacks administrative privileges.

### Verification
Implemented in `InitiateEmployeeRegistrationService`. Added tests in `InitiateEmployeeRegistrationServiceTest` and `CompleteRegistrationTest`.

---

## 13. Expose Registration REST Endpoint (✓ DONE)

### Goal
Expose the registration initiation service via a REST endpoint.

### Work
Add `POST /employees/{id}/register` to `EmployeeResource`.

### Rules
- Requires administrative privileges.
- Delegates to `InitiateEmployeeRegistrationService`.

---

## 14. Add Registration Action to UI (✓ DONE)

### Goal
Add a button to the Employees view to trigger the registration process.

### Work
- Update `EmployeeApi.js` to include `register(id)`.
- Update `EmployeesView.js` to add a "Register" button in the employee list actions.
- Show success/failure notification upon completion.

---
## 15. Expose Employee Absence Self-Service (✓ DONE)

### Goal
Expose the completed Core absence functionality through REST.

### Work
Implement or complete:

```text
GET  /me/absences
PUT  /me/absences/{id}
POST /me/absences/{id}/cancel
```

### Rules

The authenticated user identity must determine the employee.

Do not trust an employee ID supplied by the client for `/me/...` operations.

REST resources must delegate business rules to Core services.

### Acceptance Criteria

- A user only receives their own absences.
- Attempted cross-user access is impossible through these endpoints.
- Validation errors use the project's existing REST error conventions.
- REST integration tests are added where the project already uses them.

### Verification
Implemented `UpdateAbsenceService`. Added endpoints to `ChronivaroResource`. Added tests in `UpdateAbsenceServiceTest`.

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

### Goal
Implement automated booking of yearly vacation entitlement according to the specification (Section 6.7).

### Work
- Create a service or background task to calculate and book yearly entitlement.
- Entitlement must be based on the employee's employment rate and age/seniority if specified.
- Handle vacation expiry dates and carry-over rules.

### Acceptance Criteria
- Employees receive yearly entitlement entries automatically.
- Expiry and carry-over are handled according to business rules.

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