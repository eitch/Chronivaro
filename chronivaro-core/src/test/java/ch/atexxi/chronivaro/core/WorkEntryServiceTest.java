package ch.atexxi.chronivaro.core;

import ch.atexxi.chronivaro.core.model.ChronivaroModelHelper;
import ch.atexxi.chronivaro.core.model.WorkEntryHelper;
import ch.atexxi.chronivaro.core.model.WorkingLocation;
import ch.atexxi.chronivaro.core.service.AddWorkEntryService;
import ch.atexxi.chronivaro.core.service.CorrectWorkEntryService;
import ch.atexxi.chronivaro.core.service.RemoveWorkEntryService;
import ch.atexxi.chronivaro.core.service.StartTimerService;
import ch.atexxi.chronivaro.core.service.StopTimerService;
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

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static ch.atexxi.chronivaro.core.ChronivaroTestHelper.createEmployee;
import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;
import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;
import static org.junit.Assert.*;

public class WorkEntryServiceTest {

	private static RuntimeMock runtimeMock;
	private static Certificate certificate;

	@BeforeClass
	public static void beforeClass() {
		runtimeMock = new RuntimeMock().mockRuntime("target/" + WorkEntryServiceTest.class.getSimpleName(),
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
	public void shouldStartAndStopTimer() {
		String employeeId = "emp2";

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, false)) {
			createEmployee(tx, employeeId, "Jane Doe");
			tx.commitOnClose();
		}

		ServiceHandler serviceHandler = runtimeMock.getServiceHandler();

		// Start Timer
		StartTimerService.Argument startArg = new StartTimerService.Argument(employeeId, WorkingLocation.OFFICE);
		ServiceResult startResult = serviceHandler.doService(certificate, new StartTimerService(), startArg);
		assertTrue(startResult.getMessage(), startResult.isOk());

		// Verify Active Entry
		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			assertTrue(WorkEntryHelper.findActiveWorkEntry(tx, employeeId).isPresent());
		}

		// Stop Timer
		StopTimerService.StopTimerArgument stopArg = new StopTimerService.StopTimerArgument(employeeId);
		ServiceResult stopResult = serviceHandler.doService(certificate, new StopTimerService(), stopArg);
		assertTrue(stopResult.getMessage(), stopResult.isOk());

		// Verify No Active Entry and one completed entry
		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			assertTrue(WorkEntryHelper.findActiveWorkEntry(tx, employeeId).isEmpty());
			List<Resource> entries = tx
					.streamResources(TYPE_WORK_ENTRY)
					.filter(e -> e.getRelationId(PARAM_EMPLOYEE).equals(employeeId))
					.toList();
			assertEquals(1, entries.size());
			assertTrue(entries.getFirst().hasParameter(PARAM_END));
		}
	}

	@Test
	public void shouldStopTimerWithComment() {
		String employeeId = "emp-comment-test";

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, false)) {
			createEmployee(tx, employeeId, "Timer Commenter");
			tx.commitOnClose();
		}

		ServiceHandler serviceHandler = runtimeMock.getServiceHandler();

		StartTimerService.Argument startArg = new StartTimerService.Argument(employeeId, WorkingLocation.OFFICE);
		ServiceResult startResult = serviceHandler.doService(certificate, new StartTimerService(), startArg);
		assertTrue(startResult.getMessage(), startResult.isOk());

		StopTimerService.StopTimerArgument stopArg = new StopTimerService.StopTimerArgument(employeeId, "Stopped for team sync");
		ServiceResult stopResult = serviceHandler.doService(certificate, new StopTimerService(), stopArg);
		assertTrue(stopResult.getMessage(), stopResult.isOk());

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			List<Resource> entries = tx
					.streamResources(TYPE_WORK_ENTRY)
					.filter(e -> e.getRelationId(PARAM_EMPLOYEE).equals(employeeId))
					.toList();
			assertEquals(1, entries.size());
			Resource entry = entries.getFirst();
			assertEquals("Stopped for team sync", entry.getString(PARAM_COMMENT));
		}
	}

	@Test
	public void shouldAllowEmployeeToShortenWorkEntryAndEditComment() {
		String employeeId = "emp-shorten-test";
		String username = "emp_shorten_user";

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, false)) {
			Resource employee = createEmployee(tx, employeeId, "Shorten Employee");
			UserRep userRep = new UserRep(null, username, "Shorten", "Employee", UserState.ENABLED, emptySet(),
					Set.of(ROLE_EMPLOYEE, ROLE_MODEL_ACCESSOR), Locale.of("de", "CH"), emptyMap(), null);
			UserRep addedUser = runtimeMock.getPrivilegeHandler().getPrivilegeHandler().addUser(certificate, userRep, username.toCharArray());

			employee.setString(PARAM_USERNAME, addedUser.getUsername());
			employee.setString(PARAM_USER_ID, addedUser.getUserId());
			tx.update(employee);
			tx.commitOnClose();
		}

		Certificate empCert = runtimeMock.login(username, username);

		ServiceHandler serviceHandler = runtimeMock.getServiceHandler();

		ZonedDateTime start = ZonedDateTime.parse("2026-03-10T08:00:00+01:00[Europe/Zurich]");
		ZonedDateTime end = ZonedDateTime.parse("2026-03-10T17:00:00+01:00[Europe/Zurich]");

		AddWorkEntryService.AddWorkEntryArgument addArg = new AddWorkEntryService.AddWorkEntryArgument();
		addArg.employeeId = employeeId;
		addArg.start = start;
		addArg.end = end;
		addArg.workingLocation = WorkingLocation.OFFICE;
		addArg.comment = "Initial work day";
		ServiceResult addRes = serviceHandler.doService(certificate, new AddWorkEntryService(), addArg);
		assertTrue(addRes.getMessage(), addRes.isOk());

		String workEntryId;
		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			Resource we = tx.streamResources(TYPE_WORK_ENTRY)
					.filter(e -> e.getRelationId(PARAM_EMPLOYEE).equals(employeeId))
					.findFirst().orElseThrow();
			workEntryId = we.getId();
		}

		// Employee shortens to 16:30 and changes comment
		ZonedDateTime shortenedEnd = ZonedDateTime.parse("2026-03-10T16:30:00+01:00[Europe/Zurich]");
		CorrectWorkEntryService.CorrectWorkEntryArgument correctArg = new CorrectWorkEntryService.CorrectWorkEntryArgument();
		correctArg.workEntryId = workEntryId;
		correctArg.start = start;
		correctArg.end = shortenedEnd;
		correctArg.comment = "Forgot to clock out, left at 16:30";
		correctArg.workingLocation = WorkingLocation.OFFICE;

		ServiceResult correctRes = serviceHandler.doService(empCert, new CorrectWorkEntryService(), correctArg);
		assertTrue(correctRes.getMessage(), correctRes.isOk());

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			Resource we = tx.getResourceBy(TYPE_WORK_ENTRY, workEntryId, true);
			assertTrue(we.getDate(PARAM_END).isEqual(shortenedEnd));
			assertEquals("Forgot to clock out, left at 16:30", we.getString(PARAM_COMMENT));
		}
	}

	@Test
	public void shouldRejectEmployeeExtendingWorkEntry() {
		String employeeId = "emp-extend-reject";
		String username = "emp_extend_user";

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, false)) {
			Resource employee = createEmployee(tx, employeeId, "Extend Employee");
			UserRep userRep = new UserRep(null, username, "Extend", "Employee", UserState.ENABLED, emptySet(),
					Set.of(ROLE_EMPLOYEE, ROLE_MODEL_ACCESSOR), Locale.of("de", "CH"), emptyMap(), null);
			UserRep addedUser = runtimeMock.getPrivilegeHandler().getPrivilegeHandler().addUser(certificate, userRep, username.toCharArray());

			employee.setString(PARAM_USERNAME, addedUser.getUsername());
			employee.setString(PARAM_USER_ID, addedUser.getUserId());
			tx.update(employee);
			tx.commitOnClose();
		}

		Certificate empCert = runtimeMock.login(username, username);

		ServiceHandler serviceHandler = runtimeMock.getServiceHandler();

		ZonedDateTime start = ZonedDateTime.parse("2026-03-11T08:00:00+01:00[Europe/Zurich]");
		ZonedDateTime end = ZonedDateTime.parse("2026-03-11T16:00:00+01:00[Europe/Zurich]");

		AddWorkEntryService.AddWorkEntryArgument addArg = new AddWorkEntryService.AddWorkEntryArgument();
		addArg.employeeId = employeeId;
		addArg.start = start;
		addArg.end = end;
		addArg.workingLocation = WorkingLocation.OFFICE;
		ServiceResult addRes = serviceHandler.doService(certificate, new AddWorkEntryService(), addArg);
		assertTrue(addRes.getMessage(), addRes.isOk());

		String workEntryId;
		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			Resource we = tx.streamResources(TYPE_WORK_ENTRY)
					.filter(e -> e.getRelationId(PARAM_EMPLOYEE).equals(employeeId))
					.findFirst().orElseThrow();
			workEntryId = we.getId();
		}

		// Employee attempts to extend to 17:00
		ZonedDateTime extendedEnd = ZonedDateTime.parse("2026-03-11T17:00:00+01:00[Europe/Zurich]");
		CorrectWorkEntryService.CorrectWorkEntryArgument correctArg = new CorrectWorkEntryService.CorrectWorkEntryArgument();
		correctArg.workEntryId = workEntryId;
		correctArg.start = start;
		correctArg.end = extendedEnd;
		correctArg.comment = "Attempting to extend";

		ServiceResult correctRes = serviceHandler.doService(empCert, new CorrectWorkEntryService(), correctArg);
		assertFalse(correctRes.isOk());
		assertTrue(correctRes.getMessage().contains("Employees can only shorten work entries"));
	}

	@Test
	public void shouldRejectEmployeeModifyingStartTime() {
		String employeeId = "emp-start-reject";
		String username = "emp_start_user";

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, false)) {
			Resource employee = createEmployee(tx, employeeId, "Start Mod Employee");
			UserRep userRep = new UserRep(null, username, "StartMod", "Employee", UserState.ENABLED, emptySet(),
					Set.of(ROLE_EMPLOYEE, ROLE_MODEL_ACCESSOR), Locale.of("de", "CH"), emptyMap(), null);
			UserRep addedUser = runtimeMock.getPrivilegeHandler().getPrivilegeHandler().addUser(certificate, userRep, username.toCharArray());

			employee.setString(PARAM_USERNAME, addedUser.getUsername());
			employee.setString(PARAM_USER_ID, addedUser.getUserId());
			tx.update(employee);
			tx.commitOnClose();
		}

		Certificate empCert = runtimeMock.login(username, username);

		ServiceHandler serviceHandler = runtimeMock.getServiceHandler();

		ZonedDateTime start = ZonedDateTime.parse("2026-03-12T08:30:00+01:00[Europe/Zurich]");
		ZonedDateTime end = ZonedDateTime.parse("2026-03-12T17:00:00+01:00[Europe/Zurich]");

		AddWorkEntryService.AddWorkEntryArgument addArg = new AddWorkEntryService.AddWorkEntryArgument();
		addArg.employeeId = employeeId;
		addArg.start = start;
		addArg.end = end;
		addArg.workingLocation = WorkingLocation.OFFICE;
		ServiceResult addRes = serviceHandler.doService(certificate, new AddWorkEntryService(), addArg);
		assertTrue(addRes.getMessage(), addRes.isOk());

		String workEntryId;
		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			Resource we = tx.streamResources(TYPE_WORK_ENTRY)
					.filter(e -> e.getRelationId(PARAM_EMPLOYEE).equals(employeeId))
					.findFirst().orElseThrow();
			workEntryId = we.getId();
		}

		// Employee attempts to change start time to 08:00
		ZonedDateTime earlierStart = ZonedDateTime.parse("2026-03-12T08:00:00+01:00[Europe/Zurich]");
		CorrectWorkEntryService.CorrectWorkEntryArgument correctArg = new CorrectWorkEntryService.CorrectWorkEntryArgument();
		correctArg.workEntryId = workEntryId;
		correctArg.start = earlierStart;
		correctArg.end = end;
		correctArg.comment = "Attempting earlier start";

		ServiceResult correctRes = serviceHandler.doService(empCert, new CorrectWorkEntryService(), correctArg);
		assertFalse(correctRes.isOk());
		assertTrue(correctRes.getMessage().contains("Employees are only permitted to shorten work entries and cannot modify the start time"));
	}

	@Test
	public void shouldAllowAdminToPerformFullCorrectionAndDeletion() {
		String employeeId = "emp-admin-corr";

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, false)) {
			createEmployee(tx, employeeId, "Admin Corr Emp");
			tx.commitOnClose();
		}

		ServiceHandler serviceHandler = runtimeMock.getServiceHandler();

		ZonedDateTime start = ZonedDateTime.parse("2026-03-13T09:00:00+01:00[Europe/Zurich]");
		ZonedDateTime end = ZonedDateTime.parse("2026-03-13T17:00:00+01:00[Europe/Zurich]");

		AddWorkEntryService.AddWorkEntryArgument addArg = new AddWorkEntryService.AddWorkEntryArgument();
		addArg.employeeId = employeeId;
		addArg.start = start;
		addArg.end = end;
		addArg.workingLocation = WorkingLocation.OFFICE;
		ServiceResult addRes = serviceHandler.doService(certificate, new AddWorkEntryService(), addArg);
		assertTrue(addRes.getMessage(), addRes.isOk());

		String workEntryId;
		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			Resource we = tx.streamResources(TYPE_WORK_ENTRY)
					.filter(e -> e.getRelationId(PARAM_EMPLOYEE).equals(employeeId))
					.findFirst().orElseThrow();
			workEntryId = we.getId();
		}

		// Admin can move start earlier AND extend end time
		ZonedDateTime newStart = ZonedDateTime.parse("2026-03-13T08:00:00+01:00[Europe/Zurich]");
		ZonedDateTime newEnd = ZonedDateTime.parse("2026-03-13T18:00:00+01:00[Europe/Zurich]");
		CorrectWorkEntryService.CorrectWorkEntryArgument correctArg = new CorrectWorkEntryService.CorrectWorkEntryArgument();
		correctArg.workEntryId = workEntryId;
		correctArg.start = newStart;
		correctArg.end = newEnd;
		correctArg.comment = "Administrative shift adjustment";

		ServiceResult correctRes = serviceHandler.doService(certificate, new CorrectWorkEntryService(), correctArg);
		assertTrue(correctRes.getMessage(), correctRes.isOk());

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			Resource we = tx.getResourceBy(TYPE_WORK_ENTRY, workEntryId, true);
			assertTrue(we.getDate(PARAM_START).isEqual(newStart));
			assertTrue(we.getDate(PARAM_END).isEqual(newEnd));
		}

		// Admin deletes the work entry
		ServiceResult deleteRes = serviceHandler.doService(certificate, new RemoveWorkEntryService(),
				new StringArgument(workEntryId));
		assertTrue(deleteRes.getMessage(), deleteRes.isOk());

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			assertNull(tx.getResourceBy(TYPE_WORK_ENTRY, workEntryId, false));
		}
	}

	@Test
	public void shouldNotStartTimerTwice() {
		String employeeId = "emp1";
		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, false)) {
			createEmployee(tx, employeeId, "Emp 1", ZonedDateTime.now());
			tx.commitOnClose();
		}

		ServiceHandler serviceHandler = runtimeMock.getServiceHandler();
		ServiceResult result1 = serviceHandler.doService(certificate, new StartTimerService(),
				new StartTimerService.Argument(employeeId, WorkingLocation.OFFICE));
		assertTrue(result1.isOk());

		ServiceResult result2 = serviceHandler.doService(certificate, new StartTimerService(),
				new StartTimerService.Argument(employeeId, WorkingLocation.OFFICE));
		assertFalse(result2.isOk());
		assertTrue(result2.getMessage().contains("An active work entry already exists"));
	}
}
