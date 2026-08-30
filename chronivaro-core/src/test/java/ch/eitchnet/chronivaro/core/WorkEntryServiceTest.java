package ch.eitchnet.chronivaro.core;

import ch.eitchnet.chronivaro.core.model.ChronivaroModelHelper;
import ch.eitchnet.chronivaro.core.model.ChronivaroVersionHelper;
import ch.eitchnet.chronivaro.core.model.DaySummary;
import ch.eitchnet.chronivaro.core.model.PeriodHelper;
import ch.eitchnet.chronivaro.core.model.ScheduleHelper;
import ch.eitchnet.chronivaro.core.model.WorkDayHelper;
import ch.eitchnet.chronivaro.core.model.WorkEntryHelper;
import ch.eitchnet.chronivaro.core.model.WorkEntryRange;
import ch.eitchnet.chronivaro.core.model.WorkingLocation;
import ch.eitchnet.chronivaro.core.service.AddWorkEntryService;
import ch.eitchnet.chronivaro.core.service.CorrectWorkEntryService;
import ch.eitchnet.chronivaro.core.service.DaySummaryService;
import ch.eitchnet.chronivaro.core.service.MonthSummaryService;
import ch.eitchnet.chronivaro.core.service.RemoveWorkEntryService;
import ch.eitchnet.chronivaro.core.service.StartTimerService;
import ch.eitchnet.chronivaro.core.service.StopTimerService;
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

import static ch.eitchnet.chronivaro.core.ChronivaroTestHelper.createEmployee;
import static ch.eitchnet.chronivaro.core.model.ChronivaroConstants.*;
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
	public void shouldAllowEmployeeToExtendWorkEntryInOpenPeriod() {
		String employeeId = "emp-extend-test";
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

		// Employee extends to 17:00
		ZonedDateTime extendedEnd = ZonedDateTime.parse("2026-03-11T17:00:00+01:00[Europe/Zurich]");
		CorrectWorkEntryService.CorrectWorkEntryArgument correctArg = new CorrectWorkEntryService.CorrectWorkEntryArgument();
		correctArg.workEntryId = workEntryId;
		correctArg.start = start;
		correctArg.end = extendedEnd;
		correctArg.comment = "Extended work hours";

		ServiceResult correctRes = serviceHandler.doService(empCert, new CorrectWorkEntryService(), correctArg);
		assertTrue(correctRes.getMessage(), correctRes.isOk());

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			Resource we = tx.getResourceBy(TYPE_WORK_ENTRY, workEntryId, true);
			assertTrue(we.getDate(PARAM_END).isEqual(extendedEnd));
			assertEquals("Extended work hours", we.getString(PARAM_COMMENT));
			assertTrue(ChronivaroVersionHelper.getVersion(we) > 0);
		}
	}

	@Test
	public void shouldAllowEmployeeToModifyStartTimeInOpenPeriod() {
		String employeeId = "emp-start-mod";
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

		// Employee changes start time to 08:00
		ZonedDateTime earlierStart = ZonedDateTime.parse("2026-03-12T08:00:00+01:00[Europe/Zurich]");
		CorrectWorkEntryService.CorrectWorkEntryArgument correctArg = new CorrectWorkEntryService.CorrectWorkEntryArgument();
		correctArg.workEntryId = workEntryId;
		correctArg.start = earlierStart;
		correctArg.end = end;
		correctArg.comment = "Arrived earlier";
		correctArg.workingLocation = WorkingLocation.HOME_OFFICE;

		ServiceResult correctRes = serviceHandler.doService(empCert, new CorrectWorkEntryService(), correctArg);
		assertTrue(correctRes.getMessage(), correctRes.isOk());

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			Resource we = tx.getResourceBy(TYPE_WORK_ENTRY, workEntryId, true);
			assertTrue(we.getDate(PARAM_START).isEqual(earlierStart));
			assertEquals(WorkingLocation.HOME_OFFICE.name(), we.getString(PARAM_WORKING_LOCATION));
			assertEquals("Arrived earlier", we.getString(PARAM_COMMENT));
			assertTrue(ChronivaroVersionHelper.getVersion(we) > 0);
		}
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
	public void shouldAllowSupervisorAndHRToManageEmployeeWorkEntries() {
		String teamAlphaId = "team-alpha";
		String teamBetaId = "team-beta";
		String supUsername = "supervisor-alpha";
		String hrUsername = "hr-user";
		String empAId = "emp-team-a";
		String empBId = "emp-team-b";

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, false)) {
			// Create supervisor user
			UserRep supUser = new UserRep(null, supUsername, "Sup", "Alpha", UserState.ENABLED, emptySet(),
					Set.of(ROLE_SUPERVISOR, ROLE_MODEL_ACCESSOR), Locale.of("de", "CH"), emptyMap(), null);
			UserRep addedSup = runtimeMock.getPrivilegeHandler().getPrivilegeHandler().addUser(certificate, supUser, supUsername.toCharArray());

			// Create supervisor employee resource
			Resource supEmp = createEmployee(tx, "sup-emp-id", "Supervisor Alpha");
			supEmp.setString(PARAM_USERNAME, addedSup.getUsername());
			supEmp.setString(PARAM_USER_ID, addedSup.getUserId());
			tx.update(supEmp);

			// Create HR user
			UserRep hrUser = new UserRep(null, hrUsername, "HR", "User", UserState.ENABLED, emptySet(),
					Set.of(ROLE_HR, ROLE_MODEL_ACCESSOR), Locale.of("de", "CH"), emptyMap(), null);
			runtimeMock.getPrivilegeHandler().getPrivilegeHandler().addUser(certificate, hrUser, hrUsername.toCharArray());

			// Create Team Alpha with supervisor
			Resource teamAlpha = tx.getResourceTemplate(TYPE_TEAM, true);
			teamAlpha.setId(teamAlphaId);
			teamAlpha.setName("Team Alpha");
			teamAlpha.setRelation(PARAM_LEADER, supEmp);
			tx.add(teamAlpha);

			// Create Team Beta without supervisor
			Resource teamBeta = tx.getResourceTemplate(TYPE_TEAM, true);
			teamBeta.setId(teamBetaId);
			teamBeta.setName("Team Beta");
			tx.add(teamBeta);

			// Create employees in teams
			Resource empA = createEmployee(tx, empAId, "Employee A");
			empA.setRelation(PARAM_PRIMARY_TEAM, teamAlpha);
			tx.update(empA);

			Resource empB = createEmployee(tx, empBId, "Employee B");
			empB.setRelation(PARAM_PRIMARY_TEAM, teamBeta);
			tx.update(empB);

			tx.commitOnClose();
		}

		Certificate supCert = runtimeMock.login(supUsername, supUsername);
		Certificate hrCert = runtimeMock.login(hrUsername, hrUsername);
		ServiceHandler serviceHandler = runtimeMock.getServiceHandler();

		ZonedDateTime startA = ZonedDateTime.parse("2026-06-01T08:00:00+02:00[Europe/Zurich]");
		ZonedDateTime endA = ZonedDateTime.parse("2026-06-01T16:00:00+02:00[Europe/Zurich]");

		// 1. Supervisor adds work entry for supervised employee in Team Alpha -> Success
		AddWorkEntryService.AddWorkEntryArgument addArgA = new AddWorkEntryService.AddWorkEntryArgument();
		addArgA.employeeId = empAId;
		addArgA.start = startA;
		addArgA.end = endA;
		addArgA.workingLocation = WorkingLocation.OFFICE;
		addArgA.comment = "Supervisor manual add";
		ServiceResult addResA = serviceHandler.doService(supCert, new AddWorkEntryService(), addArgA);
		assertTrue(addResA.getMessage(), addResA.isOk());

		String workEntryAId;
		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			Resource we = tx.streamResources(TYPE_WORK_ENTRY)
					.filter(e -> empAId.equals(e.getRelationId(PARAM_EMPLOYEE)))
					.findFirst().orElseThrow();
			workEntryAId = we.getId();
		}

		// 2. Supervisor attempts to add work entry for employee in Team Beta -> Fails
		AddWorkEntryService.AddWorkEntryArgument addArgB = new AddWorkEntryService.AddWorkEntryArgument();
		addArgB.employeeId = empBId;
		addArgB.start = startA;
		addArgB.end = endA;
		ServiceResult addResB = serviceHandler.doService(supCert, new AddWorkEntryService(), addArgB);
		assertFalse(addResB.isOk());

		// 3. Supervisor modifies work entry for supervised employee (full adjustment) -> Success
		ZonedDateTime modStartA = ZonedDateTime.parse("2026-06-01T07:30:00+02:00[Europe/Zurich]");
		ZonedDateTime modEndA = ZonedDateTime.parse("2026-06-01T16:30:00+02:00[Europe/Zurich]");
		CorrectWorkEntryService.CorrectWorkEntryArgument corrArgA = new CorrectWorkEntryService.CorrectWorkEntryArgument();
		corrArgA.workEntryId = workEntryAId;
		corrArgA.start = modStartA;
		corrArgA.end = modEndA;
		corrArgA.workingLocation = WorkingLocation.HOME_OFFICE;
		corrArgA.comment = "Supervisor modified";
		ServiceResult corrResA = serviceHandler.doService(supCert, new CorrectWorkEntryService(), corrArgA);
		assertTrue(corrResA.getMessage(), corrResA.isOk());

		// 4. Supervisor deletes work entry for supervised employee -> Success
		ServiceResult delResA = serviceHandler.doService(supCert, new RemoveWorkEntryService(), new StringArgument(workEntryAId));
		assertTrue(delResA.getMessage(), delResA.isOk());

		// 5. HR adds work entry for employee in Team Beta -> Success
		AddWorkEntryService.AddWorkEntryArgument hrAddArg = new AddWorkEntryService.AddWorkEntryArgument();
		hrAddArg.employeeId = empBId;
		hrAddArg.start = startA;
		hrAddArg.end = endA;
		hrAddArg.workingLocation = WorkingLocation.OFFICE;
		ServiceResult hrAddRes = serviceHandler.doService(hrCert, new AddWorkEntryService(), hrAddArg);
		assertTrue(hrAddRes.getMessage(), hrAddRes.isOk());

		String workEntryBId;
		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			Resource we = tx.streamResources(TYPE_WORK_ENTRY)
					.filter(e -> empBId.equals(e.getRelationId(PARAM_EMPLOYEE)))
					.findFirst().orElseThrow();
			workEntryBId = we.getId();
		}

		// 6. HR deletes work entry for employee in Team Beta -> Success
		ServiceResult hrDelRes = serviceHandler.doService(hrCert, new RemoveWorkEntryService(), new StringArgument(workEntryBId));
		assertTrue(hrDelRes.getMessage(), hrDelRes.isOk());

		// 7. Verify audit log entries
		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			List<Resource> auditEvents = tx.streamResources(TYPE_AUDIT_EVENT)
					.filter(ae -> TYPE_WORK_ENTRY.equals(ae.getString(PARAM_ELEMENT_TYPE)))
					.toList();
			assertTrue("Audit events must exist for work entry actions", auditEvents.size() >= 4);
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

	@Test
	public void shouldRejectCorrectionWithInvalidTimesOrAcrossDays() {
		String employeeId = "emp-invalid-corr";
		String username = "emp_inv_user";

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, false)) {
			Resource employee = createEmployee(tx, employeeId, "Invalid Corr Employee");
			UserRep userRep = new UserRep(null, username, "Inv", "Employee", UserState.ENABLED, emptySet(),
					Set.of(ROLE_EMPLOYEE, ROLE_MODEL_ACCESSOR), Locale.of("de", "CH"), emptyMap(), null);
			UserRep addedUser = runtimeMock.getPrivilegeHandler().getPrivilegeHandler().addUser(certificate, userRep, username.toCharArray());

			employee.setString(PARAM_USERNAME, addedUser.getUsername());
			employee.setString(PARAM_USER_ID, addedUser.getUserId());
			tx.update(employee);
			tx.commitOnClose();
		}

		Certificate empCert = runtimeMock.login(username, username);
		ServiceHandler serviceHandler = runtimeMock.getServiceHandler();

		ZonedDateTime start = ZonedDateTime.parse("2026-03-14T08:00:00+01:00[Europe/Zurich]");
		ZonedDateTime end = ZonedDateTime.parse("2026-03-14T16:00:00+01:00[Europe/Zurich]");

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

		// 1. End before/equal to start
		CorrectWorkEntryService.CorrectWorkEntryArgument corrArg1 = new CorrectWorkEntryService.CorrectWorkEntryArgument();
		corrArg1.workEntryId = workEntryId;
		corrArg1.start = start;
		corrArg1.end = start;
		ServiceResult res1 = serviceHandler.doService(empCert, new CorrectWorkEntryService(), corrArg1);
		assertFalse(res1.isOk());
		assertTrue(res1.getMessage().contains("must be after start time"));

		// 2. Cross-day start and end spanning multiple days beyond next day
		CorrectWorkEntryService.CorrectWorkEntryArgument corrArg2 = new CorrectWorkEntryService.CorrectWorkEntryArgument();
		corrArg2.workEntryId = workEntryId;
		corrArg2.start = start;
		corrArg2.end = ZonedDateTime.parse("2026-03-16T09:00:00+01:00[Europe/Zurich]");
		ServiceResult res2 = serviceHandler.doService(empCert, new CorrectWorkEntryService(), corrArg2);
		assertFalse(res2.isOk());
		assertTrue(res2.getMessage().contains("must start and end on the same day or end on the next day"));
	}

	@Test
	public void shouldExposeModifiedAndCreatorInDaySummaryAndMonthSummary() {
		String employeeId = "emp-summary-meta";
		String username = "emp_summary_user";

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, false)) {
			Resource employee = createEmployee(tx, employeeId, "Summary Meta Employee");
			UserRep userRep = new UserRep(null, username, "Summary", "Meta", UserState.ENABLED, emptySet(),
					Set.of(ROLE_EMPLOYEE, ROLE_MODEL_ACCESSOR), Locale.of("de", "CH"), emptyMap(), null);
			UserRep addedUser = runtimeMock.getPrivilegeHandler().getPrivilegeHandler().addUser(certificate, userRep, username.toCharArray());

			employee.setString(PARAM_USERNAME, addedUser.getUsername());
			employee.setString(PARAM_USER_ID, addedUser.getUserId());
			tx.update(employee);
			tx.commitOnClose();
		}

		ServiceHandler serviceHandler = runtimeMock.getServiceHandler();

		// Created by admin on behalf of employee
		ZonedDateTime start = ZonedDateTime.parse("2026-03-15T08:00:00+01:00[Europe/Zurich]");
		ZonedDateTime end = ZonedDateTime.parse("2026-03-15T16:00:00+01:00[Europe/Zurich]");

		AddWorkEntryService.AddWorkEntryArgument addArg = new AddWorkEntryService.AddWorkEntryArgument();
		addArg.employeeId = employeeId;
		addArg.start = start;
		addArg.end = end;
		addArg.workingLocation = WorkingLocation.OFFICE;
		ServiceResult addRes = serviceHandler.doService(certificate, new AddWorkEntryService(), addArg);
		assertTrue(addRes.getMessage(), addRes.isOk());

		// Day summary before correction: source = MANUAL, createdBy = admin, modified = false
		DaySummaryService.DaySummaryArgument dayArg = new DaySummaryService.DaySummaryArgument();
		dayArg.employeeId = employeeId;
		dayArg.date = start.toLocalDate();
		DaySummaryService.DaySummaryResult dayRes = serviceHandler.doService(certificate, new DaySummaryService(), dayArg);
		assertTrue(dayRes.getMessage(), dayRes.isOk());
		assertEquals(1, dayRes.daySummary.workEntries().size());
		WorkEntryRange rangeBefore = dayRes.daySummary.workEntries().get(0);
		assertEquals(SOURCE_MANUAL, rangeBefore.source());
		assertEquals("admin", rangeBefore.createdBy());
		assertFalse(rangeBefore.modified());

		// Now correct the work entry
		CorrectWorkEntryService.CorrectWorkEntryArgument corrArg = new CorrectWorkEntryService.CorrectWorkEntryArgument();
		corrArg.workEntryId = rangeBefore.id();
		corrArg.start = ZonedDateTime.parse("2026-03-15T08:30:00+01:00[Europe/Zurich]");
		corrArg.end = ZonedDateTime.parse("2026-03-15T16:30:00+01:00[Europe/Zurich]");
		ServiceResult corrRes = serviceHandler.doService(certificate, new CorrectWorkEntryService(), corrArg);
		assertTrue(corrRes.getMessage(), corrRes.isOk());

		// Day summary after correction: modified = true
		DaySummaryService.DaySummaryResult dayResAfter = serviceHandler.doService(certificate, new DaySummaryService(), dayArg);
		assertTrue(dayResAfter.getMessage(), dayResAfter.isOk());
		WorkEntryRange rangeAfter = dayResAfter.daySummary.workEntries().get(0);
		assertTrue(rangeAfter.modified());
		assertEquals("admin", rangeAfter.createdBy());

		// Month summary
		MonthSummaryService.MonthSummaryArgument monthArg = new MonthSummaryService.MonthSummaryArgument();
		monthArg.employeeId = employeeId;
		monthArg.yearMonth = java.time.YearMonth.from(start);
		MonthSummaryService.MonthSummaryResult monthRes = serviceHandler.doService(certificate, new MonthSummaryService(), monthArg);
		assertTrue(monthRes.getMessage(), monthRes.isOk());
		DaySummary dayInMonth = monthRes.monthSummary.daySummaries().stream()
				.filter(ds -> ds.date().equals(start.toLocalDate()))
				.findFirst().orElseThrow();
		assertEquals(1, dayInMonth.workEntries().size());
		assertTrue(dayInMonth.workEntries().get(0).modified());
	}

	@Test
	public void shouldStopForgottenTimerWithSpecificTimeOnStartingDate() {
		String employeeId = "emp-stop-specific";

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, false)) {
			Resource employee = createEmployee(tx, employeeId, "Specific Stop Time");
			employee = tx.readLock(employee);
			employee.setString(PARAM_TIMEZONE, "Europe/Zurich");
			tx.update(employee);
			tx.commitOnClose();
		}

		ServiceHandler serviceHandler = runtimeMock.getServiceHandler();

		// Start timer 3 days ago at 08:30
		LocalDate startDate = LocalDate.now().minusDays(3);
		ZonedDateTime start = startDate.atTime(8, 30).atZone(java.time.ZoneId.of("Europe/Zurich"));

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, false)) {
			Resource employee = tx.getResourceBy(TYPE_EMPLOYEE, employeeId, true);
			Resource workDay = WorkDayHelper.getOrCreateWorkDay(tx, employee, start);
			Resource workEntry = tx.getResourceTemplate(TYPE_WORK_ENTRY, true);
			workEntry.setName("WorkEntry " + start);
			workEntry.setRelation(PARAM_EMPLOYEE, employee);
			workEntry.setRelation(PARAM_WORK_DAY, workDay);
			workEntry.setDate(PARAM_START, start);
			workEntry.setString(PARAM_SOURCE, SOURCE_TIMER);
			workEntry.setString(PARAM_WORKING_LOCATION, WorkingLocation.OFFICE.name());
			Resource scheduleVersion = ScheduleHelper.findScheduleVersion(tx, employeeId).orElseThrow();
			workEntry.setRelation(PARAM_SCHEDULE, scheduleVersion);
			tx.add(workEntry);
			workDay.addRelation(PARAM_WORK_ENTRIES, workEntry);
			tx.update(workDay);
			tx.commitOnClose();
		}

		// Stop timer fixing the time to 17:00 on the starting date
		ZonedDateTime stopTime = startDate.atTime(17, 0).atZone(java.time.ZoneId.of("Europe/Zurich"));
		StopTimerService.StopTimerArgument stopArg = new StopTimerService.StopTimerArgument(employeeId, stopTime, "Forgot to stop");
		ServiceResult stopResult = serviceHandler.doService(certificate, new StopTimerService(), stopArg);
		assertTrue(stopResult.getMessage(), stopResult.isOk());

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			assertTrue(WorkEntryHelper.findActiveWorkEntry(tx, employeeId).isEmpty());
			List<Resource> entries = tx.streamResources(TYPE_WORK_ENTRY)
					.filter(e -> e.getRelationId(PARAM_EMPLOYEE).equals(employeeId))
					.toList();
			assertEquals(1, entries.size());
			Resource stoppedEntry = entries.getFirst();
			assertEquals(stopTime, stoppedEntry.getDate(PARAM_END));
			assertEquals("Forgot to stop", stoppedEntry.getString(PARAM_COMMENT));
		}
	}

	@Test
	public void shouldAllowEmployeeToDeleteOwnWorkEntryAndRejectDeletingOthersOrInLockedPeriod() {
		String emp1Id = "emp-del-1";
		String emp1User = "emp_del_user_1";
		String emp2Id = "emp-del-2";
		String emp2User = "emp_del_user_2";

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, false)) {
			Resource e1 = createEmployee(tx, emp1Id, "Delete Test User 1");
			UserRep u1 = new UserRep(null, emp1User, "Del", "One", UserState.ENABLED, emptySet(),
					Set.of(ROLE_EMPLOYEE, ROLE_MODEL_ACCESSOR), Locale.of("de", "CH"), emptyMap(), null);
			UserRep added1 = runtimeMock.getPrivilegeHandler().getPrivilegeHandler().addUser(certificate, u1, emp1User.toCharArray());
			e1.setString(PARAM_USERNAME, added1.getUsername());
			e1.setString(PARAM_USER_ID, added1.getUserId());
			tx.update(e1);

			Resource e2 = createEmployee(tx, emp2Id, "Delete Test User 2");
			UserRep u2 = new UserRep(null, emp2User, "Del", "Two", UserState.ENABLED, emptySet(),
					Set.of(ROLE_EMPLOYEE, ROLE_MODEL_ACCESSOR), Locale.of("de", "CH"), emptyMap(), null);
			UserRep added2 = runtimeMock.getPrivilegeHandler().getPrivilegeHandler().addUser(certificate, u2, emp2User.toCharArray());
			e2.setString(PARAM_USERNAME, added2.getUsername());
			e2.setString(PARAM_USER_ID, added2.getUserId());
			tx.update(e2);

			tx.commitOnClose();
		}

		Certificate emp1Cert = runtimeMock.login(emp1User, emp1User);
		Certificate emp2Cert = runtimeMock.login(emp2User, emp2User);
		ServiceHandler serviceHandler = runtimeMock.getServiceHandler();

		// Add work entry for emp1
		ZonedDateTime start1 = ZonedDateTime.parse("2026-07-10T08:00:00+02:00[Europe/Zurich]");
		ZonedDateTime end1 = ZonedDateTime.parse("2026-07-10T16:00:00+02:00[Europe/Zurich]");
		AddWorkEntryService.AddWorkEntryArgument addArg1 = new AddWorkEntryService.AddWorkEntryArgument();
		addArg1.employeeId = emp1Id;
		addArg1.start = start1;
		addArg1.end = end1;
		addArg1.workingLocation = WorkingLocation.OFFICE;
		ServiceResult addRes1 = serviceHandler.doService(certificate, new AddWorkEntryService(), addArg1);
		assertTrue(addRes1.getMessage(), addRes1.isOk());

		// Add work entry for emp2
		ZonedDateTime start2 = ZonedDateTime.parse("2026-07-10T09:00:00+02:00[Europe/Zurich]");
		ZonedDateTime end2 = ZonedDateTime.parse("2026-07-10T17:00:00+02:00[Europe/Zurich]");
		AddWorkEntryService.AddWorkEntryArgument addArg2 = new AddWorkEntryService.AddWorkEntryArgument();
		addArg2.employeeId = emp2Id;
		addArg2.start = start2;
		addArg2.end = end2;
		addArg2.workingLocation = WorkingLocation.HOME_OFFICE;
		ServiceResult addRes2 = serviceHandler.doService(certificate, new AddWorkEntryService(), addArg2);
		assertTrue(addRes2.getMessage(), addRes2.isOk());

		String entry1Id;
		String entry2Id;
		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			entry1Id = tx.streamResources(TYPE_WORK_ENTRY)
					.filter(we -> emp1Id.equals(we.getRelationId(PARAM_EMPLOYEE)))
					.findFirst().orElseThrow().getId();
			entry2Id = tx.streamResources(TYPE_WORK_ENTRY)
					.filter(we -> emp2Id.equals(we.getRelationId(PARAM_EMPLOYEE)))
					.findFirst().orElseThrow().getId();
		}

		// 1. emp1 attempts to delete emp2's work entry -> Fails (AccessDenied)
		ServiceResult delResOther = serviceHandler.doService(emp1Cert, new RemoveWorkEntryService(), new StringArgument(entry2Id));
		assertFalse("Employee must not be able to delete another employee's work entry", delResOther.isOk());

		// 2. emp1 deletes own work entry -> Success
		ServiceResult delResSelf = serviceHandler.doService(emp1Cert, new RemoveWorkEntryService(), new StringArgument(entry1Id));
		assertTrue(delResSelf.getMessage(), delResSelf.isOk());

		// Verify entry1 is deleted from model and work day relations
		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			assertNull(tx.getResourceBy(TYPE_WORK_ENTRY, entry1Id, false));
			Resource workDay = tx.getResourceBy(TYPE_WORK_DAY, emp1Id + "-" + start1.toLocalDate(), false);
			if (workDay != null && workDay.hasParameter(BAG_RELATIONS, PARAM_WORK_ENTRIES)) {
				assertFalse(workDay.getStringList(BAG_RELATIONS, PARAM_WORK_ENTRIES).contains(entry1Id));
			}
		}

		// 3. Re-add entry for emp1 in August 2026, lock period (SUBMITTED), emp1 attempts to delete -> Fails
		ZonedDateTime startAug = ZonedDateTime.parse("2026-08-10T08:00:00+02:00[Europe/Zurich]");
		ZonedDateTime endAug = ZonedDateTime.parse("2026-08-10T16:00:00+02:00[Europe/Zurich]");
		AddWorkEntryService.AddWorkEntryArgument addArgAug = new AddWorkEntryService.AddWorkEntryArgument();
		addArgAug.employeeId = emp1Id;
		addArgAug.start = startAug;
		addArgAug.end = endAug;
		addArgAug.workingLocation = WorkingLocation.OFFICE;
		ServiceResult addResAug = serviceHandler.doService(certificate, new AddWorkEntryService(), addArgAug);
		assertTrue(addResAug.getMessage(), addResAug.isOk());

		String entryAugId;
		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, false)) {
			entryAugId = tx.streamResources(TYPE_WORK_ENTRY)
					.filter(we -> emp1Id.equals(we.getRelationId(PARAM_EMPLOYEE)))
					.filter(we -> startAug.equals(we.getDate(PARAM_START)))
					.findFirst().orElseThrow().getId();
			Resource period = PeriodHelper.getOrCreatePeriod(tx, emp1Id, java.time.YearMonth.from(startAug));
			period.setString(PARAM_STATE, STATE_SUBMITTED);
			tx.update(period);
			tx.commitOnClose();
		}

		ServiceResult delResLocked = serviceHandler.doService(emp1Cert, new RemoveWorkEntryService(), new StringArgument(entryAugId));
		assertFalse("Deleting work entry in locked/submitted period must fail", delResLocked.isOk());
	}
}
