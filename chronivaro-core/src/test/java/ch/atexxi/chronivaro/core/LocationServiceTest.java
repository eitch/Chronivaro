package ch.atexxi.chronivaro.core;

import ch.atexxi.chronivaro.core.service.CreateLocationService;
import ch.atexxi.chronivaro.core.service.RemoveLocationService;
import ch.atexxi.chronivaro.core.service.UpdateLocationService;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.privilege.model.Certificate;
import li.strolch.service.StringArgument;
import li.strolch.service.api.ServiceHandler;
import li.strolch.service.api.ServiceResult;
import li.strolch.testbase.runtime.RuntimeMock;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;
import static org.junit.Assert.*;

public class LocationServiceTest {

	private static RuntimeMock runtimeMock;
	private static Certificate certificate;

	@BeforeClass
	public static void beforeClass() {
		runtimeMock = new RuntimeMock().mockRuntime("target/" + LocationServiceTest.class.getSimpleName(),
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
	public void shouldCreateUpdateAndRemoveLocation() {
		ServiceHandler serviceHandler = runtimeMock.getServiceHandler();

		// Create
		CreateLocationService.LocationArgument createArg = new CreateLocationService.LocationArgument();
		createArg.name = "Test Location";
		createArg.timezone = "Europe/Zurich";
		createArg.holidayCalendarId = "cal1";

		ServiceResult createResult = serviceHandler.doService(certificate, new CreateLocationService(), createArg);
		assertTrue(createResult.getMessage(), createResult.isOk());

		String locationId;
		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			Resource location = tx
					.streamResources(TYPE_LOCATION)
					.filter(r -> r.getName().equals("Test Location"))
					.findFirst()
					.orElseThrow();
			locationId = location.getId();
			assertEquals("Test Location", location.getString(PARAM_NAME));
			assertEquals("Europe/Zurich", location.getString(PARAM_TIMEZONE));
			assertEquals("cal1", location.getString(BAG_RELATIONS, PARAM_HOLIDAY_CALENDAR));
		}

		// Update
		CreateLocationService.UpdateLocationArgument updateArg = new CreateLocationService.UpdateLocationArgument();
		updateArg.id = locationId;
		updateArg.name = "Updated Location";
		updateArg.timezone = "Europe/Zurich";
		updateArg.holidayCalendarId = "cal1";
		ServiceResult updateResult = serviceHandler.doService(certificate, new UpdateLocationService(), updateArg);
		assertTrue(updateResult.getMessage(), updateResult.isOk());

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			Resource location = tx.getResourceBy(TYPE_LOCATION, locationId, true);
			assertEquals("Updated Location", location.getString(PARAM_NAME));
		}

		// Remove
		ServiceResult removeResult = serviceHandler.doService(certificate, new RemoveLocationService(),
				new StringArgument(locationId));
		assertTrue(removeResult.getMessage(), removeResult.isOk());

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			assertNull(tx.getResourceBy(TYPE_LOCATION, locationId));
		}
	}
}
