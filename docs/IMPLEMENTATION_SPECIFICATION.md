# Chronivaro – Implementierungsspezifikation

> **Claim:** Arbeitszeit im Überblick  
> **Status:** Entwurf für die Implementierung  
> **Technologiebasis:** Strolch, JDK 25, Maven, Eclipse Jetty (embedded), Jersey / JAX-RS, Vanilla JavaScript

## 1. Zweck des Dokuments

Dieses Dokument beschreibt die fachlichen und technischen Anforderungen für **Chronivaro**, eine webbasierte Anwendung zur Erfassung und Auswertung von Arbeitszeiten, Abwesenheiten und Ferienguthaben.

Es dient als Grundlage für:

- Architektur und Datenmodell
- iterative Implementierung
- Erstellung von REST-Schnittstellen und UI
- Testplanung und Abnahme
- spätere Erweiterungen

## 2. Produktziel

Chronivaro ermöglicht Mitarbeitenden, ihre Arbeitszeiten und Abwesenheiten nachvollziehbar zu erfassen. Vorgesetzte und Personaladministration erhalten aktuelle Anwesenheitsinformationen sowie verlässliche Soll-/Ist-Auswertungen.

Historische Auswertungen müssen auch dann reproduzierbar bleiben, wenn sich Arbeitspensum, Arbeitsplan, Ferienanspruch oder andere Einstellungen später ändern.

## 3. Zielgruppen und Rollen

### 3.1 Mitarbeiter

- eigene Arbeitszeiten erfassen und bearbeiten
- eigene Abwesenheiten erfassen und einreichen
- eigene Saldi und Reports einsehen
- eigene Ferienübersicht einsehen
- eigenen laufenden Arbeitstag starten und stoppen

### 3.2 Vorgesetzter

- Daten der zugeordneten Mitarbeitenden und Teams einsehen
- Abwesenheiten genehmigen oder ablehnen
- Monatsperioden prüfen und genehmigen
- fehlende oder fehlerhafte Einträge erkennen
- Teamstatus einsehen

### 3.3 Personaladministration

- Mitarbeiterstammdaten verwalten
- Arbeitsmodelle und Ferienansprüche verwalten
- Abwesenheiten und Saldi korrigieren
- Perioden wieder öffnen
- teamübergreifende Reports erstellen

### 3.4 Administrator

- globale Konfiguration verwalten
- Rollen und Berechtigungen verwalten
- Abwesenheitsarten, Feiertage und Standorte konfigurieren
- technische Administration durchführen

### 3.5 Leseberechtigter Benutzer – optional

- freigegebene Reports einsehen
- keine Daten verändern

## 4. Umfang

### 4.1 MVP

Der erste produktiv nutzbare Umfang enthält:

1. Mitarbeiter- und Teamverwaltung
2. versionierte Arbeitsmodelle mit individuellen Sollzeiten
3. Feiertagskalender
4. Arbeitszeiterfassung mit mehreren Arbeitsblöcken pro Tag
5. manuelle Erfassung einer Tagesarbeitszeit
6. konfigurierbare Abwesenheitsarten
7. halb- und ganztägige sowie stundenweise Abwesenheiten
8. Ferienkonten mit nachvollziehbaren Kontobuchungen
9. Anwesenheitsstatus
10. Soll-/Ist-Auswertung und Zeitsaldo
11. Monatsabschluss mit Genehmigungsstatus
12. Rollen und Berechtigungen
13. Audit-Log
14. CSV-Export

### 4.2 Spätere Ausbaustufen

- Projekt-, Kunden-, Auftrags- oder Tätigkeitserfassung
- Zuschläge für Nacht-, Wochenend- oder Feiertagsarbeit
- native Excel- und PDF-Exporte
- Dokumente zu Abwesenheiten, beispielsweise Arztzeugnisse
- Benachrichtigungen und Erinnerungen
- Kalenderintegration
- Import aus bestehenden Zeiterfassungssystemen
- Mehrsprachigkeit
- Mobile-optimierte Offline-Erfassung

## 5. Fachliche Grundsätze

1. **Historisierung:** Arbeitspläne, Pensen und Ansprüche werden mit einem Gültigkeitszeitraum gespeichert und nicht rückwirkend überschrieben.
2. **Nachvollziehbarkeit:** Änderungen an fachlich relevanten Daten werden revisionsfähig protokolliert.
3. **Berechnung statt Speicherung:** Abgeleitete Werte wie Tages-Istzeit und Monatssaldo werden aus den zugrunde liegenden Buchungen berechnet. Für abgeschlossene Perioden dürfen zusätzlich unveränderliche Berechnungsergebnisse gespeichert werden.
4. **Keine verdeckten Annahmen:** Rundung, Arbeitsunterbrüche, Feiertage und Abwesenheitsanrechnung sind konfigurierbar oder als explizite Geschäftsregel dokumentiert.
5. **Datensparsamkeit:** Auf allgemein sichtbaren Statusseiten wird kein sensibler Abwesenheitsgrund angezeigt.
6. **Zeitzonenfestigkeit:** Zeitpunkte werden eindeutig gespeichert; fachliche Kalendertage werden in der Zeitzone des Mitarbeiters beziehungsweise Standorts ausgewertet.

## 6. Fachliches Domänenmodell

### 6.1 Employee – Mitarbeiter

Pflichtattribute:

| Attribut | Beschreibung |
| --- | --- |
| `employeeId` | stabile interne ID |
| `personId` | Referenz auf Benutzer beziehungsweise Person in Strolch |
| `personnelNumber` | eindeutige Personalnummer |
| `displayName` | Anzeigename |
| `teamId` | aktuelles Team |
| `locationId` | Arbeitsstandort und Feiertagskalender |
| `timeZone` | IANA-Zeitzone, standardmässig `Europe/Zurich` |
| `entryDate` | Eintrittsdatum |
| `exitDate` | optionales Austrittsdatum |
| `active` | fachlicher Aktivstatus |
| `currentWorkDayId` | Referenz auf den aktuellen `WorkDay` |

### 6.2 EmploymentScheduleVersion – versionierter Arbeitsplan

Ein Mitarbeiter besitzt mindestens einen Arbeitsplan. Änderungen erzeugen eine neue Version.

| Attribut | Beschreibung |
| --- | --- |
| `validFrom` | erster Gültigkeitstag, inklusive |
| `validTo` | letzter Gültigkeitstag, inklusive; optional |
| `employmentPercentage` | Beschäftigungsgrad, beispielsweise `80.0` |
| `weeklyTargetMinutes` | Sollzeit pro Woche |
| `mondayMinutes` bis `sundayMinutes` | Sollzeit je Wochentag |

Regeln:

- Versionen desselben Mitarbeiters dürfen sich nicht überschneiden.
- Für jeden aktiven Beschäftigungstag muss genau eine Version bestimmbar sein.
- Vergangene Versionen dürfen nur mit entsprechender Berechtigung korrigiert werden.
- Pensum und Wochentagsverteilung sind getrennt zu speichern, damit beispielsweise ein 80-%-Pensum auf vier oder fünf Tage verteilt werden kann.

### 6.3 WorkDay – Arbeitstag

Ein `WorkDay` fasst alle Arbeitszeitbuchungen eines Mitarbeiters für einen Kalendertag zusammen und referenziert den zum Zeitpunkt der Erstellung gültigen Arbeitsplan.

| Attribut | Beschreibung |
| --- | --- |
| `workDayId` | eindeutige ID |
| `employeeId` | Mitarbeiter |
| `date` | Kalendertag |
| `scheduleId` | Referenz auf die zum Startzeitpunkt aktive `EmploymentScheduleVersion` |
| `workEntryIds` | Liste der zugehörigen `WorkEntry`-Referenzen |

Regeln:

- Pro Mitarbeiter und Datum existiert maximal ein `WorkDay`.
- Der `WorkDay` wird beim ersten Starten der Arbeit für ein neues Datum automatisch erstellt.
- Er dient als Einstiegspunkt für die Suche nach aktiven Buchungen und vereinfacht die Auswertung grosser Datenmengen.

### 6.4 WorkEntry – Arbeitszeitbuchung

| Attribut | Beschreibung |
| --- | --- |
| `workEntryId` | eindeutige ID |
| `workDayId` | Referenz auf den zugehörigen `WorkDay` |
| `start` | Startzeitpunkt |
| `end` | Endzeitpunkt; bei laufender Buchung leer |
| `source` | beispielsweise `TIMER`, `MANUAL`, `IMPORT`, `ADMIN` |
| `comment` | optionaler Kommentar |
| `createdBy` | Ersteller |
| `workingLocation` | Arbeitsort: `HOME_OFFICE`, `OFFICE` oder `CUSTOMER` |

Regeln:

- Pro Mitarbeiter ist höchstens eine laufende Buchung erlaubt.
- Das Ende muss nach dem Start liegen.
- Überlappende Buchungen sind nicht erlaubt.
- Buchungen müssen am selben Tag starten und enden. Falls eine Arbeit über Mitternacht hinausgeht, muss sie um 24:00 Uhr des Starttages beendet werden und eine neue Buchung für den Folgetag auf dem entsprechenden `WorkDay` erstellt werden.
- Ein `WorkEntry` bildet ausschliesslich einen tatsächlich gearbeiteten Zeitblock ab.
- Pausen werden nicht als eigene Entität erfasst. Eine Unterbrechung ergibt sich aus der zeitlichen Lücke zwischen zwei Arbeitsblöcken.
- Direkte Tageszeiteingaben werden intern als separate manuelle Tagesbuchung oder als klar gekennzeichnete Dauerbuchung abgebildet; beide Erfassungsarten dürfen nicht zu einer Doppelzählung führen.
- Jeder `WorkEntry` muss einen Arbeitsort haben. Der Arbeitsort beschreibt den Ort des jeweiligen Zeitblocks und ist nicht zwingend für den ganzen Arbeitstag gleich.
- Ein Arbeitstag darf höchstens einen Arbeitsort am Vormittag und einen Arbeitsort am Nachmittag haben. Ein Wechsel des Arbeitsorts erzeugt daher separate, nicht überlappende `WorkEntry`-Zeitblöcke.
- Für die Erfassung im Dashboard werden die Dauerbereiche `HALF_DAY` und `FULL_DAY` unterstützt. Ein halber Tag bezieht sich auf `MORNING` oder `AFTERNOON`; ein ganzer Tag gilt für beide Tageshälften.
- `CUSTOMER` bezeichnet Arbeit beim Kunden beziehungsweise unterwegs für einen Kunden. Die konkrete Kundenreferenz ist für diese Erweiterung optional und darf später ergänzt werden.

### 6.4.1 Wöchentliche Standardarbeitsorte

Mitarbeitende können für jeden Wochentag einen Standardarbeitsort konfigurieren. Der Standard wird beim Öffnen eines neuen `WorkDay` im Dashboard vorausgefüllt und erstellt nicht selbstständig eine Arbeitszeitbuchung.

| Attribut | Beschreibung |
| --- | --- |
| `weekday` | Wochentag, für den der Standard gilt |
| `workingLocation` | `HOME_OFFICE`, `OFFICE` oder `CUSTOMER` |
| `durationType` | `HALF_DAY` oder `FULL_DAY` |
| `halfDayPart` | `MORNING` oder `AFTERNOON`, wenn `durationType` den Wert `HALF_DAY` hat |

Regeln:

- Mitarbeitende dürfen pro Wochentag und Tageshälfte höchstens einen Standard konfigurieren.
- Beim Öffnen des Dashboards wird für das aktuelle Datum der passende wöchentliche Standard ausgewählt, sofern einer vorhanden ist.
- Mitarbeitende dürfen den Standard vor dem Start oder beim Aktualisieren von Arbeitszeitbuchungen überschreiben oder löschen. Die Übersteuerung gilt nur für den betroffenen `WorkDay` und ändert die wöchentliche Konfiguration nicht.
- Ein ganztägiger Standard darf in zwei halbtägige Buchungen mit unterschiedlichen Arbeitsorten aufgeteilt werden, beispielsweise `HOME_OFFICE` am Vormittag und `CUSTOMER` am Nachmittag.
- Standards gelten nicht für arbeitsfreie Tage, ausser Mitarbeitende erfassen ausdrücklich Arbeitszeit.

### 6.5 AbsenceType – Abwesenheitsart

Konfigurierbare Beispiele:

- Ferien
- Krankheit
- Unfall
- Militär oder Zivildienst
- Arzttermin
- Weiterbildung
- Mutterschafts-, Vaterschafts- oder Elternurlaub
- unbezahlter Urlaub
- Überstundenkompensation
- sonstige Abwesenheit

| Attribut | Beschreibung |
| --- | --- |
| `absenceTypeId` | eindeutige ID |
| `name` | sichtbare Bezeichnung |
| `code` | stabiler technischer Code |
| `creditTargetTime` | ob die Abwesenheit als erfüllte Sollzeit gilt |
| `deductVacation` | ob das Ferienkonto belastet wird |
| `paid` | bezahlt oder unbezahlt |
| `approvalRequired` | Genehmigung erforderlich |
| `commentRequired` | Kommentar erforderlich |
| `allowedDurations` | `HOURS`, `HALF_DAY`, `FULL_DAY` |
| `visibleOnPublicStatus` | ob der genaue Typ sichtbar sein darf; standardmässig `false` |
| `active` | für neue Erfassungen verfügbar |

### 6.6 Absence – Abwesenheit

| Attribut | Beschreibung |
| --- | --- |
| `absenceId` | eindeutige ID |
| `employeeId` | Mitarbeiter |
| `absenceTypeId` | Art der Abwesenheit |
| `startDate` | erster Tag |
| `endDate` | letzter Tag |
| `durationType` | `HOURS`, `HALF_DAY`, `FULL_DAY` |
| `halfDayPart` | bei Halbtag `MORNING` oder `AFTERNOON` |
| `minutes` | bei stundenweiser Erfassung |
| `comment` | optional beziehungsweise gemäss Typ erforderlich |
| `status` | `DRAFT`, `SUBMITTED`, `APPROVED`, `REJECTED`, `CANCELLED` |
| `decisionBy` | Genehmiger |
| `decisionAt` | Zeitpunkt der Entscheidung |
| `decisionComment` | Begründung bei Ablehnung oder Korrektur |

Regeln:

- Ein ganzer Abwesenheitstag entspricht der individuellen Sollzeit dieses Tages.
- Ein halber Tag entspricht 50 % der individuellen Sollzeit dieses Tages.
- Arbeitsfreie Tage und Feiertage erzeugen ohne Sonderkonfiguration keine Abwesenheitsminuten.
- Sich überschneidende Abwesenheiten sind nicht erlaubt.
- Arbeitszeit und genehmigte Abwesenheit am gleichen Zeitpunkt beziehungsweise für dieselben angerechneten Minuten sind zu validieren.
- Mehrtägige Abwesenheiten werden bei der Berechnung pro Kalendertag aufgelöst.

### 6.7 VacationAccountEntry – Ferienkontobuchung

Das Ferienguthaben wird als Journal geführt und nicht als veränderbarer Einzelwert.

| Attribut | Beschreibung |
| --- | --- |
| `vacationEntryId` | eindeutige ID |
| `employeeId` | Mitarbeiter |
| `effectiveDate` | Wirksamkeitsdatum |
| `entryType` | `ENTITLEMENT`, `CARRY_OVER`, `USAGE`, `CORRECTION`, `EXPIRY` |
| `amountMinutes` | positive oder negative Anzahl Ferienminuten |
| `relatedAbsenceId` | Referenz bei Ferienbezug |
| `comment` | Begründung |
| `createdBy` | Ersteller |

Ferien werden intern in Minuten geführt. Die UI darf das Guthaben zusätzlich in Tagen anzeigen. Für die Darstellung in Tagen ist eine eindeutig definierte Bezugs-Sollzeit erforderlich.

#### 6.7.1 Automated vacation entitlement policy

The automated entitlement engine uses the following policy decisions:

- The standard annual entitlement for a full-time employee is `25` vacation days per entitlement year.
- The entitlement year is the calendar year, and the annual entitlement is booked at the beginning of that year.
- One vacation day is converted to a fixed, configured number of minutes. The exact configured minute value must be
  provided before implementation; it must not be inferred from an employee's daily target.
- Part-time entitlement is calculated pro rata from the employment rate.
- Employment starting or ending during the entitlement year is prorated for the active part of the year.
- No age-based entitlement rules apply.
- No seniority-based entitlement rules apply.
- Unused vacation is carried over without a limit at the transition to the next calendar year.
- Carry-over consumes the oldest available vacation balance first.
- Carried-over vacation does not expire.
- Only the configured standard vacation absence type creates `USAGE` entries. The technical identifier of this type must
  be provided before implementation.
- Journal entries are immutable. Corrections and reversals are represented by separately audited `CORRECTION` entries.
- Vacation approval is blocked when the requested usage exceeds the available balance. No negative balance is permitted.
- All policy values, including the fixed day-minute conversion, entitlement, proration, carry-over, expiry, and absence
  type configuration, must be configurable rather than hard-coded.

The following implementation parameters remain explicitly open because they were not defined by the policy decisions:

- the fixed number of minutes representing one vacation day;
- the rounding rule for prorated entitlement;
- the exact technical identifier of the standard vacation absence type;
- whether positive correction balances are included in the unlimited carry-over amount.

### 6.8 HolidayCalendar und Holiday

| Attribut | Beschreibung |
| --- | --- |
| `calendarId` | eindeutige Kalender-ID |
| `name` | beispielsweise `Kanton Bern` |
| `date` | Datum des Feiertags |
| `holidayName` | Name des Feiertags |
| `creditFactor` | normalerweise `1.0`; optional für halbe Feiertage `0.5` |

Der Standort eines Mitarbeiters bestimmt standardmässig den Feiertagskalender. Eine individuelle Übersteuerung bleibt möglich.

### 6.9 TimePeriod – Abschlussperiode

| Attribut | Beschreibung |
| --- | --- |
| `periodId` | eindeutige ID |
| `employeeId` | Mitarbeiter |
| `yearMonth` | Periode im Format `YYYY-MM` |
| `status` | `OPEN`, `SUBMITTED`, `APPROVED`, `REJECTED`, `LOCKED` |
| `submittedAt` | Einreichungszeitpunkt |
| `approvedAt` | Genehmigungszeitpunkt |
| `approvedBy` | Genehmiger |
| `comment` | Kommentar oder Ablehnungsgrund |
| `calculationSnapshot` | optionaler unveränderlicher Abschlussstand |

### 6.10 AuditEvent – Änderungsprotokoll

Mindestens zu protokollieren:

- Entitätstyp und Entitäts-ID
- Aktion
- Benutzer
- Zeitpunkt
- vorheriger Wert
- neuer Wert
- fachliche Begründung, sofern erforderlich
- Korrelations-ID der auslösenden Anfrage

## 7. Berechnungsregeln

### 7.1 Sollzeit eines Tages

```text
Sollzeit = Arbeitsplan-Minuten des Wochentags
```

Anpassungen:

- vor Eintritt oder nach Austritt: `0`
- gesetzlicher beziehungsweise konfigurierter Feiertag: Reduktion gemäss `creditFactor`
- unbezahlte Abwesenheit: Sollzeit bleibt für die Ausweisung erhalten, wird aber nicht als erfüllt angerechnet
- bezahlte anrechenbare Abwesenheit: wird als Abwesenheitsgutschrift ausgewiesen

### 7.2 Ist-Arbeitszeit

```text
Ist-Arbeitszeit = Summe der Dauer aller abgeschlossenen WorkEntry-Intervalle
```

Pausen werden weder separat gebucht noch automatisch von der Arbeitszeit abgezogen. Beendet ein Mitarbeiter einen Arbeitsblock und startet später einen neuen, gilt die dazwischenliegende Zeit als Arbeitsunterbruch und zählt nicht zur Ist-Arbeitszeit.

Chronivaro erzwingt keine gesetzlich vorgeschriebenen Pausendauern und enthält dafür keine fest codierten Grenzwerte. Reports stellen Beginn, Ende und Dauer der Arbeitsblöcke sowie die Unterbrüche dazwischen transparent dar. Die Beurteilung der gesetzlichen Pausen und eine allfällige Erinnerung des Mitarbeiters liegen beim zuständigen Vorgesetzten.

### 7.3 Anrechenbare Zeit

```text
Anrechenbare Zeit = Ist-Arbeitszeit
                    + bezahlte anrechenbare Abwesenheit
                    + Feiertagsgutschrift
```

### 7.4 Tagessaldo

```text
Tagessaldo = Anrechenbare Zeit - Sollzeit
```

### 7.5 Periodensaldo

```text
Periodensaldo = Summe Tagessaldo innerhalb der Periode
Endsaldo       = Anfangssaldo + Periodensaldo + manuelle Korrekturen
```

Alle Berechnungen verwenden ganzzahlige Minuten. Anzeige und Export dürfen zusätzlich ein Format wie `8:15` oder Dezimalstunden anbieten.

### 7.6 Rundung

Für das MVP gilt standardmässig keine Rundung. Falls Rundung aktiviert wird, muss sie konfigurierbar, transparent und im Audit-Log nachvollziehbar sein.

## 8. Anwesenheitsstatus

Die Statusseite beantwortet bewusst nur, ob ein Mitarbeiter aktuell arbeitet. Sie zeigt je aktivem Mitarbeiter genau einen primären Status:

| Status | Farbe | Regel |
| --- | --- | --- |
| `WORKING` | Grün | Es besteht ein laufender `WorkEntry`. |
| `NOT_WORKING` | Rot | Es besteht kein laufender `WorkEntry`. |

Eine Unterbrechung zwischen zwei Arbeitsblöcken erscheint als `NOT_WORKING`; ein eigener Pausenstatus existiert nicht. Optionale Zusatzinformationen wie eine geplante Abwesenheit, ein arbeitsfreier Tag, `HOME_OFFICE` oder `FIELD_SERVICE` dürfen separat angezeigt werden, verändern den binären Primärstatus aber nicht.

Datenschutzregel: Benutzer ohne besondere Berechtigung sehen keine Abwesenheitsgründe wie Krankheit oder Unfall.

## 9. Geschäftsprozesse

### 9.1 Arbeitstag starten und stoppen

1. Mitarbeiter startet die Arbeit.
2. System prüft, ob für das heutige Datum bereits ein `WorkDay` beim Mitarbeiter referenziert wird.
3. Falls kein `WorkDay` existiert oder das Datum des referenzierten `WorkDay` nicht dem aktuellen Datum entspricht:
   a. System erstellt einen neuen `WorkDay` für das aktuelle Datum.
   b. System ermittelt die aktuell gültige `EmploymentScheduleVersion` und referenziert diese im `WorkDay`.
   c. System aktualisiert die `currentWorkDayId` beim Mitarbeiter.
4. System prüft im aktuellen `WorkDay`, dass keine Buchung läuft.
5. System erstellt einen offenen `WorkEntry` und verknüpft ihn mit dem `WorkDay`.
6. Beim Stoppen wird der laufende `WorkEntry` im `WorkDay` beendet.
   a. Falls das Enddatum dem Startdatum entspricht, wird die Buchung normal beendet.
   b. Falls das Enddatum nach dem Startdatum liegt:
      i. Falls das Enddatum genau der Folgetag ist (Arbeit über Mitternacht):
         - Die Buchung wird am Starttag um 24:00 Uhr beendet.
         - Für die restliche Zeit wird eine neue Buchung auf dem `WorkDay` des Folgetages erstellt.
      ii. Falls das Enddatum mehr als einen Tag nach dem Startdatum liegt (vergessener Timer):
         - Die Buchung wird auf den Zeitpunkt beendet, an dem das Tagessoll (Sollzeit) erreicht wird.
         - Berechnung: `Endzeit = Startzeit + max(0, Sollzeit - bisherige_Istzeit_des_Tages)`.
         - Die Endzeit wird auf maximal 24:00 Uhr des Starttages begrenzt.
         - Die restliche Zeit wird verworfen.
         - Der `WorkEntry` wird automatisch mit einem Kommentar "Timer vergessen - auf Sollzeit begrenzt" versehen.
7. Beginnt der Mitarbeiter später erneut zu arbeiten, startet er einen neuen `WorkEntry` innerhalb desselben `WorkDay`.
8. Die Zeit zwischen zwei Arbeitsblöcken wird nur im Report als Unterbruch dargestellt und nicht als Pause gespeichert.

### 9.2 Logik für vergessene Timer (Edge Cases)

Um eine konsistente Behandlung von vergessenen Timern zu gewährleisten, gilt folgende detaillierte Logik:

- **Sollzeit bereits erreicht:** Falls die Summe der bereits abgeschlossenen `WorkEntries` des Tages die Sollzeit bereits erreicht oder überschreitet, wird der vergessene Timer auf seine Startzeit gesetzt (Dauer 0).
- **Startzeit nach Sollzeit-Erreichung:** Startet ein Mitarbeiter einen Timer, obwohl das Soll bereits erfüllt ist, und vergisst diesen, wird er ebenfalls auf die Startzeit gesetzt.
- **Sollzeit-Erreichung nach Mitternacht:** Da ein `WorkEntry` niemals über Mitternacht hinausgehen darf, wird die automatische Beendung spätestens auf 24:00 Uhr des Starttages gesetzt, auch wenn das Soll damit noch nicht erreicht ist.
- **Mehrfache vergessene Timer:** Sollte ein Mitarbeiter nacheinander mehrere Timer vergessen (extremer Ausnahmefall), wird jeder einzelne gemäss der obigen "Auffüll-Logik" behandelt, bis das Tagessoll erreicht ist.

### 9.3 Manuelle Zeitkorrektur

1. Benutzer öffnet einen Tag.
2. Benutzer ergänzt, ändert oder entfernt eine Buchung.
3. System validiert Reihenfolge, Überlappungen und Periodenstatus.
4. Bei einer bereits genehmigten oder gesperrten Periode ist die Änderung nur nach Wiederöffnung möglich.
5. Änderung wird protokolliert.

### 9.4 Abwesenheitsantrag

1. Mitarbeiter erfasst Art, Zeitraum und Dauer.
2. System berechnet die betroffenen Sollminuten als Vorschau.
3. Mitarbeiter reicht den Antrag ein.
4. Vorgesetzter genehmigt oder lehnt mit Kommentar ab.
5. Bei Ferien wird nach Genehmigung eine Ferienkontobuchung erstellt.
6. Stornierung oder Änderung erzeugt eine entsprechende Gegenbuchung; bestehende Kontobuchungen werden nicht still überschrieben.

### 9.5 Monatsabschluss

1. Mitarbeiter prüft die Monatsübersicht.
2. System zeigt Fehler und Warnungen.
3. Mitarbeiter reicht die Periode ein.
4. Vorgesetzter genehmigt oder lehnt ab.
5. Nach Genehmigung wird die Periode gesperrt.
6. Eine Wiederöffnung erfordert Berechtigung und Begründung.

### 9.6 Mitarbeiter-Registrierung

Um neuen Mitarbeitern den Zugriff auf Chronivaro zu ermöglichen, wird ein administrativer Registrierungsprozess bereitgestellt.

1. Ein Administrator wählt in der Mitarbeiterliste die Aktion "Registrieren" für einen bestimmten Mitarbeiter aus.
2. Das System identifiziert den verknüpften Strolch-Benutzer anhand der `userId` und `username`.
3. Das System löst eine Strolch-Challenge (`Usage.SET_PASSWORD`) für diesen Benutzer aus.
4. Die Challenge wird über den konfigurierten `UserChallengeHandler` (z. B. Konsole oder E-Mail) an den Mitarbeiter übermittelt.
5. Der Mitarbeiter verwendet den erhaltenen Link/Code, um sein initiales Passwort festzulegen und sich anschliessend anzumelden.

Dieser Prozess nutzt den Standard-Strolch-Mechanismus zur Passwortinitialisierung und stellt sicher, dass keine Passwörter manuell durch Administratoren vergeben oder per Klartext-E-Mail versendet werden müssen.

## 10. Validierungen

### 10.1 Blockierende Fehler

- überlappende Zeitbuchungen
- mehr als eine laufende Buchung
- Ende vor oder gleich Start
- überlappende Arbeitsplanversionen
- Abwesenheit ohne erforderlichen Kommentar
- Abwesenheit ausserhalb der erlaubten Dauerarten
- unberechtigter Zugriff auf fremde Daten
- Änderung einer gesperrten Periode
- Ferienbezug ohne ausreichendes Guthaben, sofern negative Saldi nicht erlaubt sind
- fehlende Arbeitsplanversion für einen relevanten Tag

### 10.2 Warnungen

- Soll-Arbeitstag ohne Buchung oder Abwesenheit
- ungewöhnlich lange Arbeitszeit
- Arbeit über Mitternacht (wird automatisch in zwei Buchungen aufgeteilt)
- Arbeit an einem Feiertag oder arbeitsfreien Tag
- negativer Ferien- oder Zeitsaldo

Warnungen verhindern das Speichern nicht, können aber das Einreichen einer Periode blockieren, wenn dies konfiguriert ist.

## 11. Reports

### 11.1 Tagesübersicht

- Sollzeit
- Arbeitsblöcke mit Beginn, Ende und Dauer
- zeitliche Unterbrüche zwischen den Arbeitsblöcken
- Ist-Arbeitszeit
- Abwesenheitsgutschrift
- Tagessaldo
- Validierungsstatus

Die Darstellung muss Vorgesetzten ermöglichen, lange Arbeitsblöcke und die dazwischenliegenden Unterbrüche zu beurteilen. Das System klassifiziert diese nicht automatisch als gesetzeskonform oder als Verstoss.

### 11.2 Monatsreport

- Sollzeit
- Ist-Arbeitszeit
- bezahlte Abwesenheit
- unbezahlte Abwesenheit
- Ferienbezug
- Feiertagsgutschrift
- Anfangssaldo
- Monatssaldo
- manuelle Korrekturen
- Endsaldo
- Genehmigungsstatus

### 11.3 Ferienübersicht

- Jahresanspruch
- Übertrag aus Vorjahr
- Korrekturen
- bezogene Ferien
- genehmigte zukünftige Ferien
- noch verfügbare Ferien

### 11.4 Teamreport

- Soll-/Istzeit pro Mitarbeiter
- Saldo pro Mitarbeiter
- fehlende Buchungen
- Arbeitsblöcke und Unterbrüche zur manuellen Prüfung der gesetzlichen Pausen
- offene Genehmigungen
- Abwesenheiten nach Typ, abhängig von der Berechtigung

### 11.5 Export

Der MVP unterstützt CSV mit UTF-8 und optionalem BOM für Excel-Kompatibilität. Datums-, Zeit- und Zahlenformate sind eindeutig dokumentiert. Exporte berücksichtigen dieselben Berechtigungen wie die Bildschirmansicht.

## 12. UI-Anforderungen

Die UI wird mit HTML, CSS und Vanilla JavaScript umgesetzt. Es wird kein Frontend-Framework verwendet.

### 12.1 Seiten

1. **Dashboard**
   - heutige Soll- und Istzeit
   - aktueller Status
   - Start/Stoppen
   - aktueller Zeit- und Feriensaldo
   - offene Warnungen
2. **Meine Zeiten**
   - Tages-, Wochen- und Monatsansicht
   - Zeitblöcke erfassen und bearbeiten
   - Tagessummen und Saldi
3. **Abwesenheiten**
   - Antrag erfassen
   - eigene Anträge und Status
   - Kalenderdarstellung
4. **Ferien**
   - Kontoübersicht
   - geplante und bezogene Ferien
   - Kontobuchungen
5. **Status**
   - Filter nach Team und Standort
   - farblicher Status
   - datenschutzkonforme Anzeige
6. **Genehmigungen**
   - offene Abwesenheiten
   - eingereichte Monatsperioden
7. **Reports**
   - Zeitraum und Mitarbeiter/Team auswählen
   - Soll-/Ist-Vergleich
   - CSV-Export
8. **Administration**
   - Mitarbeiter und Teams
   - Registrierungsprozess auslösen
   - Arbeitspläne
   - Standorte und Feiertage
   - Abwesenheitsarten
   - globale Einstellungen

### 12.2 UI-Grundsätze

- responsive Bedienung auf Desktop und Smartphone
- vollständige Tastaturbedienbarkeit
- ausreichender Farbkontrast
- Status nicht ausschliesslich durch Farbe vermitteln
- verständliche Fehlermeldungen direkt am betroffenen Feld
- Bestätigung vor fachlich weitreichenden Aktionen
- Datum und Zeit in lokaler Darstellung, Übertragung in eindeutigem ISO-Format
- Lade-, Leer- und Fehlerzustände für jede asynchrone Ansicht

### 12.3 JavaScript-Struktur

Empfohlene Struktur:

```text
chronivaro-web/src/main/webapp/
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

JavaScript wird als native ES-Module organisiert. API-Zugriffe laufen über eine zentrale Client-Schicht, welche Authentifizierung, Fehlerbehandlung und JSON-Verarbeitung vereinheitlicht.

## 13. REST-API

### 13.1 Allgemeine Konventionen

- Basis-Pfad: `/rest/chronivaro/v1`
- JSON als Standardformat
- ISO-8601 für Datum und Zeit
- Zeitpunkte mit Offset, beispielsweise `2026-08-04T08:15:00+02:00`
- fachliche Datumswerte als `YYYY-MM-DD`
- standardisierte Fehlerantworten
- serverseitige Berechtigungsprüfung bei jedem Endpunkt
- optimistische Nebenläufigkeitskontrolle für bearbeitbare Entitäten
- Pagination für Listen mit potenziell vielen Einträgen

Beispiel einer Fehlerantwort:

```json
{
  "errorCode": "WORK_ENTRY_OVERLAP",
  "message": "Die Zeitbuchung überschneidet sich mit einer bestehenden Buchung.",
  "fieldErrors": [
    {
      "field": "start",
      "code": "OVERLAP"
    }
  ],
  "correlationId": "01J4..."
}
```

### 13.2 Vorgesehene Ressourcen

#### Eigene Arbeitszeit

```text
GET    /me/work-entries?from={date}&to={date}
POST   /me/work-entries
PUT    /me/work-entries/{id}
DELETE /me/work-entries/{id}
POST   /me/timer/start
POST   /me/timer/stop
GET    /me/day-summary/{date}
GET    /me/month-summary/{yearMonth}
```

#### Abwesenheiten

```text
GET    /me/absences
POST   /me/absences
PUT    /me/absences/{id}
POST   /me/absences/{id}/submit
POST   /me/absences/{id}/cancel
GET    /absence-types
GET    /approvals/absences
POST   /approvals/absences/{id}/approve
POST   /approvals/absences/{id}/reject
```

#### Ferien

```text
GET    /me/vacation-account?year={year}
GET    /employees/{id}/vacation-account?year={year}
POST   /employees/{id}/vacation-adjustments
```

#### Status

```text
GET    /presence?teamId={id}&locationId={id}
```

#### Perioden und Reports

```text
GET    /me/periods/{yearMonth}
POST   /me/periods/{yearMonth}/submit
GET    /approvals/periods
POST   /approvals/periods/{id}/approve
POST   /approvals/periods/{id}/reject
POST   /periods/{id}/reopen
GET    /reports/time-balance
GET    /reports/time-balance.csv
GET    /reports/absences
```

#### Administration

```text
GET/POST/PUT /employees
POST         /employees/{id}/register
GET/POST/PUT /teams
GET/POST/PUT /locations
GET/POST/PUT /holiday-calendars
GET/POST/PUT /absence-types
GET/POST/PUT /employees/{id}/schedule-versions
GET/POST/PUT /configuration
```

Die endgültigen DTOs und HTTP-Statuscodes werden pro Endpunkt in der OpenAPI-Spezifikation festgelegt.

## 14. Modul- und Projektstruktur

```text
chronivaro/
├── pom.xml
├── chronivaro-core/
│   ├── pom.xml
│   └── src/
│       ├── main/java/
│       ├── main/resources/
│       └── test/java/
├── chronivaro-rest/
│   ├── pom.xml
│   └── src/
│       ├── main/java/
│       ├── main/resources/
│       └── test/java/
├── chronivaro-web/
│   ├── pom.xml
│   └── src/
│       ├── main/webapp/
│       └── test/
└── chronivaro-app/
    ├── pom.xml
    └── src/
        ├── main/java/
        ├── main/resources/
        └── test/java/
```

Maven-Koordinaten:

```xml
<groupId>ch.atexxi.chronivaro</groupId>
<artifactId>chronivaro-parent</artifactId>
<version>0.1.0-SNAPSHOT</version>
<packaging>pom</packaging>
```

Der Parent setzt mindestens:

- JDK-Release `25`
- einheitliche Plugin- und Dependency-Versionen
- UTF-8 als Quellcode- und Report-Encoding
- reproduzierbare Builds
- Unit- und Integrationstest-Phasen

### 14.1 `chronivaro-core`

Enthält:

- Strolch-Domänenmodell und Modellinitialisierung
- fachliche Services und Commands
- Berechnungslogik
- Validierungen
- Berechtigungsentscheidungen
- Audit-Logik
- Persistenzzugriffe innerhalb von Strolch-Transaktionen

Der Core kennt keine REST-DTOs und keine Browserkonzepte.

### 14.2 `chronivaro-rest`

Enthält:

- JAX-RS-Ressourcen
- Request- und Response-DTOs
- Mapping zwischen DTOs und Core-Modell
- Authentifizierung und Autorisierung am API-Rand
- zentrale Fehlerabbildung
- OpenAPI-Dokumentation
- REST-Integrationstests

REST-Ressourcen enthalten keine fachliche Berechnungslogik.

### 14.3 `chronivaro-web`

Enthält:

- HTML, CSS und Vanilla JavaScript
- UI-Komponenten und Seiten
- REST-Client
- clientseitige Eingabevalidierung als Benutzerhilfe
- statische Assets

Die serverseitige Validierung bleibt verbindlich.

### 14.4 `chronivaro-app`

Enthält:

- den ausführbaren Application Entry Point
- Initialisierung und kontrolliertes Herunterfahren der Chronivaro- und Strolch-Laufzeit
- den eingebetteten Eclipse-Jetty-Server
- Registrierung von Jersey / JAX-RS im Servlet-Kontext
- Bereitstellung der statischen Ressourcen aus `chronivaro-web`
- technische HTTP-Server-Konfiguration
- Runtime- und HTTP-Integrationstests

`chronivaro-app` ist die äusserste Laufzeitschicht. Das Modul darf von `chronivaro-core`, `chronivaro-rest` und dem erzeugten Frontend-Artefakt abhängen. Umgekehrt dürfen `chronivaro-core` und `chronivaro-rest` nicht von `chronivaro-app` abhängen.

Jetty-spezifischer Code bleibt auf diese Laufzeit- beziehungsweise Bootstrap-Schicht beschränkt. Insbesondere darf `chronivaro-core` keine Jetty-Abhängigkeit besitzen und JAX-RS-Ressourcen dürfen keine Jetty-spezifischen APIs voraussetzen.

## 15. Eingebetteter HTTP-Server

Chronivaro wird als eigenständige Java-Anwendung betrieben. Die Anwendung darf für ihren regulären Betrieb nicht in einen externen Servlet-Container oder Application Server deployt werden müssen.

Eclipse Jetty ist Bestandteil der Chronivaro-Laufzeit und wird durch die Anwendung selbst gestartet, konfiguriert und gestoppt.

Die Zielarchitektur ist:

```text
Chronivaro
│
├── Application Bootstrap (`chronivaro-app`)
│
├── Strolch Runtime
│
├── Chronivaro Application Services
│
└── Embedded Jetty
    │
    ├── Jersey / JAX-RS
    │   └── REST API
    │
    └── Static Resource Handler
        └── chronivaro-web
```

Tomcat ist nicht Bestandteil der benötigten Laufzeitarchitektur.

### 15.1 Laufzeitmodell und Start

Chronivaro muss über einen regulären Java-Einstiegspunkt direkt gestartet werden können. Das angestrebte Betriebsmodell ist:

```bash
java -jar chronivaro.jar
```

Die konkrete Maven-Packaging-Technik ist eine Implementierungsentscheidung. Das resultierende Produktionsartefakt muss jedoch ohne Installation oder Start eines externen Tomcat- oder Jetty-Servers ausführbar sein.

Der Application Entry Point liegt in `chronivaro-app` und übernimmt die Orchestrierung der technischen Laufzeit.

Die Startreihenfolge ist grundsätzlich:

1. Konfiguration laden und validieren.
2. Strolch Runtime und Chronivaro Application Services initialisieren.
3. Embedded Jetty initialisieren.
4. Jersey und die bestehende JAX-RS-Anwendung registrieren.
5. Statische Frontend-Ressourcen konfigurieren.
6. HTTP-Server starten und Requests akzeptieren.

Kann eine zwingend benötigte Komponente nicht initialisiert werden, darf Chronivaro nicht in einem teilweise funktionsfähigen Zustand weiterlaufen. Der Prozessstart muss insbesondere fehlschlagen, wenn der konfigurierte HTTP-Port nicht gebunden werden kann, die HTTP-Konfiguration ungültig ist oder Jersey beziehungsweise notwendige Application Services nicht initialisiert werden können.

### 15.2 REST API über Jersey / JAX-RS

Die bestehende REST API verwendet weiterhin Jersey / JAX-RS.

Der in Abschnitt 13 definierte REST-Basispfad bleibt unverändert:

```text
/rest/chronivaro/v1
```

Die Migration auf Embedded Jetty ist ein Infrastruktur-Refactoring und darf bestehende REST-Verträge, Pfade, Request- und Response-Modelle sowie HTTP-Semantik nicht verändern, sofern dies nicht durch eine andere Anforderung dieser Spezifikation ausdrücklich verlangt wird.

Jersey wird in der eingebetteten Jetty Servlet Runtime registriert.

JAX-RS-Ressourcen bleiben von Jetty-spezifischen APIs unabhängig. Fachliche Logik verbleibt in `chronivaro-core`; die REST-Schicht dient weiterhin ausschliesslich als API-Rand.

### 15.3 Frontend-Auslieferung

Das von `chronivaro-web` erzeugte Frontend wird durch denselben eingebetteten Jetty-Server ausgeliefert.

Die Anwendung stellt damit sowohl die Weboberfläche als auch die REST API über denselben HTTP-Server bereit.

Konzeptionell:

```text
/                           → Chronivaro Frontend
/assets/...                 → statische Frontend-Ressourcen
/rest/chronivaro/v1/...     → JAX-RS REST API
```

Für die Produktionsumgebung ist kein zusätzlicher Webserver ausschliesslich zur Auslieferung des Chronivaro Frontends erforderlich.

Die konkrete Einbindung des von `chronivaro-web` erzeugten Artefakts in `chronivaro-app` ist eine Build- und Packaging-Entscheidung. Sie muss reproduzierbar über Maven erfolgen und darf keine manuelle Kopieroperation für einen Produktions-Build voraussetzen.

### 15.4 Application Lifecycle und Shutdown

Jetty ist Bestandteil des Chronivaro Application Lifecycles.

Beim Beenden der JVM muss Chronivaro kontrolliert herunterfahren. Das Herunterfahren erfolgt in umgekehrter Abhängigkeitsreihenfolge des Starts.

Dabei müssen mindestens:

- keine neuen HTTP Requests mehr angenommen werden;
- laufende HTTP Requests, soweit technisch sinnvoll, kontrolliert beendet werden;
- Jetty gestoppt werden;
- Chronivaro-Ressourcen freigegeben werden;
- die Strolch Runtime ordnungsgemäss beendet werden.

Ein Termination-Signal des Betriebssystems beziehungsweise Containers muss diesen kontrollierten Shutdown auslösen.

### 15.5 HTTP-Konfiguration

Die HTTP-Server-Konfiguration ist Bestandteil der regulären Chronivaro-Konfiguration und wird nicht über eine extern installierte Jetty-Instanz verwaltet.

Mindestens folgende Werte müssen konfigurierbar sein:

- Aktivierung beziehungsweise Deaktivierung des HTTP-Servers
- Bind Address
- HTTP Port
- optionaler Context Path, falls für eine Zielumgebung benötigt
- Quelle beziehungsweise Speicherort der statischen Frontend-Ressourcen, soweit diese nicht fest in das Produktionsartefakt integriert sind

Sinnvolle Standardwerte dürfen definiert werden.

Chronivaro darf für seine grundlegende HTTP-Konfiguration keine externen Jetty-spezifischen XML-Konfigurationsdateien voraussetzen.

### 15.6 Abhängigkeitsgrenzen

Jetty ist eine technische Infrastrukturabhängigkeit und darf nicht in die fachlichen Schichten hineinreichen.

Es gelten insbesondere folgende Regeln:

- `chronivaro-core` besitzt keine Abhängigkeit zu Jetty.
- `chronivaro-rest` enthält keine Jetty-spezifische Business- oder Bootstrap-Logik.
- JAX-RS-Ressourcen verwenden keine Jetty-spezifischen APIs.
- Jetty Bootstrap und HTTP-Server-Konfiguration liegen in `chronivaro-app`.
- Standardisierte Jakarta-Servlet-, JAX-RS-, Filter- und Listener-Mechanismen dürfen verwendet werden.
- Portable Jakarta-Funktionalität wird nicht unnötig durch Jetty-spezifische Implementierungen ersetzt.

### 15.7 Unabhängigkeit von Tomcat

Chronivaro darf zur Laufzeit nicht von Tomcat oder Tomcat-spezifischer Infrastruktur abhängig sein.

Nicht erforderlich beziehungsweise nicht zulässig als Laufzeitvoraussetzung sind insbesondere:

- ein installierter Tomcat Server
- `CATALINA_HOME`
- Deployment nach `webapps`
- Tomcat-spezifische APIs
- Tomcat-spezifische Lifecycle-Mechanismen
- Tomcat-spezifische Runtime-Konfiguration
- ein klassisches WAR-Deployment als notwendiges Produktionsmodell

Bestehende Tomcat-spezifische Annahmen müssen bei der Migration identifiziert und durch portable Jakarta-Mechanismen oder durch von `chronivaro-app` verwaltete Infrastruktur ersetzt werden.

### 15.8 Logging und Beobachtbarkeit

Jetty muss in das bestehende Logging- und Beobachtbarkeitskonzept von Chronivaro integriert werden.

Es darf kein unabhängiges Logging-System ausschliesslich für Jetty eingeführt werden.

Mindestens folgende Ereignisse werden nachvollziehbar geloggt:

- Start des HTTP-Servers
- verwendete Bind Address und Port
- erfolgreiche Initialisierung der REST API
- erfolgreiche Initialisierung der Frontend-Auslieferung
- Fehler während des HTTP-Server-Starts
- kontrolliertes Stoppen des HTTP-Servers

Die in Abschnitt 18 definierten Anforderungen an strukturierte Logs, Metriken, Health und Readiness gelten auch für den eingebetteten HTTP-Server.

### 15.9 Tests und Verifikation

Die Embedded-Jetty-Laufzeit muss automatisiert getestet werden.

Mindestens abzudecken sind:

- erfolgreicher Start und Stop des HTTP-Servers;
- Erreichbarkeit eines repräsentativen REST-Endpunkts;
- korrekte Bereitstellung des Frontend Entry Points;
- korrekte Trennung zwischen Frontend-Ressourcen und REST API;
- Fehlerverhalten bei nicht verfügbarem HTTP-Port;
- kontrollierter Shutdown der Anwendung.

REST-Integrationstests sollen soweit sinnvoll gegen die tatsächlich eingebettete HTTP-Laufzeit ausgeführt werden. Unit-Tests für fachliche Logik und Tests einzelner REST-Ressourcen sollen jedoch weiterhin unabhängig vom konkreten Servlet-Container bleiben.

Zusätzlich muss die Anwendung ohne Tomcat manuell verifizierbar sein:

```bash
java -jar chronivaro.jar
```

Danach müssen mindestens das Frontend unter `/` und ein repräsentativer Endpunkt unter `/rest/chronivaro/v1/...` erreichbar sein.


## 16. Strolch-Modellierung

Die konkrete Abbildung folgt den im Projekt verwendeten Strolch-Konventionen. Als Ausgangspunkt wird folgende Zuordnung empfohlen:

| Fachliches Konzept | Empfohlene Strolch-Abbildung |
| --- | --- |
| Mitarbeiter, Team, Standort | `Resource` |
| Arbeitsplanversion | eigene versionierte Entität oder Konfigurations-`Resource` mit Gültigkeit |
| Zeitbuchung | transaktional gespeicherte Domänenentität |
| Abwesenheit | transaktional gespeicherte Domänenentität mit Statusübergängen |
| Ferienkontobuchung | unveränderliche Journalentität |
| Monatsperiode | Entität mit explizitem Statusmodell |
| globale Einstellungen | Konfiguration und versionierte Parameter |

Alle schreibenden Operationen laufen in einer Strolch-Transaktion. Fachliche Zustandsänderungen werden über Core-Services ausgeführt und nicht durch direkte Manipulation aus der REST-Schicht.

## 17. Sicherheit und Datenschutz

### 17.1 Authentifizierung

Chronivaro verwendet die in der Zielumgebung etablierte Authentifizierung. Die REST-API darf keine fachlichen Endpunkte anonym freigeben.

### 17.2 Autorisierung

Mindestens folgende Berechtigungen werden getrennt geprüft:

- eigene Zeiten lesen und ändern
- fremde Zeiten lesen und ändern
- Abwesenheiten genehmigen
- Perioden genehmigen und wieder öffnen
- Ferienkonten korrigieren
- Reports lesen und exportieren
- Anwesenheitsstatus lesen
- sensible Abwesenheitsgründe lesen
- Konfiguration administrieren

### 17.3 Datenschutz

- Krankheits- und Unfallinformationen sind besonders restriktiv sichtbar.
- Audit-Daten sind nur für berechtigte Rollen zugänglich.
- Reports und Exporte folgen denselben Zugriffsregeln wie die Anwendung.
- Aufbewahrungs- und Löschfristen sind vor Produktivbetrieb organisatorisch festzulegen.
- Kommentare dürfen nicht für unnötige medizinische Details verwendet werden.

## 18. Nichtfunktionale Anforderungen

### 18.1 Zuverlässigkeit

- schreibende Operationen sind transaktional
- wiederholte Client-Anfragen dürfen keine unbemerkten Doppelbuchungen erzeugen
- Berechnungen sind deterministisch und automatisiert getestet
- abgeschlossene Perioden bleiben reproduzierbar

### 18.2 Performance

Zielwerte für das MVP bei normaler Unternehmensgrösse:

- Statusseite: Antwort innerhalb von 2 Sekunden
- Monatsübersicht eines Mitarbeiters: innerhalb von 2 Sekunden
- Teamreport für 100 Mitarbeitende und einen Monat: innerhalb von 5 Sekunden
- Server-seitige Pagination bei grossen Datenmengen

### 18.3 Beobachtbarkeit

- strukturierte Logs mit Korrelations-ID
- keine sensiblen Inhalte in Standardlogs
- Metriken für Antwortzeiten und Fehler
- nachvollziehbare Fehlercodes
- Health- und Readiness-Prüfungen

### 18.4 Kompatibilität

- aktuelle Versionen von Firefox, Chromium-basierten Browsern und Edge
- responsive Nutzung ab einer sinnvollen Smartphone-Breite
- keine Abhängigkeit von proprietären Browsererweiterungen

## 19. Teststrategie

### 19.1 Unit-Tests im Core

Mindestens folgende Fälle:

- Sollzeit für verschiedene Wochentage und Pensen
- Pensumsänderung mitten im Monat
- Ein- und Austritt mitten im Monat
- ganzer und halber Feiertag
- halbe und ganze Abwesenheit
- stundenweise Abwesenheit
- Krankheit an einem Arbeitstag
- Abwesenheit an einem freien Tag
- Ferien über Wochenende und Feiertag
- Arbeit über Mitternacht (automatische Aufteilung und Begrenzung auf Sollzeit bei vergessenem Timer)
- Sommer-/Winterzeitwechsel
- überlappende Zeitbuchungen
- korrekte Ableitung von Unterbrüchen zwischen mehreren Arbeitsblöcken
- Saldo über Monatsgrenzen
- Ferienjournal mit Anspruch, Bezug, Korrektur und Verfall

### 19.2 REST-Integrationstests

- erfolgreiche CRUD-Operationen
- Validierungsfehler und Fehlerformat
- Rollen- und Teamgrenzen
- Zugriff auf sensible Abwesenheitsgründe
- Nebenläufigkeitskonflikte
- gesperrte Perioden
- CSV-Export und Encoding

### 19.3 UI-Tests

- Start/Stoppen und erneuter Start eines weiteren Arbeitsblocks
- Darstellung der Arbeitsblöcke und der daraus abgeleiteten Unterbrüche
- manuelle Erfassung
- Abwesenheitsantrag und Genehmigung
- Monatsabschluss
- Fehler-, Leer- und Ladezustände
- Tastaturbedienung
- Statusdarstellung zusätzlich zur Farbe

### 19.4 Runtime- und HTTP-Integrationstests

- Start und Stop der Anwendung mit Embedded Jetty
- REST-Zugriff über `/rest/chronivaro/v1`
- Auslieferung von `index.html` und statischen Assets
- parallele Bereitstellung von REST API und Frontend über denselben HTTP-Server
- Fehler beim Binden eines bereits belegten HTTP-Ports
- kontrollierter Shutdown von Jetty und Strolch Runtime

## 20. Akzeptanzkriterien für das MVP

Das MVP gilt als fachlich abnahmebereit, wenn:

1. ein Administrator Mitarbeiter, Team, Standort, Feiertagskalender und Arbeitsplan erfassen kann;
2. ein Mitarbeiter mehrere Arbeitsblöcke pro Tag erfassen kann, wobei Unterbrüche aus den zeitlichen Lücken abgeleitet werden;
3. die Anwendung Überlappungen und mehrere laufende Buchungen verhindert;
4. Soll- und Istzeit für Tag und Monat korrekt berechnet werden;
5. Pensumsänderungen alte Monatsauswertungen nicht verfälschen;
6. Ferien, Krankheit und mindestens eine weitere Abwesenheitsart erfasst werden können;
7. Halb- und Ganztage anhand des individuellen Arbeitsplans berechnet werden;
8. ein Vorgesetzter Abwesenheiten genehmigen und ablehnen kann;
9. Ferienbezüge nachvollziehbare Kontobuchungen erzeugen;
10. die Statusseite binär und aktuell anzeigt, ob ein Mitarbeiter arbeitet oder nicht arbeitet;
11. nicht berechtigte Benutzer keine sensiblen Abwesenheitsgründe sehen;
12. Monatsperioden eingereicht, genehmigt, abgelehnt, gesperrt und begründet wieder geöffnet werden können;
13. ein Monatsreport Sollzeit, Istzeit, Abwesenheiten und Saldo zeigt;
14. der Monatsreport als CSV exportiert werden kann;
15. alle fachlich relevanten Änderungen im Audit-Log nachvollziehbar sind;
16. die definierten Kernberechnungen automatisiert getestet sind;
17. Chronivaro ohne externen Servlet-Container als eigenständige Java-Anwendung gestartet werden kann;
18. Embedded Jetty sowohl das Frontend als auch die bestehende REST API bereitstellt;
19. Start, HTTP-Betrieb und kontrollierter Shutdown der Embedded-Jetty-Laufzeit automatisiert getestet sind.

## 21. Vorgeschlagene Implementierungsreihenfolge

### Phase 1 – Fundament

- Maven-Multimodulprojekt einschliesslich `chronivaro-app` erstellen
- Strolch-Laufzeit und Modellinitialisierung einrichten
- eigenständigen Application Entry Point einrichten
- Embedded Jetty als anwendungseigene HTTP-Laufzeit einrichten
- Jersey / JAX-RS in Embedded Jetty integrieren
- `chronivaro-web` über Embedded Jetty ausliefern
- Authentifizierung und Rollenmodell anbinden
- gemeinsame Fehler- und Audit-Infrastruktur aufbauen

### Phase 2 – Mitarbeiter und Sollzeit

- Mitarbeiter, Teams und Standorte
- versionierte Arbeitspläne
- Feiertagskalender
- Sollzeitberechnung mit Unit-Tests

### Phase 3 – Arbeitszeiterfassung

- WorkEntry-Modell und Validierungen
- Start-/Stopp-Logik für mehrere Arbeitsblöcke pro Tag
- Tages- und Wochenansicht
- Istzeit- und Saldoberechnung

### Phase 4 – Abwesenheiten und Ferien

- konfigurierbare Abwesenheitsarten
- Antrags- und Genehmigungsworkflow
- Ferienjournal
- Ferienübersicht

### Phase 5 – Status und Reports

- Anwesenheitsstatus
- Monatsreport
- Teamreport
- CSV-Export

### Phase 6 – Periodenabschluss und Härtung

- Monatsabschluss und Sperrung
- Audit-Vervollständigung
- Berechtigungs- und Datenschutztests
- Performance-, Browser- und Abnahmetests

## 22. Offene Produktentscheidungen

Vor oder während der Implementierung sind folgende Punkte verbindlich zu entscheiden:

1. Dürfen Zeit- und Feriensaldi negativ werden?
2. Gibt es eine Rundung, beispielsweise auf fünf Minuten?
3. Wer ist Genehmiger, wenn ein Mitarbeiter mehreren Teams zugeordnet ist?
4. Darf ein Mitarbeiter genehmigte Abwesenheiten selbst stornieren?
5. Wie wird Krankheit während bereits genehmigter Ferien behandelt?
6. Wie viele Ferien dürfen ins Folgejahr übertragen werden und wann verfallen sie?
7. Werden Überstunden unbegrenzt übertragen oder begrenzt?
8. Soll Homeoffice auf der Statusseite separat sichtbar sein?
9. Welche Authentifizierung wird in der Zielumgebung verwendet?
10. Welche Aufbewahrungs- und Löschfristen gelten?
11. Sind mehrere Rechtseinheiten, Länder, Zeitzonen oder Währungen geplant?

Bis zur Entscheidung gelten im MVP folgende Annahmen:

- keine Rundung
- negative Zeitsaldi sind erlaubt
- negative Feriensaldi sind nicht erlaubt
- ein Mitarbeiter gehört zu genau einem primären Team
- genehmigte Abwesenheiten können nur über einen Stornierungsprozess geändert werden
- `Europe/Zurich` ist die Standardzeitzone
- Homeoffice wird optional als Arbeitsort angezeigt

## 23. Definition of Done

Eine Funktion ist abgeschlossen, wenn:

- die fachlichen Akzeptanzkriterien erfüllt sind;
- Core-Logik und Randfälle automatisiert getestet sind;
- REST-Endpunkte dokumentiert und integrationsgetestet sind;
- Berechtigungen serverseitig geprüft sind;
- relevante Änderungen im Audit-Log erscheinen;
- UI-Zustände für Laden, leer, Erfolg und Fehler umgesetzt sind;
- keine bekannten Fehler hoher Priorität bestehen;
- Konfiguration und Betriebshinweise dokumentiert sind;
- der Maven-Build mit JDK 25 reproduzierbar erfolgreich ist;
- die Anwendung ohne externen Servlet-Container startbar ist;
- Frontend und REST API über den eingebetteten Jetty-Server erreichbar sind.
