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

public class VacationJournalTest {

	private static final String TARGET_PATH = "target/" + VacationJournalTest.class.getSimpleName();
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
			loc.setId("journal-loc");
			loc.setName("Journal Location");
			tx.add(loc);

			// Create Team
			Resource team = tx.getResourceTemplate(TYPE_TEAM, true);
			team.setId("journal-team");
			team.setName("Journal Team");
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
			Resource loc = tx.getResourceBy(TYPE_LOCATION, "journal-loc", true);
			Resource team = tx.getResourceBy(TYPE_TEAM, "journal-team", true);

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
			schedule.setInteger(PARAM_DAILY_TARGET_MINUTES + "Monday", dailyMin);
			schedule.setInteger(PARAM_DAILY_TARGET_MINUTES + "Tuesday", dailyMin);
			schedule.setInteger(PARAM_DAILY_TARGET_MINUTES + "Wednesday", dailyMin);
			schedule.setInteger(PARAM_DAILY_TARGET_MINUTES + "Thursday", dailyMin);
			schedule.setInteger(PARAM_DAILY_TARGET_MINUTES + "Friday", dailyMin);
			schedule.setInteger(PARAM_DAILY_TARGET_MINUTES + "Saturday", 0);
			schedule.setInteger(PARAM_DAILY_TARGET_MINUTES + "Sunday", 0);
			schedule.setInteger(PARAM_WEEKLY_TARGET_MINUTES, dailyMin * 5);
			tx.add(schedule);

			employee.setRelation(PARAM_CURRENT_SCHEDULE, schedule);
			tx.add(employee);

			tx.commitOnClose();
			return employee;
		}
	}

	@Test
	public void testAppendOnlyJournalLifecycleAndOldestBalanceConsumption() {
		String employeeId = "journal-emp-1";
		createTestEmployee(employeeId, "Test Journal", LocalDate.of(2025, 1, 1), null, 1.0);

		ServiceHandler serviceHandler = runtimeMock.getContainer().getComponent(ServiceHandler.class);

		// Year 1 (2025): Credit entitlement
		CreditVacationEntitlementService.CreditVacationEntitlementArgument creditArg =
				new CreditVacationEntitlementService.CreditVacationEntitlementArgument(employeeId, 2025, false);
		CreditVacationEntitlementService.CreditVacationEntitlementResult creditRes =
				serviceHandler.doService(adminCert, new CreditVacationEntitlementService(), creditArg);
		assertTrue(creditRes.isOk());
		assertEquals(12000, creditRes.entitlementMinutes); // 25 * 480

		// Add a positive correction in 2025: +480 minutes (1 day)
		AddVacationCorrectionService.AddVacationCorrectionArgument corrArg =
				new AddVacationCorrectionService.AddVacationCorrectionArgument();
		corrArg.employeeId = employeeId;
		corrArg.value = 480;
		corrArg.comment = "Anniversary bonus day";
		corrArg.date = LocalDate.of(2025, 6, 1).atStartOfDay(ZoneId.of("Europe/Zurich"));
		ServiceResult corrRes = serviceHandler.doService(adminCert, new AddVacationCorrectionService(), corrArg);
		assertTrue(corrRes.isOk());

		// Add usage in 2025: 4,800 minutes (10 days)
		// Request and approve vacation absence (Mon 2025-07-07 to Fri 2025-07-18 -> 10 working days = 4800 min)
		String absenceId2025;
		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, false)) {
			Resource absence = tx.getResourceTemplate(TYPE_ABSENCE, true);
			absence.setId("abs-2025-01");
			absence.setName("Summer Holiday");
			absence.setRelation(PARAM_EMPLOYEE, tx.getResourceBy(TYPE_EMPLOYEE, employeeId, true));
			absence.setRelation(PARAM_ABSENCE_TYPE, tx.getResourceBy(TYPE_ABSENCE_TYPE, "vacation-type", true));
			absence.setDate(PARAM_START, LocalDate.of(2025, 7, 7).atStartOfDay(ZoneId.of("Europe/Zurich")));
			absence.setDate(PARAM_END, LocalDate.of(2025, 7, 18).atStartOfDay(ZoneId.of("Europe/Zurich")));
			absence.setString(PARAM_DURATION_TYPE, DURATION_FULL_DAY);
			absence.setString(PARAM_STATE, STATE_SUBMITTED);
			tx.add(absence);
			absenceId2025 = absence.getId();
			tx.commitOnClose();
		}

		ServiceResult appRes = serviceHandler.doService(adminCert, new ApproveAbsenceService(), new StringArgument(absenceId2025));
		assertTrue(appRes.isOk());

		// Query 2025 account summary via GetVacationAccountSummaryService
		GetVacationAccountSummaryService.GetVacationAccountSummaryArgument summaryArg =
				new GetVacationAccountSummaryService.GetVacationAccountSummaryArgument(employeeId, 2025);
		GetVacationAccountSummaryService.GetVacationAccountSummaryResult summaryRes =
				serviceHandler.doService(adminCert, new GetVacationAccountSummaryService(), summaryArg);
		assertTrue(summaryRes.isOk());
		VacationAccountSummary sum2025 = summaryRes.summary;
		assertEquals(0, sum2025.carryOverMinutes());
		assertEquals(12000, sum2025.entitlementMinutes());
		assertEquals(480, sum2025.correctionsMinutes());
		assertEquals(4800, sum2025.usageMinutes()); // 10 working days * 480
		assertEquals(7680, sum2025.remainingMinutes()); // 12000 + 480 - 4800
		assertEquals(3, summaryRes.entries.size()); // Entitlement + Correction + Usage

		// Year 2 (2026): Credit entitlement 12,000 min
		CreditVacationEntitlementService.CreditVacationEntitlementArgument creditArg2026 =
				new CreditVacationEntitlementService.CreditVacationEntitlementArgument(employeeId, 2026, false);
		CreditVacationEntitlementService.CreditVacationEntitlementResult creditRes2026 =
				serviceHandler.doService(adminCert, new CreditVacationEntitlementService(), creditArg2026);
		assertTrue(creditRes2026.isOk());

		// Query 2026 account summary before any usage in 2026
		GetVacationAccountSummaryService.GetVacationAccountSummaryResult sumRes2026Before =
				serviceHandler.doService(adminCert, new GetVacationAccountSummaryService(),
						new GetVacationAccountSummaryService.GetVacationAccountSummaryArgument(employeeId, 2026));
		assertTrue(sumRes2026Before.isOk());
		VacationAccountSummary s2026Before = sumRes2026Before.summary;
		assertEquals(7680, s2026Before.carryOverMinutes()); // exact remaining from 2025
		assertEquals(12000, s2026Before.entitlementMinutes());
		assertEquals(0, s2026Before.correctionsMinutes());
		assertEquals(0, s2026Before.usageMinutes());
		assertEquals(19680, s2026Before.remainingMinutes()); // 7680 carry-over + 12000 entitlement

		// Add 20 days usage in 2026: 20 * 480 = 9600 min (Mon 2026-08-03 to Fri 2026-08-28)
		String absenceId2026;
		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, false)) {
			Resource absence = tx.getResourceTemplate(TYPE_ABSENCE, true);
			absence.setId("abs-2026-01");
			absence.setName("Long Trip");
			absence.setRelation(PARAM_EMPLOYEE, tx.getResourceBy(TYPE_EMPLOYEE, employeeId, true));
			absence.setRelation(PARAM_ABSENCE_TYPE, tx.getResourceBy(TYPE_ABSENCE_TYPE, "vacation-type", true));
			absence.setDate(PARAM_START, LocalDate.of(2026, 8, 3).atStartOfDay(ZoneId.of("Europe/Zurich")));
			absence.setDate(PARAM_END, LocalDate.of(2026, 8, 28).atStartOfDay(ZoneId.of("Europe/Zurich")));
			absence.setString(PARAM_DURATION_TYPE, DURATION_FULL_DAY);
			absence.setString(PARAM_STATE, STATE_SUBMITTED);
			tx.add(absence);
			absenceId2026 = absence.getId();
			tx.commitOnClose();
		}

		ServiceResult appRes2026 = serviceHandler.doService(adminCert, new ApproveAbsenceService(), new StringArgument(absenceId2026));
		assertTrue(appRes2026.isOk());

		// Query 2026 account summary after usage
		GetVacationAccountSummaryService.GetVacationAccountSummaryResult sumRes2026After =
				serviceHandler.doService(adminCert, new GetVacationAccountSummaryService(),
						new GetVacationAccountSummaryService.GetVacationAccountSummaryArgument(employeeId, 2026));
		assertTrue(sumRes2026After.isOk());
		VacationAccountSummary s2026After = sumRes2026After.summary;
		assertEquals(7680, s2026After.carryOverMinutes());
		assertEquals(12000, s2026After.entitlementMinutes());
		assertEquals(0, s2026After.correctionsMinutes());
		assertEquals(9600, s2026After.usageMinutes());
		assertEquals(10080, s2026After.remainingMinutes()); // 7680 + 12000 - 9600 = 10080

		// Verify audit logs for vacation journal
		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, true)) {
			List<Resource> auditLogs = new AuditEventSearch()
					.forElementType(TYPE_VACATION_ACCOUNT_ENTRY)
					.search(tx)
					.toList();
			assertFalse(auditLogs.isEmpty());
		}
	}

	@Test
	public void testNegativeCorrectionRejectionWhenInsufficientBalance() {
		String employeeId = "journal-emp-insufficient";
		createTestEmployee(employeeId, "Test Insufficient", LocalDate.of(2026, 1, 1), null, 1.0);

		ServiceHandler serviceHandler = runtimeMock.getContainer().getComponent(ServiceHandler.class);

		// Credit 1000 minutes via positive correction
		AddVacationCorrectionService.AddVacationCorrectionArgument posArg =
				new AddVacationCorrectionService.AddVacationCorrectionArgument();
		posArg.employeeId = employeeId;
		posArg.value = 1000;
		posArg.comment = "Initial Credit";
		ServiceResult posRes = serviceHandler.doService(adminCert, new AddVacationCorrectionService(), posArg);
		assertTrue(posRes.isOk());

		// Try to deduct 1500 minutes (which would lead to -500 balance)
		AddVacationCorrectionService.AddVacationCorrectionArgument negArg =
				new AddVacationCorrectionService.AddVacationCorrectionArgument();
		negArg.employeeId = employeeId;
		negArg.value = -1500;
		negArg.comment = "Invalid negative deduction";
		ServiceResult negRes = serviceHandler.doService(adminCert, new AddVacationCorrectionService(), negArg);
		assertFalse("Negative deduction exceeding balance must fail", negRes.isOk());

		// Verify balance remains 1000 and only 1 entry exists
		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, true)) {
			int balance = VacationHelper.getVacationBalance(tx, employeeId);
			assertEquals(1000, balance);

			List<Resource> entries = new VacationAccountEntrySearch()
					.forEmployee(employeeId)
					.search(tx)
					.toList();
			assertEquals(1, entries.size());
		}
	}

	@Test
	public void testAbsenceApprovalAndCancellationJournalReconciliation() {
		String employeeId = "journal-emp-cancel";
		createTestEmployee(employeeId, "Test Cancel", LocalDate.of(2026, 1, 1), null, 1.0);

		ServiceHandler serviceHandler = runtimeMock.getContainer().getComponent(ServiceHandler.class);

		// Credit entitlement for 2026
		ServiceResult creditRes = serviceHandler.doService(adminCert, new CreditVacationEntitlementService(),
				new CreditVacationEntitlementService.CreditVacationEntitlementArgument(employeeId, 2026, false));
		assertTrue(creditRes.isOk());

		// Request 3 days vacation (1440 min: Mon 2026-06-01 to Wed 2026-06-03)
		String absenceId;
		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, false)) {
			Resource absence = tx.getResourceTemplate(TYPE_ABSENCE, true);
			absence.setId("abs-cancel-01");
			absence.setName("Short Trip");
			absence.setRelation(PARAM_EMPLOYEE, tx.getResourceBy(TYPE_EMPLOYEE, employeeId, true));
			absence.setRelation(PARAM_ABSENCE_TYPE, tx.getResourceBy(TYPE_ABSENCE_TYPE, "vacation-type", true));
			absence.setDate(PARAM_START, LocalDate.of(2026, 6, 1).atStartOfDay(ZoneId.of("Europe/Zurich")));
			absence.setDate(PARAM_END, LocalDate.of(2026, 6, 3).atStartOfDay(ZoneId.of("Europe/Zurich")));
			absence.setString(PARAM_DURATION_TYPE, DURATION_FULL_DAY);
			absence.setString(PARAM_STATE, STATE_SUBMITTED);
			tx.add(absence);
			absenceId = absence.getId();
			tx.commitOnClose();
		}

		// Approve absence
		ServiceResult appRes = serviceHandler.doService(adminCert, new ApproveAbsenceService(), new StringArgument(absenceId));
		assertTrue(appRes.isOk());

		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, true)) {
			int balanceAfterApprove = VacationHelper.getVacationBalance(tx, employeeId);
			assertEquals(12000 - 1440, balanceAfterApprove);

			List<Resource> usageEntries = new VacationAccountEntrySearch()
					.forEmployee(employeeId)
					.forVacationType(VACATION_USAGE)
					.forAbsence(absenceId)
					.search(tx)
					.toList();
			assertEquals(1, usageEntries.size());
			Resource usage = usageEntries.getFirst();
			assertEquals(-1440, (int) usage.getInteger(PARAM_VALUE));
			assertEquals("Vacation usage for absence " + absenceId, usage.getString(PARAM_COMMENT));
		}

		// Cancel absence -> must create refund correction entry
		ServiceResult cancelRes = serviceHandler.doService(adminCert, new CancelAbsenceService(), new StringArgument(absenceId));
		assertTrue(cancelRes.isOk());

		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, true)) {
			int balanceAfterCancel = VacationHelper.getVacationBalance(tx, employeeId);
			assertEquals(12000, balanceAfterCancel);

			List<Resource> cancelEntries = new VacationAccountEntrySearch()
					.forEmployee(employeeId)
					.forVacationType(VACATION_CORRECTION)
					.forAbsence(absenceId)
					.search(tx)
					.toList();
			assertEquals(1, cancelEntries.size());
			Resource refund = cancelEntries.getFirst();
			assertEquals(1440, (int) refund.getInteger(PARAM_VALUE));
			assertEquals("Vacation cancellation refund for absence " + absenceId, refund.getString(PARAM_COMMENT));
		}
	}

	@Test
	public void testVacationAccountEntrySearchFiltering() {
		String employeeId = "journal-emp-search";
		createTestEmployee(employeeId, "Test Search", LocalDate.of(2026, 1, 1), null, 1.0);

		ServiceHandler serviceHandler = runtimeMock.getContainer().getComponent(ServiceHandler.class);

		// Add entries in different years and types
		AddVacationCorrectionService.AddVacationCorrectionArgument c1 =
				new AddVacationCorrectionService.AddVacationCorrectionArgument();
		c1.employeeId = employeeId;
		c1.value = 100;
		c1.comment = "2025 Correction";
		c1.date = LocalDate.of(2025, 5, 10).atStartOfDay(ZoneId.of("Europe/Zurich"));
		assertTrue(serviceHandler.doService(adminCert, new AddVacationCorrectionService(), c1).isOk());

		AddVacationCorrectionService.AddVacationCorrectionArgument c2 =
				new AddVacationCorrectionService.AddVacationCorrectionArgument();
		c2.employeeId = employeeId;
		c2.value = 200;
		c2.comment = "2026 Correction";
		c2.date = LocalDate.of(2026, 6, 15).atStartOfDay(ZoneId.of("Europe/Zurich"));
		assertTrue(serviceHandler.doService(adminCert, new AddVacationCorrectionService(), c2).isOk());

		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, true)) {
			List<Resource> allForEmp = new VacationAccountEntrySearch()
					.forEmployee(employeeId)
					.search(tx)
					.toList();
			assertEquals(2, allForEmp.size());

			List<Resource> entries2025 = new VacationAccountEntrySearch()
					.forEmployee(employeeId)
					.forYear(2025, ZoneId.of("Europe/Zurich"))
					.search(tx)
					.toList();
			assertEquals(1, entries2025.size());
			assertEquals("2025 Correction", entries2025.getFirst().getString(PARAM_COMMENT));

			List<Resource> entries2026 = new VacationAccountEntrySearch()
					.forEmployee(employeeId)
					.forYear(2026, ZoneId.of("Europe/Zurich"))
					.search(tx)
					.toList();
			assertEquals(1, entries2026.size());
			assertEquals("2026 Correction", entries2026.getFirst().getString(PARAM_COMMENT));
		}
	}

	@Test
	public void testVacationAccountEntryCreatedAtTimestamp() {
		String employeeId = "journal-emp-created-at";
		createTestEmployee(employeeId, "Created At Test", LocalDate.of(2026, 1, 1), null, 1.0);

		ServiceHandler serviceHandler = runtimeMock.getContainer().getComponent(ServiceHandler.class);

		ZonedDateTime beforeCredit = ZonedDateTime.now(ZoneId.of("Europe/Zurich")).minusSeconds(2);
		ServiceResult creditRes = serviceHandler.doService(adminCert, new CreditVacationEntitlementService(),
				new CreditVacationEntitlementService.CreditVacationEntitlementArgument(employeeId, 2026, false));
		assertTrue(creditRes.isOk());
		ZonedDateTime afterCredit = ZonedDateTime.now(ZoneId.of("Europe/Zurich")).plusSeconds(2);

		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, true)) {
			List<Resource> entries = new VacationAccountEntrySearch()
					.forEmployee(employeeId)
					.forVacationType(VACATION_ENTITLEMENT)
					.search(tx)
					.toList();
			assertEquals(1, entries.size());
			Resource entry = entries.getFirst();
			assertTrue("Entry must have createdAt parameter", entry.hasParameter(PARAM_CREATED_AT));
			ZonedDateTime createdAt = entry.getDate(PARAM_CREATED_AT);
			assertNotNull("createdAt must not be null", createdAt);
			assertTrue("createdAt must be after beforeCredit", !createdAt.isBefore(beforeCredit));
			assertTrue("createdAt must be before afterCredit", !createdAt.isAfter(afterCredit));
		}
	}
}
