# Chronivaro – Teststrategie und Abnahmekriterien

Dieses Dokument definiert die automatisierte Teststrategie über alle Architektur-Schichten sowie die verbindlichen Akzeptanzkriterien für Chronivaro.

## 1. Teststrategie nach Schichten

```text
┌────────────────────────────────────────────────────────┐
│             UI- & Lokalisierungstests                  │ (Vanilla JS Tests, i18n Key-Parity)
├────────────────────────────────────────────────────────┤
│           Runtime- & HTTP-Integrationstests            │ (Embedded Jetty, Static Handler, Lifecycle)
├────────────────────────────────────────────────────────┤
│                REST-Integrationstests                  │ (Jersey, Endpunkte, DTOs, Security, OpenPDF)
├────────────────────────────────────────────────────────┤
│                   Core-Unit-Tests                      │ (Strolch TX, Business Logic, Berechnungen)
└────────────────────────────────────────────────────────┘
```

---

### 1.1 Unit-Tests im Core (`chronivaro-core`)

Der Core wird durch isolierte, deterministische Unit-Tests (unter Verwendung von `RuntimeMock` und Strolch-Testframeworks) abgesichert. Folgende Testszenarien sind verpflichtend:

1. **Sollzeiten & Arbeitspläne:**
   - Sollzeitberechnung für verschiedene Wochentage, Pensen (z. B. 80 %, 100 %) und Arbeitszeitverteilungen.
   - Pensums- und Modellwechsel mitten im Monat ohne Verfälschung historischer Vormonate.
   - Ein- und Austritte mitten im Monat (Sollzeit = 0 für Tage vor `entryDate` bzw. nach `exitDate`).
2. **Feiertage & Abwesenheiten:**
   - Ganze und halbe Feiertage (`creditFactor = 1.0` vs. `0.5`).
   - Ganztägige, halbtägige und stundenweise Abwesenheiten.
   - Krankheit an regulären Arbeitstagen vs. Abwesenheiten an arbeitsfreien Tagen.
   - Ferien über Wochenenden und gesetzliche Feiertage.
3. **Zeiterfassung & Timer-Sonderfälle:**
   - Mehrmaliges Starten und Stoppen mehrerer Arbeitsblöcke pro Tag mit korrekter Ableitung der Unterbrüche.
   - Arbeit über Mitternacht (automatische Aufteilung um 24:00 Uhr und Buchung auf Folgetag).
   - Vergessener Timer: Automatisches Capping auf Tagessollzeit (max. 24:00 Uhr des Starttages).
   - Sommer-/Winterzeit-Umstellung (Zeitzonensicherheit mit `Europe/Zurich`).
   - Blockade überlappender Zeitbuchungen und mehrerer gleichzeitiger Timer.
4. **Saldi & Monatsabrechnung:**
   - Monats- und Gesamtsaldoberechnung über Monats- und Jahresgrenzen hinweg.
   - Unveränderlichkeit von `calculationSnapshot` bei genehmigten Perioden.
5. **Ferienjournal & Kontoführung:**
   - Automatisierte Anspruchsberechnung bei Jahreswechsel (`ENTITLEMENT`) und Eintritt pro-rata.
   - Abzug bei Ferienbezug (`USAGE`) nach FIFO-Prinzip (ältestes Guthaben zuerst).
   - Korrekturbuchungen (`CORRECTION`) und Blockade negativer Feriensaldi.
6. **Benutzer-Lifecycle & Audit:**
   - Benutzerlöschung führt zur Soft-Deaktivierung des verknüpften `Employee` ohne Datenverlust historischer Buchungen.
   - Reaktivierung inaktiver Mitarbeiter mit automatischer Neuerstellung des Strolch-Benutzers und Ferieninitialisierung.
   - Revisionssichere Audit-Log-Erstellung und Filterung nach Korrelations-ID, Entität, Benutzer und Aktion.

---

### 1.2 REST-Integrationstests (`chronivaro-rest`)

Integrationstests validieren die HTTP-Ressourcen gegen die Strolch-Laufzeit:

1. **Endpunkt-CRUD:** Vollständige Abdeckung aller in [REST-API-Spezifikation](07-rest-api.md) definierten Pfade.
2. **Standard-Fehlerbehandlung:** Validierung des Fehler-JSON-Formats (`errorCode`, `message`, `fieldErrors`, `correlationId`) bei ungültigen Payloads.
3. **Autorisierung & Teamgrenzen:** Verifikation, dass Mitarbeiter keine Daten anderer Mitarbeiter sehen/bearbeiten können und Vorgesetzte auf ihre zugeordneten Teams beschränkt sind.
4. **Datenschutz:** Verifikation, dass sensible Abwesenheitsgründe für unberechtigte Aufrufer maskiert werden.
5. **Export & Encoding:**
   - CSV-Export mit UTF-8 und korrektem BOM.
   - Native PDF-Generierung für Monatsreport, Ferienübersicht und Abwesenheitsreport.
   - Prüfung, dass UI, CSV und PDF identische Berechtigungs- und Datenfilter erzwingen.
6. **Lokalisierung von REST-Fehlermeldungen:** Korrekte Sprachauswahl basierend auf `Accept-Language` bei stabilem `errorCode`.
7. **Audit-API:** Abfrage von `/audits` mit Filterkriterien und Paginierung.

---

### 1.3 UI- und JavaScript-Tests (`chronivaro-web`)

1. **Interaktion:** Start/Stopp-Timer, manuelle Zeitanpassungen, Abwesenheitsanträge, Genehmigung und Monatsabschluss.
2. **Darstellung & Barrierefreiheit:**
   - Lade-, Leer- und Fehlerzustände für jede asynchrone Ansicht.
   - Tastaturbedienbarkeit und WCAG-AA-Kontraste.
   - Anzeige von Anwesenheitskarten im flexiblen Grid-Layout.
3. **Sprachwahl- und Persistenztests:**
   - Sprachwahl auf dem Login-Bildschirm und Vorrang vor Browser Storage und Strolch-Benutzer.
   - Sprachwechsel in der angemeldeten Sitzung mit Persistenz im LocalStorage und auf dem Strolch-Benutzer.
4. **Branding:** Anzeige von global konfiguriertem Firmennamen und Firmenlogo (sowie fehlerfreies Ausblenden bei Nicht-Konfiguration).

---

### 1.4 Übersetzungs- und Exporttests

- **100 % Parität:** Automatisierter Testlauf prüft, dass jeder verpflichtende i18n-Schlüssel in `de.json` und `en.json` existiert. Fehlende Schlüssel lassen den Build fehlschlagen.
- **Syntaktische Validierung:** Keine doppelten Schlüssel oder Formatierungsfehler in Übersetzungsdateien.
- **PDF-Validierung:**
  - PDF-Titel und Tabellenköpfe entsprechen der übergebenen Benutzersprache.
  - Datumswerte folgen strikt `yyyy-MM-dd`, Zeitdauern `HH:mm`.
  - Negative Saldi sind auch im Schwarz-Weiss-Ausdruck zweifelsfrei erkennbar.
  - Genehmigte Monatsreports enthalten Status, Genehmigungsdatum und Genehmiger.

---

### 1.5 Runtime- und HTTP-Integrationstests (`chronivaro-app`)

- Start und Stop der Standalone-Anwendung mit Embedded Jetty.
- Parallele Bereitstellung der JAX-RS REST-API unter `/rest/chronivaro/v1` und des Frontends unter `/`.
- Fehlerbehandlung bei blockiertem HTTP-Port (Fail-Fast).
- Kontrollierter Shutdown bei SIGTERM/SIGINT.

---

## 2. Verbindliche Akzeptanzkriterien

Das Gesamtsystem gilt als fachlich und technisch abnahmebereit, wenn alle nachfolgenden Kriterien erfüllt sind:

### 2.1 Kernfunktionalität

1. **Stammdatenverwaltung:** Administratoren können Mitarbeiter, Benutzer (auch reine Systembenutzer), Teams, Standorte, Feiertagskalender und Arbeitspläne verwalten.
2. **Nicht-destruktive Löschung:** Beim Löschen eines Benutzers wird der verknüpfte Mitarbeiter deaktiviert (`active = false`), und alle historischen Buchungen und Saldi bleiben unverändert erhalten.
3. **Mitarbeiter-Reaktivierung:** Inaktive Mitarbeiter können reaktiviert werden; dabei wird das Ferienkonto initialisiert und der Strolch-Benutzer automatisch neu angelegt.
4. **Profileinsicht:** Mitarbeitende können ihre eigenen Profildaten und Sollzeiten in der Weboberfläche einsehen.
5. **Flexible Zeiterfassung:** Mitarbeitende können mehrere Arbeitsblöcke pro Tag erfassen und kommentieren; Unterbrüche werden als zeitliche Lücken ausgewiesen.
6. **Korrekturen und Transparenz:** Mitarbeitende können eigene Buchungen in offenen Perioden bearbeiten; Vorgesetzte und HR können Buchungen für Mitarbeitende erfassen, korrigieren und löschen. Alle modifizierten und manuell erstellten Einträge werden visuell hervorgehoben und der Ersteller (`createdBy`) wird ausgewiesen.
7. **Soll- und Istzeitberechnung:** Korrekte Berechnung von Soll- und Istzeiten für Tag und Monat (inklusive Eintritten/Austritten unter dem Monat).
8. **Abwesenheitsmanagement:** Vordefinierte und kundenspezifische Abwesenheitsarten sind verfügbar; Halb- und Ganztage werden anhand des Arbeitsplans berechnet; Entwürfe (`DRAFT`) können ohne Ferienabzug verworfen werden.
9. **Fremderfassung von Abwesenheiten:** Vorgesetzte und HR können Abwesenheiten im Namen von Mitarbeitenden erfassen.
10. **Automatisierte Ferienkontoführung:** Ferienbezüge erzeugen nachvollziehbare Journaleinträge (`VacationAccountEntry`); negative Feriensaldi werden strikt verhindert.
11. **Anwesenheitsstatus:** Die Statusseite zeigt den aktuellen Zustand binär (`WORKING`/`NOT_WORKING`) ohne vertrauliche Abwesenheitsgründe.
12. **Monatsabschluss-Workflow:** Monatsperioden können eingereicht, über eine detaillierte Inspektionsansicht geprüft, genehmigt, gesperrt und begründet wiedereröffnet werden.
13. **Reports & CSV:** Monats-, Ferien-, Team- und Abwesenheitsreports stehen zur Verfügung und können als CSV exportiert werden.
14. **Audit-Trail:** Alle relevanten Vorgänge werden mit Korrelations-ID protokolliert und können über eine filterbare UI-Ansicht eingesehen werden.

### 2.2 Laufzeit, Mehrsprachigkeit und PDF-Export

15. **Containerloser Betrieb:** Chronivaro startet ohne externen Tomcat als eigenständige Java-Anwendung (`java -jar chronivaro.jar`).
16. **Embedded Jetty:** Frontend und REST-API werden gemeinsam über den eingebetteten Jetty-Server ausgeliefert.
17. **Zweisprachiger Login:** Der Login-Bildschirm erlaubt die explizite Sprachwahl zwischen Deutsch und Englisch.
18. **Sprachpriorität:** Die wirksame Sprache wird vor und nach dem Login nach der definierten Prioritätskette bestimmt und persistent gespeichert.
19. **Vollständige i18n-Abdeckung:** Sämtliche UI-Texte, Validierungen und menschenlesbaren REST-Fehlermeldungen sind in Deutsch und Englisch verfügbar; fehlende Schlüssel brechen den Build ab.
20. **Unternehmensbranding:** Firmenname und optionales Firmenlogo sind global konfigurierbar und werden in Web-UI und PDFs konsistent dargestellt.
21. **Nativer PDF-Export:** Monatsreport, Ferienübersicht und Abwesenheitsreport können serverseitig als PDF erzeugt werden, verwenden dieselben Daten und Berechtigungen wie die Weboberfläche und sind auch monochrom lesbar.
