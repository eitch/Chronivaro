# Chronivaro – Implementation Specification

> **Notice:** This monolithic specification file has been refactored and split into modular, well-scoped documents under `docs/specification/`.

The authoritative specification is now located in:

👉 **[docs/specification/README.md](specification/README.md)**

---

## Structure of the Specification

The complete Chronivaro specification is organized into the following modular files:

| Specification Module | Description |
|---|---|
| [01-product-scope.md](specification/01-product-scope.md) | Product goals, user roles, current scope, future extensions, core principles |
| [02-domain-model.md](specification/02-domain-model.md) | Domain entities, attributes, relations, and data invariants |
| [03-business-rules.md](specification/03-business-rules.md) | Calculation rules (target time, actual time, balances, vacation entitlement, capping, validations) |
| [04-business-processes.md](specification/04-business-processes.md) | Workflows (start/stop timer, manual edits, absence approval, monthly closing, user lifecycle) |
| [05-reports-and-exports.md](specification/05-reports-and-exports.md) | Report specifications, CSV export, native PDF generation and branding |
| [06-ui-and-localization.md](specification/06-ui-and-localization.md) | Web UI pages, UX principles, multi-language support (`de`/`en`), language selection priority |
| [07-rest-api.md](specification/07-rest-api.md) | REST API endpoints, conventions, error handling, and authorization rules |
| [08-architecture-and-runtime.md](specification/08-architecture-and-runtime.md) | Maven reactor structure, embedded Eclipse Jetty runtime, lifecycle, Strolch integration |
| [09-security-and-privacy.md](specification/09-security-and-privacy.md) | Authentication, role-based access control (RBAC), data privacy, and audit permissions |
| [10-non-functional-requirements.md](specification/10-non-functional-requirements.md) | Reliability, performance metrics, observability, compatibility, and UTF-8 encoding |
| [11-testing-and-acceptance.md](specification/11-testing-and-acceptance.md) | Unit tests, REST integration tests, UI tests, i18n checks, and acceptance criteria |
| [12-implementation-guidance.md](specification/12-implementation-guidance.md) | Implementation phases, task guidelines, and Definition of Done (DoD) |
| [99-open-decisions.md](specification/99-open-decisions.md) | Open product decisions, baseline assumptions, and clarified specifications |

Please consult [docs/specification/README.md](specification/README.md) as the central entry point for all implementation and architecture tasks.
