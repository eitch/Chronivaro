# Chronivaro – Offene Entscheidungen und Annahmen

Dieses Dokument dokumentiert alle offenen Produktfragen, die für das System geltenden Standardannahmen (Baseline Assumptions) sowie im Rahmen der Spezifikation und Implementierung geklärten Punkte.

## 1. Offene Produktentscheidungen

Folgende fachliche Fragestellungen sind vor oder während künftiger Ausbaustufen verbindlich zu entscheiden:

1. **Dürfen Zeit- und Feriensaldi negativ werden?**
   - *Aktuelle Regelung:* Negative Zeitsaldi (Überstunden-Minus) sind zulässig. Negative Feriensaldi sind **nicht** zulässig (Genehmigung wird blockiert).
2. **Gibt es eine Rundung, beispielsweise auf fünf Minuten?**
   - *Aktuelle Regelung:* Standardmässig keine Rundung (exakte minutengenaue Erfassung).
3. **Wer ist Genehmiger, wenn ein Mitarbeiter mehreren Teams zugeordnet ist?**
   - *Aktuelle Regelung:* Ein Mitarbeiter gehört zu genau einem primären Team (`teamId`).
4. **Darf ein Mitarbeiter genehmigte Abwesenheiten selbst stornieren?**
   - *Aktuelle Regelung:* Genehmigte Abwesenheiten und Ferien können über einen Stornierungsprozess (`POST /me/absences/{id}/cancel`) in offenen Perioden storniert werden. Bei Ferien wird automatisch eine `CORRECTION`-Gutschrift im Ferienkonto erzeugt; stornierte Abwesenheiten werden aus allen Kalendern ausgeblendet.
5. **Wie wird Krankheit während bereits genehmigter Ferien behandelt?**
   - *Offener Punkt:* Automatische Umwandlung / Rückvergütung von Ferienkontingenten vs. manuelle Stornierung und Neuerfassung von Krankheitstagen.
6. **Wie viele Ferien dürfen ins Folgejahr übertragen werden und wann verfallen sie?**
   - *Aktuelle Regelung:* Ferien werden unbegrenzt ins Folgejahr übertragen und verfallen im Standard nicht.
7. **Werden Überstunden unbegrenzt übertragen oder begrenzt?**
   - *Aktuelle Regelung:* Unbegrenzte Übertragung ohne automatische Kappung.
8. **Soll Homeoffice auf der Statusseite separat sichtbar sein?**
   - *Aktuelle Regelung:* Homeoffice (`HOME_OFFICE`) wird optional als Arbeitsort angezeigt; der Primärstatus bleibt binär (`WORKING` / `NOT_WORKING`).
9. **Welche Authentifizierung wird in der Zielumgebung verwendet?**
   - *Aktuelle Regelung:* Standard-Strolch-Authentifizierung (Session/Bearer-Token, `SET_PASSWORD`-Challenge).
10. **Welche Aufbewahrungs- und Löschfristen gelten?**
    - *Aktuelle Regelung:* Keine automatische physische Löschung historischer Zeitbuchungen. Ausscheidende Mitarbeiter werden deaktiviert (`active = false`). Revisionsfristen sind organisatorisch festzulegen.
11. **Sind mehrere Rechtseinheiten, Länder, Zeitzonen oder Währungen geplant?**
    - *Aktuelle Regelung:* Standardzeitzone ist `Europe/Zurich`. Einzelne Rechtseinheiten/Währungen sind nicht modelliert.

---

## 2. Gültige Standardannahmen (Baseline Assumptions)

Bis zur formellen Entscheidung abweichender Richtlinien gelten im gesamten System folgende verbindliche Annahmen:

- **Keine Rundung:** Zeiterfassung und Berechnungen erfolgen minutengenau.
- **Negative Zeitsaldi:** Sind erlaubt.
- **Negative Feriensaldi:** Sind strikt verboten (Genehmigung bei unzureichendem Guthaben wird blockiert).
- **Primäres Team:** Jeder Mitarbeiter ist genau einem Team zugeordnet.
- **Stornierungsworkflow:** Einmal genehmigte Abwesenheiten können nicht still editiert, sondern nur storniert werden (mit automatischer Ferienerstattung bei `VACATION`).
- **Standardzeitzone:** `Europe/Zurich`.
- **Flexible Arbeitsortwahl:** Mitarbeitende können ihren Arbeitsort im Tagesverlauf beliebig oft und frei wechseln (`HOME_OFFICE`, `OFFICE`, `FIELD`, `REMOTE`) ohne Begrenzung auf Tageshälften.
- **Kalenderausschluss stornierter Abwesenheiten:** Stornierte Abwesenheiten (`status == CANCELLED`) werden in allen Kalenderansichten ausgeblendet.
- **Administrator-Selbstgenehmigung:** Inhaber von Administratorrollen (`Admin`, `Administrator`, `StrolchAdmin`, `PrivilegeAdmin`) können eigene Abwesenheiten und Monatsabschlüsse selbst genehmigen.

---

## 3. Im Rahmen der Spezifikation geklärte Punkte

1. **Flexibler Arbeitsortwechsel ohne Halbtagsgrenze:** Die frühere Einschränkung auf maximal einen Arbeitsort am Vormittag und einen am Nachmittag (12:30 Uhr) wurde aufgehoben. Ein Arbeitstag kann beliebig viele `WorkEntry`-Blöcke mit jeweils individuellem Arbeitsort (`HOME_OFFICE`, `REMOTE`, `OFFICE`, `FIELD`) enthalten.
2. **REST-Exportpfade und Aliase:** Exportendpunkte unterstützen sowohl Datei-Endungen (z. B. `.pdf`, `.csv`), Query-Parameter (`?format=pdf`) als auch Standard-`Accept`-Header.
3. **Eintritt/Austritt unter dem Monat:** Tage vor `entryDate` und nach `exitDate` besitzen 0 Sollminuten und erzeugen keine Fehlermeldungen oder Warnungen vor fehlenden Buchungen.
4. **Ferienanspruchs-Gutschrift:** Der volle Jahresanspruch wird per 1. Januar (oder anteilig per `entryDate`) als `ENTITLEMENT` gebucht. Pensumsanpassungen oder Austritte erzeugen `CORRECTION`-Gegenbuchungen.
5. **Nicht-destruktive Benutzerlöschung:** Beim Löschen eines Benutzers wird das Strolch-Benutzerkonto entfernt und der `Employee` auf `active = false` gesetzt. Die `Employee`-Ressource und alle historischen Daten (`WorkDay`, `WorkEntry`, `Absence`, `VacationAccountEntry`, `TimePeriod`) bleiben physisch erhalten.
6. **Visuelle Hervorhebung und Erstelleranzeige:** Alle modifizierten oder manuell erstellten Arbeitszeitbuchungen werden visuell gekennzeichnet. Bei Fremderfassung wird transparent ausgewiesen, von wem (`createdBy`) der Eintrag erstellt wurde.
7. **Deterministische Sprachwahl:** Die Sprachwahl folgt einer eindeutigen Prioritätskette (Login-Auswahl > Browser Storage > Strolch-Benutzerprofil > Systemstandard).
8. **PDF-Scope:** Natives PDF wird ausschliesslich für Monatsreport, Ferienübersicht und Abwesenheitsreport erzeugt (der Teamreport ist nicht im nativen PDF-Scope).
9. **Onboarding von Mitarbeitenden mit Altdaten (Option 3):**
   - *Problemstellung:* Werden Mitarbeitende mit historischem Eintrittsdatum (`joinDate`, z. B. `2016-08-01`) erfasst, führte die automatische Rückrechnung von Sollzeiten ohne historische Ist-Buchungen zu massiven, künstlichen Defiziten (z. B. `-9232h`). Zudem wurde der Ferienanspruch fälschlicherweise für das historische Eintrittsjahr statt für das operative Systemjahr initialisiert.
   - *Beschlossene Lösung (Option 3):* 
     1. Entkopplung von rechtlichem Anstellungsbeginn (`Employee.joinDate`) und operativem Zeiterfassungsstart (`EmploymentScheduleVersion.validFrom`).
     2. Intelligente UI-Vorbelegung von `scheduleValidFrom` auf den 1. des laufenden Monats bei vergangenem Eintrittsdatum mit Hinweistext.
     3. Optionale Angabe von Anfangssaldi beim Erstellen (`initialOvertimeMinutes`, `initialVacationDays`).
     4. Automatische Anlage eines Baseline-Perioden-Snapshots für den Vormonat von `scheduleValidFrom` im Status `LOCKED` zur nahtlosen Übernahme des Überzeit-/Unterzeitsaldos.
     5. Automatische Buchung von Anfangs-Ferientagen als `CARRY_OVER`-Journaleintrag für das aktive Erfassungsjahr sowie Pro-rata-Anspruchsgutschrift (`ENTITLEMENT`) für das aktive Erfassungsjahr.
10. **Stornierung genehmigter Ferien & Kalenderausschluss:** Genehmigte Ferien (`APPROVED`) können in offenen Perioden storniert werden. Der Status wechselt auf `CANCELLED`, das bezogene Ferienguthaben wird per automatischer `CORRECTION`-Buchung im Ferienjournal erstattet und die Abwesenheit wird in allen Kalendern (`AbsenceCalendarView`, Monatsansichten) ausgeblendet.
11. **Administrator-Selbstgenehmigung:** Benutzer mit Administratorrollen (`Admin`, `Administrator`, `StrolchAdmin`, `PrivilegeAdmin`) dürfen eigene Abwesenheitsanträge direkt (`directApprove = true`) sowie über die Genehmigungs-Queue freigeben und eigene Monatsperioden abschliessen. Für reguläre Mitarbeitende und Standard-Vorgesetzte bleibt die Selbstgenehmigung strikt blockiert.
