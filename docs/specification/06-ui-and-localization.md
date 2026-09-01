# Chronivaro – UI und Lokalisierung

Dieses Dokument spezifiziert die Benutzeroberfläche, Frontend-Architektur, Bedienprinzipien sowie die verbindliche Mehrsprachigkeit und Sprachauswahl-Logik von Chronivaro.

## 1. Frontend-Architektur

Die Benutzeroberfläche von Chronivaro basiert auf modernem HTML, CSS und **Vanilla JavaScript** (native ES-Module). Es wird bewusst kein schwergewichtiges Frontend-Framework verwendet.

### 1.1 Verzeichnisstruktur (`chronivaro-web`)

```text
chronivaro-web/src/main/webapp/
├── index.html
├── assets/
│   ├── css/
│   └── icons/
└── js/
    ├── api/          # Zentrale REST-API-Client-Schicht
    ├── components/   # Wiederverwendbare UI-Elemente und Dialoge
    ├── i18n/         # Internationalisierung und Sprachdateien (de.json, en.json)
    ├── pages/        # Seiten und Ansichten (Views)
    ├── state/        # Clientseitiges State-Management
    ├── utils/        # Hilfsfunktionen (Formatierung, DOM)
    └── app.js        # Anwendungseinstiegspunkt und Router
```

- **API-Client:** Sämtliche Zugriffe auf die REST-API laufen über eine zentrale Client-Schicht (`js/api/`), welche Authentifizierung, Bearer-Tokens, Header (`Accept-Language`), Fehlerbehandlung und DTO-Mapping kapselt.
- **Statische Auslieferung:** Die Web-Assets werden durch den eingebetteten Jetty-Server direkt unter `/` ausgeliefert (siehe [Architektur und Laufzeit](08-architecture-and-runtime.md)).

---

## 2. Seiten und Ansichten (Views)

### 2.0 Header, Navigation & Profilmenü

- **Globale Header-Leiste:** Dauerhafte Anzeige von Firmenname und optionalem Firmenlogo, aktuellem Benutzer und Rolle sowie Navigation.
- **Benutzer-Dropdown-Menü:**
  - Enthält Profilinformationen (Anzeigename, Benutzername, Personalnummer, Eintrittsdatum, Pensum, Arbeitsplan, Team, Standort, Zeitzone).
  - Sprachumschaltung für die aktive Sitzung.
  - **Abmelde-Button ("Logout"):** Befindet sich fest innerhalb des Benutzer-Dropdowns (nicht als freistehender Button in der Hauptnavigation).

### 2.1 Dashboard

- Anzeige von heutiger Sollzeit, Istzeit und Tagessaldo.
- Aktueller Anwesenheitsstatus (`WORKING` / `NOT_WORKING`).
- Start-/Stopp-Schaltfläche mit optionalem Arbeitsort und Kommentarfeld.
- Aktueller Gesamtsaldo (Zeitsaldo und Feriensaldo).
- Offene Warnungen oder Erinnerungen.

### 2.2 Meine Zeiten & Mitarbeiter-Zeiterfassung

- **Ansichten:** Umschaltbare Tages-, Wochen- und Monatsansicht.
- **Zeitblöcke:** Übersicht aller Arbeitsblöcke und der daraus abgeleiteten Pausenunterbrüche.
- **Mitarbeiter-Bearbeitung:** Bearbeiten von Start-/Endzeiten, Arbeitsort und Kommentar in offenen Perioden.
- **Vorgesetzten-/HR-Funktionen:**
  - Zweistufige Mitarbeiterauswahl (Team -> Mitarbeiter).
  - Manuelle Erfassung, Vollkorrektur und Löschung von Zeitbuchungen in offenen Perioden.
- **Visuelle Kennzeichnung:** Modifizierte und manuell erstellte Buchungen werden optisch hervorgehoben (Badges).
- **Ersteller-Ausweisung:** Bei Fremderfassung wird transparent ausgewiesen, von wem (`createdBy`) der Eintrag erstellt wurde.

### 2.3 Abwesenheiten & Abwesenheitskalender

- Erfassung von Abwesenheitsanträgen (als Entwurf speichern oder direkt einreichen).
- Verwaltung von Entwürfen (`DRAFT`): Bearbeiten, Einreichen (`Submit`) oder Verwerfen (`Cancel`).
- Genehmigungshistorie und tabellarische/kartenbasierte Übersicht.
- **Fremderfassung von Abwesenheiten:** Vorgesetzte (für zugeordnete Teammitglieder) und HR/Administratoren (unternehmensweit) können Abwesenheiten im Namen von Mitarbeitenden erfassen (wahlweise direkt genehmigt `APPROVED` oder als Antrag eingereicht `SUBMITTED`).
- **Abwesenheitskalender (Team-/Mitarbeiter-Kalenderansicht):**
  - Dedizierte interaktive Kalenderansicht zur visuellen Darstellung aller Abwesenheiten von Mitarbeitenden (Monats-, Wochen- oder Mehrwochenansicht mit Filterung nach Team/Standort/Mitarbeiter).
  - Direkte Erfassung von Abwesenheiten aus dem Kalender heraus per Klick auf Kalendertage/Zeitspannen für ausgewählte Mitarbeitende.
  - **Pikettstatus im Kalender:** Möglichkeit, im Kalender direkt den Pikettstatus / die Pikettperiode (`OnCallPeriod`) für einen Mitarbeiter über einen gewünschten Zeitraum (z. B. eine Woche oder n-Tage) einzutragen und zu verwalten.
- Hohe visuelle Kontraste für alle Aktionsschaltflächen gemäss Barrierefreiheitsstandard (WCAG AA).

### 2.4 Ferien

- Kontoübersicht mit Name und Personalnummer/Benutzername (keine rohe ID).
- Jahresanspruch, Übertrag, bezogene und genehmigte zukünftige Ferien sowie Restguthaben.
- Vollständige Kontobuchungstabelle mit sauber lokalisierten Buchungstypen und formatierten Werten (keine `undefined`-Werte).

### 2.5 Status (Anwesenheitsseite)

- Filterung nach Team und Standort.
- Farblicher Status (`WORKING` = Grün, `NOT_WORKING` = Rot).
- **Anwesenheitsliste ("Wer arbeitet gerade"):** Horizontale flexible Zeilenanordnung (Row/Grid-Layout) mit einheitlicher Kartenbreite und konsistenten Abständen statt einer rein vertikalen Spaltenliste.
- Kennzeichnung vergessener Timer.
- Datenschutz: Keine Anzeige sensibler Abwesenheitsgründe.

### 2.6 Genehmigungen

- Übersicht offener Abwesenheitsanträge zur Genehmigung/Ablehnung mit Begründung.
- Eingereichte Monatsabschlüsse mit vollständiger Inspektionsansicht des Monatsreports (`GET /approvals/periods/{id}`).

### 2.7 Reports & Export

- Einheitliche horizontale Anordnung aller Kennzahlen-Karten (Summary Cards) in einer Zeile (Row).
- Datumsauswahl zwingend über Datumswähler / Monatsauswahl (keine freie Texteingabe).
- Zweistufige Mitarbeiterauswahl (Team-Dropdown filtert Mitarbeiter-Dropdown).
- Team-Monatsübersicht ausschliesslich für berechtigte Rollen (`Supervisor`, `HR`, `Admin`).
- CSV- und nativer PDF-Export für unterstützte Berichte.

### 2.8 Administration

- Benutzerverwaltung (für reine Systembenutzer und Mitarbeiter).
- Mitarbeiter- und Teamverwaltung (Deaktivierung bei Benutzerlöschung, Reaktivierung, Ferieninitialisierung).
- Arbeitspläne, Standorte, Feiertagskalender, Abwesenheitsarten.
- Globale Einstellungen (zentrierter Einstellungsbereich mit Beschreibungstext, Firmenname, Logo-Upload).
- Audit-Log-Ansicht mit Filterung und Detailmodal.

---

## 3. UI-Grundsätze und Barrierefreiheit

- **Responsives Design:** Vollständige Bedienbarkeit auf Desktop, Tablets und mobilen Endgeräten.
- **Tastaturbedienbarkeit:** Alle Formulare, Menüs und Dialoge sind per Tastatur bedienbar.
- **Farbkontraste:** Konformität mit WCAG AA; Status und Warnungen werden nie ausschliesslich über Farbe vermittelt.
- **Modale Dialoge:** Eingabedialoge sind bei kleiner Fensterhöhe vertikal scrollbar (Viewport-Overflow), sodass Speichern- und Abbrechen-Schaltflächen stets erreichbar bleiben.
- **Lösch- und Bestätigungsdialoge:** Zeigen den lesbaren Namen bzw. die Bezeichnung des zu löschenden Elements anstelle interner IDs an.
- **Datumsanzeige in der UI:** Die formatierte Datumsanzeige in Tabellen und Masken erfolgt im Format `DD-MM-YYYY` (z. B. `04-08-2026`). Die technische REST-Übertragung bleibt strikt im ISO-Format `YYYY-MM-DD`.
- **Zustandsbehandlung:** Eindeutige Lade-, Leer- und Fehlerzustände für jede asynchrone Datenansicht.

---

## 4. Mehrsprachigkeit und Lokalisierung

Chronivaro unterstützt vollständig die Sprachen **Deutsch** (`de`, Schweizer Hochdeutsch ohne `ß`) und **Englisch** (`en`).

### 4.1 Umfang der Übersetzungen

| Zu übersetzen (i18n) | Nicht übersetzt (Rohdaten) |
|---|---|
| Statische UI-Beschriftungen & Menüs | Benutzerkommentare & Freitexte |
| Navigation, Schaltflächen & Aktionen | Personen- und Mitarbeiternamen |
| Validierungs- und UI-Fehlermeldungen | Team- und Standortbezeichnungen |
| Menschenlesbare REST-Fehlermeldungen | Administrativ konfigurierte Abwesenheitsnamen |
| Reporttitel & Spaltenüberschriften | Konfigurierte Feiertagsnamen |
| PDF-Beschriftungen & Fusszeilen | Technische Codes & Enum-Werte |

---

### 4.2 Deterministische Sprachwahl-Priorität

#### 4.2.1 Sprachwahl vor dem Login (Login-Bildschirm)

Auf dem Login-Bildschirm wird die effektive Sprache in folgender Priorität bestimmt:

```text
1. Explizit auf dem aktuellen Login-Bildschirm gewählte Sprache
2. Gültiger Wert im Browser LocalStorage
3. Global konfigurierte Standardsprache des Systems
```

Nach erfolgreichem Login wird die Auswahl im Browser Storage sowie im Sprachattribut des Strolch-Benutzers gespeichert.

#### 4.2.2 Sprachwahl nach dem Login (Angemeldete Sitzung)

Während einer angemeldeten Sitzung gilt folgende Priorität:

```text
1. Explizit beim aktuellen Login gewählte Sprache
2. Gültiger Wert im Browser LocalStorage
3. Im Strolch-Benutzer gespeichertes Sprachattribut
4. Global konfigurierte Standardsprache des Systems
```

- Ändert der Benutzer die Sprache im Benutzermenü, wird die neue Sprache sofort aktiv und persistent im Browser Storage sowie im Strolch-Benutzerprofil gespeichert.

---

### 4.3 Übersetzungsschlüssel und Fallback-Kette

- Systemtexte werden über stabile hierarchische Schlüssel referenziert (z. B. `app.title`, `actions.save`, `errors.overlap`).
- URLs enthalten keinen Sprachpräfix (sprachneutrale REST- und Web-Pfade).
- Die serverseitige und clientseitige Textauflösung folgt dieser Fallback-Kette:

```text
Angeforderter Sprach-Tag (z. B. de-CH)
          │
          ▼
     Basissprache (de)
          │
          ▼
Konfigurierte Standardsprache (z. B. de)
          │
          ▼
   Übersetzungsschlüssel (Key)
```

---

### 4.4 Übersetzungsqualität und Vollständigkeit

- **100 % Parität:** Alle verpflichtenden Übersetzungsschlüssel müssen in Deutsch (`de`) und Englisch (`en`) vorhanden sein.
- **Automatisierte Build-Prüfung:** Fehlende Schlüssel in Deutsch oder Englisch, doppelte Schlüssel sowie ungültiges JSON lassen den Maven-Build fehlschlagen (siehe [Teststrategie](11-testing-and-acceptance.md#14-übersetzungs--und-exporttests)).
- Weitere Sprachen können modular über zusätzliche Sprachdateien ergänzt werden.
