# Chronivaro Implementation Status

Audit date: 2026-08-19. `IMPLEMENTATION_SPECIFICATION.md` is authoritative; the repository is authoritative for current implementation state.

---

## Implemented (Baseline)

- **Architecture & Deployment (Sections 4.1, 14, 15.1–15.9, 16, 20.1):** 4-module Maven reactor (`chronivaro-core`, `chronivaro-rest`, `chronivaro-web`, `chronivaro-app`), JDK 25, embedded Eclipse Jetty 12 lifecycle, static frontend web asset delivery at `/`, Jersey JAX-RS REST integration under `/rest/chronivaro/v1`, executable standalone fat-JAR (`chronivaro.jar`).
- **Master Data & Registration (Sections 6.1, 6.8, 9.6, 13.7):** `Employee`, `Team`, `Location`, `EmploymentScheduleVersion`, `HolidayCalendar`, Strolch user creation with `SET_PASSWORD` challenge, and token-based initial password setting.
- **Time Tracking Foundation (Sections 6.3, 6.4, 6.4.1, 7.1–7.4, 9.1, 9.2):** WorkDay/WorkEntry model, dynamic target time calculation, multi-interval start/stop timer, midnight 24:00 splitting, forgotten timer auto-capping to daily target, weekly working location defaults, historical schedule version resolution by entry date, duration validation, workday preservation for historical dates, and morning/afternoon location uniqueness rules (**Task 1**).
- **Absence Types & Draft Lifecycle (Sections 6.5, 6.6, 9.4, 10.1, 13.2):** Absence types with `commentRequired` and `visibleOnPublicStatus` metadata, comment enforcement, duration type validation, and full `DRAFT` status / explicit submission workflow (`POST /me/absences/{id}/submit`) (**Task 2**).
- **Vacation Journal Immutability & Year-End Carry-Over (Sections 6.7, 6.7.1, 11.3):** Entitlement recalculations create audited `CORRECTION` adjustments while keeping existing entries immutable; automated year-end carry-over service transfers unexpired balances as `CARRY_OVER` entries with FIFO consumption (**Task 3**).
- **Period Calculation Snapshots & Balance Carry-Forward (Sections 6.9, 11.2, 11.6.2):** Month summaries return immutable `calculationSnapshot` for approved and locked periods; `initialBalance` accurately carries forward prior month closing balance; monthly summary categorizes paid absences, unpaid absences, vacation usage, and holiday credits (**Task 4**).
- **Presence & Privacy (Section 8):** Real-time binary `WORKING`/`NOT_WORKING` presence status with sensitive absence detail filtering for non-privileged viewers unless `visibleOnPublicStatus` is true.
- **Audit Logging & Retention (Sections 6.10, 12, 13.6):** Full audit trail recording entity lifecycle events, parameter mutations, correlation IDs, user details, and retention purge service.
- **Reporting Foundation & CSV Export (Sections 11.1–11.5, 12.1–12.2, 13.8):** Core calculation engines, Web UI report viewers, and deterministic RFC 4180 UTF-8 BOM CSV exports for Day, Month, Vacation, Team, and Absence reports.
- **System Probes & Operations (Sections 13.2, 19, 20):** Unauthenticated health, readiness, version, and metrics probe endpoints; structured logging with MDC correlation IDs.

---

## Missing Requirements (Backlog Items 5–7)

- **Global Application Branding & Configuration (Sections 6.11, 18):** `companyName`, `companyLogo`, and `defaultLanguage` are missing from `GlobalConfiguration` model, REST endpoints, and UI navigation header (**Task 5**).
- **Multi-Language (i18n) Infrastructure & DE/EN Localization (Sections 4.2, 12.3, 16, 18.5):** No translation dictionaries, i18n resolution engine, language switcher, or translation key parity build tests exist (**Task 6.1 & 6.2**).
- **Native PDF Report Generation (Sections 4.2, 11.6.2, 17, 18.6):** Server-side PDF generation engine (OpenPDF/PDFBox) and PDF endpoints for Month, Vacation, and Absence reports are missing (**Task 7.1 & 7.2**).

---

## Specification Ambiguities Clarified

1. **Working Location Half-Day Split Boundary (Sections 6.4 & 6.4.1):** Cutoff between `MORNING` and `AFTERNOON` defined as 12:30 (or schedule midpoint).
2. **REST Route Naming Discrepancies (Sections 13.2 & 13.8):** Query parameters (`?format=pdf`), `Accept` headers, and route aliases are supported.
3. **Negative Vacation Balances (Section 6.7.1 Rule 13):** Strictly prohibited; approvals exceeding available entitlement are blocked.

---

## Verification Basis

- Comprehensive repository inspection across `chronivaro-core`, `chronivaro-rest`, `chronivaro-web`, `chronivaro-app`, configuration templates, and documentation.
- All existing reactor test suites passing cleanly (`mvn clean test`).
