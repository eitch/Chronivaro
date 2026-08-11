package ch.atexxi.chronivaro.core;

import ch.atexxi.chronivaro.core.service.AddWorkEntryService;
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

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;
import static org.junit.Assert.assertEquals;
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
			Resource employee = tx.getResourceTemplate(TYPE_EMPLOYEE, true);
			employee.setId(employeeId);
			employee.setName("Add Work Emp");
			employee.setBoolean(PARAM_ACTIVE, true);
			employee.setDate(PARAM_JOIN_DATE, ZonedDateTime.parse("2026-01-01T00:00:00Z"));
			tx.add(employee);
			tx.commitOnClose();
		}

		ServiceHandler serviceHandler = runtimeMock.getServiceHandler();

		AddWorkEntryService.AddWorkEntryArgument arg = new AddWorkEntryService.AddWorkEntryArgument();
		arg.employeeId = employeeId;
		arg.start = ZonedDateTime.parse("2026-05-04T08:00:00+02:00[Europe/Zurich]");
		arg.end = ZonedDateTime.parse("2026-05-04T12:00:00+02:00[Europe/Zurich]");
		arg.comment = "Test work entry";

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
		}
	}
}
