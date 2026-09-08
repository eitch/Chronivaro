package ch.eitchnet.chronivaro.core.holiday;

import org.junit.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class FcalChDeHolidayParserTest {

	private static final String CSV_CONTENT = """
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
			"01.01.2026";"Neujahr";"Do.";"Woche 01"
			"02.01.2026";"Berchtoldstag";"Fr.";"Woche 01"
			"03.04.2026";"Karfreitag";"Fr.";"Woche 14"
			"06.04.2026";"Ostermontag";"Mo.";"Woche 15"
			"14.05.2026";"Auffahrt";"Do.";"Woche 20"
			"25.05.2026";"Pfingstmontag";"Mo.";"Woche 22"
			"01.08.2026";"Nationalfeiertag Schweiz";"Sa.";"Woche 31"
			"25.12.2026";"Weihnachten";"Fr.";"Woche 52"
			"26.12.2026";"Stephanstag";"Sa.";"Woche 52"
			"01.01.2027";"Neujahr";"Fr.";"Woche 53"
			"02.01.2027";"Berchtoldstag";"Sa.";"Woche 53"
			"26.03.2027";"Karfreitag";"Fr.";"Woche 12"
			"29.03.2027";"Ostermontag";"Mo.";"Woche 13"
			"06.05.2027";"Auffahrt";"Do.";"Woche 18"
			"17.05.2027";"Pfingstmontag";"Mo.";"Woche 20"
			"01.08.2027";"Nationalfeiertag Schweiz";"So.";"Woche 30"
			"25.12.2027";"Weihnachten";"Sa.";"Woche 51"
			"26.12.2027";"Stephanstag";"So.";"Woche 51"
			""";

	@Test
	public void testParseFcalChDe() {
		FcalChDeHolidayParser parser = new FcalChDeHolidayParser();
		List<ParsedHoliday> holidays = parser.parse(CSV_CONTENT);

		assertNotNull(holidays);
		assertEquals(27, holidays.size());

		ParsedHoliday first = holidays.get(0);
		assertEquals(LocalDate.of(2025, 1, 1), first.date());
		assertEquals("Neujahr", first.name());
		assertEquals(1.0, first.creditFactor(), 0.001);

		ParsedHoliday last = holidays.get(26);
		assertEquals(LocalDate.of(2027, 12, 26), last.date());
		assertEquals("Stephanstag", last.name());
		assertEquals(1.0, last.creditFactor(), 0.001);
	}

	@Test
	public void testParseWithBomAndEmptyLines() {
		String content = "\uFEFF\"Datum\";\"Bezeichnung\";\"Wochentag\";\"Kalenderwoche\"\r\n\r\n\"01.01.2025\";\"Neujahr\";\"Mi.\";\"Woche 01\"\r\n";
		FcalChDeHolidayParser parser = new FcalChDeHolidayParser();
		List<ParsedHoliday> holidays = parser.parse(content);

		assertNotNull(holidays);
		assertEquals(1, holidays.size());
		assertEquals(LocalDate.of(2025, 1, 1), holidays.get(0).date());
		assertEquals("Neujahr", holidays.get(0).name());
	}
}
