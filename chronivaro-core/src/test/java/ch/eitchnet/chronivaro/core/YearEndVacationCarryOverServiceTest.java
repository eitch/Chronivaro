package ch.eitchnet.chronivaro.core;

import ch.eitchnet.chronivaro.core.model.VacationAccountSummary;
import ch.eitchnet.chronivaro.core.model.VacationHelper;
import ch.eitchnet.chronivaro.core.search.AuditEventSearch;
import ch.eitchnet.chronivaro.core.search.VacationAccountEntrySearch;
import ch.eitchnet.chronivaro.core.service.*;
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
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static ch.eitchnet.chronivaro.core.model.ChronivaroConstants.*;
import static org.junit.Assert.*;

public class YearEndVacationCarryOverServiceTest {

	private static final String TARGET_PATH = "target/" + YearEndVacationCarryOverServiceTest.class.getSimpleName();
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
			loc.setId("ye-loc");
			loc.setName("YearEnd Location");
			tx.add(loc);

			// Create Team
			Resource team = tx.getResourceTemplate(TYPE_TEAM, true);
			team.setId("ye-team");
			team.setName("YearEnd Team");
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
			Resource loc = tx.getResourceBy(TYPE_LOCATION, "ye-loc", true);
			Resource team = tx.getResourceBy(TYPE_TEAM, "ye-team", true);

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
			schedule.setInteger(PARAM_DAILY_TARGET_MINUTES_MONDAY, dailyMin);
			schedule.setInteger(PARAM_DAILY_TARGET_MINUTES_TUESDAY, dailyMin);
			schedule.setInteger(PARAM_DAILY_TARGET_MINUTES_WEDNESDAY, dailyMin);
			schedule.setInteger(PARAM_DAILY_TARGET_MINUTES_THURSDAY, dailyMin);
			schedule.setInteger(PARAM_DAILY_TARGET_MINUTES_FRIDAY, dailyMin);
			schedule.setInteger(PARAM_DAILY_TARGET_MINUTES_SATURDAY, 0);
			schedule.setInteger(PARAM_DAILY_TARGET_MINUTES_SUNDAY, 0);

			tx.add(employee);
			tx.add(schedule);
			tx.commitOnClose();
			return employee;
		}
	}

	@Test
	public void testYearEndCarryOverSingleEmployee() {
		String empId = "ye-emp-single";
		createTestEmployee(empId, "Alice Single", LocalDate.of(2025, 1, 1), null, 1.0);

		ServiceHandler serviceHandler = runtimeMock.getContainer().getComponent(ServiceHandler.class);

		// 1. Credit 2025 entitlement (12000 min)
		ServiceResult credit2025 = serviceHandler.doService(adminCert, new CreditVacationEntitlementService(),
				new CreditVacationEntitlementService.CreditVacationEntitlementArgument(empId, 2025, false));
		assertTrue(credit2025.isOk());

		// 2. Add 2025 Correction (+480 min bonus)
		AddVacationCorrectionService.AddVacationCorrectionArgument corrArg =
				new AddVacationCorrectionService.AddVacationCorrectionArgument();
		corrArg.employeeId = empId;
		corrArg.value = 480;
		corrArg.comment = "Performance bonus";
		corrArg.date = ZonedDateTime.of(2025, 6, 1, 0, 0, 0, 0, ZoneId.of("Europe/Zurich"));
		assertTrue(serviceHandler.doService(adminCert, new AddVacationCorrectionService(), corrArg).isOk());

		// 3. Take 10 days vacation in 2025 (4800 min)
		String absId;
		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, false)) {
			Resource absence = tx.getResourceTemplate(TYPE_ABSENCE, true);
			absence.setId("ye-abs-2025");
			absence.setName("Summer Break");
			absence.setRelation(PARAM_EMPLOYEE, tx.getResourceBy(TYPE_EMPLOYEE, empId, true));
			absence.setRelation(PARAM_ABSENCE_TYPE, tx.getResourceBy(TYPE_ABSENCE_TYPE, "vacation-type", true));
			absence.setDate(PARAM_START, LocalDate.of(2025, 7, 7).atStartOfDay(ZoneId.of("Europe/Zurich")));
			absence.setDate(PARAM_END, LocalDate.of(2025, 7, 18).atStartOfDay(ZoneId.of("Europe/Zurich")));
			absence.setString(PARAM_DURATION_TYPE, DURATION_FULL_DAY);
			absence.setString(PARAM_STATE, STATE_SUBMITTED);
			tx.add(absence);
			absId = absence.getId();
			tx.commitOnClose();
		}
		assertTrue(serviceHandler.doService(adminCert, new ApproveAbsenceService(), new StringArgument(absId)).isOk());

		// Verify 2025 summary before year-end carry over
		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, true)) {
			VacationAccountSummary summary2025 = VacationHelper.getVacationAccountSummary(tx, empId, 2025);
			assertEquals(12000, summary2025.entitlementMinutes());
			assertEquals(480, summary2025.correctionsMinutes());
			assertEquals(4800, summary2025.usageMinutes());
			assertEquals(7680, summary2025.remainingMinutes()); // 12000 + 480 - 4800 = 7680
		}

		// 4. Run YearEndVacationCarryOverService for 2025 -> 2026
		YearEndVacationCarryOverService.YearEndVacationCarryOverArgument carryOverArg =
				new YearEndVacationCarryOverService.YearEndVacationCarryOverArgument(empId, 2025, 2026);
		YearEndVacationCarryOverService.YearEndVacationCarryOverResult carryOverResult =
				serviceHandler.doService(adminCert, new YearEndVacationCarryOverService(), carryOverArg);
		assertTrue(carryOverResult.isOk());
		assertEquals(1, carryOverResult.processedEmployeesCount);
		assertEquals(7680, carryOverResult.totalCarryOverMinutes);
		assertEquals(1, carryOverResult.createdEntryIds.size());

		// 5. Credit 2026 annual entitlement (12000 min)
		ServiceResult credit2026 = serviceHandler.doService(adminCert, new CreditVacationEntitlementService(),
				new CreditVacationEntitlementService.CreditVacationEntitlementArgument(empId, 2026, false));
		assertTrue(credit2026.isOk());

		// Verify 2026 vacation account summary
		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, true)) {
			VacationAccountSummary summary2026 = VacationHelper.getVacationAccountSummary(tx, empId, 2026);
			assertEquals(7680, summary2026.carryOverMinutes());
			assertEquals(12000, summary2026.entitlementMinutes());
			assertEquals(0, summary2026.correctionsMinutes());
			assertEquals(0, summary2026.usageMinutes());
			assertEquals(19680, summary2026.remainingMinutes()); // 7680 + 12000 = 19680

			int balance = VacationHelper.getVacationBalance(tx, empId, ZonedDateTime.of(2026, 1, 15, 0, 0, 0, 0, ZoneId.of("Europe/Zurich")));
			assertEquals(19680, balance);
		}

		// 6. Test FIFO usage consumption: Take 20 days in 2026 (9600 min)
		String abs2026Id;
		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, false)) {
			Resource absence = tx.getResourceTemplate(TYPE_ABSENCE, true);
			absence.setId("ye-abs-2026");
			absence.setName("Spring Holiday");
			absence.setRelation(PARAM_EMPLOYEE, tx.getResourceBy(TYPE_EMPLOYEE, empId, true));
			absence.setRelation(PARAM_ABSENCE_TYPE, tx.getResourceBy(TYPE_ABSENCE_TYPE, "vacation-type", true));
			absence.setDate(PARAM_START, LocalDate.of(2026, 3, 2).atStartOfDay(ZoneId.of("Europe/Zurich")));
			absence.setDate(PARAM_END, LocalDate.of(2026, 3, 27).atStartOfDay(ZoneId.of("Europe/Zurich")));
			absence.setString(PARAM_DURATION_TYPE, DURATION_FULL_DAY);
			absence.setString(PARAM_STATE, STATE_SUBMITTED);
			tx.add(absence);
			abs2026Id = absence.getId();
			tx.commitOnClose();
		}
		assertTrue(serviceHandler.doService(adminCert, new ApproveAbsenceService(), new StringArgument(abs2026Id)).isOk());

		// 7. Verify updated 2026 summary (consumed 7680 carry-over + 1920 2026 entitlement, leaving 10080 min)
		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, true)) {
			VacationAccountSummary summary2026After = VacationHelper.getVacationAccountSummary(tx, empId, 2026);
			assertEquals(7680, summary2026After.carryOverMinutes());
			assertEquals(12000, summary2026After.entitlementMinutes());
			assertEquals(9600, summary2026After.usageMinutes());
			assertEquals(10080, summary2026After.remainingMinutes());
		}
	}

	@Test
	public void testYearEndCarryOverIdempotencyAndForceAdjustment() {
		String empId = "ye-emp-idemp";
		createTestEmployee(empId, "Bob Idemp", LocalDate.of(2025, 1, 1), null, 1.0);

		ServiceHandler serviceHandler = runtimeMock.getContainer().getComponent(ServiceHandler.class);

		// Credit 2025 entitlement (12000 min)
		assertTrue(serviceHandler.doService(adminCert, new CreditVacationEntitlementService(),
				new CreditVacationEntitlementService.CreditVacationEntitlementArgument(empId, 2025, false)).isOk());

		// Run carry over 2025 -> 2026
		YearEndVacationCarryOverService.YearEndVacationCarryOverArgument arg =
				new YearEndVacationCarryOverService.YearEndVacationCarryOverArgument(empId, 2025, 2026);
		YearEndVacationCarryOverService.YearEndVacationCarryOverResult res1 =
				serviceHandler.doService(adminCert, new YearEndVacationCarryOverService(), arg);
		assertTrue(res1.isOk());
		assertEquals(12000, res1.totalCarryOverMinutes);
		assertEquals(1, res1.createdEntryIds.size());

		// Re-run carry over without force -> should not create duplicates
		YearEndVacationCarryOverService.YearEndVacationCarryOverResult res2 =
				serviceHandler.doService(adminCert, new YearEndVacationCarryOverService(), arg);
		assertTrue(res2.isOk());
		assertEquals(0, res2.createdEntryIds.size());

		// Now add a late adjustment in 2025 (-960 min)
		AddVacationCorrectionService.AddVacationCorrectionArgument corrArg =
				new AddVacationCorrectionService.AddVacationCorrectionArgument();
		corrArg.employeeId = empId;
		corrArg.value = -960;
		corrArg.comment = "Late 2025 deduction";
		corrArg.date = ZonedDateTime.of(2025, 12, 1, 0, 0, 0, 0, ZoneId.of("Europe/Zurich"));
		assertTrue(serviceHandler.doService(adminCert, new AddVacationCorrectionService(), corrArg).isOk());

		// Re-run with force = true
		arg.force = true;
		YearEndVacationCarryOverService.YearEndVacationCarryOverResult res3 =
				serviceHandler.doService(adminCert, new YearEndVacationCarryOverService(), arg);
		assertTrue(res3.isOk());
		assertEquals(1, res3.createdEntryIds.size()); // Appended a correction adjustment

		// Verify 2026 summary reflects 11040 carry over (12000 - 960)
		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, true)) {
			VacationAccountSummary summary2026 = VacationHelper.getVacationAccountSummary(tx, empId, 2026);
			assertEquals(11040, summary2026.carryOverMinutes());
		}
	}

	@Test
	public void testRecalculateVacationEntitlementImmutability() {
		String empId = "ye-emp-immut";
		createTestEmployee(empId, "Charlie Immut", LocalDate.of(2026, 1, 1), null, 1.0);

		ServiceHandler serviceHandler = runtimeMock.getContainer().getComponent(ServiceHandler.class);

		// 1. Initial credit for 2026 (12000 min)
		CreditVacationEntitlementService.CreditVacationEntitlementArgument arg =
				new CreditVacationEntitlementService.CreditVacationEntitlementArgument(empId, 2026, false);
		CreditVacationEntitlementService.CreditVacationEntitlementResult res =
				serviceHandler.doService(adminCert, new CreditVacationEntitlementService(), arg);
		assertTrue(res.isOk());
		assertEquals(12000, res.entitlementMinutes);
		String originalEntryId = res.entryId;

		// 2. Change employee schedule to 80% (which yields 9600 annual vacation minutes)
		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, false)) {
			Resource schedule = tx.getResourceBy(TYPE_EMPLOYMENT_SCHEDULE, "sched-" + empId, true).getClone();
			schedule.setDouble(PARAM_EMPLOYMENT_RATE, 0.8);
			schedule.setInteger(PARAM_DAILY_TARGET_MINUTES_MONDAY, 384);
			schedule.setInteger(PARAM_DAILY_TARGET_MINUTES_TUESDAY, 384);
			schedule.setInteger(PARAM_DAILY_TARGET_MINUTES_WEDNESDAY, 384);
			schedule.setInteger(PARAM_DAILY_TARGET_MINUTES_THURSDAY, 384);
			schedule.setInteger(PARAM_DAILY_TARGET_MINUTES_FRIDAY, 384);
			tx.update(schedule);
			tx.commitOnClose();
		}

		// 3. Re-run entitlement with forceRecalculate = true
		arg.forceRecalculate = true;
		CreditVacationEntitlementService.CreditVacationEntitlementResult recalcRes =
				serviceHandler.doService(adminCert, new CreditVacationEntitlementService(), arg);
		assertTrue(recalcRes.isOk());
		assertEquals(9600, recalcRes.entitlementMinutes);
		assertNotEquals(originalEntryId, recalcRes.entryId); // New correction entry ID

		// 4. Verify original ENTITLEMENT entry is intact (immutable: still 12000)
		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, true)) {
			Resource originalEntry = tx.getResourceBy(TYPE_VACATION_ACCOUNT_ENTRY, originalEntryId, true);
			assertEquals(12000, (int) originalEntry.getInteger(PARAM_VALUE));
			assertEquals(VACATION_ENTITLEMENT, originalEntry.getString(PARAM_VACATION_TYPE));

			// Verify new correction entry of -2400 exists
			Resource corrEntry = tx.getResourceBy(TYPE_VACATION_ACCOUNT_ENTRY, recalcRes.entryId, true);
			assertEquals(-2400, (int) corrEntry.getInteger(PARAM_VALUE));
			assertEquals(VACATION_CORRECTION, corrEntry.getString(PARAM_VACATION_TYPE));

			// Verify total balance is 9600
			assertEquals(9600, VacationHelper.getVacationBalance(tx, empId));

			// Verify audit event for correction creation
			List<Resource> audits = new AuditEventSearch()
					.forElementType(TYPE_VACATION_ACCOUNT_ENTRY)
					.forElementId(corrEntry.getId())
					.forAction(AUDIT_ACTION_CREATE)
					.search(tx)
					.toList();
			assertFalse("Audit event for correction creation must exist", audits.isEmpty());
		}
	}
}
