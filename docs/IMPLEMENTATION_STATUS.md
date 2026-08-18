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
- Audit logging (sections 5.2, 6.10, 9.3–9.5, 16.3): `ChronivaroAuditHelper` enhanced with full metadata (action, reason, correlation ID from MDC/thread-local, details, date, user, old/new values), `AuditEventSearch` fluent query, and `PurgeAuditEventsService` retention purge logic implemented in Core (Task 4.1 completed); mutation service coverage across all Core services (Task 4.2) and administrative REST view (Task 4.3) remaining.
- Administration (sections 3.3–3.4, 12.1): employee/team/location/schedule/holiday/absence-type pages and APIs exist, but global configuration has core support only and no REST/UI administration.
- UI coverage (sections 12.1–12.2): dashboard, times, presence, and administration pages exist; personal absences, vacations, approvals, reports, period workflow, and explicit loading/empty/error/accessibility coverage remain incomplete.

## Missing

- Structured time-balance and absence reporting, report REST endpoints, CSV serialization, and report UI (sections 11.2, 11.4–11.5, 13.2): no `ReportResource`, report query/service, CSV implementation, or report page was found.
- Supervisor approval queue/API/UI for absences and submitted periods (sections 3.2, 12.1, 13.2): no approval queue query/resource/view was found.
- Automated vacation entitlement engine and configurable policy values (section 6.7.1), subject to the open parameters listed below.
- Registration of all required audit fields and audit access controls (sections 6.10 and 16.3), beyond the partial helper implementation.
- REST-wide specification conventions: standard error payload with field errors/correlation ID (Task 2.1), pagination contracts/helpers (Task 2.2), optimistic concurrency control (Task 2.3), and OpenAPI documentation (Task 3) implemented; integration coverage remaining (sections 13.1, 14.2, 18.2).
- Non-functional production controls: structured correlation-ID logs, metrics, health/readiness checks, documented retention/deletion policy, performance evidence, and responsive/accessibility verification (sections 16.3 and 17).

## Specification ambiguity resolved

- Vacation calculation and accounting rules are resolved in section 6.7.1 and section 22: 25 days annual entitlement (480 min/day), commercial rounding to whole minute, technical code `VACATION`, unlimited carry-over, oldest-balance consumption, and no negative vacation balances.
- Product decisions for MVP are resolved in section 22: negative time balances allowed, no rounding, employee assigned to one primary team with supervisor approval, cancellation workflow with reversing journal entries, `Europe/Zurich` default timezone, single legal entity, and home office visible as optional working location.
- Standard REST endpoints are fixed in section 13.2, including `/approvals/absences` and `/approvals/periods`.

## Dependency-ordered next steps

1. Apply comprehensive audit logging across Core domain mutation services (Task 4.2).
2. Expose and secure the Admin Audit Logs REST endpoint with filtering and pagination (Task 4.3).
3. Complete the period lifecycle, including submission, rejection, reopening, snapshots, and approval endpoints (Task 5).
4. Expose personal and period workflow REST endpoints (Task 6).
5. Implement configurable vacation entitlement and immutable journal accounting (Tasks 7 and 8).
6. Complete vacation and absence REST surfaces (Task 9).
7. Add scoped supervisor approval queues and the approvals UI (Task 10).
8. Add personal absence and vacation pages (Task 11).
9. Implement structured reports and CSV export (Task 12).
10. Run the full non-functional and acceptance verification track.

The matching dependency-ordered tasks, evidence, dependencies, and acceptance criteria are in `IMPLEMENTATION_BACKLOG.md`.

## Verification basis

- Repository inspection covered `chronivaro-core`, `chronivaro-rest`, `chronivaro-web`, runtime templates/configuration, tests, and `Chronivaro/pom.xml`.
- No source code was modified. Detailed backlog items for every incomplete classification are in `IMPLEMENTATION_BACKLOG.md` under “Verified specification gap analysis — 2026-08-17”.
