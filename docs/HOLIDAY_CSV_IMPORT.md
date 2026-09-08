# Holiday Calendar CSV Import Specification

## Overview

Chronivaro supports importing public holidays from CSV files into configured holiday calendars. The CSV parser architecture is extensible and registered centrally in `HolidayCsvParserRegistry`, allowing multiple format specifications to be supported and selected via the user interface.

## Supported Formats

### 1. `fcal.ch DE`
The `fcal.ch DE` format is a semicolon-separated CSV format exported from fcal.ch for Swiss holidays with German descriptions.

- **Encoding**: UTF-8 (optional UTF-8 BOM is automatically stripped).
- **Delimiter**: Semicolon (`;`).
- **Quote Character**: Double quotes (`"`).
- **Header Line**: `"Datum";"Bezeichnung";"Wochentag";"Kalenderwoche"` (ignored during import).
- **Date Format**: `dd.MM.yyyy` (e.g. `01.01.2025`).
- **Default Credit Factor**: `1.0` (Full-day holiday credit).

#### Sample File

```csv
"Datum";"Bezeichnung";"Wochentag";"Kalenderwoche"
"01.01.2025";"Neujahr";"Mi.";"Woche 01"
"02.01.2025";"Berchtoldstag";"Do.";"Woche 01"
"18.04.2025";"Karfreitag";"Fr.";"Woche 16"
"21.04.2025";"Ostermontag";"Mo.";"Woche 17"
"29.05.2025";"Auffahrt";"Do.";"Woche 22"
"09.06.2025";"Pfingstmontag";"Mo.";"Woche 24"
"01.08.2025";"Nationalfeiertag Schweiz";"Fr.";"Woche 31"
"25.12.2025";"Weihnachten";"Do.";"Woche 52"
"26.12.2025";"Stephanstag";"Fr.";"Woche 52"
```

## REST API Endpoints

### 1. `GET /chronivaro/v1/admin/holiday-calendars/csv-formats`
Returns a list of supported CSV format descriptors.

**Response:**
```json
[
  {
    "id": "fcal.ch DE",
    "name": "fcal.ch DE"
  }
]
```

### 2. `POST /chronivaro/v1/admin/holiday-calendars/{id}/import-csv`
Imports holidays from raw CSV data using the designated format. Duplicate holiday dates already present in the target calendar or in the CSV payload are automatically skipped.

**Request Payload:**
```json
{
  "format": "fcal.ch DE",
  "csvData": "\"Datum\";\"Bezeichnung\";\"Wochentag\";\"Kalenderwoche\"\n\"01.01.2025\";\"Neujahr\";\"Mi.\";\"Woche 01\"\n"
}
```

**Response:**
Standard `ServiceResult` JSON with status, message, and import statistics.
