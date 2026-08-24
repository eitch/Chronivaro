# Chronivaro

Chronivaro is a Resource-Order-Activity based working time and absence management system built on top of the [Strolch](https://strolch.li) framework. It delivers precise time recording, multi-block daily time tracking with derived break intervals, vacation journal accounting, period closing workflows, role-based approval queues, deterministic CSV reporting, and tenant-wide administration through a lightweight single-page Web UI and a REST API served by an embedded Eclipse Jetty runtime.

---

## Key Features

- **Time Tracking & Timer Controls**: Real-time timer start/stop operations, manual work entry creation and editing, automatic break derivation from time gaps, and overnight block handling.
- **Absence Management**: Multi-type absence requests (Vacation, Sickness, Military/Civil Defense, Paid/Unpaid Leave) with configurable duration units (Full-Day, Half-Day, Hours), vacation quota accounting, and balance progression.
- **Supervisor Approval Queues**: Dedicated approval inbox for team supervisors and HR managers with optimistic concurrency validation, team/type filters, and mandatory rejection feedback.
- **Monthly Period Closing Workflow**: Employee period submission, calculation snapshot generation, supervisor approval, rejection comment inspection, and administrator period locking.
- **Reporting & RFC 4180 CSV Export**: Daily summaries, monthly reports, vacation account journals, team overviews, and filtered absence reports with UTF-8 BOM encoding for seamless Excel import.
- **Tenant Administration & User Self-Service**: Master data management for employees, employment schedules, teams, locations, holiday calendars, absence types, global tenant parameters, and user self-service password changes accessible directly from the header user info dropdown.
- **Audit Logging**: Immutable audit records capturing all state transitions, approvals, rejections, locks, and configuration updates.
- **Standalone Embedded Jetty Execution**: Single self-contained fat-JAR (`chronivaro.jar`) delivering both frontend web assets and REST API endpoints without external servlet containers.

---

## Architecture & Module Structure

The project is structured as a multi-module Maven project:

```text
Chronivaro/
├── chronivaro-core/     # Domain model, Strolch services, commands, searches, and policies
├── chronivaro-rest/     # JAX-RS (Jersey) REST endpoints, DTO mappers, auth filters, and error mappers
├── chronivaro-web/      # Single Page Application (Web Components, CSS, JavaScript modules)
├── chronivaro-app/      # Standalone launcher with embedded Eclipse Jetty HTTP server and packaging
├── docs/                # Architecture specifications, OpenAPI definition, and operations guides
├── runtime/             # Strolch runtime directory (configuration, templates, and model data)
└── pom.xml              # Maven reactor parent configuration
```

---

## Prerequisites

- **Java Runtime**: JDK 25 (or JDK 24+)
- **Build Tool**: Maven 3.6+
- **Browser**: Modern web browser (Firefox, Chromium-based browsers, Edge, Safari)

---

## Build & Installation

To build all modules and create the standalone executable JAR:

```bash
mvn clean install
```

To run unit and integration tests across all modules:

```bash
mvn test
```

The resulting executable fat-JAR is located at:
`chronivaro-app/target/chronivaro.jar`

---

## Running Chronivaro

Start Chronivaro as a standalone application using Java:

```bash
java -jar chronivaro-app/target/chronivaro.jar --port 8080 --runtime ./runtime --env dev
```

### Command-Line Arguments & Environment Variables

| Argument | Environment Variable | Default Value | Description |
|---|---|---|---|
| `--port <int>` | `PORT` / `CHRONIVARO_PORT` | `8080` | HTTP port to listen on (`0` binds to a dynamic available port) |
| `--bind <address>` | `BIND_ADDRESS` / `CHRONIVARO_BIND` | `0.0.0.0` | IP address to bind HTTP listener |
| `--context-path <path>` | `CONTEXT_PATH` | `/` | Base HTTP context path for Web UI and REST endpoints |
| `--no-http` | `NO_HTTP` | `false` | Disable HTTP server and run Strolch core runtime only |
| `--runtime <path>` | `STROLCH_PATH` | `./runtime` | Path to Strolch runtime directory containing `config/` and `data/` |
| `--env <name>` | `STROLCH_ENV` / `STROLCH_ENVIRONMENT` | `dev` | Environment configuration profile (e.g. `dev`, `prod`, `test`) |
| `--web-resources <path>`| `WEB_RESOURCES_PATH` | `null` (auto) | Custom filesystem path to override static frontend assets |

---

## 🐳 Running with Docker

### Running for Development (Local Build)

1. Build the project from the root:
   ```bash
   mvn clean package -DskipTests
   ```
2. Start the application using Docker Compose:
   ```bash
   docker compose -f docker-compose-dev.yml up --build
   ```
3. The application will be available at `http://localhost:8080`.

### Building and Pushing to Docker Repository

To build and tag the Docker image locally:
```bash
./build-docker-image.sh
```

To build, tag, and push the image to the remote registry (`repo.strolch.li`):
```bash
./build-and-push-docker.sh
```

### Running with Docker in Production

1. Create directory structure on host:
   ```bash
   mkdir chronivaro
   cd chronivaro
   mkdir -p runtime/{config,data,temp}
   ```
2. Copy configuration files from the `runtime` directory into `runtime/` (`config/`, `data/`, `temp/`).
3. Generate random values for `secretKey` and `secretSalt` in `runtime/config/PrivilegeConfig.xml`.
4. Copy `docker-compose.yml` to the directory.
5. Start the application:
   ```bash
   docker compose up -d
   docker compose logs -f
   ```

---

## System Probes & Telemetry Endpoints

Chronivaro provides unauthenticated standard system probes for container orchestration, health checkers, and telemetry:

| Endpoint | Method | Description |
|---|---|---|
| `/rest/chronivaro/v1/system/health` | `GET` | **Liveness Probe**: Returns HTTP 200 `{"status": "UP", "agentState": "RUNNING", "uptimeMs": ...}` |
| `/rest/chronivaro/v1/system/readiness`| `GET` | **Readiness Probe**: Returns HTTP 200 `{"status": "READY", "activeRealms": [...]}` when ready for traffic |
| `/rest/chronivaro/v1/system/version` | `GET` | **Version Metadata**: Returns application version, build timestamp, environment, and Strolch version |
| `/rest/chronivaro/v1/system/metrics` | `GET` | **JVM & Telemetry**: Returns heap/non-heap memory, active threads, system load average, and uptime |

---

## Observability & Structured Logging

Chronivaro uses SLF4J with Logback for structured log outputs. Every HTTP request is tagged with an `X-Correlation-Id` header and propagated to the logging MDC context:

```text
2026-08-19 13:30:00.123 [qtp1234567-24] [corrId=e4a1b2c3-4d5e-6f7a-8b9c-0d1e2f3a4b5c] INFO  ch.atexxi.chronivaro.rest.resource.ChronivaroResource - Processed presence query
```

Clients can supply custom correlation IDs via the `X-Correlation-Id` request header; otherwise, the server automatically generates and returns a UUID correlation ID in the response headers.

---

## Role-Based Access & Security

Chronivaro enforces role-based privilege checks across all services and REST resources:

- **Employee**: Record work entries, start/stop timers, view team presence ("Who is working?"), view personal balance, submit absence requests, submit personal monthly closing periods.
- **Supervisor**: View team presence, approve/reject team absence requests, review submitted monthly periods for supervised teams with calculation snapshots.
- **HR**: Manage employee profiles, administer vacation entitlements and manual adjustments, unlock/lock periods across the tenant, view all absence reports.
- **Administrator**: Manage global configuration parameters, holiday calendars, teams, locations, schedule templates, and inspect tenant audit logs.
- **StrolchAdmin**: Full framework administration and privilege user/role management.

---

## Documentation

- **[Operations & Deployment Guide](docs/OPERATIONS.md)**: Production deployment, container setup, systemd integration, disaster recovery, and data retention policies.
- **[OpenAPI Specification](docs/openapi.yaml)**: Comprehensive REST API contract with request/response schemas and examples.
- **[Implementation Backlog](docs/IMPLEMENTATION_BACKLOG.md)**: Granular task breakdown and implementation history.
- **[Implementation Status](docs/IMPLEMENTATION_STATUS.md)**: Architectural decisions, delivered milestones, and test suite verification.
