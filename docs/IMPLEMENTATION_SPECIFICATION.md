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

- eigene Arbeitszeiten erfassen und bearbeiten (Start- und Endzeit, Arbeitsort und Kommentar in offenen Perioden anpassen)
- eigene Abwesenheiten erfassen und einreichen
- eigene Mitarbeiterinformationen einsehen (z. B. Personalnummer, Eintrittsdatum, Beschäftigungsgrad, Arbeitsplan, Team, Standort)
- eigene Saldi und Reports einsehen
- eigene Ferienübersicht einsehen
- eigenen laufenden Arbeitstag starten und stoppen

### 3.2 Vorgesetzter

- Daten der zugeordneten Mitarbeitenden und Teams einsehen
- Arbeitszeitbuchungen (`WorkEntry`) für zugeordnete Mitarbeitende und Teams einsehen, manuell erfassen, vollständig bearbeiten (Start, Ende, Arbeitsort, Kommentar) und löschen (innerhalb offener Perioden)
- Abwesenheiten für zugeordnete Mitarbeitende und Teams im Namen der Mitarbeitenden erfassen
- Abwesenheiten genehmigen oder ablehnen
- Monatsperioden prüfen und genehmigen
- fehlende oder fehlerhafte Einträge erkennen
- Teamstatus einsehen

### 3.3 Personaladministration

- Mitarbeiterstammdaten verwalten
- Arbeitszeitbuchungen (`WorkEntry`) aller Mitarbeitenden einsehen, manuell erfassen, vollständig bearbeiten (Start, Ende, Arbeitsort, Kommentar) und löschen (innerhalb offener Perioden bzw. nach Wiedereröffnung)
- Abwesenheiten für alle Mitarbeitenden im Namen der Mitarbeitenden erfassen und anpassen
- Arbeitsmodelle und Ferienansprüche verwalten
- Abwesenheiten und Saldi korrigieren
- Perioden wieder öffnen
- teamübergreifende Reports erstellen

### 3.4 Administrator

- globale Konfiguration verwalten
- Benutzer, Rollen und Berechtigungen verwalten (auch für Nicht-Mitarbeiter)
- Abwesenheitsarten, Feiertage und Standorte konfigurieren
- technische Administration durchführen

### 3.5 Leseberechtigter Benutzer – optional

- freigegebene Reports einsehen
- keine Daten verändern

### 3.6 Reine Systembenutzer ohne Mitarbeiterprofil

- Benutzer wie Systemadministratoren, HR-Manager, reine Vorgesetzte oder externe Revisoren können als Strolch-Benutzer mit entsprechenden Rollen existieren, ohne selbst Arbeitszeiten zu erfassen oder ein `Employee`-Profil zu besitzen.

## 4. Umfang

### 4.1 MVP

Der erste produktiv nutzbare Umfang enthält:

1. Mitarbeiter-, Benutzer- und Teamverwaltung (einschliesslich reiner Systembenutzer ohne Mitarbeiterprofil, Benutzerlöschung und Mitarbeiterdeaktivierung/-reaktivierung)
2. versionierte Arbeitsmodelle mit individuellen Sollzeiten
3. Feiertagskalender
4. Arbeitszeiterfassung mit mehreren Arbeitsblöcken pro Tag und Kommentarfunktion
5. Arbeitszeitanpassungen durch Mitarbeitende (Start- und Endzeit, Arbeitsort, Kommentar in offenen Perioden) sowie administrative und supervisorische Zeitkorrekturen (Erfassen, Bearbeiten und Löschen von Arbeitszeitbuchungen durch Vorgesetzte für zugeordnete Mitarbeitende und durch HR/Administratoren für alle Mitarbeitenden), inklusive visueller Hervorhebung aller modifizierten und manuell erstellten Buchungen sowie transparenter Ausweisung des Erstellers bei Fremderfassung
6. konfigurierbare und vordefinierte Abwesenheitsarten sowie Erfassung von Abwesenheiten durch Mitarbeitende und im Namen von Mitarbeitenden durch Vorgesetzte und Personaladministration
7. halb- und ganztägige sowie stundenweise Abwesenheiten
8. Ferienkonten mit nachvollziehbaren Kontobuchungen
9. Anwesenheitsstatus
10. Soll-/Ist-Auswertung und Zeitsaldo
11. Monatsabschluss mit Genehmigungsworkflow und detaillierter Inspektionsansicht für Vorgesetzte
12. Rollen und Berechtigungen
13. Audit-Log mit UI-Ansicht zur Einsichtnahme und Filterung von Revisionsereignissen
14. CSV-Export

### 4.2 Erweiterungen des implementierten Grundumfangs

Folgende Erweiterungen gehören zum aktuellen Produktscope und sind als reguläre Anforderungen dieser Spezifikation umzusetzen:

- mehrsprachige Benutzeroberfläche mit initialer Unterstützung für Deutsch und Englisch
- native PDF-Exporte für Monatsreport, Ferienübersicht und Abwesenheitsreport
- globale Unternehmensdarstellung mit Firmenname und optionalem Firmenlogo

### 4.3 Spätere Ausbaustufen

- Projekt-, Kunden-, Auftrags- oder Tätigkeitserfassung
- Zuschläge für Nacht-, Wochenend- oder Feiertagsarbeit
- native Excel-Exporte
- Dokumente zu Abwesenheiten, beispielsweise Arztzeugnisse
- Benachrichtigungen und Erinnerungen
- Kalenderintegration
- Import aus bestehenden Zeiterfassungssystemen
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

Regeln:

- Eine `Employee`-Ressource wird bei Benutzerlöschungen niemals physisch aus dem System gelöscht, um historische Buchungen, Saldi und Berichte konsistent und reproduzierbar zu halten.
- Wird der mit dem Mitarbeiter verknüpfte Strolch-Benutzer gelöscht, wird der `Employee` automatisch auf inaktiv gesetzt (`active = false`).
- Ein inaktiver Mitarbeiter kann später wieder aktiviert werden (`active = true`). Bei der Reaktivierung wird der zugehörige Strolch-Benutzer im System neu angelegt und für die Registrierung/Passwortvergabe freigegeben.
- Mitarbeitende können ihre eigenen Mitarbeiter- und Profilinformationen (u. a. Personalnummer, Eintrittsdatum, Austrittsdatum, Anzeigename, zugeordnetes Team, Standort, Zeitzone sowie aktueller Arbeitsplan und Beschäftigungsgrad) in der Benutzeroberfläche einsehen.

### 6.1.1 User – Strolch-Benutzer (auch für Nicht-Mitarbeiter)

Strolch verwaltet Benutzer und Rollen unabhängig von der fachlichen `Employee`-Ressource.

Regeln:

- Jeder `Employee` referenziert einen Strolch-Benutzer (`personId`), um sich anzumelden und Arbeitszeiten zu erfassen.
- Es können Strolch-Benutzer ohne verknüpftes `Employee`-Profil existieren (z. B. reine Systemadministratoren, HR-Personal ohne Zeiterfassung, reine Vorgesetzte oder externe Revisoren).
- Reine Benutzer besitzen Rollen (z. B. `Admin`, `HR`, `Supervisor`, `Reader`), Benutzername, Name und Status, werden jedoch nicht in der Mitarbeiterübersicht, Zeiterfassung oder Statusanzeige geführt.
- Die Benutzerverwaltung erlaubt die Pflege dieser Benutzer inklusive Rollenzuweisung, Löschung und Passwort-Initialisierung (`SET_PASSWORD`-Challenge).
- **Löschen von Benutzern:**
  - Wird ein reiner Strolch-Benutzer (ohne Mitarbeiterverknüpfung) gelöscht, wird das Benutzerkonto in Strolch entfernt.
  - Wird ein Strolch-Benutzer gelöscht, der mit einem `Employee` verknüpft ist, wird der Strolch-Benutzer entfernt und die zugehörige `Employee`-Ressource auf `active = false` gesetzt. Die `Employee`-Ressource selbst wird nicht gelöscht.
  - Bei einer späteren Reaktivierung des Mitarbeiters wird ein neuer Strolch-Benutzer erstellt und mit dem Mitarbeiter verknüpft.

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
- **Eintritt unter dem Monat:** Tritt ein Mitarbeiter im Laufe eines Monats ein (`entryDate`), gilt für alle Tage vor dem Eintrittsdatum eine tägliche Sollzeit von `0` Minuten. Die Monatssollzeit ergibt sich ausschliesslich aus der Summe der aktiven Tage ab `entryDate` bis Monatsende. Tage vor dem Eintrittsdatum werden im Monatskalender als inaktiv dargestellt und lösen keine Warnungen vor fehlenden Buchungen aus.
- **Austritt unter dem Monat:** Scheidet ein Mitarbeiter im Laufe eines Monats aus (`exitDate`), gilt für alle Tage nach dem Austrittsdatum eine Sollzeit von `0` Minuten.

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
- **Kommentare:** Mitarbeitende können zu jedem `WorkEntry` einen optionalen Kommentar erfassen und bearbeiten (z. B. beim Stoppen des Timers oder bei einer nachträglichen Korrektur).
- **Bearbeitung von Zeitbuchungen durch Mitarbeitende:** Mitarbeitende können ihre eigenen, noch nicht eingereichten oder gesperrten `WorkEntry`-Buchungen in offenen Perioden bearbeiten (Start- und Endzeit anpassen, Arbeitsort ändern, Kommentar anpassen). Buchungen müssen weiterhin am selben Tag starten und enden, dürfen sich nicht mit anderen Buchungen überschneiden und das Ende muss nach dem Start liegen.
- **Administrative und supervisorische Erfassung und Korrekturen:** Vorgesetzte (für ihre zugeordneten Mitarbeitenden/Teams) sowie die Personaladministration und Administratoren (unternehmensweit) können Arbeitszeitbuchungen von Mitarbeitenden in offenen Perioden vollständig anpassen (Start- und Endzeit ändern, Arbeitsort ändern, Kommentar anpassen), neue Zeitbuchungen für beliebige Tage manuell erfassen und fehlerhafte Zeitbuchungen löschen. Änderungen sind ausschliesslich in offenen (noch nicht genehmigten/gesperrten) Perioden zulässig; bei gesperrten Perioden muss die Periode zuerst wiedereröffnet werden. Jede manuelle Erfassung, Anpassung oder Löschung wird revisionssicher mit Vorher-/Nachher-Zustand, Begründung und ausführendem Benutzer im Audit-Log protokolliert.
- **Visuelle Hervorhebung und Ausweisung des Erstellers:**
  - Alle modifizierten (nachträglich bearbeiteten) sowie manuell erstellten Arbeitszeitbuchungen (`source = MANUAL` oder modifizierter Status) werden in der Benutzeroberfläche (z. B. Tages-, Wochen-, Monatsansicht, Genehmigungsansicht) und in Berichten/Exporten visuell hervorgehoben (z. B. durch optische Kennzeichnung/Badge/Hervorhebung).
  - Wurde eine Arbeitszeitbuchung nicht durch den Mitarbeiter selbst erstellt (z. B. durch Vorgesetzte, HR oder Administrator manuell erfasst), wird beim Eintrag transparent und gut sichtbar ausgewiesen, von wem (`createdBy`) der Eintrag erstellt wurde.
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

#### 6.5.1 Standardmässig vorkonfigurierte Abwesenheitsarten

Chronivaro liefert initial folgende Standard-Abwesenheitsarten aus:

| Code | Name | Sollzeit-Gutschrift | Ferienabzug | Bezahlt | Genehmigungspflichtig | Kommentarpflichtig | Erlaubte Dauern | Sichtbar Status |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
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

Lokalisierungsregel:

- `code` bleibt sprachunabhängig und ist der stabile technische Bezeichner.
- Der bestehende fachlich konfigurierte `name` wird vorerst nicht übersetzt.
- Statische Enum-Werte und andere feste Systembegriffe werden über i18n-Schlüssel lokalisiert.
- Benutzererfasste Werte, insbesondere Kommentare, Namen, Teambezeichnungen und Standortbezeichnungen, werden nicht automatisch übersetzt.

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
| `createdBy` | Ersteller (Mitarbeiter selbst oder bei Fremderfassung Vorgesetzter / HR / Administrator) |
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
- Entwurfsstatus (`DRAFT`): Abwesenheiten können als Entwurf gespeichert werden. Ein Entwurf kann durch den Mitarbeiter vor dem Einreichen beliebig bearbeitet (Attribute wie Datum, Dauer, Kommentar anpassen) oder verworfen/storniert werden (`DRAFT` -> `CANCELLED`).
- Das Stornieren eines Entwurfs erzeugt keine Ferienkontobuchung (weder Bezug noch Rückvergütung), da Entwürfe noch keine Ferienminuten abgezogen haben.
- Durch das Einreichen (`DRAFT` -> `SUBMITTED`) wird der Antrag finalisiert und für Vorgesetzte zur Genehmigung freigegeben.
- **Erfassung im Namen von Mitarbeitenden (Fremderfassung):** Vorgesetzte (für ihre zugeordneten Mitarbeitenden) sowie Personaladministration und Administratoren (unternehmensweit für alle Mitarbeitenden) können Abwesenheiten direkt im Namen von Mitarbeitenden erfassen. Solche Abwesenheiten können wahlweise direkt als genehmigt (`APPROVED`) angelegt werden oder den regulären Genehmigungsworkflow durchlaufen. Der Ersteller (`createdBy`) wird transparent gespeichert und ausgewiesen. Bei direkt genehmigten Ferienabwesenheiten wird sofort die entsprechende Buchung im Ferienkonto ausgelöst.

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

## 6.7.1 Automatisierte Ferienanspruchsregelung

Die automatisierte Anspruchsberechnung verwendet die folgenden Regelungen:

- Der standardmässige jährliche Ferienanspruch für einen vollzeitbeschäftigten Mitarbeiter beträgt `25` Ferientage pro Anspruchsjahr.
- Das Anspruchsjahr entspricht dem Kalenderjahr, und der jährliche Ferienanspruch wird für bestehende Mitarbeitende zu Beginn dieses Jahres (per `1. Januar`) als `ENTITLEMENT`-Journaleintrag gutgeschrieben.
- Tritt ein Mitarbeiter im Laufe des Anspruchsjahres ein (`entryDate`), wird der anteilige Ferienanspruch ab dem Eintrittsdatum bis zum Jahresende berechnet und per `entryDate` als `ENTITLEMENT`-Journaleintrag gebucht.
- Ein Ferientag entspricht `480` Minuten. Dieser Wert ist global konfigurierbar und hat standardmässig den Wert `480` Minuten. Er wird nicht aus der individuellen täglichen Sollarbeitszeit oder dem Beschäftigungsgrad eines Mitarbeiters abgeleitet.
- Der Ferienanspruch bei Teilzeitbeschäftigung wird anteilsmässig anhand des Beschäftigungsgrads berechnet.
- Beginnt oder endet ein Arbeitsverhältnis während des Anspruchsjahres, wird der Ferienanspruch anteilsmässig für den aktiven Zeitraum des Jahres berechnet.
- Anteilig berechnete Ferienansprüche werden mit voller Genauigkeit berechnet, in Minuten umgerechnet und anschliessend kaufmännisch auf die nächste ganze Minute gerundet.
- Ändert sich der Beschäftigungsgrad während des Jahres oder wird ein Austrittsdatum (`exitDate`) erfasst bzw. geändert, erfolgt eine automatisierte Neuberechnung des anteiligen Jahresanspruchs mit entsprechender `CORRECTION`-Gegenbuchung im Journal.
- Es gelten keine altersabhängigen Ferienanspruchsregeln.
- Es gelten keine dienstaltersabhängigen Ferienanspruchsregeln.
- Nicht bezogene Ferien werden beim Übergang ins nächste Kalenderjahr ohne Begrenzung übertragen.
- Positive `CORRECTION`-Guthaben werden in den übertragbaren Ferienbestand einbezogen.
- Beim Ferienbezug wird zuerst das älteste verfügbare Ferienguthaben verwendet.
- Übertragene Ferien verfallen nicht.
- Nur der konfigurierte Standard-Abwesenheitstyp für Ferien erzeugt `USAGE`-Einträge. Der technische Bezeichner dieses Typs lautet `VACATION`.
- Journaleinträge sind unveränderlich. Korrekturen und Stornierungen werden durch separat auditierte `CORRECTION`-Einträge dargestellt.
- Die Genehmigung von Ferien wird blockiert, wenn der beantragte Bezug das verfügbare Guthaben überschreitet. Ein negatives Guthaben ist nicht zulässig.
- Sämtliche Regelwerte, einschliesslich der Umrechnung von Ferientagen in Minuten, des jährlichen Anspruchs, der anteilsmässigen Berechnung, der Rundungsregel, der Übertragung, des Verfalls und der Konfiguration des Abwesenheitstyps, müssen konfigurierbar und dürfen nicht hart codiert sein.

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

Regeln:

- Revisionsrelevante Vorgänge (z. B. Anlage, Änderung oder Löschung von Benutzern, Deaktivierung und Reaktivierung von Mitarbeitern, Anpassung von Buchungen, Genehmigungen, Periodensperrungen) werden als unveränderliche `AuditEvent`-Einträge festgehalten.
- Audit-Ereignisse können über Filterkriterien (Zeitraum, Entitätstyp, Entitäts-ID, Benutzer, Aktion) abgefragt und in einer dedizierten Benutzeroberfläche eingesehen werden.

### 6.11 Globale Anwendungskonfiguration

Zusätzlich zu den bereits bestehenden globalen Einstellungen unterstützt Chronivaro mindestens folgende produktweite Darstellungs- und Lokalisierungswerte:

| Attribut | Beschreibung |
| --- | --- |
| `defaultLanguage` | Standardsprache der Anwendung; initial `de` oder `en` |
| `companyName` | global angezeigter Firmenname |
| `companyLogo` | optionales globales Firmenlogo (unterstützt Bild-Upload und Speicherung/Auslieferung) |

Regeln:

- Die initial unterstützten Sprachen sind Deutsch (`de`) und Englisch (`en`).
- Weitere Sprachen müssen ohne Änderung der fachlichen Kernlogik ergänzbar sein.
- `companyName` und `companyLogo` gelten global und sind nicht benutzer-, team- oder reportspezifisch.
- In der Systemkonfiguration der Administration kann eine Bilddatei für das Firmenlogo hochgeladen werden.
- Ist ein Firmenlogo konfiguriert, wird es sowohl in der Anwendung als auch in unterstützten PDF-Reports angezeigt.
- Ist kein Firmenlogo konfiguriert, dürfen UI und PDF-Ausgabe keine leeren oder fehlerhaften Platzhalter anzeigen.

## 7. Berechnungsregeln

### 7.1 Sollzeit eines Tages

```text
Sollzeit = Arbeitsplan-Minuten des Wochentags
```

Anpassungen:

- vor Eintritt (`entryDate`) oder nach Austritt (`exitDate`): `0`
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

1. Benutzer öffnet einen Tag oder die Zeiterfassungsansicht eines Mitarbeiters.
2. Mitarbeitende können bestehende eigene Zeitbuchungen in offenen Perioden bearbeiten (Start- und Endzeit anpassen, Arbeitsort und Kommentar ändern).
3. Vorgesetzte (für zugeordnete Mitarbeitende und Teams) sowie Personaladministration und Administratoren (unternehmensweit für alle Mitarbeitenden) können mit entsprechender Berechtigung Zeitbuchungen von Mitarbeitenden vollständig bearbeiten (Start- und Endzeiten anpassen, Arbeitsort und Kommentar ändern), neue Buchungen manuell hinzufügen oder bestehende Buchungen löschen.
4. Das System validiert Reihenfolge, Überlappungen und den Periodenstatus (nur in offenen Perioden möglich).
5. Bei einer bereits genehmigten oder gesperrten Periode ist jede Änderung nur nach vorheriger Wiederöffnung durch berechtigte Rollen möglich.
6. Alle modifizierten sowie manuell erstellten Zeitbuchungen werden in der Benutzeroberfläche und in Auswertungen optisch hervorgehoben. Wurde ein Eintrag nicht durch den Mitarbeiter selbst erstellt, wird der Ersteller (`createdBy`) transparent ausgewiesen.
7. Jede Änderung wird im Audit-Log revisionssicher mit Vorher-/Nachher-Werten protokolliert.

### 9.4 Abwesenheitsantrag

1. Mitarbeiter erfasst Art, Zeitraum und Dauer und kann den Antrag direkt einreichen oder als Entwurf (`DRAFT`) speichern.
2. System berechnet die betroffenen Sollminuten als Vorschau.
3. Gespeicherte Entwürfe können in der Abwesenheitsübersicht eingesehen, nachträglich bearbeitet (z. B. Korrektur von Tippfehlern, Datums- oder Daueranpassung) oder verworfen/storniert (`DRAFT` -> `CANCELLED`) werden. Das Verwerfen eines Entwurfs löst keine Ferienkontobuchung aus.
4. Der Mitarbeiter reicht den fertigen Antrag bzw. Entwurf ein (`DRAFT` -> `SUBMITTED`).
5. Vorgesetzter genehmigt oder lehnt mit Kommentar ab.
6. Bei Ferien wird nach Genehmigung eine Ferienkontobuchung erstellt.
7. Stornierung oder Änderung einer genehmigten Abwesenheit erzeugt eine entsprechende Gegenbuchung; bestehende Kontobuchungen werden nicht still überschrieben.
8. **Erfassung durch Vorgesetzte und HR:** Vorgesetzte (für zugeordnete Mitarbeitende) sowie Personaladministration und Administratoren (unternehmensweit) können Abwesenheiten im Namen von Mitarbeitenden direkt erfassen. Diese können je nach Berechtigung und Anwendungsfall direkt im Status `APPROVED` oder als `SUBMITTED` erstellt werden. Der ausführende Benutzer wird als Ersteller (`createdBy`) transparent protokolliert. Bei genehmigten Ferienabwesenheiten wird direkt die entsprechende Abbuchung auf dem Ferienkonto des Mitarbeiters ausgelöst.

### 9.5 Monatsabschluss

1. Mitarbeiter prüft die Monatsübersicht.
2. System zeigt Fehler und Warnungen.
3. Mitarbeiter reicht die Periode ein.
4. Vorgesetzter öffnet die eingereichte Periode in der Genehmigungsansicht.
5. In der Genehmigungsansicht kann der Vorgesetzte den vollständigen Monatsreport (Tagesaufstellung, Soll-/Istzeiten, Arbeitsblöcke, Unterbrüche, Abwesenheiten, Saldi und Kommentare) in einer detaillierten Inspektionsansicht einsehen.
6. Der Vorgesetzte kann die Periode direkt aus der Detailansicht oder der Übersichtstabelle genehmigen oder mit Begründung ablehnen.
7. Nach Genehmigung wird die Periode gesperrt.
8. Eine Wiederöffnung erfordert Berechtigung und Begründung.

### 9.6 Mitarbeiter-Registrierung

Um neuen Mitarbeitern den Zugriff auf Chronivaro zu ermöglichen, wird ein administrativer Registrierungsprozess bereitgestellt.

1. Ein Administrator wählt in der Mitarbeiterliste die Aktion "Registrieren" für einen bestimmten Mitarbeiter aus.
2. Das System identifiziert den verknüpften Strolch-Benutzer anhand der `userId` und `username`.
3. Das System löst eine Strolch-Challenge (`Usage.SET_PASSWORD`) für diesen Benutzer aus.
4. Die Challenge wird über den konfigurierten `UserChallengeHandler` (z. B. Konsole oder E-Mail) an den Mitarbeiter übermittelt.
5. Der Mitarbeiter verwendet den erhaltenen Link/Code, um sein initiales Passwort festzulegen und sich anschliessend anzumelden.

Dieser Prozess nutzt den Standard-Strolch-Mechanismus zur Passwortinitialisierung und stellt sicher, dass keine Passwörter manuell durch Administratoren vergeben oder per Klartext-E-Mail versendet werden müssen.

### 9.7 Benutzerverwaltung für Nicht-Mitarbeiter

Für Personen, die das System administrieren, überwachen oder leiten, ohne selbst als Mitarbeiter Arbeitszeiten zu erfassen (z. B. Systemadministratoren, HR-Personal, reine Vorgesetzte oder externe Revisoren):

1. Ein Administrator legt in der Benutzerverwaltung einen neuen Strolch-Benutzer mit Benutzername, Name und Rollenzuweisung an.
2. Das System erstellt den Benutzer ohne verknüpfte `Employee`-Ressource.
3. Der Administrator löst die Registrierung / Passwort-Challenge (`Usage.SET_PASSWORD`) aus.
4. Der Benutzer erhält den Aktivierungslink, setzt sein Passwort und kann sich mit seinen zugewiesenen Rollen am System anmelden.

### 9.8 Löschen von Benutzern und Mitarbeiterdeaktivierung

1. Ein Administrator wählt in der Benutzerverwaltung oder Mitarbeiterübersicht die Löschung eines Benutzers aus.
2. Handelt es sich um einen reinen Strolch-Benutzer (ohne Mitarbeiterverknüpfung):
   - Der Benutzer wird aus dem System gelöscht.
3. Handelt es sich um einen Strolch-Benutzer, der mit einer `Employee`-Ressource verknüpft ist:
   - Die `Employee`-Ressource wird **nicht** gelöscht, um historische Buchungen, Saldi und Auswertungen unverändert und reproduzierbar zu erhalten.
   - Der `Employee` wird auf inaktiv gesetzt (`active = false`).
   - Der Strolch-Benutzer wird aus dem System gelöscht, sodass keine Anmeldung mehr möglich ist.
4. Der Vorgang wird im Audit-Log revisionssicher protokolliert.

### 9.9 Reaktivierung von Mitarbeitern

1. Ein Administrator öffnet einen inaktiven Mitarbeiter in der Mitarbeiterverwaltung und wählt die Aktion "Reaktivieren".
2. Der Aktivstatus des Mitarbeiters wird wieder auf `active = true` gesetzt.
3. Das System prüft und initialisiert das Ferienkonto und den Ferienanspruch für das laufende Anspruchsjahr (pro-rata ab Reaktivierungsdatum bzw. gemäss gültigem Arbeitsplan), sofern für den Zeitraum noch keine gültige Anspruchsbuchung existiert.
4. Das System erstellt automatisch einen neuen Strolch-Benutzer mit dem konfigurierten Benutzernamen und den erforderlichen Rollen.
5. Der Administrator löst anschliessend die Registrierung / Passwort-Challenge (`Usage.SET_PASSWORD`) aus, damit der Mitarbeiter sein Passwort festlegen und sich wieder anmelden kann.
6. Der Vorgang wird im Audit-Log revisionssicher protokolliert.

### 9.10 Einsichtnahme in das Audit-Log

1. Ein berechtigter Benutzer (z. B. Administrator oder Revisor) öffnet die Audit-Log-Ansicht in der Benutzeroberfläche.
2. Die Ansicht erlaubt das Filtern nach Datumsbereich (`from`, `to`), Entitätstyp (`entityType`), Entitäts-ID (`entityId`), ausführendem Benutzer (`username`) und Aktion (`action`).
3. Die Treffer werden in einer übersichtlichen, paginierten Tabelle mit Zeitstempel, Benutzer, Aktion, betroffener Entität und Zusammenfassung dargestellt.
4. Per Klick auf einen Eintrag können die vollständigen Detaildaten (Vorher-/Nachher-Werte, Begründung, Korrelations-ID) in einer Detailansicht eingesehen werden.

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
- visuelle Kennzeichnung/Hervorhebung modifizierter und manuell erstellter Zeitbuchungen
- Ausweisung des Erstellers (`createdBy`), falls die Buchung nicht durch den Mitarbeiter selbst erstellt wurde

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
- visuelle Kennzeichnung/Hervorhebung modifizierter und manuell erstellter Zeitbuchungen
- Ausweisung des Erstellers (`createdBy`), falls die Buchung nicht durch den Mitarbeiter selbst erstellt wurde

### 11.3 Ferienübersicht

- Mitarbeiteridentifikation über Anzeigename und Personalnummer (beziehungsweise Benutzername, statt roher interner Mitarbeiter-ID)
- Jahresanspruch
- Übertrag aus Vorjahr
- Korrekturen
- bezogene Ferien
- genehmigte zukünftige Ferien
- noch verfügbare Ferien
- tabellarische Kontobuchungen mit sauber lokalisiertem Buchungstyp (z. B. Anspruch, Übertrag, Bezug, Korrektur, Verfall) und definierten Werten (keine `undefined`-Minutenwerte)

### 11.4 Teamreport

- Nur sichtbar und aufrufbar für Rollen mit Berechtigung (Vorgesetzter, HR, Administrator); nicht verfügbar für reine Mitarbeiter-Rollen
- Auswahl des auszuwertenden Teams über eine Dropdown-Auswahl (keine manuelle Eingabe einer Team-ID)
- Datumsauswahl für den Berichtsmonat über Datumswähler/Monatsauswahl
- Soll-/Istzeit pro Mitarbeiter
- Saldo pro Mitarbeiter
- fehlende Buchungen
- Arbeitsblöcke und Unterbrüche zur manuellen Prüfung der gesetzlichen Pausen
- offene Genehmigungen
- Abwesenheiten nach Typ, abhängig von der Berechtigung
- visuelle Kennzeichnung/Hervorhebung modifizierter und manuell erstellter Zeitbuchungen
- Ausweisung des Erstellers (`createdBy`), falls die Buchung nicht durch den Mitarbeiter selbst erstellt wurde

### 11.5 Abwesenheitsreport

Der Abwesenheitsreport stellt Abwesenheiten für den gewählten Zeitraum und den berechtigten Mitarbeiter- beziehungsweise Teamkontext dar. Sichtbarkeit und Detailgrad folgen denselben Berechtigungs- und Datenschutzregeln wie die entsprechende Bildschirmansicht. Freitext-Kommentare werden standardmässig nicht in den Report übernommen.

### 11.6 Export

Chronivaro unterstützt CSV sowie native PDF-Ausgabe. Datums-, Zeit- und Zahlenformate sind eindeutig dokumentiert. Sämtliche Exporte berücksichtigen dieselben Berechtigungen, Filter und fachlichen Berechnungen wie die entsprechende Bildschirmansicht.

#### 11.6.1 CSV

CSV wird mit UTF-8 und optionalem BOM für Excel-Kompatibilität erzeugt.

#### 11.6.2 Native PDF-Ausgabe

Native PDF-Ausgabe wird initial für folgende Reports unterstützt:

- Monatsreport
- Ferienübersicht
- Abwesenheitsreport

Für PDF-Ausgaben gelten folgende Regeln:

- PDFs werden bei Bedarf serverseitig erzeugt und direkt an den Client ausgeliefert; erzeugte PDF-Dateien werden nicht dauerhaft in Chronivaro gespeichert.
- PDF, CSV und Bildschirmansicht verwenden dieselbe fachliche Report-Datenbasis. Berechnungen, Filter und Berechtigungen dürfen zwischen den Ausgabeformaten nicht voneinander abweichen.
- Für genehmigte beziehungsweise gesperrte Perioden wird, soweit vorhanden, der unveränderliche `calculationSnapshot` als Grundlage verwendet.
- Die aktuelle UI-Sprache bestimmt die Sprache von Reporttitel, Überschriften, statischen Bezeichnungen und sonstigen systemgenerierten Texten.
- Datumswerte werden im Format `yyyy-MM-dd` dargestellt.
- Zeitdauern werden als `HH:mm` dargestellt.
- PDF-Seiten verwenden A4. Portrait ist der Standard; bei breiten Reportlayouts darf Landscape verwendet werden.
- Verwendete Schriftarten werden in die PDF-Datei eingebettet.
- PDF/A-Konformität ist nicht erforderlich.
- Digitale Signaturen sind nicht Bestandteil dieser Ausbaustufe. Chronivaro bleibt die autoritative Quelle für Genehmigungs- und Periodenstatus.
- PDF-Exporte selbst erzeugen keinen fachlichen Audit-Event.
- Freitext-Kommentare werden standardmässig nicht exportiert.
- Sensible Abwesenheitsinformationen werden nur ausgegeben, wenn der exportierende Benutzer für dieselben Informationen auch in der Anwendung berechtigt ist.
- Negative Saldi werden visuell hervorgehoben. Die negative Bedeutung muss auch bei monochromem Ausdruck eindeutig erkennbar bleiben und darf nicht ausschliesslich durch Farbe vermittelt werden.
- Modifizierte und manuell erstellte Zeitbuchungen werden auch in der PDF-Ausgabe visuell gekennzeichnet; bei Fremderfassung wird der Ersteller ausgewiesen.
- Dateinamen werden deterministisch aus Reporttyp, relevantem Mitarbeiter- beziehungsweise Kontextbezug und Berichtszeitraum gebildet.

Jeder unterstützte PDF-Report enthält einen konsistenten Kopf- beziehungsweise Fussbereich mit mindestens:

- Firmenname
- optionalem konfiguriertem Firmenlogo
- Reporttitel
- Berichtszeitraum
- Erstellungszeitpunkt
- Seitennummerierung

Persönliche Reports enthalten zusätzlich:

- Anzeigename des Mitarbeiters
- Personalnummer
- Team
- Standort

Genehmigte Monatsreports enthalten zusätzlich:

- Genehmigungsstatus
- Genehmigungsdatum
- Genehmiger

## 12. UI-Anforderungen

Die UI wird mit HTML, CSS und Vanilla JavaScript umgesetzt. Es wird kein Frontend-Framework verwendet.

### 12.1 Seiten

0. **Header & Navigation / Benutzer-Menü & Mein Profil**
   - Anzeige des angemeldeten Benutzers und seiner Rolle
   - Dropdown-Menü und Profilansicht für Benutzer- und Mitarbeiterinformationen: Einsicht in die eigenen Stammdaten wie Anzeigename, Benutzername, Personalnummer, Eintrittsdatum, Beschäftigungsgrad, Arbeitsplan/Sollzeiten, Team, Standort, Zeitzone sowie Spracheinstellungen
   - Platzierung des Abmelde-Buttons ("Logout") innerhalb des Benutzer-Dropdown-Menüs (nicht als freistehender Button in der Hauptnavigation)

1. **Dashboard**
   - heutige Soll- und Istzeit
   - aktueller Status
   - Start/Stoppen (mit optionalem Kommentar)
   - aktueller Zeit- und Feriensaldo
   - offene Warnungen
2. **Meine Zeiten & Mitarbeiter-Zeiterfassung**
   - Tages-, Wochen- und Monatsansicht
   - Zeitblöcke einsehen, bearbeiten (Start- und Endzeit, Arbeitsort, Kommentar in offenen Perioden)
   - für Vorgesetzte (teambezogen) und HR/Administratoren (unternehmensweit): Mitarbeiter-/Team-Auswahl zur Einsicht der Arbeitszeiten von Mitarbeitenden, manuelles Hinzufügen neuer Arbeitszeitbuchungen, vollständiges Bearbeiten (Start, Ende, Arbeitsort, Kommentar) und Löschen von Arbeitszeitbuchungen mit Validierungsprüfungen und Bestätigungsdialogen
   - visuelle Kennzeichnung und Hervorhebung aller modifizierten sowie manuell erstellten Arbeitszeitbuchungen (z. B. optische Kennzeichnung/Badges)
   - transparente Anzeige des Erstellers (`createdBy`), falls ein Eintrag nicht vom Mitarbeiter selbst erstellt wurde
   - Tagessummen und Saldi
3. **Abwesenheiten**
   - Antrag erfassen (als Entwurf speichern oder direkt einreichen)
   - für Vorgesetzte (für zugeordnete Mitarbeitende/Teams) und HR/Administration (unternehmensweit): Mitarbeiter-Auswahl zur Erfassung von Abwesenheiten im Namen von Mitarbeitenden
   - eigene Anträge und Status-Historie (inklusive transparenter Ausweisung des Erstellers bei Fremderfassung)
   - Aktionen für Entwürfe: Einreichen (`Submit`), Bearbeiten im Modal (`Edit`) und Verwerfen/Stornieren (`Cancel`)
   - hohe visuelle Kontraste für alle Aktionsschaltflächen gemäss Barrierefreiheitsanforderungen (WCAG AA), sodass Text- und Hintergrundfarben klar unterscheidbar sind
   - Kalenderdarstellung
4. **Ferien**
   - Kontoübersicht (Anzeige von Name und Personalnummer/Benutzername des Mitarbeiters statt roher interner Mitarbeiter-ID)
   - geplante und bezogene Ferien mit korrekter Initialisierung (keine `undefined`-Minutenangaben bei reaktivierten oder bestehenden Mitarbeitern)
   - Kontobuchungen mit sauber formatierten und lokalisierten Buchungstypen
5. **Status**
   - Filter nach Team und Standort
   - farblicher Status
   - datenschutzkonforme Anzeige
6. **Genehmigungen**
   - offene Abwesenheiten
   - eingereichte Monatsperioden mit Detailprüfung (Öffnen des vollständigen Monatsreports in einer Inspektionsansicht mit direkter Genehmigungs-/Ablehnungsaktion)
7. **Reports & Export**
   - Zeitraum über Datumswähler / Monatsauswahl wählen (keine manuelle Texteingabe von Datumswerten)
   - Mitarbeiter-Auswahl für berechtigte Rollen zweistufig (zuerst Team-Auswahl, danach Mitarbeiter-Auswahl per Dropdown; keine manuelle Eingabe einer Mitarbeiter-ID)
   - Monatsreport: Datumsauswahl per Datumswähler/Monatsauswahl
   - Team-Monatsübersicht: Nur für Rollen mit entsprechender Berechtigung (Supervisor, HR, Admin) sichtbar; Team-Auswahl per Dropdown und Datumsauswahl per Datumswähler
   - Soll-/Ist-Vergleich
   - CSV-Export
   - PDF-Export für Monatsreport, Ferienübersicht und Abwesenheitsreport
8. **Administration**
   - Benutzerverwaltung (für reine Systembenutzer sowie Mitarbeiterbenutzer, Rollenzuweisung, Benutzerlöschung und Passwort-Challenge)
   - Mitarbeiter und Teams (inkl. Deaktivieren bei Benutzerlöschung und Reaktivieren mit automatischer Neuerstellung des Strolch-Benutzers sowie Ferieninitialisierung)
   - Registrierungsprozess auslösen
   - Arbeitspläne
   - Standorte und Feiertage
   - Abwesenheitsarten
   - globale Einstellungen: zentrierter Einstellungsbereich/Container mit Beschreibungstext unterhalb des Titels; Unterstützung für das Hochladen einer Bilddatei für das Firmenlogo
   - Audit-Log-Ansicht zur filterbaren und detaillierten Einsicht aller protokollierten Systemereignisse (Filter nach Zeitraum, Entität, Benutzer und Aktion)

### 12.2 UI-Grundsätze

- responsive Bedienung auf Desktop und Smartphone
- vollständige Tastaturbedienbarkeit
- ausreichender Farbkontrast
- Status nicht ausschliesslich durch Farbe vermitteln
- verständliche Fehlermeldungen direkt am betroffenen Feld
- Bestätigung vor fachlich weitreichenden Aktionen
- Datumsdarstellung in der UI vorerst einheitlich im Format `yyyy-MM-dd`; Übertragung in eindeutigem ISO-Format
- Lade-, Leer- und Fehlerzustände für jede asynchrone Ansicht

### 12.3 Mehrsprachigkeit und Sprachwahl

Die Chronivaro-Benutzeroberfläche unterstützt initial Deutsch und Englisch. Die technische Internationalisierung muss so aufgebaut sein, dass zusätzliche Sprachen durch Ergänzen von Übersetzungsressourcen und Konfiguration hinzugefügt werden können, ohne die fachliche Kernlogik zu ändern.

Zu übersetzen sind mindestens:

- statische UI-Bezeichnungen
- Navigation
- Buttons und Aktionen
- Validierungs- und Fehlermeldungen
- menschenlesbare REST-Fehlermeldungen
- E-Mails und zukünftige Benachrichtigungen
- Reportüberschriften
- PDF- und zukünftige Excel-Exporte
- statische Enum-Bezeichnungen und andere feste Systemwerte

Nicht automatisch übersetzt werden:

- benutzererfasste Kommentare und Freitexte
- Mitarbeiter- und Personennamen
- Teamnamen
- Standortnamen
- konfigurierte Abwesenheitsnamen
- konfigurierte Feiertagsnamen

#### 12.3.1 Sprachwahl vor dem Login

Der Login-Bildschirm stellt eine explizite Sprachauswahl bereit. Vor dem Login wird die effektive Sprache in folgender Priorität bestimmt:

1. explizit auf dem aktuellen Login-Bildschirm gewählte Sprache
2. gültiger Wert im Browser Storage
3. global konfigurierte Standardsprache

Eine explizite Auswahl auf dem Login-Bildschirm hat immer Vorrang. Nach erfolgreichem Login wird diese Auswahl sowohl im Browser Storage als auch im Sprachattribut des Strolch-Benutzers gespeichert und wird damit zur neuen persistenten Benutzersprache.

#### 12.3.2 Sprachwahl nach dem Login

Nach erfolgreichem Login wird die effektive Sprache in folgender Priorität bestimmt:

1. explizit auf dem Login-Bildschirm gewählte Sprache für den aktuellen Login-Vorgang
2. gültiger Wert im Browser Storage
3. im Strolch-Benutzer gespeicherte Sprache
4. global konfigurierte Standardsprache

Der angemeldete Benutzer kann die Sprache über eine Menüeinstellung ändern. Eine solche Änderung wird unmittelbar angewendet und sowohl im Browser Storage als auch auf dem Strolch-Benutzer gespeichert. Dadurch bleibt die Sprache sitzungs- und geräteübergreifend erhalten, sofern auf einem konkreten Browser kein abweichender gültiger Browser-Storage-Wert gesetzt ist.

#### 12.3.3 Übersetzungsschlüssel und Fallback

- Übersetzbare Systemtexte werden über stabile i18n-Schlüssel referenziert.
- Fachliche Codes und Enum-Werte bleiben sprachunabhängig.
- Die Auflösung unterstützt Sprach-Tags und verwendet folgende Fallback-Kette:

```text
angeforderter Sprach-Tag → Basissprache → konfigurierte Standardsprache → Übersetzungsschlüssel
```

Beispiel:

```text
de-CH → de → konfigurierte Standardsprache → Übersetzungsschlüssel
```

- URLs bleiben sprachneutral und enthalten keinen Sprachpräfix.
- Sprache und Locale werden in dieser Ausbaustufe nicht als getrennte fachliche Konzepte modelliert.
- Die Datumsdarstellung bleibt unabhängig von der Sprache vorerst `yyyy-MM-dd`.

#### 12.3.4 Übersetzungsqualität

- Deutsch und Englisch sind verpflichtend vollständig unterstützte Sprachen.
- Jeder verpflichtende Übersetzungsschlüssel muss in beiden Sprachen vorhanden sein.
- Fehlende Schlüssel in Deutsch oder Englisch, doppelte Schlüssel sowie syntaktisch ungültige Übersetzungsressourcen lassen die automatisierten Übersetzungstests und damit den Maven-Build fehlschlagen.
- Weitere Sprachen dürfen als unvollständig gekennzeichnet werden und die definierte Fallback-Logik verwenden, ohne den Build allein aufgrund fehlender optionaler Übersetzungen fehlschlagen zu lassen.

### 12.4 JavaScript-Struktur

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
    ├── i18n/
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
- standardisierte Fehlerantworten mit sprachunabhängigem `errorCode` und lokalisierter menschenlesbarer `message`
- der Webclient übermittelt die aktuell ausgewählte Sprache über den Standard-HTTP-Header `Accept-Language`; serverseitig erzeugte menschenlesbare Texte verwenden dieselbe Fallback-Logik wie die UI
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

#### Arbeitszeiten und Buchungen

```text
GET    /me/work-entries?from={date}&to={date}
POST   /me/work-entries
PUT    /me/work-entries/{id}
DELETE /me/work-entries/{id}
GET    /employees/{id}/work-entries?from={date}&to={date}
POST   /employees/{id}/work-entries
PUT    /admin/work-entries/{id}
DELETE /admin/work-entries/{id}
POST   /me/timer/start
POST   /me/timer/stop
GET    /me/day-summary/{date}
GET    /me/month-summary/{yearMonth}
GET    /me/profile
```

- `GET /me/profile`: Abrufen der eigenen Benutzer- und Mitarbeiterstammdaten (Anzeigename, Benutzername, Personalnummer, Eintrittsdatum, Beschäftigungsgrad, Arbeitsplan, Team, Standort, Zeitzone).
- `PUT /me/work-entries/{id}`: Aktualisieren einer offenen/nicht eingereichten Buchung durch den Mitarbeiter (Bearbeiten von Start- und Endzeit, Arbeitsort und Kommentar in offenen Perioden).
- `GET /me/work-entries?from={date}&to={date}`: Abrufen der eigenen Arbeitszeitbuchungen (inklusive `source`, `createdBy` und Modifikationsinformationen zur visuellen Hervorhebung und Erstelleranzeige).
- `GET /employees/{id}/work-entries?from={date}&to={date}`: Abrufen aller Arbeitszeitbuchungen eines Mitarbeiters im angegebenen Zeitraum (zulässig für den Mitarbeiter selbst, den zuständigen Vorgesetzten sowie HR/Admin; inklusive `source`, `createdBy` und Modifikationsinformationen).
- `POST /employees/{id}/work-entries`: Manuelles Erfassen einer Arbeitszeitbuchung für einen Mitarbeiter durch zuständige Vorgesetzte oder HR/Admin.
- `PUT /admin/work-entries/{id}`: Vollständige administrative und supervisorische Korrektur einer Zeitbuchung (Start, Ende, Arbeitsort, Kommentar) durch zuständige Vorgesetzte oder HR/Admin.
- `DELETE /admin/work-entries/{id}`: Löschen einer Zeitbuchung durch zuständige Vorgesetzte oder HR/Admin.

#### Abwesenheiten

```text
GET    /me/absences
POST   /me/absences
PUT    /me/absences/{id}
POST   /me/absences/{id}/submit
POST   /me/absences/{id}/cancel
GET    /employees/{id}/absences
POST   /employees/{id}/absences
GET    /absence-types
GET    /approvals/absences
POST   /approvals/absences/{id}/approve
POST   /approvals/absences/{id}/reject
```

- `PUT /me/absences/{id}`: Aktualisieren eines Abwesenheitsentwurfs (`DRAFT`) vor der Einreichung (z. B. Korrektur von Datumsbereich, Dauer oder Kommentar).
- `POST /me/absences/{id}/submit`: Einreichen eines Abwesenheitsentwurfs (`DRAFT` -> `SUBMITTED`).
- `POST /me/absences/{id}/cancel`: Stornieren einer Abwesenheit (`DRAFT`, `SUBMITTED` oder `APPROVED` -> `CANCELLED`). Bei Stornierung eines Entwurfs (`DRAFT`) werden keine Gegenbuchungen auf dem Ferienkonto vorgenommen.
- `GET /employees/{id}/absences`: Abrufen der Abwesenheiten eines Mitarbeiters (zulässig für den Mitarbeiter selbst, zuständige Vorgesetzte sowie HR/Admin).
- `POST /employees/{id}/absences`: Erfassen einer Abwesenheit für einen Mitarbeiter durch zuständige Vorgesetzte oder HR/Admin (im Namen des Mitarbeiters).

#### Ferien

```text
GET    /me/vacation-account?year={year}
GET    /me/vacation-account.pdf?year={year}
GET    /employees/{id}/vacation-account?year={year}
GET    /employees/{id}/vacation-account.pdf?year={year}
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
GET    /approvals/periods/{id}
POST   /approvals/periods/{id}/approve
POST   /approvals/periods/{id}/reject
POST   /periods/{id}/reopen
GET    /reports/time-balance
GET    /reports/time-balance.csv
GET    /reports/time-balance.pdf
GET    /reports/absences
GET    /reports/absences.pdf
```

- `GET /approvals/periods/{id}`: Vollständige Monatsreport-Detailansicht der eingereichten Periode (Tage, Buchungen, Unterbrüche, Abwesenheiten, Saldi) für Genehmiger vor der Entscheidung.

#### Administration

```text
GET/POST/PUT        /users
DELETE              /users/{id}
POST                /users/{id}/register
GET/POST/PUT        /employees
POST                /employees/{id}/register
POST                /employees/{id}/reactivate
GET/POST/PUT        /teams
GET/POST/PUT        /locations
GET/POST/PUT        /holiday-calendars
GET/POST/PUT        /absence-types
GET/POST/PUT        /employees/{id}/schedule-versions
GET/POST/PUT        /configuration
GET                 /audits?from={date}&to={date}&entityType={type}&entityId={id}&username={user}&action={action}&offset={offset}&limit={limit}
```

- `GET/POST/PUT /users`: Verwaltung reiner Strolch-Benutzer (Systemadmins, HR-Manager, Revisoren) unabhängig von Mitarbeiterprofilen.
- `DELETE /users/{id}`: Löschen eines Strolch-Benutzers. Handelt es sich um einen mit einem `Employee` verknüpften Benutzer, wird der Strolch-Benutzer entfernt und der `Employee` auf `active = false` gesetzt (die `Employee`-Ressource bleibt unverändert erhalten).
- `POST /users/{id}/register`: Passwort-Challenge (`Usage.SET_PASSWORD`) für reine Benutzer auslösen.
- `POST /employees/{id}/reactivate`: Reaktivieren eines inaktiven Mitarbeiters (`active = true`) und automatische Neuerstellung des verknüpften Strolch-Benutzers.
- `GET /audits`: Revisionssichere Abfrage und Filterung von Audit-Ereignissen mit Pagination.

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
<groupId>ch.eitchnet.chronivaro</groupId>
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
- zentrale Fehlerabbildung und Lokalisierung menschenlesbarer REST-Fehlermeldungen
- Report-Renderer für serverseitige Exportformate, insbesondere PDF
- OpenAPI-Dokumentation
- REST-Integrationstests

REST-Ressourcen enthalten keine fachliche Berechnungslogik.

### 14.3 `chronivaro-web`

Enthält:

- HTML, CSS und Vanilla JavaScript
- UI-Komponenten und Seiten
- REST-Client
- clientseitige Eingabevalidierung als Benutzerhilfe
- i18n-Ressourcen und clientseitige Sprachauflösung
- globale Darstellung von Firmenname und optionalem Firmenlogo in einem dauerhaft sichtbaren Anwendungs- beziehungsweise Headerbereich
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

- eigene Zeiten lesen und bearbeiten / kommentieren
- eigene Mitarbeiter- und Profilinformationen einsehen
- eigene Abwesenheiten erfassen, einreichen und stornieren
- fremde Zeiten lesen und administrativ korrigieren / erfassen / löschen
- Abwesenheiten im Namen von Mitarbeitenden erfassen (Vorgesetzte für zugeordnete Mitarbeitende, HR/Admin unternehmensweit)
- Benutzer und Rollen administrieren (auch für reine Systembenutzer, inklusive Benutzerlöschung und Mitarbeiterreaktivierung)
- Abwesenheiten genehmigen
- Perioden genehmigen und wieder öffnen
- Ferienkonten korrigieren
- Reports lesen und exportieren
- Anwesenheitsstatus lesen
- sensible Abwesenheitsgründe lesen
- Konfiguration administrieren
- Audit-Log einsehen und filtern

### 17.3 Datenschutz

- Krankheits- und Unfallinformationen sind besonders restriktiv sichtbar.
- Audit-Daten sind nur für berechtigte Rollen zugänglich.
- Reports und Exporte folgen denselben Zugriffsregeln wie die Anwendung; ein alternatives Ausgabeformat darf keine zusätzlichen Daten offenlegen.
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

### 18.5 Internationalisierung

- Deutsch und Englisch werden vollständig unterstützt.
- Übersetzungsressourcen verwenden UTF-8.
- Neue Sprachen müssen ohne Änderung der fachlichen Kernlogik ergänzt werden können.
- Sprachwechsel nach dem Login erfolgt ohne erneute Authentifizierung und wird persistent gespeichert.
- Stabile technische Codes, REST-Fehlercodes und fachliche IDs sind sprachunabhängig.

### 18.6 PDF-Ausgabe

- PDF-Erzeugung erfolgt vollständig serverseitig und darf keine Browser- oder proprietären Office-Komponenten voraussetzen.
- Die PDF-Ausgabe muss deterministisch aus der jeweiligen Report-Datenbasis erzeugt werden.
- Eingebettete Schriftarten müssen die für Deutsch und Englisch benötigten Zeichen vollständig darstellen können.
- Ein nicht konfiguriertes Firmenlogo darf die Reportgenerierung nicht verhindern. Eine ungültige Logo-Konfiguration muss bei der Konfigurationsvalidierung abgelehnt werden.

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
- Löschen von Benutzern mit automatischer Deaktivierung verknüpfter Mitarbeiter ohne Datenverlust
- Reaktivierung inaktiver Mitarbeiter mit automatischer Neuerstellung des Strolch-Benutzers
- Erfassung und strukturierte Filterung von Audit-Events

### 19.2 REST-Integrationstests

- erfolgreiche CRUD-Operationen (inkl. Benutzerlöschung und Mitarbeiterreaktivierung)
- Validierungsfehler und Fehlerformat
- Rollen- und Teamgrenzen
- Zugriff auf sensible Abwesenheitsgründe
- Nebenläufigkeitskonflikte
- gesperrte Perioden
- CSV-Export und Encoding
- PDF-Export für Monatsreport, Ferienübersicht und Abwesenheitsreport
- identische Berechtigungsgrenzen zwischen UI, CSV und PDF
- Lokalisierung von REST-Fehlermeldungen bei stabilem `errorCode`
- Abfrage von Audit-Ereignissen über `/audits` mit Filterkriterien und Pagination

### 19.3 UI-Tests

- Start/Stoppen und erneuter Start eines weiteren Arbeitsblocks
- Darstellung der Arbeitsblöcke und der daraus abgeleiteten Unterbrüche
- manuelle Erfassung
- Abwesenheitsantrag und Genehmigung
- Monatsabschluss
- Fehler-, Leer- und Ladezustände
- Tastaturbedienung
- Statusdarstellung zusätzlich zur Farbe
- Login-Sprachauswahl und korrekte Priorität gegenüber Browser Storage und Strolch-Benutzersprache
- Sprachwechsel nach Login und Persistenz in Browser Storage sowie auf dem Strolch-Benutzer
- vollständige deutsche und englische UI-Texte ohne fehlende Übersetzungsschlüssel
- Anzeige von Firmenname und optionalem Firmenlogo in der globalen Anwendung
- Benutzerverwaltung mit Benutzerlöschung sowie Deaktivierung/Reaktivierung von Mitarbeitern
- Audit-Log-Ansicht mit Filterung und Detaildarstellung von Audit-Einträgen

### 19.4 Übersetzungs- und Exporttests

- jeder verpflichtende i18n-Schlüssel ist in Deutsch und Englisch vorhanden
- doppelte oder syntaktisch ungültige Übersetzungsschlüssel führen zu einem fehlgeschlagenen Build
- Fallback von Sprach-Tag auf Basissprache, Standardsprache und schliesslich Übersetzungsschlüssel
- PDF-Titel und statische Bezeichnungen entsprechen der aktuell ausgewählten UI-Sprache
- PDF-Datumswerte verwenden `yyyy-MM-dd` und Zeitdauern `HH:mm`
- genehmigte Monatsreports enthalten Status, Genehmigungsdatum und Genehmiger
- persönliche PDF-Reports enthalten Anzeigename, Personalnummer, Team und Standort
- negative Saldi sind auch ohne Farbdarstellung eindeutig als negativ erkennbar
- PDF-Erzeugung funktioniert mit und ohne konfiguriertes Firmenlogo
- PDF-Ausgaben enthalten keine Freitext-Kommentare, sofern dies nicht für einen konkreten Report ausdrücklich vorgesehen ist

### 19.5 Runtime- und HTTP-Integrationstests

- Start und Stop der Anwendung mit Embedded Jetty
- REST-Zugriff über `/rest/chronivaro/v1`
- Auslieferung von `index.html` und statischen Assets
- parallele Bereitstellung von REST API und Frontend über denselben HTTP-Server
- Fehler beim Binden eines bereits belegten HTTP-Ports
- kontrollierter Shutdown von Jetty und Strolch Runtime

## 20. Akzeptanzkriterien für das MVP

Das MVP gilt als fachlich abnahmebereit, wenn:

1. ein Administrator Mitarbeiter, Benutzer (auch reine Systembenutzer), Teams, Standorte, Feiertagskalender und Arbeitspläne verwalten sowie Benutzer löschen kann (wobei verknüpfte Mitarbeiter nicht gelöscht, sondern deaktiviert werden und bei Reaktivierung der Benutzer neu erstellt wird);
2. ein Mitarbeiter seine eigenen Mitarbeiter- und Profilinformationen (u. a. Personalnummer, Eintrittsdatum, Pensum, Team, Standort) einsehen kann;
3. ein Mitarbeiter mehrere Arbeitsblöcke pro Tag erfassen und kommentieren kann, wobei Unterbrüche aus den zeitlichen Lücken abgeleitet werden;
4. Mitarbeiter ihre offenen Zeitbuchungen bei Bedarf bearbeiten (Start- und Endzeit, Arbeitsort, Kommentar) können, Vorgesetzte (für zugeordnete Mitarbeitende) sowie HR/Administratoren (unternehmensweit) Arbeitszeitbuchungen von Mitarbeitenden in offenen Perioden manuell erfassen, vollständig korrigieren und löschen können, alle modifizierten und manuell erstellten Einträge visuell hervorgehoben werden und bei Fremderstellung der jeweilige Ersteller ausgewiesen wird;
5. die Anwendung Überlappungen und mehrere laufende Buchungen verhindert;
6. Soll- und Istzeit für Tag und Monat (inklusive Eintritten/Austritten unter dem Monat) korrekt berechnet werden;
7. Pensumsänderungen alte Monatsauswertungen nicht verfälschen;
8. die vorkonfigurierten Standard-Abwesenheitsarten verfügbar sind und weitere Abwesenheitsarten erfasst werden können;
9. Halb- und Ganztage anhand des individuellen Arbeitsplans berechnet werden;
10. ein Vorgesetzter Abwesenheiten genehmigen und ablehnen kann sowie Vorgesetzte (für zugeordnete Mitarbeitende) und HR/Administration (unternehmensweit) Abwesenheiten im Namen von Mitarbeitenden erfassen können;
11. Ferienbezüge nachvollziehbare Kontobuchungen erzeugen und der Ferienanspruch automatisiert gebucht wird;
12. die Statusseite binär und aktuell anzeigt, ob ein Mitarbeiter arbeitet oder nicht arbeitet;
13. nicht berechtigte Benutzer keine sensiblen Abwesenheitsgründe sehen;
14. Monatsperioden eingereicht, vom Vorgesetzten über eine detaillierte Inspektionsansicht geprüft, genehmigt, abgelehnt, gesperrt und begründet wieder geöffnet werden können;
15. ein Monatsreport Sollzeit, Istzeit, Abwesenheiten und Saldo zeigt;
16. der Monatsreport als CSV exportiert werden kann;
17. alle fachlich relevanten Änderungen im Audit-Log nachvollziehbar sind und über eine dedizierte UI-Ansicht filterbar eingesehen werden können;
18. die definierten Kernberechnungen automatisiert getestet sind;
19. Chronivaro ohne externen Servlet-Container als eigenständige Java-Anwendung gestartet werden kann;
20. Embedded Jetty sowohl das Frontend als auch die bestehende REST API bereitstellt;
21. Start, HTTP-Betrieb und kontrollierter Shutdown der Embedded-Jetty-Laufzeit automatisiert getestet sind.

### 20.1 Akzeptanzkriterien für die aktuellen Erweiterungen

Die Erweiterungen gemäss Abschnitt 4.2 gelten als abnahmebereit, wenn:

1. der Login-Bildschirm eine explizite Auswahl zwischen Deutsch und Englisch erlaubt;
2. eine explizit beim Login gewählte Sprache gegenüber Browser Storage und gespeicherter Benutzersprache Vorrang hat;
3. die nach Login wirksame Sprache gemäss definierter Priorität aus Login-Auswahl, Browser Storage, Strolch-Benutzersprache und Standardsprache bestimmt wird;
4. ein Sprachwechsel nach Login unmittelbar wirksam wird und sowohl im Browser Storage als auch im Strolch-Benutzer persistiert wird;
5. alle verpflichtenden statischen UI-Texte, Validierungsmeldungen und menschenlesbaren REST-Fehlermeldungen in Deutsch und Englisch verfügbar sind;
6. fehlende verpflichtende deutsche oder englische Übersetzungsschlüssel den Build fehlschlagen lassen;
7. zusätzliche Sprachen über Übersetzungsressourcen und Konfiguration ergänzt werden können, ohne die fachliche Kernlogik zu ändern;
8. Firmenname und optionales Firmenlogo global konfigurierbar und in der Anwendung sichtbar sind;
9. Monatsreport, Ferienübersicht und Abwesenheitsreport nativ als PDF exportiert werden können;
10. PDF-Exporte dieselben Daten, Filter, Berechnungen und Berechtigungen wie die entsprechenden Bildschirmansichten verwenden;
11. persönliche PDFs Anzeigename, Personalnummer, Team und Standort enthalten;
12. genehmigte Monatsreports Status, Genehmigungsdatum und Genehmiger enthalten;
13. PDF-Ausgaben Firmenname und, sofern konfiguriert, Firmenlogo enthalten;
14. PDF-Ausgaben in der aktuell ausgewählten UI-Sprache erzeugt werden;
15. negative Saldi visuell hervorgehoben und auch monochrom eindeutig als negativ erkennbar sind;
16. PDF-Dateien on demand erzeugt und nicht dauerhaft gespeichert werden.

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

### Phase 7 – Mehrsprachigkeit und native PDF-Ausgabe

- i18n-Infrastruktur und Übersetzungsressourcen für Deutsch und Englisch
- Sprachwahl auf Login-Seite und nach Login
- Persistenz der Benutzersprache in Browser Storage und Strolch-Benutzer
- Lokalisierung von UI-, Validierungs- und REST-Fehlermeldungen
- globale Konfiguration von Firmenname und optionalem Firmenlogo sowie Anzeige in der Anwendung
- gemeinsames serverseitiges Report-Modell für UI/CSV/PDF sicherstellen
- native PDF-Ausgabe für Monatsreport, Ferienübersicht und Abwesenheitsreport
- automatisierte Übersetzungs- und PDF-Tests

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
- Frontend und REST API über den eingebetteten Jetty-Server erreichbar sind;
- bei übersetzbaren Funktionen Deutsch und Englisch vollständig abgedeckt sind und die i18n-Prüfungen erfolgreich laufen;
- bei PDF-fähigen Reports die Ausgabe für die definierten Formate automatisiert getestet ist und dieselben Berechtigungs- und Berechnungsregeln wie die Bildschirmansicht verwendet.
