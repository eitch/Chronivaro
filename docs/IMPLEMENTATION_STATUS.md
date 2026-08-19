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

- Period workflow (spec sections 6.9, 9.5, 10.1, 13.2): Core period lifecycle completed with auto-creation, submission, approval, rejection, reopening with reason, calculation snapshots, audit trails, period closure locking, and `TimePeriodSearch` (Task 5 completed); REST personal, period, approvals, and admin workflow endpoints with concurrency control and role authorization implemented and verified (Task 6 completed); supervisor approval queues with primary-team scoping, self-approval prevention, cross-team access denial, and pagination implemented and verified (Task 10 completed).
- Vacation accounting (sections 6.7, 9.4, 11.3): configurable vacation entitlement policy, proration engine, whole-minute commercial rounding, yearly account summaries with unlimited carry-over, calculation/crediting services, and absence approval balance blocking implemented and verified (Task 7 completed); immutable append-only journal, search query, yearly account summaries with oldest-balance reconciliation, absence cancellation refunds, and negative correction blocking implemented in Core (Task 8 completed); vacation account and absence REST endpoints (`/me/vacation-account`, `/me/absences` filtering/ownership, admin vacation account, calculation, crediting, and corrections) implemented and verified (Task 9 completed); personal absences and vacation account UI views implemented and verified (Task 11.1 completed).
- Audit logging (sections 5.2, 6.10, 9.3–9.5, 16.3): `ChronivaroAuditHelper` enhanced with full metadata (action, reason, correlation ID from MDC/thread-local, details, date, user, old/new values), `AuditEventSearch` fluent query, and `PurgeAuditEventsService` retention purge logic implemented in Core (Task 4.1 completed); administrative master data mutation services audited (Task 4.2.1 completed); Employee and Schedule services audited (Task 4.2.2 completed); operational Time Tracking, Absence, Vacation, Period, and Configuration services audited (Task 4.2.3 completed); administrative REST endpoint and security implemented (Task 4.3 completed).
- Administration (sections 3.3–3.4, 12.1): employee/team/location/schedule/holiday/absence-type pages and APIs exist, but global configuration has core support only and no REST/UI administration.
- UI coverage (sections 12.1–12.2): dashboard, times, presence, personal absences & vacation account (Task 11.1 completed), personal periods & monthly closing (Task 11.2 completed), supervisor approval queues (Task 11.3 completed), administration pages, and structured reports & CSV export UI (Task 12.2 completed) exist.
- Structured time-balance, absence, vacation, and team reports with CSV serialization and UI (sections 11.1–11.5, 13.2): Core report services, deterministic RFC 4180 UTF-8 BOM CSV export serializer, REST resources under `/chronivaro/v1/reports`, and Web UI views & API client implemented and verified (Tasks 12.1 and 12.2 completed).

## Missing

- Global configuration REST and UI administration (sections 3.3–3.4, 12.1).
- Registration of all required audit fields and audit access controls (sections 6.10 and 16.3), beyond the partial helper implementation.
- REST-wide specification conventions: standard error payload with field errors/correlation ID (Task 2.1), pagination contracts/helpers (Task 2.2), optimistic concurrency control (Task 2.3), and OpenAPI documentation (Task 3) implemented; integration coverage remaining (sections 13.1, 14.2, 18.2).
- Non-functional production controls: structured correlation-ID logs, metrics, health/readiness checks, documented retention/deletion policy, performance evidence, and responsive/accessibility verification (sections 16.3 and 17).

## Specification ambiguity resolved

- Vacation calculation and accounting rules are resolved in section 6.7.1 and section 22: 25 days annual entitlement (480 min/day), commercial rounding to whole minute, technical code `VACATION`, unlimited carry-over, oldest-balance consumption, and no negative vacation balances.
- Product decisions for MVP are resolved in section 22: negative time balances allowed, no rounding, employee assigned to one primary team with supervisor approval, cancellation workflow with reversing journal entries, `Europe/Zurich` default timezone, single legal entity, and home office visible as optional working location.
- Standard REST endpoints are fixed in section 13.2, including `/approvals/absences` and `/approvals/periods`.

## Dependency-ordered next steps

1. Complete global configuration administration (Task 13).
2. Create `chronivaro-app` and executable packaging (Task 14).
3. Implement embedded Jetty lifecycle and configuration (Task 15).
4. Integrate Jersey and serve the frontend from Jetty (Task 16).
5. Add standalone and non-functional verification (Task 17).

The matching dependency-ordered tasks, evidence, dependencies, and acceptance criteria are in `IMPLEMENTATION_BACKLOG.md`.

## Verification basis

- Repository inspection covered `chronivaro-core`, `chronivaro-rest`, `chronivaro-web`, runtime templates/configuration, tests, and `Chronivaro/pom.xml`.
- No source code was modified. Detailed backlog items for every incomplete classification are in `IMPLEMENTATION_BACKLOG.md` under “Verified specification gap analysis — 2026-08-17”.
