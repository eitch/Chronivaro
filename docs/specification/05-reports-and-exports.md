# Chronivaro – Reports und Exporte

Dieses Dokument beschreibt alle Auswertungen, Berichtsstrukturen, Exportformate (CSV und natives PDF) sowie die Layout- und Branding-Regeln.

## 1. Übersicht der Reports

Chronivaro stellt folgende standardisierte Auswertungen bereit:

| Report | Zweck | Zielgruppe | Unterstützte Exportformate |
|---|---|---|---|
| **Tagesübersicht** | Detaillierte Tagesanalyse mit Arbeitsblöcken und Unterbrüchen | Mitarbeiter, Vorgesetzter, HR | UI, CSV |
| **Monatsreport** | Vollständige Monatsabrechnung mit Soll/Ist, Abwesenheiten und Saldi | Mitarbeiter, Vorgesetzter, HR, Admin | UI, CSV, **Natives PDF** |
| **Ferienübersicht** | Journal und Stand des Ferienkontos | Mitarbeiter, Vorgesetzter, HR, Admin | UI, CSV, **Natives PDF** |
| **Teamreport** | Team-Monatsübersicht aller Mitarbeitenden eines Teams | Vorgesetzter, HR, Admin | UI, CSV |
| **Abwesenheitsreport** | Übersicht geplanter und genommener Abwesenheiten | Mitarbeiter, Vorgesetzter, HR, Admin | UI, CSV, **Natives PDF** |

*Hinweis zu Exportformaten:* Native Excel-Exporte (`.xlsx`) sind ausdrücklich eine zukünftige Ausbaustufe (siehe [01-product-scope.md](01-product-scope.md#43-spätere-ausbaustufen-explizit-out-of-scope)). Der Teamreport ist ausschliesslich für UI und CSV vorgesehen und gehört nicht zum nativen PDF-Scope.

---

## 2. Fachliche Report-Spezifikationen

### 2.1 Tagesübersicht

Stellt die Arbeitszeiten eines einzelnen Kalendertages detailliert dar:

- **Sollzeit:** Tages-Sollzeit gemäss Arbeitsplan und Feiertagskalender.
- **Arbeitsblöcke:** Auflistung aller `WorkEntry`-Intervalle mit Beginn, Ende, Dauer und Arbeitsort.
- **Arbeitsunterbrüche:** Zeitliche Lücken zwischen den Arbeitsblöcken zur Prüfung von Pausenzeiten durch den Vorgesetzten.
- **Ist-Arbeitszeit:** Summe aller abgeschlossenen Arbeitsblöcke des Tages.
- **Abwesenheitsgutschrift:** Ausweis bezahlter Abwesenheiten.
- **Tagessaldo:** Differenz zwischen anrechenbarer Arbeitszeit und Sollzeit.
- **Visuelle Kennzeichnung:** Modifizierte und manuell erstellte Zeitbuchungen werden optisch hervorgehoben.
- **Ersteller-Ausweisung:** Bei Fremderfassung wird der Ersteller (`createdBy`) transparent angezeigt.

---

### 2.2 Monatsreport

Monatliche Gesamtübersicht der geleisteten Arbeits- und Abwesenheitszeiten:

- **Sollzeit:** Monatssollzeit (unter Berücksichtigung von Ein-/Austritten unter dem Monat).
- **Ist-Arbeitszeit:** Gesamte erfasste Arbeitszeit.
- **Bezahlte Abwesenheiten:** Summe aller bezahlten Abwesenheitsstunden (z. B. Krankheit, Unfall, Weiterbildung).
- **Unbezahlte Abwesenheiten:** Summe unbezahlter Urlaubstage/Stunden.
- **Ferienbezug:** Im Monat bezogene Ferienzeiten.
- **Feiertagsgutschrift:** Summe der Feiertagsstunden.
- **Anfangssaldo:** Übertragener Schlusssaldo des Vormonats.
- **Monatssaldo:** Summe der Tagessaldi des aktuellen Monats.
- **Manuelle Korrekturen:** Im Monat vorgenommene administrative Saldoanpassungen.
- **Endsaldo:** Neuer Gesamtsaldo per Monatsende.
- **Genehmigungsstatus:** `OPEN`, `SUBMITTED`, `APPROVED`, `REJECTED` oder `LOCKED`.
- **Snapshot-Konsistenz:** Für genehmigte/gesperrte Perioden wird der unveränderliche `calculationSnapshot` geladen.

---

### 2.3 Ferienübersicht

Detaillierte Übersicht des Ferienguthabens und des Urlaubskontos:

- **Mitarbeiteridentifikation:** Ausweisung von Anzeigename und Personalnummer (bzw. Benutzername, keine rohe interne Mitarbeiter-ID).
- **Jahresanspruch:** Anteiliger Anspruch für das laufende Anspruchsjahr (in Minuten und Tagen).
- **Übertrag:** Aus dem Vorjahr übertragene Restferien (`CARRY_OVER`).
- **Korrekturen:** Manuelle oder pensumbedingte `CORRECTION`-Buchungen.
- **Bezogene Ferien:** Bisher im Kalenderjahr konsumierte Ferientage (`USAGE`).
- **Genehmigte zukünftige Ferien:** Bereits genehmigte, in der Zukunft liegende Ferienabwesenheiten.
- **Verfügbares Restguthaben:** Aktuell noch planbare Ferientage.
- **Kontobuchungstabelle:** Vollständiges, chronologisches Journal aller `VacationAccountEntry`-Einträge mit lokalisiertem Buchungstyp (z. B. `Anspruch`, `Übertrag`, `Bezug`, `Korrektur`, `Verfall`), Datum, Minuten/Tagen und Begründungskommentar. Keine undefinierten (`undefined`) Werte.

---

### 2.4 Teamreport (Team-Monatsübersicht)

Aggregierte Monatsauswertung für Führungskräfte:

- **Zugriffsbeschränkung:** Nur sichtbar für Rollen mit entsprechender Berechtigung (`Supervisor`, `HR`, `Admin`); für reine Mitarbeiter verborgen.
- **Filter:** Teamauswahl über Dropdown (keine ID-Texteingabe) und Monatsauswahl über Datumswähler.
- **Kennzahlen je Mitarbeiter:**
  - Monatssollzeit und Monatsistzeit
  - Monatssaldo und Gesamtsaldo
  - Offene Genehmigungen
  - Fehltage und Abwesenheiten nach Typ (unter Beachtung des Datenschutzes)
  - Fehlende Buchungen / unvollständige Tage
- **Pausenprüfung:** Detaillierte Arbeitsblöcke und Unterbrüche zur manuellen Beurteilung der gesetzlichen Ruhezeiten.
- **Visuelle Kennzeichnung:** Modifizierte Buchungen und Fremderfasser werden transparent ausgewiesen.

---

### 2.5 Abwesenheitsreport

Stellt Abwesenheiten für den gewählten Zeitraum und Mitarbeiter-/Teamkontext dar:

- **Datenschutz:** Benutzer ohne Sonderberechtigung sehen keine sensiblen Gründe wie Krankheit oder Unfall.
- **Kommentare:** Freitextkommentare werden standardmässig nicht in den Report übernommen.
- **Filter:** Nach Zeitraum, Team und Abwesenheitstyp.

---

## 3. Export-Spezifikation

### 3.1 Allgemeine Exportgrundsätze

- **Einheitliche Datenbasis:** Bildschirmansicht, CSV-Export und PDF-Ausgabe basieren auf identischen Berechnungsmodellen und Core-Services. Es dürfen keine abweichenden Berechnungen existieren.
- **Berechtigungskonsistenz:** Sämtliche Exporte erzwingen dieselben Autorisierungsprüfungen wie die Benutzeroberfläche.
- **Snapshot-Nutzung:** Liegt für eine abgeschlossene Periode ein `calculationSnapshot` vor, verwenden alle Exportformate diesen unveränderlichen Stand.

---

### 3.2 CSV-Export

- **Zeichensatz:** UTF-8-Encoding mit optionalem Byte Order Mark (BOM) für fehlerfreie Darstellung in Tabellenkalkulationsprogrammen wie Microsoft Excel.
- **Standard:** RFC 4180 mit Trennzeichen Semikolon (`;`) oder Komma (`,`) gemäss Standardkonfiguration.
- **Zahlenformate:** Deterministische Ausgabe von Dezimalwerten und Zeitangaben.

---

### 3.3 Native PDF-Ausgabe

Die native PDF-Generierung erfolgt vollständig serverseitig und wird für **Monatsreport**, **Ferienübersicht** und **Abwesenheitsreport** bereitgestellt.

#### Technische und gestalterische Regeln

1. **Serverseitige Erzeugung:** PDFs werden on demand über REST-Endpunkte erzeugt und gestreamt. Sie werden nicht dauerhaft auf dem Dateisystem der Anwendung gespeichert.
2. **Mehrsprachigkeit:** Die aktuell ausgewählte UI-Sprache des Benutzers steuert alle statischen Beschriftungen, Reporttitel, Spaltenüberschriften und Metadaten des PDFs.
3. **Datums- und Zeitformat:** 
   - Datumswerte im PDF: `yyyy-MM-dd`
   - Zeitdauern im PDF: `HH:mm`
4. **Seitenformat:** Standardmässig DIN A4 Portrait (Hochformat); bei breiten Tabellenlayouts ist Landscape (Querformat) zulässig.
5. **Schrifteinbettung:** Alle verwendeten Schriftarten werden vollständig eingebettet und unterstützen den Zeichensatz für Deutsch und Englisch.
6. **Monochrome Lesbarkeit:** Negative Saldi werden visuell hervorgehoben (z. B. fettgedruckt und mit Minuszeichen versehen), sodass die negative Bedeutung auch bei reinem Schwarz-Weiss-Ausdruck zweifelsfrei erkennbar ist.
7. **Keine Freitextkommentare:** Freitexte und Kommentare werden standardmässig nicht in PDF-Reports gedruckt.
8. **Revisionsneutralität:** Das Generieren oder Herunterladen eines PDFs erzeugt keinen Audit-Event.
9. **Deterministische Dateinamen:** Dateinamen werden nach dem Schema  
   `[reportType]_[context]_[periodOrDate].pdf` generiert (z. B. `month-report_meier-hans_2026-08.pdf`).

---

### 3.4 Kopf- und Fussbereich in PDF-Reports

Jedes PDF-Dokument enthält einen einheitlichen und professionellen Kopf- und Fussbereich:

#### Allgemeiner Kopf- und Fussbereich

- **Firmenname:** Global konfigurierter Unternehmensname.
- **Firmenlogo:** Global konfiguriertes Logo (oben rechts oder links). Ist kein Logo konfiguriert, wird der Platzhalter sauber ausgeblendet (keine leeren Kästen oder Fehleranzeigen).
- **Reporttitel:** Eindeutige, lokalisierte Bezeichnung des Berichts.
- **Berichtszeitraum:** Formatierter Zeitraum der Auswertung.
- **Erstellungszeitpunkt:** Zeitstempel der PDF-Generierung mit Zeitzone.
- **Seitennummerierung:** Im Format `Seite X von Y` in der Fusszeile.

#### Spezifische Zusatzdaten

- **Persönliche Reports (Monatsreport, Ferienübersicht):**
  - Anzeigename des Mitarbeiters
  - Personalnummer
  - Team
  - Arbeitsstandort
- **Genehmigte Monatsreports:**
  - Genehmigungsstatus (`Genehmigt` / `APPROVED`)
  - Genehmigungsdatum (`yyyy-MM-dd`)
  - Name des Genehmigers (`approvedBy`)
