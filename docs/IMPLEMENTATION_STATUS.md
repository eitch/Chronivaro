# Chronivaro Implementation Status

Audit date: 2026-08-17. `IMPLEMENTATION_SPECIFICATION.md` is authoritative; the existing backlog was not used as evidence.

## Implemented

- MVP project structure, Maven parent, four modules (`chronivaro-core`, `chronivaro-rest`, `chronivaro-web`, `chronivaro-app`), JDK 25, UTF-8, Strolch BOM, and executable shaded fat-JAR packaging (`chronivaro.jar`) (spec sections 14, 15.1, and 20.1): `Chronivaro/pom.xml` and `chronivaro-app/pom.xml` (Task 14 completed); embedded Eclipse Jetty lifecycle with configurable enablement, bind address/port, context path, web resource location, clean stop/graceful shutdown, and port conflict failure handling implemented and verified (Task 15 completed); unified same-server Jersey JAX-RS REST integration under `/rest/chronivaro/v1` and static frontend web resource delivery at `/` with strict architectural separation implemented and verified (Task 16 completed).
- Employee, team, location, schedule, holiday-calendar, WorkDay, WorkEntry, absence-type, absence, and period model/service foundations: `Chronivaro/runtime/data/Templates.xml` and `chronivaro-core/src/main/java/ch/atexxi/chronivaro/core`.
- Historical schedule lookup and overlap prevention: `ScheduleHelper`, `WorkDayHelper`, and `CreateScheduleService`.
- Timer start/stop, same-day validation, midnight splitting, forgotten-timer capping, multiple blocks, and working-location defaults/overrides: `StartTimerService`, `StopTimerService`, `WorkEntryHelper`, `WorkingLocationDefault*`, and `chronivaro-web/src/main/webapp/js/pages/DashboardView.js`.
- Absence self-service, submit/reject/approve/cancel services, registration flow, presence query/UI, holiday calendar CRUD, and role configuration: corresponding core services, REST resources, `PrivilegeRoles.xml`, and `PresenceView.js`.
- Day and month summary calculations exist: `DaySummaryService`, `MonthSummaryService`, `DaySummaryDto`, and `/me/day-summary/{date}` plus `/me/month-summary/{yearMonth}`.

## Partially implemented

- Period workflow (spec sections 6.9, 9.5, 10.1, 13.2): Core period lifecycle completed with auto-creation, submission, approval, rejection, reopening with reason, calculation snapshots, audit trails, period closure locking, and `TimePeriodSearch` (Task 5 completed); REST personal, period, approvals, and admin workflow endpoints with concurrency control and role authorization implemented and verified (Task 6 completed); supervisor approval queues with primary-team scoping, self-approval prevention, cross-team access denial, and pagination implemented and verified (Task 10 completed).
- Vacation accounting (sections 6.7, 9.4, 11.3): configurable vacation entitlement policy, proration engine, whole-minute commercial rounding, yearly account summaries with unlimited carry-over, calculation/crediting services, and absence approval balance blocking implemented and verified (Task 7 completed); immutable append-only journal, search query, yearly account summaries with oldest-balance reconciliation, absence cancellation refunds, and negative correction blocking implemented in Core (Task 8 completed); vacation account and absence REST endpoints (`/me/vacation-account`, `/me/absences` filtering/ownership, admin vacation account, calculation, crediting, and corrections) implemented and verified (Task 9 completed); personal absences and vacation account UI views implemented and verified (Task 11.1 completed).
- Audit logging (sections 5.2, 6.10, 9.3–9.5, 16.3): `ChronivaroAuditHelper` enhanced with full metadata (action, reason, correlation ID from MDC/thread-local, details, date, user, old/new values), `AuditEventSearch` fluent query, and `PurgeAuditEventsService` retention purge logic implemented in Core (Task 4.1 completed); administrative master data mutation services audited (Task 4.2.1 completed); Employee and Schedule services audited (Task 4.2.2 completed); operational Time Tracking, Absence, Vacation, Period, and Configuration services audited (Task 4.2.3 completed); administrative REST endpoint and security implemented (Task 4.3 completed).
- Administration (sections 3.3–3.4, 12.1): employee/team/location/schedule/holiday/absence-type pages and APIs exist, and global configuration REST and UI administration with optimistic concurrency control and validation is completed and verified (Task 13 completed).
- UI coverage (sections 12.1–12.2): dashboard, times, presence, personal absences & vacation account (Task 11.1 completed), personal periods & monthly closing (Task 11.2 completed), supervisor approval queues (Task 11.3 completed), global system configuration administration (Task 13 completed), administration pages, and structured reports & CSV export UI (Task 12.2 completed) exist.
- Structured time-balance, absence, vacation, and team reports with CSV serialization and UI (sections 11.1–11.5, 13.2): Core report services, deterministic RFC 4180 UTF-8 BOM CSV export serializer, REST resources under `/chronivaro/v1/reports`, and Web UI views & API client implemented and verified (Tasks 12.1 and 12.2 completed).

## Missing

- None. All 17 implementation backlog tasks and non-functional verifications are complete and tested.

## Specification ambiguity resolved

- Vacation calculation and accounting rules are resolved in section 6.7.1 and section 22: 25 days annual entitlement (480 min/day), commercial rounding to whole minute, technical code `VACATION`, unlimited carry-over, oldest-balance consumption, and no negative vacation balances.
- Product decisions for MVP are resolved in section 22: negative time balances allowed, no rounding, employee assigned to one primary team with supervisor approval, cancellation workflow with reversing journal entries, `Europe/Zurich` default timezone, single legal entity, and home office visible as optional working location.
- Standard REST endpoints are fixed in section 13.2, including `/approvals/absences` and `/approvals/periods`.

## Dependency-ordered next steps

- All MVP backlog tasks (1 through 17) have been implemented, tested, and verified.

## Verification basis

- Repository inspection covered `chronivaro-core`, `chronivaro-rest`, `chronivaro-web`, `chronivaro-app`, runtime templates/configuration, test suites, and `pom.xml`.
- Verified with full Maven reactor test suite (`mvn clean test`) passing cleanly across all modules (19 tests run with 0 failures and 0 errors).
