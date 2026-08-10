package ch.atexxi.chronivaro.core;

import ch.atexxi.chronivaro.core.service.CreateAbsenceTypeService;
import ch.atexxi.chronivaro.core.service.RemoveAbsenceTypeService;
import ch.atexxi.chronivaro.core.service.UpdateAbsenceTypeService;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.privilege.model.Certificate;
import li.strolch.service.StringArgument;
import li.strolch.service.api.ServiceHandler;
import li.strolch.service.api.ServiceResult;
import li.strolch.testbase.runtime.RuntimeMock;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;
import static org.junit.Assert.*;

public class AbsenceTypeServiceTest {

	private static RuntimeMock runtimeMock;
	private static Certificate certificate;

	@BeforeClass
	public static void beforeClass() {
		runtimeMock = new RuntimeMock().mockRuntime("target/" + AbsenceTypeServiceTest.class.getSimpleName(),
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
	public void shouldCreateUpdateAndRemoveAbsenceType() {
		ServiceHandler serviceHandler = runtimeMock.getServiceHandler();

		// Create
		CreateAbsenceTypeService.AbsenceTypeArgument createArg = new CreateAbsenceTypeService.AbsenceTypeArgument();
		createArg.code = "VAC";
		createArg.name = "Vacation";
		createArg.countAsTargetTime = true;
		createArg.reduceVacationCredit = true;
		createArg.paid = true;
		createArg.approvalRequired = true;
		createArg.durationTypes = List.of(DURATION_FULL_DAY, DURATION_HALF_DAY);
		createArg.active = true;

		ServiceResult createResult = serviceHandler.doService(certificate, new CreateAbsenceTypeService(), createArg);
		assertTrue(createResult.getMessage(), createResult.isOk());

		String absenceTypeId;
		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			Resource type = tx
					.streamResources(TYPE_ABSENCE_TYPE)
					.filter(r -> r.getName().equals("Vacation"))
					.findFirst()
					.orElseThrow();
			absenceTypeId = type.getId();
			assertEquals("VAC", type.getString(PARAM_CODE));
			assertEquals("Vacation", type.getString(PARAM_NAME));
			assertTrue(type.getBoolean(PARAM_ACTIVE));
			assertEquals(2, type.getStringList(PARAM_DURATION_TYPES).size());
		}

		// Update
		CreateAbsenceTypeService.UpdateAbsenceTypeArgument updateArg
				= new CreateAbsenceTypeService.UpdateAbsenceTypeArgument();
		updateArg.id = absenceTypeId;
		updateArg.code = "VAC";
		updateArg.name = "Updated Vacation";
		updateArg.countAsTargetTime = true;
		updateArg.reduceVacationCredit = true;
		updateArg.paid = true;
		updateArg.approvalRequired = true;
		updateArg.durationTypes = List.of(DURATION_FULL_DAY, DURATION_HALF_DAY);
		updateArg.active = true;
		ServiceResult updateResult = serviceHandler.doService(certificate, new UpdateAbsenceTypeService(), updateArg);
		assertTrue(updateResult.getMessage(), updateResult.isOk());

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			Resource type = tx.getResourceBy(TYPE_ABSENCE_TYPE, absenceTypeId, true);
			assertEquals("Updated Vacation", type.getString(PARAM_NAME));
		}

		// Remove
		ServiceResult removeResult = serviceHandler.doService(certificate, new RemoveAbsenceTypeService(),
				new StringArgument(absenceTypeId));
		assertTrue(removeResult.getMessage(), removeResult.isOk());

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			assertNull(tx.getResourceBy(TYPE_ABSENCE_TYPE, absenceTypeId));
		}
	}
}
