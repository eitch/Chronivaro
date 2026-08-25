package ch.eitchnet.chronivaro.core;

import ch.eitchnet.chronivaro.core.model.ScheduleHelper;
import ch.eitchnet.chronivaro.core.service.CreateScheduleService;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.privilege.model.Certificate;
import li.strolch.service.api.ServiceHandler;
import li.strolch.service.api.ServiceResult;
import li.strolch.testbase.runtime.RuntimeMock;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.time.LocalDate;
import java.time.ZonedDateTime;

import static ch.eitchnet.chronivaro.core.model.ChronivaroConstants.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class HistoricalScheduleHelperTest {

	private static RuntimeMock runtimeMock;
	private static Certificate certificate;

	@BeforeClass
	public static void beforeClass() {
		runtimeMock = new RuntimeMock().mockRuntime("target/" + HistoricalScheduleHelperTest.class.getSimpleName(),
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
	public void shouldFindHistoricalScheduleVersion() {
		String employeeId = "emp_hist";
		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, false)) {
			// Create Employee
			Resource employee = tx.getResourceTemplate(TYPE_EMPLOYEE, true);
			employee.setId(employeeId);
			employee.setName("Historical Joe");
			employee.setBoolean(PARAM_ACTIVE, true);
			employee.setDate(PARAM_JOIN_DATE, ZonedDateTime.parse("2025-01-01T00:00:00Z"));
			tx.add(employee);

			// Version 1: 100% (480 min/day) from 2025-01-01 to 2025-06-30
			Resource v1 = tx.getResourceTemplate(TYPE_EMPLOYMENT_SCHEDULE, true);
			v1.setId("v1");
			v1.setName("100% Schedule");
			v1.setRelation(PARAM_EMPLOYEE, employee);
			v1.setDate(PARAM_VALID_FROM, ZonedDateTime.parse("2025-01-01T00:00:00Z"));
			v1.setDate(PARAM_VALID_TO, ZonedDateTime.parse("2025-06-30T00:00:00Z"));
			v1.setInteger(PARAM_DAILY_TARGET_MINUTES, 480);
			tx.add(v1);

			// Version 2: 80% (384 min/day) from 2025-07-01
			Resource v2 = tx.getResourceTemplate(TYPE_EMPLOYMENT_SCHEDULE, true);
			v2.setId("v2");
			v2.setName("80% Schedule");
			v2.setRelation(PARAM_EMPLOYEE, employee);
			v2.setDate(PARAM_VALID_FROM, ZonedDateTime.parse("2025-07-01T00:00:00Z"));
			v2.setInteger(PARAM_DAILY_TARGET_MINUTES, 384);
			tx.add(v2);

			employee.setRelation(PARAM_CURRENT_SCHEDULE, v2);
			tx.update(employee);

			tx.commitOnClose();
		}

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			// Test historical date (should be v1)
			LocalDate pastDate = LocalDate.of(2025, 3, 1);
			// Currently findScheduleVersion only takes employeeId, so it returns current (v2)
			// This test should fail once we update the signature or if we expect it to be historical
			// But for now, let's see what it does.
			assertEquals(480, ScheduleHelper.getTargetMinutes(tx, employeeId, pastDate));

			// Test recent date (should be v2)
			LocalDate recentDate = LocalDate.of(2025, 8, 1);
			assertEquals(384, ScheduleHelper.getTargetMinutes(tx, employeeId, recentDate));
		}
	}

	@Test
	public void shouldPreventOverlappingSchedules() {
		String employeeId = "emp_overlap";
		ServiceHandler serviceHandler = runtimeMock.getServiceHandler();

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, false)) {
			Resource employee = tx.getResourceTemplate(TYPE_EMPLOYEE, true);
			employee.setId(employeeId);
			employee.setName("Overlap Joe");
			employee.setBoolean(PARAM_ACTIVE, true);
			tx.add(employee);
			tx.commitOnClose();
		}

		// Initial Schedule: 2026-01-01 to 2026-06-30
		CreateScheduleService.CreateScheduleArgument arg1 = new CreateScheduleService.CreateScheduleArgument();
		arg1.employeeId = employeeId;
		arg1.validFrom = ZonedDateTime.parse("2026-01-01T00:00:00Z");
		arg1.validTo = ZonedDateTime.parse("2026-06-30T00:00:00Z");
		ServiceResult result1 = serviceHandler.doService(certificate, new CreateScheduleService(), arg1);
		assertTrue(result1.getMessage(), result1.isOk());

		// Overlapping Schedule: 2026-06-01 to 2026-12-31 (Overlaps with arg1)
		CreateScheduleService.CreateScheduleArgument arg2 = new CreateScheduleService.CreateScheduleArgument();
		arg2.employeeId = employeeId;
		arg2.validFrom = ZonedDateTime.parse("2026-06-01T00:00:00Z");
		arg2.validTo = ZonedDateTime.parse("2026-12-31T00:00:00Z");
		ServiceResult result2 = serviceHandler.doService(certificate, new CreateScheduleService(), arg2);
		assertTrue("Should fail due to overlap", result2.isNok());

		// Non-overlapping Schedule: 2026-07-01 onwards
		CreateScheduleService.CreateScheduleArgument arg3 = new CreateScheduleService.CreateScheduleArgument();
		arg3.employeeId = employeeId;
		arg3.validFrom = ZonedDateTime.parse("2026-07-01T00:00:00Z");
		ServiceResult result3 = serviceHandler.doService(certificate, new CreateScheduleService(), arg3);
		assertTrue(result3.getMessage(), result3.isOk());
	}
}
