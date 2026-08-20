package ch.atexxi.chronivaro.core;

import ch.atexxi.chronivaro.core.service.RequestAbsenceService;
import ch.atexxi.chronivaro.core.service.UpdateAbsenceService;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.privilege.model.Certificate;
import li.strolch.privilege.model.UserRep;
import li.strolch.privilege.model.UserState;
import li.strolch.service.api.ServiceHandler;
import li.strolch.service.api.ServiceResult;
import li.strolch.testbase.runtime.RuntimeMock;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.time.ZonedDateTime;
import java.util.Locale;
import java.util.Set;

import static ch.atexxi.chronivaro.core.ChronivaroTestHelper.createEmployee;
import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;
import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class UpdateAbsenceServiceTest {

	private static RuntimeMock runtimeMock;
	private static Certificate certificate;

	@BeforeClass
	public static void beforeClass() {
		runtimeMock = new RuntimeMock().mockRuntime("target/" + UpdateAbsenceServiceTest.class.getSimpleName(),
				"src/test/resources");
		runtimeMock.startContainer();
		certificate = runtimeMock.login("admin", "admin");
	}

	@AfterClass
	public static void afterClass() {
		if (runtimeMock != null)
			runtimeMock.destroyRuntime();
	}

	@Test
	public void shouldUpdateAbsence() {
		String employeeId = "upd-abs-test";
		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, false)) {
			createEmployee(tx, employeeId, "Update Absence Test");
			ChronivaroTestHelper.createAbsenceType(tx, "VACATION", "Vacation");
			tx.commitOnClose();
		}

		ServiceHandler serviceHandler = runtimeMock.getServiceHandler();

		// 1. Request Absence
		RequestAbsenceService.RequestAbsenceArgument reqArg = new RequestAbsenceService.RequestAbsenceArgument();
		reqArg.employeeId = employeeId;
		reqArg.absenceTypeCode = "VACATION";
		reqArg.start = ZonedDateTime.parse("2026-09-01T08:00:00Z");
		reqArg.end = ZonedDateTime.parse("2026-09-01T17:00:00Z");
		reqArg.durationType = DURATION_FULL_DAY;
		reqArg.comment = "Initial comment";

		ServiceResult reqResult = serviceHandler.doService(certificate, new RequestAbsenceService(), reqArg);
		assertTrue(reqResult.isOk());

		String absenceId;
		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			Resource absence = tx
					.streamResources(TYPE_ABSENCE)
					.filter(a -> a.getRelationId(PARAM_EMPLOYEE).equals(employeeId))
					.findFirst()
					.orElseThrow();
			absenceId = absence.getId();
			assertEquals("Initial comment", absence.getString(PARAM_COMMENT));
		}

		// 2. Update Absence
		UpdateAbsenceService.UpdateAbsenceArgument updArg = new UpdateAbsenceService.UpdateAbsenceArgument();
		updArg.absenceId = absenceId;
		updArg.comment = "Updated comment";
		updArg.minutes = 120; // Changed just to see if it updates

		ServiceResult updResult = serviceHandler.doService(certificate, new UpdateAbsenceService(), updArg);
		assertTrue(updResult.getMessage(), updResult.isOk());

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			Resource absence = tx.getResourceBy(TYPE_ABSENCE, absenceId, true);
			assertEquals("Updated comment", absence.getString(PARAM_COMMENT));
			assertEquals(120, (int) absence.getInteger(PARAM_MINUTES));
		}
	}

	@Test
	public void shouldUpdateOwnAbsence() {
		String employeeId = "upd-abs-self";
		String userId = "user-upd-self";
		Certificate userCert;

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, false)) {
			Resource employee = createEmployee(tx, employeeId, "Jill Doe");
			employee.setString(PARAM_USER_ID, userId);
			employee.setString(PARAM_USERNAME, "jill-upd");
			tx.update(employee);

			ChronivaroTestHelper.createAbsenceType(tx, "VACATION2", "Vacation 2");
			tx.commitOnClose();

			UserRep userRep = new UserRep(null, userId, "Jill", "Doe", UserState.ENABLED, emptySet(),
					Set.of(ROLE_EMPLOYEE, ROLE_MODEL_ACCESSOR), Locale.of("de", "CH"), emptyMap(), null);
			runtimeMock.getPrivilegeHandler().getPrivilegeHandler().addUser(certificate, userRep, userId.toCharArray());
			userCert = runtimeMock.login(userId, userId);
		}

		ServiceHandler serviceHandler = runtimeMock.getServiceHandler();

		// 1. Request Absence
		RequestAbsenceService.RequestAbsenceArgument reqArg = new RequestAbsenceService.RequestAbsenceArgument();
		reqArg.employeeId = employeeId;
		reqArg.absenceTypeCode = "VACATION2";
		reqArg.start = ZonedDateTime.parse("2026-10-01T08:00:00Z");
		reqArg.end = ZonedDateTime.parse("2026-10-01T17:00:00Z");
		reqArg.durationType = DURATION_FULL_DAY;
		reqArg.comment = "Initial";

		ServiceResult reqResult = serviceHandler.doService(userCert, new RequestAbsenceService(), reqArg);
		assertTrue(reqResult.isOk());

		String absenceId;
		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			Resource absence = tx
					.streamResources(TYPE_ABSENCE)
					.filter(a -> a.getRelationId(PARAM_EMPLOYEE).equals(employeeId))
					.findFirst()
					.orElseThrow();
			absenceId = absence.getId();
		}

		// 2. Update Absence (Self-service)
		UpdateAbsenceService.UpdateAbsenceArgument updArg = new UpdateAbsenceService.UpdateAbsenceArgument();
		updArg.absenceId = absenceId;
		updArg.comment = "Updated by self";

		ServiceResult updResult = serviceHandler.doService(userCert, new UpdateAbsenceService(), updArg);
		assertTrue(updResult.getMessage(), updResult.isOk());

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			Resource absence = tx.getResourceBy(TYPE_ABSENCE, absenceId, true);
			assertEquals("Updated by self", absence.getString(PARAM_COMMENT));
		}
	}

	@Test
	public void shouldUpdateDraftAbsence() {
		String employeeId = "upd-draft-emp";

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, false)) {
			createEmployee(tx, employeeId, "Update Draft Emp");
			ChronivaroTestHelper.createAbsenceType(tx, "VACATION3", "Vacation 3");
			tx.commitOnClose();
		}

		ServiceHandler serviceHandler = runtimeMock.getServiceHandler();

		// 1. Create Draft
		RequestAbsenceService.RequestAbsenceArgument reqArg = new RequestAbsenceService.RequestAbsenceArgument();
		reqArg.employeeId = employeeId;
		reqArg.absenceTypeCode = "VACATION3";
		reqArg.start = ZonedDateTime.parse("2026-11-01T08:00:00Z");
		reqArg.end = ZonedDateTime.parse("2026-11-01T17:00:00Z");
		reqArg.durationType = DURATION_FULL_DAY;
		reqArg.asDraft = true;

		li.strolch.service.StringResult reqResult = serviceHandler.doService(certificate, new RequestAbsenceService(),
				reqArg);
		assertTrue(reqResult.isOk());
		String absenceId = reqResult.getValue();

		// 2. Update Draft
		UpdateAbsenceService.UpdateAbsenceArgument updArg = new UpdateAbsenceService.UpdateAbsenceArgument();
		updArg.absenceId = absenceId;
		updArg.comment = "Draft updated comment";

		ServiceResult updResult = serviceHandler.doService(certificate, new UpdateAbsenceService(), updArg);
		assertTrue(updResult.getMessage(), updResult.isOk());

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			Resource absence = tx.getResourceBy(TYPE_ABSENCE, absenceId, true);
			assertEquals(STATE_DRAFT, absence.getString(PARAM_STATE));
			assertEquals("Draft updated comment", absence.getString(PARAM_COMMENT));
		}
	}
}
