# Master-Prompt für die Implementierung von Chronivaro mit Junie

Du bist der verantwortliche Softwareentwicklungs-Agent für das Projekt **Chronivaro**. Implementiere die Anwendung direkt im aktuell in IntelliJ IDEA geöffneten Projekt im Chronivaro-Verzeichnis.

Die vollständige fachliche Spezifikation befindet sich unter:

```text
docs/Chronivaro-Implementierungsspezifikation.md
```

Lies diese Datei vollständig, bevor du Architekturentscheidungen triffst oder Code erzeugst. Sie ist die fachliche Quelle der Wahrheit. Falls sie im Projekt noch nicht vorhanden ist, verwende die Anforderungen in diesem Prompt als Ausgangspunkt und weise mich darauf hin, dass die vollständige Spezifikation noch in das Verzeichnis `docs` kopiert werden muss.

## Ziel

Erstelle eine produktionsnahe Webanwendung für:

- Erfassung der täglichen Arbeitszeit pro Mitarbeiter
- mehrere Arbeitsblöcke pro Tag
- Starten und Stoppen eines laufenden Arbeitsblocks
- manuelle Erfassung und Korrektur von Arbeitszeiten
- versionierte Sollarbeitszeiten und individuelle Arbeitspensen
- Abwesenheiten wie Krankheit, Ferien und Militär, stundenweise, halb- oder ganztägig
- Ferienanspruch und Ferienguthaben als nachvollziehbares Journal
- aktuelle Statusseite mit `WORKING` oder `NOT_WORKING`
- Soll-/Ist-Auswertungen und Zeitsaldi
- Genehmigung von Abwesenheiten und Monatsperioden
- Rollen, Berechtigungen und Audit-Log
- CSV-Export

Die Anwendung verwendet:

- **JDK 25**
- **Maven**
- **Strolch** für Domänenmodell, Transaktionen, Services und Persistenz
- **JAX-RS** für die REST-API
- **Vanilla JavaScript** mit nativen ES-Modulen für die UI
- HTML und CSS ohne Frontend-Framework

## Verbindliche Modulstruktur

Erstelle beziehungsweise erhalte ein Maven-Multimodulprojekt mit genau diesen fachlichen Bereichen:

```text
chronivaro/
├── pom.xml
├── chronivaro-core/
├── chronivaro-rest/
└── chronivaro-ui/
```

Verwende diese Maven-Koordinaten:

```xml
<groupId>ch.atexxi.chronivaro</groupId>
<artifactId>chronivaro-parent</artifactId>
<version>0.1.0-SNAPSHOT</version>
```

Die Verantwortlichkeiten sind strikt zu trennen:

### `chronivaro-core`

- Strolch-Domänenmodell und Modellinitialisierung
- fachliche Services und Zustandsübergänge
- Berechnungen für Sollzeit, Istzeit, Abwesenheiten, Ferien und Saldi
- Validierungen
- Berechtigungsentscheidungen
- Audit-Logik
- transaktionale Persistenz

Der Core darf keine REST-DTOs, HTTP-Abhängigkeiten oder Browserkonzepte kennen.

### `chronivaro-rest`

- JAX-RS-Ressourcen
- Request- und Response-DTOs
- DTO-Mapping
- Authentifizierung und Autorisierung am API-Rand
- einheitliche Fehlerabbildung
- OpenAPI-Dokumentation
- REST-Integrationstests

REST-Ressourcen dürfen keine fachliche Berechnungslogik enthalten. Sie validieren das Transportformat, prüfen die Berechtigung und delegieren an Core-Services.

### `chronivaro-web`

- HTML
- CSS
- Vanilla JavaScript als native ES-Module
- zentraler REST-Client
- Seiten und wiederverwendbare UI-Komponenten
- clientseitige Validierung als Benutzerhilfe

Verwende kein React, Vue, Angular, TypeScript oder anderes Frontend-Framework. Die serverseitige Validierung bleibt immer verbindlich.

## Wichtigste fachliche Vorgabe: Arbeitsblöcke und Pausen

Diese Vorgabe ist unverhandelbar:

- Ein `WorkEntry` repräsentiert ausschliesslich einen tatsächlich gearbeiteten Zeitblock.
- `WorkEntry` besitzt keinen Typ `BREAK` und generell keinen `entryType`.
- Pausen werden nicht als eigene Entität und nicht als eigene Buchung erfasst.
- Es gibt keine Pause-/Resume-Endpunkte und keinen separaten Pausenstatus.
- Ein Mitarbeiter startet einen Arbeitsblock und stoppt ihn. Arbeitet er später weiter, startet er einen neuen Arbeitsblock.
- Eine Unterbrechung ergibt sich ausschliesslich aus der zeitlichen Lücke zwischen zwei Arbeitsblöcken.
- Die Ist-Arbeitszeit eines Tages ist die Summe der Dauer aller abgeschlossenen Arbeitsblöcke.
- Unterbrüche zählen nicht zur Ist-Arbeitszeit.
- Gesetzliche Pausendauern werden nicht fest codiert, nicht automatisch erzwungen und nicht automatisch als Verstoss klassifiziert.
- Reports zeigen Start, Ende und Dauer jedes Arbeitsblocks sowie die Unterbrüche dazwischen.
- Der zuständige Vorgesetzte beurteilt anhand des Reports, ob die gesetzlichen Pausen eingehalten wurden, und erinnert den Mitarbeiter bei Bedarf.

Implementiere keine automatische Pausenlogik, auch wenn eine Bibliothek oder ein Beispielprojekt eine solche nahelegt.

## Fachliches Kernmodell

Implementiere mindestens die folgenden Konzepte gemäss der vollständigen Spezifikation:

### Employee

- stabile interne ID
- Referenz auf die zugehörige Strolch-Person beziehungsweise den Benutzer
- eindeutige Personalnummer
- Anzeigename
- primäres Team
- Standort
- Zeitzone, standardmässig `Europe/Zurich`
- Eintrittsdatum
- optionales Austrittsdatum
- Aktivstatus

### EmploymentScheduleVersion

- Mitarbeiterreferenz
- `validFrom` und optional `validTo`
- Beschäftigungsgrad
- wöchentliche Sollminuten
- Sollminuten je Wochentag
- keine überlappenden Versionen
- historische Versionen dürfen durch spätere Änderungen nicht überschrieben werden

### WorkEntry

- ID
- Mitarbeiterreferenz
- Startzeitpunkt
- optionaler Endzeitpunkt für eine laufende Buchung
- Quelle wie `TIMER`, `MANUAL`, `IMPORT` oder `ADMIN`
- optionaler Kommentar
- Ersteller

Regeln:

- höchstens ein laufender `WorkEntry` pro Mitarbeiter
- Ende muss nach dem Start liegen
- keine Überlappungen
- Buchungen über Mitternacht sind erlaubt
- Zeitzonen- und Sommerzeitwechsel müssen korrekt behandelt werden
- manuelle Tageszeiteingaben dürfen nicht zu Doppelzählungen führen

### AbsenceType

Konfigurierbare Abwesenheitsart mit mindestens:

- stabilem Code und Name
- Anrechnung an die Sollzeit
- Belastung des Ferienkontos
- bezahlt oder unbezahlt
- Genehmigung erforderlich
- Kommentar erforderlich
- erlaubte Dauerarten `HOURS`, `HALF_DAY`, `FULL_DAY`
- Sichtbarkeit auf allgemein zugänglichen Statusansichten
- Aktivstatus

Lege mindestens Ferien, Krankheit und Militär beziehungsweise Zivildienst als initiale Typen an. Die Architektur muss weitere Typen ohne Codeänderung erlauben.

### Absence

- Mitarbeiter und Abwesenheitsart
- Start- und Enddatum
- Dauerart
- bei Halbtag `MORNING` oder `AFTERNOON`
- Minuten bei stundenweiser Erfassung
- Kommentar
- Status `DRAFT`, `SUBMITTED`, `APPROVED`, `REJECTED`, `CANCELLED`
- Genehmigungsinformationen

Halb- und Ganztage werden anhand der für den konkreten Tag gültigen individuellen Sollzeit berechnet. Arbeitsfreie Tage und Feiertage erzeugen standardmässig keine Abwesenheitsminuten.

### VacationAccountEntry

Modelliere das Ferienkonto als unveränderliches Journal und nicht als überschreibbaren Saldo:

- `ENTITLEMENT`
- `CARRY_OVER`
- `USAGE`
- `CORRECTION`
- `EXPIRY`

Speichere Ferien intern in Minuten. Änderungen oder Stornierungen erzeugen Gegenbuchungen; vorhandene Journalbuchungen werden nicht still verändert.

### HolidayCalendar und Holiday

- Kalender pro Standort
- Datum und Name des Feiertags
- Gutschriftfaktor, normalerweise `1.0`, optional `0.5`
- individuelle Übersteuerung muss später möglich bleiben

### TimePeriod

Monatliche Abschlussperiode mit:

- `OPEN`
- `SUBMITTED`
- `APPROVED`
- `REJECTED`
- `LOCKED`

Änderungen in gesperrten Perioden sind nicht erlaubt. Eine Wiederöffnung erfordert Berechtigung und Begründung.

### AuditEvent

Protokolliere mindestens:

- Entitätstyp und Entitäts-ID
- Aktion
- Benutzer
- Zeitpunkt
- vorherigen und neuen Wert
- fachliche Begründung, sofern erforderlich
- Korrelations-ID

## Berechnungen

Implementiere die Berechnungen zentral im Core und teste sie unabhängig von REST und UI.

```text
Sollzeit = Sollminuten des gültigen Arbeitsplans für den Wochentag

Ist-Arbeitszeit = Summe der Dauer aller abgeschlossenen WorkEntry-Intervalle

Anrechenbare Zeit = Ist-Arbeitszeit
                    + bezahlte anrechenbare Abwesenheit
                    + Feiertagsgutschrift

Tagessaldo = Anrechenbare Zeit - Sollzeit

Periodensaldo = Summe aller Tagessaldi der Periode

Endsaldo = Anfangssaldo + Periodensaldo + manuelle Korrekturen
```

Verwende intern ganzzahlige Minuten. Verwende geeignete Typen aus `java.time`: fachliche Tage als `LocalDate`, Monate als `YearMonth`, Zeitzonen als `ZoneId` und eindeutige Zeitpunkte mit Offset beziehungsweise als `Instant`, soweit dies mit der Strolch-Modellierung vereinbar ist.

Vor Eintritt und nach Austritt beträgt die Sollzeit null. Feiertage, individuelle Arbeitstage, Pensumsänderungen sowie halb- und ganztägige Abwesenheiten müssen historisch korrekt berechnet werden.

Es gibt im MVP keine Rundung.

## Anwesenheitsstatus

Der primäre Status ist absichtlich binär:

- `WORKING`: Grün, wenn ein offener `WorkEntry` existiert
- `NOT_WORKING`: Rot, wenn kein offener `WorkEntry` existiert

Eine Unterbrechung zwischen Arbeitsblöcken ist `NOT_WORKING`. Ein eigener Pausenstatus ist verboten.

Zusatzinformationen wie geplante Abwesenheit, arbeitsfreier Tag, Homeoffice oder Aussendienst dürfen separat angezeigt werden, ändern aber den binären Primärstatus nicht. Ohne besondere Berechtigung dürfen keine sensiblen Gründe wie Krankheit oder Unfall sichtbar sein.

## REST-API

Verwende den Basis-Pfad:

```text
/api/chronivaro/v1
```

Implementiere die in der Spezifikation definierten Ressourcen. Beginne mindestens mit:

```text
GET    /me/work-entries?from={date}&to={date}
POST   /me/work-entries
PUT    /me/work-entries/{id}
DELETE /me/work-entries/{id}
POST   /me/timer/start
POST   /me/timer/stop
GET    /me/day-summary/{date}
GET    /me/month-summary/{yearMonth}
GET    /presence?teamId={id}&locationId={id}
```

Danach folgen die Endpunkte für:

- Abwesenheiten und Genehmigungen
- Ferienkonto und Korrekturen
- Monatsperioden und Genehmigungen
- Reports und CSV-Export
- Mitarbeiter, Teams, Standorte, Feiertagskalender, Arbeitspläne und Konfiguration

REST-Konventionen:

- JSON als Standardformat
- ISO-8601 für Datum und Zeit
- Zeitpunkte immer eindeutig mit Offset
- einheitliche Fehlerantwort mit `errorCode`, `message`, optionalen `fieldErrors` und `correlationId`
- serverseitige Autorisierung bei jedem Endpunkt
- optimistische Nebenläufigkeitskontrolle für bearbeitbare Entitäten
- Pagination für potenziell grosse Listen
- OpenAPI-3-Dokumentation direkt an den REST-Methoden

## UI

Implementiere eine klare, responsive Oberfläche mit mindestens:

1. Dashboard mit Sollzeit, Istzeit, Saldi, Status und Start/Stoppen
2. Tages-, Wochen- und Monatsansicht der eigenen Zeiten
3. manuelle Erfassung und Korrektur von Arbeitsblöcken
4. Abwesenheitsanträge
5. Ferienkonto
6. binäre Statusseite
7. Genehmigungsansicht
8. Reports
9. Administrationsansichten

Die Tages- und Reportansichten müssen Arbeitsblöcke mit Start, Ende und Dauer sowie die daraus abgeleiteten Unterbrüche zeigen.

Organisiere das JavaScript ungefähr so, sofern die vorhandene Projektstruktur nichts Besseres vorgibt:

```text
chronivaro-ui/src/main/webapp/
├── index.html
├── assets/
│   ├── css/
│   └── icons/
└── js/
    ├── api/
    ├── components/
    ├── pages/
    ├── state/
    ├── utils/
    └── app.js
```

Anforderungen an die Bedienung:

- responsive auf Desktop und Smartphone
- per Tastatur bedienbar
- ausreichender Kontrast
- Status nie nur durch Farbe vermitteln
- verständliche Feld- und Serverfehler
- sichtbare Lade-, Leer- und Fehlerzustände
- Bestätigung vor fachlich weitreichenden Aktionen

## Sicherheit und Datenschutz

Implementiere mindestens getrennte Berechtigungen für:

- eigene Zeiten lesen und ändern
- fremde Zeiten lesen und ändern
- Abwesenheiten genehmigen
- Perioden genehmigen und wieder öffnen
- Ferienkonto korrigieren
- Reports lesen und exportieren
- Anwesenheitsstatus lesen
- sensible Abwesenheitsgründe lesen
- Konfiguration administrieren

Verlasse dich nie ausschliesslich auf ausgeblendete UI-Elemente. Jede Berechtigung ist im Backend zu prüfen. Schreibe keine sensiblen Abwesenheitsinformationen in normale Logs.

## Arbeitsweise

Arbeite in kleinen, überprüfbaren Schritten direkt im Projekt:

1. Analysiere zuerst den bestehenden Projektzustand, vorhandene Maven-Konfiguration, Strolch-Versionen, lokale Konventionen und bestehende Tests.
2. Erfinde keine Strolch-APIs oder Dependency-Koordinaten. Nutze die im Projekt vorhandenen Versionen und Beispiele. Wenn eine zwingende Information nicht aus dem Projekt hervorgeht, frage gezielt nach.
3. Erstelle danach einen kurzen Implementierungsplan nach den sechs Phasen der Spezifikation.
4. Beginne unmittelbar mit der Implementierung. Liefere nicht nur Architekturvorschläge oder Pseudocode.
5. Implementiere vertikale, lauffähige Inkremente. Ein Inkrement soll nach Möglichkeit Core-Logik, REST-Endpunkt, UI und Tests für denselben Anwendungsfall umfassen.
6. Führe nach jedem sinnvollen Inkrement die betroffenen Tests aus.
7. Führe regelmässig den vollständigen Maven-Build mit JDK 25 aus.
8. Behebe selbst verursachte Compiler-, Test- und Formatierungsfehler, bevor du fortfährst.
9. Ändere keine bestehenden, fachfremden Dateien ohne Notwendigkeit.
10. Dokumentiere relevante Architekturentscheidungen knapp im Projekt.

Wenn das Projekt leer ist, beginne mit Phase 1. Wenn bereits Code existiert, ermittle den erreichten Stand und setze bei der ersten unvollständigen Phase fort.

## Implementierungsphasen

### Phase 1 – Fundament

- Maven-Multimodulprojekt
- JDK 25
- Strolch-Laufzeit und Modellinitialisierung
- Grundstruktur von Core, REST und UI
- Authentifizierungs- und Rollenrahmen
- einheitliche Fehlerbehandlung
- Audit-Grundlage
- erfolgreicher Build

### Phase 2 – Mitarbeiter und Sollzeit

- Mitarbeiter, Teams und Standorte
- versionierte Arbeitspläne
- Feiertagskalender
- Sollzeitberechnung
- umfassende Core-Tests

### Phase 3 – Arbeitszeit

- `WorkEntry`
- Überlappungs- und Laufendbuchungsvalidierung
- Start und Stoppen
- mehrere Arbeitsblöcke pro Tag
- Ableitung der Unterbrüche
- Tagesübersicht
- Istzeit und Saldo
- erste nutzbare UI

### Phase 4 – Abwesenheiten und Ferien

- konfigurierbare Abwesenheitsarten
- Abwesenheitsworkflow
- Genehmigung und Ablehnung
- Ferienjournal mit Gegenbuchungen
- Ferienübersicht

### Phase 5 – Status und Reports

- `WORKING` und `NOT_WORKING`
- Monats- und Teamreport
- Darstellung von Arbeitsblöcken und Unterbrüchen
- CSV-Export in UTF-8 mit optionalem BOM

### Phase 6 – Periodenabschluss und Härtung

- Einreichen, Genehmigen, Ablehnen und Sperren von Monatsperioden
- begründete Wiederöffnung
- Audit-Vervollständigung
- Berechtigungs- und Datenschutztests
- Performance- und Browserprüfung
- Betriebs- und Entwicklerdokumentation

## Tests und Qualitätsanforderungen

Verwende automatisierte Tests für die Fachlogik. Decke mindestens ab:

- verschiedene Arbeitspensen und Wochentage
- Pensumsänderung mitten im Monat
- Eintritt und Austritt mitten im Monat
- ganze und halbe Feiertage
- stundenweise, halbe und ganze Abwesenheiten
- Abwesenheiten an arbeitsfreien Tagen
- Ferien über Wochenenden und Feiertage
- Arbeit über Mitternacht
- Sommer-/Winterzeitwechsel
- überlappende Arbeitsblöcke
- mehrere Start-/Stopp-Blöcke pro Tag
- korrekte Unterbrüche zwischen den Blöcken
- Zeit- und Feriensaldi über Periodengrenzen
- Journalbuchungen und Gegenbuchungen
- Rollen- und Teamgrenzen
- gesperrte Perioden
- CSV-Encoding

Tests müssen fachliches Verhalten prüfen und dürfen nicht lediglich Getter, Setter oder Framework-Code abdecken.

## Annahmen für das MVP

Solange nichts anderes entschieden wurde:

- keine Rundung
- negative Zeitsaldi sind erlaubt
- negative Feriensaldi sind nicht erlaubt
- ein Mitarbeiter gehört zu genau einem primären Team
- genehmigte Abwesenheiten werden über einen Stornierungsprozess geändert
- Standardzeitzone ist `Europe/Zurich`
- Homeoffice ist eine optionale Zusatzinformation
- keine automatische Pausenprüfung
- keine fest codierten gesetzlichen Pausenregeln

Bei einer echten Blockade stelle eine konkrete Frage mit einer empfohlenen Option. Für reversible technische Detailentscheidungen triff eine vernünftige, dokumentierte Entscheidung und arbeite weiter.

## Erwartetes Ergebnis jeder Arbeitsetappe

Gib nach jeder abgeschlossenen Etappe eine kurze Zusammenfassung mit:

- implementierten Funktionen
- neu angelegten oder wesentlich geänderten Dateien
- ausgeführten Tests und deren Ergebnis
- bekannten Einschränkungen
- nächstem vorgesehenen Inkrement

Behaupte keine erfolgreiche Funktion oder keinen erfolgreichen Build, ohne ihn tatsächlich geprüft zu haben.

## Startauftrag

Beginne jetzt mit folgenden Schritten:

1. Lies `docs/Chronivaro-Implementierungsspezifikation.md` vollständig.
2. Analysiere den bestehenden Projektzustand.
3. Prüfe insbesondere vorhandene Strolch-Abhängigkeiten, Konventionen und Beispielimplementierungen.
4. Erstelle einen kurzen, konkreten Plan für die nächste lauffähige Etappe.
5. Implementiere diese Etappe vollständig.
6. Führe die zugehörigen Tests sowie den Maven-Build aus.
7. Berichte kompakt über Ergebnis und nächsten Schritt.

