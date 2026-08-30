# Chronivaro – REST-API-Spezifikation

Dieses Dokument spezifiziert die REST-Schnittstelle von Chronivaro, einschliesslich Pfadkonventionen, Fehlerbehandlung, Lokalisierungsverhalten und allen Endpunkten.

## 1. Allgemeine Konventionen

- **Basis-Pfad:** `/rest/chronivaro/v1`
- **Datenformat:** Standardmässig `application/json; charset=UTF-8`
- **Datums- und Zeitformate:**
  - ISO-8601 Zeitpunkte mit Zeitzonen-Offset (z. B. `2026-08-04T08:15:00+02:00`)
  - Fachliche Kalendertage im Format `YYYY-MM-DD` (z. B. `2026-08-04`)
  - Periodenbezeichner als `YYYY-MM` (z. B. `2026-08`)
- **Authentifizierung:** Bearer-Token / Strolch-Zertifikat im `Authorization`-Header. Keine anonymen fachlichen Endpunkte.
- **Lokalisierung:** Der Client übermittelt die gewünschte Sprache im HTTP-Header `Accept-Language` (z. B. `de` oder `en`). Serverseitig erzeugte menschenlesbare Meldungen werden entsprechend lokalisiert.
- **Optimistische Nebenläufigkeitskontrolle:** Über Versions-Header / ETag bei schreibenden Änderungen.
- **Pagination:** Listenabfragen mit potenziell vielen Treffern unterstützen `offset` und `limit`.

---

## 2. Standardisiertes Fehlerformat

Tritt bei der Verarbeitung ein fachlicher oder Validierungsfehler auf, antwortet die API mit einem einheitlichen Fehler-JSON:

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
  "correlationId": "01J4F8K9X7P2Q5W1"
}
```

- `errorCode`: Stabiler, sprachunabhängiger technischer Fehlercode.
- `message`: Lokalisierte, menschenlesbare Fehlermeldung gemäss `Accept-Language`.
- `fieldErrors`: Optionale Liste feldspezifischer Validierungsfehler.
- `correlationId`: Eindeutige ID zur Nachverfolgung in den Server-Logs und im Audit-Log.

---

## 3. Ressourcen und Endpunkte

### 3.1 Arbeitszeiten und Buchungen

| Methode | Pfad | Beschreibung | Rollen / Berechtigung |
|---|---|---|---|
| `GET` | `/me/profile` | Eigene Benutzer- und Mitarbeiterstammdaten | Angemeldeter Benutzer |
| `GET` | `/me/work-entries?from={date}&to={date}` | Eigene Arbeitszeitbuchungen im Zeitraum | Angemeldeter Mitarbeiter |
| `POST` | `/me/work-entries` | Eigene Arbeitszeitbuchung manuell erfassen | Angemeldeter Mitarbeiter |
| `PUT` | `/me/work-entries/{id}` | Eigene offene Buchung bearbeiten (Start/Ende/Ort/Kommentar) | Angemeldeter Mitarbeiter |
| `DELETE` | `/me/work-entries/{id}` | Eigene Buchung in offener Periode löschen | Angemeldeter Mitarbeiter |
| `POST` | `/me/timer/start` | Arbeitszeiterfassung (Timer) starten | Angemeldeter Mitarbeiter |
| `POST` | `/me/timer/stop` | Laufenden Timer stoppen (mit optionalem Kommentar) | Angemeldeter Mitarbeiter |
| `GET` | `/me/day-summary/{date}` | Tageszusammenfassung (Soll/Ist/Saldo/Unterbrüche) | Angemeldeter Mitarbeiter |
| `GET` | `/me/month-summary/{yearMonth}` | Monatszusammenfassung für den Mitarbeiter | Angemeldeter Mitarbeiter |
| `GET` | `/employees/{id}/work-entries?from={date}&to={date}` | Arbeitszeitbuchungen eines Mitarbeiters abrufen | Supervisor (Team), HR, Admin |
| `POST` | `/employees/{id}/work-entries` | Buchung für Mitarbeiter manuell erfassen | Supervisor (Team), HR, Admin |
| `PUT` | `/admin/work-entries/{id}` | Zeitbuchung administrativ/supervisorisch korrigieren | Supervisor (Team), HR, Admin |
| `DELETE` | `/admin/work-entries/{id}` | Zeitbuchung administrativ/supervisorisch löschen | Supervisor (Team), HR, Admin |

---

### 3.2 Abwesenheiten

| Methode | Pfad | Beschreibung | Rollen / Berechtigung |
|---|---|---|---|
| `GET` | `/me/absences` | Eigene Abwesenheiten abrufen | Angemeldeter Mitarbeiter |
| `POST` | `/me/absences` | Eigenen Abwesenheitsantrag erfassen (Entwurf oder einreichen) | Angemeldeter Mitarbeiter |
| `PUT` | `/me/absences/{id}` | Abwesenheitsentwurf (`DRAFT`) bearbeiten | Angemeldeter Mitarbeiter |
| `POST` | `/me/absences/{id}/submit` | Abwesenheitsentwurf einreichen (`DRAFT` -> `SUBMITTED`) | Angemeldeter Mitarbeiter |
| `POST` | `/me/absences/{id}/cancel` | Abwesenheit stornieren (`CANCELLED`) | Angemeldeter Mitarbeiter |
| `GET` | `/employees/{id}/absences` | Abwesenheiten eines Mitarbeiters einsehen | Supervisor (Team), HR, Admin |
| `POST` | `/employees/{id}/absences` | Abwesenheit im Namen eines Mitarbeiters erfassen | Supervisor (Team), HR, Admin |
| `GET` | `/absence-types` | Liste der aktiven Abwesenheitsarten abrufen | Alle authentifizierten Benutzer |
| `GET` | `/approvals/absences` | Liste offener Abwesenheitsanträge zur Genehmigung | Supervisor, HR, Admin |
| `POST` | `/approvals/absences/{id}/approve` | Abwesenheitsantrag genehmigen | Supervisor, HR, Admin |
| `POST` | `/approvals/absences/{id}/reject` | Abwesenheitsantrag mit Begründung ablehnen | Supervisor, HR, Admin |

---

### 3.3 Ferienkonto

| Methode | Pfad | Beschreibung | Rollen / Berechtigung |
|---|---|---|---|
| `GET` | `/me/vacation-account?year={year}` | Eigene Ferienübersicht und Kontobuchungen abrufen | Angemeldeter Mitarbeiter |
| `GET` | `/me/vacation-account.pdf?year={year}` | Eigene Ferienübersicht als natives PDF herunterladen | Angemeldeter Mitarbeiter |
| `GET` | `/employees/{id}/vacation-account?year={year}` | Ferienkonto eines Mitarbeiters einsehen | Supervisor (Team), HR, Admin |
| `GET` | `/employees/{id}/vacation-account.pdf?year={year}` | Ferienübersicht eines Mitarbeiters als PDF | Supervisor (Team), HR, Admin |
| `POST` | `/employees/{id}/vacation-adjustments` | Manuelle Ferienkorrektur (`CORRECTION`) mit Pflichtkommentar | Supervisor (Team), HR, Admin |

---

### 3.4 Anwesenheitsstatus

| Methode | Pfad | Beschreibung | Rollen / Berechtigung |
|---|---|---|---|
| `GET` | `/presence?teamId={id}&locationId={id}` | Anwesenheitsliste der Mitarbeitenden (`WORKING`/`NOT_WORKING`) | Alle authentifizierten Benutzer |

---

### 3.5 Perioden und Monatsabschluss

| Methode | Pfad | Beschreibung | Rollen / Berechtigung |
|---|---|---|---|
| `GET` | `/me/periods/{yearMonth}` | Status der eigenen Monatsperiode abrufen | Angemeldeter Mitarbeiter |
| `POST` | `/me/periods/{yearMonth}/submit` | Eigene Monatsperiode einreichen | Angemeldeter Mitarbeiter |
| `GET` | `/approvals/periods` | Liste der eingereichten Monatsperioden zur Genehmigung | Supervisor, HR, Admin |
| `GET` | `/approvals/periods/{id}` | Vollständiger Monatsreport zur Genehmigungsinspektion | Supervisor, HR, Admin |
| `POST` | `/approvals/periods/{id}/approve` | Monatsperiode genehmigen und sperren (`LOCKED`) | Supervisor, HR, Admin |
| `POST` | `/approvals/periods/{id}/reject` | Monatsperiode mit Begründung ablehnen | Supervisor, HR, Admin |
| `POST` | `/periods/{id}/reopen` | Gesperrte Periode mit Begründung wiedereröffnen | HR, Admin |

---

### 3.6 Reports und Exporte

| Methode | Pfad | Beschreibung | Rollen / Berechtigung |
|---|---|---|---|
| `GET` | `/reports/time-balance` | Monatsreport (JSON) | Mitarbeiter, Supervisor, HR, Admin |
| `GET` | `/reports/time-balance.csv` | Monatsreport als CSV-Export | Mitarbeiter, Supervisor, HR, Admin |
| `GET` | `/reports/time-balance.pdf` | Monatsreport als native PDF-Ausgabe | Mitarbeiter, Supervisor, HR, Admin |
| `GET` | `/reports/absences` | Abwesenheitsreport (JSON/CSV) | Mitarbeiter, Supervisor, HR, Admin |
| `GET` | `/reports/absences.pdf` | Abwesenheitsreport als native PDF-Ausgabe | Mitarbeiter, Supervisor, HR, Admin |

*Format-Aliase:* Für Exportendpunkte werden neben der Dateiendung (z. B. `.pdf`, `.csv`) auch Query-Parameter (z. B. `?format=pdf`) sowie Standard-`Accept`-Header (`application/pdf`, `text/csv`) unterstützt.

---

### 3.7 Administration

| Methode | Pfad | Beschreibung | Rollen / Berechtigung |
|---|---|---|---|
| `GET` / `POST` / `PUT` | `/users` | Benutzerverwaltung für reine Strolch-Benutzer | Admin |
| `DELETE` | `/users/{id}` | Benutzer löschen (Mitarbeiter wird deaktiviert, nicht gelöscht) | Admin |
| `POST` | `/users/{id}/register` | Passwort-Challenge (`SET_PASSWORD`) für reine Benutzer auslösen | Admin |
| `GET` / `POST` / `PUT` | `/employees` | Mitarbeiterstamm verwalten | HR, Admin |
| `POST` | `/employees/{id}/register` | Registrierungs-E-Mail / Onboarding-Challenge auslösen | HR, Admin |
| `POST` | `/employees/{id}/reactivate` | Inaktiven Mitarbeiter reaktivieren & Ferien initialisieren | HR, Admin |
| `GET` / `POST` / `PUT` | `/teams` | Teams verwalten | HR, Admin |
| `GET` / `POST` / `PUT` | `/locations` | Arbeitsstandorte verwalten | Admin |
| `GET` / `POST` / `PUT` | `/holiday-calendars` | Feiertagskalender verwalten | Admin |
| `GET` / `POST` / `PUT` | `/absence-types` | Abwesenheitsarten konfigurieren | Admin |
| `GET` / `POST` / `PUT` | `/employees/{id}/schedule-versions` | Arbeitspläne versionieren | HR, Admin |
| `GET` / `POST` / `PUT` | `/configuration` | Globale Einstellungen (Firmenname, Logo, Bürozeiten) | Admin |
| `GET` | `/audits` | Audit-Log mit Filterparametern und Pagination abrufen | Admin, Revisor |

Detaillierte Autorisierungsregeln für alle Operationen sind in [Sicherheit und Datenschutz](09-security-and-privacy.md) festgelegt.
