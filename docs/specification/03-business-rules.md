# Chronivaro – Berechnungs- und Geschäftsregeln

Dieses Dokument enthält alle verbindlichen mathematischen und deterministischen Berechnungsregeln, Zeitlogiken, Ferienberechnungen und Validierungsregeln für Chronivaro.

## 1. Zeiteinheiten und Berechnungsbasis

- **Ganzzahlige Minuten:** Sämtliche internen Zeit- und Saldoberechnungen werden mit ganzzahligen Minuten (`int` / `long`) durchgeführt.
- **Rundung von Zwischenergebnissen:** Anteilig berechnete Zeiten und Ansprüche werden mit voller Genauigkeit berechnet und kaufmännisch auf die nächste ganze Minute gerundet.
- **Anzeigeformate:** Die Benutzeroberfläche und Exporte dürfen Minutenwerte formatiert als `HH:mm` (z. B. `08:15`) oder als Dezimalstunden (z. B. `8.25 h`) darstellen.

---

## 2. Sollzeit-Berechnung

### 2.1 Sollzeit eines Tages

Die tägliche Sollzeit ergibt sich aus der zum Kalendertag aktiven `EmploymentScheduleVersion`:

```text
Sollzeit = Arbeitsplan-Minuten des jeweiligen Wochentags
```

### 2.2 Anpassungen und Sonderfälle

1. **Eintritt unter dem Monat und operativer Erfassungsstart (`validFrom`):** Für alle Tage vor dem Beginn der ersten Arbeitsplanversion (`EmploymentScheduleVersion.validFrom`) gilt eine Sollzeit von `0` Minuten. Liegt das vertragliche Eintrittsdatum (`entryDate`) in der Vergangenheit vor dem Start der Zeiterfassung in Chronivaro, werden vor `validFrom` keine Sollminuten berechnet. Diese Tage werden als inaktiv gewertet und erzeugen keine Warnungen über fehlende Buchungen.
2. **Austritt unter dem Monat (`exitDate`):** Für alle Tage nach dem Austrittsdatum gilt eine Sollzeit von `0` Minuten.
3. **Gesetzliche und konfigurierte Feiertage:** 
   - Ganzer Feiertag (`creditFactor = 1.0`): Reduktion der Sollzeit bzw. Feiertagsgutschrift in voller Höhe der Arbeitsplan-Sollzeit dieses Tages.
   - Halber Feiertag (`creditFactor = 0.5`): Reduktion der Sollzeit bzw. Feiertagsgutschrift im Umfang von 50 % der Arbeitsplan-Sollzeit.
4. **Unbezahlte Abwesenheiten:** Die Sollzeit bleibt für die statistische Ausweisung erhalten, wird aber bei der anrechenbaren Zeit nicht als erfüllt angerechnet.
5. **Bezahlte anrechenbare Abwesenheiten:** Werden als Abwesenheitsgutschrift in Höhe der ausgefallenen Sollzeit gewertet.

---

## 3. Ist-Arbeitszeit und Pausen

### 3.1 Ist-Arbeitszeit

```text
Ist-Arbeitszeit = Summe der Dauer aller abgeschlossenen WorkEntry-Intervalle des Tages
```

### 3.2 Pausen- und Unterbruchsregelung

- Pausen werden **weder** als eigene Buchung erfasst **noch** automatisch pauschal abgezogen.
- Ein Arbeitsunterbruch ergibt sich ausschliesslich aus der zeitlichen Lücke zwischen zwei aufeinanderfolgenden `WorkEntry`-Blöcken.
- Chronivaro erzwingt keine automatischen Pausenabzüge und enthält keine fest codierten Grenzwerte.
- Reports stellen Beginn, Ende und Dauer der Arbeitsblöcke sowie die Unterbrüche dazwischen transparent dar, damit Vorgesetzte die Einhaltung gesetzlicher Ruhezeiten beurteilen können.

---

## 4. Anrechenbare Arbeitszeit und Saldi

### 4.1 Anrechenbare Arbeitszeit

```text
Anrechenbare Zeit = Ist-Arbeitszeit
                    + bezahlte anrechenbare Abwesenheitsgutschrift
                    + Feiertagsgutschrift
```

### 4.2 Tagessaldo

```text
Tagessaldo = Anrechenbare Zeit - Sollzeit
```

### 4.3 Periodensaldo und Endsaldo

#### 4.3.1 Grundlegende Definitionen
```text
Periodensaldo (Vollmonat) = Summe aller Tagessaldi innerhalb der Monatsperiode
Endsaldo (Vollmonat)       = Anfangssaldo + Periodensaldo (Vollmonat) + manuelle Korrekturen
```

- **Anfangssaldo:** Entspricht dem `Endsaldo` der vorangegangenen Monatsperiode. Bei der Berechnung traversiert die Saldenermittlung zurück bis zur frühesten Periode oder stoppt sofort, sobald eine genehmigte oder gesperrte Periode (`APPROVED`/`LOCKED`) mit einem gespeicherten `calculationSnapshot` angetroffen wird.
- **Anfangssaldo beim Onboarding (Baseline-Periode):** Wurde beim Onboarding ein Überzeit-/Unterzeitsaldo (`initialOvertimeMinutes`) erfasst, existiert für den Vormonat von `validFrom` eine gesperrte Baseline-`TimePeriod` mit `endBalanceMinutes = initialOvertimeMinutes`. Der Anfangssaldo des ersten aktiven Erfassungsmonats übernimmt exakt diesen Wert, ohne weiter in die Vergangenheit zurückzurechnen.
- **Zulässigkeit negativer Zeitsaldi:** Negative Zeitsaldi sind standardmässig zulässig.

#### 4.3.2 Saldo per Vortag für laufende (in-progress) Perioden
Für die **aktuelle, laufende Erfassungsperiode** (`yearMonth == aktueller Kalendermonat`) wird zur Vermeidung künstlicher Morgendefizite (z. B. scheinbarer 8-Stunden-Rückstand am frühen Morgen des aktuellen Arbeitstages) die operative Saldoberechnung nach dem Schweizer HR-Standard **per Vortag** (`date < heute`) ermittelt:

$$\text{Sollzeit (per Vortag)} = \sum_{d \in \text{Monat}, d < \text{heute}} \text{Sollzeit}(d)$$
$$\text{Anrechenbare Zeit (per Vortag)} = \sum_{d \in \text{Monat}, d < \text{heute}} \text{Anrechenbare Zeit}(d)$$
$$\text{Periodensaldo (per Vortag)} = \text{Anrechenbare Zeit (per Vortag)} - \text{Sollzeit (per Vortag)}$$
$$\text{Endsaldo (per Vortag)} = \text{Anfangssaldo} + \text{Periodensaldo (per Vortag)} + \text{manuelle Korrekturen}$$

**Regeln zur Stichtagsabgrenzung:**
1. **Laufender Monat (`yearMonth == YearMonth.now(zone)`):** 
   - Sämtliche Tage vor dem heutigen Kalendertag (`date < heute`) fliessen in den Saldo per Vortag ein.
   - Am ersten Tag des Monats (`heute == 1. des Monats`) liegen 0 Tage vor heute; der Periodensaldo per Vortag beträgt `0 min`, und der Endsaldo per Vortag entspricht exakt dem `Anfangssaldo`.
   - Der heutige Arbeitstag (`heute`) wird im Dashboard/Tagesübersicht in Echtzeit separat als aktiver Erfassungstag ausgewiesen (`Ist: X h / Soll: Y h`), ohne den kumulierten Periodensaldo zu verzerren.
2. **Abgeschlossene / vergangene Perioden (`yearMonth < YearMonth.now(zone)` oder Status `APPROVED`/`LOCKED`):**
   - Alle Tage des Monats liegen in der Vergangenheit ($d < \text{heute}$). Der Saldo per Vortag ist identisch mit dem Vollmonatssaldo.
3. **Zukünftige Perioden (`yearMonth > YearMonth.now(zone)`):**
   - Es liegen noch keine Tage vor heute ($d < \text{heute}$); der Saldo per Vortag ist `0 min`.
4. **Bezeichnung in Benutzeroberfläche und Berichten:**
   - In der Monatsübersicht ("Meine Perioden"), in Team-Übersichten für Vorgesetzte und HR sowie in Berichten wird der Saldo für laufende Monate eindeutig als **"Saldo (per Vortag)"** / **"Balance (as of yesterday)"** ausgewiesen.

---

## 5. Rundungsregeln

- Im Standard-Produktumfang gilt **keine Rundung** (exakte minutengenaue Erfassung und Abrechnung).
- Falls Rundungsregeln (z. B. 5-Minuten-Rundung) über Systemeinstellungen aktiviert werden, müssen diese transparent, konfigurierbar und im Audit-Log nachvollziehbar sein.

---

## 6. Automatisierte Ferienanspruchsregelung

Das Ferienguthaben wird als unveränderliches Journal (`VacationAccountEntry`) geführt. Die automatisierte Berechnung und Verbuchung folgt diesen verbindlichen Regeln:

### 6.1 Grundparameter

- **Standardanspruch:** Der jährliche Standard-Ferienanspruch für ein 100-%-Pensum beträgt `25` Ferientage pro Anspruchsjahr.
- **Umrechnungsfaktor:** Ein voller Ferientag entspricht global konfigurierbar `480` Minuten (8 Stunden). Dieser Wert ist unabhängig von der individuellen täglichen Sollzeit eines Mitarbeiters.
- **Anspruchsjahr:** Entspricht dem Kalenderjahr (`1. Januar` bis `31. Dezember`).
- **Initialbuchung:** Für bestehende Mitarbeitende wird der Jahresanspruch per `1. Januar` als `ENTITLEMENT`-Journaleintrag gutgeschrieben.

### 6.2 Eintritt unter dem Jahr und Onboarding mit Altdaten

1. **Regulärer Eintritt:** Tritt ein Mitarbeiter während des laufenden Jahres ein (`entryDate` im selben Jahr wie der Erfassungsstart `scheduleValidFrom`), wird der anteilige Ferienanspruch ab `validFrom` bis Jahresende berechnet und per `validFrom` als `ENTITLEMENT` gebucht:

$$\text{Anspruch (Minuten)} = \text{round}\left( 25 \times 480 \times \frac{\text{Aktive Kalendertage ab } validFrom}{\text{Gesamttage des Kalenderjahres}} \times \frac{\text{employmentPercentage}}{100} \right)$$

2. **Onboarding mit historischem Eintrittsdatum (`entryDate` in Vorjahren):** 
   - Bei Mitarbeitenden, deren vertraglicher Eintritt vor dem aktiven Zeiterfassungsjahr liegt (`entryDate < Jahresbeginn von validFrom`), wird der anteilige Ferienanspruch für das Jahr des operativen Erfassungsstarts (`validFrom.getYear()`) berechnet und gutgeschrieben (pro-rata ab `validFrom` bis Jahresende). Vergangene, in Chronivaro nicht aktiv geführte Vorjahre erhalten keine rückwirkenden Buchungen.
   - **Initialer Ferienübertrag (`initialVacationDays`):** Werden beim Onboarding bestehende Restferientage aus dem Altsystem angegeben, werden diese als `CARRY_OVER`-Journaleintrag für das aktive Erfassungsjahr per `validFrom` gutgeschrieben ($1 \text{ Tag} = 480 \text{ Minuten}$). Dieser Übertrag steht sofort für künftige Ferienbezüge zur Verfügung.

### 6.3 Pensums- und Beschäftigungsgradanpassungen

- Der Ferienanspruch bei Teilzeit wird proportional zum Beschäftigungsgrad berechnet.
- Ändert sich der Beschäftigungsgrad während des Jahres oder wird ein Austrittsdatum (`exitDate`) gesetzt oder geändert, erfolgt eine automatisierte Neuberechnung des anteiligen Jahresanspruchs mit entsprechender `CORRECTION`-Buchung im Journal.
- Eine doppelte Initialbuchung bei Mitarbeitererstellung wird verhindert (initialer Anspruch ausschliesslich als `ENTITLEMENT`).

### 6.4 Keine Alters- oder Dienstaltersstaffelung

- Es gelten keine alters- oder dienstzeitabhängigen Sonderansprüche im Standard.

### 6.5 Übertrag und Verfall

- Nicht bezogene Ferientage werden beim Jahreswechsel ohne Begrenzung als `CARRY_OVER` ins Folgejahr übertragen.
- Positive `CORRECTION`-Guthaben zählen zum übertragbaren Bestand.
- Übertragene Ferien verfallen im Standard nicht (`EXPIRY` nur bei expliziter administrativer Buchung).
- **FIFO-Verbrauch:** Beim Ferienbezug wird stets das älteste verfügbare Guthaben zuerst verbraucht.

### 6.6 Ferienbezug und Verbot negativer Feriensaldi

- Nur der vorkonfigurierte Abwesenheitstyp mit dem Code `VACATION` erzeugt `USAGE`-Buchungen auf dem Ferienkonto.
- **Verbot negativer Saldi:** Die Genehmigung eines Ferienantrags wird blockiert, wenn der Bezug das verfügbare Ferienguthaben übersteigen würde. Negative Feriensaldi sind unzulässig.

### 6.7 Manuelle Ferienkorrekturen

- Personaladministration und Vorgesetzte können manuelle Korrekturbuchungen (`entryType = CORRECTION`) vornehmen.
- Jede manuelle Korrektur erfordert zwingend einen aussagekräftigen Begründungskommentar (`comment`).

---

## 7. Berechnungsregeln für vergessene Timer (Edge Cases)

Wird ein laufender `WorkEntry` gestoppt, dessen Enddatum mehr als einen Tag nach dem Startdatum liegt (vergessener Timer), greift folgende automatische Capping-Logik:

### 7.1 Capping-Formel

```text
Endzeit = Startzeit + max(0, Sollzeit_des_Tages - bisherige_Istzeit_des_Tages)
```

- Die berechnete Endzeit wird auf **maximal 24:00 Uhr des Starttages** begrenzt.
- Jegliche über das Tagessoll hinausgehende Zeit wird verworfen.
- Der `WorkEntry` erhält automatisch den Kommentar:  
  `"Timer vergessen - auf Sollzeit begrenzt"`

### 7.2 Edge Cases

1. **Sollzeit am Starttag bereits erreicht:** Hat der Mitarbeiter vor dem vergessenen Timer bereits das Tagessoll erfüllt, wird die Endzeit des vergessenen Timers gleich der Startzeit gesetzt (Dauer = `0` Minuten).
2. **Startzeit nach Sollzeiterreichung:** Wird ein Timer gestartet, nachdem das Soll bereits erreicht war, wird die Dauer ebenfalls auf `0` gesetzt.
3. **Sollzeit-Erreichung nach 24:00 Uhr:** Reicht die Zeit bis 24:00 Uhr nicht aus, um das Soll zu erfüllen, endet die Buchung strikt um 24:00 Uhr.
4. **Mehrfache vergessene Timer:** Mehrere vergessene Timer an einem Tag füllen nacheinander die Sollzeit auf, bis das Tagessoll erreicht ist.

---

## 8. Anwesenheitsstatus-Logik

Die Anwesenheitsseite ermittelt für jeden aktiven Mitarbeiter einen binären Primärstatus:

| Status | Farbe | Bedingung |
|---|---|---|
| `WORKING` | Grün | Es existiert mindestens ein laufender `WorkEntry` ohne Endzeit. |
| `NOT_WORKING` | Rot | Es existiert kein laufender `WorkEntry`. |

- Eine Pause/Unterbrechung zwischen zwei Zeitblöcken wird als `NOT_WORKING` dargestellt.
- Zusatzinformationen (z. B. `HOME_OFFICE`, geplante Abwesenheit) ändern den binären Primärstatus nicht.
- Datenschutzregel: Abwesenheitsgründe (wie Krankheit oder Unfall) werden auf der öffentlichen Statusanzeige niemals angezeigt.

---

## 9. Validierungsregeln

### 9.1 Blockierende Fehler (Validierungsfehler)

Folgende Zustände sind unzulässig und führen zur Ablehnung der Aktion mit standardisiertem Fehlercode (siehe [REST-API-Spezifikation](07-rest-api.md)):

1. **Überlappende Zeitbuchungen:** `WorkEntry`-Buchungen desselben Mitarbeiters dürfen sich zeitlich nicht überschneiden.
2. **Mehrere offene Timer:** Pro Mitarbeiter darf höchstens ein `WorkEntry` ohne Endzeit existieren.
3. **Invertierte Zeiträume:** Das Ende einer Buchung muss chronologisch nach dem Start liegen.
4. **Überlappende Arbeitspläne:** `EmploymentScheduleVersion`-Einträge desselben Mitarbeiters dürfen sich nicht überschneiden.
5. **Fehlender Pflichtkommentar:** Abwesenheiten bei Typen mit `commentRequired = true` oder manuelle Ferienkorrekturen ohne Kommentar.
6. **Ungültige Abwesenheitsdauer:** Erfassung einer Dauerart, die im `AbsenceType` (`allowedDurations`) nicht zugelassen ist.
7. **Unberechtigter Zugriff:** Schreib- oder Leseversuche ausserhalb der eigenen Berechtigungs- oder Teamgrenzen.
8. **Modifikation gesperrter Perioden:** Änderungen an Zeitbuchungen in Perioden mit Status `APPROVED` oder `LOCKED` (erfordern vorherige Wiedereröffnung).
9. **Unzureichendes Ferienguthaben:** Ferienanträge oder -genehmigungen, die zu einem negativen Feriensaldo führen würden.
10. **Fehlender Arbeitsplan:** Fehlende `EmploymentScheduleVersion` für einen relevanten Arbeitstag.

### 9.2 Nicht-blockierende Warnungen

Warnungen informieren den Benutzer und Vorgesetzten in Übersichten und Reports, blockieren das Speichern von Buchungen jedoch nicht:

- Soll-Arbeitstag ohne erfasste Buchung und ohne Abwesenheit
- Ungewöhnlich lange tägliche Arbeitszeit
- Arbeit über Mitternacht (wird automatisch in zwei Buchungen aufgeteilt)
- Arbeit an einem Feiertag oder an einem arbeitsfreien Tag
- Negativer Zeitsaldo (Überstunden-Minus)
