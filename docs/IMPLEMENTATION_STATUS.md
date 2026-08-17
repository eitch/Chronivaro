# Chronivaro Implementation Status

Audit date: 2026-08-17. `IMPLEMENTATION_SPECIFICATION.md` is authoritative; the existing backlog was not used as evidence.

## Implemented

- MVP project structure, Maven parent, three modules, JDK 25, UTF-8, and Strolch BOM (spec sections 14 and 20.1): `Chronivaro/pom.xml`.
- Employee, team, location, schedule, holiday-calendar, WorkDay, WorkEntry, absence-type, absence, and period model/service foundations: `Chronivaro/runtime/data/Templates.xml` and `chronivaro-core/src/main/java/ch/atexxi/chronivaro/core`.
- Historical schedule lookup and overlap prevention: `ScheduleHelper`, `WorkDayHelper`, and `CreateScheduleService`.
- Timer start/stop, same-day validation, midnight splitting, forgotten-timer capping, multiple blocks, and working-location defaults/overrides: `StartTimerService`, `StopTimerService`, `WorkEntryHelper`, `WorkingLocationDefault*`, and `chronivaro-web/src/main/webapp/js/pages/DashboardView.js`.
- Absence self-service, submit/reject/approve/cancel services, registration flow, presence query/UI, holiday calendar CRUD, and role configuration: corresponding core services, REST resources, `PrivilegeRoles.xml`, and `PresenceView.js`.
- Day and month summary calculations exist: `DaySummaryService`, `MonthSummaryService`, `DaySummaryDto`, and `/me/day-summary/{date}` plus `/me/month-summary/{yearMonth}`.

## Partially implemented

- Period workflow (spec sections 6.9, 9.5, 10.1, 13.2): submit/approve/lock core services exist and `PeriodResource` exposes only admin approve/lock; rejection, reopen-with-reason, period lookup, full status transitions, snapshots, and complete `/me`/approval endpoints are absent.
- Vacation accounting (sections 6.7, 9.4, 11.3): immutable-style correction support and balance lookup exist in `VacationHelper` and `AddVacationCorrectionService`, but entitlement, carry-over, usage linkage, approval balance blocking, and complete account reporting/UI are absent.
- Audit logging (sections 5.2, 6.10, 9.3–9.5, 16.3): `ChronivaroAuditHelper` records element/parameter/old/new/date/user, but not explicit action, reason, correlation ID, complete service coverage, or an authorized audit view.
- Administration (sections 3.3–3.4, 12.1): employee/team/location/schedule/holiday/absence-type pages and APIs exist, but global configuration has core support only and no REST/UI administration.
- UI coverage (sections 12.1–12.2): dashboard, times, presence, and administration pages exist; personal absences, vacations, approvals, reports, period workflow, and explicit loading/empty/error/accessibility coverage remain incomplete.

## Missing

- Structured time-balance and absence reporting, report REST endpoints, CSV serialization, and report UI (sections 11.2, 11.4–11.5, 13.2): no `ReportResource`, report query/service, CSV implementation, or report page was found.
- Supervisor approval queue/API/UI for absences and submitted periods (sections 3.2, 12.1, 13.2): no approval queue query/resource/view was found.
- Automated vacation entitlement engine and configurable policy values (section 6.7.1), subject to the open parameters listed below.
- Registration of all required audit fields and audit access controls (sections 6.10 and 16.3), beyond the partial helper implementation.
- REST-wide specification conventions: standard error payload with field errors/correlation ID, optimistic concurrency, pagination, OpenAPI documentation, and integration coverage (sections 13.1, 14.2, 18.2).
- Non-functional production controls: structured correlation-ID logs, metrics, health/readiness checks, documented retention/deletion policy, performance evidence, and responsive/accessibility verification (sections 16.3 and 17).

## Specification ambiguity

- Vacation day-to-minute conversion, proration rounding, standard vacation absence type ID, and treatment of positive corrections in unlimited carry-over are explicitly open in section 6.7.1 (items 303–308); entitlement automation must not guess them.
- Product decisions remain open in section 21: negative balances, rounding, multi-team approver selection, cancellation rules, illness during vacation, overtime carry-over, home-office status visibility, authentication target, retention, and multi-entity/time-zone scope.
- The specification names both `/approvals/...` and an existing repository convention `/admin/periods/...`; final endpoint paths and OpenAPI status codes are not yet fixed (sections 13.2 and 13.3).

## Dependency-ordered next steps

1. Resolve open product and API decisions in specification sections 6.7.1, 13.2–13.3, 17.3, and 21.
2. Establish shared REST error, correlation-ID, concurrency, pagination, OpenAPI, and authorization foundations.
3. Complete audit fields, service coverage, correlation propagation, and restricted audit access.
4. Complete the period lifecycle, including submission, rejection, reopening, snapshots, and approval endpoints.
5. Implement configurable vacation entitlement and immutable journal accounting.
6. Add scoped supervisor approval queues and the approvals UI.
7. Add personal absence and vacation pages.
8. Implement structured reports and CSV export.
9. Run the full non-functional and acceptance verification track.

The matching dependency-ordered tasks, evidence, dependencies, and acceptance criteria are in `IMPLEMENTATION_BACKLOG.md`.

## Verification basis

- Repository inspection covered `chronivaro-core`, `chronivaro-rest`, `chronivaro-web`, runtime templates/configuration, tests, and `Chronivaro/pom.xml`.
- No source code was modified. Detailed backlog items for every incomplete classification are in `IMPLEMENTATION_BACKLOG.md` under “Verified specification gap analysis — 2026-08-17”.
