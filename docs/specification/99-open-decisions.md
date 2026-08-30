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
   - *Aktuelle Regelung:* Genehmigte Abwesenheiten können über einen Stornierungsprozess (`POST /me/absences/{id}/cancel`) storniert werden, was eine Gegenbuchung erzeugt.
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
- **Stornierungsworkflow:** Einmal genehmigte Abwesenheiten können nicht still editiert, sondern nur storniert werden.
- **Standardzeitzone:** `Europe/Zurich`.
- **Arbeitsortanzeige:** `HOME_OFFICE`, `OFFICE` und `CUSTOMER` sind als Arbeitsorte verfügbar.

---

## 3. Im Rahmen der Spezifikation geklärte Punkte

1. **Halbtags-Grenze für Arbeitsorte:** Die Grenze zwischen Vormittag (`MORNING`) und Nachmittag (`AFTERNOON`) ist auf 12:30 Uhr (bzw. die Mitte des täglichen Arbeitsplans) festgelegt.
2. **REST-Exportpfade und Aliase:** Exportendpunkte unterstützen sowohl Datei-Endungen (z. B. `.pdf`, `.csv`), Query-Parameter (`?format=pdf`) als auch Standard-`Accept`-Header.
3. **Eintritt/Austritt unter dem Monat:** Tage vor `entryDate` und nach `exitDate` besitzen 0 Sollminuten und erzeugen keine Fehlermeldungen oder Warnungen vor fehlenden Buchungen.
4. **Ferienanspruchs-Gutschrift:** Der volle Jahresanspruch wird per 1. Januar (oder anteilig per `entryDate`) als `ENTITLEMENT` gebucht. Pensumsanpassungen oder Austritte erzeugen `CORRECTION`-Gegenbuchungen.
5. **Nicht-destruktive Benutzerlöschung:** Beim Löschen eines Benutzers wird das Strolch-Benutzerkonto entfernt und der `Employee` auf `active = false` gesetzt. Die `Employee`-Ressource und alle historischen Daten (`WorkDay`, `WorkEntry`, `Absence`, `VacationAccountEntry`, `TimePeriod`) bleiben physisch erhalten.
6. **Visuelle Hervorhebung und Erstelleranzeige:** Alle modifizierten oder manuell erstellten Arbeitszeitbuchungen werden visuell gekennzeichnet. Bei Fremderfassung wird transparent ausgewiesen, von wem (`createdBy`) der Eintrag erstellt wurde.
7. **Deterministische Sprachwahl:** Die Sprachwahl folgt einer eindeutigen Prioritätskette (Login-Auswahl > Browser Storage > Strolch-Benutzerprofil > Systemstandard).
8. **PDF-Scope:** Natives PDF wird ausschliesslich für Monatsreport, Ferienübersicht und Abwesenheitsreport erzeugt (der Teamreport ist nicht im nativen PDF-Scope).
