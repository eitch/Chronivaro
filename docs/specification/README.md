# Chronivaro – Implementation Specification

> **Claim:** Arbeitszeit im Überblick  
> **Status:** Authoritative Specification  
> **Technologiebasis:** Strolch, JDK 25, Maven, Eclipse Jetty (embedded), Jersey / JAX-RS, Vanilla JavaScript

## 1. Übersicht und Zweck

**Chronivaro** ist eine webbasierte Unternehmensanwendung zur verlässlichen, nachvollziehbaren Erfassung und Auswertung von Arbeitszeiten, Abwesenheiten und Ferienguthaben. Sie bietet Mitarbeitenden, Vorgesetzten und der Personaladministration tagesaktuelle Anwesenheitsübersichten, flexible Zeiterfassung sowie revisionssichere Soll-/Ist-Auswertungen und Genehmigungsworkflows.

Diese Spezifikation definiert alle fachlichen, technischen, architektonischen und qualitativen Anforderungen an das Gesamtsystem Chronivaro.

## 2. Struktur der Spezifikation

Um eine effiziente, zielgerichtete und modulare Bearbeitung durch Entwickler und AI-Implementierungsagenten zu ermöglichen, ist die Spezifikation in klar abgegrenzte Themenbereiche unterteilt:

| Datei | Titel / Themenbereich | Inhaltliche Schwerpunkte |
|---|---|---|
| [01-product-scope.md](01-product-scope.md) | Produktziel und Umfang | Zweck, Zielgruppen/Rollen, Produktgrundsätze, aktueller Produktumfang (MVP & Erweiterungen), zukünftige Ausbaustufen |
| [02-domain-model.md](02-domain-model.md) | Fachliches Domänenmodell | Entitäten und Attribute (`Employee`, `User`, `EmploymentScheduleVersion`, `WorkDay`, `WorkEntry`, `AbsenceType`, `Absence`, `VacationAccountEntry`, `HolidayCalendar`, `TimePeriod`, `AuditEvent`, `Configuration`, `OnCallPeriod`) |
| [03-business-rules.md](03-business-rules.md) | Berechnungs- und Geschäftsregeln | Sollzeit, Istzeit, anrechenbare Zeit, Tagessaldo, Periodensaldo, Rundung, automatisierte Ferienanspruchsregeln, vergessene Timer (Edge Cases), Anwesenheitsstatus-Regeln, Validierungen |
| [04-business-processes.md](04-business-processes.md) | Geschäftsprozesse und Workflows | Start/Stopp-Ablauf, manuelle Zeiterfassung/Korrekturen, Abwesenheitsantrag und Genehmigung, Monatsabschluss, Benutzer- und Mitarbeiter-Lifecycle (Registrierung, Deaktivierung, Reaktivierung), Audit-Log-Einsicht |
| [05-reports-and-exports.md](05-reports-and-exports.md) | Reports und Exporte | Tagesübersicht, Monatsreport, Ferienübersicht, Teamreport, Abwesenheitsreport, CSV-Export, serverseitige native PDF-Ausgabe, Layout, Kopf-/Fusszeilen, Branding |
| [06-ui-and-localization.md](06-ui-and-localization.md) | UI und Lokalisierung | Seiten und Ansichten, UI-Grundsätze, Barrierefreiheit (WCAG AA), JavaScript-Architektur (ES-Module), Mehrsprachigkeit (`de`/`en`), deterministische Sprachwahl-Priorität vor/nach Login, Fallback-Kette, Datumsformate |
| [07-rest-api.md](07-rest-api.md) | REST-API-Spezifikation | Konventionen (`/rest/chronivaro/v1`), Fehlerantwort-Format (`errorCode`, `message`), alle Endpunkte und Ressourcenpfade, Autorisierungserwartungen, DTO-/OpenAPI-Hinweise |
| [08-architecture-and-runtime.md](08-architecture-and-runtime.md) | Architektur und Laufzeit | Maven-Modulstruktur (`chronivaro-core`, `chronivaro-rest`, `chronivaro-web`, `chronivaro-app`), Embedded Eclipse Jetty, Lifecycle/Shutdown, HTTP-Konfiguration, Tomcat-Unabhängigkeit, Strolch-Modellierung |
| [09-security-and-privacy.md](09-security-and-privacy.md) | Sicherheit und Datenschutz | Authentifizierung, rollenbasierte Autorisierung (RBAC), getrennt zu prüfende Berechtigungen, Schutz sensibler Abwesenheitsgründe, Audit-Zugriffsregeln, Datenschutz |
| [10-non-functional-requirements.md](10-non-functional-requirements.md) | Nichtfunktionale Anforderungen | Zuverlässigkeit/Transaktionalität, Performance-Grenzwerte, Beobachtbarkeit (Logs, Metriken, Health/Readiness), Browserkompatibilität, UTF-8-Encoding, PDF-Layout-Rendering |
| [11-testing-and-acceptance.md](11-testing-and-acceptance.md) | Teststrategie und Abnahmekriterien | Core-Unit-Tests, REST-Integrationstests, UI-Tests, i18n-Vollständigkeitstests, HTTP/Jetty-Tests, verbindliche Akzeptanzkriterien für den gesamten Produktumfang |
| [12-implementation-guidance.md](12-implementation-guidance.md) | Implementierungsleitfaden & Definition of Done | Empfohlene Phasenreihenfolge für die Umsetzung, verbindliche Definition of Done (DoD), Richtlinien für iterative Implementierungsaufgaben |
| [99-open-decisions.md](99-open-decisions.md) | Offene Entscheidungen & Annahmen | Offene Produktentscheidungen, dokumentierte Standardannahmen (Baseline Assumptions), klargestellte Spezifikationspunkte |

## 3. Verbindlichkeit und Gesamtzusammenhang

- **Gesamtspezifikation:** Alle oben aufgeführten Dokumente bilden in ihrer Gesamtheit die vollständige und verbindliche Spezifikation von Chronivaro.
- **Keine isolierte Betrachtung:** Kein Dokument darf isoliert interpretiert werden. Wenn eine Anforderung Bezug auf andere Bereiche nimmt (z. B. eine REST-Ressource auf Berechtigungen, Geschäftsregeln oder Validierungen), sind die entsprechenden verlinkten Spezifikationsdokumente stets gemeinsam massgebend.
- **Quellenpriorität:** Die Dateien unter `docs/specification/` sind die massgebliche fachliche und technische Referenz (Source of Truth) für das Systemverhalten.

---

## Guidance for implementation agents

Implementation agents (such as Junie or other automated tools) should **not** load all specification files at once for every single task. Doing so is inefficient and consumes context unnecessarily.

Instead, agents must follow this incremental procedure:

1. **Start with this README:** Understand the domain context and locate the relevant specification modules.
2. **Identify affected topic areas:** Use the task mapping table below to determine which primary specification file(s) apply to the backlog task.
3. **Read the primary specification file(s):** Read the designated document thoroughly.
4. **Follow cross-references:** Always follow relative links to cross-cutting concerns (especially `03-business-rules.md`, `07-rest-api.md`, `09-security-and-privacy.md`, and `11-testing-and-acceptance.md`).
5. **Check open decisions:** Consult `99-open-decisions.md` whenever a requirement touches a configured default, an edge case, or an unresolved product decision.
6. **Apply Definition of Done:** Verify that the work satisfies the criteria in `12-implementation-guidance.md` and the acceptance tests in `11-testing-and-acceptance.md`.
7. **No assumption of omission:** The absence of a rule in one specific file never implies permission to bypass requirements documented in another applicable specification file.

### Task-to-Specification Mapping

| Task type | Primary specification file | Supporting / Cross-cutting files |
|---|---|---|
| Domain entity & attribute changes | [02-domain-model.md](02-domain-model.md) | [08-architecture-and-runtime.md](08-architecture-and-runtime.md), [03-business-rules.md](03-business-rules.md) |
| Time & balance calculations, rounding | [03-business-rules.md](03-business-rules.md) | [02-domain-model.md](02-domain-model.md), [11-testing-and-acceptance.md](11-testing-and-acceptance.md) |
| Vacation entitlement & journal rules | [03-business-rules.md](03-business-rules.md), [02-domain-model.md](02-domain-model.md) | [04-business-processes.md](04-business-processes.md), [05-reports-and-exports.md](05-reports-and-exports.md) |
| Absence request & approval workflow | [04-business-processes.md](04-business-processes.md) | [02-domain-model.md](02-domain-model.md), [09-security-and-privacy.md](09-security-and-privacy.md) |
| Time tracking & timer start/stop | [04-business-processes.md](04-business-processes.md) | [03-business-rules.md](03-business-rules.md), [02-domain-model.md](02-domain-model.md) |
| Period closing & approval | [04-business-processes.md](04-business-processes.md) | [03-business-rules.md](03-business-rules.md), [05-reports-and-exports.md](05-reports-and-exports.md) |
| User lifecycle (register, deactivate, reactivate) | [04-business-processes.md](04-business-processes.md) | [02-domain-model.md](02-domain-model.md), [09-security-and-privacy.md](09-security-and-privacy.md) |
| Reports, CSV export & native PDF generation | [05-reports-and-exports.md](05-reports-and-exports.md) | [03-business-rules.md](03-business-rules.md), [06-ui-and-localization.md](06-ui-and-localization.md), [07-rest-api.md](07-rest-api.md) |
| Web UI views, layout, styling, forms | [06-ui-and-localization.md](06-ui-and-localization.md) | [05-reports-and-exports.md](05-reports-and-exports.md), [07-rest-api.md](07-rest-api.md) |
| Multi-language, translations & i18n engine | [06-ui-and-localization.md](06-ui-and-localization.md) | [07-rest-api.md](07-rest-api.md), [11-testing-and-acceptance.md](11-testing-and-acceptance.md) |
| REST endpoints, DTOs & error handling | [07-rest-api.md](07-rest-api.md) | [09-security-and-privacy.md](09-security-and-privacy.md), [06-ui-and-localization.md](06-ui-and-localization.md) |
| Maven build, embedded Jetty, packaging | [08-architecture-and-runtime.md](08-architecture-and-runtime.md) | [10-non-functional-requirements.md](10-non-functional-requirements.md) |
| Roles, permissions & data privacy | [09-security-and-privacy.md](09-security-and-privacy.md) | [07-rest-api.md](07-rest-api.md), [04-business-processes.md](04-business-processes.md) |
| Performance, observability, reliability | [10-non-functional-requirements.md](10-non-functional-requirements.md) | [08-architecture-and-runtime.md](08-architecture-and-runtime.md) |
| Test suite implementation & QA verification | [11-testing-and-acceptance.md](11-testing-and-acceptance.md) | [12-implementation-guidance.md](12-implementation-guidance.md) |
| Implementation phases & Definition of Done | [12-implementation-guidance.md](12-implementation-guidance.md) | [11-testing-and-acceptance.md](11-testing-and-acceptance.md) |
