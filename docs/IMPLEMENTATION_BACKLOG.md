# Chronivaro – Implementation Backlog

Audit basis: 2026-08-19. `IMPLEMENTATION_SPECIFICATION.md` is authoritative for requirements; the repository is authoritative for implementation status.

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

---

## Verified Implementation Baseline (Completed Tasks)

The following foundational areas are verified as fully implemented in the repository:

- **Architecture & Deployment:** 4-module Maven reactor (`chronivaro-core`, `chronivaro-rest`, `chronivaro-web`, `chronivaro-app`), JDK 25, embedded Jetty 12 lifecycle, same-server frontend and REST delivery under `/rest/chronivaro/v1`, executable fat-JAR (`chronivaro.jar`).
- **Master Data & Registration:** `Employee`, `Team`, `Location`, `EmploymentScheduleVersion`, `HolidayCalendar`, Strolch user challenge initiation (`SET_PASSWORD`), and token-based password setting.
- **Time Tracking Foundation:** WorkDay/WorkEntry model, dynamic target time calculation, start/stop timer, midnight splitting (24:00 boundary), forgotten timer auto-capping to daily target, and weekly working location defaults.
- **Presence & Privacy:** Binary `WORKING`/`NOT_WORKING` presence status with sensitive absence detail filtering.
- **Audit Logging & Retention:** Comprehensive `AuditEvent` recording with correlation IDs, change tracking, and retention purge service.
- **Reporting Foundation (CSV):** Core calculation engines and deterministic RFC 4180 CSV exports for Day, Month, Vacation, Team, and Absence reports.

---

## Prioritized Implementation Backlog

### Task 1: Fix Manual Work Entry Schedule Versioning, Duration Validation, and Location Half-Day Rules

- **Specification Reference:** Section 6.4, Section 10.1
- **Status:** `COMPLETED`
- **Scope:**
  1. Fix `AddWorkEntryService` to resolve historical schedule version using entry date (`start.toLocalDate()`) via `ScheduleHelper.findScheduleVersion(tx, employeeId, entryDate)`.
  2. Reject manual work entries where `end` is less than or equal to `start` (`start.equals(end)` or `end.isBefore(start)`).
  3. Ensure `WorkDayHelper.getOrCreateWorkDay` does not mutate employee's `currentWorkDayId` when creating/opening historical work days.
  4. Enforce working location constraint: maximum of one distinct `workingLocation` for `MORNING` and one for `AFTERNOON` on the same calendar day.
- **Affected Components:**
  - `chronivaro-core/src/main/java/ch/atexxi/chronivaro/core/service/AddWorkEntryService.java`
  - `chronivaro-core/src/main/java/ch/atexxi/chronivaro/core/service/CorrectWorkEntryService.java`
  - `chronivaro-core/src/main/java/ch/atexxi/chronivaro/core/service/StartTimerService.java`
  - `chronivaro-core/src/main/java/ch/atexxi/chronivaro/core/model/ScheduleHelper.java`
  - `chronivaro-core/src/main/java/ch/atexxi/chronivaro/core/model/WorkDayHelper.java`
  - `chronivaro-core/src/main/java/ch/atexxi/chronivaro/core/model/WorkEntryHelper.java`
  - `chronivaro-rest/src/main/java/ch/atexxi/chronivaro/rest/resource/ChronivaroResource.java`
- **Acceptance Criteria:**
  - Creating a manual entry for a past date correctly links the schedule version valid on that date.
  - Submitting an entry with `start == end` is rejected with `INVALID_ENTRY_DURATION` (400 Bad Request).
  - Adding a manual entry for a past date leaves `currentWorkDayId` pointing to the current day's active workday.
  - Adding multiple conflicting locations in the morning or afternoon window on the same day is rejected.
  - Unit and REST integration tests verify all scenarios.
- **Verification:**
  - Unit tests in `AddWorkEntryServiceTest`, `TimerWorkDayTest`, and `HistoricalScheduleHelperTest`.
  - Integration tests in `ChronivaroResourceTest`.
  - Full reactor test suite passing.
- **Dependencies:** None.

---

### Task 2: Complete Absence Type Metadata and Draft Submission Workflow

- **Specification Reference:** Section 6.5, Section 6.6, Section 9.4, Section 10.1, Section 13.2
- **Status:** `COMPLETED`
- **Scope:**
  1. Add `commentRequired` (boolean) and `visibleOnPublicStatus` (boolean) to `AbsenceType` model, DTOs, and create/update services.
  2. Enforce comment validation in `RequestAbsenceService`: if `commentRequired` is true, reject requests with blank/null comments.
  3. Implement `DRAFT` status workflow for absences:
     - Allow creating absence requests in `DRAFT` status.
     - Allow updating draft absence details (`PUT /me/absences/{id}`).
     - Implement explicit submission service and endpoint (`POST /me/absences/{id}/submit`).
  4. Update public presence/status views so that absence details are hidden unless `visibleOnPublicStatus` is true.
- **Affected Components:**
  - `chronivaro-core/src/main/java/ch/atexxi/chronivaro/core/model/ChronivaroConstants.java`
  - `chronivaro-core/src/main/java/ch/atexxi/chronivaro/core/model/AbsenceHelper.java`
  - `chronivaro-core/src/main/java/ch/atexxi/chronivaro/core/service/CreateAbsenceTypeService.java`
  - `chronivaro-core/src/main/java/ch/atexxi/chronivaro/core/service/UpdateAbsenceTypeService.java`
  - `chronivaro-core/src/main/java/ch/atexxi/chronivaro/core/service/RequestAbsenceService.java`
  - `chronivaro-core/src/main/java/ch/atexxi/chronivaro/core/service/UpdateAbsenceService.java`
  - `chronivaro-core/src/main/java/ch/atexxi/chronivaro/core/service/SubmitAbsenceService.java`
  - `chronivaro-core/src/main/java/ch/atexxi/chronivaro/core/service/PresenceService.java`
  - `chronivaro-rest/src/main/java/ch/atexxi/chronivaro/rest/dto/AbsenceTypeDto.java`
  - `chronivaro-rest/src/main/java/ch/atexxi/chronivaro/rest/dto/ChronivaroMapper.java`
  - `chronivaro-rest/src/main/java/ch/atexxi/chronivaro/rest/resource/AbsenceTypeResource.java`
  - `chronivaro-rest/src/main/java/ch/atexxi/chronivaro/rest/resource/ChronivaroResource.java`
  - `chronivaro-web/src/main/webapp/js/api/AbsenceApi.js`
  - `chronivaro-web/src/main/webapp/js/pages/MyAbsencesView.js`
  - `chronivaro-web/src/main/webapp/js/pages/AbsenceTypesView.js`
- **Acceptance Criteria:**
  - Absence types store and return `commentRequired` and `visibleOnPublicStatus`.
  - Absence requests for types requiring comments fail validation if no comment is supplied.
  - Users can save an absence as `DRAFT`, edit it, and submit it when ready.
  - Public status masks absence types that have `visibleOnPublicStatus = false`.
  - Core and REST integration tests verify draft editing, submission, and validation.
- **Verification:**
  - Unit tests in `AbsenceTypeServiceTest`, `AbsenceServiceTest`, `UpdateAbsenceServiceTest`, and `PresenceServiceTest`.
  - Integration tests in `AbsenceTypeResourceTest`, `ChronivaroResourceTest`, and `ApprovalsQueueTest`.
  - Full reactor test suite passing cleanly (`mvn clean test`).
- **Dependencies:** None.

---

### Task 3: Vacation Journal Immutability, Carry-Over and Year-End Processing

- **Specification Reference:** Section 6.7, Section 6.7.1, Section 11.3
- **Status:** `COMPLETED`
- **Scope:**
  1. Fix `CreditVacationEntitlementService` and vacation adjustment logic to ensure journal immutability: entitlement adjustments or recalculations must append audited `CORRECTION` entries rather than updating existing `ENTITLEMENT` records in-place.
  2. Implement automated year-end vacation carry-over processing service (`YearEndVacationCarryOverService`):
     - Calculate remaining unused vacation minutes at year-end.
     - Generate immutable `CARRY_OVER` journal entries for the subsequent year without balance expiration.
     - Include positive `CORRECTION` amounts into carry-over balances.
  3. Ensure FIFO deduction consumption tracking against oldest available entitlement batches.
- **Affected Components:**
  - `chronivaro-core/src/main/java/ch/atexxi/chronivaro/core/service/CreditVacationEntitlementService.java`
  - `chronivaro-core/src/main/java/ch/atexxi/chronivaro/core/service/YearEndVacationCarryOverService.java`
  - `chronivaro-core/src/main/java/ch/atexxi/chronivaro/core/model/VacationHelper.java`
  - `chronivaro-core/src/main/java/ch/atexxi/chronivaro/core/service/ApproveAbsenceService.java`
  - `runtime/config/PrivilegeRoles.xml`
- **Acceptance Criteria:**
  - Recalculating or correcting entitlements never mutates existing `VacationAccountEntry` records.
  - Year-end service successfully transfers all unexpired vacation balances to the next year as `CARRY_OVER` entries.
  - Approving vacation requests continues to block negative balances and creates immutable `USAGE` journal entries consuming oldest credits first.
  - Comprehensive unit and integration tests cover year-end transitions and journal immutability.
- **Verification:**
  - Unit tests in `YearEndVacationCarryOverServiceTest`, `VacationJournalTest`, and `VacationEntitlementServiceTest`.
  - Full reactor test suite passing cleanly (`mvn clean test`).
- **Dependencies:** None.

---

### Task 4: Integrate Calculation Snapshot and Balance Carry-Forward into Month Reports

- **Specification Reference:** Section 6.9, Section 11.2, Section 11.6.2
- **Status:** `DONE`
- **Scope:**
  1. Update `MonthSummaryService` to check if a requested period is in `APPROVED` or `LOCKED` state, and if so, return the stored immutable `calculationSnapshot` rather than re-calculating live data.
  2. Implement balance carry-forward: `initialBalance` in `MonthSummary` must compute the cumulative ending balance of the previous month (including previous balance, monthly net variance, and manual adjustments).
  3. Expand `MonthSummary` breakdown to explicitly separate paid absence, unpaid absence, vacation usage, and holiday credits as required by Section 11.2.
- **Affected Components:**
  - `chronivaro-core/src/main/java/ch/atexxi/chronivaro/core/service/MonthSummaryService.java`
  - `chronivaro-core/src/main/java/ch/atexxi/chronivaro/core/model/MonthSummary.java`
  - `chronivaro-core/src/main/java/ch/atexxi/chronivaro/core/model/PeriodHelper.java`
  - `chronivaro-core/src/main/java/ch/atexxi/chronivaro/core/model/AbsenceHelper.java`
  - `chronivaro-rest/src/main/java/ch/atexxi/chronivaro/rest/dto/MonthSummaryDto.java`
  - `chronivaro-rest/src/main/java/ch/atexxi/chronivaro/rest/dto/ChronivaroMapper.java`
  - `chronivaro-web/src/main/webapp/js/pages/ReportsView.js`
- **Acceptance Criteria:**
  - For `APPROVED` and `LOCKED` periods, month summaries are served directly from the `calculationSnapshot`.
  - `initialBalance` correctly reflects the prior month's `closingBalance`.
  - Monthly summary clearly itemizes actual working time, target time, holiday credits, paid/unpaid absences, vacation usage, starting balance, period variance, and final balance.
  - Tests verify snapshot retrieval, carry-forward math, and period locking immutability.
- **Verification:**
  - Unit tests in `PeriodLifecycleServiceTest` verifying multi-month balance carry-forward, snapshot serialization/deserialization, and paid/unpaid/vacation absence categorization.
  - Full reactor test suite passing cleanly (`mvn clean test`).
- **Dependencies:** None.

---

### Task 5: Implement Company Branding and Default Language in Global Configuration

- **Specification Reference:** Section 6.11, Section 18
- **Status:** `COMPLETED`
- **Scope:**
  1. Add `companyName` (String), `companyLogo` (String image/base64 URL), and `defaultLanguage` (String, initial `de` or `en`) to `GlobalConfiguration` model and constants.
  2. Update `UpdateConfigurationService` and `ConfigurationDto` with validations (valid language code, valid logo format).
  3. Update `ConfigurationResource` and `ConfigurationView.js` to allow viewing and editing branding and default language.
  4. Update application navigation header in `chronivaro-web` (`index.html`, `app.js`) to display `companyName` and `companyLogo` globally across all views without broken placeholders when no logo is set.
- **Affected Components:**
  - `chronivaro-core/src/main/java/ch/atexxi/chronivaro/core/model/ChronivaroConstants.java`
  - `chronivaro-core/src/main/java/ch/atexxi/chronivaro/core/service/UpdateConfigurationService.java`
  - `chronivaro-rest/src/main/java/ch/atexxi/chronivaro/rest/dto/ConfigurationDto.java`
  - `chronivaro-rest/src/main/java/ch/atexxi/chronivaro/rest/dto/BrandingDto.java`
  - `chronivaro-rest/src/main/java/ch/atexxi/chronivaro/rest/dto/ChronivaroMapper.java`
  - `chronivaro-rest/src/main/java/ch/atexxi/chronivaro/rest/resource/ConfigurationResource.java`
  - `chronivaro-rest/src/main/java/ch/atexxi/chronivaro/rest/resource/SystemResource.java`
  - `chronivaro-web/src/main/webapp/index.html`
  - `chronivaro-web/src/main/webapp/js/app.js`
  - `chronivaro-web/src/main/webapp/js/api/ConfigurationApi.js`
  - `chronivaro-web/src/main/webapp/js/pages/ConfigurationView.js`
  - `chronivaro-web/src/main/webapp/assets/css/style.css`
- **Acceptance Criteria:**
  - Administrators can read and update `companyName`, `companyLogo`, and `defaultLanguage` via REST and UI.
  - The web application header dynamically displays the configured company name and logo across all pages.
  - If no logo is configured, no broken image placeholder or error appears in the UI.
  - Integration tests verify configuration persistence, validation, and REST roundtrips.
- **Verification:**
  - Unit tests in `ConfigurationServiceTest` verifying update, persistence, validation of language and logo, and audit logging.
  - REST and UI integration tests in `ConfigurationResourceTest` and `WebConfigurationUiTest` verifying public branding retrieval, admin PUT/GET with ETag optimistic concurrency, and web asset/header structure assertions.
  - Full reactor test suite passing cleanly (`mvn clean test`).
- **Dependencies:** None.

---

### Task 6: Implement Multi-Language (i18n) Infrastructure and DE/EN Localization

Task 6 is split into subtasks **6.1**, **6.2**, **6.3**, and **6.4** to separate the client-side i18n engine and translation dictionaries, app shell and core authentication/dashboard views, employee self-service views, and master data administration views per task size limits.

#### 6.1: i18n Core Bundle, Language Resolution Engine, and Key Parity Testing
- **Specification Reference:** Section 4.2, Section 12.3, Section 16, Section 18.5
- **Status:** `COMPLETED`
- **Scope:**
  1. Create translation dictionaries `de.json` and `en.json` under `chronivaro-web/src/main/webapp/i18n/` covering all common labels, navigation, button texts, error messages, and enum values.
  2. Implement client-side `I18n.js` module supporting parameterized string formatting, key lookup, and fallback chain: `explicit choice -> localStorage -> Strolch User.locale -> defaultLanguage -> key`.
  3. Implement automated test in `chronivaro-web` to enforce 100% key parity between `de.json` and `en.json`.
- **Acceptance Criteria:**
  - Comprehensive translation dictionaries exist for German (following Swiss German conventions) and English.
  - Language resolution follows the defined priority chain and falls back gracefully.
  - Automated test fails the build if any key is missing in either language bundle.
- **Verification:**
  - Unit tests in `I18nKeyParityTest` in `chronivaro-web` enforcing 100% key parity between `de.json` and `en.json`, no null or empty translations, Swiss German compliance (no `ß`), placeholder consistency, and validating `I18n.js` exports and fallback methods.
  - Full reactor test suite passing cleanly (`mvn clean test`).
- **Dependencies:** Task 5.

#### 6.2: App Shell, Language Switcher, Authentication, and Dashboard Localization
- **Specification Reference:** Section 4.2, Section 16, Section 18.5
- **Status:** `COMPLETED`
- **Scope:**
  1. Add language switcher dropdown to the global navigation header (`#header-language-select`) and login screen (`#login-language-select`).
  2. Add `data-i18n` attributes to all navigation items and implement live re-translation in `app.js` upon language switch without requiring a full page reload.
  3. Migrate `LoginView.js`, `CompleteRegistrationView.js`, and `DashboardView.js` to dynamic translation keys (`I18n.t`).
  4. Persist language selection in `localStorage` and sync with user preferences upon login.
- **Acceptance Criteria:**
  - Login and registration screens allow switching between German and English before authentication.
  - Switching language updates navigation and active view immediately.
  - User-selected language persists across sessions and page reloads.
  - Web UI integration tests verify language switching, navigation attributes, and localized rendering.
- **Verification:**
  - `WebLocalizationUiTest` in `chronivaro-web` asserting header language selector presence, navigation translation keys, `app.js` lifecycle hooks, login/registration view localization, and dashboard view localization.
  - `I18nKeyParityTest` verifying dictionary synchronization and Swiss German compliance.
  - Full reactor test suite passing cleanly (`mvn clean test`).
- **Dependencies:** Task 6.1.

#### 6.3: Employee Self-Service Views Localization
- **Specification Reference:** Section 4.2, Section 16, Section 18.5
- **Status:** `OPEN`
- **Scope:**
  1. Migrate employee self-service views (`MyTimesView.js`, `MyAbsencesView.js`, `MyPeriodsView.js`, `PresenceView.js`, `ApprovalsView.js`, `ReportsView.js`) to use `I18n.t` for table headers, form labels, status badges, buttons, modals, and error dialogues.
  2. Update localized date/time formatting helpers where appropriate.
- **Acceptance Criteria:**
  - All employee-facing time, absence, period, approval, presence, and reporting views render in the active language.
  - Form validation messages and notifications are localized.
- **Dependencies:** Task 6.2.

#### 6.4: Administration Views Localization
- **Specification Reference:** Section 4.2, Section 16, Section 18.5
- **Status:** `OPEN`
- **Scope:**
  1. Migrate master data and system admin views (`EmployeesView.js`, `TeamsView.js`, `LocationsView.js`, `AbsenceTypesView.js`, `HolidayCalendarsView.js`, `ScheduleTemplatesView.js`, `SchedulesView.js`, `ConfigurationView.js`) to use `I18n.t`.
- **Acceptance Criteria:**
  - All admin views render in the active language and respond to language changes.
- **Dependencies:** Task 6.2.

---

### Task 7: Implement Server-Side Native PDF Export Generation

Task 7 is split into subtasks **7.1** and **7.2** to separate the PDF generation engine/layout templates from REST resource streaming and UI download integration.

#### 7.1: PDF Generation Engine and Layout Templates (OpenPDF / PDFBox)
- **Specification Reference:** Section 4.2, Section 11.6.2, Section 17, Section 18.6
- **Status:** `OPEN`
- **Scope:**
  1. Add OpenPDF (or PDFBox) dependency to `pom.xml` and `chronivaro-rest`.
  2. Implement server-side `PdfExportHelper` with A4 page layout, standard company branding header (displaying `companyName` and `companyLogo` from Task 5), document metadata, employee summary box, and page-numbered footer.
  3. Implement PDF report builders for:
     - **Month Report PDF:** Monthly balance summary, calculation snapshot metrics, daily time breakdown table, work intervals, absences, and clear negative balance formatting.
     - **Vacation Summary PDF:** Annual entitlement, carry-over, adjustments, usage list, and remaining balance.
     - **Absence Report PDF:** Filtered employee absence list, absence types, date ranges, duration, and approval status.
- **Acceptance Criteria:**
  - PDF generator produces clean A4 documents with Unicode support without requiring external browser or office dependencies.
  - Documents include consistent header branding, employee information, clear data tables, and page footers.
  - Negative time and balance values are clearly identifiable in monochromatic print.
  - Core/REST unit tests verify deterministic PDF generation and byte streaming.
- **Dependencies:** Task 4, Task 5.

#### 7.2: PDF REST Endpoints and Web UI Download Integration
- **Specification Reference:** Section 4.2, Section 11.6.2, Section 13.2, Section 13.8, Section 17
- **Status:** `OPEN`
- **Scope:**
  1. Add `format=pdf` query parameter support and `Accept: application/pdf` content negotiation to `ReportsResource` for `/reports/month`, `/reports/vacation`, and `/reports/absences`.
  2. Support direct streaming responses (`Response.ok(pdfBytes, "application/pdf").header("Content-Disposition", "attachment; filename=...").build()`) with no persistent server storage.
  3. Add PDF download buttons to `ReportsView.js` and `MyPeriodsView.js` using `Rest.getBlob`.
- **Acceptance Criteria:**
  - REST endpoints stream valid PDF binaries with appropriate MIME types and attachment headers.
  - Users can trigger PDF downloads directly from the Reports and Monthly Closing UI views.
  - Integration tests verify PDF endpoint status codes, headers, and payload validity.
- **Dependencies:** Task 7.1.

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
