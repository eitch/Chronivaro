# Chronivaro Operations and Production Guide

This guide describes operational requirements, deployment procedures, system health monitoring, logging, performance expectations, data privacy, and disaster recovery for Chronivaro.

---

## 1. Production Architecture

Chronivaro is packaged as an executable standalone application (`chronivaro.jar`) combining the Strolch in-memory transaction runtime and embedded Eclipse Jetty 12 (EE10) HTTP servlet container.

```mermaid
graph LR
    LB[Load Balancer / Ingress] -->|HTTP/HTTPS| Jetty[Embedded Jetty 12 Server]
    Jetty -->|Static Routing| WebUI[Frontend Web App /]
    Jetty -->|JAX-RS Servlet| Jersey[Jersey REST API /rest/*]
    Jersey -->|Privileged Services| StrolchCore[Strolch Runtime / Core]
    StrolchCore -->|Transactions| Storage[(XML / PostgreSQL Storage)]
```

- **Unified Port Architecture**: Both static frontend web assets (`/`, `/index.html`, `/assets/*`, `/js/*`) and JAX-RS REST endpoints (`/rest/chronivaro/v1/*`) are served on a single HTTP connector port.
- **Strict Layering**: `chronivaro-core` and `chronivaro-rest` maintain zero dependencies on Jetty or servlet container internals.

---

## 2. Deployment Procedures

### 2.1 Standalone Systemd Service Unit

Deploy Chronivaro on Linux using a standard systemd service unit (`/etc/systemd/system/chronivaro.service`):

```ini
[Unit]
Description=Chronivaro Time and Absence Tracking Service
After=network.target

[Service]
Type=simple
User=chronivaro
Group=chronivaro
WorkingDirectory=/opt/chronivaro
ExecStart=/usr/bin/java -Xms512m -Xmx2048m -jar /opt/chronivaro/chronivaro.jar --port 8080 --runtime /opt/chronivaro/runtime --env prod
Restart=always
RestartSec=10
SuccessExitStatus=143
TimeoutStopSec=15

[Install]
WantedBy=multi-user.target
```

Commands:
```bash
sudo systemctl daemon-reload
sudo systemctl enable chronivaro
sudo systemctl start chronivaro
sudo systemctl status chronivaro
```

### 2.2 Containerization & Dockerfile

Example Docker configuration:

```dockerfile
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
COPY chronivaro-app/target/chronivaro.jar /app/chronivaro.jar
COPY runtime /app/runtime
EXPOSE 8080
ENV PORT=8080
ENV STROLCH_PATH=/app/runtime
ENV STROLCH_ENV=prod
ENTRYPOINT ["java", "-Xms512m", "-Xmx2048m", "-jar", "/app/chronivaro.jar"]
```

---

## 3. Health Checks & Monitoring

Chronivaro exposes standard unauthenticated system probes:

### 3.1 Liveness Probe (`GET /rest/chronivaro/v1/system/health`)

Verifies that the HTTP server and Strolch agent are active:

```json
{
  "status": "UP",
  "agentState": "RUNNING",
  "uptimeMs": 3600120,
  "timestamp": "2026-08-19T13:30:00.000+02:00"
}
```

### 3.2 Readiness Probe (`GET /rest/chronivaro/v1/system/readiness`)

Verifies that domain realms are loaded and ready to serve user requests:

```json
{
  "status": "READY",
  "agentState": "RUNNING",
  "activeRealms": ["strolch"],
  "timestamp": "2026-08-19T13:30:00.000+02:00"
}
```
*HTTP Status 200 is returned when ready; 503 SERVICE_UNAVAILABLE is returned when starting up or shutting down.*

### 3.3 System Metrics (`GET /rest/chronivaro/v1/system/metrics`)

Returns runtime memory, thread pool, and CPU telemetry:

```json
{
  "heapUsedBytes": 134217728,
  "heapMaxBytes": 2147483648,
  "heapFreeBytes": 402653184,
  "nonHeapUsedBytes": 67108864,
  "activeThreads": 28,
  "peakThreadCount": 35,
  "totalStartedThreadCount": 94,
  "availableProcessors": 8,
  "systemLoadAverage": 1.25,
  "uptimeMs": 3600120,
  "timestamp": "2026-08-19T13:30:00.000+02:00"
}
```

---

## 4. Structured Logging & Distributed Tracing

Chronivaro attaches a correlation ID to every request through `CorrelationIdFilter` and Logback MDC:

- **MDC Key**: `correlationId`
- **HTTP Header**: `X-Correlation-Id`
- **Pattern**: `%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] [corrId=%X{correlationId:-NONE}] %-5level %logger{36} - %msg%n`

### Error Response Format

All REST error responses return standardized JSON error objects:

```json
{
  "error": "BAD_REQUEST",
  "message": "Start time cannot be after end time",
  "details": [
    {
      "field": "endTime",
      "code": "INVALID_CHRONOLOGY"
    }
  ]
}
```

---

## 5. Performance Benchmarks & SLAs

Chronivaro is engineered for low latency:

| Operation | Target Response Time (SLA) | Tested Performance |
|---|---|---|
| Daily Summary Calculation | `< 500 ms` | `~15 ms` |
| Monthly Employee Summary | `< 2000 ms` | `~45 ms` |
| Team Monthly Report (100 Employees) | `< 5000 ms` | `~120 ms` |
| Presence Status Overview | `< 500 ms` | `~10 ms` |
| RFC 4180 CSV Stream Export | `< 2000 ms` | `~35 ms` |

Server-side pagination (`offset` and `limit`) prevents unbounded memory allocations on large query volumes.

---

## 6. Data Privacy, Retention & Compliance

### 6.1 Role-Based Access Scoping

- **Medical & Sickness Privacy**: Sickness absence reasons and doctor's certificates are masked from team peers and visible exclusively to HR and direct supervisors.
- **Presence Abstract Status**: Unprivileged users querying `/presence` see binary working/absent states rather than sensitive absence categories.

### 6.2 Period Freezing & Tamper Prevention

- Once a monthly period is approved and locked by HR/Administrator (`STATE_LOCKED`), work entries and absences falling into that month cannot be added, edited, or deleted without formal supervisor reopening with mandatory audit justification.

### 6.3 Audit Log Immutability

- Every lifecycle change (timer start/stop, work entry edit, absence approval/rejection, period submission, config update) produces an append-only audit trail accessible under `/rest/chronivaro/v1/admin/audit-logs`.

---

## 7. Backup & Disaster Recovery

1. **Strolch Runtime State**:
   - For file-based persistence: Regularly back up the `runtime/data/` directory.
   - For PostgreSQL persistence: Execute scheduled `pg_dump` of the configured Strolch schema.
2. **Configuration Files**:
   - Maintain version control for `runtime/config/StrolchConfiguration.xml`, `PrivilegeRoles.xml`, `PrivilegeUsers.xml`, and `Templates.xml`.
3. **Session State**:
   - Session tokens are maintained in-memory and persisted across graceful restarts in `runtime/temp/sessions.dat`.
