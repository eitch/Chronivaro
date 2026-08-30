# Chronivaro – Fachliches Domänenmodell

Dieses Dokument definiert das vollständige fachliche Domänenmodell von Chronivaro einschliesslich aller Entitäten, ihrer Pflichtattribute und Domäneninvarianten.

## 1. Übersicht der Domänenentitäten

```text
┌──────────────┐         1:1         ┌──────────────┐
│ Strolch User │ ◄─────────────────► │   Employee   │
└──────────────┘ (reine Benutzer     └──────┬───────┘
                  ohne Employee             │ 1:N
                  möglich)                  ├──────────────────┬─────────────────┬─────────────────┐
                                            ▼                  ▼                 ▼                 ▼
                                    ┌──────────────┐    ┌──────────────┐  ┌──────────────┐  ┌──────────────┐
                                    │  Schedule-   │    │   WorkDay    │  │   Absence    │  │  Vacation-   │
                                    │   Version    │    └──────┬───────┘  └──────────────┘  │ AccountEntry │
                                    └──────────────┘           │ 1:N                    └──────────────┘
                                                               ▼
                                                        ┌──────────────┐
                                                        │  WorkEntry   │
                                                        └──────────────┘
```

---

## 2. Entitätsspezifikationen

### 2.1 Employee – Mitarbeiter

Ein `Employee` repräsentiert das fachliche Mitarbeiterprofil für die Zeiterfassung, Ferienverwaltung und Soll-/Ist-Auswertung.

#### Attribute

| Attribut | Typ / Format | Beschreibung |
|---|---|---|
| `employeeId` | String | Stabile, unveränderliche interne ID |
| `personId` | String | Referenz auf den verknüpften Strolch-Benutzer bzw. Person |
| `personnelNumber` | String | Eindeutige Personalnummer |
| `displayName` | String | Anzeigename des Mitarbeiters |
| `teamId` | String | Referenz auf das zugeordnete Team |
| `locationId` | String | Referenz auf den Arbeitsstandort (und Feiertagskalender) |
| `timeZone` | String (IANA) | Zeitzone, standardmässig `Europe/Zurich` |
| `entryDate` | Datum (`YYYY-MM-DD`) | Eintrittsdatum |
| `exitDate` | Datum (`YYYY-MM-DD`) | Optionales Austrittsdatum |
| `active` | Boolean | Fachlicher Aktivstatus |
| `currentWorkDayId` | String | Referenz auf den aktuellen `WorkDay` |

#### Regeln und Invarianten

- **Nicht-destruktive Persistenz:** Eine `Employee`-Ressource wird bei Benutzerlöschungen niemals physisch aus dem System gelöscht, um historische Buchungen, Saldi und Berichte konsistent und reproduzierbar zu halten.
- **Deaktivierung:** Wird der mit dem Mitarbeiter verknüpfte Strolch-Benutzer gelöscht, wird der `Employee` automatisch auf inaktiv gesetzt (`active = false`).
- **Reaktivierung:** Ein inaktiver Mitarbeiter kann später wieder aktiviert werden (`active = true`). Bei der Reaktivierung wird der zugehörige Strolch-Benutzer im System neu angelegt und für die Registrierung/Passwortvergabe freigegeben (Workflow siehe [Geschäftsprozesse](04-business-processes.md#44-reaktivierung-von-mitarbeitern)).
- **Profileinsicht:** Mitarbeitende können ihre eigenen Mitarbeiter- und Profilinformationen (u. a. Personalnummer, Eintrittsdatum, Austrittsdatum, Anzeigename, zugeordnetes Team, Standort, Zeitzone sowie aktueller Arbeitsplan und Beschäftigungsgrad) in der Benutzeroberfläche einsehen.

---

### 2.2 User – Strolch-Benutzer (auch für Nicht-Mitarbeiter)

Strolch verwaltet Benutzer, Passwörter und Rollen unabhängig von der fachlichen `Employee`-Ressource.

#### Regeln

- Jeder `Employee` referenziert einen Strolch-Benutzer (`personId`), um sich anzumelden und Arbeitszeiten zu erfassen.
- **Reine Systembenutzer:** Es können Strolch-Benutzer ohne verknüpftes `Employee`-Profil existieren (z. B. reine Systemadministratoren, HR-Personal ohne Zeiterfassung, reine Vorgesetzte oder externe Revisoren).
- Reine Benutzer besitzen Rollen (z. B. `Admin`, `HR`, `Supervisor`, `Reader`), Benutzername, Name und Status, werden jedoch nicht in der Mitarbeiterübersicht, Zeiterfassung oder Statusanzeige geführt.
- Die Benutzerverwaltung erlaubt die Pflege dieser Benutzer inklusive Rollenzuweisung, Löschung und Passwort-Initialisierung (`SET_PASSWORD`-Challenge).
- **Löschen von Benutzern:**
  - Wird ein reiner Strolch-Benutzer (ohne Mitarbeiterverknüpfung) gelöscht, wird das Benutzerkonto in Strolch entfernt.
  - Wird ein Strolch-Benutzer gelöscht, der mit einem `Employee` verknüpft ist, wird der Strolch-Benutzer entfernt und die zugehörige `Employee`-Ressource auf `active = false` gesetzt. Die `Employee`-Ressource selbst wird nicht gelöscht.
  - Bei einer späteren Reaktivierung des Mitarbeiters wird ein neuer Strolch-Benutzer erstellt und mit dem Mitarbeiter verknüpft.

---

### 2.3 EmploymentScheduleVersion – Versionierter Arbeitsplan

Ein Mitarbeiter besitzt mindestens einen Arbeitsplan. Jede Änderung des Beschäftigungsgrads oder der Sollzeiten erzeugt eine neue, historisierte Version.

#### Attribute

| Attribut | Typ / Format | Beschreibung |
|---|---|---|
| `scheduleId` | String | Eindeutige ID der Arbeitsplanversion |
| `employeeId` | String | Referenz auf den Mitarbeiter |
| `validFrom` | Datum (`YYYY-MM-DD`) | Erster Gültigkeitstag, inklusive |
| `validTo` | Datum (`YYYY-MM-DD`) | Letzter Gültigkeitstag, inklusive; optional (offenes Ende) |
| `employmentPercentage` | Double | Beschäftigungsgrad in Prozent (z. B. `80.0` oder `100.0`) |
| `weeklyTargetMinutes` | Integer | Sollzeit pro Woche in Minuten |
| `mondayMinutes` bis `sundayMinutes` | Integer | Sollzeit je Wochentag in Minuten |

#### Regeln und Invarianten

- **Keine Überschneidungen:** Versionen desselben Mitarbeiters dürfen sich zeitlich nicht überschneiden.
- **Eindeutigkeit:** Für jeden aktiven Beschäftigungstag muss genau eine Version bestimmbar sein.
- **Korrekturen vergangener Versionen:** Vergangene Versionen dürfen nur mit entsprechender Berechtigung korrigiert werden.
- **Pensum vs. Verteilung:** Pensum und Wochentagsverteilung sind getrennt zu speichern, damit beispielsweise ein 80-%-Pensum auf vier oder fünf Tage verteilt werden kann.
- **Eintritt unter dem Monat:** Tritt ein Mitarbeiter im Laufe eines Monats ein (`entryDate`), gilt für alle Tage vor dem Eintrittsdatum eine tägliche Sollzeit von `0` Minuten. Die Monatssollzeit ergibt sich ausschliesslich aus der Summe der aktiven Tage ab `entryDate` bis Monatsende. Tage vor dem Eintrittsdatum werden im Monatskalender als inaktiv dargestellt und lösen keine Warnungen vor fehlenden Buchungen aus.
- **Austritt unter dem Monat:** Scheidet ein Mitarbeiter im Laufe eines Monats aus (`exitDate`), gilt für alle Tage nach dem Austrittsdatum eine Sollzeit von `0` Minuten.

---

### 2.4 WorkDay – Arbeitstag

Ein `WorkDay` fasst alle Arbeitszeitbuchungen eines Mitarbeiters für einen konkreten Kalendertag zusammen und referenziert den zum Zeitpunkt der Erstellung gültigen Arbeitsplan.

#### Attribute

| Attribut | Typ / Format | Beschreibung |
|---|---|---|
| `workDayId` | String | Eindeutige ID |
| `employeeId` | String | Referenz auf den Mitarbeiter |
| `date` | Datum (`YYYY-MM-DD`) | Kalendertag |
| `scheduleId` | String | Referenz auf die zum Erstellungszeitpunkt aktive `EmploymentScheduleVersion` |
| `workEntryIds` | Liste von Strings | Liste der zugehörigen `WorkEntry`-Referenzen |

#### Regeln und Invarianten

- Pro Mitarbeiter und Datum existiert maximal ein `WorkDay`.
- Der `WorkDay` wird beim ersten Starten der Arbeit für ein neues Datum automatisch erstellt.
- Er dient als Einstiegspunkt für die Suche nach aktiven Buchungen und vereinfacht die Auswertung grosser Datenmengen.

---

### 2.5 WorkEntry – Arbeitszeitbuchung

Ein `WorkEntry` bildet ausschliesslich einen tatsächlich gearbeiteten, kontinuierlichen Zeitblock ab.

#### Attribute

| Attribut | Typ / Format | Beschreibung |
|---|---|---|
| `workEntryId` | String | Eindeutige ID |
| `workDayId` | String | Referenz auf den zugehörigen `WorkDay` |
| `start` | ISO-8601 Timestamp | Startzeitpunkt mit Zeitzonen-Offset |
| `end` | ISO-8601 Timestamp | Endzeitpunkt mit Zeitzonen-Offset; bei laufender Buchung leer |
| `source` | Enum | Quelle der Buchung: `TIMER`, `MANUAL`, `IMPORT`, `ADMIN` |
| `comment` | String | Optionaler Kommentar des Erfassers |
| `createdBy` | String | Benutzername des Erstellers |
| `workingLocation` | Enum | Arbeitsort: `HOME_OFFICE`, `OFFICE` oder `CUSTOMER` |

#### Regeln und Invarianten

- **Maximal eine laufende Buchung:** Pro Mitarbeiter ist zu jedem Zeitpunkt höchstens eine laufende Buchung erlaubt.
- **Chronologie:** Das Ende muss nach dem Start liegen.
- **Keine Überlappungen:** Überlappende Buchungen sind unzulässig.
- **Tagesgrenze & Mitternachtsaufteilung:** Buchungen müssen am selben Kalendertag starten und enden. Geht eine Arbeit über Mitternacht hinaus, wird die Buchung um 24:00 Uhr des Starttages beendet und für den Folgetag eine neue Buchung auf dem entsprechenden `WorkDay` erstellt.
- **Pausen als Unterbrüche:** Pausen werden nicht als eigene Entität erfasst. Eine Unterbrechung ergibt sich ausschliesslich aus der zeitlichen Lücke zwischen zwei Arbeitsblöcken.
- **Tageszeiteingaben:** Direkte Tageszeiteingaben werden intern als separate manuelle Tagesbuchung oder als klar gekennzeichnete Dauerbuchung abgebildet; beide Erfassungsarten dürfen nicht zu einer Doppelzählung führen.
- **Kommentare:** Mitarbeitende können zu jedem `WorkEntry` einen optionalen Kommentar erfassen und bearbeiten.
- **Bearbeitung durch Mitarbeitende:** Mitarbeitende können ihre eigenen, noch nicht eingereichten oder gesperrten `WorkEntry`-Buchungen in offenen Perioden anpassen (Start- und Endzeit, Arbeitsort, Kommentar).
- **Administrative/Supervisorische Korrekturen:** Vorgesetzte (für zugeordnete Teams) sowie Personaladministration und Administratoren (unternehmensweit) können Zeitbuchungen in offenen Perioden vollständig anpassen, manuell erfassen oder löschen. Jede Änderung wird revisionssicher mit Vorher-/Nachher-Zustand, Begründung und Bearbeiter im Audit-Log protokolliert.
- **Mitternachtsarbeit bei manueller Bearbeitung:** Wenn ein Vorgesetzter oder HR die Arbeitszeit bearbeitet, besteht die Option anzugeben, dass der Mitarbeiter über Mitternacht gearbeitet hat (Endzeit am Folgetag mit automatischer Mitternachtsaufteilung).
- **Visuelle Hervorhebung und Ausweisung des Erstellers:**
  - Alle nachträglich modifizierten sowie manuell erstellten Arbeitszeitbuchungen (`source = MANUAL` oder modifizierter Status) werden in der UI und in Berichten/Exporten visuell hervorgehoben (z. B. Badge/Badge-Kennzeichnung).
  - Wurde eine Arbeitszeitbuchung nicht durch den Mitarbeiter selbst erstellt (Fremderfassung), wird transparent ausgewiesen, von wem (`createdBy`) der Eintrag erstellt wurde.
- **Arbeitsort:** Jeder `WorkEntry` besitzt einen Arbeitsort (`HOME_OFFICE`, `OFFICE`, `CUSTOMER`). Ein Arbeitstag darf höchstens einen Arbeitsort am Vormittag und einen Arbeitsort am Nachmittag haben. Ein Wechsel des Arbeitsorts erzeugt separate Zeitblöcke.
- **Dauerbereiche im Dashboard:** Für die Schnellauswahl im Dashboard werden `HALF_DAY` (`MORNING` oder `AFTERNOON`) und `FULL_DAY` unterstützt.

---

### 2.6 Wöchentliche Standardarbeitsorte

Mitarbeitende können für jeden Wochentag einen Standardarbeitsort hinterlegen, der beim Öffnen eines neuen `WorkDay` im Dashboard vorausgewählt wird.

#### Attribute

| Attribut | Typ / Format | Beschreibung |
|---|---|---|
| `weekday` | Enum | Wochentag (`MONDAY` bis `SUNDAY`) |
| `workingLocation` | Enum | `HOME_OFFICE`, `OFFICE` oder `CUSTOMER` |
| `durationType` | Enum | `HALF_DAY` oder `FULL_DAY` |
| `halfDayPart` | Enum | `MORNING` oder `AFTERNOON` (nur bei `HALF_DAY`) |

#### Regeln

- Pro Wochentag und Tageshälfte darf höchstens ein Standard konfiguriert sein.
- Der Standard erzeugt nicht selbstständig Buchungen, sondern füllt Auswahlen im Dashboard vor.
- Mitarbeitende können den vorausgewählten Ort vor dem Start oder beim Aktualisieren von Buchungen überschreiben.
- Standards gelten nicht für arbeitsfreie Tage, ausser es wird ausdrücklich Arbeitszeit erfasst.

---

### 2.7 AbsenceType – Abwesenheitsart

`AbsenceType` definiert die fachlichen und abrechnungsrelevanten Eigenschaften von Abwesenheiten.

#### Attribute

| Attribut | Typ / Format | Beschreibung |
|---|---|---|
| `absenceTypeId` | String | Eindeutige ID |
| `name` | String | Sichtbare Bezeichnung |
| `code` | String | Stabiler, sprachunabhängiger technischer Code |
| `creditTargetTime` | Boolean | Gibt an, ob die Abwesenheit als erfüllte Sollzeit gutgeschrieben wird |
| `deductVacation` | Boolean | Gibt an, ob die Abwesenheit das Ferienkonto belastet |
| `paid` | Boolean | Bezahlt oder unbezahlt |
| `approvalRequired` | Boolean | Genehmigung durch Vorgesetzten erforderlich |
| `commentRequired` | Boolean | Zwingende Eingabe eines Kommentars erforderlich |
| `allowedDurations` | Set von Enums | Erlaubte Erfassungsdauern: `HOURS`, `HALF_DAY`, `FULL_DAY` |
| `visibleOnPublicStatus` | Boolean | Ob der Typ auf der öffentlichen Statusseite sichtbar sein darf (standardmässig `false`) |
| `active` | Boolean | Für neue Erfassungen verfügbar |

#### Vorkonfigurierte Standard-Abwesenheitsarten

Chronivaro liefert initial folgende Standard-Abwesenheitsarten aus:

| Code | Name | Sollzeit-Gutschrift | Ferienabzug | Bezahlt | Genehmigungspflichtig | Kommentarpflichtig | Erlaubte Dauern | Sichtbar Status |
|---|---|---|---|---|---|---|---|---|
| `VACATION` | Ferien | `true` | `true` | `true` | `true` | `false` | `HALF_DAY`, `FULL_DAY` | `false` |
| `ILLNESS` | Krankheit | `true` | `false` | `true` | `true` | `false` | `HOURS`, `HALF_DAY`, `FULL_DAY` | `false` |
| `ACCIDENT` | Unfall | `true` | `false` | `true` | `true` | `false` | `HOURS`, `HALF_DAY`, `FULL_DAY` | `false` |
| `MILITARY_CIVIL_DEFENSE` | Militär / Zivilschutz | `true` | `false` | `true` | `true` | `false` | `HALF_DAY`, `FULL_DAY` | `false` |
| `DOCTOR_APPOINTMENT` | Arzttermin | `true` | `false` | `true` | `false` | `false` | `HOURS` | `false` |
| `TRAINING` | Weiterbildung | `true` | `false` | `true` | `true` | `true` | `HOURS`, `HALF_DAY`, `FULL_DAY` | `false` |
| `PARENTAL_LEAVE` | Elternurlaub | `true` | `false` | `true` | `true` | `false` | `HALF_DAY`, `FULL_DAY` | `false` |
| `UNPAID_LEAVE` | Unbezahlter Urlaub | `false` | `false` | `false` | `true` | `true` | `HALF_DAY`, `FULL_DAY` | `false` |
| `OVERTIME_COMPENSATION` | Überstundenkompensation | `false` | `false` | `true` | `true` | `false` | `HOURS`, `HALF_DAY`, `FULL_DAY` | `false` |
| `OTHER` | Sonstige Abwesenheit | `false` | `false` | `true` | `true` | `true` | `HOURS`, `HALF_DAY`, `FULL_DAY` | `false` |

#### Lokalisierungsregeln für Abwesenheitsarten

- `code` bleibt sprachunabhängig und ist der stabile technische Bezeichner.
- Der bestehende, administrativ konfigurierte `name` wird in dieser Ausbaustufe nicht automatisch übersetzt.
- Statische Enum-Werte und Systembegriffe werden über i18n-Schlüssel lokalisiert (Details siehe [UI und Lokalisierung](06-ui-and-localization.md)).

---

### 2.8 Absence – Abwesenheit

Repräsentiert einen konkreten Abwesenheitsantrag oder eine erfasste Abwesenheit eines Mitarbeiters.

#### Attribute

| Attribut | Typ / Format | Beschreibung |
|---|---|---|
| `absenceId` | String | Eindeutige ID |
| `employeeId` | String | Referenz auf den Mitarbeiter |
| `absenceTypeId` | String | Referenz auf die Abwesenheitsart |
| `startDate` | Datum (`YYYY-MM-DD`) | Erster Tag der Abwesenheit |
| `endDate` | Datum (`YYYY-MM-DD`) | Letzter Tag der Abwesenheit |
| `durationType` | Enum | `HOURS`, `HALF_DAY`, `FULL_DAY` |
| `halfDayPart` | Enum | Bei `HALF_DAY`: `MORNING` oder `AFTERNOON` |
| `minutes` | Integer | Minutenanzahl bei stundenweiser Erfassung (`HOURS`) |
| `comment` | String | Begründungskommentar |
| `status` | Enum | `DRAFT`, `SUBMITTED`, `APPROVED`, `REJECTED`, `CANCELLED` |
| `createdBy` | String | Benutzername des Erstellers (Mitarbeiter selbst oder Fremderfasser) |
| `decisionBy` | String | Benutzername des Genehmigers |
| `decisionAt` | ISO-8601 Timestamp | Zeitpunkt der Genehmigung / Ablehnung |
| `decisionComment` | String | Begründung bei Ablehnung oder Korrektur |

#### Regeln und Invarianten

- **Sollzeit-Anrechnung:** Ein ganzer Abwesenheitstag entspricht der individuellen Sollzeit dieses Tages; ein halber Tag entspricht 50 % der Sollzeit.
- **Arbeitsfreie Tage & Feiertage:** Erzeugen keine Abwesenheitsminuten.
- **Keine Überschneidungen:** Sich überschneidende Abwesenheiten sind unzulässig.
- **Entwurfsstatus (`DRAFT`):** Abwesenheiten können als Entwurf gespeichert, vor dem Einreichen beliebig bearbeitet oder verworfen (`CANCELLED`) werden. Das Verwerfen eines Entwurfs löst keine Ferienkontobuchung aus.
- **Einreichung (`SUBMITTED`):** Überführt den Antrag in den Genehmigungsworkflow für Vorgesetzte.
- **Fremderfassung:** Vorgesetzte und HR/Administration können Abwesenheiten im Namen von Mitarbeitenden erfassen (wahlweise direkt `APPROVED` oder regulär `SUBMITTED`). Der Ersteller (`createdBy`) wird transparent protokolliert.

---

### 2.9 VacationAccountEntry – Ferienkontobuchung

Das Ferienguthaben wird als unveränderliches Journal (`VacationAccountEntry`) geführt und nicht als veränderlicher Einzelwert.

#### Attribute

| Attribut | Typ / Format | Beschreibung |
|---|---|---|
| `vacationEntryId` | String | Eindeutige ID |
| `employeeId` | String | Referenz auf den Mitarbeiter |
| `effectiveDate` | Datum (`YYYY-MM-DD`) | Wirksamkeitsdatum der Buchung |
| `createdAt` | ISO-8601 Timestamp | Erstellungszeitpunkt |
| `entryType` | Enum | `ENTITLEMENT`, `CARRY_OVER`, `USAGE`, `CORRECTION`, `EXPIRY` |
| `amountMinutes` | Integer | Positive oder negative Anzahl Ferienminuten |
| `relatedAbsenceId` | String | Referenz auf die zugehörige `Absence` bei Ferienbezug (`USAGE`) |
| `comment` | String | Pflichtkommentar bzw. Begründung |
| `createdBy` | String | Benutzername des Erstellers |

#### Regeln und Invarianten

- **Interne Minutenführung:** Ferienguthaben wird intern stets in ganzzahligen Minuten geführt (standardmässig entspricht 1 Ferientag = 480 Minuten; Details siehe [Berechnungsregeln](03-business-rules.md#6-automatisierte-ferienanspruchsregelung)).
- **Unveränderlichkeit:** Journaleinträge sind unveränderlich (Append-Only). Korrekturen und Stornierungen erfolgen über `CORRECTION`-Gegenbuchungen.
- **Manuelle Korrekturen:** Personaladministration und Vorgesetzte können manuelle `CORRECTION`-Buchungen mit zwingendem Begründungskommentar vornehmen.
- **Negative Feriensaldi:** Sind nicht zulässig. Genehmigungen von Ferienbezügen, die das Guthaben übersteigen, werden blockiert.

---

### 2.10 HolidayCalendar und Holiday – Feiertage

Definiert arbeitsfreie Feiertage je Region oder Standort.

#### Attribute

| Attribut | Typ / Format | Beschreibung |
|---|---|---|
| `calendarId` | String | Eindeutige ID des Feiertagskalenders |
| `name` | String | Bezeichnung (z. B. `Kanton Bern`) |
| `date` | Datum (`YYYY-MM-DD`) | Datum des Feiertags |
| `holidayName` | String | Name des Feiertags |
| `creditFactor` | Double | Gutschriftfaktor: in der Regel `1.0` (ganzer Feiertag), optional `0.5` (halber Feiertag) |

Der Standort (`locationId`) eines Mitarbeiters bestimmt standardmässig den Feiertagskalender.

---

### 2.11 TimePeriod – Erfassungs- und Abschlussperiode

Repräsentiert die monatliche Zeiterfassungs- und Genehmigungsperiode eines Mitarbeiters.

#### Attribute

| Attribut | Typ / Format | Beschreibung |
|---|---|---|
| `periodId` | String | Eindeutige ID |
| `employeeId` | String | Referenz auf den Mitarbeiter |
| `yearMonth` | String (`YYYY-MM`) | Abrechnungsmonat (z. B. `2026-08`) |
| `status` | Enum | `OPEN`, `SUBMITTED`, `APPROVED`, `REJECTED`, `LOCKED` |
| `submittedAt` | ISO-8601 Timestamp | Einreichungszeitpunkt |
| `approvedAt` | ISO-8601 Timestamp | Genehmigungszeitpunkt |
| `approvedBy` | String | Benutzername des Genehmigers |
| `comment` | String | Begründungskommentar bei Ablehnung oder Genehmigung |
| `calculationSnapshot` | JSON-Objekt | Unveränderlicher Abschlussstand mit Soll-/Istzeiten und Saldi |

---

### 2.12 AuditEvent – Revisionssicherer Audit-Trail

Protokolliert alle fachlich und sicherheitsrelevanten Änderungen im System unveränderlich.

#### Attribute

| Attribut | Typ / Format | Beschreibung |
|---|---|---|
| `auditId` | String | Eindeutige ID des Audit-Ereignisses |
| `timestamp` | ISO-8601 Timestamp | Zeitpunkt des Ereignisses |
| `username` | String | Ausführender Benutzer |
| `entityType` | String | Typ der betroffenen Entität (z. B. `Employee`, `WorkEntry`, `Absence`, `User`) |
| `entityId` | String | ID der betroffenen Entität |
| `action` | String | Ausgeführte Aktion (z. B. `CREATE`, `UPDATE`, `DELETE`, `APPROVE`, `REOPEN`) |
| `previousValue` | JSON / String | Zustand vor der Änderung |
| `newValue` | JSON / String | Zustand nach der Änderung |
| `reason` | String | Fachliche Begründung (falls erforderlich) |
| `correlationId` | String | Eindeutige Korrelations-ID der auslösenden HTTP-Anfrage |

---

### 2.13 Globale Anwendungskonfiguration

Hinterlegt produktweite Einstellungen und Darstellungsoptionen.

#### Attribute

| Attribut | Typ / Format | Beschreibung |
|---|---|---|
| `defaultLanguage` | String | Standardsprache der Anwendung (`de` oder `en`) |
| `companyName` | String | Global angezeigter Firmenname |
| `companyLogo` | String (Base64 / URL) | Optionales globales Firmenlogo für UI und PDF |
| `serverBaseUrl` | String | Basis-URL des Servers (z. B. `https://chronivaro.example.com`) für Links in Onboarding-E-Mails |
| `officeHoursStart` | String (`HH:mm`) | Beginn der regulären Büro-/Geschäftszeiten (z. B. `07:00`) |
| `officeHoursEnd` | String (`HH:mm`) | Ende der regulären Büro-/Geschäftszeiten (z. B. `18:00`) |

---

### 2.14 OnCallPeriod – Pikettdienst / Rufbereitschaft

Definiert Zeiträume, in denen Mitarbeitende für den Pikettdienst eingeteilt sind.

#### Attribute

| Attribut | Typ / Format | Beschreibung |
|---|---|---|
| `onCallPeriodId` | String | Eindeutige ID |
| `employeeId` | String | Referenz auf den Mitarbeiter |
| `startDate` | Datum (`YYYY-MM-DD`) | Beginn der Pikettperiode |
| `startTime` | Zeit (`HH:mm`) | Optionale Startzeit |
| `endDate` | Datum (`YYYY-MM-DD`) | Ende der Pikettperiode |
| `endTime` | Zeit (`HH:mm`) | Optionale Endzeit |
| `comment` | String | Optionaler Kommentar |
| `createdBy` | String | Ersteller (Vorgesetzter oder HR) |

#### Regeln

- HR und Vorgesetzte konfigurieren Pikettperioden für Mitarbeitende.
- Zeiten ausserhalb der regulären Bürozeiten (`officeHoursStart`/`officeHoursEnd`) gelten als Freizeit/Off-duty.
- Arbeitet ein Mitarbeiter während einer aktiven Pikettperiode ausserhalb der Bürozeiten, kann beim Stoppen des Timers oder bei der Bearbeitung explizit angegeben werden, ob es sich um einen Piketteinsatz (`isOnCall = true`) oder um reguläre Überstunden handelt.
- Pikettbereitschaften und geleistete Einsätze werden in Tages-, Monats- und dedizierten Pikett-Reports ausgewiesen (siehe [Reports und Exporte](05-reports-and-exports.md)).

---

## 3. Strolch-Framework-Abbildung

Die Domänenkonzepte werden wie folgt auf Strolch-Grundelemente abgebildet:

| Fachliches Konzept | Strolch-Abbildung | Bemerkungen |
|---|---|---|
| `Employee`, `Team`, `Location` | `Resource` | Master Data mit ParameterBags |
| `EmploymentScheduleVersion` | Versionierte `Resource` / ParameterBag | Eigene Entität mit Gültigkeitszeitraum |
| `WorkDay`, `WorkEntry` | Transaktionale `Resource` / Domänenstruktur | Im `WorkDay` gruppierte Arbeitsblöcke |
| `AbsenceType` | Konfigurations-`Resource` | Vordefinierte und kundenspezifische Typen |
| `Absence` | Transaktionale Domänen-`Resource` | Explizite Statusübergänge (`DRAFT` bis `CANCELLED`) |
| `VacationAccountEntry` | Unveränderliche Journal-`Resource` | Append-Only Kontoführung |
| `TimePeriod` | Transaktionale `Resource` / `Order` | Periodenstatus und `calculationSnapshot` |
| `AuditEvent` | Audit-`Resource` / Strolch-Audit | Revisionssicher indiziert und paginiert |
| Globale Konfiguration | Strolch-Konfigurations-`Resource` | Zentral verwaltete Parameter |
