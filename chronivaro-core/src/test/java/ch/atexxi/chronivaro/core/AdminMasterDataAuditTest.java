package ch.atexxi.chronivaro.core;

import ch.atexxi.chronivaro.core.model.ChronivaroAuditHelper;
import ch.atexxi.chronivaro.core.model.WorkingLocation;
import ch.atexxi.chronivaro.core.model.WorkingLocationDurationType;
import ch.atexxi.chronivaro.core.search.AuditEventSearch;
import ch.atexxi.chronivaro.core.service.*;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.privilege.model.Certificate;
import li.strolch.service.StringArgument;
import li.strolch.service.StringResult;
import li.strolch.service.api.ServiceHandler;
import li.strolch.service.api.ServiceResult;
import li.strolch.testbase.runtime.RuntimeMock;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;
import static org.junit.Assert.*;

public class AdminMasterDataAuditTest {

	private static RuntimeMock runtimeMock;
	private static Certificate adminCert;

	@BeforeClass
	public static void beforeClass() {
		runtimeMock = new RuntimeMock().mockRuntime("target/" + AdminMasterDataAuditTest.class.getSimpleName(),
				"src/test/resources");
		runtimeMock.startContainer();
		adminCert = runtimeMock.login("admin", "admin");

		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, false)) {
			Resource employee = tx.getResourceTemplate(TYPE_EMPLOYEE, true);
			employee.setId("emp01");
			employee.setName("Audit Employee");
			tx.add(employee);
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
	public void shouldAuditTeamLifecycle() {
		ServiceHandler serviceHandler = runtimeMock.getServiceHandler();

		// Create Team
		CreateTeamService.TeamArgument createArg = new CreateTeamService.TeamArgument();
		createArg.name = "Engineering Audit Team";
		ServiceResult createResult = serviceHandler.doService(adminCert, new CreateTeamService(), createArg);
		assertTrue(createResult.getMessage(), createResult.isOk());

		String teamId;
		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, true)) {
			Resource team = tx.streamResources(TYPE_TEAM)
					.filter(r -> "Engineering Audit Team".equals(r.getName()))
					.findFirst()
					.orElseThrow();
			teamId = team.getId();

			List<Resource> events = new AuditEventSearch()
					.forElementType(TYPE_TEAM)
					.forElementId(teamId)
					.forAction(AUDIT_ACTION_CREATE)
					.search(tx)
					.toList();
			assertEquals(1, events.size());
		}

		// Update Team
		CreateTeamService.UpdateTeamArgument updateArg = new CreateTeamService.UpdateTeamArgument();
		updateArg.id = teamId;
		updateArg.name = "Engineering Audit Team Renamed";
		ServiceResult updateResult = serviceHandler.doService(adminCert, new UpdateTeamService(), updateArg);
		assertTrue(updateResult.getMessage(), updateResult.isOk());

		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, true)) {
			List<Resource> events = new AuditEventSearch()
					.forElementType(TYPE_TEAM)
					.forElementId(teamId)
					.forAction(AUDIT_ACTION_UPDATE)
					.search(tx)
					.toList();
			assertEquals(1, events.size());
			assertEquals(PARAM_NAME, events.get(0).getString(PARAM_NAME));
			assertEquals("Engineering Audit Team", events.get(0).getString(PARAM_OLD_VALUE));
			assertEquals("Engineering Audit Team Renamed", events.get(0).getString(PARAM_NEW_VALUE));
		}

		// Remove Team
		ServiceResult removeResult = serviceHandler.doService(adminCert, new RemoveTeamService(),
				new StringArgument(teamId));
		assertTrue(removeResult.getMessage(), removeResult.isOk());

		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, true)) {
			List<Resource> events = new AuditEventSearch()
					.forElementType(TYPE_TEAM)
					.forElementId(teamId)
					.forAction(AUDIT_ACTION_REMOVE)
					.search(tx)
					.toList();
			assertEquals(1, events.size());
		}
	}

	@Test
	public void shouldAuditLocationAndHolidayCalendarLifecycle() {
		ServiceHandler serviceHandler = runtimeMock.getServiceHandler();

		// Create Holiday Calendar
		CreateHolidayCalendarService.HolidayCalendarArgument calArg = new CreateHolidayCalendarService.HolidayCalendarArgument();
		calArg.name = "Zurich Audit Holidays";
		StringResult calResult = serviceHandler.doService(adminCert, new CreateHolidayCalendarService(), calArg);
		assertTrue(calResult.getMessage(), calResult.isOk());
		String calendarId = calResult.getValue();

		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, true)) {
			List<Resource> events = new AuditEventSearch()
					.forElementType(TYPE_HOLIDAY_CALENDAR)
					.forElementId(calendarId)
					.forAction(AUDIT_ACTION_CREATE)
					.search(tx)
					.toList();
			assertEquals(1, events.size());
		}

		// Create Holiday
		CreateHolidayService.HolidayArgument holArg = new CreateHolidayService.HolidayArgument();
		holArg.holidayCalendarId = calendarId;
		holArg.name = "National Day";
		holArg.date = LocalDate.of(2026, 8, 1);
		holArg.creditFactor = 1.0;
		ServiceResult holResult = serviceHandler.doService(adminCert, new CreateHolidayService(), holArg);
		assertTrue(holResult.getMessage(), holResult.isOk());

		String holidayId;
		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, true)) {
			Resource hol = tx.streamResources(TYPE_HOLIDAY)
					.filter(h -> calendarId.equals(h.getRelationId(PARAM_HOLIDAY_CALENDAR)))
					.findFirst()
					.orElseThrow();
			holidayId = hol.getId();

			List<Resource> events = new AuditEventSearch()
					.forElementType(TYPE_HOLIDAY)
					.forElementId(holidayId)
					.forAction(AUDIT_ACTION_CREATE)
					.search(tx)
					.toList();
			assertEquals(1, events.size());
		}

		// Remove Holiday
		ServiceResult remHolResult = serviceHandler.doService(adminCert, new RemoveHolidayService(),
				new StringArgument(holidayId));
		assertTrue(remHolResult.getMessage(), remHolResult.isOk());

		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, true)) {
			List<Resource> events = new AuditEventSearch()
					.forElementType(TYPE_HOLIDAY)
					.forElementId(holidayId)
					.forAction(AUDIT_ACTION_REMOVE)
					.search(tx)
					.toList();
			assertEquals(1, events.size());
		}

		// Create Location
		CreateLocationService.LocationArgument locArg = new CreateLocationService.LocationArgument();
		locArg.name = "Zurich Audit Office";
		locArg.holidayCalendarId = calendarId;
		locArg.timezone = "Europe/Zurich";
		ServiceResult locResult = serviceHandler.doService(adminCert, new CreateLocationService(), locArg);
		assertTrue(locResult.getMessage(), locResult.isOk());

		String locationId;
		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, true)) {
			Resource loc = tx.streamResources(TYPE_LOCATION)
					.filter(l -> "Zurich Audit Office".equals(l.getName()))
					.findFirst()
					.orElseThrow();
			locationId = loc.getId();

			List<Resource> events = new AuditEventSearch()
					.forElementType(TYPE_LOCATION)
					.forElementId(locationId)
					.forAction(AUDIT_ACTION_CREATE)
					.search(tx)
					.toList();
			assertEquals(1, events.size());
		}

		// Update Location
		CreateLocationService.UpdateLocationArgument updLocArg = new CreateLocationService.UpdateLocationArgument();
		updLocArg.id = locationId;
		updLocArg.name = "Zurich Audit Office HQ";
		updLocArg.holidayCalendarId = calendarId;
		updLocArg.timezone = "Europe/Zurich";
		ServiceResult updLocResult = serviceHandler.doService(adminCert, new UpdateLocationService(), updLocArg);
		assertTrue(updLocResult.getMessage(), updLocResult.isOk());

		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, true)) {
			List<Resource> events = new AuditEventSearch()
					.forElementType(TYPE_LOCATION)
					.forElementId(locationId)
					.forAction(AUDIT_ACTION_UPDATE)
					.search(tx)
					.toList();
			assertEquals(1, events.size());
		}

		// Remove Location
		ServiceResult remLocResult = serviceHandler.doService(adminCert, new RemoveLocationService(),
				new StringArgument(locationId));
		assertTrue(remLocResult.getMessage(), remLocResult.isOk());

		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, true)) {
			List<Resource> events = new AuditEventSearch()
					.forElementType(TYPE_LOCATION)
					.forElementId(locationId)
					.forAction(AUDIT_ACTION_REMOVE)
					.search(tx)
					.toList();
			assertEquals(1, events.size());
		}

		// Remove Holiday Calendar
		ServiceResult remCalResult = serviceHandler.doService(adminCert, new RemoveHolidayCalendarService(),
				new StringArgument(calendarId));
		assertTrue(remCalResult.getMessage(), remCalResult.isOk());

		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, true)) {
			List<Resource> events = new AuditEventSearch()
					.forElementType(TYPE_HOLIDAY_CALENDAR)
					.forElementId(calendarId)
					.forAction(AUDIT_ACTION_REMOVE)
					.search(tx)
					.toList();
			assertEquals(1, events.size());
		}
	}

	@Test
	public void shouldAuditAbsenceTypeLifecycle() {
		ServiceHandler serviceHandler = runtimeMock.getServiceHandler();

		// Create Absence Type
		CreateAbsenceTypeService.AbsenceTypeArgument createArg = new CreateAbsenceTypeService.AbsenceTypeArgument();
		createArg.code = "AUDIT_EDU";
		createArg.name = "Audit Education";
		createArg.active = true;
		createArg.countAsTargetTime = true;
		createArg.reduceVacationCredit = false;
		createArg.paid = true;
		createArg.approvalRequired = false;
		createArg.durationTypes = List.of(DURATION_FULL_DAY, DURATION_HALF_DAY);

		ServiceResult createResult = serviceHandler.doService(adminCert, new CreateAbsenceTypeService(), createArg);
		assertTrue(createResult.getMessage(), createResult.isOk());

		String absenceTypeId;
		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, true)) {
			Resource type = tx.streamResources(TYPE_ABSENCE_TYPE)
					.filter(r -> "AUDIT_EDU".equals(r.getString(PARAM_CODE)))
					.findFirst()
					.orElseThrow();
			absenceTypeId = type.getId();

			List<Resource> events = new AuditEventSearch()
					.forElementType(TYPE_ABSENCE_TYPE)
					.forElementId(absenceTypeId)
					.forAction(AUDIT_ACTION_CREATE)
					.search(tx)
					.toList();
			assertEquals(1, events.size());
		}

		// Update Absence Type
		CreateAbsenceTypeService.UpdateAbsenceTypeArgument updateArg = new CreateAbsenceTypeService.UpdateAbsenceTypeArgument();
		updateArg.id = absenceTypeId;
		updateArg.code = "AUDIT_EDU";
		updateArg.name = "Audit Education Advanced";
		updateArg.active = true;
		updateArg.countAsTargetTime = true;
		updateArg.reduceVacationCredit = false;
		updateArg.paid = true;
		updateArg.approvalRequired = true;
		updateArg.durationTypes = List.of(DURATION_FULL_DAY);

		ServiceResult updateResult = serviceHandler.doService(adminCert, new UpdateAbsenceTypeService(), updateArg);
		assertTrue(updateResult.getMessage(), updateResult.isOk());

		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, true)) {
			List<Resource> events = new AuditEventSearch()
					.forElementType(TYPE_ABSENCE_TYPE)
					.forElementId(absenceTypeId)
					.forAction(AUDIT_ACTION_UPDATE)
					.search(tx)
					.toList();
			assertEquals(1, events.size());
		}

		// Remove Absence Type
		ServiceResult removeResult = serviceHandler.doService(adminCert, new RemoveAbsenceTypeService(),
				new StringArgument(absenceTypeId));
		assertTrue(removeResult.getMessage(), removeResult.isOk());

		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, true)) {
			List<Resource> events = new AuditEventSearch()
					.forElementType(TYPE_ABSENCE_TYPE)
					.forElementId(absenceTypeId)
					.forAction(AUDIT_ACTION_REMOVE)
					.search(tx)
					.toList();
			assertEquals(1, events.size());
		}
	}

	@Test
	public void shouldAuditScheduleTemplateLifecycle() {
		ServiceHandler serviceHandler = runtimeMock.getServiceHandler();

		// Create Schedule Template
		CreateScheduleTemplateService.CreateScheduleTemplateArgument createArg
				= new CreateScheduleTemplateService.CreateScheduleTemplateArgument();
		createArg.name = "Audit 100% Standard";
		createArg.monday = 504;
		createArg.tuesday = 504;
		createArg.wednesday = 504;
		createArg.thursday = 504;
		createArg.friday = 504;

		StringResult createResult = serviceHandler.doService(adminCert, new CreateScheduleTemplateService(), createArg);
		assertTrue(createResult.getMessage(), createResult.isOk());
		String templateId = createResult.getValue();

		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, true)) {
			List<Resource> events = new AuditEventSearch()
					.forElementType(TYPE_EMPLOYMENT_SCHEDULE_TEMPLATE)
					.forElementId(templateId)
					.forAction(AUDIT_ACTION_CREATE)
					.search(tx)
					.toList();
			assertEquals(1, events.size());
		}

		// Update Schedule Template
		UpdateScheduleTemplateService.UpdateScheduleTemplateArgument updateArg
				= new UpdateScheduleTemplateService.UpdateScheduleTemplateArgument();
		updateArg.id = templateId;
		updateArg.name = "Audit 100% Standard Updated";
		updateArg.monday = 480;
		updateArg.tuesday = 480;
		updateArg.wednesday = 480;
		updateArg.thursday = 480;
		updateArg.friday = 480;

		ServiceResult updateResult = serviceHandler.doService(adminCert, new UpdateScheduleTemplateService(), updateArg);
		assertTrue(updateResult.getMessage(), updateResult.isOk());

		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, true)) {
			List<Resource> events = new AuditEventSearch()
					.forElementType(TYPE_EMPLOYMENT_SCHEDULE_TEMPLATE)
					.forElementId(templateId)
					.forAction(AUDIT_ACTION_UPDATE)
					.search(tx)
					.toList();
			assertEquals(1, events.size());
		}

		// Remove Schedule Template
		ServiceResult removeResult = serviceHandler.doService(adminCert, new RemoveScheduleTemplateService(),
				new StringArgument(templateId));
		assertTrue(removeResult.getMessage(), removeResult.isOk());

		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, true)) {
			List<Resource> events = new AuditEventSearch()
					.forElementType(TYPE_EMPLOYMENT_SCHEDULE_TEMPLATE)
					.forElementId(templateId)
					.forAction(AUDIT_ACTION_REMOVE)
					.search(tx)
					.toList();
			assertEquals(1, events.size());
		}
	}

	@Test
	public void shouldAuditWorkingLocationDefaultLifecycle() {
		ServiceHandler serviceHandler = runtimeMock.getServiceHandler();

		// Add Working Location Default
		AddOrUpdateWorkingLocationDefaultService.Argument addArg = new AddOrUpdateWorkingLocationDefaultService.Argument();
		addArg.employeeId = "emp01";
		addArg.weekday = DayOfWeek.MONDAY;
		addArg.durationType = WorkingLocationDurationType.FULL_DAY;
		addArg.workingLocation = WorkingLocation.HOME_OFFICE.name();

		ServiceResult addResult = serviceHandler.doService(adminCert, new AddOrUpdateWorkingLocationDefaultService(), addArg);
		assertTrue(addResult.getMessage(), addResult.isOk());

		String defaultId;
		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, true)) {
			Resource r = tx.streamResources(TYPE_WORKING_LOCATION_DEFAULT)
					.filter(res -> "emp01".equals(res.getRelationId(PARAM_EMPLOYEE))
							&& DayOfWeek.MONDAY.name().equals(res.getString(PARAM_WEEKDAY)))
					.findFirst()
					.orElseThrow();
			defaultId = r.getId();

			List<Resource> events = new AuditEventSearch()
					.forElementType(TYPE_WORKING_LOCATION_DEFAULT)
					.forElementId(defaultId)
					.forAction(AUDIT_ACTION_CREATE)
					.search(tx)
					.toList();
			assertEquals(1, events.size());
		}

		// Remove Working Location Default
		ServiceResult removeResult = serviceHandler.doService(adminCert, new RemoveWorkingLocationDefaultService(),
				new StringArgument(defaultId));
		assertTrue(removeResult.getMessage(), removeResult.isOk());

		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, true)) {
			List<Resource> events = new AuditEventSearch()
					.forElementType(TYPE_WORKING_LOCATION_DEFAULT)
					.forElementId(defaultId)
					.forAction(AUDIT_ACTION_REMOVE)
					.search(tx)
					.toList();
			assertEquals(1, events.size());
		}
	}
}
