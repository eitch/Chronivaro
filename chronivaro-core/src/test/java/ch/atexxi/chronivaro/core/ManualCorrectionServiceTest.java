package ch.atexxi.chronivaro.core;

import ch.atexxi.chronivaro.core.service.AddVacationCorrectionService;
import ch.atexxi.chronivaro.core.service.CorrectWorkEntryService;
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

public class ManualCorrectionServiceTest {

	private static RuntimeMock runtimeMock;
	private static Certificate certificate;

	@BeforeClass
	public static void beforeClass() {
		runtimeMock = new RuntimeMock().mockRuntime("target/" + ManualCorrectionServiceTest.class.getSimpleName(),
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
	public void shouldAddVacationCorrection() {
		String employeeId = "emp_vac";
		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, false)) {
			Resource employee = new Resource(employeeId, "Vacation Employee", TYPE_EMPLOYEE);
			employee.addParameterBag(new ParameterBag(BAG_PARAMETERS, "Parameters", "Parameters"));
			employee.addParameterBag(new ParameterBag(BAG_RELATIONS, "Relations", "Relations"));
			employee.setBoolean(PARAM_ACTIVE, true);
			tx.add(employee);
			tx.commitOnClose();
		}

		ServiceHandler serviceHandler = runtimeMock.getServiceHandler();
		AddVacationCorrectionService.AddVacationCorrectionArgument arg = new AddVacationCorrectionService.AddVacationCorrectionArgument();
		arg.employeeId = employeeId;
		arg.value = 480;
		arg.comment = "Manual addition";

		ServiceResult result = serviceHandler.doService(certificate, new AddVacationCorrectionService(), arg);
		assertTrue(result.getMessage(), result.isOk());

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			List<Resource> entries = tx.streamResources(TYPE_VACATION_ACCOUNT_ENTRY)
					.filter(e -> e.getString(BAG_RELATIONS, TYPE_EMPLOYEE).equals(employeeId))
					.toList();
			assertEquals(1, entries.size());
			assertEquals(480, entries.getFirst().getInteger(PARAM_VALUE));
			assertEquals(VACATION_CORRECTION, entries.getFirst().getString(PARAM_VACATION_TYPE));

			// Check Audit
			List<Resource> audits = tx.streamResources(TYPE_AUDIT_EVENT)
					.filter(e -> e.getString(PARAM_ELEMENT_TYPE).equals(TYPE_VACATION_ACCOUNT_ENTRY))
					.toList();
			assertEquals(1, audits.size());
		}
	}

	@Test
	public void shouldCorrectWorkEntry() {
		String employeeId = "emp_work";
		String workEntryId = "work1";
		ZonedDateTime start = ZonedDateTime.parse("2026-08-08T08:00:00Z");
		ZonedDateTime end = ZonedDateTime.parse("2026-08-08T12:00:00Z");

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, false)) {
			Resource employee = new Resource(employeeId, "Work Employee", TYPE_EMPLOYEE);
			employee.addParameterBag(new ParameterBag(BAG_PARAMETERS, "Parameters", "Parameters"));
			employee.addParameterBag(new ParameterBag(BAG_RELATIONS, "Relations", "Relations"));
			employee.setBoolean(PARAM_ACTIVE, true);
			tx.add(employee);

			Resource workEntry = new Resource(workEntryId, "Work Entry", TYPE_WORK_ENTRY);
			workEntry.addParameterBag(new ParameterBag(BAG_PARAMETERS, "Parameters", "Parameters"));
			workEntry.addParameterBag(new ParameterBag(BAG_RELATIONS, "Relations", "Relations"));
			workEntry.setString(BAG_RELATIONS, TYPE_EMPLOYEE, employeeId);
			workEntry.setDate(PARAM_START, start);
			workEntry.setDate(PARAM_END, end);
			tx.add(workEntry);

			tx.commitOnClose();
		}

		ServiceHandler serviceHandler = runtimeMock.getServiceHandler();
		CorrectWorkEntryService.CorrectWorkEntryArgument arg = new CorrectWorkEntryService.CorrectWorkEntryArgument();
		arg.workEntryId = workEntryId;
		arg.start = start.plusHours(1);
		arg.end = end.plusHours(1);
		arg.comment = "Manual correction";

		ServiceResult result = serviceHandler.doService(certificate, new CorrectWorkEntryService(), arg);
		assertTrue(result.getMessage(), result.isOk());

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			Resource workEntry = tx.getResourceBy(TYPE_WORK_ENTRY, workEntryId, true);
			assertEquals(start.plusHours(1), workEntry.getDate(PARAM_START));
			assertEquals(end.plusHours(1), workEntry.getDate(PARAM_END));
			assertEquals(SOURCE_MANUAL, workEntry.getString(PARAM_SOURCE));

			// Check Audit (should have 2: one for start, one for end)
			List<Resource> audits = tx.streamResources(TYPE_AUDIT_EVENT)
					.filter(e -> e.getString(PARAM_ELEMENT_TYPE).equals(TYPE_WORK_ENTRY) && e.getString(PARAM_ELEMENT_ID).equals(workEntryId))
					.toList();
			assertEquals(2, audits.size());
		}
	}
}
