package ch.eitchnet.chronivaro.core.model;

import ch.eitchnet.chronivaro.core.service.AddWorkEntryService;
import ch.eitchnet.chronivaro.core.service.CorrectWorkEntryService;
import ch.eitchnet.chronivaro.core.service.CreateOnCallPeriodService;
import ch.eitchnet.chronivaro.core.service.StartTimerService;
import ch.eitchnet.chronivaro.core.service.StopTimerService;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.privilege.base.PrivilegeException;
import li.strolch.privilege.model.Certificate;
import li.strolch.service.StringResult;
import li.strolch.service.api.ServiceResult;
import li.strolch.testbase.runtime.RuntimeMock;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static ch.eitchnet.chronivaro.core.model.ChronivaroConstants.*;
import static org.junit.Assert.*;

public class OnCallWorkEntryTest {

	private static final String TARGET_PATH = "target/" + OnCallWorkEntryTest.class.getSimpleName();
	private static final String SOURCE_PATH = "src/test/resources";
	private static RuntimeMock runtimeMock;

	private static Certificate adminCert;
	private static final String EMPLOYEE_ID = "on-call-emp-01";

	@BeforeClass
	public static void beforeClass() {
		runtimeMock = new RuntimeMock().mockRuntime(TARGET_PATH, SOURCE_PATH);
		runtimeMock.startContainer();
		adminCert = runtimeMock.login("admin", "admin");

		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, false)) {
			Resource loc = tx.getResourceTemplate(TYPE_LOCATION, true);
			loc.setId("on-call-loc");
			loc.setName("On Call Location");
			tx.add(loc);

			Resource team = tx.getResourceTemplate(TYPE_TEAM, true);
			team.setId("on-call-team");
			team.setName("On Call Team");
			tx.add(team);

			Resource employee = tx.getResourceTemplate(TYPE_EMPLOYEE, true);
			employee.setId(EMPLOYEE_ID);
			employee.setName("On Call Tester");
			employee.setString(PARAM_PERSONAL_NUMBER, "ONC-001");
			employee.setString(PARAM_FIRSTNAME, "OnCall");
			employee.setString(PARAM_LASTNAME, "Tester");
			employee.setString(PARAM_TIMEZONE, "Europe/Zurich");
			employee.setDate(PARAM_JOIN_DATE, LocalDate.of(2026, 1, 1).atStartOfDay(ZoneId.of("Europe/Zurich")));
			employee.setBoolean(PARAM_ACTIVE, true);
			employee.setString(PARAM_USERNAME, "admin");
			employee.setRelation(PARAM_LOCATION, loc);
			employee.setRelation(PARAM_PRIMARY_TEAM, team);

			Resource schedule = tx.getResourceTemplate(TYPE_EMPLOYMENT_SCHEDULE, true);
			schedule.setId(EMPLOYEE_ID + "-sched-01");
			schedule.setName("Schedule 1");
			schedule.setRelation(PARAM_EMPLOYEE, employee);
			schedule.setDate(PARAM_VALID_FROM, LocalDate.of(2026, 1, 1).atStartOfDay(ZoneId.of("Europe/Zurich")));
			schedule.setDouble(PARAM_EMPLOYMENT_RATE, 1.0);
			schedule.setInteger(PARAM_WEEKLY_TARGET_MINUTES, 2400);
			schedule.setInteger(PARAM_DAILY_TARGET_MINUTES_MONDAY, 480);
			schedule.setInteger(PARAM_DAILY_TARGET_MINUTES_TUESDAY, 480);
			schedule.setInteger(PARAM_DAILY_TARGET_MINUTES_WEDNESDAY, 480);
			schedule.setInteger(PARAM_DAILY_TARGET_MINUTES_THURSDAY, 480);
			schedule.setInteger(PARAM_DAILY_TARGET_MINUTES_FRIDAY, 480);
			schedule.setInteger(PARAM_DAILY_TARGET_MINUTES_SATURDAY, 0);
			schedule.setInteger(PARAM_DAILY_TARGET_MINUTES_SUNDAY, 0);
			tx.add(schedule);

			employee.setRelation(PARAM_CURRENT_SCHEDULE, schedule);
			tx.add(employee);

			tx.commitOnClose();
		}
	}

	@AfterClass
	public static void afterClass() {
		if (runtimeMock != null)
			runtimeMock.destroyRuntime();
	}

	@Test
	public void testOnCallHelperPeriodLookupAndOffDuty() {
		LocalDate startDate = LocalDate.of(2026, 9, 1);
		LocalDate endDate = LocalDate.of(2026, 9, 5);

		CreateOnCallPeriodService createService = new CreateOnCallPeriodService();
		CreateOnCallPeriodService.CreateOnCallPeriodArgument createArg = new CreateOnCallPeriodService.CreateOnCallPeriodArgument();
		createArg.employeeId = EMPLOYEE_ID;
		createArg.startDate = startDate;
		createArg.startTime = "18:00";
		createArg.endDate = endDate;
		createArg.endTime = "07:00";
		createArg.comment = "Night duty";

		ServiceResult createResult = runtimeMock.getServiceHandler().doService(adminCert, createService, createArg);
		assertTrue(createResult.getMessage(), createResult.isOk());

		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, false)) {
			ZoneId tz = ZoneId.of("Europe/Zurich");

			// Test period lookup
			List<Resource> periods = OnCallHelper.findOnCallPeriods(tx, EMPLOYEE_ID);
			assertFalse(periods.isEmpty());

			// Check within period: 2026-09-01 at 19:00 -> within
			ZonedDateTime time1 = ZonedDateTime.of(2026, 9, 1, 19, 0, 0, 0, tz);
			assertTrue(OnCallHelper.hasActiveOnCallPeriod(tx, EMPLOYEE_ID, time1));
			Optional<Resource> periodOpt = OnCallHelper.findActiveOnCallPeriod(tx, EMPLOYEE_ID, time1);
			assertTrue(periodOpt.isPresent());

			// Check within period: 2026-09-01 at 12:00 -> before start time on start date -> not within
			ZonedDateTime time2 = ZonedDateTime.of(2026, 9, 1, 12, 0, 0, 0, tz);
			assertFalse(OnCallHelper.hasActiveOnCallPeriod(tx, EMPLOYEE_ID, time2));

			// Check within period: 2026-09-03 at 12:00 -> middle date -> within
			ZonedDateTime time3 = ZonedDateTime.of(2026, 9, 3, 12, 0, 0, 0, tz);
			assertTrue(OnCallHelper.hasActiveOnCallPeriod(tx, EMPLOYEE_ID, time3));

			// Check within period: 2026-09-05 at 06:00 -> before end time on end date -> within
			ZonedDateTime time4 = ZonedDateTime.of(2026, 9, 5, 6, 0, 0, 0, tz);
			assertTrue(OnCallHelper.hasActiveOnCallPeriod(tx, EMPLOYEE_ID, time4));

			// Check within period: 2026-09-05 at 08:00 -> after end time on end date -> not within
			ZonedDateTime time5 = ZonedDateTime.of(2026, 9, 5, 8, 0, 0, 0, tz);
			assertFalse(OnCallHelper.hasActiveOnCallPeriod(tx, EMPLOYEE_ID, time5));

			// Test off-duty hours calculation (default office hours: 07:00 - 18:00)
			ZonedDateTime workHours = ZonedDateTime.of(2026, 9, 2, 10, 0, 0, 0, tz);
			assertFalse(OnCallHelper.isOffDutyHours(tx, workHours));

			ZonedDateTime offHoursEarly = ZonedDateTime.of(2026, 9, 2, 6, 30, 0, 0, tz);
			assertTrue(OnCallHelper.isOffDutyHours(tx, offHoursEarly));

			ZonedDateTime offHoursLate = ZonedDateTime.of(2026, 9, 2, 18, 30, 0, 0, tz);
			assertTrue(OnCallHelper.isOffDutyHours(tx, offHoursLate));

			// Test eligibility
			assertTrue(OnCallHelper.isEligibleForOnCall(tx, EMPLOYEE_ID, offHoursLate, offHoursLate.plusHours(2)));
		}
	}

	@Test
	public void testAddAndCorrectWorkEntryWithIsOnCall() {
		ZoneId tz = ZoneId.of("Europe/Zurich");

		ZonedDateTime start = ZonedDateTime.of(2026, 9, 10, 20, 0, 0, 0, tz);
		ZonedDateTime end = ZonedDateTime.of(2026, 9, 10, 22, 0, 0, 0, tz);

		AddWorkEntryService addService = new AddWorkEntryService();
		AddWorkEntryService.AddWorkEntryArgument addArg = new AddWorkEntryService.AddWorkEntryArgument();
		addArg.employeeId = EMPLOYEE_ID;
		addArg.start = start;
		addArg.end = end;
		addArg.workingLocation = WorkingLocation.HOME_OFFICE;
		addArg.comment = "On-call intervention";
		addArg.isOnCall = true;

		ServiceResult addResult = runtimeMock.getServiceHandler().doService(adminCert, addService, addArg);
		assertTrue(addResult.getMessage(), addResult.isOk());
		String workEntryId = ((StringResult) addResult).getValue();

		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, false)) {
			Resource workEntry = tx.getResourceBy(TYPE_WORK_ENTRY, workEntryId, true);
			assertTrue(workEntry.hasParameter(PARAM_IS_ON_CALL));
			assertTrue(workEntry.getBoolean(PARAM_IS_ON_CALL));
		}

		// Correct work entry and update isOnCall to false
		CorrectWorkEntryService correctService = new CorrectWorkEntryService();
		CorrectWorkEntryService.CorrectWorkEntryArgument correctArg = new CorrectWorkEntryService.CorrectWorkEntryArgument();
		correctArg.workEntryId = workEntryId;
		correctArg.start = start;
		correctArg.end = ZonedDateTime.of(2026, 9, 10, 21, 30, 0, 0, tz);
		correctArg.workingLocation = WorkingLocation.HOME_OFFICE;
		correctArg.comment = "Corrected non-on-call intervention";
		correctArg.isOnCall = false;

		ServiceResult correctResult = runtimeMock.getServiceHandler().doService(adminCert, correctService, correctArg);
		assertTrue(correctResult.getMessage(), correctResult.isOk());

		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, false)) {
			Resource workEntry = tx.getResourceBy(TYPE_WORK_ENTRY, workEntryId, true);
			assertFalse(workEntry.getBoolean(PARAM_IS_ON_CALL));
		}
	}

	@Test
	public void testTimerStartAndStopPreservesIsOnCall() {
		ZoneId tz = ZoneId.of("Europe/Zurich");

		ZonedDateTime timerStart = ZonedDateTime.of(2026, 9, 15, 23, 0, 0, 0, tz);

		StartTimerService startService = new StartTimerService();
		StartTimerService.Argument startArg = new StartTimerService.Argument(EMPLOYEE_ID, WorkingLocation.HOME_OFFICE, timerStart, true);

		ServiceResult startResult = runtimeMock.getServiceHandler().doService(adminCert, startService, startArg);
		assertTrue(startResult.getMessage(), startResult.isOk());

		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, false)) {
			Optional<Resource> activeEntryOpt = WorkEntryHelper.findActiveWorkEntry(tx, EMPLOYEE_ID);
			assertTrue(activeEntryOpt.isPresent());
			Resource activeEntry = activeEntryOpt.get();
			assertTrue(activeEntry.getBoolean(PARAM_IS_ON_CALL));
		}

		// Stop timer next day (split past midnight)
		ZonedDateTime timerStop = ZonedDateTime.of(2026, 9, 16, 2, 0, 0, 0, tz);
		StopTimerService stopService = new StopTimerService();
		StopTimerService.StopTimerArgument stopArg = new StopTimerService.StopTimerArgument(EMPLOYEE_ID, timerStop, "Night call");

		ServiceResult stopResult = runtimeMock.getServiceHandler().doService(adminCert, stopService, stopArg);
		assertTrue(stopResult.getMessage(), stopResult.isOk());

		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, false)) {
			List<Resource> entriesDay1 = WorkEntryHelper.findWorkEntries(tx, EMPLOYEE_ID,
					ZonedDateTime.of(2026, 9, 15, 0, 0, 0, 0, tz),
					ZonedDateTime.of(2026, 9, 15, 23, 59, 59, 0, tz));
			assertFalse(entriesDay1.isEmpty());
			assertTrue(entriesDay1.get(0).getBoolean(PARAM_IS_ON_CALL));

			List<Resource> entriesDay2 = WorkEntryHelper.findWorkEntries(tx, EMPLOYEE_ID,
					ZonedDateTime.of(2026, 9, 16, 0, 0, 0, 0, tz),
					ZonedDateTime.of(2026, 9, 16, 23, 59, 59, 0, tz));
			assertFalse(entriesDay2.isEmpty());
			assertTrue(entriesDay2.get(0).getBoolean(PARAM_IS_ON_CALL));
		}
	}
}
