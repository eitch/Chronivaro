package ch.eitchnet.chronivaro.core;

import ch.eitchnet.chronivaro.core.model.*;
import ch.eitchnet.chronivaro.core.report.AbsenceReportItem;
import ch.eitchnet.chronivaro.core.report.PdfExportHelper;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import li.strolch.model.Resource;
import org.junit.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import static ch.eitchnet.chronivaro.core.model.ChronivaroConstants.*;
import static org.junit.Assert.*;

public class PdfExportHelperTest {

	private Resource createMockEmployee(String id, String name, String persNr, String teamId, String locationId) {
		Resource emp = new Resource(id, name, TYPE_EMPLOYEE);
		emp.setString(PARAM_PERSONAL_NUMBER, persNr);
		emp.relationsBag().setString(PARAM_PRIMARY_TEAM, teamId);
		emp.relationsBag().setString(PARAM_LOCATION, locationId);
		return emp;
	}

	private Resource createMockCompanyConfig(String companyName, String logo) {
		Resource config = new Resource("configuration", "Configuration", TYPE_GLOBAL_CONFIGURATION);
		if (companyName != null) {
			config.setString(PARAM_COMPANY_NAME, companyName);
		}
		if (logo != null) {
			config.setString(PARAM_COMPANY_LOGO, logo);
		}
		return config;
	}

	@Test
	public void shouldExportMonthReportToPdfInGerman() throws Exception {
		Resource emp = createMockEmployee("emp-001", "Max Muster", "1001", "dev-team", "zurich");
		Resource config = createMockCompanyConfig("Muster AG", null);

		List<DaySummary> days = new ArrayList<>();
		days.add(new DaySummary(
				LocalDate.of(2026, 8, 1),
				DayState.NOT_WORKING,
				"Weekend",
				0, 0, 0, 0,
				true,
				null,
				List.of(),
				List.of()
		));
		days.add(new DaySummary(
				LocalDate.of(2026, 8, 3),
				DayState.WORKING,
				"Worked",
				504, 480, 0, 0,
				false,
				WorkingLocation.OFFICE,
				List.of(),
				List.of()
		));
		days.add(new DaySummary(
				LocalDate.of(2026, 8, 4),
				DayState.NOT_WORKING,
				"Sick",
				504, 0, 0, 504,
				false,
				WorkingLocation.HOME_OFFICE,
				List.of(),
				List.of()
		));

		MonthSummary summary = new MonthSummary(
				"emp-001",
				YearMonth.of(2026, 8),
				10080,
				9500,
				504,
				0,
				0,
				0,
				504,
				-120,
				0,
				days
		);

		byte[] pdfBytes = PdfExportHelper.exportMonthReportToPdf(
				summary,
				STATE_OPEN,
				null,
				null,
				emp,
				config,
				"de"
		);

		assertNotNull(pdfBytes);
		assertTrue("PDF size must be > 1KB", pdfBytes.length > 1000);
		assertEquals('%', (char) pdfBytes[0]);
		assertEquals('P', (char) pdfBytes[1]);
		assertEquals('D', (char) pdfBytes[2]);
		assertEquals('F', (char) pdfBytes[3]);

		PdfReader reader = new PdfReader(pdfBytes);
		assertTrue(reader.getNumberOfPages() >= 1);

		PdfTextExtractor extractor = new PdfTextExtractor(reader);
		String text = extractor.getTextFromPage(1);

		assertTrue(text.contains("Monatsreport"));
		assertTrue(text.contains("Muster AG"));
		assertTrue(text.contains("Max Muster"));
		assertTrue(text.contains("1001"));
		assertTrue(text.contains("Zusammenfassung"));
		assertTrue(text.contains("Sollzeit"));
		assertTrue(text.contains("Istzeit"));
		reader.close();
	}

	@Test
	public void shouldExportMonthReportToPdfInEnglishWithApprovedState() throws Exception {
		Resource emp = createMockEmployee("emp-002", "Alice Smith", "1002", "sales-team", "geneva");
		Resource config = createMockCompanyConfig("Acme Global", "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==");

		MonthSummary summary = new MonthSummary(
				"emp-002",
				YearMonth.of(2026, 7),
				10000,
				10200,
				0,
				0,
				0,
				0,
				0,
				60,
				0,
				List.of(
						new DaySummary(
								LocalDate.of(2026, 7, 1),
								DayState.WORKING,
								"Worked",
								480, 510, 0, 0,
								false,
								WorkingLocation.OFFICE,
								List.of(),
								List.of()
						)
				)
		);

		ZonedDateTime approvalDate = ZonedDateTime.of(2026, 8, 2, 14, 30, 0, 0, ZoneId.of("Europe/Zurich"));
		byte[] pdfBytes = PdfExportHelper.exportMonthReportToPdf(
				summary,
				STATE_APPROVED,
				approvalDate,
				"boss_user",
				emp,
				config,
				"en"
		);

		assertNotNull(pdfBytes);
		assertTrue(pdfBytes.length > 1000);

		PdfReader reader = new PdfReader(pdfBytes);
		assertEquals(1, reader.getNumberOfPages());

		PdfTextExtractor extractor = new PdfTextExtractor(reader);
		String text = extractor.getTextFromPage(1);

		assertTrue(text.contains("Monthly Time Report"));
		assertTrue(text.contains("Acme Global"));
		assertTrue(text.contains("Alice Smith"));
		assertTrue(text.contains("boss_user"));
		assertTrue(text.contains("APPROVED"));
		assertTrue(text.contains("Monthly Summary"));
		assertTrue(text.contains("Target Time"));
		assertTrue(text.contains("Starting Balance"));
		reader.close();
	}

	@Test
	public void shouldExportVacationReportToPdf() throws Exception {
		Resource emp = createMockEmployee("emp-003", "Beat Keller", "1003", "ops-team", "bern");
		Resource config = createMockCompanyConfig("Schweiz AG", null);

		VacationAccountSummary summary = new VacationAccountSummary(
				"emp-003",
				2026,
				1200,  // carry over (2.5 days)
				12000, // annual (25 days)
				480,   // corrections (+1 day)
				4800,  // usage (10 days)
				8880   // remaining (18.5 days)
		);

		List<Resource> entries = new ArrayList<>();
		Resource entry1 = new Resource("vac-entry-1", "Carry Over", TYPE_VACATION_ACCOUNT_ENTRY);
		entry1.setDate(PARAM_DATE, ZonedDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneId.of("UTC")));
		entry1.setString(PARAM_VACATION_TYPE, "CARRY_OVER");
		entry1.setInteger(PARAM_VALUE, 1200);
		entry1.setString(PARAM_COMMENT, "Carry-over from 2025");
		entry1.setString(PARAM_CREATED_BY, "system");
		entries.add(entry1);

		Resource entry2 = new Resource("vac-entry-2", "Summer Vacation", TYPE_VACATION_ACCOUNT_ENTRY);
		entry2.setDate(PARAM_DATE, ZonedDateTime.of(2026, 7, 15, 0, 0, 0, 0, ZoneId.of("UTC")));
		entry2.setString(PARAM_VACATION_TYPE, "USAGE");
		entry2.setInteger(PARAM_VALUE, -2400);
		entry2.relationsBag().setString(PARAM_ABSENCE, "abs-12345");
		entry2.setString(PARAM_COMMENT, "Summer holidays");
		entry2.setString(PARAM_CREATED_BY, "emp-003");
		entries.add(entry2);

		byte[] pdfBytesDe = PdfExportHelper.exportVacationReportToPdf(
				summary,
				entries,
				emp,
				2026,
				config,
				"de"
		);

		assertNotNull(pdfBytesDe);
		assertTrue(pdfBytesDe.length > 1000);

		PdfReader readerDe = new PdfReader(pdfBytesDe);
		PdfTextExtractor extractorDe = new PdfTextExtractor(readerDe);
		String textDe = extractorDe.getTextFromPage(1);

		assertTrue(textDe.contains("Ferienübersicht"));
		assertTrue(textDe.contains("Beat Keller"));
		assertTrue(textDe.contains("Jahresanspruch"));
		assertTrue(textDe.contains("Übertrag Vorjahr"));
		assertTrue(textDe.contains("Restguthaben"));
		assertTrue(textDe.contains("Ferienbuchungen"));
		readerDe.close();

		byte[] pdfBytesEn = PdfExportHelper.exportVacationReportToPdf(
				summary,
				entries,
				emp,
				2026,
				config,
				"en"
		);
		PdfReader readerEn = new PdfReader(pdfBytesEn);
		PdfTextExtractor extractorEn = new PdfTextExtractor(readerEn);
		String textEn = extractorEn.getTextFromPage(1);

		assertTrue(textEn.contains("Vacation Summary"));
		assertTrue(textEn.contains("Annual Entitlement"));
		assertTrue(textEn.contains("Remaining Balance"));
		readerEn.close();
	}

	@Test
	public void shouldExportVacationReportToPdfForNewEmployeeWithEmptyOrNullSummary() throws Exception {
		Resource config = createMockCompanyConfig("Alpha Corp", null);
		Resource emp = createMockEmployee("newbie", "Newbie Employee", "1099", "team-1", "loc-1");

		// Test with null summary and empty entries
		byte[] pdfBytesNull = PdfExportHelper.exportVacationReportToPdf(
				null,
				List.of(),
				emp,
				2026,
				config,
				"de"
		);
		assertNotNull(pdfBytesNull);
		assertTrue(pdfBytesNull.length > 500);

		PdfReader readerNull = new PdfReader(pdfBytesNull);
		PdfTextExtractor extractorNull = new PdfTextExtractor(readerNull);
		String textNull = extractorNull.getTextFromPage(1);
		assertTrue(textNull.contains("Ferienübersicht"));
		assertTrue(textNull.contains("Newbie"));
		assertTrue(textNull.contains("00:00"));
		readerNull.close();

		// Test with zeroed summary
		VacationAccountSummary zeroSummary = new VacationAccountSummary("newbie", 2026, 0, 0, 0, 0, 0);
		byte[] pdfBytesZero = PdfExportHelper.exportVacationReportToPdf(
				zeroSummary,
				List.of(),
				emp,
				2026,
				config,
				"en"
		);
		assertNotNull(pdfBytesZero);
		assertTrue(pdfBytesZero.length > 500);

		PdfReader readerZero = new PdfReader(pdfBytesZero);
		PdfTextExtractor extractorZero = new PdfTextExtractor(readerZero);
		String textZero = extractorZero.getTextFromPage(1);
		assertTrue(textZero.contains("Vacation Summary"));
		assertTrue(textZero.contains("Newbie"));
		assertTrue(textZero.contains("00:00"));
		readerZero.close();
	}

	@Test
	public void shouldExportAbsenceReportToPdf() throws Exception {
		Resource config = createMockCompanyConfig("Alpha Corp", null);

		List<AbsenceReportItem> items = List.of(
				new AbsenceReportItem(
						"abs-1",
						"emp-10",
						"Clara Oswald",
						"VACATION",
						"Ferien",
						LocalDate.of(2026, 8, 10),
						LocalDate.of(2026, 8, 14),
						"FULL_DAY",
						"FULL",
						2400,
						"APPROVED",
						true,
						null,
						ZonedDateTime.now(),
						ZonedDateTime.now(),
						"manager_user"
				),
				new AbsenceReportItem(
						"abs-2",
						"emp-11",
						"David Tennant",
						"SICKNESS",
						"Krankheit",
						LocalDate.of(2026, 8, 18),
						LocalDate.of(2026, 8, 18),
						"HALF_DAY",
						"MORNING",
						240,
						"APPROVED",
						true,
						null,
						ZonedDateTime.now(),
						ZonedDateTime.now(),
						"hr_user"
				)
		);

		byte[] pdfBytes = PdfExportHelper.exportAbsenceReportToPdf(
				items,
				"Engineering",
				null,
				LocalDate.of(2026, 8, 1),
				LocalDate.of(2026, 8, 31),
				config,
				"en"
		);

		assertNotNull(pdfBytes);
		assertTrue(pdfBytes.length > 1000);

		PdfReader reader = new PdfReader(pdfBytes);
		PdfTextExtractor extractor = new PdfTextExtractor(reader);
		String text = extractor.getTextFromPage(1);

		assertTrue(text.contains("Absence Report"));
		assertTrue(text.contains("Engineering"));
		assertTrue(text.contains("Clara Oswald"));
		assertTrue(text.contains("David Tennant"));
		assertTrue(text.contains("Ferien"));
		assertTrue(text.contains("Krankheit"));
		reader.close();
	}

	@Test
	public void shouldHandleLargeMultiPageAbsenceReportAndInvalidLogo() throws Exception {
		Resource config = createMockCompanyConfig("Enterprise Corp", "invalid-non-existent-logo-format-that-should-not-crash");

		List<AbsenceReportItem> items = new ArrayList<>();
		for (int i = 1; i <= 60; i++) {
			items.add(new AbsenceReportItem(
					"abs-" + i,
					"emp-" + (i % 10),
					"Employee Number " + i,
					"VACATION",
					"Ferien",
					LocalDate.of(2026, 8, 1).plusDays(i % 25),
					LocalDate.of(2026, 8, 2).plusDays(i % 25),
					"FULL_DAY",
					"FULL",
					480,
					"APPROVED",
					true,
					null,
					ZonedDateTime.now(),
					ZonedDateTime.now(),
					"supervisor"
			));
		}

		byte[] pdfBytes = PdfExportHelper.exportAbsenceReportToPdf(
				items,
				"All Teams",
				null,
				LocalDate.of(2026, 8, 1),
				LocalDate.of(2026, 8, 31),
				config,
				"de"
		);

		assertNotNull(pdfBytes);
		PdfReader reader = new PdfReader(pdfBytes);
		assertTrue("Multi-page report should generate >= 2 pages", reader.getNumberOfPages() >= 2);

		PdfTextExtractor extractor = new PdfTextExtractor(reader);
		String page1 = extractor.getTextFromPage(1);
		String page2 = extractor.getTextFromPage(2);

		assertTrue(page1.contains("Abwesenheitsreport"));
		assertTrue(page1.contains("Enterprise Corp"));
		assertTrue(page2.contains("Employee Number"));
		reader.close();
	}

	@Test
	public void shouldFormatDurationAndFileNamesCorrectly() {
		assertEquals("00:00", PdfExportHelper.formatDuration(0));
		assertEquals("08:30", PdfExportHelper.formatDuration(510));
		assertEquals("-02:15", PdfExportHelper.formatDuration(-135));

		assertEquals("month-report-emp-01-2026-08.pdf", PdfExportHelper.getMonthReportPdfFileName("emp-01", YearMonth.of(2026, 8)));
		assertEquals("vacation-report-emp-01-2026.pdf", PdfExportHelper.getVacationReportPdfFileName("emp-01", 2026));
		assertEquals("absence-report-teamA-2026-08-01_2026-08-31.pdf",
				PdfExportHelper.getAbsenceReportPdfFileName("teamA", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)));
	}
}
