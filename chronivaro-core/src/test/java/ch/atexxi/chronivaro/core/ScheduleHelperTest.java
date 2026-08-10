package ch.atexxi.chronivaro.core;

import ch.atexxi.chronivaro.core.model.HolidayHelper;
import ch.atexxi.chronivaro.core.model.ScheduleHelper;
import li.strolch.model.ParameterBag;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.privilege.model.Certificate;
import li.strolch.testbase.runtime.RuntimeMock;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.time.LocalDate;
import java.time.ZonedDateTime;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;
import static org.junit.Assert.assertEquals;

public class ScheduleHelperTest {

	private static RuntimeMock runtimeMock;
	private static Certificate certificate;

	@BeforeClass
	public static void beforeClass() {
		runtimeMock = new RuntimeMock().mockRuntime("target/" + ScheduleHelperTest.class.getSimpleName(),
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
	public void shouldCalculateTargetMinutes() {
		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, false)) {
			// Create Location
			Resource location = new Resource("loc1", "Location 1", TYPE_LOCATION);
			location.addParameterBag(new ParameterBag(BAG_RELATIONS, "Relations", "Relations"));
			location.setString(BAG_RELATIONS, TYPE_HOLIDAY_CALENDAR, "cal1");
			tx.add(location);

			// Create Employee
			Resource employee = new Resource("emp1", "John Doe", TYPE_EMPLOYEE);
			employee.addParameterBag(new ParameterBag(BAG_PARAMETERS, "Parameters", "Parameters"));
			employee.addParameterBag(new ParameterBag(BAG_RELATIONS, "Relations", "Relations"));
			employee.setBoolean(PARAM_ACTIVE, true);
			employee.setDate(PARAM_JOIN_DATE, ZonedDateTime.parse("2026-01-01T00:00:00Z"));
			employee.setString(BAG_RELATIONS, TYPE_LOCATION, "loc1");
			tx.add(employee);

			// Create Schedule Version
			Resource schedule = new Resource("v1", "Schedule V1", TYPE_EMPLOYMENT_SCHEDULE_VERSION);
			schedule.addParameterBag(new ParameterBag(BAG_PARAMETERS, "Parameters", "Parameters"));
			schedule.addParameterBag(new ParameterBag(BAG_RELATIONS, "Relations", "Relations"));
			schedule.setString(BAG_RELATIONS, TYPE_EMPLOYEE, "emp1");
			schedule.setDate(PARAM_VALID_FROM, ZonedDateTime.parse("2026-01-01T00:00:00Z"));
			schedule.setInteger(PARAM_DAILY_TARGET_MINUTES + "Monday", 480);
			schedule.setInteger(PARAM_DAILY_TARGET_MINUTES + "Tuesday", 480);
			schedule.setInteger(PARAM_DAILY_TARGET_MINUTES + "Wednesday", 480);
			schedule.setInteger(PARAM_DAILY_TARGET_MINUTES + "Thursday", 480);
			schedule.setInteger(PARAM_DAILY_TARGET_MINUTES + "Friday", 480);
			schedule.setInteger(PARAM_DAILY_TARGET_MINUTES + "Saturday", 0);
			schedule.setInteger(PARAM_DAILY_TARGET_MINUTES + "Sunday", 0);
			tx.add(schedule);

			// Create Holiday
			Resource holiday = new Resource("h1", "New Year", TYPE_HOLIDAY);
			holiday.addParameterBag(new ParameterBag(BAG_RELATIONS, "Relations", "Relations"));
			holiday.addParameterBag(new ParameterBag(BAG_PARAMETERS, "Parameters", "Parameters"));
			holiday.setString(BAG_RELATIONS, TYPE_HOLIDAY_CALENDAR, "cal1");
			holiday.setDate(PARAM_DATE, ZonedDateTime.parse("2026-01-01T00:00:00Z"));
			holiday.setDouble(PARAM_CREDIT_FACTOR, 1.0);
			tx.add(holiday);

			tx.commitOnClose();
		}

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			// 2026-01-01 was a Thursday
			LocalDate holidayDate = LocalDate.of(2026, 1, 1);
			assertEquals(480, ScheduleHelper.getTargetMinutes(tx, "emp1", holidayDate));
			assertEquals(480, HolidayHelper.getHolidayMinutes(tx, "emp1", holidayDate));

			// 2026-01-02 was a Friday
			LocalDate normalDate = LocalDate.of(2026, 1, 2);
			assertEquals(480, ScheduleHelper.getTargetMinutes(tx, "emp1", normalDate));
			assertEquals(0, HolidayHelper.getHolidayMinutes(tx, "emp1", normalDate));

			// 2026-01-03 was a Saturday
			LocalDate weekendDate = LocalDate.of(2026, 1, 3);
			assertEquals(0, ScheduleHelper.getTargetMinutes(tx, "emp1", weekendDate));
		}
	}
}
