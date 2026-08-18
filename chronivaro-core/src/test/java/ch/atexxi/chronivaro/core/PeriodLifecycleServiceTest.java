package ch.atexxi.chronivaro.core;

import ch.atexxi.chronivaro.core.model.ChronivaroAuditHelper;
import ch.atexxi.chronivaro.core.model.WorkingLocation;
import ch.atexxi.chronivaro.core.search.AuditEventSearch;
import ch.atexxi.chronivaro.core.search.TimePeriodSearch;
import ch.atexxi.chronivaro.core.service.*;
import com.google.gson.JsonParser;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.privilege.model.Certificate;
import li.strolch.service.api.ServiceHandler;
import li.strolch.service.api.ServiceResult;
import li.strolch.testbase.runtime.RuntimeMock;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;
import static org.junit.Assert.*;

public class PeriodLifecycleServiceTest {

	private static final String TARGET_PATH = "target/" + PeriodLifecycleServiceTest.class.getSimpleName();
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
			loc.setId("period-loc");
			loc.setName("Period Location");
			tx.add(loc);

			// Create Team
			Resource team = tx.getResourceTemplate(TYPE_TEAM, true);
			team.setId("period-team");
			team.setName("Period Team");
			tx.add(team);

			// Create Absence Type (Vacation)
			Resource vacationType = tx.getResourceTemplate(TYPE_ABSENCE_TYPE, true);
			vacationType.setId("period-vacation");
			vacationType.setName("Vacation");
			vacationType.setString(PARAM_CODE, "VAC");
			vacationType.setBoolean(PARAM_REDUCE_VACATION_CREDIT, true);
			vacationType.setBoolean(PARAM_PAID, true);
			vacationType.setBoolean(PARAM_APPROVAL_REQUIRED, true);
			tx.add(vacationType);

			// Create Employee
			Resource employee = tx.getResourceTemplate(TYPE_EMPLOYEE, true);
			employee.setId("emp-period-01");
			employee.setName("Period Tester");
			employee.setString(PARAM_PERSONAL_NUMBER, "P-001");
			employee.setString(PARAM_FIRSTNAME, "Period");
			employee.setString(PARAM_LASTNAME, "Tester");
			employee.setString(PARAM_TIMEZONE, "Europe/Zurich");
			employee.setDate(PARAM_JOIN_DATE, LocalDate.of(2026, 1, 1).atStartOfDay(ZoneId.of("Europe/Zurich")));
			employee.setBoolean(PARAM_ACTIVE, true);
			employee.setString(PARAM_USERNAME, "admin");
			employee.setRelation(PARAM_LOCATION, loc);
			employee.setRelation(PARAM_PRIMARY_TEAM, team);

			Resource schedule = tx.getResourceTemplate(TYPE_EMPLOYMENT_SCHEDULE, true);
			schedule.setId("emp-period-01-sched-01");
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
	public void cleanState() {
		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, false)) {
			for (Resource r : tx.streamResources(TYPE_TIME_PERIOD).toList()) {
				tx.remove(r);
			}
			for (Resource r : tx.streamResources(TYPE_AUDIT_EVENT).toList()) {
				tx.remove(r);
			}
			for (Resource r : tx.streamResources(TYPE_WORK_ENTRY).toList()) {
				tx.remove(r);
			}
			for (Resource r : tx.streamResources(TYPE_WORK_DAY).toList()) {
				tx.remove(r);
			}
			for (Resource r : tx.streamResources(TYPE_ABSENCE).toList()) {
				tx.remove(r);
			}
			tx.commitOnClose();
		}
		ChronivaroAuditHelper.removeCorrelationId();
	}

	@Test
	public void shouldTransitionPeriodLifecycleSuccessfully() {
		ServiceHandler serviceHandler = runtimeMock.getServiceHandler();
		YearMonth ym = YearMonth.of(2026, 8);

		// 1. Submit by employee and yearMonth (auto-creates period)
		PeriodActionArgument submitArg = new PeriodActionArgument(employeeId, ym, "Submitting August 2026");
		ServiceResult subRes = serviceHandler.doService(adminCert, new SubmitPeriodService(), submitArg);
		assertTrue(subRes.getMessage(), subRes.isOk());

		String periodId;
		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, true)) {
			List<Resource> periods = new TimePeriodSearch()
					.forEmployee(employeeId)
					.forYearMonth(ym)
					.search(tx)
					.toList();
			assertEquals(1, periods.size());
			Resource period = periods.getFirst();
			periodId = period.getId();
			assertEquals(STATE_SUBMITTED, period.getString(PARAM_STATE));
			assertEquals("Submitting August 2026", period.getString(PARAM_COMMENT));
			assertNotNull(period.getDate(PARAM_SUBMITTED_AT));
			assertNotNull(period.getString(PARAM_CALCULATION_SNAPSHOT));
			assertTrue(JsonParser.parseString(period.getString(PARAM_CALCULATION_SNAPSHOT)).isJsonObject());
			assertEquals(1, period.getInteger(PARAM_VERSION));
		}

		// 2. Approve period
		PeriodActionArgument approveArg = new PeriodActionArgument(periodId, "Approved by admin");
		ServiceResult appRes = serviceHandler.doService(adminCert, new ApprovePeriodService(), approveArg);
		assertTrue(appRes.getMessage(), appRes.isOk());

		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, true)) {
			Resource period = tx.getResourceBy(TYPE_TIME_PERIOD, periodId, true);
			assertEquals(STATE_APPROVED, period.getString(PARAM_STATE));
			assertEquals("Approved by admin", period.getString(PARAM_COMMENT));
			assertEquals("admin", period.getString(PARAM_APPROVED_BY));
			assertNotNull(period.getDate(PARAM_APPROVED_AT));
			assertEquals(2, period.getInteger(PARAM_VERSION));
		}

		// 3. Lock period
		PeriodActionArgument lockArg = new PeriodActionArgument(periodId, "Monthly closing locked");
		ServiceResult lockRes = serviceHandler.doService(adminCert, new LockPeriodService(), lockArg);
		assertTrue(lockRes.getMessage(), lockRes.isOk());

		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, true)) {
			Resource period = tx.getResourceBy(TYPE_TIME_PERIOD, periodId, true);
			assertEquals(STATE_LOCKED, period.getString(PARAM_STATE));
			assertEquals("Monthly closing locked", period.getString(PARAM_COMMENT));
			assertEquals(3, period.getInteger(PARAM_VERSION));

			// Check Audit events
			List<Resource> audits = new AuditEventSearch()
					.forElementType(TYPE_TIME_PERIOD)
					.forElementId(periodId)
					.search(tx)
					.toList();
			assertEquals(3, audits.size());
		}
	}

	@Test
	public void shouldSupportRejectionAndResubmission() {
		ServiceHandler serviceHandler = runtimeMock.getServiceHandler();
		YearMonth ym = YearMonth.of(2026, 9);

		// 1. Submit
		PeriodActionArgument submitArg = new PeriodActionArgument(employeeId, ym, "September submission");
		ServiceResult subRes = serviceHandler.doService(adminCert, new SubmitPeriodService(), submitArg);
		assertTrue(subRes.getMessage(), subRes.isOk());

		// 2. Reject without reason must fail
		PeriodActionArgument emptyRejArg = new PeriodActionArgument(employeeId, ym, "");
		ServiceResult emptyRejRes = serviceHandler.doService(adminCert, new RejectPeriodService(), emptyRejArg);
		assertFalse(emptyRejRes.isOk());

		// 3. Reject with reason
		PeriodActionArgument rejArg = new PeriodActionArgument(employeeId, ym, "Missing doctor's note for Sept 15");
		ServiceResult rejRes = serviceHandler.doService(adminCert, new RejectPeriodService(), rejArg);
		assertTrue(rejRes.getMessage(), rejRes.isOk());

		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, true)) {
			Resource period = new TimePeriodSearch().forEmployee(employeeId).forYearMonth(ym).search(tx).toList().getFirst();
			assertEquals(STATE_REJECTED, period.getString(PARAM_STATE));
			assertEquals("Missing doctor's note for Sept 15", period.getString(PARAM_COMMENT));
			assertEquals("admin", period.getString(PARAM_REJECTED_BY));
			assertNotNull(period.getDate(PARAM_REJECTED_AT));
		}

		// 4. Re-submit rejected period
		PeriodActionArgument resubArg = new PeriodActionArgument(employeeId, ym, "Updated with doctor note attached");
		ServiceResult resubRes = serviceHandler.doService(adminCert, new SubmitPeriodService(), resubArg);
		assertTrue(resubRes.getMessage(), resubRes.isOk());

		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, true)) {
			Resource period = new TimePeriodSearch().forEmployee(employeeId).forYearMonth(ym).search(tx).toList().getFirst();
			assertEquals(STATE_SUBMITTED, period.getString(PARAM_STATE));
		}
	}

	@Test
	public void shouldSupportReopeningLockedPeriodWithReason() {
		ServiceHandler serviceHandler = runtimeMock.getServiceHandler();
		YearMonth ym = YearMonth.of(2026, 10);

		// Advance to LOCKED
		serviceHandler.doService(adminCert, new SubmitPeriodService(), new PeriodActionArgument(employeeId, ym));
		serviceHandler.doService(adminCert, new ApprovePeriodService(), new PeriodActionArgument(employeeId, ym));
		serviceHandler.doService(adminCert, new LockPeriodService(), new PeriodActionArgument(employeeId, ym));

		// Reopening without reason must fail
		PeriodActionArgument emptyReopenArg = new PeriodActionArgument(employeeId, ym, "");
		ServiceResult emptyReopenRes = serviceHandler.doService(adminCert, new ReopenPeriodService(), emptyReopenArg);
		assertFalse(emptyReopenRes.isOk());

		// Reopen with reason
		PeriodActionArgument reopenArg = new PeriodActionArgument(employeeId, ym, "Late expense overtime correction required");
		ServiceResult reopenRes = serviceHandler.doService(adminCert, new ReopenPeriodService(), reopenArg);
		assertTrue(reopenRes.getMessage(), reopenRes.isOk());

		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, true)) {
			Resource period = new TimePeriodSearch().forEmployee(employeeId).forYearMonth(ym).search(tx).toList().getFirst();
			assertEquals(STATE_OPEN, period.getString(PARAM_STATE));
			assertEquals("Late expense overtime correction required", period.getString(PARAM_COMMENT));

			// Check Reopen Audit event
			List<Resource> reopenAudits = new AuditEventSearch()
					.forElementType(TYPE_TIME_PERIOD)
					.forElementId(period.getId())
					.forAction(AUDIT_ACTION_REOPEN)
					.search(tx)
					.toList();
			assertEquals(1, reopenAudits.size());
			assertEquals("Late expense overtime correction required", reopenAudits.getFirst().getString(PARAM_REASON));
		}
	}

	@Test
	public void shouldEnforcePeriodLockingOnWorkEntriesAndAbsences() {
		ServiceHandler serviceHandler = runtimeMock.getServiceHandler();
		YearMonth ym = YearMonth.of(2026, 11);
		ZoneId zone = ZoneId.of("Europe/Zurich");

		// Submit period for November 2026
		serviceHandler.doService(adminCert, new SubmitPeriodService(), new PeriodActionArgument(employeeId, ym));

		// 1. Adding work entry in November must fail
		AddWorkEntryService.AddWorkEntryArgument addArg = new AddWorkEntryService.AddWorkEntryArgument();
		addArg.employeeId = employeeId;
		addArg.start = LocalDate.of(2026, 11, 5).atTime(9, 0).atZone(zone);
		addArg.end = LocalDate.of(2026, 11, 5).atTime(17, 0).atZone(zone);
		addArg.comment = "Late work";
		addArg.workingLocation = WorkingLocation.OFFICE;

		ServiceResult addRes = serviceHandler.doService(adminCert, new AddWorkEntryService(), addArg);
		assertFalse("Adding work entry to submitted period must fail", addRes.isOk());
		assertTrue(addRes.getMessage().contains("Cannot modify records for period"));

		// 2. Requesting absence in November must fail
		RequestAbsenceService.RequestAbsenceArgument absArg = new RequestAbsenceService.RequestAbsenceArgument();
		absArg.employeeId = employeeId;
		absArg.absenceTypeCode = "VAC";
		absArg.start = LocalDate.of(2026, 11, 10).atStartOfDay(zone);
		absArg.end = LocalDate.of(2026, 11, 10).atTime(23, 59, 59).atZone(zone);
		absArg.durationType = DURATION_FULL_DAY;
		absArg.comment = "Vacation request";

		ServiceResult absRes = serviceHandler.doService(adminCert, new RequestAbsenceService(), absArg);
		assertFalse("Requesting absence in submitted period must fail", absRes.isOk());
		assertTrue(absRes.getMessage().contains("Cannot modify records for period"));

		// 3. Reopening the period enables modifications
		serviceHandler.doService(adminCert, new ReopenPeriodService(), new PeriodActionArgument(employeeId, ym, "Employee forgot hours"));
		ServiceResult retryAddRes = serviceHandler.doService(adminCert, new AddWorkEntryService(), addArg);
		assertTrue(retryAddRes.getMessage(), retryAddRes.isOk());
	}

	@Test
	public void shouldRejectInvalidStateTransitions() {
		ServiceHandler serviceHandler = runtimeMock.getServiceHandler();
		YearMonth ym = YearMonth.of(2026, 12);

		// Direct approval on OPEN period must fail
		ServiceResult appRes = serviceHandler.doService(adminCert, new ApprovePeriodService(),
				new PeriodActionArgument(employeeId, ym));
		assertFalse(appRes.isOk());

		// Direct lock on OPEN period must fail
		ServiceResult lockRes = serviceHandler.doService(adminCert, new LockPeriodService(),
				new PeriodActionArgument(employeeId, ym));
		assertFalse(lockRes.isOk());

		// Direct reject on OPEN period must fail
		ServiceResult rejRes = serviceHandler.doService(adminCert, new RejectPeriodService(),
				new PeriodActionArgument(employeeId, ym, "Cannot reject open period"));
		assertFalse(rejRes.isOk());
	}

	@Test
	public void shouldSupportTimePeriodSearchFilters() {
		ServiceHandler serviceHandler = runtimeMock.getServiceHandler();

		serviceHandler.doService(adminCert, new SubmitPeriodService(), new PeriodActionArgument(employeeId, YearMonth.of(2026, 1)));
		serviceHandler.doService(adminCert, new SubmitPeriodService(), new PeriodActionArgument(employeeId, YearMonth.of(2026, 2)));
		serviceHandler.doService(adminCert, new ApprovePeriodService(), new PeriodActionArgument(employeeId, YearMonth.of(2026, 2)));

		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, true)) {
			// By Employee and Year
			List<Resource> yearPeriods = new TimePeriodSearch()
					.forEmployee(employeeId)
					.forYear(2026)
					.search(tx)
					.toList();
			assertEquals(2, yearPeriods.size());

			// By State
			List<Resource> approved = new TimePeriodSearch()
					.forEmployee(employeeId)
					.forState(STATE_APPROVED)
					.search(tx)
					.toList();
			assertEquals(1, approved.size());
			assertEquals("2026-02", approved.getFirst().getString(PARAM_YEAR_MONTH));

			List<Resource> submitted = new TimePeriodSearch()
					.forEmployee(employeeId)
					.forState(STATE_SUBMITTED)
					.search(tx)
					.toList();
			assertEquals(1, submitted.size());
			assertEquals("2026-01", submitted.getFirst().getString(PARAM_YEAR_MONTH));
		}
	}
}
