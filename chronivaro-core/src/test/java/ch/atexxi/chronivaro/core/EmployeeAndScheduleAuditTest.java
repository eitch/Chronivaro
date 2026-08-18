package ch.atexxi.chronivaro.core;

import ch.atexxi.chronivaro.core.model.ChronivaroAuditHelper;
import ch.atexxi.chronivaro.core.search.AuditEventSearch;
import ch.atexxi.chronivaro.core.service.*;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.privilege.model.Certificate;
import li.strolch.privilege.model.UserRep;
import li.strolch.service.StringArgument;
import li.strolch.service.StringResult;
import li.strolch.service.api.ServiceHandler;
import li.strolch.service.api.ServiceResult;
import li.strolch.testbase.runtime.RuntimeMock;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;
import static org.junit.Assert.*;

public class EmployeeAndScheduleAuditTest {

	private static RuntimeMock runtimeMock;
	private static Certificate adminCert;

	@BeforeClass
	public static void beforeClass() {
		runtimeMock = new RuntimeMock().mockRuntime("target/" + EmployeeAndScheduleAuditTest.class.getSimpleName(),
				"src/test/resources");
		runtimeMock.startContainer();
		adminCert = runtimeMock.login("admin", "admin");

		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, false)) {
			Resource location = tx.getResourceTemplate(TYPE_LOCATION, true);
			location.setId("loc1");
			location.setName("Location 1");
			tx.add(location);

			Resource team = tx.getResourceTemplate(TYPE_TEAM, true);
			team.setId("team1");
			team.setName("Team 1");
			tx.add(team);

			Resource template = tx.getResourceTemplate(TYPE_EMPLOYMENT_SCHEDULE_TEMPLATE, true);
			template.setId("template-fulltime");
			template.setName("Full-time Template");
			template.setInteger(PARAM_DAILY_TARGET_MINUTES_MONDAY, 480);
			template.setInteger(PARAM_DAILY_TARGET_MINUTES_TUESDAY, 480);
			template.setInteger(PARAM_DAILY_TARGET_MINUTES_WEDNESDAY, 480);
			template.setInteger(PARAM_DAILY_TARGET_MINUTES_THURSDAY, 480);
			template.setInteger(PARAM_DAILY_TARGET_MINUTES_FRIDAY, 480);
			template.setInteger(PARAM_DAILY_TARGET_MINUTES_SATURDAY, 0);
			template.setInteger(PARAM_DAILY_TARGET_MINUTES_SUNDAY, 0);
			tx.add(template);

			tx.commitOnClose();
		}
	}

	@AfterClass
	public static void afterClass() {
		if (runtimeMock != null)
			runtimeMock.destroyRuntime();
	}

	@Before
	public void cleanAuditEvents() {
		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, false)) {
			for (Resource r : tx.streamResources(TYPE_AUDIT_EVENT).toList()) {
				tx.remove(r);
			}
			tx.commitOnClose();
		}
		ChronivaroAuditHelper.removeCorrelationId();
	}

	@Test
	public void shouldAuditEmployeeLifecycle() {
		ServiceHandler serviceHandler = runtimeMock.getServiceHandler();
		String correlationId = "corr-emp-lifecycle";
		ChronivaroAuditHelper.setCorrelationId(correlationId);

		String username = "emp_audit_user";

		// 1. Create Employee
		CreateEmployeeService.EmployeeArgument createArg = new CreateEmployeeService.EmployeeArgument();
		createArg.personalNumber = "EMP-001";
		createArg.firstname = "Alice";
		createArg.lastname = "Smith";
		createArg.birthdate = LocalDate.of(1992, 3, 15);
		createArg.teamId = "team1";
		createArg.locationId = "loc1";
		createArg.timezone = "Europe/Zurich";
		createArg.joinDate = LocalDate.of(2026, 1, 1);
		createArg.active = true;
		createArg.username = username;

		ServiceResult createResult = serviceHandler.doService(adminCert, new CreateEmployeeService(), createArg);
		assertTrue(createResult.getMessage(), createResult.isOk());

		String employeeId = ((StringResult) createResult).getValue();

		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, true)) {
			List<Resource> events = new AuditEventSearch()
					.forElementType(TYPE_EMPLOYEE)
					.forElementId(employeeId)
					.search(tx)
					.toList();
			assertEquals(1, events.size());
			Resource createEvent = events.getFirst();
			assertEquals(AUDIT_ACTION_CREATE, createEvent.getString(PARAM_ACTION));
			assertEquals(correlationId, createEvent.getString(PARAM_CORRELATION_ID));
			assertEquals("admin", createEvent.getString(PARAM_CREATED_BY));
			assertTrue(createEvent.getString(PARAM_DETAILS).contains("Alice Smith"));
		}

		// 2. Update Employee
		CreateEmployeeService.UpdateEmployeeArgument updateArg = new CreateEmployeeService.UpdateEmployeeArgument();
		updateArg.id = employeeId;
		updateArg.personalNumber = "EMP-001";
		updateArg.firstname = "Alice";
		updateArg.lastname = "Johnson";
		updateArg.birthdate = LocalDate.of(1992, 3, 15);
		updateArg.teamId = "team1";
		updateArg.locationId = "loc1";
		updateArg.timezone = "Europe/Zurich";
		updateArg.joinDate = LocalDate.of(2026, 1, 1);
		updateArg.active = true;
		updateArg.username = username;

		ServiceResult updateResult = serviceHandler.doService(adminCert, new UpdateEmployeeService(), updateArg);
		assertTrue(updateResult.getMessage(), updateResult.isOk());

		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, true)) {
			List<Resource> events = new AuditEventSearch()
					.forElementType(TYPE_EMPLOYEE)
					.forElementId(employeeId)
					.search(tx)
					.toList();
			assertEquals(2, events.size());
			Resource updateEvent = events.stream()
					.filter(e -> AUDIT_ACTION_UPDATE.equals(e.getString(PARAM_ACTION)))
					.findFirst()
					.orElseThrow();
			assertEquals(correlationId, updateEvent.getString(PARAM_CORRELATION_ID));
			assertTrue(updateEvent.getString(PARAM_DETAILS).contains("Alice Johnson"));
		}

		// 3. Remove Employee
		ServiceResult removeResult = serviceHandler.doService(adminCert, new RemoveEmployeeService(),
				new StringArgument(employeeId));
		assertTrue(removeResult.getMessage(), removeResult.isOk());

		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, true)) {
			List<Resource> events = new AuditEventSearch()
					.forElementType(TYPE_EMPLOYEE)
					.forElementId(employeeId)
					.search(tx)
					.toList();
			assertEquals(3, events.size());
			Resource removeEvent = events.stream()
					.filter(e -> AUDIT_ACTION_REMOVE.equals(e.getString(PARAM_ACTION)))
					.findFirst()
					.orElseThrow();
			assertEquals(correlationId, removeEvent.getString(PARAM_CORRELATION_ID));
			assertTrue(removeEvent.getString(PARAM_DETAILS).contains("Alice Johnson"));
		}
	}

	@Test
	public void shouldAuditEmployeeWithScheduleTemplateCreation() {
		ServiceHandler serviceHandler = runtimeMock.getServiceHandler();

		CreateEmployeeService.EmployeeArgument createArg = new CreateEmployeeService.EmployeeArgument();
		createArg.personalNumber = "EMP-002";
		createArg.firstname = "Bob";
		createArg.lastname = "Builder";
		createArg.birthdate = LocalDate.of(1985, 7, 10);
		createArg.teamId = "team1";
		createArg.locationId = "loc1";
		createArg.timezone = "Europe/Zurich";
		createArg.joinDate = LocalDate.of(2026, 1, 1);
		createArg.scheduleTemplateId = "template-fulltime";
		createArg.active = true;
		createArg.username = "bob_builder";

		ServiceResult createResult = serviceHandler.doService(adminCert, new CreateEmployeeService(), createArg);
		assertTrue(createResult.getMessage(), createResult.isOk());

		String employeeId = ((StringResult) createResult).getValue();

		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, true)) {
			// Employee audit event
			List<Resource> empEvents = new AuditEventSearch()
					.forElementType(TYPE_EMPLOYEE)
					.forElementId(employeeId)
					.search(tx)
					.toList();
			assertEquals(1, empEvents.size());
			assertEquals(AUDIT_ACTION_CREATE, empEvents.getFirst().getString(PARAM_ACTION));

			// Schedule audit event
			List<Resource> schedEvents = new AuditEventSearch()
					.forElementType(TYPE_EMPLOYMENT_SCHEDULE)
					.search(tx)
					.toList();
			assertEquals(1, schedEvents.size());
			Resource schedEvent = schedEvents.getFirst();
			assertEquals(AUDIT_ACTION_CREATE, schedEvent.getString(PARAM_ACTION));
			assertTrue(schedEvent.getString(PARAM_DETAILS).contains("Bob Builder"));
			assertTrue(schedEvent.getString(PARAM_DETAILS).contains("Full-time Template"));
		}

		// Cleanup employee
		serviceHandler.doService(adminCert, new RemoveEmployeeService(), new StringArgument(employeeId));
	}

	@Test
	public void shouldAuditRegistrationFlow() throws Exception {
		ServiceHandler serviceHandler = runtimeMock.getServiceHandler();

		String username = "reg_user";

		CreateEmployeeService.EmployeeArgument createArg = new CreateEmployeeService.EmployeeArgument();
		createArg.personalNumber = "EMP-REG";
		createArg.firstname = "Reg";
		createArg.lastname = "User";
		createArg.teamId = "team1";
		createArg.locationId = "loc1";
		createArg.timezone = "Europe/Zurich";
		createArg.joinDate = LocalDate.of(2026, 1, 1);
		createArg.scheduleTemplateId = "template-fulltime";
		createArg.active = true;
		createArg.username = username;

		ServiceResult createResult = serviceHandler.doService(adminCert, new CreateEmployeeService(), createArg);
		assertTrue(createResult.getMessage(), createResult.isOk());
		String employeeId = ((StringResult) createResult).getValue();

		// Clean creation events
		cleanAuditEvents();

		// Initiate Registration
		ServiceResult initResult = serviceHandler.doService(adminCert, new InitiateEmployeeRegistrationService(),
				new StringArgument(employeeId));
		assertTrue(initResult.getMessage(), initResult.isOk());

		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, true)) {
			List<Resource> events = new AuditEventSearch()
					.forElementType(TYPE_EMPLOYEE)
					.forElementId(employeeId)
					.search(tx)
					.toList();
			assertEquals(1, events.size());
			Resource initEvent = events.getFirst();
			assertEquals(AUDIT_ACTION_REGISTRATION_INITIATED, initEvent.getString(PARAM_ACTION));
			assertTrue(initEvent.getString(PARAM_DETAILS).contains(username));
		}

		// Complete Registration
		TestUserChallengeHandler challengeHandler = TestUserChallengeHandler.getInstance();
		String challengeCode = challengeHandler.getChallenges().values().stream()
				.filter(c -> c.getUser().getUsername().equals(username))
				.map(li.strolch.privilege.model.internal.UserChallenge::getChallenge)
				.findFirst()
				.orElseThrow();

		CompleteRegistrationService.CompleteRegistrationArgument compArg = new CompleteRegistrationService.CompleteRegistrationArgument();
		compArg.username = username;
		compArg.challenge = challengeCode;
		compArg.source = "test";
		compArg.password = "NewSecretPassword123!";

		ServiceResult compResult = runtimeMock.getAgent().runAsAgentWithResult(ctx -> {
			return serviceHandler.doService(ctx.getCertificate(), new CompleteRegistrationService(), compArg);
		});
		assertTrue(compResult.getMessage(), compResult.isOk());

		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, true)) {
			List<Resource> events = new AuditEventSearch()
					.forElementType(TYPE_EMPLOYEE)
					.forElementId(employeeId)
					.search(tx)
					.toList();
			assertEquals(2, events.size());
			Resource compEvent = events.stream()
					.filter(e -> AUDIT_ACTION_REGISTRATION_COMPLETED.equals(e.getString(PARAM_ACTION)))
					.findFirst()
					.orElseThrow();
			assertTrue(compEvent.getString(PARAM_DETAILS).contains(username));
		}

		// Cleanup
		serviceHandler.doService(adminCert, new RemoveEmployeeService(), new StringArgument(employeeId));
	}

	@Test
	public void shouldAuditScheduleLifecycleAndVersioning() {
		ServiceHandler serviceHandler = runtimeMock.getServiceHandler();

		// Create employee first
		CreateEmployeeService.EmployeeArgument createArg = new CreateEmployeeService.EmployeeArgument();
		createArg.personalNumber = "EMP-SCHED";
		createArg.firstname = "Charlie";
		createArg.lastname = "Schedule";
		createArg.teamId = "team1";
		createArg.locationId = "loc1";
		createArg.timezone = "Europe/Zurich";
		createArg.joinDate = LocalDate.of(2026, 1, 1);
		createArg.active = true;
		createArg.username = "charlie_sched";

		ServiceResult createResult = serviceHandler.doService(adminCert, new CreateEmployeeService(), createArg);
		assertTrue(createResult.getMessage(), createResult.isOk());
		String employeeId = ((StringResult) createResult).getValue();

		cleanAuditEvents();

		// 1. Create Schedule
		CreateScheduleService.CreateScheduleArgument schedArg = new CreateScheduleService.CreateScheduleArgument();
		schedArg.employeeId = employeeId;
		schedArg.validFrom = ZonedDateTime.parse("2026-01-01T00:00:00+01:00[Europe/Zurich]");
		schedArg.monday = 480;
		schedArg.tuesday = 480;
		schedArg.wednesday = 480;
		schedArg.thursday = 480;
		schedArg.friday = 480;
		schedArg.saturday = 0;
		schedArg.sunday = 0;

		ServiceResult schedResult = serviceHandler.doService(adminCert, new CreateScheduleService(), schedArg);
		assertTrue(schedResult.getMessage(), schedResult.isOk());

		String scheduleId;
		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, true)) {
			Resource schedule = tx.streamResources(TYPE_EMPLOYMENT_SCHEDULE)
					.filter(s -> employeeId.equals(s.getRelationId(PARAM_EMPLOYEE)))
					.findFirst()
					.orElseThrow();
			scheduleId = schedule.getId();

			List<Resource> events = new AuditEventSearch()
					.forElementType(TYPE_EMPLOYMENT_SCHEDULE)
					.forElementId(scheduleId)
					.search(tx)
					.toList();
			assertEquals(1, events.size());
			assertEquals(AUDIT_ACTION_CREATE, events.getFirst().getString(PARAM_ACTION));
		}

		// 2. Update Schedule in place (no versioning needed when same validFrom and no work entries)
		UpdateScheduleService.UpdateScheduleArgument updateArg = new UpdateScheduleService.UpdateScheduleArgument();
		updateArg.id = scheduleId;
		updateArg.validFrom = ZonedDateTime.parse("2026-01-01T00:00:00+01:00[Europe/Zurich]");
		updateArg.monday = 450;
		updateArg.tuesday = 450;
		updateArg.wednesday = 450;
		updateArg.thursday = 450;
		updateArg.friday = 450;
		updateArg.saturday = 0;
		updateArg.sunday = 0;

		ServiceResult updateResult = serviceHandler.doService(adminCert, new UpdateScheduleService(), updateArg);
		assertTrue(updateResult.getMessage(), updateResult.isOk());

		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, true)) {
			List<Resource> events = new AuditEventSearch()
					.forElementType(TYPE_EMPLOYMENT_SCHEDULE)
					.forElementId(scheduleId)
					.search(tx)
					.toList();
			assertEquals(2, events.size());
			Resource updateEvent = events.stream()
					.filter(e -> AUDIT_ACTION_UPDATE.equals(e.getString(PARAM_ACTION)))
					.findFirst()
					.orElseThrow();
			assertTrue(updateEvent.getString(PARAM_DETAILS).contains("Updated schedule"));
		}

		// 3. Update Schedule with new start date -> triggers versioning
		UpdateScheduleService.UpdateScheduleArgument versionArg = new UpdateScheduleService.UpdateScheduleArgument();
		versionArg.id = scheduleId;
		versionArg.validFrom = ZonedDateTime.parse("2026-06-01T00:00:00+02:00[Europe/Zurich]");
		versionArg.monday = 400;
		versionArg.tuesday = 400;
		versionArg.wednesday = 400;
		versionArg.thursday = 400;
		versionArg.friday = 400;
		versionArg.saturday = 0;
		versionArg.sunday = 0;

		ServiceResult versionResult = serviceHandler.doService(adminCert, new UpdateScheduleService(), versionArg);
		assertTrue(versionResult.getMessage(), versionResult.isOk());

		String newScheduleId;
		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, true)) {
			// Check that old version has an update audit event for closing validTo
			List<Resource> oldEvents = new AuditEventSearch()
					.forElementType(TYPE_EMPLOYMENT_SCHEDULE)
					.forElementId(scheduleId)
					.search(tx)
					.toList();
			assertEquals(3, oldEvents.size());

			// Find new version
			Resource newSched = tx.streamResources(TYPE_EMPLOYMENT_SCHEDULE)
					.filter(s -> employeeId.equals(s.getRelationId(PARAM_EMPLOYEE)) && !s.getId().equals(scheduleId))
					.findFirst()
					.orElseThrow();
			newScheduleId = newSched.getId();

			List<Resource> newEvents = new AuditEventSearch()
					.forElementType(TYPE_EMPLOYMENT_SCHEDULE)
					.forElementId(newScheduleId)
					.search(tx)
					.toList();
			assertEquals(1, newEvents.size());
			assertEquals(AUDIT_ACTION_CREATE, newEvents.getFirst().getString(PARAM_ACTION));
			assertTrue(newEvents.getFirst().getString(PARAM_DETAILS).contains("Created new schedule version"));
		}

		// 4. Remove schedule
		ServiceResult removeResult = serviceHandler.doService(adminCert, new RemoveScheduleService(),
				new StringArgument(newScheduleId));
		assertTrue(removeResult.getMessage(), removeResult.isOk());

		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, true)) {
			List<Resource> events = new AuditEventSearch()
					.forElementType(TYPE_EMPLOYMENT_SCHEDULE)
					.forElementId(newScheduleId)
					.search(tx)
					.toList();
			assertEquals(2, events.size());
			Resource removeEvent = events.stream()
					.filter(e -> AUDIT_ACTION_REMOVE.equals(e.getString(PARAM_ACTION)))
					.findFirst()
					.orElseThrow();
			assertTrue(removeEvent.getString(PARAM_DETAILS).contains("Removed schedule"));
		}

		// Cleanup employee
		serviceHandler.doService(adminCert, new RemoveEmployeeService(), new StringArgument(employeeId));
	}
}
