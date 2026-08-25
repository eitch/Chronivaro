package ch.eitchnet.chronivaro.core;

import ch.eitchnet.chronivaro.core.model.HolidayHelper;
import ch.eitchnet.chronivaro.core.model.ScheduleHelper;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.privilege.model.Certificate;
import li.strolch.testbase.runtime.RuntimeMock;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.time.LocalDate;
import java.time.ZonedDateTime;

import static ch.eitchnet.chronivaro.core.model.ChronivaroConstants.*;
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
			// Create Holiday Calendar
			Resource calendar = tx.getResourceTemplate(TYPE_HOLIDAY_CALENDAR, true);
			calendar.setId("cal1");
			calendar.setName("Calendar 1");
			tx.add(calendar);

			// Create Location
			Resource location = tx.getResourceTemplate(TYPE_LOCATION, true);
			location.setId("loc1");
			location.setName("Location 1");
			location.setRelation(PARAM_HOLIDAY_CALENDAR, calendar);
			tx.add(location);

			// Create Employee
			Resource employee = tx.getResourceTemplate(TYPE_EMPLOYEE, true);
			employee.setId("emp1");
			employee.setName("John Doe");
			employee.setBoolean(PARAM_ACTIVE, true);
			employee.setDate(PARAM_JOIN_DATE, ZonedDateTime.parse("2026-01-01T00:00:00Z"));
			employee.setRelationId(PARAM_LOCATION, "loc1");
			tx.add(employee);

			// Create Schedule Version
			Resource schedule = tx.getResourceTemplate(TYPE_EMPLOYMENT_SCHEDULE, true);
			schedule.setId("v1");
			schedule.setName("Schedule V1");
			schedule.setRelation(PARAM_EMPLOYEE, employee);
			schedule.setDate(PARAM_VALID_FROM, ZonedDateTime.parse("2026-01-01T00:00:00Z"));
			schedule.setInteger(PARAM_DAILY_TARGET_MINUTES + "Monday", 480);
			schedule.setInteger(PARAM_DAILY_TARGET_MINUTES + "Tuesday", 480);
			schedule.setInteger(PARAM_DAILY_TARGET_MINUTES + "Wednesday", 480);
			schedule.setInteger(PARAM_DAILY_TARGET_MINUTES + "Thursday", 480);
			schedule.setInteger(PARAM_DAILY_TARGET_MINUTES + "Friday", 480);
			schedule.setInteger(PARAM_DAILY_TARGET_MINUTES + "Saturday", 0);
			schedule.setInteger(PARAM_DAILY_TARGET_MINUTES + "Sunday", 0);
			employee.setRelation(PARAM_CURRENT_SCHEDULE, schedule);
			tx.add(schedule);

			// Create Holiday
			Resource holiday = tx.getResourceTemplate(TYPE_HOLIDAY, true);
			holiday.setId("h1");
			holiday.setName("New Year");
			holiday.setRelation(PARAM_HOLIDAY_CALENDAR, calendar);
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
