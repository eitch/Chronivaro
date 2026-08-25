package ch.eitchnet.chronivaro.core;

import ch.eitchnet.chronivaro.core.model.VacationHelper;
import ch.eitchnet.chronivaro.core.service.ApproveAbsenceService;
import ch.eitchnet.chronivaro.core.service.CancelAbsenceService;
import ch.eitchnet.chronivaro.core.service.RejectAbsenceService;
import ch.eitchnet.chronivaro.core.service.RequestAbsenceService;
import ch.eitchnet.chronivaro.core.service.SubmitAbsenceService;
import ch.eitchnet.chronivaro.core.service.UpdateAbsenceService;
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
import java.util.Set;

import static ch.eitchnet.chronivaro.core.ChronivaroTestHelper.createEmployee;
import static ch.eitchnet.chronivaro.core.model.ChronivaroConstants.*;
import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;
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
			Resource absence = tx
					.streamResources(TYPE_ABSENCE)
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
					Set.of(ROLE_EMPLOYEE, ROLE_MODEL_ACCESSOR), Locale.of("de", "CH"), emptyMap(), null);
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

	@Test
	public void shouldEnforceCommentRequired() {
		String employeeId = "emp-comment-req";
		String absenceTypeCode = "TRAINING";

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, false)) {
			createEmployee(tx, employeeId, "Comment Emp");
			tx.commitOnClose();
		}

		ServiceHandler serviceHandler = runtimeMock.getServiceHandler();

		// 1. Request without comment -> should fail
		RequestAbsenceService.RequestAbsenceArgument reqArg = new RequestAbsenceService.RequestAbsenceArgument();
		reqArg.employeeId = employeeId;
		reqArg.absenceTypeCode = absenceTypeCode;
		reqArg.start = ZonedDateTime.parse("2026-05-04T00:00:00+02:00[Europe/Zurich]");
		reqArg.end = ZonedDateTime.parse("2026-05-04T23:59:59+02:00[Europe/Zurich]");
		reqArg.durationType = DURATION_FULL_DAY;
		ServiceResult failResult1 = serviceHandler.doService(certificate, new RequestAbsenceService(), reqArg);
		assertTrue("Should fail when comment is null", failResult1.isNok());

		// 2. Request with blank comment -> should fail
		reqArg.comment = "   ";
		ServiceResult failResult2 = serviceHandler.doService(certificate, new RequestAbsenceService(), reqArg);
		assertTrue("Should fail when comment is blank", failResult2.isNok());

		// 3. Request with valid comment -> should succeed
		reqArg.comment = "Attending Java Conference";
		ServiceResult okResult = serviceHandler.doService(certificate, new RequestAbsenceService(), reqArg);
		assertTrue(okResult.getMessage(), okResult.isOk());
	}

	@Test
	public void shouldEnforceAllowedDurationTypes() {
		String employeeId = "emp-dur-types";
		String absenceTypeCode = "DOCTOR";

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, false)) {
			createEmployee(tx, employeeId, "Duration Emp");

			Resource doctor = tx.getResourceTemplate(TYPE_ABSENCE_TYPE, true);
			doctor.setId(absenceTypeCode);
			doctor.setName("Doctor Appointment");
			doctor.setString(PARAM_CODE, absenceTypeCode);
			doctor.setStringList(PARAM_DURATION_TYPES, java.util.List.of(DURATION_HOURS, DURATION_HALF_DAY));
			tx.add(doctor);

			tx.commitOnClose();
		}

		ServiceHandler serviceHandler = runtimeMock.getServiceHandler();

		// 1. Request with disallowed duration FULL_DAY -> should fail
		RequestAbsenceService.RequestAbsenceArgument reqArg = new RequestAbsenceService.RequestAbsenceArgument();
		reqArg.employeeId = employeeId;
		reqArg.absenceTypeCode = absenceTypeCode;
		reqArg.start = ZonedDateTime.parse("2026-05-05T00:00:00+02:00[Europe/Zurich]");
		reqArg.end = ZonedDateTime.parse("2026-05-05T23:59:59+02:00[Europe/Zurich]");
		reqArg.durationType = DURATION_FULL_DAY;
		ServiceResult failResult = serviceHandler.doService(certificate, new RequestAbsenceService(), reqArg);
		assertTrue("FULL_DAY should be rejected", failResult.isNok());

		// 2. Request with allowed duration HOURS -> should succeed
		reqArg.durationType = DURATION_HOURS;
		reqArg.minutes = 120;
		ServiceResult okResult = serviceHandler.doService(certificate, new RequestAbsenceService(), reqArg);
		assertTrue(okResult.getMessage(), okResult.isOk());
	}

	@Test
	public void shouldHandleDraftAbsenceLifecycle() {
		String employeeId = "emp-draft-test";
		String absenceTypeCode = "SPECIAL_LEAVE";

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, false)) {
			createEmployee(tx, employeeId, "Draft Emp");

			Resource special = tx.getResourceTemplate(TYPE_ABSENCE_TYPE, true);
			special.setId(absenceTypeCode);
			special.setName("Special Leave");
			special.setString(PARAM_CODE, absenceTypeCode);
			special.setBoolean(PARAM_COMMENT_REQUIRED, true);
			tx.add(special);

			tx.commitOnClose();
		}

		ServiceHandler serviceHandler = runtimeMock.getServiceHandler();

		// 1. Create draft without comment -> should succeed
		RequestAbsenceService.RequestAbsenceArgument reqArg = new RequestAbsenceService.RequestAbsenceArgument();
		reqArg.employeeId = employeeId;
		reqArg.absenceTypeCode = absenceTypeCode;
		reqArg.start = ZonedDateTime.parse("2026-05-06T00:00:00+02:00[Europe/Zurich]");
		reqArg.end = ZonedDateTime.parse("2026-05-06T23:59:59+02:00[Europe/Zurich]");
		reqArg.durationType = DURATION_FULL_DAY;
		reqArg.asDraft = true;
		li.strolch.service.StringResult createDraftResult = serviceHandler.doService(certificate,
				new RequestAbsenceService(), reqArg);
		assertTrue(createDraftResult.getMessage(), createDraftResult.isOk());
		String absenceId = createDraftResult.getValue();

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			Resource absence = tx.getResourceBy(TYPE_ABSENCE, absenceId, true);
			assertEquals(STATE_DRAFT, absence.getString(PARAM_STATE));
		}

		// 2. Try to submit draft while comment is missing -> should fail
		ServiceResult submitFail = serviceHandler.doService(certificate, new SubmitAbsenceService(),
				new StringArgument(absenceId));
		assertTrue("Submit should fail when comment is required but missing", submitFail.isNok());

		// 3. Update draft to add comment -> should succeed
		UpdateAbsenceService.UpdateAbsenceArgument updArg = new UpdateAbsenceService.UpdateAbsenceArgument();
		updArg.absenceId = absenceId;
		updArg.comment = "Moving house";
		ServiceResult updResult = serviceHandler.doService(certificate, new UpdateAbsenceService(), updArg);
		assertTrue(updResult.getMessage(), updResult.isOk());

		// 4. Submit draft -> should succeed
		ServiceResult submitOk = serviceHandler.doService(certificate, new SubmitAbsenceService(),
				new StringArgument(absenceId));
		assertTrue(submitOk.getMessage(), submitOk.isOk());

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			Resource absence = tx.getResourceBy(TYPE_ABSENCE, absenceId, true);
			assertEquals(STATE_SUBMITTED, absence.getString(PARAM_STATE));
			assertEquals("Moving house", absence.getString(PARAM_COMMENT));
		}

		// 5. Try to submit already submitted absence -> should fail
		ServiceResult submitAgain = serviceHandler.doService(certificate, new SubmitAbsenceService(),
				new StringArgument(absenceId));
		assertTrue("Cannot submit already submitted absence", submitAgain.isNok());
	}

	@Test
	public void shouldCancelDraftAbsenceWithoutVacationRefund() {
		String employeeId = "emp-draft-cancel-test";
		String absenceTypeCode = "VACATION";

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, false)) {
			createEmployee(tx, employeeId, "Draft Cancel Emp");
			tx.commitOnClose();
		}

		ServiceHandler serviceHandler = runtimeMock.getServiceHandler();

		// 1. Create draft vacation absence
		RequestAbsenceService.RequestAbsenceArgument reqArg = new RequestAbsenceService.RequestAbsenceArgument();
		reqArg.employeeId = employeeId;
		reqArg.absenceTypeCode = absenceTypeCode;
		reqArg.start = ZonedDateTime.parse("2026-06-01T00:00:00+02:00[Europe/Zurich]");
		reqArg.end = ZonedDateTime.parse("2026-06-02T23:59:59+02:00[Europe/Zurich]");
		reqArg.durationType = DURATION_FULL_DAY;
		reqArg.asDraft = true;
		li.strolch.service.StringResult createDraftResult = serviceHandler.doService(certificate,
				new RequestAbsenceService(), reqArg);
		assertTrue(createDraftResult.getMessage(), createDraftResult.isOk());
		String absenceId = createDraftResult.getValue();

		// 2. Cancel draft absence
		ServiceResult cancelResult = serviceHandler.doService(certificate, new CancelAbsenceService(),
				new StringArgument(absenceId));
		assertTrue(cancelResult.getMessage(), cancelResult.isOk());

		// 3. Verify state is CANCELLED and no vacation journal entries exist for this absence
		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			Resource absence = tx.getResourceBy(TYPE_ABSENCE, absenceId, true);
			assertEquals(STATE_CANCELLED, absence.getString(PARAM_STATE));

			boolean hasVacationRefund = tx.streamResources(TYPE_VACATION_ACCOUNT_ENTRY)
					.anyMatch(entry -> entry.hasRelation(PARAM_ABSENCE) &&
							absenceId.equals(entry.getRelationId(PARAM_ABSENCE)));
			assertFalse("Cancelling draft must not create vacation refund journal entry", hasVacationRefund);
		}
	}

	@Test
	public void shouldPreventOverlappingAbsences() {
		String employeeId = "emp-overlap-test";

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, false)) {
			createEmployee(tx, employeeId, "Overlap Emp");
			tx.commitOnClose();
		}

		ServiceHandler serviceHandler = runtimeMock.getServiceHandler();

		// 1. First absence: May 11 to May 13
		RequestAbsenceService.RequestAbsenceArgument req1 = new RequestAbsenceService.RequestAbsenceArgument();
		req1.employeeId = employeeId;
		req1.absenceTypeCode = "VACATION";
		req1.start = ZonedDateTime.parse("2026-05-11T00:00:00+02:00[Europe/Zurich]");
		req1.end = ZonedDateTime.parse("2026-05-13T23:59:59+02:00[Europe/Zurich]");
		req1.durationType = DURATION_FULL_DAY;
		assertTrue(serviceHandler.doService(certificate, new RequestAbsenceService(), req1).isOk());

		// 2. Overlapping absence: May 12 to May 15 -> should fail
		RequestAbsenceService.RequestAbsenceArgument req2 = new RequestAbsenceService.RequestAbsenceArgument();
		req2.employeeId = employeeId;
		req2.absenceTypeCode = "VACATION";
		req2.start = ZonedDateTime.parse("2026-05-12T00:00:00+02:00[Europe/Zurich]");
		req2.end = ZonedDateTime.parse("2026-05-15T23:59:59+02:00[Europe/Zurich]");
		req2.durationType = DURATION_FULL_DAY;
		assertTrue("Overlapping absence must fail",
				serviceHandler.doService(certificate, new RequestAbsenceService(), req2).isNok());
	}
}
