# Chronivaro – Implementierungsleitfaden und Definition of Done

Dieses Dokument bietet Entwicklern und AI-Implementierungsagenten (wie Junie) eine strukturierte Orientierung für die phasenweise, iterative Umsetzung von Aufgaben sowie die verbindliche **Definition of Done**.

## 1. Empfohlene Phasenreihenfolge

Die Entwicklung baut auf aufeinander aufsetzenden Phasen auf:

```text
Phase 1: Fundament & Laufzeit (Maven Reactor, Strolch TX, Embedded Jetty, Auth)
    │
    ▼
Phase 2: Stammdaten & Sollzeiten (Mitarbeiter, Teams, Versionierte Arbeitspläne, Feiertage)
    │
    ▼
Phase 3: Zeiterfassung (WorkDay, WorkEntry, Timer Start/Stopp, Mitternacht-Split, Capping)
    │
    ▼
Phase 4: Abwesenheiten & Ferienkonto (AbsenceTypes, DRAFT-Workflow, VacationAccountEntry Journal)
    │
    ▼
Phase 5: Status & Reporting (Anwesenheit, Monatsreport, Teamreport, CSV-Export)
    │
    ▼
Phase 6: Monatsabschluss & Audit (Genehmigungsworkflow, calculationSnapshot, Audit-Trail)
    │
    ▼
Phase 7: Mehrsprachigkeit & PDF-Export (i18n DE/EN, Login-Sprachwahl, OpenPDF-Renderer, Branding)
```

---

## 2. Arbeitsanweisungen für iterative Implementierungsagenten

Bei der schrittweisen Bearbeitung von Backlog-Aufgaben (z. B. aus `docs/IMPLEMENTATION_BACKLOG.md`) ist folgende Vorgehensweise einzuhalten:

1. **Ein Task zur Zeit:** Bearbeite stets genau eine nummerierte Backlog-Aufgabe isoliert.
2. **Spezifikation konsultieren:** Lies vor Beginn das für den Task zuständige Spezifikationsdokument (siehe Zuordnungstabelle in [README.md](README.md#task-to-specification-mapping)).
3. **Bestehende Muster wiederverwenden:** Untersuche die bestehende Codebasis (`chronivaro-core`, `chronivaro-rest`, `chronivaro-web`, `chronivaro-app`), um bestehende Strolch-Services, Commands, DTOs oder UI-Komponenten konsistent zu erweitern.
4. **Test-First bzw. begleitendes Testen:** Schreibe Unit- und Integrationstests für alle neuen Services und Randfälle gemäss [Teststrategie](11-testing-and-acceptance.md).
5. **Keine verdeckten Annahmen:** Prüfe [Offene Entscheidungen](99-open-decisions.md) bei Unklarheiten zu Standardwerten.

---

## 3. Definition of Done (DoD)

Eine Aufgabe oder Funktion gilt erst dann als vollständig abgeschlossen, wenn alle nachfolgenden Qualitätskriterien nachweislich erfüllt sind:

- [ ] **Fachliche Kriterien:** Alle fachlichen Akzeptanzkriterien des betroffenen Features sind erfüllt.
- [ ] **Automatisierte Core-Tests:** Core-Logik, Berechnungen und Randfälle sind durch automatisierte Unit-Tests abgedeckt und erfolgreich.
- [ ] **REST-Integrationstests:** REST-Endpunkte sind dokumentiert, validieren Eingaben und sind durch Integrationstests abgesichert.
- [ ] **Serverseitige Autorisierung:** Alle Berechtigungsprüfungen sind serverseitig in Transaktionen und Core-Services implementiert.
- [ ] **Revisionssicheres Audit-Log:** Relevante Zustandsänderungen erzeugen strukturierte `AuditEvent`-Einträge mit Vorher-/Nachher-Zustand und Korrelations-ID.
- [ ] **Vollständige UI-Zustände:** Weboberflächen behandeln Lade-, Leer-, Erfolgs- und Fehlerzustände fehlerfrei.
- [ ] **Barrierefreiheit & UX:** WCAG-AA-Kontraste, Tastaturbedienbarkeit und scrollbare Modaldialoge bei Viewport-Overflow sind sichergestellt.
- [ ] **Fehlerfreiheit:** Keine bekannten Fehler hoher Priorität oder Regressionen in bestehenden Testsuiten.
- [ ] **Dokumentation:** Konfigurations- und Betriebsänderungen sind in den entsprechenden Dokumenten nachgeführt.
- [ ] **JDK 25 Maven-Build:** Der gesamte Reaktor-Build (`mvn clean test` bzw. `mvn clean install`) baut mit JDK 25 ohne Fehler reproduzierbar durch.
- [ ] **Embedded Jetty Startfähigkeit:** Die Anwendung lässt sich als Standalone-Anwendung ohne externen Tomcat über `chronivaro-app` starten und stellt Frontend sowie REST-API bereit.
- [ ] **Vollständige Mehrsprachigkeit (i18n):** Bei übersetzbaren Funktionen sind Deutsch (`de`) und Englisch (`en`) zu 100 % gepflegt und die automatisierten Übersetzungstests laufen fehlerfrei durch.
- [ ] **PDF-Konsistenz:** Bei PDF-fähigen Berichten verwendet die PDF-Generierung dieselben Berechnungs- und Berechtigungsregeln wie die Weboberfläche und ist automatisiert getestet.
