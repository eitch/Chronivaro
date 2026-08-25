package ch.eitchnet.chronivaro.core;

import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.privilege.model.Certificate;
import li.strolch.testbase.runtime.RuntimeMock;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.time.ZonedDateTime;

import static ch.eitchnet.chronivaro.core.ChronivaroTestHelper.createEmployee;
import static ch.eitchnet.chronivaro.core.ChronivaroTestHelper.createWorkEntry;
import static ch.eitchnet.chronivaro.core.model.ChronivaroConstants.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class WorkDayModelTest {

	private static RuntimeMock runtimeMock;
	private static Certificate certificate;

	@BeforeClass
	public static void beforeClass() {
		runtimeMock = new RuntimeMock().mockRuntime("target/" + WorkDayModelTest.class.getSimpleName(),
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
	public void shouldCreateWorkDay() {
		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, false)) {
			Resource workDay = tx.getResourceTemplate(TYPE_WORK_DAY, true);
			workDay.setId("workDay01");
			workDay.setName("Work Day 2026-08-14");
			workDay.setDate(PARAM_DATE, ZonedDateTime.parse("2026-08-14T00:00:00Z"));
			workDay.setRelationId(PARAM_EMPLOYEE, "emp01");
			workDay.setRelationId(PARAM_SCHEDULE, "sched01");
			tx.add(workDay);
			tx.commitOnClose();
		}

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			Resource workDay = tx.getResourceBy(TYPE_WORK_DAY, "workDay01", true);
			assertNotNull(workDay);
			assertEquals("workDay01", workDay.getId());
			assertEquals("emp01", workDay.getRelationId(PARAM_EMPLOYEE));
		}
	}

	@Test
	public void shouldReferenceWorkDayFromEmployee() {
		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, false)) {
			Resource employee = tx.getResourceTemplate(TYPE_EMPLOYEE, true);
			employee.setId("emp-ref-test");
			employee.setRelationId(PARAM_CURRENT_WORK_DAY, "workDay01");
			tx.add(employee);
			tx.commitOnClose();
		}

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			Resource employee = tx.getResourceBy(TYPE_EMPLOYEE, "emp-ref-test", true);
			assertEquals("workDay01", employee.getRelationId(PARAM_CURRENT_WORK_DAY));
		}
	}

	@Test
	public void shouldReferenceWorkDayFromWorkEntry() {
		String workEntryId;
		String workDayId;
		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, false)) {
			Resource employee = createEmployee(tx, "emp-we-test", "WE Test Employee");

			Resource workEntry = createWorkEntry(tx, employee, ZonedDateTime.parse("2026-08-14T08:00:00Z"),
					ZonedDateTime.parse("2026-08-14T12:00:00Z"));
			workEntryId = workEntry.getId();
			workDayId = workEntry.getRelationId(PARAM_WORK_DAY);
			tx.commitOnClose();
		}

		try (StrolchTransaction tx2 = runtimeMock.openUserTx(certificate, true)) {
			Resource we = tx2.getResourceBy(TYPE_WORK_ENTRY, workEntryId, true);
			assertEquals(workDayId, we.getRelationId(PARAM_WORK_DAY));
		}
	}
}
