package ch.eitchnet.chronivaro.core;

import ch.eitchnet.chronivaro.core.model.WorkingLocation;
import ch.eitchnet.chronivaro.core.service.AddWorkEntryService;
import li.strolch.model.ParameterBag;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.privilege.model.Certificate;
import li.strolch.service.api.ServiceHandler;
import li.strolch.service.api.ServiceResult;
import li.strolch.testbase.runtime.RuntimeMock;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.time.ZonedDateTime;
import java.util.List;

import static ch.eitchnet.chronivaro.core.ChronivaroTestHelper.createEmployee;
import static ch.eitchnet.chronivaro.core.model.ChronivaroConstants.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class AddWorkEntryServiceTest {

	private static RuntimeMock runtimeMock;
	private static Certificate certificate;

	@BeforeClass
	public static void beforeClass() {
		runtimeMock = new RuntimeMock().mockRuntime("target/" + AddWorkEntryServiceTest.class.getSimpleName(),
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
	public void shouldAddWorkEntry() {
		String employeeId = "emp-add-work";

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, false)) {
			createEmployee(tx, employeeId, "Add Work Emp");
			tx.commitOnClose();
		}

		ServiceHandler serviceHandler = runtimeMock.getServiceHandler();

		AddWorkEntryService.AddWorkEntryArgument arg = new AddWorkEntryService.AddWorkEntryArgument();
		arg.employeeId = employeeId;
		arg.start = ZonedDateTime.parse("2026-05-04T08:00:00+02:00[Europe/Zurich]");
		arg.end = ZonedDateTime.parse("2026-05-04T12:00:00+02:00[Europe/Zurich]");
		arg.comment = "Test work entry";
		arg.workingLocation = WorkingLocation.HOME_OFFICE;

		ServiceResult result = serviceHandler.doService(certificate, new AddWorkEntryService(), arg);
		assertTrue(result.getMessage(), result.isOk());

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			List<Resource> entries = tx
					.streamResources(TYPE_WORK_ENTRY)
					.filter(e -> e.getRelationId(PARAM_EMPLOYEE).equals(employeeId))
					.toList();
			assertEquals(1, entries.size());
			Resource entry = entries.getFirst();
			assertEquals(arg.start, entry.getDate(PARAM_START));
			assertEquals(arg.end, entry.getDate(PARAM_END));
			assertEquals(arg.comment, entry.getString(PARAM_COMMENT));
			assertEquals(SOURCE_MANUAL, entry.getString(PARAM_SOURCE));
			assertEquals(WorkingLocation.HOME_OFFICE.name(), entry.getString(PARAM_WORKING_LOCATION));
		}
	}

	@Test
	public void shouldLinkScheduleVersionByEntryDate() {
		String employeeId = "emp-sched-res";

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, false)) {
			Resource employee = createEmployee(tx, employeeId, "Schedule Resolution Emp", false);
			employee.setDate(PARAM_JOIN_DATE, ZonedDateTime.parse("2025-01-01T00:00:00Z"));

			Resource v1 = tx.getResourceTemplate(TYPE_EMPLOYMENT_SCHEDULE, true);
			v1.setId("sched-v1-" + employeeId);
			v1.setName("100% Schedule");
			v1.setRelation(PARAM_EMPLOYEE, employee);
			v1.setDate(PARAM_VALID_FROM, ZonedDateTime.parse("2025-01-01T00:00:00Z"));
			v1.setDate(PARAM_VALID_TO, ZonedDateTime.parse("2025-06-30T23:59:59Z"));
			v1.setInteger(PARAM_DAILY_TARGET_MINUTES, 480);
			tx.add(v1);

			Resource v2 = tx.getResourceTemplate(TYPE_EMPLOYMENT_SCHEDULE, true);
			v2.setId("sched-v2-" + employeeId);
			v2.setName("80% Schedule");
			v2.setRelation(PARAM_EMPLOYEE, employee);
			v2.setDate(PARAM_VALID_FROM, ZonedDateTime.parse("2025-07-01T00:00:00Z"));
			v2.setInteger(PARAM_DAILY_TARGET_MINUTES, 384);
			tx.add(v2);

			employee.setRelation(PARAM_CURRENT_SCHEDULE, v2);
			tx.update(employee);

			tx.commitOnClose();
		}

		ServiceHandler serviceHandler = runtimeMock.getServiceHandler();

		// Entry 1 in March 2025 (should link to v1)
		AddWorkEntryService.AddWorkEntryArgument arg1 = new AddWorkEntryService.AddWorkEntryArgument();
		arg1.employeeId = employeeId;
		arg1.start = ZonedDateTime.parse("2025-03-10T08:00:00+01:00[Europe/Zurich]");
		arg1.end = ZonedDateTime.parse("2025-03-10T12:00:00+01:00[Europe/Zurich]");
		arg1.comment = "Entry in Q1";
		ServiceResult result1 = serviceHandler.doService(certificate, new AddWorkEntryService(), arg1);
		assertTrue(result1.getMessage(), result1.isOk());

		// Entry 2 in August 2025 (should link to v2)
		AddWorkEntryService.AddWorkEntryArgument arg2 = new AddWorkEntryService.AddWorkEntryArgument();
		arg2.employeeId = employeeId;
		arg2.start = ZonedDateTime.parse("2025-08-15T08:00:00+02:00[Europe/Zurich]");
		arg2.end = ZonedDateTime.parse("2025-08-15T12:00:00+02:00[Europe/Zurich]");
		arg2.comment = "Entry in Q3";
		ServiceResult result2 = serviceHandler.doService(certificate, new AddWorkEntryService(), arg2);
		assertTrue(result2.getMessage(), result2.isOk());

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			List<Resource> entries = tx.streamResources(TYPE_WORK_ENTRY)
					.filter(e -> employeeId.equals(e.getRelationId(PARAM_EMPLOYEE)))
					.toList();
			assertEquals(2, entries.size());

			Resource entry1 = entries.stream()
					.filter(e -> e.getDate(PARAM_START).getMonthValue() == 3)
					.findFirst()
					.orElseThrow();
			assertEquals("sched-v1-" + employeeId, entry1.getRelationId(PARAM_SCHEDULE));

			Resource entry2 = entries.stream()
					.filter(e -> e.getDate(PARAM_START).getMonthValue() == 8)
					.findFirst()
					.orElseThrow();
			assertEquals("sched-v2-" + employeeId, entry2.getRelationId(PARAM_SCHEDULE));
		}
	}

	@Test
	public void shouldRejectZeroOrNegativeDuration() {
		String employeeId = "emp-duration-test";

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, false)) {
			createEmployee(tx, employeeId, "Duration Test Emp");
			tx.commitOnClose();
		}

		ServiceHandler serviceHandler = runtimeMock.getServiceHandler();

		// Zero duration: start == end
		AddWorkEntryService.AddWorkEntryArgument zeroArg = new AddWorkEntryService.AddWorkEntryArgument();
		zeroArg.employeeId = employeeId;
		zeroArg.start = ZonedDateTime.parse("2026-06-01T08:00:00+02:00[Europe/Zurich]");
		zeroArg.end = ZonedDateTime.parse("2026-06-01T08:00:00+02:00[Europe/Zurich]");
		zeroArg.comment = "Zero duration";
		ServiceResult zeroResult = serviceHandler.doService(certificate, new AddWorkEntryService(), zeroArg);
		assertTrue("Zero duration entry should be rejected", zeroResult.isNok());

		// Inverted duration: end < start
		AddWorkEntryService.AddWorkEntryArgument invertedArg = new AddWorkEntryService.AddWorkEntryArgument();
		invertedArg.employeeId = employeeId;
		invertedArg.start = ZonedDateTime.parse("2026-06-01T12:00:00+02:00[Europe/Zurich]");
		invertedArg.end = ZonedDateTime.parse("2026-06-01T08:00:00+02:00[Europe/Zurich]");
		invertedArg.comment = "Inverted duration";
		ServiceResult invertedResult = serviceHandler.doService(certificate, new AddWorkEntryService(), invertedArg);
		assertTrue("Inverted duration entry should be rejected", invertedResult.isNok());
	}

	@Test
	public void shouldNotMutateCurrentWorkDayForPastDate() {
		String employeeId = "emp-past-workday";

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, false)) {
			createEmployee(tx, employeeId, "Past WorkDay Emp");
			tx.commitOnClose();
		}

		ServiceHandler serviceHandler = runtimeMock.getServiceHandler();

		// Add entry for past date 2026-01-15
		AddWorkEntryService.AddWorkEntryArgument arg = new AddWorkEntryService.AddWorkEntryArgument();
		arg.employeeId = employeeId;
		arg.start = ZonedDateTime.parse("2026-01-15T08:00:00+01:00[Europe/Zurich]");
		arg.end = ZonedDateTime.parse("2026-01-15T12:00:00+01:00[Europe/Zurich]");
		arg.comment = "Past date entry";
		ServiceResult result = serviceHandler.doService(certificate, new AddWorkEntryService(), arg);
		assertTrue(result.getMessage(), result.isOk());

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			Resource employee = tx.getResourceBy(TYPE_EMPLOYEE, employeeId, true);
			String currentWorkDayId = employee.getRelationId(PARAM_CURRENT_WORK_DAY);
			// currentWorkDayId should NOT point to 2026-01-15
			assertTrue(currentWorkDayId == null || !currentWorkDayId.contains("2026-01-15"));

			Resource pastWorkDay = tx.getResourceBy(TYPE_WORK_DAY, employeeId + "-2026-01-15", false);
			assertTrue(pastWorkDay != null);
		}
	}

	@Test
	public void shouldEnforceWorkingLocationConstraints() {
		String employeeId = "emp-loc-constraints";

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, false)) {
			createEmployee(tx, employeeId, "Location Constraint Emp");
			tx.commitOnClose();
		}

		ServiceHandler serviceHandler = runtimeMock.getServiceHandler();

		// 1. Morning entry with HOME_OFFICE (08:00 - 10:00)
		AddWorkEntryService.AddWorkEntryArgument m1 = new AddWorkEntryService.AddWorkEntryArgument();
		m1.employeeId = employeeId;
		m1.start = ZonedDateTime.parse("2026-05-10T08:00:00+02:00[Europe/Zurich]");
		m1.end = ZonedDateTime.parse("2026-05-10T10:00:00+02:00[Europe/Zurich]");
		m1.workingLocation = WorkingLocation.HOME_OFFICE;
		assertTrue(serviceHandler.doService(certificate, new AddWorkEntryService(), m1).isOk());

		// 2. Another morning entry with SAME location HOME_OFFICE (10:30 - 12:00) -> Allowed
		AddWorkEntryService.AddWorkEntryArgument m2 = new AddWorkEntryService.AddWorkEntryArgument();
		m2.employeeId = employeeId;
		m2.start = ZonedDateTime.parse("2026-05-10T10:30:00+02:00[Europe/Zurich]");
		m2.end = ZonedDateTime.parse("2026-05-10T12:00:00+02:00[Europe/Zurich]");
		m2.workingLocation = WorkingLocation.HOME_OFFICE;
		assertTrue(serviceHandler.doService(certificate, new AddWorkEntryService(), m2).isOk());

		// 3. Morning entry with CONFLICTING location OFFICE (12:00 - 12:30) -> Rejected
		AddWorkEntryService.AddWorkEntryArgument mConflict = new AddWorkEntryService.AddWorkEntryArgument();
		mConflict.employeeId = employeeId;
		mConflict.start = ZonedDateTime.parse("2026-05-10T12:00:00+02:00[Europe/Zurich]");
		mConflict.end = ZonedDateTime.parse("2026-05-10T12:30:00+02:00[Europe/Zurich]");
		mConflict.workingLocation = WorkingLocation.OFFICE;
		assertTrue("Conflicting morning location should be rejected",
				serviceHandler.doService(certificate, new AddWorkEntryService(), mConflict).isNok());

		// 4. Afternoon entry with CUSTOMER (13:00 - 15:00) -> Allowed
		AddWorkEntryService.AddWorkEntryArgument a1 = new AddWorkEntryService.AddWorkEntryArgument();
		a1.employeeId = employeeId;
		a1.start = ZonedDateTime.parse("2026-05-10T13:00:00+02:00[Europe/Zurich]");
		a1.end = ZonedDateTime.parse("2026-05-10T15:00:00+02:00[Europe/Zurich]");
		a1.workingLocation = WorkingLocation.CUSTOMER;
		assertTrue(serviceHandler.doService(certificate, new AddWorkEntryService(), a1).isOk());

		// 5. Afternoon entry with CONFLICTING location OFFICE (15:30 - 17:00) -> Rejected
		AddWorkEntryService.AddWorkEntryArgument aConflict = new AddWorkEntryService.AddWorkEntryArgument();
		aConflict.employeeId = employeeId;
		aConflict.start = ZonedDateTime.parse("2026-05-10T15:30:00+02:00[Europe/Zurich]");
		aConflict.end = ZonedDateTime.parse("2026-05-10T17:00:00+02:00[Europe/Zurich]");
		aConflict.workingLocation = WorkingLocation.OFFICE;
		assertTrue("Conflicting afternoon location should be rejected",
				serviceHandler.doService(certificate, new AddWorkEntryService(), aConflict).isNok());
	}
}
