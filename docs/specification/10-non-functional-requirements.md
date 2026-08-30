# Chronivaro – Nichtfunktionale Anforderungen

Dieses Dokument definiert alle qualitativen, technischen und betrieblichen Nichtfunktionalen Anforderungen (NFRs) für Chronivaro.

## 1. Zuverlässigkeit und Datenintegrität

- **Transaktionale Konsistenz:** Alle schreibenden Operationen auf Ressourcen, Arbeitsplänen, Buchungen und Saldi laufen in isolierten Strolch-Transaktionen mit vollständigem Rollback im Fehlerfall.
- **Schutz vor Doppelbuchungen (Idempotenz):** Wiederholte Client-Anfragen (z. B. durch doppeltes Klicken oder Netzwerk-Retries) dürfen keine unbemerkten Doppelbuchungen oder inkonsistenten Timer-Zustände erzeugen.
- **Deterministische Berechnungen:** Alle Berechnungsregeln (Soll-/Istzeiten, Saldi, Ferienkontingente) sind deterministisch und werden durch automatisierte Unit- und Integrationstests abgesichert.
- **Reproduzierbarkeit historischer Perioden:** Einmal genehmigte und gesperrte Perioden (`LOCKED`) bleiben durch unveränderliche Berechnungsergebnisse (`calculationSnapshot`) dauerhaft reproduzierbar, selbst wenn sich Arbeitsmodelle oder Stammdaten später ändern.

---

## 2. Performance und Skalierbarkeit

Für ein typisches Unternehmensszenario gelten unter Normallast folgende maximale serverseitige Antwortzeiten:

| Anwendungsfall | Maximale Antwortzeit (95. Perzentil) |
|---|---|
| Anwesenheits- / Statusseite laden | $\le 2{,}0 \text{ Sekunden}$ |
| Monatsübersicht eines Mitarbeiters berechnen und laden | $\le 2{,}0 \text{ Sekunden}$ |
| Teamreport für 100 Mitarbeitende über einen Monat aggregieren | $\le 5{,}0 \text{ Sekunden}$ |
| Start/Stopp-Timer-Aktion ausführen | $\le 0{,}5 \text{ Sekunden}$ |

- **Server-seitige Paginierung:** Bei grossen Treffermengen (insbesondere im Audit-Log und in Mitarbeiterlisten) wird server-seitige Paginierung mit `offset` und `limit` eingesetzt.

---

## 3. Beobachtbarkeit und Monitoring

- **Strukturierte Protokollierung:** Alle Systemlogs verwenden strukturierte Formate mit konsistent mitgeführter `correlationId`.
- **Keine sensiblen Daten im Log:** Passwörter, Authentifizierungstokens und vertrauliche medizinische Details dürfen unter keinen Umständen in Anwendungs- oder Serverlogs ausgegeben werden.
- **Metriken und Fehlerdiagnose:** Einheitliche Erfassung von Fehlercodes und HTTP-Statusverteilung.
- **Health- und Readiness-Prüfungen:** Bereitstellung technischer Endpunkte zur Prüfung der Systemverfügbarkeit für Container- und Überwachungsumgebungen.

---

## 4. Kompatibilität und Barrierefreiheit

- **Browser-Unterstützung:** Vollständige Funktionalität in aktuellen Versionen von Mozilla Firefox, Google Chrome / Chromium-basierten Browsern, Apple Safari und Microsoft Edge.
- **Keine proprietären Plug-ins:** Kein Einsatz proprietärer Erweiterungen oder Framework-Abhängigkeiten.
- **Responsives Verhalten:** Vollständig nutzbar ab gängigen Smartphone-Bildschirmbreiten (ab 360px).
- **Barrierefreiheit:** Einhaltung der WCAG 2.1 Stufe AA bezüglich Kontrasten, Tastaturfokussierung und screenreader-tauglichen semantischen Elementen.

---

## 5. Zeichensatz und Internationalisierung

- **Einheitliches Encoding:** Quellcode, JSON-Payloads, Logdateien, CSV-Exporte und PDF-Dokumente verwenden ausnahmslos `UTF-8`.
- **Vollständige Lokalisierung:** Deutsch (`de`) und Englisch (`en`) sind zu 100 % abgedeckt.
- **Sprachunabhängige Identifikatoren:** Technische Codes, REST-Fehlercodes (`errorCode`) und fachliche IDs bleiben stabil und sprachneutral.

---

## 6. PDF-Generierung und Rendering

- **Rein serverseitige Erzeugung:** Die PDF-Erzeugung erfolgt direkt im Java-Prozess (mittels OpenPDF) ohne Abhängigkeit von externen Renderern (wie Headless Chrome oder LibreOffice).
- **Schrifteinbettung:** Alle benötigten Schriftarten (inkl. Sonderzeichen und Umlauten) werden in das PDF eingebettet.
- **Logo-Robustheit:** Ist kein Firmenlogo konfiguriert, wird die PDF-Generierung fehlerfrei ohne Platzhalter durchgeführt. Eine ungültige Logo-Konfiguration wird bei der Konfigurationsvalidierung abgewiesen.
