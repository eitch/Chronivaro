package ch.atexxi.chronivaro.core;

import ch.atexxi.chronivaro.core.model.VacationHelper;
import ch.atexxi.chronivaro.core.service.ApproveAbsenceService;
import ch.atexxi.chronivaro.core.service.CancelAbsenceService;
import ch.atexxi.chronivaro.core.service.RejectAbsenceService;
import ch.atexxi.chronivaro.core.service.RequestAbsenceService;
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

import java.time.ZonedDateTime;
import java.util.Locale;

import static ch.atexxi.chronivaro.core.ChronivaroTestHelper.createEmployee;
import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;
import static java.util.Collections.*;
import static org.junit.Assert.*;

public class AbsenceServiceTest {

	private static RuntimeMock runtimeMock;
	private static Certificate certificate;

	@BeforeClass
	public static void beforeClass() {
		runtimeMock = new RuntimeMock().mockRuntime("target/" + AbsenceServiceTest.class.getSimpleName(),
				"src/test/resources");
		runtimeMock.startContainer();
		certificate = runtimeMock.login("admin", "admin");

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, false)) {
			Resource vacation = tx.getResourceTemplate(TYPE_ABSENCE_TYPE, true);
			vacation.setId("vacation");
			vacation.setName("Vacation");
			vacation.setString(PARAM_CODE, "VACATION");
			vacation.setBoolean(PARAM_REDUCE_VACATION_CREDIT, true);
			tx.add(vacation);

			Resource sick = tx.getResourceTemplate(TYPE_ABSENCE_TYPE, true);
			sick.setId("sick");
			sick.setName("Sick");
			sick.setString(PARAM_CODE, "SICK");
			sick.setBoolean(PARAM_REDUCE_VACATION_CREDIT, false);
			tx.add(sick);

			tx.commitOnClose();
		}
	}

	@AfterClass
	public static void afterClass() {
		if (runtimeMock != null)
			runtimeMock.destroyRuntime();
	}

	@Test
	public void shouldRequestAndApproveAbsence() {
		String employeeId = "emp4";

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, false)) {
			Resource employee = createEmployee(tx, employeeId, "Jane Doe");
			Resource schedule = tx.getResourceByRelation(employee, PARAM_CURRENT_SCHEDULE, true);
			schedule.setInteger(PARAM_DAILY_TARGET_MINUTES + "Monday", 480);
			tx.update(schedule);

			// Add initial vacation entitlement
			Resource entry = tx.getResourceTemplate(TYPE_VACATION_ACCOUNT_ENTRY, true);
			entry.setName("Entitlement");
			entry.setRelation(PARAM_EMPLOYEE, employee);
			entry.setDate(PARAM_DATE, ZonedDateTime.parse("2026-01-01T00:00:00Z"));
			entry.setString(PARAM_VACATION_TYPE, VACATION_ENTITLEMENT);
			entry.setInteger(PARAM_VALUE, 20 * 480); // 20 days
			tx.add(entry);

			tx.commitOnClose();
		}

		ServiceHandler serviceHandler = runtimeMock.getServiceHandler();

		// Request Absence
		RequestAbsenceService.RequestAbsenceArgument reqArg = new RequestAbsenceService.RequestAbsenceArgument();
		reqArg.employeeId = employeeId;
		reqArg.absenceTypeCode = "VACATION";
		reqArg.start = ZonedDateTime.parse("2026-02-02T00:00:00+01:00[Europe/Zurich]"); // Monday
		reqArg.end = ZonedDateTime.parse("2026-02-02T23:59:59+01:00[Europe/Zurich]");
		reqArg.durationType = DURATION_FULL_DAY;
		ServiceResult reqResult = serviceHandler.doService(certificate, new RequestAbsenceService(), reqArg);
		assertTrue(reqResult.getMessage(), reqResult.isOk());

		String absenceId;
		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			Resource absence = tx.streamResources(TYPE_ABSENCE)
					.filter(a -> a.getRelationId(PARAM_EMPLOYEE).equals(employeeId))
					.findFirst()
					.orElseThrow();
			absenceId = absence.getId();
			assertEquals(STATE_SUBMITTED, absence.getString(PARAM_STATE));
		}

		// Approve Absence
		ServiceResult appResult = serviceHandler.doService(certificate, new ApproveAbsenceService(),
				new StringArgument(absenceId));
		assertTrue(appResult.getMessage(), appResult.isOk());

		// Verify Balance
		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			Resource absence = tx.getResourceBy(TYPE_ABSENCE, absenceId, true);
			assertEquals(STATE_APPROVED, absence.getString(PARAM_STATE));

			int balance = VacationHelper.getVacationBalance(tx, employeeId, ZonedDateTime.now());
			assertEquals((20 - 1) * 480, balance);
		}
	}

	@Test
	public void shouldRejectAbsence() {
		String employeeId = "emp5";

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, false)) {
			createEmployee(tx, employeeId, "Jack Doe");

			tx.commitOnClose();
		}

		ServiceHandler serviceHandler = runtimeMock.getServiceHandler();

		// Request Absence
		RequestAbsenceService.RequestAbsenceArgument reqArg = new RequestAbsenceService.RequestAbsenceArgument();
		reqArg.employeeId = employeeId;
		reqArg.absenceTypeCode = "SICK";
		reqArg.start = ZonedDateTime.parse("2026-03-02T00:00:00+01:00[Europe/Zurich]");
		reqArg.end = ZonedDateTime.parse("2026-03-02T23:59:59+01:00[Europe/Zurich]");
		reqArg.durationType = DURATION_FULL_DAY;
		ServiceResult reqResult = serviceHandler.doService(certificate, new RequestAbsenceService(), reqArg);
		assertTrue(reqResult.getMessage(), reqResult.isOk());

		String absenceId;
		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			Resource absence = tx
					.streamResources(TYPE_ABSENCE)
					.filter(a -> a.getRelationId(PARAM_EMPLOYEE).equals(employeeId))
					.findFirst()
					.orElseThrow();
			absenceId = absence.getId();
		}

		// Reject Absence - Missing Comment
		RejectAbsenceService.RejectAbsenceArgument rejArg = new RejectAbsenceService.RejectAbsenceArgument();
		rejArg.absenceId = absenceId;
		ServiceResult rejResult = serviceHandler.doService(certificate, new RejectAbsenceService(), rejArg);
		assertFalse("Should fail without comment", rejResult.isOk());

		// Reject Absence - Success
		rejArg.comment = "Not allowed";
		rejResult = serviceHandler.doService(certificate, new RejectAbsenceService(), rejArg);
		assertTrue(rejResult.getMessage(), rejResult.isOk());

		// Verify Rejection
		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			Resource absence = tx.getResourceBy(TYPE_ABSENCE, absenceId, true);
			assertEquals(STATE_REJECTED, absence.getString(PARAM_STATE));
			assertEquals("Not allowed", absence.getString(PARAM_COMMENT));
		}

		// Reject Absence - Invalid State (already rejected)
		rejResult = serviceHandler.doService(certificate, new RejectAbsenceService(), rejArg);
		assertFalse("Should fail if not in SUBMITTED state", rejResult.isOk());
	}

	@Test
	public void shouldCancelOwnAbsence() {
		String employeeId = "emp6";
		String userId = "user6";
		Certificate userCert;

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, false)) {
			Resource employee = createEmployee(tx, employeeId, "Jill Doe");
			employee.setString(PARAM_USER_ID, userId);
			employee.setString(PARAM_USERNAME, "jill");
			tx.update(employee);

			Resource schedule = tx.getResourceByRelation(employee, PARAM_CURRENT_SCHEDULE, true);
			schedule.setInteger(PARAM_DAILY_TARGET_MINUTES + "Wednesday", 480);
			tx.update(schedule);

			// Add initial vacation entitlement
			Resource entry = tx.getResourceTemplate(TYPE_VACATION_ACCOUNT_ENTRY, true);
			entry.setName("Entitlement");
			entry.setRelation(PARAM_EMPLOYEE, employee);
			entry.setDate(PARAM_DATE, ZonedDateTime.parse("2026-01-01T00:00:00Z"));
			entry.setString(PARAM_VACATION_TYPE, VACATION_ENTITLEMENT);
			entry.setInteger(PARAM_VALUE, 20 * 480); // 20 days
			tx.add(entry);

			tx.commitOnClose();

			UserRep userRep = new UserRep(null, userId, "Jill", "Doe", UserState.ENABLED, emptySet(),
					singleton("Employee"), Locale.of("de", "CH"), emptyMap(), null);
			runtimeMock.getPrivilegeHandler().getPrivilegeHandler().addUser(certificate, userRep, userId.toCharArray());
			userCert = runtimeMock.login(userId, userId);
		}

		ServiceHandler serviceHandler = runtimeMock.getServiceHandler();

		// 1. Request Absence (Submitted)
		RequestAbsenceService.RequestAbsenceArgument reqArg = new RequestAbsenceService.RequestAbsenceArgument();
		reqArg.employeeId = employeeId;
		reqArg.absenceTypeCode = "VACATION";
		reqArg.start = ZonedDateTime.parse("2026-04-01T00:00:00+02:00[Europe/Zurich]"); // Wednesday
		reqArg.end = ZonedDateTime.parse("2026-04-01T23:59:59+02:00[Europe/Zurich]");
		reqArg.durationType = DURATION_FULL_DAY;
		ServiceResult reqResult = serviceHandler.doService(userCert, new RequestAbsenceService(), reqArg);
		assertTrue(reqResult.getMessage(), reqResult.isOk());

		String absenceId;
		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			Resource absence = tx
					.streamResources(TYPE_ABSENCE)
					.filter(a -> a.getRelationId(PARAM_EMPLOYEE).equals(employeeId))
					.findFirst()
					.orElseThrow();
			absenceId = absence.getId();
		}

		// 2. Cancel Absence (Self-service)
		ServiceResult cancelResult = serviceHandler.doService(userCert, new CancelAbsenceService(),
				new StringArgument(absenceId));
		assertTrue(cancelResult.getMessage(), cancelResult.isOk());

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			Resource absence = tx.getResourceBy(TYPE_ABSENCE, absenceId, true);
			assertEquals(STATE_CANCELLED, absence.getString(PARAM_STATE));
		}

		// 3. Request another one and Approve it
		reqArg.start = ZonedDateTime.parse("2026-04-08T00:00:00+02:00[Europe/Zurich]");
		reqArg.end = ZonedDateTime.parse("2026-04-08T23:59:59+02:00[Europe/Zurich]");
		reqResult = serviceHandler.doService(userCert, new RequestAbsenceService(), reqArg);
		assertTrue(reqResult.getMessage(), reqResult.isOk());

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			absenceId = tx
					.streamResources(TYPE_ABSENCE)
					.filter(a -> a.getRelationId(PARAM_EMPLOYEE).equals(employeeId))
					.filter(a -> a.getString(PARAM_STATE).equals(STATE_SUBMITTED))
					.findFirst()
					.orElseThrow()
					.getId();
		}

		serviceHandler.doService(certificate, new ApproveAbsenceService(), new StringArgument(absenceId));

		// Check balance before cancellation
		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			int balance = VacationHelper.getVacationBalance(tx, employeeId, ZonedDateTime.now());
			assertEquals("Balance should be reduced by 1 day", (20 - 1) * 480, balance);
		}

		// 4. Cancel Approved Absence
		cancelResult = serviceHandler.doService(userCert, new CancelAbsenceService(), new StringArgument(absenceId));
		assertTrue(cancelResult.getMessage(), cancelResult.isOk());

		// Check balance after cancellation
		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			int balance = VacationHelper.getVacationBalance(tx, employeeId, ZonedDateTime.now());
			assertEquals("Balance should be restored", 20 * 480, balance);

			Resource absence = tx.getResourceBy(TYPE_ABSENCE, absenceId, true);
			assertEquals(STATE_CANCELLED, absence.getString(PARAM_STATE));
		}
	}
}
