package ch.eitchnet.chronivaro.core;

import ch.eitchnet.chronivaro.core.holiday.FcalChDeHolidayParser;
import ch.eitchnet.chronivaro.core.service.CreateHolidayCalendarService;
import ch.eitchnet.chronivaro.core.service.ImportHolidayCsvService;
import ch.eitchnet.chronivaro.core.model.ChronivaroConstants;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.privilege.model.Certificate;
import li.strolch.service.api.ServiceResult;
import li.strolch.testbase.runtime.RuntimeMock;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;

import static ch.eitchnet.chronivaro.core.model.ChronivaroConstants.PARAM_HOLIDAY_CALENDAR;
import static ch.eitchnet.chronivaro.core.model.ChronivaroConstants.TYPE_HOLIDAY;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ImportHolidayCsvServiceTest {

	private static final String TARGET_PATH = "target/" + ImportHolidayCsvServiceTest.class.getSimpleName();
	private static final String SOURCE_PATH = "src/test/resources";
	private static RuntimeMock runtimeMock;

	private static final String CSV_SAMPLE = """
			"Datum";"Bezeichnung";"Wochentag";"Kalenderwoche"
			"01.01.2025";"Neujahr";"Mi.";"Woche 01"
			"02.01.2025";"Berchtoldstag";"Do.";"Woche 01"
			"18.04.2025";"Karfreitag";"Fr.";"Woche 16"
			""";

	@BeforeClass
	public static void beforeClass() {
		runtimeMock = new RuntimeMock().mockRuntime(TARGET_PATH, SOURCE_PATH);
		runtimeMock.startContainer();
	}

	@AfterClass
	public static void afterClass() {
		if (runtimeMock != null)
			runtimeMock.destroyRuntime();
	}

	@Test
	public void testImportHolidayCsv() {
		Certificate cert = runtimeMock.login("admin", "admin");

		// Create a holiday calendar first
		CreateHolidayCalendarService createCalService = new CreateHolidayCalendarService();
		CreateHolidayCalendarService.HolidayCalendarArgument createCalArg = new CreateHolidayCalendarService.HolidayCalendarArgument();
		createCalArg.name = "Test Calendar";
		li.strolch.service.StringResult calResult = (li.strolch.service.StringResult) runtimeMock.getServiceHandler().doService(cert, createCalService, createCalArg);
		assertTrue(calResult.isOk());

		String calId = calResult.getValue();

		// Import CSV
		ImportHolidayCsvService importService = new ImportHolidayCsvService();
		ImportHolidayCsvService.ImportHolidayCsvArgument importArg = new ImportHolidayCsvService.ImportHolidayCsvArgument();
		importArg.holidayCalendarId = calId;
		importArg.format = FcalChDeHolidayParser.FORMAT_ID;
		importArg.csvData = CSV_SAMPLE;

		ServiceResult rawImportResult = runtimeMock.getServiceHandler().doService(cert, importService, importArg);
		assertTrue(rawImportResult.getMessage(), rawImportResult.isOk());
		ImportHolidayCsvService.ImportHolidayCsvResult importResult = (ImportHolidayCsvService.ImportHolidayCsvResult) rawImportResult;
		assertEquals(3, importResult.getImportedCount());
		assertEquals(0, importResult.getSkippedCount());

		// Verify holidays exist in transaction
		try (StrolchTransaction tx = runtimeMock.openUserTx(cert, true)) {
			List<Resource> holidays = tx.streamResources(TYPE_HOLIDAY)
					.filter(h -> h.hasRelation(PARAM_HOLIDAY_CALENDAR) && h
							.getRelationId(PARAM_HOLIDAY_CALENDAR)
							.equals(calId))
					.toList();
			assertEquals(3, holidays.size());
		}

		// Re-import same CSV -> all should be skipped
		ImportHolidayCsvService importService2 = new ImportHolidayCsvService();
		ImportHolidayCsvService.ImportHolidayCsvArgument importArg2 = new ImportHolidayCsvService.ImportHolidayCsvArgument();
		importArg2.holidayCalendarId = calId;
		importArg2.format = FcalChDeHolidayParser.FORMAT_ID;
		importArg2.csvData = CSV_SAMPLE;

		ServiceResult rawReimportResult = runtimeMock.getServiceHandler().doService(cert, importService2, importArg2);
		assertTrue(rawReimportResult.getMessage(), rawReimportResult.isOk());
		ImportHolidayCsvService.ImportHolidayCsvResult reimportResult = (ImportHolidayCsvService.ImportHolidayCsvResult) rawReimportResult;
		assertEquals(0, reimportResult.getImportedCount());
		assertEquals(3, reimportResult.getSkippedCount());
	}
}
