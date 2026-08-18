package ch.atexxi.chronivaro.core;

import ch.atexxi.chronivaro.core.model.ChronivaroAuditHelper;
import ch.atexxi.chronivaro.core.model.WorkingLocation;
import ch.atexxi.chronivaro.core.search.AuditEventSearch;
import ch.atexxi.chronivaro.core.service.*;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.privilege.model.Certificate;
import li.strolch.service.StringArgument;
import li.strolch.service.api.ServiceHandler;
import li.strolch.service.api.ServiceResult;
import li.strolch.testbase.runtime.RuntimeMock;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;
import static org.junit.Assert.*;

public class OperationalServicesAuditTest {

	private static final String TARGET_PATH = "target/" + OperationalServicesAuditTest.class.getSimpleName();
	private static final String SOURCE_PATH = "src/test/resources";

	private static RuntimeMock runtimeMock;
	private static Certificate adminCert;
	private static String employeeId;

	@BeforeClass
	public static void beforeClass() {
		runtimeMock = new RuntimeMock().mockRuntime(TARGET_PATH, SOURCE_PATH);
		runtimeMock.startContainer();
		adminCert = runtimeMock.login("admin", "admin");

		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, false)) {
			// Create Location
			Resource loc = tx.getResourceTemplate(TYPE_LOCATION, true);
			loc.setId("op-loc");
			loc.setName("Op Location");
			tx.add(loc);

			// Create Team
			Resource team = tx.getResourceTemplate(TYPE_TEAM, true);
			team.setId("op-team");
			team.setName("Op Team");
			tx.add(team);

			// Create Schedule Template
			Resource template = tx.getResourceTemplate(TYPE_EMPLOYMENT_SCHEDULE_TEMPLATE, true);
			template.setId("op-sched-template");
			template.setName("Op Sched Template");
			template.setInteger(PARAM_DAILY_TARGET_MINUTES_MONDAY, 480);
			template.setInteger(PARAM_DAILY_TARGET_MINUTES_TUESDAY, 480);
			template.setInteger(PARAM_DAILY_TARGET_MINUTES_WEDNESDAY, 480);
			template.setInteger(PARAM_DAILY_TARGET_MINUTES_THURSDAY, 480);
			template.setInteger(PARAM_DAILY_TARGET_MINUTES_FRIDAY, 480);
			template.setInteger(PARAM_DAILY_TARGET_MINUTES_SATURDAY, 0);
			template.setInteger(PARAM_DAILY_TARGET_MINUTES_SUNDAY, 0);
			tx.add(template);

			// Create Absence Type (Vacation)
			Resource vacationType = tx.getResourceTemplate(TYPE_ABSENCE_TYPE, true);
			vacationType.setId("op-vacation");
			vacationType.setName("Vacation");
			vacationType.setString(PARAM_CODE, "VAC");
			vacationType.setBoolean(PARAM_REDUCE_VACATION_CREDIT, true);
			vacationType.setBoolean(PARAM_PAID, true);
			vacationType.setBoolean(PARAM_APPROVAL_REQUIRED, true);
			tx.add(vacationType);

			// Create Employee
			Resource employee = tx.getResourceTemplate(TYPE_EMPLOYEE, true);
			employee.setId("op-emp-01");
			employee.setName("Operational Tester");
			employee.setString(PARAM_PERSONAL_NUMBER, "OP-001");
			employee.setString(PARAM_FIRSTNAME, "Operational");
			employee.setString(PARAM_LASTNAME, "Tester");
			employee.setString(PARAM_TIMEZONE, "Europe/Zurich");
			employee.setDate(PARAM_JOIN_DATE, LocalDate.of(2026, 1, 1).atStartOfDay(ZoneId.of("Europe/Zurich")));
			employee.setBoolean(PARAM_ACTIVE, true);
			employee.setString(PARAM_USERNAME, "admin");
			employee.setRelation(PARAM_LOCATION, loc);
			employee.setRelation(PARAM_PRIMARY_TEAM, team);

			Resource schedule = tx.getResourceTemplate(TYPE_EMPLOYMENT_SCHEDULE, true);
			schedule.setId("op-emp-01-sched-01");
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

			employeeId = employee.getId();
			tx.commitOnClose();
		}
	}

	@AfterClass
	public static void afterClass() {
		if (runtimeMock != null)
			runtimeMock.destroyRuntime();
	}

	@Before
	public void cleanAuditEvents() {
		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, false)) {
			for (Resource r : tx.streamResources(TYPE_AUDIT_EVENT).toList()) {
				tx.remove(r);
			}
			tx.commitOnClose();
		}
		ChronivaroAuditHelper.removeCorrelationId();
	}

	@Test
	public void shouldAuditTimerStartAndStop() {
		ServiceHandler serviceHandler = runtimeMock.getServiceHandler();
		String corrId = "corr-timer-lifecycle";
		ChronivaroAuditHelper.setCorrelationId(corrId);

		ZoneId zone = ZoneId.of("Europe/Zurich");
		ZonedDateTime start = LocalDate.of(2026, 8, 1).atTime(8, 0).atZone(zone);
		ZonedDateTime stop = LocalDate.of(2026, 8, 1).atTime(12, 0).atZone(zone);

		// 1. Start Timer
		StartTimerService.Argument startArg = new StartTimerService.Argument();
		startArg.employeeId = employeeId;
		startArg.workingLocation = WorkingLocation.OFFICE;

		ServiceResult startRes = serviceHandler.doService(adminCert, new StartTimerService(), startArg);
		assertTrue(startRes.getMessage(), startRes.isOk());

		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, true)) {
			List<Resource> events = new AuditEventSearch()
					.forElementType(TYPE_WORK_ENTRY)
					.forAction(AUDIT_ACTION_START)
					.search(tx)
					.toList();
			assertEquals(1, events.size());
			Resource event = events.getFirst();
			assertEquals(corrId, event.getString(PARAM_CORRELATION_ID));
			assertEquals("admin", event.getString(PARAM_CREATED_BY));
			assertTrue(event.getString(PARAM_DETAILS).contains(employeeId));
		}

		// 2. Stop Timer
		StopTimerService.StopTimerArgument stopArg = new StopTimerService.StopTimerArgument();
		stopArg.employeeId = employeeId;
		stopArg.time = null;

		ServiceResult stopRes = serviceHandler.doService(adminCert, new StopTimerService(), stopArg);
		assertTrue(stopRes.getMessage(), stopRes.isOk());

		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, true)) {
			List<Resource> events = new AuditEventSearch()
					.forElementType(TYPE_WORK_ENTRY)
					.forAction(AUDIT_ACTION_STOP)
					.search(tx)
					.toList();
			assertEquals(1, events.size());
			Resource event = events.getFirst();
			assertEquals(corrId, event.getString(PARAM_CORRELATION_ID));
			assertTrue(event.getString(PARAM_DETAILS).contains(employeeId));
		}
	}

	@Test
	public void shouldAuditAddAndCorrectWorkEntry() {
		ServiceHandler serviceHandler = runtimeMock.getServiceHandler();
		String corrId = "corr-workentry-ops";
		ChronivaroAuditHelper.setCorrelationId(corrId);

		ZoneId zone = ZoneId.of("Europe/Zurich");
		ZonedDateTime start = LocalDate.of(2026, 8, 2).atTime(9, 0).atZone(zone);
		ZonedDateTime end = LocalDate.of(2026, 8, 2).atTime(17, 0).atZone(zone);

		// 1. Add manual entry
		AddWorkEntryService.AddWorkEntryArgument addArg = new AddWorkEntryService.AddWorkEntryArgument();
		addArg.employeeId = employeeId;
		addArg.start = start;
		addArg.end = end;
		addArg.comment = "Manual meeting work";
		addArg.workingLocation = WorkingLocation.OFFICE;

		ServiceResult addRes = serviceHandler.doService(adminCert, new AddWorkEntryService(), addArg);
		assertTrue(addRes.getMessage(), addRes.isOk());

		String workEntryId;
		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, true)) {
			List<Resource> events = new AuditEventSearch()
					.forElementType(TYPE_WORK_ENTRY)
					.forAction(AUDIT_ACTION_CREATE)
					.search(tx)
					.toList();
			assertEquals(1, events.size());
			Resource event = events.getFirst();
			assertEquals(corrId, event.getString(PARAM_CORRELATION_ID));
			assertEquals("Manual meeting work", event.getString(PARAM_REASON));
			workEntryId = event.getString(PARAM_ELEMENT_ID);
		}

		// 2. Correct work entry
		ZonedDateTime correctedEnd = LocalDate.of(2026, 8, 2).atTime(18, 0).atZone(zone);
		CorrectWorkEntryService.CorrectWorkEntryArgument corrArg = new CorrectWorkEntryService.CorrectWorkEntryArgument();
		corrArg.workEntryId = workEntryId;
		corrArg.start = start;
		corrArg.end = correctedEnd;
		corrArg.comment = "Overtime adjustment";
		corrArg.workingLocation = WorkingLocation.HOME_OFFICE;

		ServiceResult corrRes = serviceHandler.doService(adminCert, new CorrectWorkEntryService(), corrArg);
		assertTrue(corrRes.getMessage(), corrRes.isOk());

		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, true)) {
			List<Resource> events = new AuditEventSearch()
					.forElementType(TYPE_WORK_ENTRY)
					.forElementId(workEntryId)
					.forAction(AUDIT_ACTION_CORRECT)
					.search(tx)
					.toList();
			assertEquals(1, events.size());
			Resource event = events.getFirst();
			assertEquals(corrId, event.getString(PARAM_CORRELATION_ID));
			assertEquals("Overtime adjustment", event.getString(PARAM_REASON));
			assertTrue(event.getString(PARAM_DETAILS).contains("Corrected work entry"));
		}
	}

	@Test
	public void shouldAuditAbsenceLifecycleAndVacationAccounting() {
		ServiceHandler serviceHandler = runtimeMock.getServiceHandler();
		String corrId = "corr-absence-lifecycle";
		ChronivaroAuditHelper.setCorrelationId(corrId);

		ZoneId zone = ZoneId.of("Europe/Zurich");
		ZonedDateTime start = LocalDate.of(2026, 8, 10).atStartOfDay(zone);
		ZonedDateTime end = LocalDate.of(2026, 8, 10).atTime(23, 59, 59).atZone(zone);

		// 1. Request Absence
		RequestAbsenceService.RequestAbsenceArgument reqArg = new RequestAbsenceService.RequestAbsenceArgument();
		reqArg.employeeId = employeeId;
		reqArg.absenceTypeCode = "VAC";
		reqArg.start = start;
		reqArg.end = end;
		reqArg.durationType = DURATION_FULL_DAY;
		reqArg.comment = "Family vacation day";

		ServiceResult reqRes = serviceHandler.doService(adminCert, new RequestAbsenceService(), reqArg);
		assertTrue(reqRes.getMessage(), reqRes.isOk());

		String absenceId;
		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, true)) {
			List<Resource> events = new AuditEventSearch()
					.forElementType(TYPE_ABSENCE)
					.forAction(AUDIT_ACTION_SUBMIT)
					.search(tx)
					.toList();
			assertEquals(1, events.size());
			Resource event = events.getFirst();
			assertEquals(corrId, event.getString(PARAM_CORRELATION_ID));
			assertEquals("Family vacation day", event.getString(PARAM_REASON));
			absenceId = event.getString(PARAM_ELEMENT_ID);
		}

		// 2. Update Absence
		UpdateAbsenceService.UpdateAbsenceArgument updArg = new UpdateAbsenceService.UpdateAbsenceArgument();
		updArg.absenceId = absenceId;
		updArg.comment = "Updated family holiday";
		ServiceResult updRes = serviceHandler.doService(adminCert, new UpdateAbsenceService(), updArg);
		assertTrue(updRes.getMessage(), updRes.isOk());

		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, true)) {
			List<Resource> events = new AuditEventSearch()
					.forElementType(TYPE_ABSENCE)
					.forElementId(absenceId)
					.forAction(AUDIT_ACTION_UPDATE)
					.search(tx)
					.toList();
			assertEquals(1, events.size());
			assertEquals("Updated family holiday", events.getFirst().getString(PARAM_REASON));
		}

		// 3. Approve Absence
		ServiceResult appRes = serviceHandler.doService(adminCert, new ApproveAbsenceService(), new StringArgument(absenceId));
		assertTrue(appRes.getMessage(), appRes.isOk());

		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, true)) {
			List<Resource> absEvents = new AuditEventSearch()
					.forElementType(TYPE_ABSENCE)
					.forElementId(absenceId)
					.forAction(AUDIT_ACTION_APPROVE)
					.search(tx)
					.toList();
			assertEquals(1, absEvents.size());

			// Check VacationAccountEntry creation audit
			List<Resource> vacEvents = new AuditEventSearch()
					.forElementType(TYPE_VACATION_ACCOUNT_ENTRY)
					.forAction(AUDIT_ACTION_CREATE)
					.search(tx)
					.toList();
			assertEquals(1, vacEvents.size());
			assertTrue(vacEvents.getFirst().getString(PARAM_DETAILS).contains(absenceId));
		}

		// 4. Cancel Absence
		ServiceResult cancelRes = serviceHandler.doService(adminCert, new CancelAbsenceService(), new StringArgument(absenceId));
		assertTrue(cancelRes.getMessage(), cancelRes.isOk());

		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, true)) {
			List<Resource> absEvents = new AuditEventSearch()
					.forElementType(TYPE_ABSENCE)
					.forElementId(absenceId)
					.forAction(AUDIT_ACTION_CANCEL)
					.search(tx)
					.toList();
			assertEquals(1, absEvents.size());

			// Check VacationAccountEntry cancellation refund audit
			List<Resource> vacEvents = new AuditEventSearch()
					.forElementType(TYPE_VACATION_ACCOUNT_ENTRY)
					.forAction(AUDIT_ACTION_CREATE)
					.search(tx)
					.toList();
			assertEquals(2, vacEvents.size());
			assertTrue(vacEvents.stream().anyMatch(e -> e.getString(PARAM_DETAILS).contains("cancellation refund")));
		}
	}

	@Test
	public void shouldAuditAbsenceRejection() {
		ServiceHandler serviceHandler = runtimeMock.getServiceHandler();
		ZoneId zone = ZoneId.of("Europe/Zurich");

		RequestAbsenceService.RequestAbsenceArgument reqArg = new RequestAbsenceService.RequestAbsenceArgument();
		reqArg.employeeId = employeeId;
		reqArg.absenceTypeCode = "VAC";
		reqArg.start = LocalDate.of(2026, 8, 15).atStartOfDay(zone);
		reqArg.end = LocalDate.of(2026, 8, 15).atTime(23, 59, 59).atZone(zone);
		reqArg.durationType = DURATION_FULL_DAY;
		reqArg.comment = "Peak day request";

		ServiceResult reqRes = serviceHandler.doService(adminCert, new RequestAbsenceService(), reqArg);
		assertTrue(reqRes.getMessage(), reqRes.isOk());

		String absenceId;
		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, true)) {
			absenceId = new AuditEventSearch()
					.forElementType(TYPE_ABSENCE)
					.search(tx)
					.toList()
					.getFirst()
					.getString(PARAM_ELEMENT_ID);
		}

		cleanAuditEvents();

		RejectAbsenceService.RejectAbsenceArgument rejArg = new RejectAbsenceService.RejectAbsenceArgument();
		rejArg.absenceId = absenceId;
		rejArg.comment = "Too many team members on leave";

		ServiceResult rejRes = serviceHandler.doService(adminCert, new RejectAbsenceService(), rejArg);
		assertTrue(rejRes.getMessage(), rejRes.isOk());

		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, true)) {
			List<Resource> events = new AuditEventSearch()
					.forElementType(TYPE_ABSENCE)
					.forElementId(absenceId)
					.forAction(AUDIT_ACTION_REJECT)
					.search(tx)
					.toList();
			assertEquals(1, events.size());
			Resource event = events.getFirst();
			assertEquals("Too many team members on leave", event.getString(PARAM_REASON));
		}
	}

	@Test
	public void shouldAuditVacationCorrection() {
		ServiceHandler serviceHandler = runtimeMock.getServiceHandler();
		String corrId = "corr-vacation-correction";
		ChronivaroAuditHelper.setCorrelationId(corrId);

		AddVacationCorrectionService.AddVacationCorrectionArgument arg = new AddVacationCorrectionService.AddVacationCorrectionArgument();
		arg.employeeId = employeeId;
		arg.value = 480;
		arg.comment = "Special anniversary bonus credit";

		ServiceResult res = serviceHandler.doService(adminCert, new AddVacationCorrectionService(), arg);
		assertTrue(res.getMessage(), res.isOk());

		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, true)) {
			List<Resource> events = new AuditEventSearch()
					.forElementType(TYPE_VACATION_ACCOUNT_ENTRY)
					.forAction(AUDIT_ACTION_CREATE)
					.search(tx)
					.toList();
			assertEquals(1, events.size());
			Resource event = events.getFirst();
			assertEquals(corrId, event.getString(PARAM_CORRELATION_ID));
			assertEquals("Special anniversary bonus credit", event.getString(PARAM_REASON));
			assertTrue(event.getString(PARAM_DETAILS).contains("480 minutes"));
		}
	}

	@Test
	public void shouldAuditPeriodTransitions() {
		ServiceHandler serviceHandler = runtimeMock.getServiceHandler();
		String periodId = "period-2026-08";

		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, false)) {
			Resource period = tx.getResourceTemplate(TYPE_TIME_PERIOD, true);
			period.setId(periodId);
			period.setName("Period August 2026");
			period.setRelation(PARAM_EMPLOYEE, tx.getResourceBy(TYPE_EMPLOYEE, employeeId, true));
			period.setString(PARAM_YEAR_MONTH, "2026-08");
			period.setString(PARAM_STATE, STATE_OPEN);
			tx.add(period);
			tx.commitOnClose();
		}

		cleanAuditEvents();
		String corrId = "corr-period-workflow";
		ChronivaroAuditHelper.setCorrelationId(corrId);

		// 1. Submit Period
		ServiceResult subRes = serviceHandler.doService(adminCert, new SubmitPeriodService(), new StringArgument(periodId));
		assertTrue(subRes.getMessage(), subRes.isOk());

		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, true)) {
			List<Resource> events = new AuditEventSearch()
					.forElementType(TYPE_TIME_PERIOD)
					.forElementId(periodId)
					.forAction(AUDIT_ACTION_SUBMIT)
					.search(tx)
					.toList();
			assertEquals(1, events.size());
			assertEquals(corrId, events.getFirst().getString(PARAM_CORRELATION_ID));
		}

		// 2. Approve Period
		ServiceResult appRes = serviceHandler.doService(adminCert, new ApprovePeriodService(), new StringArgument(periodId));
		assertTrue(appRes.getMessage(), appRes.isOk());

		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, true)) {
			List<Resource> events = new AuditEventSearch()
					.forElementType(TYPE_TIME_PERIOD)
					.forElementId(periodId)
					.forAction(AUDIT_ACTION_APPROVE)
					.search(tx)
					.toList();
			assertEquals(1, events.size());
			assertEquals(corrId, events.getFirst().getString(PARAM_CORRELATION_ID));
		}

		// 3. Lock Period
		ServiceResult lockRes = serviceHandler.doService(adminCert, new LockPeriodService(), new StringArgument(periodId));
		assertTrue(lockRes.getMessage(), lockRes.isOk());

		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, true)) {
			List<Resource> events = new AuditEventSearch()
					.forElementType(TYPE_TIME_PERIOD)
					.forElementId(periodId)
					.forAction(AUDIT_ACTION_LOCK)
					.search(tx)
					.toList();
			assertEquals(1, events.size());
			assertEquals(corrId, events.getFirst().getString(PARAM_CORRELATION_ID));
		}
	}

	@Test
	public void shouldAuditGlobalConfigurationUpdate() {
		ServiceHandler serviceHandler = runtimeMock.getServiceHandler();
		String corrId = "corr-config-update";
		ChronivaroAuditHelper.setCorrelationId(corrId);

		UpdateConfigurationService.UpdateConfigurationArgument arg = new UpdateConfigurationService.UpdateConfigurationArgument();
		arg.weeklyTargetMinutes = 2500;

		ServiceResult res = serviceHandler.doService(adminCert, new UpdateConfigurationService(), arg);
		assertTrue(res.getMessage(), res.isOk());

		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, true)) {
			List<Resource> events = new AuditEventSearch()
					.forElementType(TYPE_GLOBAL_CONFIGURATION)
					.forAction(AUDIT_ACTION_UPDATE)
					.search(tx)
					.toList();
			assertEquals(1, events.size());
			Resource event = events.getFirst();
			assertEquals(corrId, event.getString(PARAM_CORRELATION_ID));
			assertTrue(event.getString(PARAM_DETAILS).contains("2500"));
		}
	}
}
