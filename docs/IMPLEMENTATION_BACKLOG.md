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

- **Architecture & Deployment:** 4-module Maven reactor (`chronivaro-core`, `chronivaro-rest`, `chronivaro-web`, `chronivaro-app`), JDK 25, embedded Jetty 12 lifecycle, same-server frontend and REST delivery under `/rest/chronivaro/v1`, executable fat-JAR (`chronivaro.jar`).
- **Master Data & Registration:** `Employee`, `Team`, `Location`, `EmploymentScheduleVersion`, `HolidayCalendar`, Strolch user challenge initiation (`SET_PASSWORD`), and token-based password setting.
- **Time Tracking Foundation:** WorkDay/WorkEntry model, dynamic target time calculation, start/stop timer, midnight splitting (24:00 boundary), forgotten timer auto-capping to daily target, weekly working location defaults, historical schedule resolution, and morning/afternoon location uniqueness.
- **Absence Lifecycle, Quotas & Preconfigured Types:** Absence types with `commentRequired` and `visibleOnPublicStatus` flags, 10 standard preconfigured default absence types bootstrapped in `Model.xml` matching Section 6.5.1, draft saving/submission workflow, vacation journal immutability with audited `CORRECTION` adjustments, and year-end `CARRY_OVER` transfer.
- **Monthly Period Calculations & Snapshots:** Immutable `calculationSnapshot` for approved/locked periods, monthly balance carry-forward, and detailed absence categorization.
- **Reporting & Exports:** RFC 4180 CSV exports and native server-side OpenPDF generation for Month, Vacation, and Absence reports.
- **Presence, Audit & System Operations:** Masked binary presence indicators, comprehensive audit trail with retention purging, health probes, and structured logging.
- **Localization & Branding:** Global company branding and complete DE (Swiss German) / EN client-side translations with automated parity validation.

---

## Prioritized Implementation Backlog

### Task 9: Add Preconfigured Default Absence Types to Model.xml

- **Specification Reference:** Section 4.1 (#6), Section 6.5, Section 6.5.1
- **Status:** `COMPLETED`
- **Scope:**
  1. Add resource declarations for the 10 standard preconfigured absence types to `runtime/data/Model.xml`:
     - `VACATION` (Ferien): creditTargetTime=true, deductVacation=true, paid=true, approvalRequired=true, commentRequired=false, allowedDurations=[HALF_DAY, FULL_DAY], visibleOnPublicStatus=false
     - `ILLNESS` (Krankheit): creditTargetTime=true, deductVacation=false, paid=true, approvalRequired=true, commentRequired=false, allowedDurations=[HOURS, HALF_DAY, FULL_DAY], visibleOnPublicStatus=false
     - `ACCIDENT` (Unfall): creditTargetTime=true, deductVacation=false, paid=true, approvalRequired=true, commentRequired=false, allowedDurations=[HOURS, HALF_DAY, FULL_DAY], visibleOnPublicStatus=false
     - `MILITARY_CIVIL_DEFENSE` (Militär / Zivilschutz): creditTargetTime=true, deductVacation=false, paid=true, approvalRequired=true, commentRequired=false, allowedDurations=[HALF_DAY, FULL_DAY], visibleOnPublicStatus=false
     - `DOCTOR_APPOINTMENT` (Arzttermin): creditTargetTime=true, deductVacation=false, paid=true, approvalRequired=false, commentRequired=false, allowedDurations=[HOURS], visibleOnPublicStatus=false
     - `TRAINING` (Weiterbildung): creditTargetTime=true, deductVacation=false, paid=true, approvalRequired=true, commentRequired=true, allowedDurations=[HOURS, HALF_DAY, FULL_DAY], visibleOnPublicStatus=false
     - `PARENTAL_LEAVE` (Elternurlaub): creditTargetTime=true, deductVacation=false, paid=true, approvalRequired=true, commentRequired=false, allowedDurations=[HALF_DAY, FULL_DAY], visibleOnPublicStatus=false
     - `UNPAID_LEAVE` (Unbezahlter Urlaub): creditTargetTime=false, deductVacation=false, paid=false, approvalRequired=true, commentRequired=true, allowedDurations=[HALF_DAY, FULL_DAY], visibleOnPublicStatus=false
     - `OVERTIME_COMPENSATION` (Überstundenkompensation): creditTargetTime=false, deductVacation=false, paid=true, approvalRequired=true, commentRequired=false, allowedDurations=[HOURS, HALF_DAY, FULL_DAY], visibleOnPublicStatus=false
     - `OTHER` (Sonstige Abwesenheit): creditTargetTime=false, deductVacation=false, paid=true, approvalRequired=true, commentRequired=true, allowedDurations=[HOURS, HALF_DAY, FULL_DAY], visibleOnPublicStatus=false
  2. Ensure integration test validates all 10 absence types load properly on clean startup and match metadata rules.
- **Affected Components:**
  - `runtime/data/Model.xml`
  - `chronivaro-core/src/test/resources/data/Model.xml`
  - `chronivaro-core/src/test/java/ch/atexxi/chronivaro/core/AbsenceTypeServiceTest.java`
- **Acceptance Criteria:**
  - `Model.xml` contains resource definitions for all 10 standard absence types matching Section 6.5.1.
  - Upon runtime startup, all 10 types are immediately available for employee absence requests and validation.
- **Verification:**
  - Unit tests in `AbsenceTypeServiceTest.shouldLoadPreconfiguredDefaultAbsenceTypesFromModel` verifying all 10 preconfigured absence types, attributes, duration types, and flags.
  - Full reactor test suite passing cleanly (`mvn clean test`).
- **Dependencies:** None.

---

### Task 10: Automate Vacation Entitlement Granting on Employee Creation and Schedule Corrections

- **Specification Reference:** Section 6.7, Section 6.7.1, Section 7.5
- **Status:** `COMPLETED`
- **Scope:**
  1. In `CreateEmployeeService`, automatically calculate and book initial pro-rated annual vacation entitlement (from `joinDate`/`entryDate` to end of calendar year) as an `ENTITLEMENT` entry in `VacationAccountEntry`.
  2. In `UpdateEmployeeService`, recalculate pro-rated annual entitlement when `exitDate` is set or changed, and generate an audited `CORRECTION` entry in `VacationAccountEntry` for the delta.
  3. In `UpdateScheduleService`, recalculate the annual vacation entitlement when weekly target hours or pensum change, booking the difference as an audited `CORRECTION` entry in `VacationAccountEntry`.
- **Affected Components:**
  - `chronivaro-core/src/main/java/ch/atexxi/chronivaro/core/model/VacationHelper.java`
  - `chronivaro-core/src/main/java/ch/atexxi/chronivaro/core/service/CreateEmployeeService.java`
  - `chronivaro-core/src/main/java/ch/atexxi/chronivaro/core/service/UpdateEmployeeService.java`
  - `chronivaro-core/src/main/java/ch/atexxi/chronivaro/core/service/UpdateScheduleService.java`
  - `chronivaro-core/src/main/java/ch/atexxi/chronivaro/core/service/CreditVacationEntitlementService.java`
  - `chronivaro-core/src/test/java/ch/atexxi/chronivaro/core/VacationEntitlementServiceTest.java`
- **Acceptance Criteria:**
  - Creating a new employee automatically credits pro-rated vacation days for their join year in `VacationAccountEntry`.
  - Updating an employee's `exitDate` or changing schedule employment rate automatically posts a `CORRECTION` entry for the difference without altering prior records.
  - Unit tests verify automated entitlement calculation upon employee creation and adjustments upon schedule/exit date changes.
- **Verification:**
  - Unit tests in `VacationEntitlementServiceTest` (`testAutomatedVacationEntitlementOnEmployeeCreation`, `testAutomatedVacationCorrectionOnExitDateUpdate`, `testAutomatedVacationCorrectionOnScheduleUpdate`) passing and validating pro-rated journal bookings and adjustments.
  - Full reactor test suite passing cleanly (`mvn clean test`).
- **Dependencies:** `VacationAccountEntry` journal and `VacationHelper` baseline.

---

### Task 11: Restrict Employee Work Entry Edits to Shortening/Comments and Add MyTimes UI Controls

- **Specification Reference:** Section 4.1 (#4, #5), Section 6.4, Section 9.1, Section 9.3, Section 12.1 (#1, #2), Section 13.2
- **Status:** `COMPLETED`
- **Scope:**
  1. Updated `StopTimerService` to accept an optional `comment` string and persist it to the `WorkEntry`. Updated `DashboardView.js` to allow entering comments when stopping the timer.
  2. Updated `CorrectWorkEntryService` and `ChronivaroResource`:
     - For regular employee role: enforced that `start` is unchanged, `end` is less than or equal to previous `end` (shorten-only restriction), and permitted updating `comment`.
     - Rejected attempts by regular employees to move `start` earlier or extend `end` with validation errors.
     - Separated administrative full corrections and deletions to privileged endpoints (`PUT /admin/work-entries/{id}`, `DELETE /admin/work-entries/{id}`) and `RemoveWorkEntryService`.
  3. Updated `MyTimesView.js` to provide action controls and a modal dialog for shortening end times and editing comments on recorded work entries.
  4. Updated German (`de.json`) and English (`en.json`) translations with full key parity.
- **Affected Components:**
  - `chronivaro-core/src/main/java/ch/atexxi/chronivaro/core/service/StopTimerService.java`
  - `chronivaro-core/src/main/java/ch/atexxi/chronivaro/core/service/CorrectWorkEntryService.java`
  - `chronivaro-core/src/main/java/ch/atexxi/chronivaro/core/service/RemoveWorkEntryService.java`
  - `chronivaro-core/src/main/java/ch/atexxi/chronivaro/core/model/ChronivaroModelHelper.java`
  - `chronivaro-core/src/test/java/ch/atexxi/chronivaro/core/WorkEntryServiceTest.java`
  - `chronivaro-rest/src/main/java/ch/atexxi/chronivaro/rest/resource/ChronivaroResource.java`
  - `chronivaro-web/src/main/webapp/js/api/WorkEntryApi.js`
  - `chronivaro-web/src/main/webapp/js/pages/DashboardView.js`
  - `chronivaro-web/src/main/webapp/js/pages/MyTimesView.js`
  - `chronivaro-web/src/main/webapp/i18n/de.json`
  - `chronivaro-web/src/main/webapp/i18n/en.json`
- **Acceptance Criteria:**
  - Timer stop persists optional work entry comments.
  - Calling `PUT /me/work-entries/{id}` allows employees to shorten the end time and update comments, but rejects any start time modification or end time extension.
  - `MyTimesView.js` provides UI dialogues for editing comments and shortening time blocks.
  - Full corrections and deletions available to administrators.
- **Verification:**
  - Unit tests in `WorkEntryServiceTest` (`shouldStopTimerWithComment`, `shouldAllowEmployeeToShortenWorkEntryAndEditComment`, `shouldRejectEmployeeExtendingWorkEntry`, `shouldRejectEmployeeModifyingStartTime`, `shouldAllowAdminToPerformFullCorrectionAndDeletion`) passed.
  - Full reactor test suite passing cleanly (`mvn clean test`).
- **Dependencies:** `CorrectWorkEntryService` and `StopTimerService` baseline.

---

### Task 12: Implement Detailed Monthly Period Inspection Endpoint and Approvals Detail View

- **Specification Reference:** Section 4.1 (#11), Section 9.5, Section 12.1 (#6), Section 13.2
- **Status:** `COMPLETED`
- **Scope:**
  1. Implemented `GET /approvals/periods/{id}` endpoint in `ApprovalsResource.java` returning the complete `MonthSummaryDto` (daily breakdown, work intervals, breaks, absences, target/actual calculations, comments) for the submitted period under supervisor authorization with scoping checks.
  2. Updated `MonthSummaryService.java` and `PeriodHelper.java` to populate and preserve daily work entry ranges and breaks across live queries and calculation snapshots.
  3. Extended `ApprovalsApi.js` and `ApprovalsView.js` in `chronivaro-web` with a detailed period inspection dialog/modal (`#period-inspect-modal`).
  4. Added "Inspect" button to submitted period rows and enabled supervisors to review the full daily breakdown in the inspection modal and approve or reject (with required reason) directly from that detail view as well as the summary table.
  5. Added corresponding i18n translation keys in German (`de.json`) and English (`en.json`).
- **Affected Components:**
  - `chronivaro-core/src/main/java/ch/atexxi/chronivaro/core/service/MonthSummaryService.java`
  - `chronivaro-core/src/main/java/ch/atexxi/chronivaro/core/model/PeriodHelper.java`
  - `chronivaro-rest/src/main/java/ch/atexxi/chronivaro/rest/resource/ApprovalsResource.java`
  - `chronivaro-rest/src/test/java/ch/atexxi/chronivaro/rest/ApprovalsQueueTest.java`
  - `chronivaro-web/src/main/webapp/js/api/ApprovalsApi.js`
  - `chronivaro-web/src/main/webapp/js/pages/ApprovalsView.js`
  - `chronivaro-web/src/main/webapp/i18n/de.json`
  - `chronivaro-web/src/main/webapp/i18n/en.json`
- **Acceptance Criteria:**
  - `GET /chronivaro/v1/approvals/periods/{id}` returns the full monthly detail report for the submitted period to authorized supervisors.
  - `ApprovalsView.js` allows clicking a submitted period row to open a full inspection modal displaying daily time blocks, breaks, absences, and balances.
  - Direct approve and reject actions can be executed from within the inspection modal.
- **Verification:**
  - REST test in `ApprovalsQueueTest` validating `GET /approvals/periods/{id}` 200 OK return with day summaries, 403 Forbidden for unsupervised employee periods, 404 Not Found for missing periods, and HR/Admin permissions.
  - Full reactor test suite passing cleanly (`mvn clean test`).
- **Dependencies:** `MonthSummaryService` baseline.

---

### Task 13: Implement User Management for Pure System Users (Core Services, REST API, Web UI)

- **Specification Reference:** Section 3.6, Section 6.1.1, Section 9.7, Section 12.1 (#8), Section 13.2
- **Status:** `COMPLETED`
- **Scope:**
  1. Implemented core services in `chronivaro-core`:
     - `CreateUserService`: creates Strolch `User` with roles (e.g. `Administrator`, `HR`, `Supervisor`, `Employee`), username, and name without creating an `Employee` resource.
     - `UpdateUserService`: updates pure user properties and role assignments.
     - `InitiateUserRegistrationService`: initiates the `Usage.SET_PASSWORD` challenge token for pure users.
  2. Implemented REST endpoints in `chronivaro-rest`:
     - `GET /chronivaro/v1/admin/users`: list all Strolch users with metadata and assigned roles.
     - `GET /chronivaro/v1/admin/users/{id}`: get specific user details.
     - `POST /chronivaro/v1/admin/users`: create a new pure system user.
     - `PUT /chronivaro/v1/admin/users/{id}`: update user details and roles.
     - `POST /chronivaro/v1/admin/users/{id}/register`: initiate password setting challenge for the user.
  3. Implemented Web UI in `chronivaro-web`:
     - Added `UsersView.js` under Administration navigation.
     - Supported listing users, adding/editing pure users, role selection, and triggering password setup tokens.
     - Synchronized i18n translation keys in `de.json` and `en.json` with 100% key parity.
     - Updated `index.html` and `app.js` routing for `#users`.
- **Affected Components:**
  - `chronivaro-core/src/main/java/ch/atexxi/chronivaro/core/service/CreateUserService.java`
  - `chronivaro-core/src/main/java/ch/atexxi/chronivaro/core/service/UpdateUserService.java`
  - `chronivaro-core/src/main/java/ch/atexxi/chronivaro/core/service/InitiateUserRegistrationService.java`
  - `chronivaro-core/src/test/java/ch/atexxi/chronivaro/core/UserServiceTest.java`
  - `chronivaro-rest/src/main/java/ch/atexxi/chronivaro/rest/resource/UserResource.java`
  - `chronivaro-rest/src/main/java/ch/atexxi/chronivaro/rest/dto/UserDto.java`
  - `chronivaro-rest/src/main/java/ch/atexxi/chronivaro/rest/ChronivaroRestfulClasses.java`
  - `chronivaro-rest/src/test/java/ch/atexxi/chronivaro/rest/UserResourceTest.java`
  - `chronivaro-web/src/main/webapp/js/api/UserApi.js`
  - `chronivaro-web/src/main/webapp/js/pages/UsersView.js`
  - `chronivaro-web/src/main/webapp/index.html`
  - `chronivaro-web/src/main/webapp/js/app.js`
  - `chronivaro-web/src/main/webapp/i18n/de.json`
  - `chronivaro-web/src/main/webapp/i18n/en.json`
  - `runtime/config/PrivilegeRoles.xml`
- **Acceptance Criteria:**
  - Administrators can create, list, and update pure Strolch users without requiring an `Employee` resource.
  - Password initialization challenge (`SET_PASSWORD`) can be triggered for pure users via `POST /users/{id}/register` and the UI.
  - Pure users can log in, receive their privileges (e.g. Admin, HR, Supervisor), and operate without time-tracking records.
  - Unit and REST integration tests verify pure user lifecycle.
- **Verification:**
  - Unit tests in `UserServiceTest` (`shouldCreateUpdateAndInitiateRegistrationForPureUser`, `shouldFailToCreateDuplicateUser`) passed.
  - REST integration tests in `UserResourceTest` (`shouldPerformCrudAndRegistrationOnUsers`) passed.
  - Full reactor test suite passing cleanly (`mvn clean test`).
- **Dependencies:** None.

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
