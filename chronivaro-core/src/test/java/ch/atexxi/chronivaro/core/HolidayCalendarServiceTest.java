package ch.atexxi.chronivaro.core;

import ch.atexxi.chronivaro.core.service.CreateHolidayCalendarService;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.privilege.model.Certificate;
import li.strolch.service.api.ServiceHandler;
import li.strolch.service.api.ServiceResult;
import li.strolch.testbase.runtime.RuntimeMock;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;
import static org.junit.Assert.*;

public class HolidayCalendarServiceTest {

	private static RuntimeMock runtimeMock;
	private static Certificate certificate;

	@BeforeClass
	public static void beforeClass() {
		runtimeMock = new RuntimeMock().mockRuntime("target/" + HolidayCalendarServiceTest.class.getSimpleName(),
				"src/test/resources");
		runtimeMock.startContainer();
		certificate = runtimeMock.login("admin", "admin");
	}

	@AfterClass
	public static void afterClass() {
		if (runtimeMock != null)
			runtimeMock.destroyRuntime();
	}

	@Test
	public void shouldCreateHolidayCalendar() {
		ServiceHandler serviceHandler = runtimeMock.getServiceHandler();

		String calendarId = "test-calendar";

		// Create
		CreateHolidayCalendarService.HolidayCalendarArgument createArg = new CreateHolidayCalendarService.HolidayCalendarArgument();
		createArg.id = calendarId;
		createArg.name = "Test Calendar";

		ServiceResult createResult = serviceHandler.doService(certificate, new CreateHolidayCalendarService(), createArg);
		assertTrue(createResult.getMessage(), createResult.isOk());

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			Resource calendar = tx.getResourceBy(TYPE_HOLIDAY_CALENDAR, calendarId, true);
			assertEquals("Test Calendar", calendar.getString(PARAM_NAME));
		}
	}
}
