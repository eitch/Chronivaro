package ch.eitchnet.chronivaro.core;

import ch.eitchnet.chronivaro.core.model.VacationAccountSummary;
import ch.eitchnet.chronivaro.core.model.VacationHelper;
import ch.eitchnet.chronivaro.core.search.AuditEventSearch;
import ch.eitchnet.chronivaro.core.service.*;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.privilege.model.Certificate;
import li.strolch.service.StringArgument;
import li.strolch.service.StringResult;
import li.strolch.service.api.ServiceHandler;
import li.strolch.service.api.ServiceResult;
import li.strolch.testbase.runtime.RuntimeMock;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static ch.eitchnet.chronivaro.core.model.ChronivaroConstants.*;
import static ch.eitchnet.chronivaro.core.model.ChronivaroVersionHelper.initVersion;
import static org.junit.Assert.*;

public class VacationEntitlementServiceTest {

	private static final String TARGET_PATH = "target/" + VacationEntitlementServiceTest.class.getSimpleName();
	private static final String SOURCE_PATH = "src/test/resources";

	private static RuntimeMock runtimeMock;
	private static Certificate adminCert;

	@BeforeClass
	public static void beforeClass() {
		runtimeMock = new RuntimeMock().mockRuntime(TARGET_PATH, SOURCE_PATH);
		runtimeMock.startContainer();
		adminCert = runtimeMock.login("admin", "admin");

		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, false)) {
			// Create Location
			Resource loc = tx.getResourceTemplate(TYPE_LOCATION, true);
			loc.setId("vac-loc");
			loc.setName("Vacation Location");
			tx.add(loc);

			// Create Team
			Resource team = tx.getResourceTemplate(TYPE_TEAM, true);
			team.setId("vac-team");
			team.setName("Vacation Team");
			tx.add(team);

			// Create Absence Type (Vacation)
			Resource vacationType = tx.getResourceTemplate(TYPE_ABSENCE_TYPE, true);
			vacationType.setId("vacation-type");
			vacationType.setName("Vacation");
			vacationType.setString(PARAM_CODE, "VACATION");
			vacationType.setBoolean(PARAM_REDUCE_VACATION_CREDIT, true);
			vacationType.setBoolean(PARAM_PAID, true);
			vacationType.setBoolean(PARAM_APPROVAL_REQUIRED, true);
			tx.add(vacationType);

			tx.commitOnClose();
		}
	}

	@AfterClass
	public static void afterClass() {
		if (runtimeMock != null)
			runtimeMock.destroyRuntime();
	}

	@Before
	public void cleanData() {
		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, false)) {
			tx.streamResources(TYPE_VACATION_ACCOUNT_ENTRY).forEach(tx::remove);
			tx.streamResources(TYPE_ABSENCE).forEach(tx::remove);
			tx.streamResources(TYPE_AUDIT_EVENT).forEach(tx::remove);
			tx.commitOnClose();
		}
	}

	private Resource createTestEmployee(String id, String name, LocalDate joinDate, LocalDate exitDate,
			double employmentRate) {
		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, false)) {
			Resource loc = tx.getResourceBy(TYPE_LOCATION, "vac-loc", true);
			Resource team = tx.getResourceBy(TYPE_TEAM, "vac-team", true);

			Resource employee = tx.getResourceTemplate(TYPE_EMPLOYEE, true);
			employee.setId(id);
			employee.setName(name);
			employee.setString(PARAM_FIRSTNAME, name.split(" ")[0]);
			employee.setString(PARAM_LASTNAME, name.split(" ")[1]);
			employee.setString(PARAM_TIMEZONE, "Europe/Zurich");
			employee.setDate(PARAM_JOIN_DATE, joinDate.atStartOfDay(ZoneId.of("Europe/Zurich")));
			if (exitDate != null) {
				employee.setDate(PARAM_EXIT_DATE, exitDate.atStartOfDay(ZoneId.of("Europe/Zurich")));
			}
			employee.setBoolean(PARAM_ACTIVE, true);
			employee.setString(PARAM_USERNAME, id);
			employee.setRelation(PARAM_LOCATION, loc);
			employee.setRelation(PARAM_PRIMARY_TEAM, team);

			Resource schedule = tx.getResourceTemplate(TYPE_EMPLOYMENT_SCHEDULE, true);
			schedule.setId("sched-" + id);
			schedule.setName("Schedule " + name);
			schedule.setRelation(PARAM_EMPLOYEE, employee);
			schedule.setDate(PARAM_VALID_FROM, joinDate.atStartOfDay(ZoneId.of("Europe/Zurich")));
			schedule.setDouble(PARAM_EMPLOYMENT_RATE, employmentRate);
			int dailyMin = (int) Math.round(480 * employmentRate);
			schedule.setInteger(PARAM_WEEKLY_TARGET_MINUTES, dailyMin * 5);
			schedule.setInteger(PARAM_DAILY_TARGET_MINUTES_MONDAY, dailyMin);
			schedule.setInteger(PARAM_DAILY_TARGET_MINUTES_TUESDAY, dailyMin);
			schedule.setInteger(PARAM_DAILY_TARGET_MINUTES_WEDNESDAY, dailyMin);
			schedule.setInteger(PARAM_DAILY_TARGET_MINUTES_THURSDAY, dailyMin);
			schedule.setInteger(PARAM_DAILY_TARGET_MINUTES_FRIDAY, dailyMin);
			schedule.setInteger(PARAM_DAILY_TARGET_MINUTES_SATURDAY, 0);
			schedule.setInteger(PARAM_DAILY_TARGET_MINUTES_SUNDAY, 0);
			tx.add(schedule);

			employee.setRelation(PARAM_CURRENT_SCHEDULE, schedule);
			tx.add(employee);
			tx.commitOnClose();
			return employee;
		}
	}

	@Test
	public void testFullYearEntitlementDefault() {
		createTestEmployee("emp-full-2025", "Full Year", LocalDate.of(2025, 1, 1), null, 1.0);

		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, true)) {
			int entitlement = VacationHelper.calculateAnnualEntitlement(tx, "emp-full-2025", 2025);
			// 25 days * 480 min = 12000 minutes (200 hours)
			assertEquals(12000, entitlement);
		}
	}

	@Test
	public void testLeapYearEntitlement() {
		createTestEmployee("emp-leap-2024", "Leap Year", LocalDate.of(2024, 1, 1), null, 1.0);

		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, true)) {
			int entitlement = VacationHelper.calculateAnnualEntitlement(tx, "emp-leap-2024", 2024);
			// Leap year (366 days): 366 * (12000/366) = 12000 minutes
			assertEquals(12000, entitlement);
		}
	}

	@Test
	public void testPartTimeEntitlement() {
		createTestEmployee("emp-part-2025", "Part Time", LocalDate.of(2025, 1, 1), null, 0.8);

		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, true)) {
			int entitlement = VacationHelper.calculateAnnualEntitlement(tx, "emp-part-2025", 2025);
			// 80% of 12000 = 9600 minutes (20 days)
			assertEquals(9600, entitlement);
		}
	}

	@Test
	public void testProratedEntryMidYear() {
		// Joined July 1, 2025 (184 days in second half of non-leap 2025: July 1 to Dec 31)
		createTestEmployee("emp-join-mid", "Join Mid", LocalDate.of(2025, 7, 1), null, 1.0);

		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, true)) {
			int entitlement = VacationHelper.calculateAnnualEntitlement(tx, "emp-join-mid", 2025);
			// 184 / 365 * 12000 = 6049.315... -> rounded to 6049 minutes
			assertEquals(6049, entitlement);
		}
	}

	@Test
	public void testProratedExitMidYear() {
		// Exited June 30, 2025 (181 days in first half of non-leap 2025: Jan 1 to June 30)
		createTestEmployee("emp-exit-mid", "Exit Mid", LocalDate.of(2025, 1, 1), LocalDate.of(2025, 6, 30), 1.0);

		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, true)) {
			int entitlement = VacationHelper.calculateAnnualEntitlement(tx, "emp-exit-mid", 2025);
			// 181 / 365 * 12000 = 5950.68... -> rounded to 5951 minutes
			assertEquals(5951, entitlement);
		}
	}

	@Test
	public void testScheduleChangeMidYear() {
		// Employee joins Jan 1, 2025 with 100% schedule, then gets 50% schedule from July 1, 2025
		createTestEmployee("emp-sched-change", "Schedule Change", LocalDate.of(2025, 1, 1), null, 1.0);

		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, false)) {
			Resource employee = tx.getResourceBy(TYPE_EMPLOYEE, "emp-sched-change", true);
			Resource sched1 = tx.getResourceBy(TYPE_EMPLOYMENT_SCHEDULE, "sched-emp-sched-change", true);
			sched1.setDate(PARAM_VALID_TO, LocalDate.of(2025, 6, 30).atTime(23, 59, 59).atZone(ZoneId.of("Europe/Zurich")));
			tx.update(sched1);

			Resource sched2 = tx.getResourceTemplate(TYPE_EMPLOYMENT_SCHEDULE, true);
			sched2.setId("sched-emp-sched-change-v2");
			sched2.setName("Schedule 50%");
			sched2.setRelation(PARAM_EMPLOYEE, employee);
			sched2.setDate(PARAM_VALID_FROM, LocalDate.of(2025, 7, 1).atStartOfDay(ZoneId.of("Europe/Zurich")));
			sched2.setDouble(PARAM_EMPLOYMENT_RATE, 0.5);
			sched2.setInteger(PARAM_WEEKLY_TARGET_MINUTES, 1200);
			sched2.setInteger(PARAM_DAILY_TARGET_MINUTES_MONDAY, 240);
			sched2.setInteger(PARAM_DAILY_TARGET_MINUTES_TUESDAY, 240);
			sched2.setInteger(PARAM_DAILY_TARGET_MINUTES_WEDNESDAY, 240);
			sched2.setInteger(PARAM_DAILY_TARGET_MINUTES_THURSDAY, 240);
			sched2.setInteger(PARAM_DAILY_TARGET_MINUTES_FRIDAY, 240);
			sched2.setInteger(PARAM_DAILY_TARGET_MINUTES_SATURDAY, 0);
			sched2.setInteger(PARAM_DAILY_TARGET_MINUTES_SUNDAY, 0);
			tx.add(sched2);

			employee.setRelation(PARAM_CURRENT_SCHEDULE, sched2);
			tx.update(employee);
			tx.commitOnClose();
		}

		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, true)) {
			int entitlement = VacationHelper.calculateAnnualEntitlement(tx, "emp-sched-change", 2025);
			// 181 days at 100% (5950.68) + 184 days at 50% (3024.66) = 8975.34 -> 8975
			assertEquals(8975, entitlement);
		}
	}

	@Test
	public void testCustomGlobalConfiguration() {
		ServiceHandler serviceHandler = runtimeMock.getContainer().getComponent(ServiceHandler.class);
		UpdateConfigurationService.UpdateConfigurationArgument configArg = new UpdateConfigurationService.UpdateConfigurationArgument();
		configArg.annualVacationDays = 30;
		configArg.minutesPerVacationDay = 500;
		ServiceResult configRes = serviceHandler.doService(adminCert, new UpdateConfigurationService(), configArg);
		assertTrue(configRes.isOk());

		createTestEmployee("emp-custom-cfg", "Custom Config", LocalDate.of(2025, 1, 1), null, 1.0);

		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, true)) {
			int entitlement = VacationHelper.calculateAnnualEntitlement(tx, "emp-custom-cfg", 2025);
			// 30 days * 500 min = 15000 minutes
			assertEquals(15000, entitlement);
		}

		// Reset to default
		configArg.annualVacationDays = 25;
		configArg.minutesPerVacationDay = 480;
		serviceHandler.doService(adminCert, new UpdateConfigurationService(), configArg);
	}

	@Test
	public void testCreditVacationEntitlementService() {
		createTestEmployee("emp-credit-01", "Credit Tester", LocalDate.of(2025, 1, 1), null, 1.0);

		ServiceHandler serviceHandler = runtimeMock.getContainer().getComponent(ServiceHandler.class);
		CreditVacationEntitlementService.CreditVacationEntitlementArgument arg =
				new CreditVacationEntitlementService.CreditVacationEntitlementArgument("emp-credit-01", 2025, false);

		CreditVacationEntitlementService.CreditVacationEntitlementResult result =
				serviceHandler.doService(adminCert, new CreditVacationEntitlementService(), arg);
		assertTrue(result.isOk());
		assertEquals(12000, result.entitlementMinutes);
		assertNotNull(result.entryId);

		// Verify audit event
		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, true)) {
			List<Resource> audits = new AuditEventSearch()
					.forElementType(TYPE_VACATION_ACCOUNT_ENTRY)
					.forElementId(result.entryId)
					.forAction(AUDIT_ACTION_CREATE)
					.search(tx)
					.toList();
			assertFalse(audits.isEmpty());
		}

		// Re-run with forceRecalculate
		arg.forceRecalculate = true;
		CreditVacationEntitlementService.CreditVacationEntitlementResult rerun =
				serviceHandler.doService(adminCert, new CreditVacationEntitlementService(), arg);
		assertTrue(rerun.isOk());
		assertEquals(12000, rerun.entitlementMinutes);
	}

	@Test
	public void testVacationAccountSummaryAndCarryOver() {
		createTestEmployee("emp-journal-01", "Journal Tester", LocalDate.of(2024, 1, 1), null, 1.0);

		ServiceHandler serviceHandler = runtimeMock.getContainer().getComponent(ServiceHandler.class);

		// 1. Credit 2024 entitlement (12000 min)
		CreditVacationEntitlementService.CreditVacationEntitlementArgument credit2024 =
				new CreditVacationEntitlementService.CreditVacationEntitlementArgument("emp-journal-01", 2024, false);
		assertTrue(serviceHandler.doService(adminCert, new CreditVacationEntitlementService(), credit2024).isOk());

		// 2. Add 2024 Usage (9600 min taken in 2024 -> leaves 2400 min carry over)
		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, false)) {
			Resource entry = tx.getResourceTemplate(TYPE_VACATION_ACCOUNT_ENTRY, true);
			entry.setName("Usage 2024");
			entry.setRelation(PARAM_EMPLOYEE, tx.getResourceBy(TYPE_EMPLOYEE, "emp-journal-01", true));
			entry.setDate(PARAM_DATE, LocalDate.of(2024, 8, 1).atStartOfDay(ZoneId.of("Europe/Zurich")));
			entry.setString(PARAM_VACATION_TYPE, VACATION_USAGE);
			entry.setInteger(PARAM_VALUE, -9600);
			tx.add(entry);
			tx.commitOnClose();
		}

		// 3. Credit 2025 entitlement (12000 min)
		CreditVacationEntitlementService.CreditVacationEntitlementArgument credit2025 =
				new CreditVacationEntitlementService.CreditVacationEntitlementArgument("emp-journal-01", 2025, false);
		assertTrue(serviceHandler.doService(adminCert, new CreditVacationEntitlementService(), credit2025).isOk());

		// 4. Add 2025 Correction (+480 min)
		AddVacationCorrectionService.AddVacationCorrectionArgument corrArg =
				new AddVacationCorrectionService.AddVacationCorrectionArgument();
		corrArg.employeeId = "emp-journal-01";
		corrArg.value = 480;
		corrArg.comment = "Anniversary bonus";
		corrArg.date = ZonedDateTime.of(2025, 3, 1, 0, 0, 0, 0, ZoneId.of("Europe/Zurich"));
		assertTrue(serviceHandler.doService(adminCert, new AddVacationCorrectionService(), corrArg).isOk());

		// 5. Add 2025 Usage (4800 min)
		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, false)) {
			Resource entry = tx.getResourceTemplate(TYPE_VACATION_ACCOUNT_ENTRY, true);
			entry.setName("Usage 2025");
			entry.setRelation(PARAM_EMPLOYEE, tx.getResourceBy(TYPE_EMPLOYEE, "emp-journal-01", true));
			entry.setDate(PARAM_DATE, LocalDate.of(2025, 5, 1).atStartOfDay(ZoneId.of("Europe/Zurich")));
			entry.setString(PARAM_VACATION_TYPE, VACATION_USAGE);
			entry.setInteger(PARAM_VALUE, -4800);
			tx.add(entry);
			tx.commitOnClose();
		}

		// Verify 2025 VacationAccountSummary
		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, true)) {
			VacationAccountSummary summary = VacationHelper.getVacationAccountSummary(tx, "emp-journal-01", 2025);
			assertEquals(2400, summary.carryOverMinutes()); // 12000 - 9600
			assertEquals(12000, summary.entitlementMinutes());
			assertEquals(480, summary.correctionsMinutes());
			assertEquals(4800, summary.usageMinutes());
			// Remaining: 2400 + 12000 + 480 - 4800 = 10080 minutes
			assertEquals(10080, summary.remainingMinutes());
		}
	}

	@Test
	public void testApproveAbsenceBlocksNegativeBalance() {
		createTestEmployee("emp-balance-check", "Balance Checker", LocalDate.of(2025, 1, 1), null, 1.0);

		ServiceHandler serviceHandler = runtimeMock.getContainer().getComponent(ServiceHandler.class);

		// 1. Credit small entitlement of 480 minutes (1 day)
		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, false)) {
			Resource entry = tx.getResourceTemplate(TYPE_VACATION_ACCOUNT_ENTRY, true);
			entry.setName("Small Entitlement");
			entry.setRelation(PARAM_EMPLOYEE, tx.getResourceBy(TYPE_EMPLOYEE, "emp-balance-check", true));
			entry.setDate(PARAM_DATE, LocalDate.of(2025, 1, 1).atStartOfDay(ZoneId.of("Europe/Zurich")));
			entry.setString(PARAM_VACATION_TYPE, VACATION_ENTITLEMENT);
			entry.setInteger(PARAM_VALUE, 480);
			tx.add(entry);
			tx.commitOnClose();
		}

		// 2. Request a 2-day absence (960 min requested, only 480 available)
		String excessAbsenceId;
		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, false)) {
			Resource absence = tx.getResourceTemplate(TYPE_ABSENCE, true);
			absence.setId("abs-excess-01");
			absence.setName("Excess Vacation");
			absence.setRelation(PARAM_EMPLOYEE, tx.getResourceBy(TYPE_EMPLOYEE, "emp-balance-check", true));
			absence.setRelation(PARAM_ABSENCE_TYPE, tx.getResourceBy(TYPE_ABSENCE_TYPE, "vacation-type", true));
			absence.setDate(PARAM_START, LocalDate.of(2025, 3, 3).atStartOfDay(ZoneId.of("Europe/Zurich")));
			absence.setDate(PARAM_END, LocalDate.of(2025, 3, 4).atStartOfDay(ZoneId.of("Europe/Zurich")));
			absence.setString(PARAM_DURATION_TYPE, DURATION_FULL_DAY);
			absence.setString(PARAM_STATE, STATE_SUBMITTED);
			tx.add(absence);
			excessAbsenceId = absence.getId();
			tx.commitOnClose();
		}

		// 3. Attempt to approve excess absence -> must fail due to insufficient balance
		ServiceResult failResult = serviceHandler.doService(adminCert, new ApproveAbsenceService(),
				new StringArgument(excessAbsenceId));
		assertFalse("Approval must fail when balance is insufficient", failResult.isOk());
		assertTrue("Error message must mention insufficient vacation balance",
				failResult.getMessage().contains("Insufficient vacation balance"));

		// 4. Request a 1-day absence (480 min requested, 480 min available)
		String validAbsenceId;
		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, false)) {
			Resource absence = tx.getResourceTemplate(TYPE_ABSENCE, true);
			absence.setId("abs-valid-01");
			absence.setName("Valid Vacation");
			absence.setRelation(PARAM_EMPLOYEE, tx.getResourceBy(TYPE_EMPLOYEE, "emp-balance-check", true));
			absence.setRelation(PARAM_ABSENCE_TYPE, tx.getResourceBy(TYPE_ABSENCE_TYPE, "vacation-type", true));
			absence.setDate(PARAM_START, LocalDate.of(2025, 3, 5).atStartOfDay(ZoneId.of("Europe/Zurich")));
			absence.setDate(PARAM_END, LocalDate.of(2025, 3, 5).atStartOfDay(ZoneId.of("Europe/Zurich")));
			absence.setString(PARAM_DURATION_TYPE, DURATION_FULL_DAY);
			absence.setString(PARAM_STATE, STATE_SUBMITTED);
			tx.add(absence);
			validAbsenceId = absence.getId();
			tx.commitOnClose();
		}

		// 5. Approve valid absence -> must succeed
		ServiceResult okResult = serviceHandler.doService(adminCert, new ApproveAbsenceService(),
				new StringArgument(validAbsenceId));
		assertTrue("Approval must succeed when balance is sufficient", okResult.isOk());

		// 6. Verify remaining balance is 0
		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, true)) {
			int balance = VacationHelper.getVacationBalance(tx, "emp-balance-check");
			assertEquals(0, balance);
		}
	}

	@Test
	public void testAutomatedVacationEntitlementOnEmployeeCreation() {
		ServiceHandler serviceHandler = runtimeMock.getContainer().getComponent(ServiceHandler.class);
		CreateEmployeeService.EmployeeArgument arg = new CreateEmployeeService.EmployeeArgument();
		arg.username = "emp-auto-vacation";
		arg.firstname = "Auto";
		arg.lastname = "Vacation";
		arg.personalNumber = "P-AUTO-01";
		arg.teamId = "vac-team";
		arg.locationId = "vac-loc";
		arg.joinDate = LocalDate.of(2025, 7, 1);
		arg.active = true;

		StringResult result = serviceHandler.doService(adminCert, new CreateEmployeeService(), arg);
		assertTrue(result.getMessage(), result.isOk());
		String employeeId = result.getValue();

		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, true)) {
			// Pro-rated 184 days in second half of non-leap 2025: July 1 to Dec 31
			// 184 / 365 * 12000 = 6049
			Optional<Resource> entryOpt = VacationHelper.findEntitlementEntry(tx, employeeId, 2025);
			assertTrue(entryOpt.isPresent());
			Resource entry = entryOpt.get();
			assertEquals(6049, entry.getInteger(PARAM_VALUE));
			assertEquals(VACATION_ENTITLEMENT, entry.getString(PARAM_VACATION_TYPE));

			VacationAccountSummary summary = VacationHelper.getVacationAccountSummary(tx, employeeId, 2025);
			assertEquals(6049, summary.entitlementMinutes());
			assertEquals(6049, summary.remainingMinutes());
		}
	}

	@Test
	public void testAutomatedVacationCorrectionOnExitDateUpdate() {
		ServiceHandler serviceHandler = runtimeMock.getContainer().getComponent(ServiceHandler.class);
		CreateEmployeeService.EmployeeArgument arg = new CreateEmployeeService.EmployeeArgument();
		arg.username = "emp-auto-exit";
		arg.firstname = "Auto";
		arg.lastname = "Exit";
		arg.personalNumber = "P-AUTO-EXIT";
		arg.teamId = "vac-team";
		arg.locationId = "vac-loc";
		arg.joinDate = LocalDate.of(2025, 1, 1);
		arg.active = true;

		StringResult result = serviceHandler.doService(adminCert, new CreateEmployeeService(), arg);
		assertTrue(result.getMessage(), result.isOk());
		String employeeId = result.getValue();

		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, true)) {
			Optional<Resource> entryOpt = VacationHelper.findEntitlementEntry(tx, employeeId, 2025);
			assertTrue(entryOpt.isPresent());
			assertEquals(12000, entryOpt.get().getInteger(PARAM_VALUE));
		}

		// Update exitDate to June 30, 2025 (181 days: 5951 minutes)
		CreateEmployeeService.UpdateEmployeeArgument updateArg = new CreateEmployeeService.UpdateEmployeeArgument();
		updateArg.id = employeeId;
		updateArg.username = arg.username;
		updateArg.firstname = arg.firstname;
		updateArg.lastname = arg.lastname;
		updateArg.personalNumber = arg.personalNumber;
		updateArg.teamId = arg.teamId;
		updateArg.locationId = arg.locationId;
		updateArg.timezone = "Europe/Zurich";
		updateArg.joinDate = arg.joinDate;
		updateArg.exitDate = LocalDate.of(2025, 6, 30);
		updateArg.active = true;

		ServiceResult updateResult = serviceHandler.doService(adminCert, new UpdateEmployeeService(), updateArg);
		assertTrue(updateResult.getMessage(), updateResult.isOk());

		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, true)) {
			// Base entry is immutable (12000), but a CORRECTION entry was created for delta -6049
			Optional<Resource> entryOpt = VacationHelper.findEntitlementEntry(tx, employeeId, 2025);
			assertTrue(entryOpt.isPresent());
			assertEquals(12000, entryOpt.get().getInteger(PARAM_VALUE));

			List<Resource> corrections = tx.streamResources(TYPE_VACATION_ACCOUNT_ENTRY)
					.filter(e -> e.getRelationId(PARAM_EMPLOYEE).equals(employeeId))
					.filter(e -> VACATION_CORRECTION.equals(e.getString(PARAM_VACATION_TYPE)))
					.toList();
			assertEquals(1, corrections.size());
			assertEquals(-6049, corrections.get(0).getInteger(PARAM_VALUE));
			assertTrue(corrections.get(0).getString(PARAM_COMMENT).contains("exit date update to 2025-06-30"));

			VacationAccountSummary summary = VacationHelper.getVacationAccountSummary(tx, employeeId, 2025);
			assertEquals(5951, summary.entitlementMinutes());
			assertEquals(5951, summary.remainingMinutes());
		}
	}

	@Test
	public void testAutomatedVacationCorrectionOnScheduleUpdate() {
		ServiceHandler serviceHandler = runtimeMock.getContainer().getComponent(ServiceHandler.class);
		CreateEmployeeService.EmployeeArgument arg = new CreateEmployeeService.EmployeeArgument();
		arg.username = "emp-auto-sched";
		arg.firstname = "Auto";
		arg.lastname = "Schedule";
		arg.personalNumber = "P-AUTO-SCHED";
		arg.teamId = "vac-team";
		arg.locationId = "vac-loc";
		arg.joinDate = LocalDate.of(2025, 1, 1);
		arg.active = true;

		StringResult result = serviceHandler.doService(adminCert, new CreateEmployeeService(), arg);
		assertTrue(result.getMessage(), result.isOk());
		String employeeId = result.getValue();

		String scheduleId;
		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, false)) {
			Resource sched = tx.getResourceTemplate(TYPE_EMPLOYMENT_SCHEDULE, true);
			sched.setName("Schedule 100%");
			sched.setRelationId(PARAM_EMPLOYEE, employeeId);
			sched.setDate(PARAM_VALID_FROM, LocalDate.of(2025, 1, 1).atStartOfDay(ZoneId.of("Europe/Zurich")));
			sched.setInteger(PARAM_DAILY_TARGET_MINUTES_MONDAY, 480);
			sched.setInteger(PARAM_DAILY_TARGET_MINUTES_TUESDAY, 480);
			sched.setInteger(PARAM_DAILY_TARGET_MINUTES_WEDNESDAY, 480);
			sched.setInteger(PARAM_DAILY_TARGET_MINUTES_THURSDAY, 480);
			sched.setInteger(PARAM_DAILY_TARGET_MINUTES_FRIDAY, 480);
			sched.setInteger(PARAM_DAILY_TARGET_MINUTES_SATURDAY, 0);
			sched.setInteger(PARAM_DAILY_TARGET_MINUTES_SUNDAY, 0);
			sched.setInteger(PARAM_WEEKLY_TARGET_MINUTES, 2400);
			sched.setDouble(PARAM_EMPLOYMENT_RATE, 1.0);
			initVersion(sched, tx);
			tx.add(sched);

			Resource employee = tx.getResourceBy(TYPE_EMPLOYEE, employeeId, true);
			employee.setRelation(PARAM_CURRENT_SCHEDULE, sched);
			tx.update(employee);

			scheduleId = sched.getId();
			tx.commitOnClose();
		}

		// Update schedule from July 1, 2025 to 50% pensum (240 min / day)
		UpdateScheduleService.UpdateScheduleArgument schedArg = new UpdateScheduleService.UpdateScheduleArgument();
		schedArg.id = scheduleId;
		schedArg.validFrom = LocalDate.of(2025, 7, 1).atStartOfDay(ZoneId.of("Europe/Zurich"));
		schedArg.monday = 240;
		schedArg.tuesday = 240;
		schedArg.wednesday = 240;
		schedArg.thursday = 240;
		schedArg.friday = 240;
		schedArg.saturday = 0;
		schedArg.sunday = 0;

		ServiceResult schedResult = serviceHandler.doService(adminCert, new UpdateScheduleService(), schedArg);
		assertTrue(schedResult.getMessage(), schedResult.isOk());

		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, true)) {
			// Total entitlement for 2025: 181 days at 100% (5950.68) + 184 days at 50% (3024.66) = 8975
			// Delta: 8975 - 12000 = -3025
			List<Resource> corrections = tx.streamResources(TYPE_VACATION_ACCOUNT_ENTRY)
					.filter(e -> e.hasRelation(PARAM_EMPLOYEE) && employeeId.equals(e.getRelationId(PARAM_EMPLOYEE)))
					.filter(e -> VACATION_CORRECTION.equals(e.getString(PARAM_VACATION_TYPE)))
					.toList();
			assertEquals(1, corrections.size());
			assertEquals(-3025, corrections.get(0).getInteger(PARAM_VALUE));
			assertTrue(corrections.get(0).getString(PARAM_COMMENT).contains("update of schedule version"));

			VacationAccountSummary summary = VacationHelper.getVacationAccountSummary(tx, employeeId, 2025);
			assertEquals(8975, summary.entitlementMinutes());
			assertEquals(8975, summary.remainingMinutes());
		}
	}

	@Test
	public void testAutomatedVacationCorrectionOnScheduleCreation() {
		ServiceHandler serviceHandler = runtimeMock.getContainer().getComponent(ServiceHandler.class);
		CreateEmployeeService.EmployeeArgument arg = new CreateEmployeeService.EmployeeArgument();
		arg.username = "emp-create-sched";
		arg.firstname = "Create";
		arg.lastname = "Schedule";
		arg.personalNumber = "P-CREATE-SCHED";
		arg.teamId = "vac-team";
		arg.locationId = "vac-loc";
		arg.joinDate = LocalDate.of(2025, 1, 1);
		arg.active = true;

		StringResult result = serviceHandler.doService(adminCert, new CreateEmployeeService(), arg);
		assertTrue(result.getMessage(), result.isOk());
		String employeeId = result.getValue();

		// Employee was created without a schedule -> initial 100% entitlement = 12000
		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, true)) {
			Optional<Resource> entryOpt = VacationHelper.findEntitlementEntry(tx, employeeId, 2025);
			assertTrue(entryOpt.isPresent());
			assertEquals(12000, entryOpt.get().getInteger(PARAM_VALUE));
			assertEquals("Annual vacation entitlement 2025", entryOpt.get().getString(PARAM_COMMENT));
		}

		// Create a schedule with 80% (4 days of 480 = 1920 min / week)
		CreateScheduleService.CreateScheduleArgument schedArg = new CreateScheduleService.CreateScheduleArgument();
		schedArg.employeeId = employeeId;
		schedArg.validFrom = LocalDate.of(2025, 1, 1).atStartOfDay(ZoneId.of("Europe/Zurich"));
		schedArg.monday = 480;
		schedArg.tuesday = 480;
		schedArg.wednesday = 480;
		schedArg.thursday = 480;
		schedArg.friday = 0;
		schedArg.saturday = 0;
		schedArg.sunday = 0;

		ServiceResult schedResult = serviceHandler.doService(adminCert, new CreateScheduleService(), schedArg);
		assertTrue(schedResult.getMessage(), schedResult.isOk());

		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, true)) {
			// Total entitlement for 2025: 80% of 12000 = 9600. Delta = 9600 - 12000 = -2400
			List<Resource> corrections = tx.streamResources(TYPE_VACATION_ACCOUNT_ENTRY)
					.filter(e -> e.hasRelation(PARAM_EMPLOYEE) && employeeId.equals(e.getRelationId(PARAM_EMPLOYEE)))
					.filter(e -> VACATION_CORRECTION.equals(e.getString(PARAM_VACATION_TYPE)))
					.toList();
			assertEquals(1, corrections.size());
			Resource corr = corrections.get(0);
			assertEquals(-2400, corr.getInteger(PARAM_VALUE));
			assertTrue(corr.getString(PARAM_COMMENT).contains("creation of schedule version"));

			VacationAccountSummary summary = VacationHelper.getVacationAccountSummary(tx, employeeId, 2025);
			assertEquals(9600, summary.entitlementMinutes());
			assertEquals(9600, summary.remainingMinutes());
		}
	}
}
