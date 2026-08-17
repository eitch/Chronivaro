package ch.atexxi.chronivaro.core;

import ch.atexxi.chronivaro.core.model.AbsenceHelper;
import ch.atexxi.chronivaro.core.service.PresenceService;
import ch.atexxi.chronivaro.core.service.StartTimerService;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.privilege.model.Certificate;
import li.strolch.privilege.model.PrivilegeContext;
import li.strolch.privilege.model.SimpleRestrictable;
import li.strolch.service.StringArgument;
import li.strolch.service.api.ServiceHandler;
import li.strolch.testbase.runtime.RuntimeMock;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;

import static ch.atexxi.chronivaro.core.ChronivaroTestHelper.createAbsenceType;
import static ch.atexxi.chronivaro.core.ChronivaroTestHelper.createEmployee;
import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;
import static ch.atexxi.chronivaro.core.service.PresenceService.PRIVILEGE_GET_ABSENCE_REASON;
import static org.junit.Assert.*;

public class PresenceServiceTest {

	private static RuntimeMock runtimeMock;
	private static Certificate certificate;

	@BeforeClass
	public static void beforeClass() {
		runtimeMock = new RuntimeMock().mockRuntime("target/" + PresenceServiceTest.class.getSimpleName(),
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
	public void shouldCalculatePresence() {
		String teamId = "team-p";
		String locationId = "loc-p";

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, false)) {
			// Team
			Resource team = tx.getResourceTemplate(TYPE_TEAM, true);
			team.setId(teamId);
			team.setName("Test Team");
			tx.add(team);

			// Emp 1: Working
			Resource e1 = createEmployee(tx, "p-emp1", "Emp 1", ZonedDateTime.now());
			e1 = tx.readLock(e1);
			e1.setRelationId(PARAM_LOCATION, locationId);
			e1.setRelation(PARAM_PRIMARY_TEAM, team);
			tx.update(e1);

			Resource s1 = tx.readLock(tx.getResourceByRelation(e1, PARAM_CURRENT_SCHEDULE, true));
			s1.setInteger(PARAM_DAILY_TARGET_MINUTES + "Monday", 480);
			s1.setInteger(PARAM_DAILY_TARGET_MINUTES + "Tuesday", 480);
			s1.setInteger(PARAM_DAILY_TARGET_MINUTES + "Wednesday", 480);
			s1.setInteger(PARAM_DAILY_TARGET_MINUTES + "Thursday", 480);
			s1.setInteger(PARAM_DAILY_TARGET_MINUTES + "Friday", 480);
			s1.setInteger(PARAM_DAILY_TARGET_MINUTES + "Saturday", 0);
			s1.setInteger(PARAM_DAILY_TARGET_MINUTES + "Sunday", 0);
			tx.update(s1);

			// Emp 2: Not working
			Resource e2 = createEmployee(tx, "p-emp2", "Emp 2", ZonedDateTime.now());
			e2 = tx.readLock(e2);
			e2.setRelationId(PARAM_LOCATION, locationId);
			e2.setRelation(PARAM_PRIMARY_TEAM, team);
			tx.update(e2);

			Resource s2 = tx.readLock(tx.getResourceByRelation(e2, PARAM_CURRENT_SCHEDULE, true));
			s2.setInteger(PARAM_DAILY_TARGET_MINUTES + "Monday", 480);
			s2.setInteger(PARAM_DAILY_TARGET_MINUTES + "Tuesday", 480);
			s2.setInteger(PARAM_DAILY_TARGET_MINUTES + "Wednesday", 480);
			s2.setInteger(PARAM_DAILY_TARGET_MINUTES + "Thursday", 480);
			s2.setInteger(PARAM_DAILY_TARGET_MINUTES + "Friday", 480);
			s2.setInteger(PARAM_DAILY_TARGET_MINUTES + "Saturday", 0);
			s2.setInteger(PARAM_DAILY_TARGET_MINUTES + "Sunday", 0);
			tx.update(s2);

			// Emp 3: Absence
			Resource e3 = createEmployee(tx, "p-emp3", "Emp 3", ZonedDateTime.now());
			e3 = tx.readLock(e3);
			e3.setRelationId(PARAM_LOCATION, locationId);
			e3.setRelation(PARAM_PRIMARY_TEAM, team);
			tx.update(e3);

			Resource at = createAbsenceType(tx, "VACATION", "Vacation");
			Resource absence = tx.getResourceTemplate(TYPE_ABSENCE, true);
			absence.setId("abs1");
			absence.setRelation(PARAM_EMPLOYEE, e3);
			absence.setRelation(PARAM_ABSENCE_TYPE, at);
			absence.setDate(PARAM_START, ZonedDateTime.now().minusHours(1));
			absence.setDate(PARAM_END, ZonedDateTime.now().plusHours(1));
			absence.setString(PARAM_STATE, STATE_APPROVED);
			absence.setString(PARAM_DURATION_TYPE, DURATION_FULL_DAY);
			tx.add(absence);

			tx.commitOnClose();
		}

		ServiceHandler serviceHandler = runtimeMock.getServiceHandler();

		// Start timer for Emp 1
		serviceHandler.doService(certificate, new StartTimerService(), new StringArgument("p-emp1"));

		// Check presence
		PresenceService.PresenceArgument arg = new PresenceService.PresenceArgument();
		arg.teamId = teamId;
		PresenceService.PresenceResult result = serviceHandler.doService(certificate, new PresenceService(), arg);
		assertTrue(result.isOk());
		assertEquals(3, result.presenceInfos.size());

		PresenceService.PresenceInfo info1 = result.presenceInfos
				.stream()
				.filter(i -> i.employeeId().equals("p-emp1"))
				.findFirst()
				.orElseThrow();
		assertEquals(PresenceService.PresenceStatus.WORKING, info1.status());
		assertEquals(PresenceService.PresenceStatus.WORKING.getLabel(), info1.statusLabel());
		assertTrue(info1.minutesToday() >= 0);
		assertNull(info1.absenceTypeCode());
		boolean isOffDuty = switch (LocalDate.now().getDayOfWeek()) {
			case SATURDAY, SUNDAY -> true;
			default -> false;
		};
		assertEquals(isOffDuty, info1.isOff());

		PresenceService.PresenceInfo info2 = result.presenceInfos
				.stream()
				.filter(i -> i.employeeId().equals("p-emp2"))
				.findFirst()
				.orElseThrow();
		assertEquals(PresenceService.PresenceStatus.NOT_WORKING, info2.status());
		assertEquals(PresenceService.PresenceStatus.NOT_WORKING.getLabel(), info2.statusLabel());
		assertEquals(0, info2.minutesToday());
		assertNull(info2.absenceTypeCode());
		assertEquals(isOffDuty, info2.isOff());

		PresenceService.PresenceInfo info3 = result.presenceInfos
				.stream()
				.filter(i -> i.employeeId().equals("p-emp3"))
				.findFirst()
				.orElseThrow();
		assertEquals(PresenceService.PresenceStatus.NOT_WORKING, info3.status());
		assertEquals(PresenceService.PresenceStatus.NOT_WORKING.getLabel(), info3.statusLabel());
		assertEquals("VACATION", info3.absenceTypeCode());
		assertEquals("Vacation", info3.absenceTypeName());

		// Test Privacy
		Certificate testCert = runtimeMock.login("supervisor", "admin");
		try (StrolchTransaction tx = runtimeMock.openUserTx(testCert, true)) {
			assertFalse(tx.getPrivilegeContext().hasPrivilege(new SimpleRestrictable(PRIVILEGE_GET_ABSENCE_REASON, "VACATION")));
		}
		
		result = serviceHandler.doService(testCert, new PresenceService(), arg);
		assertTrue(result.isOk());
		PresenceService.PresenceInfo info3Privacy = result.presenceInfos
				.stream()
				.filter(i -> i.employeeId().equals("p-emp3"))
				.findFirst()
				.orElseThrow();
		assertEquals("ABSENT", info3Privacy.absenceTypeCode());
		assertEquals("Abwesend", info3Privacy.absenceTypeName());
	}
}
