# Chronivaro – Architektur und Laufzeit

Dieses Dokument definiert die Modul- und Projektstruktur, die Schichtenarchitektur, das Laufzeitmodell mit eingebettetem Eclipse Jetty sowie die Integration des Strolch-Frameworks.

## 1. Maven-Modulstruktur

Chronivaro ist als Multi-Modul-Maven-Projekt organisiert:

```text
chronivaro/
├── pom.xml                  # Parent-POM (chronivaro-parent)
├── chronivaro-core/         # Fachliche Domänenlogik, Services, Transaktionen
├── chronivaro-rest/         # JAX-RS REST API, DTOs, Error-Handling, PDF-Renderer
├── chronivaro-web/          # Vanilla JS Web-Frontend, statische Assets, i18n
└── chronivaro-app/          # Application Entry Point, Embedded Jetty, Laufzeit
```

### 1.1 Maven-Koordinaten und Build-Vorgaben

```xml
<groupId>ch.eitchnet.chronivaro</groupId>
<artifactId>chronivaro-parent</artifactId>
<version>0.1.0-SNAPSHOT</version>
<packaging>pom</packaging>
```

- **Java-Version:** Java / JDK 25
- **Quellcode- und Report-Encoding:** Strikt `UTF-8`
- **Build-Reproduzierbarkeit:** Vollständig reproduzierbare Maven-Builds (`mvn clean install`)
- **Container-Unabhängigkeit:** Das erzeugte Artefakt ist eine eigenständige, ausführbare Java-Anwendung (Fat-JAR).

---

## 2. Modulverantwortlichkeiten und Abhängigkeitsgrenzen

```text
┌─────────────────────────────────────────────────────────────┐
│                       chronivaro-app                        │
│ (Entry Point, Embedded Jetty, Lifecycle, HTTP-Server-Config)│
└──────────────┬──────────────────────────────┬───────────────┘
               │                              │
               ▼                              ▼
┌──────────────────────────────┐┌──────────────────────────────┐
│       chronivaro-rest        ││        chronivaro-web        │
│(JAX-RS, DTOs, Error, OpenPDF)││ (HTML, CSS, JS, i18n Assets) │
└──────────────┬───────────────┘└──────────────────────────────┘
               │
               ▼
┌──────────────────────────────┐
│       chronivaro-core        │
│(Strolch Model, Services, TX) │
└──────────────────────────────┘
```

### 2.1 `chronivaro-core`

- **Verantwortung:** Domänenmodell, Strolch-Modellinitialisierung, fachliche Services, Commands, Berechnungslogik, Validierungsregeln, Berechtigungsentscheidungen, Audit-Logik und Datenpersistenz innerhalb von Strolch-Transaktionen.
- **Abgrenzung:** Der Core kennt keine REST-DTOs, keine HTTP-/Servlet-Konzepte und keine Browser-APIs. Er besitzt **keine Abhängigkeit zu Eclipse Jetty oder Tomcat**.

### 2.2 `chronivaro-rest`

- **Verantwortung:** JAX-RS-Ressourcen, Request- und Response-DTOs, Validierung von Eingabedaten am API-Rand, Fehler-Mapping, Lokalisierung von REST-Fehlermeldungen, OpenAPI-Dokumentation sowie serverseitige Report-Renderer (OpenPDF).
- **Abgrenzung:** Enthält keine fachlichen Berechnungen (diese liegen im Core) und keine Jetty-spezifischen APIs.

### 2.3 `chronivaro-web`

- **Verantwortung:** Statische Web-Assets (HTML, CSS, Vanilla JavaScript als native ES-Module), UI-Komponenten, Router, Client-State, zentrale API-Client-Schicht, clientseitige Validierungen und Übersetzungsressourcen (`de.json`, `en.json`).

### 2.4 `chronivaro-app`

- **Verantwortung:** Der ausführbare Application Entry Point (`main`-Methode), Initialisierung und kontrollierter Shutdown der Strolch-Runtime und Anwendungs-Services, Initialisierung des eingebetteten Eclipse-Jetty-Servers, Registrierung von Jersey/JAX-RS und Auslieferung der Web-Assets.
- **Abgrenzung:** `chronivaro-app` ist die äusserste Schicht. Sie bindet `core`, `rest` und `web` zusammen. Keine innere Schicht darf von `chronivaro-app` abhängen.

---

## 3. Eingebetteter HTTP-Server (Eclipse Jetty)

Chronivaro wird als eigenständige Java-Anwendung betrieben und benötigt keinen externen Servlet-Container oder Application Server.

### 3.1 Laufzeit-Architektur

```text
Chronivaro Application Process
│
├── Application Bootstrap (chronivaro-app)
│
├── Strolch Runtime & Chronivaro Application Services
│
└── Embedded Eclipse Jetty
    │
    ├── Jersey / JAX-RS Servlet Container
    │   └── REST API (/rest/chronivaro/v1/...)
    │
    └── Static Resource Handler
        └── Web Frontend (/ und /assets/...)
```

### 3.2 Start- und Lifecycle-Orchestrierung

Der Start erfolgt direkt über die Kommandozeile:

```bash
java -jar chronivaro.jar
```

#### Startreihenfolge

1. Konfiguration laden und validieren (Ports, Pfade, Strolch-Umgebung).
2. Strolch Runtime und Chronivaro Core Services initialisieren.
3. Embedded Jetty initialisieren.
4. Jersey JAX-RS REST-Anwendung registrieren.
5. Statische Frontend-Ressourcen konfigurieren und mounten.
6. HTTP-Server starten und Anfragen entgegennehmen.

Kann eine zwingend benötigte Komponente nicht initialisiert werden (z. B. Port belegt oder Konfiguration fehlerhaft), schlägt der Startprozess mit aussagekräftiger Fehlermeldung sofort fehl.

#### Kontrollierter Shutdown

Bei Empfang eines Termination-Signals (SIGTERM / SIGINT) fährt die Anwendung in umgekehrter Startreihenfolge kontrolliert herunter:

1. Annahme neuer HTTP-Requests stoppen.
2. Laufende Requests nach Möglichkeit kontrolliert beenden.
3. Jetty-Server stoppen.
4. Chronivaro-Ressourcen freigeben.
5. Strolch Runtime ordnungsgemäss beenden.

---

## 4. HTTP- und Serverkonfiguration

Die Konfiguration erfolgt über die Standard-Strolch-Konfigurationsmechanismen (keine externen Jetty-XML-Dateien erforderlich):

| Parameter | Beschreibung | Standardwert |
|---|---|---|
| `http.enabled` | Aktivierung des eingebetteten HTTP-Servers | `true` |
| `http.bindAddress` | IP-Adresse, an die der Server bindet | `0.0.0.0` |
| `http.port` | HTTP-Port | `8080` |
| `http.contextPath` | Optionaler Context Path | `/` |
| `http.webResourcePath` | Pfad zu statischen Frontend-Assets | (integriert aus `chronivaro-web`) |

---

## 5. Vollständige Unabhängigkeit von Tomcat

Chronivaro ist vollständig unabhängig von Apache Tomcat:

- Kein installierter Tomcat-Server oder `CATALINA_HOME` erforderlich.
- Kein Deployment in ein `webapps`-Verzeichnis.
- Keine Tomcat-spezifischen Klassen, APIs oder Konfigurationsdateien.
- Standardisierte Jakarta REST- / Servlet-Mechanismen garantieren Portabilität.

---

## 6. Strolch-Framework-Architektur und Transaktionsmodell

- **In-Memory-Modell mit Transaktionen:** Alle schreibenden Operationen auf Ressourcen, Arbeitsplänen, Buchungen und Saldi werden ausschliesslich innerhalb einer `StrolchTransaction` (`agent.openTx(...)`) über Core-Services ausgeführt.
- **Append-Only Kontoführung:** Änderungen an Ferienguthaben und Revisionsereignissen werden als unveränderliche Journale (`VacationAccountEntry`, `AuditEvent`) persistiert.
- **Audit-Integration:** Revisionsrelevante Transaktionen schreiben automatisch unveränderliche Audit-Ereignisse mit Korrelations-ID.
