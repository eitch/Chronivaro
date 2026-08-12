# Chronivaro – Refined Implementation Backlog

Use the existing `docs/Chronivaro-Implementierungsspezifikation.md` as the authoritative source.

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

# Phase 1 – Foundations

## 1. Business Roles and Permissions (✓ DONE)

### Goal

Implement the roles required by the specification:

- `Employee`
- `Vorgesetzter`
- `Personaladministration`
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

## 2. Localisation Resources (✓ DONE)

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

## 3. Global Configuration (✓ DONE)

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

---

## 5. Cancel Own Absence

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

---

## 6. Expose Employee Absence Self-Service

### Goal

Expose the completed Core absence functionality through REST.

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

---

## 7. Personal Absence UI

### Goal

Implement the employee-facing absence view using the REST endpoints from Task 6.

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

# Phase 3 – Period Workflow

## 8. Reject Submitted Period

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

## 9. Reopen Closed Period

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

## 10. Period REST Operations

Expose the Core operations from Tasks 8 and 9 using the existing `PeriodResource`.

Do not duplicate workflow validation in REST.

Add only endpoints required by the specification.

---

# Phase 4 – Administrative Audit Coverage

## 11. Audit Employee Updates

Update `UpdateEmployeeService` to use the existing Chronivaro/Strolch audit mechanism consistently.

### Acceptance Criteria

An actual master-data change produces an audit entry containing enough information to identify:

- acting user;
- affected employee;
- operation;
- changed data according to existing audit conventions.

Avoid audit entries for no-op updates if the existing project convention does so.

---

## 12. Audit Team Updates

Apply the same rules to `UpdateTeamService`.

---

## 13. Audit Schedule Updates

Apply the same rules to `UpdateScheduleService`.

Schedule history must remain consistent with effective-date behaviour defined in the specification.

---

# Phase 5 – Vacation Account UI

## 14. Vacation Account REST Verification

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

## 15. Vacation Account UI

Create the employee-facing vacation view.

Display at least the values explicitly required by the specification.

Where journal entries exist, show:

- date;
- type/reason;
- amount;
- resulting or relevant balance if available.

No balance calculations should be performed independently by JavaScript.

---

# Phase 6 – Presence Status

## 16. Presence Status Core Query

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

## 17. Presence Status REST Endpoint

Expose the presence query through a read-only REST endpoint.

Return only information the authenticated user is permitted to see.

---

## 18. Presence Status UI

Implement the "Who is working?" view.

Use the exact status semantics defined by the backend.

The frontend must not infer presence independently from raw time entries.

---

# Phase 7 – Supervisor Approvals

## 19. Approval Queue Query

Verify or implement a Core query that returns approval-relevant items for the current supervisor's permitted team(s).

Avoid loading all employees and filtering only in JavaScript.

---

## 20. Approval REST API

Expose pending:

- absences;
- submitted periods;

using the existing REST conventions.

Ensure the authenticated supervisor's scope is applied server-side.

---

## 21. Supervisor Approval UI

Create `ApprovalsView.js` using existing UI conventions.

Support:

- viewing pending requests;
- approval;
- rejection;
- mandatory rejection comment;
- refresh after an operation.

Group by team only if team information already exists and this improves the existing UI pattern.

---

# Phase 8 – Reporting

## 22. Time Balance Report Query

Implement or complete a Core reporting query for Soll/Ist values.

The Core query must produce structured report data independent of output format.

Do not generate CSV directly from persistence/domain objects.

### Acceptance Criteria

The report correctly exposes the values required by the specification for the selected period and employee scope.

Test calculations independently from REST and CSV.

---

## 23. Absence Report Query

Implement the corresponding structured Core query for absence reporting.

Reuse existing absence data and permission scope.

---

## 24. Reporting REST API

Create or complete `ReportResource`.

Implement the endpoints defined by the specification, for example:

```text
GET /reports/time-balance
GET /reports/absences
```

Use the project's established query-parameter conventions for period and employee/team filters.

JSON must be supported.

---

## 25. CSV Serialisation

Add CSV output based on the structured report results from Tasks 22 and 23.

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

## 26. Reporting UI

Create the reporting view.

Support the filters explicitly required by the specification and allow CSV download.

The UI must consume the report API and must not recreate Soll/Ist calculations.

---

# Phase 9 – Global Configuration REST/UI

## 27. Configuration REST API

Expose the Core configuration from Task 3.

Implement the smallest required API using the project's existing conventions.

Enforce administrative permissions in Core and REST.

---

## 28. Configuration Administration UI

Create an administration view for the configurable values defined in the specification.

Do not expose internal configuration values not intended for users.

Validate input server-side.

---

# Final Verification

After all backlog tasks are complete:

1. Run the complete Maven test suite.
2. Run `mvn verify` from the project root.
3. Verify that no module contains compilation errors.
4. Search for TODOs introduced during implementation.
5. Compare the completed application against `Chronivaro-Implementierungsspezifikation.md` again.
6. Produce a new gap analysis.
7. Do **not** automatically implement newly discovered gaps.

The final report must categorise findings as:

- implemented;
- partially implemented;
- missing;
- specification ambiguity.

For each remaining gap, provide exact source locations and a proposed next task.