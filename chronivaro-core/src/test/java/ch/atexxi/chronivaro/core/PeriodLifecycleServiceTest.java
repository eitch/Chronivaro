package ch.atexxi.chronivaro.core;

import ch.atexxi.chronivaro.core.model.*;
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

			// Create Absence Type (Paid Sick Leave)
			Resource sickType = tx.getResourceTemplate(TYPE_ABSENCE_TYPE, true);
			sickType.setId("period-sick");
			sickType.setName("Sick Leave");
			sickType.setString(PARAM_CODE, "SICK");
			sickType.setBoolean(PARAM_REDUCE_VACATION_CREDIT, false);
			sickType.setBoolean(PARAM_PAID, true);
			sickType.setBoolean(PARAM_COUNT_AS_TARGET_TIME, true);
			sickType.setBoolean(PARAM_APPROVAL_REQUIRED, false);
			tx.add(sickType);

			// Create Absence Type (Unpaid Leave)
			Resource unpaidType = tx.getResourceTemplate(TYPE_ABSENCE_TYPE, true);
			unpaidType.setId("period-unpaid");
			unpaidType.setName("Unpaid Leave");
			unpaidType.setString(PARAM_CODE, "UNPAID");
			unpaidType.setBoolean(PARAM_REDUCE_VACATION_CREDIT, false);
			unpaidType.setBoolean(PARAM_PAID, false);
			unpaidType.setBoolean(PARAM_COUNT_AS_TARGET_TIME, false);
			unpaidType.setBoolean(PARAM_APPROVAL_REQUIRED, true);
			tx.add(unpaidType);

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

	@Test
	public void shouldCarryForwardBalancesAcrossConsecutiveMonthsAndPreserveSnapshotImmutability() {
		ServiceHandler serviceHandler = runtimeMock.getServiceHandler();
		ZoneId zone = ZoneId.of("Europe/Zurich");

		// Month 1: 2026-01 (Join month) -> Work 1 extra hour on Jan 5th (+60 min period balance)
		AddWorkEntryService.AddWorkEntryArgument m1Work = new AddWorkEntryService.AddWorkEntryArgument();
		m1Work.employeeId = employeeId;
		m1Work.start = LocalDate.of(2026, 1, 5).atTime(8, 0).atZone(zone);
		m1Work.end = LocalDate.of(2026, 1, 5).atTime(17, 0).atZone(zone); // 9h worked vs 8h target (+60 min)
		m1Work.comment = "Overtime 1h";
		m1Work.workingLocation = WorkingLocation.OFFICE;
		assertTrue(serviceHandler.doService(adminCert, new AddWorkEntryService(), m1Work).isOk());

		// Submit and Approve January 2026 period
		YearMonth ymJan = YearMonth.of(2026, 1);
		serviceHandler.doService(adminCert, new SubmitPeriodService(), new PeriodActionArgument(employeeId, ymJan));
		serviceHandler.doService(adminCert, new ApprovePeriodService(), new PeriodActionArgument(employeeId, ymJan));

		// Verify January summary and snapshot
		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, true)) {
			MonthSummary janSummary = MonthSummaryService.getMonthSummary(tx, employeeId, ymJan);
			assertEquals(0, janSummary.initialBalanceMinutes()); // Join month has 0 initial balance
			assertEquals(540 - (22 * 480), janSummary.getPeriodBalance()); // 540 worked - 10560 target = -10020
			assertEquals(janSummary.getPeriodBalance(), janSummary.getEndBalance());

			Resource janPeriod = PeriodHelper.getPeriod(tx, employeeId, ymJan, true);
			assertNotNull(janPeriod.getString(PARAM_CALCULATION_SNAPSHOT));
		}

		// Month 2: 2026-02 -> Verify February's initialBalanceMinutes equals January's endBalanceMinutes
		YearMonth ymFeb = YearMonth.of(2026, 2);
		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, true)) {
			MonthSummary febSummary = MonthSummaryService.getMonthSummary(tx, employeeId, ymFeb);
			MonthSummary janSummary = MonthSummaryService.getMonthSummary(tx, employeeId, ymJan);
			assertEquals(janSummary.getEndBalance(), febSummary.initialBalanceMinutes());
		}

		// Month 2: Add work entry for Feb 2nd (8 hours = 480 min, exactly matching daily target)
		AddWorkEntryService.AddWorkEntryArgument m2Work = new AddWorkEntryService.AddWorkEntryArgument();
		m2Work.employeeId = employeeId;
		m2Work.start = LocalDate.of(2026, 2, 2).atTime(8, 0).atZone(zone);
		m2Work.end = LocalDate.of(2026, 2, 2).atTime(16, 0).atZone(zone);
		m2Work.comment = "Standard 8h";
		m2Work.workingLocation = WorkingLocation.OFFICE;
		assertTrue(serviceHandler.doService(adminCert, new AddWorkEntryService(), m2Work).isOk());

		// Submit, Approve, and Lock February period
		serviceHandler.doService(adminCert, new SubmitPeriodService(), new PeriodActionArgument(employeeId, ymFeb));
		serviceHandler.doService(adminCert, new ApprovePeriodService(), new PeriodActionArgument(employeeId, ymFeb));
		serviceHandler.doService(adminCert, new LockPeriodService(), new PeriodActionArgument(employeeId, ymFeb));

		// Month 3: 2026-03 -> Verify March's initialBalanceMinutes equals February's snapshot endBalanceMinutes
		YearMonth ymMar = YearMonth.of(2026, 3);
		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, true)) {
			MonthSummary febSummary = MonthSummaryService.getMonthSummary(tx, employeeId, ymFeb);
			MonthSummary marSummary = MonthSummaryService.getMonthSummary(tx, employeeId, ymMar);
			assertEquals(febSummary.getEndBalance(), marSummary.initialBalanceMinutes());
		}
	}

	@Test
	public void shouldCategorizePaidUnpaidAndVacationAbsencesCorrectly() {
		ServiceHandler serviceHandler = runtimeMock.getServiceHandler();
		ZoneId zone = ZoneId.of("Europe/Zurich");
		YearMonth ym = YearMonth.of(2026, 6);

		// Credit vacation entitlement for 2026
		serviceHandler.doService(adminCert, new CreditVacationEntitlementService(),
				new CreditVacationEntitlementService.CreditVacationEntitlementArgument(employeeId, 2026, false));

		// 1. Vacation on June 1st (Full Day: 480 min, reduces vacation credit, paid)
		RequestAbsenceService.RequestAbsenceArgument vacArg = new RequestAbsenceService.RequestAbsenceArgument();
		vacArg.employeeId = employeeId;
		vacArg.absenceTypeCode = "VAC";
		vacArg.start = LocalDate.of(2026, 6, 1).atStartOfDay(zone);
		vacArg.end = LocalDate.of(2026, 6, 1).atTime(23, 59, 59).atZone(zone);
		vacArg.durationType = DURATION_FULL_DAY;
		vacArg.comment = "Annual Leave";
		ServiceResult vacRes = serviceHandler.doService(adminCert, new RequestAbsenceService(), vacArg);
		assertTrue(vacRes.isOk());

		// 2. Paid Sick Leave on June 2nd (Full Day: 480 min, counts as target time, paid)
		RequestAbsenceService.RequestAbsenceArgument sickArg = new RequestAbsenceService.RequestAbsenceArgument();
		sickArg.employeeId = employeeId;
		sickArg.absenceTypeCode = "SICK";
		sickArg.start = LocalDate.of(2026, 6, 2).atStartOfDay(zone);
		sickArg.end = LocalDate.of(2026, 6, 2).atTime(23, 59, 59).atZone(zone);
		sickArg.durationType = DURATION_FULL_DAY;
		sickArg.comment = "Flu";
		ServiceResult sickRes = serviceHandler.doService(adminCert, new RequestAbsenceService(), sickArg);
		assertTrue(sickRes.isOk());

		// 3. Unpaid Leave on June 3rd (Full Day: 480 min, unpaid, does not count as target time)
		RequestAbsenceService.RequestAbsenceArgument unpaidArg = new RequestAbsenceService.RequestAbsenceArgument();
		unpaidArg.employeeId = employeeId;
		unpaidArg.absenceTypeCode = "UNPAID";
		unpaidArg.start = LocalDate.of(2026, 6, 3).atStartOfDay(zone);
		unpaidArg.end = LocalDate.of(2026, 6, 3).atTime(23, 59, 59).atZone(zone);
		unpaidArg.durationType = DURATION_FULL_DAY;
		unpaidArg.comment = "Unpaid Personal Day";
		ServiceResult unpaidRes = serviceHandler.doService(adminCert, new RequestAbsenceService(), unpaidArg);
		assertTrue(unpaidRes.isOk());

		// Approve submitted absences
		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, true)) {
			List<Resource> absences = tx.streamResources(TYPE_ABSENCE)
					.filter(a -> employeeId.equals(a.getRelationId(PARAM_EMPLOYEE)))
					.filter(a -> STATE_SUBMITTED.equals(a.getString(PARAM_STATE)))
					.toList();
			for (Resource a : absences) {
				serviceHandler.doService(adminCert, new ApproveAbsenceService(), new li.strolch.service.StringArgument(a.getId()));
			}
		}

		// Verify MonthSummary breakdown
		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, true)) {
			MonthSummary summary = MonthSummaryService.calculateMonthSummary(tx, employeeId, ym);
			assertEquals(480, summary.vacationMinutes());
			assertEquals(480, summary.paidAbsenceMinutes());
			assertEquals(480, summary.unpaidAbsenceMinutes());
			assertEquals(960, summary.totalAbsenceMinutes()); // Credited = vacation (480) + paid (480) = 960

			// Verify Day 3 (Unpaid) absenceMinutes in DaySummary is 0 credited time
			DaySummary day3 = summary.daySummaries().get(2);
			assertEquals(0, day3.absenceMinutes());
			assertEquals(-480, day3.getBalance()); // 0 actual + 0 holiday + 0 absence - 480 target = -480

			// Verify Day 2 (Sick) absenceMinutes is 480 credited
			DaySummary day2 = summary.daySummaries().get(1);
			assertEquals(480, day2.absenceMinutes());
			assertEquals(0, day2.getBalance()); // 0 actual + 0 holiday + 480 absence - 480 target = 0

			// Verify Day 1 (Vacation) absenceMinutes is 480 credited
			DaySummary day1 = summary.daySummaries().get(0);
			assertEquals(480, day1.absenceMinutes());
			assertEquals(0, day1.getBalance());
		}
	}
}
