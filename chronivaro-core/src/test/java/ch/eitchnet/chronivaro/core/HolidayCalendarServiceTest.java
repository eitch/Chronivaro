package ch.eitchnet.chronivaro.core;

import ch.eitchnet.chronivaro.core.service.CreateHolidayCalendarService;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.privilege.model.Certificate;
import li.strolch.service.api.ServiceHandler;
import li.strolch.service.api.ServiceResult;
import li.strolch.testbase.runtime.RuntimeMock;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static ch.eitchnet.chronivaro.core.model.ChronivaroConstants.PARAM_NAME;
import static ch.eitchnet.chronivaro.core.model.ChronivaroConstants.TYPE_HOLIDAY_CALENDAR;
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

		// Create
		CreateHolidayCalendarService.HolidayCalendarArgument createArg
				= new CreateHolidayCalendarService.HolidayCalendarArgument();
		createArg.name = "Test Calendar";

		ServiceResult createResult = serviceHandler.doService(certificate, new CreateHolidayCalendarService(),
				createArg);
		assertTrue(createResult.getMessage(), createResult.isOk());

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			Resource calendar = tx
					.streamResources(TYPE_HOLIDAY_CALENDAR)
					.filter(r -> r.getName().equals("Test Calendar"))
					.findFirst()
					.orElseThrow();
			assertEquals("Test Calendar", calendar.getString(PARAM_NAME));
		}
	}

	@Test
	public void shouldCreateHolidayCalendarWithoutId() {
		ServiceHandler serviceHandler = runtimeMock.getServiceHandler();

		// Create
		CreateHolidayCalendarService.HolidayCalendarArgument createArg
				= new CreateHolidayCalendarService.HolidayCalendarArgument();
		createArg.name = "Test Calendar 2";

		ServiceResult createResult = serviceHandler.doService(certificate, new CreateHolidayCalendarService(),
				createArg);
		assertTrue(createResult.getMessage(), createResult.isOk());

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			Resource calendar = tx
					.streamResources(TYPE_HOLIDAY_CALENDAR)
					.filter(r -> r.getName().equals("Test Calendar 2"))
					.findFirst()
					.orElseThrow();
			assertNotNull(calendar.getId());
			assertEquals("Test Calendar 2", calendar.getString(PARAM_NAME));
		}
	}
}
