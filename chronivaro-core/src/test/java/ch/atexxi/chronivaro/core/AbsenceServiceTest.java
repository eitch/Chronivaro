package ch.atexxi.chronivaro.core;

import ch.atexxi.chronivaro.core.model.VacationHelper;
import ch.atexxi.chronivaro.core.service.ApproveAbsenceService;
import ch.atexxi.chronivaro.core.service.RequestAbsenceService;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.privilege.model.Certificate;
import li.strolch.service.StringArgument;
import li.strolch.service.api.ServiceHandler;
import li.strolch.service.api.ServiceResult;
import li.strolch.testbase.runtime.RuntimeMock;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.time.ZonedDateTime;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AbsenceServiceTest {

	private static RuntimeMock runtimeMock;
	private static Certificate certificate;

	@BeforeClass
	public static void beforeClass() {
		runtimeMock = new RuntimeMock().mockRuntime("target/" + AbsenceServiceTest.class.getSimpleName(),
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
	public void shouldRequestAndApproveAbsence() {
		String employeeId = "emp4";

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, false)) {
			Resource employee = tx.getResourceTemplate(TYPE_EMPLOYEE, true);
			employee.setId(employeeId);
			employee.setName("Jane Doe");
			employee.setBoolean(PARAM_ACTIVE, true);
			employee.setDate(PARAM_JOIN_DATE, ZonedDateTime.parse("2026-01-01T00:00:00Z"));
			tx.add(employee);

			Resource schedule = tx.getResourceTemplate(TYPE_EMPLOYMENT_SCHEDULE_VERSION, true);
			schedule.setName("Schedule");
			schedule.setRelation(PARAM_EMPLOYEE, employee);
			schedule.setDate(PARAM_VALID_FROM, ZonedDateTime.parse("2026-01-01T00:00:00Z"));
			schedule.setInteger(PARAM_DAILY_TARGET_MINUTES + "Monday", 480);
			tx.add(schedule);

			Resource absenceType = tx.getResourceTemplate(TYPE_ABSENCE_TYPE, true);
			absenceType.setId("vacation");
			absenceType.setName("Vacation");
			absenceType.setString(PARAM_CODE, "VACATION");
			absenceType.setBoolean(PARAM_REDUCE_VACATION_CREDIT, true);
			tx.add(absenceType);

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
			Resource absence = tx.streamResources(TYPE_ABSENCE).findFirst().orElseThrow();
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
}
