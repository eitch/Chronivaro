# Chronivaro – Produktziel und Umfang

## 1. Zweck des Dokuments

Dieses Dokument beschreibt die übergeordneten fachlichen Ziele, Zielgruppen, Rollen sowie den verbindlichen Produktumfang für **Chronivaro**, eine webbasierte Anwendung zur Erfassung und Auswertung von Arbeitszeiten, Abwesenheiten und Ferienguthaben.

Es dient als Grundlage für:

- Architektur und Datenmodell (siehe [Fachliches Domänenmodell](02-domain-model.md) und [Architektur und Laufzeit](08-architecture-and-runtime.md))
- Geschäftsregeln und Berechnungen (siehe [Berechnungs- und Geschäftsregeln](03-business-rules.md))
- Geschäftsprozesse (siehe [Geschäftsprozesse und Workflows](04-business-processes.md))
- Schnittstellen und Benutzeroberfläche (siehe [REST-API-Spezifikation](07-rest-api.md) und [UI und Lokalisierung](06-ui-and-localization.md))
- Testplanung und Abnahme (siehe [Teststrategie und Abnahmekriterien](11-testing-and-acceptance.md))
- spätere Erweiterungen und Abgrenzungen

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

Details zur Autorisierung und Berechtigungsprüfung sind in [Sicherheit und Datenschutz](09-security-and-privacy.md) festgelegt.

## 4. Umfang

### 4.1 Implementierter Grundumfang (MVP-Kern)

Der funktionale Grundumfang enthält:

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

- mehrsprachige Benutzeroberfläche mit initialer Unterstützung für Deutsch (`de`) und Englisch (`en`) (siehe [UI und Lokalisierung](06-ui-and-localization.md))
- native PDF-Exporte für Monatsreport, Ferienübersicht und Abwesenheitsreport (siehe [Reports und Exporte](05-reports-and-exports.md))
- globale Unternehmensdarstellung mit Firmenname und optionalem Firmenlogo (siehe [UI und Lokalisierung](06-ui-and-localization.md) und [Reports und Exporte](05-reports-and-exports.md))
- Pikettdienst / Rufbereitschaft (`OnCallPeriod`) und Ausweisung von Einsätzen ausserhalb der regulären Bürozeiten (siehe [Fachliches Domänenmodell](02-domain-model.md) und [Reports und Exporte](05-reports-and-exports.md))

### 4.3 Spätere Ausbaustufen (Explizit Out of Scope)

Folgende Funktionen sind ausdrücklich **nicht** Bestandteil des aktuellen Implementierungsumfangs:

- Projekt-, Kunden-, Auftrags- oder Tätigkeitserfassung
- Zuschläge für Nacht-, Wochenend- oder Feiertagsarbeit
- native Excel-Exporte (`.xlsx`) (CSV und natives PDF sind Teil des aktuellen Umfangs)
- Dokumente zu Abwesenheiten, beispielsweise Uploads von Arztzeugnissen
- Benachrichtigungen und E-Mail-Erinnerungen
- Kalenderintegration (z. B. iCal, Exchange)
- Import-Werkzeuge aus bestehenden Altsystemen
- Mobile-optimierte Offline-Erfassung

## 5. Fachliche Grundsätze

1. **Historisierung:** Arbeitspläne, Pensen und Ansprüche werden mit einem Gültigkeitszeitraum gespeichert und nicht rückwirkend überschrieben.
2. **Nachvollziehbarkeit:** Änderungen an fachlich relevanten Daten werden revisionsfähig protokolliert.
3. **Berechnung statt Speicherung:** Abgeleitete Werte wie Tages-Istzeit und Monatssaldo werden aus den zugrunde liegenden Buchungen berechnet. Für abgeschlossene Perioden dürfen zusätzlich unveränderliche Berechnungsergebnisse (`calculationSnapshot`) gespeichert werden.
4. **Keine verdeckten Annahmen:** Rundung, Arbeitsunterbrüche, Feiertage und Abwesenheitsanrechnung sind konfigurierbar oder als explizite Geschäftsregel dokumentiert.
5. **Datensparsamkeit:** Auf allgemein sichtbaren Statusseiten wird kein sensibler Abwesenheitsgrund angezeigt.
6. **Zeitzonenfestigkeit:** Zeitpunkte werden eindeutig gespeichert; fachliche Kalendertage werden in der Zeitzone des Mitarbeiters beziehungsweise Standorts ausgewertet (Standardzeitzone: `Europe/Zurich`).
