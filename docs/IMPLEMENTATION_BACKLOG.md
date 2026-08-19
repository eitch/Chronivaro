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

- **Status:** `COMPLETED`
- **Specification:** Sections 6.7.1, 13.2, and 22.
- **Verification:** All open decisions for MVP are resolved and fixed in `IMPLEMENTATION_SPECIFICATION.md`:
  - Vacation absence type code: `VACATION` (section 6.7.1).
  - Vacation calculation rules: 25 days/year, 480 min/day, whole-minute commercial rounding, unlimited carry-over, positive corrections included in carry-over, oldest-balance usage, no negative vacation balances (sections 6.7.1 and 22).
  - Standard REST approval routes: `/approvals/absences`, `/approvals/absences/{id}/approve`, `/approvals/absences/{id}/reject`, `/approvals/periods`, `/approvals/periods/{id}/approve`, `/approvals/periods/{id}/reject`, `/periods/{id}/reopen` (section 13.2).
  - Approver selection: Each employee belongs to exactly one primary team; supervisor approves team members' absences and periods (section 22).
  - Cancellation & illness rules: Approved absences modified only via cancellation workflow with reversing journal entries (section 22).
  - Time balances: Negative time balances allowed; no rounding in MVP (section 22).
  - Default timezone & legal entity: `Europe/Zurich`, single legal entity (section 22).
  - Working location visibility: Home office optionally visible as working location (section 22).
- **Dependencies:** None.

## Dependency-ordered implementation tasks

### 2. Establish shared REST contracts

Task 2 was split into tasks **2.1**, **2.2**, and **2.3** per the task-size and single-concept rules (avoiding changes across 10+ files simultaneously).

#### 2.1. Establish standard REST error contracts and correlation ID propagation

- **Status:** `COMPLETED`
- **Scope:** Add standard error payload DTOs (`ErrorDto`, `FieldErrorDto` matching section 13.1), correlation ID request filter and response header (`X-Correlation-Id`), standard exception mappers and `ServiceResult` error conversion in the REST layer, with integration tests covering standard error responses and correlation propagation.
- **Acceptance:** Error responses strictly follow the section 13.1 schema; correlation IDs are generated or propagated and returned in headers/payloads; unhandled exceptions and service failures map deterministically to appropriate HTTP status codes (400, 401, 403, 404, 500).
- **Verification:**
  - Implemented `ErrorDto` and `FieldErrorDto` matching the Section 13.1 schema.
  - Implemented `CorrelationIdFilter` to extract incoming `X-Correlation-Id` or generate a unique correlation ID, propating it via thread local, SLF4J MDC, request properties, and response headers.
  - Implemented `ChronivaroRestfulExceptionMapper` to map uncaught exceptions and `RestException` (including field errors) to standard HTTP statuses and `ErrorDto` responses with correlation ID.
  - Updated `ChronivaroRestHelper` and all REST resources to map `ServiceResult` errors and missing entities to standard `ErrorDto` responses.
  - Verified via integration tests in `RestErrorTest` and full test suite run (`mvn test`).
- **Dependencies:** Task 1.

#### 2.2. Implement REST pagination helpers and contracts

- **Status:** `COMPLETED`
- **Scope:** Define shared pagination query parameters (e.g. `offset`, `limit`) and paged response envelope DTOs (`PagedResultDto<T>`), along with Strolch search pagination utilities for REST list endpoints.
- **Acceptance:** List endpoints support consistent pagination contracts and return correct total count, limit, and offset metadata.
- **Verification:**
  - Implemented `PagedResultDto<T>` record with fields `data`, `offset`, `limit`, `total`, and `size`.
  - Implemented `PaginationHelper` with offset/limit validation, sanitization (defaults 0, 50, max 1000), `toPagedResult` for lists and `SearchResult<T>`, and `toPagedOrListResponse`.
  - Integrated pagination parameters and helpers across all REST list endpoints (`EmployeeResource`, `TeamResource`, `LocationResource`, `HolidayCalendarsResource`, `AbsenceTypeResource`, `ScheduleTemplateResource`, `AbsenceResource`, and `ChronivaroResource`).
  - Added unit and REST integration tests in `RestPaginationTest` covering valid pagination, out-of-bounds offsets, list slicing, `SearchResult` paging, and bad request validation errors for negative offset/invalid limit.
  - Verified with full test suite passing via `mvn test`.
- **Dependencies:** Task 2.1.

#### 2.3. Implement REST optimistic concurrency control

- **Status:** `COMPLETED`
- **Scope:** Add version/ETag concurrency validation mechanisms for mutable domain entities and REST resources.
- **Acceptance:** Stale updates return 409 Conflict with standard error payload; concurrent modifications are safely rejected.
- **Verification:**
  - Implemented `ChronivaroVersionHelper` in `chronivaro-core` for initialising and incrementing element versions (`PARAM_VERSION`, `PARAM_UPDATED_BY`, and Strolch `Version` metadata) during entity creation and modification.
  - Implemented `ConcurrencyHelper` in `chronivaro-rest` supporting ETag generation (`ETag` header / `EntityTag`), weak/strong ETag parsing, and `If-Match` validation rejecting mismatched or stale updates with HTTP 409 Conflict (`CONCURRENCY_CONFLICT`) and standard `ErrorDto` payload.
  - Updated all mutable REST resource endpoints (`EmployeeResource`, `TeamResource`, `LocationResource`, `AbsenceTypeResource`, `ScheduleTemplateResource`, `HolidayCalendarsResource`, `AbsenceResource`, and `ChronivaroResource`) to return `ETag` headers on GET/PUT/POST mutations and enforce `If-Match` optimistic locking on updates and deletions.
  - Added comprehensive REST integration tests in `RestConcurrencyTest` validating ETag returns, matching If-Match success, stale If-Match 409 Conflict rejections, weak ETag matching, and invalid If-Match header 400 Bad Request error mappings.
  - Verified with full test suite passing via `mvn test` (38 passing tests, 0 failures, 0 errors).
- **Dependencies:** Task 2.1.

### 3. Document the REST contract

- **Status:** `COMPLETED`
- **Scope:** Add OpenAPI documentation for implemented and planned `/rest/chronivaro/v1` resources, errors, pagination, concurrency, authorization, and status codes.
- **Acceptance:** Documentation matches executable routes and DTOs and has a focused contract check.
- **Verification:**
  - Created `docs/openapi.yaml` (OpenAPI 3.0.3) covering all current endpoints and planned routes from specification Section 13.2 (system/version, authentication/registration, presence, timer/work entries, day/month summaries, absences/approvals, period lifecycle/closing, and administrative resources for employees, schedules, vacation account, teams, locations, absence types, holiday calendars, schedule templates, audit logs, and configuration).
  - Defined all shared headers (`X-Correlation-Id`, `ETag`, `If-Match`), pagination parameters (`offset`, `limit`), error structures (`ErrorDto`, `FieldErrorDto`), and standard DTO schemas matching existing Java records.
  - Added automated contract verification test in `chronivaro-rest` (`OpenApiSpecTest`) validating file presence, version, required schemas, headers, query parameters, and all endpoint paths.
  - Verified with full project test suite passing (`mvn test`, 39 tests passing with 0 failures and 0 errors).
- **Dependencies:** Tasks 1, 2.1–2.3.

### 4. Complete audit metadata and access

Task 4 was split into subtasks **4.1**, **4.2.1**, **4.2.2**, **4.2.3**, and **4.3** per the task-size and single-concept rules (avoiding changes across 10+ files simultaneously).

#### 4.1. Extend Core audit model, audit helper, retention purging, and search query

- **Status:** `COMPLETED`
- **Scope:** Extend `ChronivaroAuditEvent` template and constants with action, reason, correlation ID, and details; enhance `ChronivaroAuditHelper` to populate structured audit events (including MDC/thread-local correlation ID capture); implement audit retention/purge logic (`PurgeAuditEventsService` / retention rule); implement `AuditEventSearch` with fluent filters (entityType, entityId, username, action, date range) and privilege assertions in Core.
- **Acceptance:** Audit events store complete metadata (action, reason, correlationId, details, createdBy, date, old/new values); retention purging removes aged events deterministically; `AuditEventSearch` correctly filters audit records and enforces privilege checks; verified by comprehensive Core unit/integration tests.
- **Verification:**
  - Extended `ChronivaroConstants` with audit constants (`PARAM_ACTION`, `PARAM_REASON`, `PARAM_CORRELATION_ID`, `PARAM_DETAILS`, and standard audit action identifiers).
  - Enhanced `ChronivaroAuditHelper` to capture correlation IDs from thread-local / SLF4J MDC and populate structured `ChronivaroAuditEvent` elements with complete action/reason/correlation/value metadata.
  - Implemented `AuditEventSearch` extending `ResourceSearch` with fluent query filters (`forElementType`, `forElementId`, `forUsername`, `forAction`, `forCorrelationId`, `inDateRange`) and privilege assertions.
  - Implemented `PurgeAuditEventsService` supporting retention period (`retentionDays`) or explicit cutoff date (`cutoffDate`) purging with automated audit event recording.
  - Added unit and integration tests in `AuditEventTest` validating full metadata persistence, MDC correlation extraction, query filtering, retention purging, and Strolch privilege enforcement (39 tests passing across reactor modules).
- **Dependencies:** Tasks 1, 2.1.

#### 4.2.1. Audit logging for Administrative Master Data services

- **Status:** `COMPLETED`
- **Scope:** Integrate `ChronivaroAuditHelper.audit(...)` across administrative master data mutation services: Team (`CreateTeamService`, `UpdateTeamService`, `RemoveTeamService`), Location & Defaults (`CreateLocationService`, `UpdateLocationService`, `RemoveLocationService`, `AddOrUpdateWorkingLocationDefaultService`, `RemoveWorkingLocationDefaultService`), Holiday Calendar & Holidays (`CreateHolidayCalendarService`, `RemoveHolidayCalendarService`, `CreateHolidayService`, `RemoveHolidayService`), Absence Types (`CreateAbsenceTypeService`, `UpdateAbsenceTypeService`, `RemoveAbsenceTypeService`), and Schedule Templates (`CreateScheduleTemplateService`, `UpdateScheduleTemplateService`, `RemoveScheduleTemplateService`).
- **Acceptance:** All create, update, and remove actions on administrative master data entities record structured audit events with appropriate action tags (`CREATE`, `UPDATE`, `REMOVE`), parameter changes, and descriptive details; verified by Core unit and lifecycle tests.
- **Verification:**
  - Integrated `ChronivaroAuditHelper.audit(...)` across all 18 administrative master data services in `chronivaro-core`.
  - Added unit and lifecycle integration tests in `AdminMasterDataAuditTest` verifying audit creation, modification, removal, and search querying across Teams, Locations, Holiday Calendars, Holidays, Absence Types, Schedule Templates, and Working Location Defaults.
  - Verified with `mvn test` (all 39 tests passing with 0 failures and 0 errors).
- **Dependencies:** Task 4.1.

#### 4.2.2. Audit logging for Employee and Schedule services

- **Status:** `COMPLETED`
- **Scope:** Integrate `ChronivaroAuditHelper.audit(...)` across Employee lifecycle and Schedule mutation services (`CreateEmployeeService`, `UpdateEmployeeService`, `RemoveEmployeeService`, `InitiateEmployeeRegistrationService`, `CompleteRegistrationService`, `CreateScheduleService`, `UpdateScheduleService`, `RemoveScheduleService`).
- **Acceptance:** Employee creation, updates, removals, self-registration lifecycle steps, and employment schedule assignments/modifications record structured audit events.
- **Verification:**
  - Integrated `ChronivaroAuditHelper.audit(...)` into `CreateEmployeeService`, `UpdateEmployeeService`, `RemoveEmployeeService`, `InitiateEmployeeRegistrationService`, `CompleteRegistrationService`, `CreateScheduleService`, `UpdateScheduleService`, and `RemoveScheduleService`.
  - Added registration audit action constants (`AUDIT_ACTION_REGISTRATION_INITIATED`, `AUDIT_ACTION_REGISTRATION_COMPLETED`) to `ChronivaroConstants`.
  - Added unit and lifecycle integration tests in `EmployeeAndScheduleAuditTest` verifying audit creation, modification, removal, registration challenge/completion tracking, and schedule versioning/closure events.
  - Verified with `mvn test` passing all tests with 0 failures and 0 errors.
- **Dependencies:** Task 4.1.

#### 4.2.3. Audit logging for Time Tracking, Absence, Vacation, Period, and Configuration services

- **Status:** `COMPLETED`
- **Scope:** Integrate `ChronivaroAuditHelper.audit(...)` across operational services: Timers & Work Entries (`StartTimerService`, `StopTimerService`, `AddWorkEntryService`, `CorrectWorkEntryService`), Absences (`RequestAbsenceService`, `UpdateAbsenceService`, `ApproveAbsenceService`, `RejectAbsenceService`, `CancelAbsenceService`), Vacation Corrections (`AddVacationCorrectionService`), Period Lifecycle (`SubmitPeriodService`, `ApprovePeriodService`, `LockPeriodService`), and System Configuration (`UpdateConfigurationService`).
- **Acceptance:** All operational state transitions, manual edits, approvals, cancellations, period closings, and configuration modifications produce structured audit records with audit actions and details.
- **Verification:**
  - Integrated `ChronivaroAuditHelper.audit(...)` across timer services (`StartTimerService`, `StopTimerService`), work entry mutations (`AddWorkEntryService`, `CorrectWorkEntryService`), absence workflow services (`RequestAbsenceService`, `UpdateAbsenceService`, `ApproveAbsenceService`, `RejectAbsenceService`, `CancelAbsenceService`), vacation accounting (`AddVacationCorrectionService`), period lifecycle transitions (`SubmitPeriodService`, `ApprovePeriodService`, `LockPeriodService`), and global configuration (`UpdateConfigurationService`).
  - Adjusted `ChronivaroAuditHelper` overload resolution to support `audit(tx, elementType, elementId, action, reason, details)` cleanly alongside `auditChange` and `auditAction`.
  - Added comprehensive test suite in `OperationalServicesAuditTest` validating audit creation, correlation propagation, reason tracking, vacation accounting side effects, period transitions, and configuration changes.
  - Verified with full test suite passing via `mvn test` (39 unit/integration tests passing across reactor modules with 0 failures and 0 errors).
- **Dependencies:** Task 4.1.

#### 4.3. Expose and secure the Admin Audit Logs REST endpoint

- **Status:** `COMPLETED`
- **Scope:** Implement `/chronivaro/v1/admin/audit-logs` endpoint with query filters (`entityType`, `entityId`, `username`, `from`, `to`), pagination envelopes (`PagedResultDto<AuditLogDto>`), correlation ID headers, and privilege enforcement (restricted to StrolchAdmin / Admin role).
- **Acceptance:** Authorized administrators can query and paginate audit logs; unauthorized or non-admin users receive 403 Forbidden; filter combinations and correlation ID propagation are verified by REST integration tests.
- **Verification:**
  - Implemented `AuditLogDto` in `chronivaro-rest` matching OpenAPI schema (`id`, `timestamp`, `username`, `action`, `entityType`, `entityId`, `details`).
  - Added `auditLogToDto` in `ChronivaroMapper` converting `ChronivaroAuditEvent` resources into `AuditLogDto`.
  - Implemented `AuditLogsResource` (`/chronivaro/v1/admin/audit-logs`) with query filters (`entityType`, `entityId`, `username`, `action`, `from`, `to`), descending timestamp ordering (`orderByParam(PARAM_DATE, true)`), and pagination integration via `PaginationHelper`.
  - Registered `AuditLogsResource` in `ChronivaroRestfulClasses`.
  - Enforced Strolch privilege authorization ensuring non-admin users receive 403 Forbidden (`ACCESS_DENIED`) via `ChronivaroRestfulExceptionMapper`.
  - Added comprehensive REST integration tests in `AuditLogsResourceTest` covering admin queries, filtering, date range parsing, pagination envelopes, 403 Forbidden checks for non-admin employees, 401 Unauthorized for unauthenticated requests, and 400 Bad Request on invalid date formats.
  - Verified with full test suite passing via `mvn test` (46 unit/integration tests passing across reactor modules with 0 failures and 0 errors).
- **Dependencies:** Tasks 2.1, 2.2, 3, 4.1, 4.2.1, 4.2.2, 4.2.3.

### 5. Finish the period lifecycle in Core

- **Status:** `COMPLETED`
- **Scope:** Implement lookup, submit/reject/reopen-with-reason, transition validation, snapshots, remaining services, warnings, and rollback using existing transaction patterns.
- **Acceptance:** Valid transitions succeed, invalid transitions do not mutate, reopening requires a reason, snapshots preserve history, and Core tests cover errors and rollback.
- **Verification:**
  - Implemented `PeriodHelper` with `getPeriodId`, `findPeriod`, `getOrCreatePeriod`, `isPeriodClosed`, `assertPeriodOpen`, and `createCalculationSnapshot` (capturing month summary target, actual, holiday, absence, and balance calculations).
  - Implemented `PeriodActionArgument` supporting period resolution by `periodId` or `(employeeId, yearMonth)` and comment/reason payloads.
  - Implemented `SubmitPeriodService`, `ApprovePeriodService`, `RejectPeriodService`, `ReopenPeriodService`, and `LockPeriodService` validating state transitions (`OPEN`/`REJECTED` -> `SUBMITTED` -> `APPROVED` -> `LOCKED`, with `REOPEN` -> `OPEN`), recording timestamps (`submittedAt`, `approvedAt`, `approvedBy`, `rejectedAt`, `rejectedBy`), capturing calculation snapshots, asserting mandatory rejection/reopening reasons, bumping version metadata, and auditing all transitions via `ChronivaroAuditHelper`.
  - Enforced period closure locking across operational mutation services (`AddWorkEntryService`, `CorrectWorkEntryService`, `StartTimerService`, `RequestAbsenceService`, `UpdateAbsenceService`, `CancelAbsenceService`) using `PeriodHelper.assertPeriodOpen`.
  - Implemented `TimePeriodSearch` providing fluent queries by `employee`, `yearMonth`, `state`, and `year`.
  - Configured role privileges for `Employee`, `Supervisor`, `HR`, and `Administrator` in `PrivilegeRoles.xml`.
  - Added comprehensive unit and integration tests in `PeriodLifecycleServiceTest` and updated `OperationalServicesAuditTest`, `PeriodResource`, and `ChronivaroResource`.
  - Verified with full test suite passing via `mvn test` (46 unit/integration tests passing across reactor modules with 0 failures and 0 errors).
- **Dependencies:** Tasks 1, 2.1, and 4.

### 6. Expose personal and period workflow REST endpoints

- **Status:** `COMPLETED`
- **Scope:** Complete employee period/status endpoints and agreed approval routes while preserving existing REST contracts and HTTP semantics.
- **Acceptance:** Employee, supervisor, and administrator access is enforced server-side; integration tests cover transitions, conflicts, forbidden access, and standard errors.
- **Verification:**
  - Implemented `PeriodStatusDto` and `PeriodActionRequestDto` matching OpenAPI and specification data models.
  - Added `periodToDto` mapping in `ChronivaroMapper`.
  - Implemented `PeriodResource` (`/chronivaro/v1/periods`) supporting `GET /status`, `POST /submit`, `POST /approve`, `POST /reject`, `POST /reopen`, and `POST /{id}/reopen` with ETag validation, Gson deserialization, and standard error handling.
  - Implemented `AdminPeriodResource` (`/chronivaro/v1/admin/periods`) supporting `POST /{id}/approve`, `POST /{id}/reject`, `POST /{id}/reopen`, and `POST /{id}/lock`.
  - Implemented `ApprovalsResource` (`/chronivaro/v1/approvals`) supporting `GET /periods` (paged list of submitted periods), `POST /periods/{id}/approve`, `POST /periods/{id}/reject`, `GET /absences` (paged list of submitted absences), `POST /absences/{id}/approve`, and `POST /absences/{id}/reject`.
  - Added personal period endpoints in `ChronivaroResource` (`GET /chronivaro/v1/me/periods/{yearMonth}`, `POST /chronivaro/v1/me/periods/{yearMonth}/submit`, and `POST /chronivaro/v1/me/periods/{id}/submit`).
  - Registered new resources in `ChronivaroRestfulClasses`.
  - Added comprehensive integration tests in `PeriodResourceTest` covering status lookup/auto-creation, submission, approvals queue retrieval, supervisor approval, rejection with mandatory comment, reopening with mandatory comment, HR period locking, Strolch role privilege enforcement (403 Forbidden for unauthorized actions), concurrency control (409 Conflict with stale If-Match ETag), unauthenticated 401 handling, and 400 Bad Request parameter validation.
  - Verified with full test suite passing via `mvn test` (56 tests passing across all reactor modules with 0 failures and 0 errors).
- **Dependencies:** Tasks 2.1, 2.2, 3, and 5.

### 7. Implement configurable vacation entitlement policy

- **Status:** `COMPLETED`
- **Scope:** Implement the section 6.7.1 entitlement, proration, year-boundary, carry-over, oldest-balance usage, and no-negative-balance rules as configurable Core logic; use Task 1 only for the vacation type identifier if it remains unresolved.
- **Acceptance:** The 25-day/480-minute defaults, configurable values, employment-period and part-time proration, commercial whole-minute rounding, unlimited carry-over, positive-correction carry-over, oldest-balance consumption, and insufficient-balance blocking are reproducible and covered by boundary tests; changes are audited.
- **Verification:**
  - Added vacation parameters and default configuration constants (`PARAM_ANNUAL_VACATION_DAYS`, `PARAM_MINUTES_PER_VACATION_DAY`, `PARAM_VACATION_ABSENCE_TYPE_CODE`, `DEFAULT_ANNUAL_VACATION_DAYS`, `DEFAULT_MINUTES_PER_VACATION_DAY`, `DEFAULT_VACATION_ABSENCE_TYPE_CODE`) in `ChronivaroConstants` and XML templates/models.
  - Implemented `VacationAccountSummary` record providing complete yearly account breakdown (`carryOverMinutes`, `entitlementMinutes`, `correctionsMinutes`, `usageMinutes`, `remainingMinutes`).
  - Implemented `VacationHelper` calculation and accounting engine handling annual entitlement calculation with schedule and employment proration, leap-year boundary handling, commercial whole-minute rounding (`Math.round`), balance query, account breakdown, and balance sufficiency assertions.
  - Implemented `CalculateVacationEntitlementService` and `CreditVacationEntitlementService` with versioning and audit trail recording.
  - Updated `UpdateConfigurationService` to support runtime configuration of `annualVacationDays`, `minutesPerVacationDay`, `vacationAbsenceTypeCode`, and `weeklyTargetMinutes`.
  - Updated `ApproveAbsenceService` to enforce vacation balance sufficiency (`assertSufficientVacationBalance`), preventing negative balances on vacation approval as specified in section 6.7.1.
  - Updated Strolch role privilege definitions in `PrivilegeRoles.xml` for `Employee`, `Supervisor`, `HR`, and `StrolchAdmin`.
  - Added comprehensive test suite in `VacationEntitlementServiceTest` covering standard full-year calculation, leap years, part-time proration, mid-year entry and exit, schedule changes, custom configuration parameters, service crediting/recalculation, audit verification, yearly journal summaries with carry-over, and absence approval blocking on insufficient balance.
  - Verified with full test suite passing via `mvn test` (56 tests passing across all reactor modules with 0 failures and 0 errors).
- **Dependencies:** Tasks 1 and 4.

### 8. Complete the immutable vacation journal

- **Status:** `COMPLETED`
- **Scope:** Link approved usage to the journal, enforce corrections and the fixed no-negative-balance rule, and complete account/year lookup and audit behaviour.
- **Acceptance:** Entries are append-only, usage consumes the oldest available balance, usage and corrections reconcile deterministically, insufficient balance is rejected, and rollback/audit tests pass.
- **Verification:**
  - Implemented `VacationAccountEntrySearch` extending `ResourceSearch` with fluent filters for `employee`, `vacationType`, `absence`, and year/date ranges.
  - Implemented `GetVacationAccountSummaryService` resolving employee yearly vacation summaries (`carryOverMinutes`, `entitlementMinutes`, `correctionsMinutes`, `usageMinutes`, `remainingMinutes`) and sorted entry journals.
  - Enhanced `VacationHelper` with `getVacationEntries` and `getAllVacationEntries` sorted chronological journals and oldest-balance consumption reconciliation.
  - Updated `AddVacationCorrectionService` to enforce balance checks preventing negative balances on retroactive or current negative corrections.
  - Updated `ApproveAbsenceService` and `CancelAbsenceService` to maintain complete metadata (`PARAM_COMMENT`, `PARAM_CREATED_BY`, versioning) and audit events for `VACATION_USAGE` and `VACATION_CORRECTION` journal entries.
  - Configured Strolch service and search privileges in `PrivilegeRoles.xml` for `Employee`, `Supervisor`, `HR`, and `StrolchAdmin`.
  - Added comprehensive test suite in `VacationJournalTest` covering append-only entries, year-over-year carry-over with oldest-balance consumption, absence approval usage deduction, absence cancellation refund reversal, negative correction rejection on insufficient balance, date-range/type search filtering, and audit logging.
  - Verified with full test suite passing via `mvn test` (60 tests passing across all reactor modules with 0 failures and 0 errors).
- **Dependencies:** Tasks 4 and 7.

### 9. Complete vacation and absence REST surfaces

- **Status:** `COMPLETED`
- **Scope:** Complete `/me/absences`, vacation-account, and related status routes, DTOs, authorization, pagination, and error/concurrency handling.
- **Acceptance:** Documented routes return calculated values; invalid dates/statuses and unauthorized cross-user access are rejected.
- **Verification:**
  - Implemented `VacationAccountEntryDto`, `VacationAccountSummaryDto`, `VacationEntitlementCalculationDto`, and `VacationEntitlementCreditDto` in `chronivaro-rest`.
  - Added mapper functions `vacationEntryToDto` and `vacationSummaryToDto` in `ChronivaroMapper`.
  - Implemented `GET /chronivaro/v1/me/vacation-account` in `ChronivaroResource` returning full yearly summary breakdown and journal entries for the current employee with year resolution.
  - Enhanced `GET /chronivaro/v1/me/absences` in `ChronivaroResource` to support date range (`from`, `to`), `status`, `absenceTypeCode`, and pagination (`offset`, `limit`).
  - Implemented cross-user ownership verification and concurrency control on `GET /chronivaro/v1/me/absences/{id}`, `PUT /chronivaro/v1/me/absences/{id}`, and `POST /chronivaro/v1/me/absences/{id}/cancel` (rejecting cross-user access with 403 Forbidden and stale updates with 409 Conflict).
  - Updated `RequestAbsenceService` to return `StringResult(absenceId)` on creation and returned created `AbsenceDto` with ETag.
  - Implemented `GET /chronivaro/v1/admin/employees/{id}/vacation-account` supporting both `summary=true` breakdowns and paged journal entry lists.
  - Implemented `POST /chronivaro/v1/admin/employees/{id}/vacation-entitlement/calculate` and `POST /chronivaro/v1/admin/employees/{id}/vacation-entitlement/credit` in `EmployeeResource`.
  - Added comprehensive integration tests in `VacationAndAbsenceRestTest` covering personal vacation accounts, admin vacation accounts with pagination, calculations, credits, corrections, absence date/type/status filtering, optimistic concurrency, and cross-user authorization enforcement.
  - Verified with full project test suite passing via `mvn test` (59 tests passing across all reactor modules with 0 failures and 0 errors).
- **Dependencies:** Tasks 2.1, 2.2, 3, 6, and 8.

### 10. Build supervisor approval queues

- **Status:** `COMPLETED`
- **Scope:** Add scoped Core searches/services and REST resources for absence and submitted-period approval queues using the decided approver rules.
- **Acceptance:** Results are scope-limited and paginated; transitions are atomic and audited; queue and authorization tests pass.
- **Verification:**
  - Implemented `AbsenceSearch` in `chronivaro-core` supporting fluent queries by state, employee, multiple employees, and absence type with Strolch privilege assertions.
  - Enhanced `TimePeriodSearch` to support `forEmployees(Collection<String> employeeIds)`.
  - Implemented supervisor scoping and authorization helpers in `ChronivaroModelHelper` (`getSupervisedEmployeeIds`, `findEmployeesByTeam`, `assertCanManageEmployee` with self-approval prevention).
  - Enforced supervisor authorization and self-approval restrictions in `ApproveAbsenceService`, `RejectAbsenceService`, `ApprovePeriodService`, and `RejectPeriodService`.
  - Enriched `ApprovalsResource` (`GET /chronivaro/v1/approvals/periods` and `GET /chronivaro/v1/approvals/absences`) with team/employee/date/type filtering, supervisor scope limiting, pagination, and atomic approval/rejection handling.
  - Configured Strolch search and service privileges in `PrivilegeRoles.xml` for `Employee`, `Supervisor`, and `HR`.
  - Added comprehensive REST integration test suite in `ApprovalsQueueTest` covering queue scoping, cross-team approval rejections (403 Forbidden), self-approval rejections (403 Forbidden), HR escalations, rejections with mandatory comments, and pagination.
  - Verified with full project test suite passing via `mvn clean test` (62 tests passing with 0 failures and 0 errors across all modules).
- **Dependencies:** Tasks 2.1, 2.2, 4, 5, 6, and 9.

### 11. Add personal workflow and approval UI

Task 11 was split into subtasks **11.1**, **11.2**, and **11.3** per the task-size and single-concept rules (avoiding changes across 10+ files simultaneously and separating employee self-service from supervisor approvals).

#### 11.1. Personal Absences and Vacation Account UI

- **Status:** `COMPLETED`
- **Scope:** Add Vanilla JS views (`MyAbsencesView.js`) and API clients (`AbsenceApi.js`, `VacationApi.js`) for submitting absence requests (with date ranges, duration types, half-day parts, hours/minutes, absence type selection, comments), viewing personal absence history with statuses/reasons and cancellation actions, and viewing the personal vacation account summary (yearly entitlement, carry-over, corrections, usage, remaining balance) and immutable journal entries with year filtering.
- **Acceptance:** Loading, empty, success, error, and validation states are covered; server authorization remains authoritative; integration with application router and navigation.
- **Verification:**
  - Implemented `Rest.js` enhancements supporting custom headers (e.g. `If-Match` for optimistic concurrency) and comprehensive error parsing.
  - Implemented `AbsenceApi.js` supporting `getMyAbsences` (with date range, status, type filters), `getAbsenceTypes`, `requestAbsence`, `getAbsence`, and `cancelAbsence` (with `If-Match` ETag and reason).
  - Implemented `VacationAccountApi.js` supporting `getMyVacationAccount(year)`.
  - Implemented `Format.js` extensions `durationDays`, `date`, and `dateTime`.
  - Implemented `MyAbsencesView.js` featuring vacation summary metrics cards (Entitlement, Carry-Over, Adjustments, Usage, Remaining balance), collapsible immutable vacation journal table with year navigation, personal absences history table with status badges (`SUBMITTED`, `APPROVED`, `REJECTED`, `CANCELLED`), cancellation action prompts, filter bar, and absence request modal dialog with dynamic duration options and validation.
  - Registered navigation link in `index.html` and router mapping in `app.js`.
  - Added CSS styling in `style.css` for vacation cards, journal tables, status badges, filter bars, and modal forms.
  - Created automated test `WebPersonalAbsenceUiTest` verifying web assets, router registration, DOM structure, and full personal absence/vacation REST integration workflow.
  - Verified with full project test suite passing via `mvn clean test` (64 tests passing with 0 failures and 0 errors across all modules).
- **Dependencies:** Tasks 6, 9, and 10.

#### 11.2. Personal Period Workflow and Monthly Closing UI

- **Status:** `MISSING`
- **Scope:** Add Vanilla JS views (`MyPeriodsView.js` / period closing controls) and API client (`PeriodApi.js`) for viewing monthly period summary snapshots, current lifecycle state (`OPEN`, `SUBMITTED`, `APPROVED`, `REJECTED`, `LOCKED`), rejection reasons, submitting monthly periods for supervisor approval, and navigating period history.
- **Acceptance:** Lifecycle states, calculation snapshots, and submission workflows are rendered with loading/empty/error states.
- **Dependencies:** Tasks 6 and 11.1.

#### 11.3. Supervisor Approval Queues UI

- **Status:** `MISSING`
- **Scope:** Add Vanilla JS views (`ApprovalsView.js`) and API client (`ApprovalsApi.js`) for supervisors and HR to review pending absence requests and submitted time periods with team/employee filtering, atomic approval and rejection with mandatory comments, and pagination.
- **Acceptance:** Supervisors see only their scoped team members; approval and rejection actions execute cleanly with optimistic concurrency and error handling.
- **Dependencies:** Tasks 6, 10, and 11.2.

### 12. Implement structured reports and CSV export

- **Status:** `MISSING`
- **Scope:** Add Core report queries/services, REST resources, stable CSV serialization, and UI for day/month/time-balance and absence reports.
- **Acceptance:** Reports use calculated values, respect authorization/date filters, produce deterministic CSV, and have Core/REST/UI tests.
- **Dependencies:** Tasks 3, 5, 8, and 9.

### 13. Complete global configuration administration

- **Status:** `PARTIAL`
- **Scope:** Expose supported Core configuration through authorized REST and UI with validation and audit metadata.
- **Acceptance:** Administrators can update only supported values; invalid values fail consistently and changes are audited.
- **Dependencies:** Tasks 2.1, 3, 4, and 7.

### 14. Create `chronivaro-app` and executable packaging

- **Status:** `MISSING`
- **Scope:** Add the application module and executable artifact with application-owned configuration/startup contracts. Keep Jetty out of Core and REST; remove Tomcat as a runtime requirement.
- **Acceptance:** The parent builds four modules and the documented artifact launches with `java -jar`.
- **Dependencies:** Tasks 2.1–2.3, 3, and the existing modules.

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
