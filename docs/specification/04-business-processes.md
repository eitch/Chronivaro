# Chronivaro – Geschäftsprozesse und Workflows

Dieses Dokument beschreibt alle fachlichen und administrativen Interaktions- und Systemworkflows in Chronivaro.

## 1. Zeiterfassung und Arbeitstag

### 1.1 Arbeitstag starten und stoppen

```text
[Mitarbeiter: Start] ──► [WorkDay prüfen/erstellen] ──► [Schedule auflösen] ──► [Offenen WorkEntry anlegen]
                                                                                        │
[Mitarbeiter: Stopp] ◄──────────────────────────────────────────────────────────────────┘
         │
         ├── Enddatum = Startdatum: Normal beenden
         ├── Enddatum = Folgetag:   Mitternacht-Split (24:00 Uhr Starttag + Folgetag-Buchung)
         └── Enddatum > Folgetag:   Vergessener Timer (Capping auf Tagessoll, max 24:00 Uhr)
```

#### Ablauf

1. **Arbeit starten:** Mitarbeiter klickt auf "Start" im Dashboard (optional mit Arbeitsort und Kommentar).
2. **WorkDay prüfen:** Das System prüft, ob für das heutige Kalenderdatum bereits ein `WorkDay` beim Mitarbeiter existiert.
3. **WorkDay initialisieren (falls neu):**
   - Erstellt einen neuen `WorkDay` für das aktuelle Tagesdatum.
   - Ermittelt die zu diesem Datum gültige `EmploymentScheduleVersion` und referenziert diese im `WorkDay`.
   - Aktualisiert das Feld `currentWorkDayId` am `Employee`.
4. **Validierung:** Das System prüft, dass auf dem aktuellen `WorkDay` keine bereits laufende Buchung existiert.
5. **WorkEntry erstellen:** Erstellt einen offenen `WorkEntry` (Startzeit = jetzt, Endzeit = leer, `workingLocation`, `source = TIMER`) und verknüpft ihn mit dem `WorkDay`.
6. **Arbeit stoppen:** Mitarbeiter klickt auf "Stopp":
   - **Regulärer Fall (gleicher Tag):** Der laufende `WorkEntry` erhält den aktuellen Zeitstempel als `end`.
   - **Arbeit über Mitternacht (Enddatum = Folgetag):**
     - Die Buchung wird am Starttag um `24:00 Uhr` (`23:59:59.999`) beendet.
     - Für die restliche Zeit wird automatisch eine neue Buchung auf dem `WorkDay` des Folgetages angelegt.
   - **Vergessener Timer (Enddatum > Folgetag):**
     - Der Timer wird automatisch auf den Zeitpunkt begrenzt, an dem das Tagessoll erreicht wurde:  
       `Endzeit = Startzeit + max(0, Sollzeit - bisherige_Istzeit)`.
     - Die Endzeit wird auf maximal `24:00 Uhr` des Starttages gedeckelt; überschüssige Zeit wird verworfen.
     - Der `WorkEntry` erhält automatisch den Kommentar `"Timer vergessen - auf Sollzeit begrenzt"`.
7. **Weiterer Arbeitsblock:** Beginnt der Mitarbeiter später am selben Tag erneut zu arbeiten, startet er einen neuen `WorkEntry` auf demselben `WorkDay`. Die Lücke dazwischen wird als Arbeitsunterbruch ausgewiesen.

---

### 1.2 Manuelle Zeiterfassung, Bearbeitung und administrative Korrekturen

1. **Bearbeitung durch den Mitarbeiter:**
   - Mitarbeiter öffnet die Zeiterfassungsansicht ("Meine Zeiten") für einen Tag in einer **offenen Periode**.
   - Kann Start- und Endzeiten anpassen, Arbeitsort ändern oder Kommentare erfassen.
   - Das System validiert, dass keine Überlappungen entstehen, das Ende nach dem Start liegt und die Buchung innerhalb der offenen Periode liegt.
2. **Administrative Korrekturen durch Vorgesetzte, HR und Administratoren:**
   - Vorgesetzte (für zugeordnete Mitarbeitende/Teams) und HR/Administratoren (unternehmensweit) können Zeitbuchungen für beliebige Tage in offenen Perioden manuell erfassen, vollständig bearbeiten (Start, Ende, Ort, Kommentar) oder löschen.
   - **Mitternachtsarbeit:** Bei der manuellen Erfassung/Bearbeitung kann angegeben werden, dass über Mitternacht gearbeitet wurde (mit automatischer Aufteilung).
   - **Gesperrte Perioden:** Bei genehmigten oder gesperrten Perioden muss die Periode zuerst begründet wiedereröffnet werden (siehe [Monatsabschluss](#3-monatsabschluss-und-periodengenehmigung)).
3. **Visuelle Hervorhebung und Transparenz:**
   - Alle modifizierten oder manuell erstellten Zeitbuchungen werden in UI, PDF und Reports optisch hervorgehoben (z. B. Badge).
   - Wurde ein Eintrag fremderfasst (durch Vorgesetzte/HR/Admin), wird der Ersteller (`createdBy`) gut sichtbar ausgewiesen.
4. **Revisionssichere Protokollierung:** Jede Erfassung, Änderung oder Löschung wird mit Vorher-/Nachher-Werten und Begründung im Audit-Log festgehalten.

---

## 2. Abwesenheiten und Ferien

### 2.1 Abwesenheitsantrag und Genehmigung

```text
[Mitarbeiter] ──► Antrag erfassen ──┬──► [DRAFT] ──► Bearbeiten / Verwerfen (kein Ferienabzug)
                                    │
                                    └──► [SUBMITTED] ──► [Vorgesetzter]
                                                              │
                                       ┌──────────────────────┴──────────────────────┐
                                       ▼                                             ▼
                                  [APPROVED]                                    [REJECTED]
                                       │                                        (mit Begründung)
                               (bei VACATION:
                            VacationAccountEntry
                             USAGE abbuchen)
```

#### Ablauf

1. **Antrag erfassen:** Mitarbeiter wählt Abwesenheitsart (`AbsenceType`), Zeitraum (`startDate`, `endDate`), Dauer (`HOURS`, `HALF_DAY`, `FULL_DAY`), ggf. Tageshälfte (`MORNING`/`AFTERNOON`) oder Stunden sowie einen Kommentar.
2. **Sollzeit-Vorschau:** Das System berechnet die betroffenen Sollminuten und prüft eventuelle Überschneidungen und Feriensaldi.
3. **Entwurf speichern (`DRAFT`):**
   - Der Antrag kann als Entwurf zwischengespeichert werden.
   - Entwürfe können in der Übersicht nachträglich bearbeitet oder verworfen/storniert werden (`CANCELLED`).
   - Das Stornieren eines Entwurfs erzeugt **keine** Ferienbuchung.
4. **Antrag einreichen (`SUBMITTED`):** Der Mitarbeiter reicht den Antrag ein. Der Antrag erscheint in der Genehmigungsansicht des zuständigen Vorgesetzten.
5. **Entscheidung durch Vorgesetzten:**
   - **Genehmigung (`APPROVED`):** Der Antrag wird genehmigt. Handelt es sich um den Typ `VACATION`, wird sofort ein `USAGE`-Journaleintrag auf dem Ferienkonto erstellt.
   - **Ablehnung (`REJECTED`):** Der Vorgesetzte lehnt den Antrag mit verpflichtendem Begründungskommentar ab.
6. **Stornierung genehmigter Abwesenheiten:** Die Stornierung einer bereits genehmigten Abwesenheit erzeugt eine `CORRECTION`-Gegenbuchung im Ferienkonto; historische Einträge werden nicht gelöscht.
7. **Fremderfassung durch Vorgesetzte und HR:** Vorgesetzte und HR/Administratoren können Abwesenheiten im Namen von Mitarbeitenden erfassen (wahlweise direkt `APPROVED` oder als `SUBMITTED`). Bei direkt genehmigten Ferien wird die Ferienabbuchung unmittelbar ausgelöst. Der Ersteller (`createdBy`) wird transparent dokumentiert.

---

## 3. Monatsabschluss und Periodengenehmigung

```text
[Mitarbeiter] ──► Monatsreport prüfen ──► Einreichen [SUBMITTED]
                                                │
                                                ▼
[Vorgesetzter] ──► Detailinspektion öffnen ──┬──► Genehmigen [APPROVED] ──► [LOCKED]
                                             │
                                             └──► Ablehnen [REJECTED] (mit Begründung)
                                                        │
                                                        ▼
                                             [Wiedereröffnung OPEN] (nur mit Berechtigung)
```

#### Ablauf

1. **Prüfung durch Mitarbeiter:** Mitarbeiter prüft am Monatsende seinen Monatsreport (Tagesaufstellung, Soll/Ist, Saldi, Warnungen).
2. **Einreichung (`SUBMITTED`):** Mitarbeiter reicht die Periode ein (`POST /me/periods/{yearMonth}/submit`).
3. **Detailprüfung durch Vorgesetzten:**
   - Der Vorgesetzte öffnet die Genehmigungsansicht (`ApprovalsView`).
   - Über eine detaillierte Inspektionsansicht (`GET /approvals/periods/{id}`) sieht der Vorgesetzte den vollständigen Monatsreport mit allen Tagen, Zeitblöcken, Unterbrüchen, Abwesenheiten, Saldi und Kommentaren.
4. **Genehmigung oder Ablehnung:**
   - **Genehmigung (`APPROVED`):** Die Periode wird genehmigt. Das System erzeugt einen unveränderlichen `calculationSnapshot` und sperrt die Periode für weitere Bearbeitungen (`LOCKED`).
   - **Ablehnung (`REJECTED`):** Vorgesetzter lehnt die Periode mit Begründung ab. Der Mitarbeiter kann Korrekturen vornehmen und die Periode erneut einreichen.
5. **Wiedereröffnung (`REOPEN`):** Eine gesperrte Periode kann durch HR/Administration mit expliziter Begründung wiedereröffnet werden (`POST /periods/{id}/reopen`), wodurch sie wieder den Status `OPEN` erhält.

---

## 4. Benutzer- und Mitarbeiter-Lifecycle

### 4.1 Registrierung und Onboarding von Mitarbeitern

1. Ein Administrator legt einen neuen Mitarbeiter an und wählt in der Mitarbeiterverwaltung die Aktion "Registrieren".
2. Das System identifiziert den verknüpften Strolch-Benutzer.
3. Das System löst eine Strolch-Challenge (`Usage.SET_PASSWORD`) aus.
4. Der `UserChallengeHandler` übermittelt eine ansprechende Onboarding-E-Mail mit dem Registrierungstoken und einem direkten Link zum Passwortformular (unter Verwendung der konfigurierten `serverBaseUrl`).
5. Der Mitarbeiter öffnet den Link, vergibt sein initiales Passwort und meldet sich an.
6. Es werden keine Passwörter manuell vergeben oder im Klartext versendet.

---

### 4.2 Benutzerverwaltung für reine Systembenutzer

1. Ein Administrator legt in der Benutzerverwaltung einen neuen Strolch-Benutzer (z. B. für Systemadmins, HR-Manager, Revisoren) ohne verknüpftes Mitarbeiterprofil an.
2. Der Benutzer erhält Rollen (z. B. `Admin`, `HR`, `Supervisor`, `Reader`).
3. Der Administrator löst die Registrierung / Passwort-Challenge (`Usage.SET_PASSWORD`) aus.
4. Der Benutzer aktiviert sein Konto und erhält rollenbasierten Zugriff.

---

### 4.3 Nicht-destruktive Benutzerlöschung und Mitarbeiterdeaktivierung

```text
[Benutzer löschen] ──┬──► Reiner Benutzer (ohne Employee): Strolch-Benutzerkonto physisch löschen
                     │
                     └──► Mitarbeiter-Benutzer: 
                          ├── Strolch-Benutzerkonto entfernen (kein Login mehr möglich)
                          ├── Employee auf inaktiv setzen (active = false)
                          └── Alle Buchungen & Saldi unverändert erhalten (kein Datenverlust!)
```

1. Administrator löst die Löschung eines Benutzers aus.
2. **Reiner Benutzer:** Das Benutzerkonto wird aus Strolch entfernt.
3. **Mitarbeiter-Benutzer:**
   - Die `Employee`-Ressource wird **nicht** gelöscht.
   - Der `Employee` wird auf `active = false` gesetzt.
   - Der zugehörige Strolch-Benutzer wird entfernt.
   - Alle historischen Buchungen (`WorkDay`, `WorkEntry`, `Absence`, `VacationAccountEntry`, `TimePeriod`) bleiben vollständig und reproduzierbar erhalten.
4. Der Vorgang wird im Audit-Log protokolliert.

---

### 4.4 Reaktivierung von Mitarbeitern

1. Ein Administrator öffnet einen inaktiven Mitarbeiter (`active = false`) und wählt "Reaktivieren".
2. Der Status wird auf `active = true` gesetzt.
3. **Ferieninitialisierung:** Das System initialisiert automatisch den anteiligen Ferienanspruch für das laufende Anspruchsjahr (pro-rata ab Reaktivierungsdatum).
4. **Benutzerkonto wiederherstellen:** Das System erstellt automatisch einen neuen Strolch-Benutzer mit dem konfigurierten Benutzernamen und den erforderlichen Rollen.
5. **Passwort-Initialisierung:** Der Administrator löst die Passwort-Challenge (`Usage.SET_PASSWORD`) aus, damit der Mitarbeiter sein Passwort festlegen und sich anmelden kann.
6. Der Vorgang wird im Audit-Log protokolliert.

---

## 5. Audit-Log-Einsicht und Filterung

1. Berechtigte Rollen (`Admin`, Revisor) öffnen die Audit-Log-Ansicht in der Administration.
2. Filterkriterien:
   - Zeitraum (`from`, `to`)
   - Entitätstyp (`entityType`: `Employee`, `WorkEntry`, `Absence`, etc.)
   - Entitäts-ID (`entityId`)
   - Benutzername (`username`)
   - Aktion (`action`: `CREATE`, `UPDATE`, `DELETE`, `APPROVE`, `REOPEN`, etc.)
3. Die Treffer werden paginiert mit Zeitstempel, Benutzer, Aktion und Zusammenfassung dargestellt.
4. Per Klick auf einen Eintrag öffnet sich ein Detailmodal mit vollständigen Vorher-/Nachher-Snapshots, Begründung und Korrelations-ID.
