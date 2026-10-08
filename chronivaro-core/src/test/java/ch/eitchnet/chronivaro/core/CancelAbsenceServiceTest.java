package ch.eitchnet.chronivaro.core;

import ch.eitchnet.chronivaro.core.model.VacationHelper;
import ch.eitchnet.chronivaro.core.service.*;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.privilege.model.Certificate;
import li.strolch.privilege.model.UserRep;
import li.strolch.privilege.model.UserState;
import li.strolch.service.StringArgument;
import li.strolch.service.api.ServiceHandler;
import li.strolch.service.api.ServiceResult;
import li.strolch.testbase.runtime.RuntimeMock;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static ch.eitchnet.chronivaro.core.ChronivaroTestHelper.createEmployee;
import static ch.eitchnet.chronivaro.core.model.ChronivaroConstants.*;
import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;
import static org.junit.Assert.*;

public class CancelAbsenceServiceTest {

	private static RuntimeMock runtimeMock;
	private static Certificate adminCertificate;

	@BeforeClass
	public static void beforeClass() {
		runtimeMock = new RuntimeMock().mockRuntime("target/" + CancelAbsenceServiceTest.class.getSimpleName(),
				"src/test/resources");
		runtimeMock.startContainer();
		adminCertificate = runtimeMock.login("admin", "admin");

		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCertificate, false)) {
			ChronivaroTestHelper.createAbsenceType(tx, "SICK", "Sick");
			tx.commitOnClose();
		}
	}

	@AfterClass
	public static void afterClass() {
		if (runtimeMock != null)
			runtimeMock.destroyRuntime();
	}

	@Test
	public void shouldCancelSubmittedAbsenceAndExcludeFromReport() {
		String employeeId = "emp-cancel-1";
		String userId = "user-cancel-1";
		Certificate userCert;

		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCertificate, false)) {
			Resource employee = createEmployee(tx, employeeId, "Alice Cancel");
			employee.setString(PARAM_USER_ID, userId);
			employee.setString(PARAM_USERNAME, "alice");
			tx.update(employee);

			Resource schedule = tx.getResourceByRelation(employee, PARAM_CURRENT_SCHEDULE, true);
			schedule.setInteger(PARAM_DAILY_TARGET_MINUTES_MONDAY, 480);
			tx.update(schedule);

			tx.commitOnClose();

			UserRep userRep = new UserRep(null, userId, "Alice", "Cancel", UserState.ENABLED, emptySet(),
					Set.of(ROLE_EMPLOYEE, ROLE_MODEL_ACCESSOR), Locale.of("de", "CH"), emptyMap(), null);
			runtimeMock.getPrivilegeHandler().getPrivilegeHandler().addUser(adminCertificate, userRep, userId.toCharArray());
			userCert = runtimeMock.login(userId, userId);
		}

		ServiceHandler serviceHandler = runtimeMock.getServiceHandler();

		// Request Absence (Submitted)
		RequestAbsenceService.RequestAbsenceArgument reqArg = new RequestAbsenceService.RequestAbsenceArgument();
		reqArg.employeeId = employeeId;
		reqArg.absenceTypeCode = "SICK";
		reqArg.start = ZonedDateTime.parse("2026-06-01T00:00:00+02:00[Europe/Zurich]"); // Monday
		reqArg.end = ZonedDateTime.parse("2026-06-01T23:59:59+02:00[Europe/Zurich]");
		reqArg.durationType = DURATION_FULL_DAY;
		ServiceResult reqResult = serviceHandler.doService(userCert, new RequestAbsenceService(), reqArg);
		assertTrue(reqResult.getMessage(), reqResult.isOk());

		String absenceId;
		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCertificate, true)) {
			Resource absence = tx.streamResources(TYPE_ABSENCE)
					.filter(a -> a.getRelationId(PARAM_EMPLOYEE).equals(employeeId))
					.findFirst()
					.orElseThrow();
			absenceId = absence.getId();
			assertEquals(STATE_SUBMITTED, absence.getString(PARAM_STATE));
		}

		// Verify Absence appears in AbsenceReport before cancellation
		AbsenceReportService.AbsenceReportArgument reportArg = new AbsenceReportService.AbsenceReportArgument();
		reportArg.employeeId = employeeId;
		AbsenceReportService.AbsenceReportResult repRes1 = serviceHandler.doService(adminCertificate,
				new AbsenceReportService(), reportArg);
		assertTrue(repRes1.isOk());
		assertEquals(1, repRes1.items.size());

		// Cancel Absence
		ServiceResult cancelResult = serviceHandler.doService(userCert, new CancelAbsenceService(),
				new StringArgument(absenceId));
		assertTrue(cancelResult.getMessage(), cancelResult.isOk());

		// Verify Absence state is CANCELLED
		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCertificate, true)) {
			Resource absence = tx.getResourceBy(TYPE_ABSENCE, absenceId, true);
			assertEquals(STATE_CANCELLED, absence.getString(PARAM_STATE));
		}

		// Verify Absence is excluded from AbsenceReport by default
		AbsenceReportService.AbsenceReportResult repRes2 = serviceHandler.doService(adminCertificate,
				new AbsenceReportService(), reportArg);
		assertTrue(repRes2.isOk());
		assertTrue("Cancelled absence should be excluded from default report", repRes2.items.isEmpty());

		// Verify Absence is found when explicitly searching with state = CANCELLED
		reportArg.state = STATE_CANCELLED;
		AbsenceReportService.AbsenceReportResult repRes3 = serviceHandler.doService(adminCertificate,
				new AbsenceReportService(), reportArg);
		assertTrue(repRes3.isOk());
		assertEquals(1, repRes3.items.size());
		assertEquals(absenceId, repRes3.items.get(0).id());
	}

	@Test
	public void shouldCancelApprovedVacationAndRefundEntitlement() {
		String employeeId = "emp-cancel-vac";
		String userId = "user-cancel-vac";
		Certificate userCert;

		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCertificate, false)) {
			Resource employee = createEmployee(tx, employeeId, "Bob Vacation");
			employee.setString(PARAM_USER_ID, userId);
			employee.setString(PARAM_USERNAME, "bobvac");
			tx.update(employee);

			Resource schedule = tx.getResourceByRelation(employee, PARAM_CURRENT_SCHEDULE, true);
			schedule.setInteger(PARAM_DAILY_TARGET_MINUTES_MONDAY, 480);
			schedule.setInteger(PARAM_DAILY_TARGET_MINUTES_TUESDAY, 480);
			tx.update(schedule);

			// Add 10 days of vacation entitlement
			Resource entry = tx.getResourceTemplate(TYPE_VACATION_ACCOUNT_ENTRY, true);
			entry.setName("Entitlement");
			entry.setRelation(PARAM_EMPLOYEE, employee);
			entry.setDate(PARAM_DATE, ZonedDateTime.parse("2026-01-01T00:00:00Z"));
			entry.setString(PARAM_VACATION_TYPE, VACATION_ENTITLEMENT);
			entry.setInteger(PARAM_VALUE, 10 * 480);
			tx.add(entry);

			tx.commitOnClose();

			UserRep userRep = new UserRep(null, userId, "Bob", "Vac", UserState.ENABLED, emptySet(),
					Set.of(ROLE_EMPLOYEE, ROLE_MODEL_ACCESSOR), Locale.of("de", "CH"), emptyMap(), null);
			runtimeMock.getPrivilegeHandler().getPrivilegeHandler().addUser(adminCertificate, userRep, userId.toCharArray());
			userCert = runtimeMock.login(userId, userId);
		}

		ServiceHandler serviceHandler = runtimeMock.getServiceHandler();

		// Request 2 days of vacation (Monday + Tuesday)
		RequestAbsenceService.RequestAbsenceArgument reqArg = new RequestAbsenceService.RequestAbsenceArgument();
		reqArg.employeeId = employeeId;
		reqArg.absenceTypeCode = "VACATION";
		reqArg.start = ZonedDateTime.parse("2026-07-06T00:00:00+02:00[Europe/Zurich]"); // Monday
		reqArg.end = ZonedDateTime.parse("2026-07-07T23:59:59+02:00[Europe/Zurich]");   // Tuesday
		reqArg.durationType = DURATION_FULL_DAY;
		ServiceResult reqResult = serviceHandler.doService(userCert, new RequestAbsenceService(), reqArg);
		assertTrue(reqResult.getMessage(), reqResult.isOk());

		String absenceId;
		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCertificate, true)) {
			Resource absence = tx.streamResources(TYPE_ABSENCE)
					.filter(a -> a.getRelationId(PARAM_EMPLOYEE).equals(employeeId))
					.findFirst()
					.orElseThrow();
			absenceId = absence.getId();
		}

		// Approve Absence
		ServiceResult appResult = serviceHandler.doService(adminCertificate, new ApproveAbsenceService(),
				new StringArgument(absenceId));
		assertTrue(appResult.getMessage(), appResult.isOk());

		// Verify Balance is reduced by 2 days (960 min)
		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCertificate, true)) {
			int balance = VacationHelper.getVacationBalance(tx, employeeId, ZonedDateTime.parse("2026-07-08T00:00:00Z"));
			assertEquals(8 * 480, balance);
		}

		// Cancel Absence
		ServiceResult cancelResult = serviceHandler.doService(userCert, new CancelAbsenceService(),
				new StringArgument(absenceId));
		assertTrue(cancelResult.getMessage(), cancelResult.isOk());

		// Verify Balance is restored to 10 days (4800 min) and CORRECTION entry exists
		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCertificate, true)) {
			Resource absence = tx.getResourceBy(TYPE_ABSENCE, absenceId, true);
			assertEquals(STATE_CANCELLED, absence.getString(PARAM_STATE));

			List<Resource> entries = tx.streamResources(TYPE_VACATION_ACCOUNT_ENTRY)
					.filter(e -> e.getRelationId(PARAM_EMPLOYEE).equals(employeeId))
					.filter(e -> VACATION_CORRECTION.equals(e.getString(PARAM_VACATION_TYPE)))
					.toList();
			assertEquals(1, entries.size());
			Resource correctionEntry = entries.get(0);
			assertEquals(960, correctionEntry.getInteger(PARAM_VALUE));
			assertEquals(absenceId, correctionEntry.getRelationId(PARAM_ABSENCE));

			int balance = VacationHelper.getVacationBalance(tx, employeeId, ZonedDateTime.parse("2026-07-08T00:00:00Z"));
			assertEquals(10 * 480, balance);
		}
	}

	@Test
	public void shouldFailToCancelWhenPeriodLocked() {
		String employeeId = "emp-cancel-locked";
		String userId = "user-cancel-locked";
		Certificate userCert;

		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCertificate, false)) {
			Resource employee = createEmployee(tx, employeeId, "Charlie Locked");
			employee.setString(PARAM_USER_ID, userId);
			employee.setString(PARAM_USERNAME, "charlie");
			tx.update(employee);

			Resource schedule = tx.getResourceByRelation(employee, PARAM_CURRENT_SCHEDULE, true);
			schedule.setInteger(PARAM_DAILY_TARGET_MINUTES_MONDAY, 480);
			tx.update(schedule);

			tx.commitOnClose();

			UserRep userRep = new UserRep(null, userId, "Charlie", "Locked", UserState.ENABLED, emptySet(),
					Set.of(ROLE_EMPLOYEE, ROLE_MODEL_ACCESSOR), Locale.of("de", "CH"), emptyMap(), null);
			runtimeMock.getPrivilegeHandler().getPrivilegeHandler().addUser(adminCertificate, userRep, userId.toCharArray());
			userCert = runtimeMock.login(userId, userId);
		}

		ServiceHandler serviceHandler = runtimeMock.getServiceHandler();

		// Request Absence in May 2026
		RequestAbsenceService.RequestAbsenceArgument reqArg = new RequestAbsenceService.RequestAbsenceArgument();
		reqArg.employeeId = employeeId;
		reqArg.absenceTypeCode = "SICK";
		reqArg.start = ZonedDateTime.parse("2026-05-11T00:00:00+02:00[Europe/Zurich]"); // Monday
		reqArg.end = ZonedDateTime.parse("2026-05-11T23:59:59+02:00[Europe/Zurich]");
		reqArg.durationType = DURATION_FULL_DAY;
		ServiceResult reqResult = serviceHandler.doService(userCert, new RequestAbsenceService(), reqArg);
		assertTrue(reqResult.getMessage(), reqResult.isOk());

		String absenceId;
		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCertificate, true)) {
			Resource absence = tx.streamResources(TYPE_ABSENCE)
					.filter(a -> a.getRelationId(PARAM_EMPLOYEE).equals(employeeId))
					.findFirst()
					.orElseThrow();
			absenceId = absence.getId();
		}

		// Submit, Approve, and Lock period for May 2026
		YearMonth ym = YearMonth.of(2026, 5);
		serviceHandler.doService(adminCertificate, new SubmitPeriodService(), new PeriodActionArgument(employeeId, ym));
		serviceHandler.doService(adminCertificate, new ApprovePeriodService(), new PeriodActionArgument(employeeId, ym));
		ServiceResult lockResult = serviceHandler.doService(adminCertificate, new LockPeriodService(), new PeriodActionArgument(employeeId, ym));
		assertTrue(lockResult.getMessage(), lockResult.isOk());

		// Attempt to cancel absence in locked period -> should fail
		ServiceResult cancelResult = serviceHandler.doService(userCert, new CancelAbsenceService(),
				new StringArgument(absenceId));
		assertTrue("Cancelling absence in locked period must fail", cancelResult.isNok());
	}
}
