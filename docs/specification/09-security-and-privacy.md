# Chronivaro – Sicherheit und Datenschutz

Dieses Dokument spezifiziert die Anforderungen an Authentifizierung, feingranulare Autorisierung (Rollen und Privilegien) sowie den Schutz sensibler personenbezogener Daten in Chronivaro.

## 1. Authentifizierung

- **Sitzungsverwaltung:** Chronivaro nutzt die Authentifizierungsmechanismen des Strolch-Frameworks (Token-/Session-basiert).
- **Keine anonymen Endpunkte:** Alle fachlichen und administrativen REST-Endpunkte unter `/rest/chronivaro/v1` erfordern eine gültige Authentifizierung.
- **Passwortinitialisierung und Onboarding:** Passwörter werden niemals im Klartext übertragen oder manuell von Administratoren vergeben. Das Onboarding erfolgt über zeitlich befristete Strolch-Challenges (`Usage.SET_PASSWORD`) mit sicher generierten Registrierungstokens.

---

## 2. Rollenmodell und Autorisierung

Chronivaro erzwingt ein rollenbasiertes Zugriffskontrollmodell (Role-Based Access Control, RBAC). Autorisierungsprüfungen werden bei jedem Aufruf serverseitig in den Core-Services und am REST-Rand validiert.

### 2.1 Standardrollen

| Rolle | Fachliche Verantwortung | Typischer Umfang |
|---|---|---|
| `Employee` | Regulärer Mitarbeiter | Eigene Arbeitszeiten, eigene Abwesenheiten, eigenes Profil, eigene Saldi |
| `Supervisor` | Teamleiter / Vorgesetzter | Zeiteinsicht und -korrektur für zugeordnete Teams, Abwesenheits- und Periodengenehmigungen |
| `HR` | Personaladministration | Unternehmensweite Pflege von Mitarbeiterstammdaten, Arbeitsmodellen, Ferienkonten und Reports |
| `Admin` | Systemadministrator | Globale Konfiguration, Benutzerverwaltung (auch reine Systembenutzer), Stammdaten, Audit-Log |
| `Reader` | Revisor / Leseberechtigter | Lesender Zugriff auf freigegebene Berichte und Auswertungen ohne Änderungsrechte |

---

### 2.2 Getrennt zu prüfende Berechtigungen (Privilegien)

Die Core-Logik prüft folgende Berechtigungen strikt getrennt:

1. **Eigene Buchungen:** Eigene Arbeitszeiten erfassen, lesen, bearbeiten und kommentieren (in offenen Perioden).
2. **Eigenes Profil:** Eigene Mitarbeiter- und Stammdaten einsehen.
3. **Eigene Abwesenheiten:** Eigene Abwesenheiten als Entwurf erfassen, einreichen und stornieren.
4. **Fremde Zeitbuchungen:** Arbeitszeitbuchungen fremder Mitarbeiter einsehen, manuell erfassen, korrigieren und löschen (Vorgesetzte für zugeordnete Mitarbeitende/Teams; HR und Admin unternehmensweit).
5. **Abwesenheiten im Namen anderer:** Abwesenheiten für Mitarbeitende fremderfassen (Vorgesetzte teambezogen, HR/Admin unternehmensweit).
6. **Abwesenheitsgenehmigung:** Abwesenheitsanträge von Mitarbeitenden genehmigen oder ablehnen.
7. **Periodengenehmigung & Wiedereröffnung:** Monatsperioden prüfen, genehmigen, sperren sowie gesperrte Perioden begründet wiedereröffnen.
8. **Ferienkontokorrektur:** Manuelle `CORRECTION`-Buchungen auf dem Ferienkonto vornehmen.
9. **Benutzer- & Rollenadministration:** Strolch-Benutzer anlegen, Rollen zuweisen, Benutzer löschen und Passwort-Challenges initiieren (auch für reine Systembenutzer).
10. **Mitarbeiter-Reaktivierung:** Inaktive Mitarbeiter reaktivieren und Ferienansprüche initialisieren.
11. **Report- & Exportzugriff:** Zugriff auf Monatsreports, Ferienübersichten, Teamreports und Abwesenheitsauswertungen.
12. **Statusanzeige:** Anwesenheitsstatus (`WORKING` / `NOT_WORKING`) abfragen.
13. **Sensible Abwesenheitsgründe:** Einsicht in vertrauliche Abwesenheitsarten (z. B. Krankheit, Unfall).
14. **Systemkonfiguration:** Globale Einstellungen (Firmenname, Logo, Bürozeiten) verwalten.
15. **Audit-Log-Einsicht:** Abfrage und Filterung revisionssicherer Systemprotokolle.

---

## 3. Datenschutz und Vertraulichkeit

### 3.1 Schutz sensibler Gesundheits- und Personaldaten

- **Maskierung auf Statusseiten:** Die öffentliche Anwesenheitsliste zeigt ausschliesslich den binären Status (`WORKING` / `NOT_WORKING`) oder neutrale Arbeitsorte (`HOME_OFFICE`). Abwesenheitsgründe wie Krankheit oder Unfall werden niemals auf allgemeinen Statusseiten angezeigt.
- **Einschränkung in Reports:** Der Abwesenheitstyp (Krankheit/Unfall) ist nur für Benutzer mit expliziter Berechtigung (`HR`, `Admin` und berechtigte Vorgesetzte) sichtbar.
- **Kommentare:** Freitextkommentare dürfen keine vertraulichen medizinischen Diagnosen enthalten und werden standardmässig nicht in PDF- und CSV-Exporte übernommen.

---

### 3.2 Zugriffsschutz auf Audit-Daten

- Das Audit-Log enthält historische Personen-, Zeit- und Änderungsinformationen und ist ausschliesslich für Administratoren und Auditoren (`Admin`, `Reader`) zugänglich.
- Audit-Einträge sind unveränderlich (Append-Only) und vor Manipulation geschützt.

---

### 3.3 Berechtigungskonsistenz über alle Exportformate

- Reports und Exporte (UI, CSV und serverseitiges PDF) unterliegen denselben Sicherheits- und Berechtigungsregeln.
- Kein Exportformat darf zusätzliche Daten oder sensible Felder offenlegen, die der anfragende Benutzer in der Weboberfläche nicht einsehen darf.

---

### 3.4 Datenaufbewahrung und Revisionssicherheit

- **Nicht-destruktive Mitarbeiterverwaltung:** Ein `Employee` wird niemals physisch gelöscht, wenn der Benutzer ausscheidet. Stattdessen wird der Mitarbeiter deaktiviert (`active = false`), um historische Buchungen, Saldi und steuer-/arbeitsrechtliche Nachweise unverändert und reproduzierbar zu erhalten.
- Revisionsrelevante Vorgänge werden mit Korrelations-ID, Vorher-/Nachher-Zustand, Benutzername und Zeitstempel unveränderlich auditiert.
