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
	public void shouldLoadPreconfiguredDefaultAbsenceTypesFromModel() {
		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			List<Resource> types = tx.streamResources(TYPE_ABSENCE_TYPE).toList();
			assertTrue("Expected at least 10 default absence types", types.size() >= 10);

			// VACATION
			Resource vacation = tx.getResourceBy(TYPE_ABSENCE_TYPE, "VACATION", true);
			assertEquals("VACATION", vacation.getString(PARAM_CODE));
			assertEquals("Ferien", vacation.getString(PARAM_NAME));
			assertTrue(vacation.getBoolean(PARAM_COUNT_AS_TARGET_TIME));
			assertTrue(vacation.getBoolean(PARAM_REDUCE_VACATION_CREDIT));
			assertTrue(vacation.getBoolean(PARAM_PAID));
			assertTrue(vacation.getBoolean(PARAM_APPROVAL_REQUIRED));
			assertFalse(vacation.getBoolean(PARAM_COMMENT_REQUIRED));
			assertFalse(vacation.getBoolean(PARAM_VISIBLE_ON_PUBLIC_STATUS));
			assertEquals(List.of(DURATION_HALF_DAY, DURATION_FULL_DAY), vacation.getStringList(PARAM_DURATION_TYPES));
			assertTrue(vacation.getBoolean(PARAM_ACTIVE));

			// ILLNESS
			Resource illness = tx.getResourceBy(TYPE_ABSENCE_TYPE, "ILLNESS", true);
			assertEquals("ILLNESS", illness.getString(PARAM_CODE));
			assertEquals("Krankheit", illness.getString(PARAM_NAME));
			assertTrue(illness.getBoolean(PARAM_COUNT_AS_TARGET_TIME));
			assertFalse(illness.getBoolean(PARAM_REDUCE_VACATION_CREDIT));
			assertTrue(illness.getBoolean(PARAM_PAID));
			assertTrue(illness.getBoolean(PARAM_APPROVAL_REQUIRED));
			assertFalse(illness.getBoolean(PARAM_COMMENT_REQUIRED));
			assertFalse(illness.getBoolean(PARAM_VISIBLE_ON_PUBLIC_STATUS));
			assertEquals(List.of(DURATION_HOURS, DURATION_HALF_DAY, DURATION_FULL_DAY), illness.getStringList(PARAM_DURATION_TYPES));
			assertTrue(illness.getBoolean(PARAM_ACTIVE));

			// ACCIDENT
			Resource accident = tx.getResourceBy(TYPE_ABSENCE_TYPE, "ACCIDENT", true);
			assertEquals("ACCIDENT", accident.getString(PARAM_CODE));
			assertEquals("Unfall", accident.getString(PARAM_NAME));
			assertTrue(accident.getBoolean(PARAM_COUNT_AS_TARGET_TIME));
			assertFalse(accident.getBoolean(PARAM_REDUCE_VACATION_CREDIT));
			assertTrue(accident.getBoolean(PARAM_PAID));
			assertTrue(accident.getBoolean(PARAM_APPROVAL_REQUIRED));
			assertFalse(accident.getBoolean(PARAM_COMMENT_REQUIRED));
			assertFalse(accident.getBoolean(PARAM_VISIBLE_ON_PUBLIC_STATUS));
			assertEquals(List.of(DURATION_HOURS, DURATION_HALF_DAY, DURATION_FULL_DAY), accident.getStringList(PARAM_DURATION_TYPES));
			assertTrue(accident.getBoolean(PARAM_ACTIVE));

			// MILITARY_CIVIL_DEFENSE
			Resource military = tx.getResourceBy(TYPE_ABSENCE_TYPE, "MILITARY_CIVIL_DEFENSE", true);
			assertEquals("MILITARY_CIVIL_DEFENSE", military.getString(PARAM_CODE));
			assertEquals("Militär / Zivilschutz", military.getString(PARAM_NAME));
			assertTrue(military.getBoolean(PARAM_COUNT_AS_TARGET_TIME));
			assertFalse(military.getBoolean(PARAM_REDUCE_VACATION_CREDIT));
			assertTrue(military.getBoolean(PARAM_PAID));
			assertTrue(military.getBoolean(PARAM_APPROVAL_REQUIRED));
			assertFalse(military.getBoolean(PARAM_COMMENT_REQUIRED));
			assertFalse(military.getBoolean(PARAM_VISIBLE_ON_PUBLIC_STATUS));
			assertEquals(List.of(DURATION_HALF_DAY, DURATION_FULL_DAY), military.getStringList(PARAM_DURATION_TYPES));
			assertTrue(military.getBoolean(PARAM_ACTIVE));

			// DOCTOR_APPOINTMENT
			Resource doctor = tx.getResourceBy(TYPE_ABSENCE_TYPE, "DOCTOR_APPOINTMENT", true);
			assertEquals("DOCTOR_APPOINTMENT", doctor.getString(PARAM_CODE));
			assertEquals("Arzttermin", doctor.getString(PARAM_NAME));
			assertTrue(doctor.getBoolean(PARAM_COUNT_AS_TARGET_TIME));
			assertFalse(doctor.getBoolean(PARAM_REDUCE_VACATION_CREDIT));
			assertTrue(doctor.getBoolean(PARAM_PAID));
			assertFalse(doctor.getBoolean(PARAM_APPROVAL_REQUIRED));
			assertFalse(doctor.getBoolean(PARAM_COMMENT_REQUIRED));
			assertFalse(doctor.getBoolean(PARAM_VISIBLE_ON_PUBLIC_STATUS));
			assertEquals(List.of(DURATION_HOURS), doctor.getStringList(PARAM_DURATION_TYPES));
			assertTrue(doctor.getBoolean(PARAM_ACTIVE));

			// TRAINING
			Resource training = tx.getResourceBy(TYPE_ABSENCE_TYPE, "TRAINING", true);
			assertEquals("TRAINING", training.getString(PARAM_CODE));
			assertEquals("Weiterbildung", training.getString(PARAM_NAME));
			assertTrue(training.getBoolean(PARAM_COUNT_AS_TARGET_TIME));
			assertFalse(training.getBoolean(PARAM_REDUCE_VACATION_CREDIT));
			assertTrue(training.getBoolean(PARAM_PAID));
			assertTrue(training.getBoolean(PARAM_APPROVAL_REQUIRED));
			assertTrue(training.getBoolean(PARAM_COMMENT_REQUIRED));
			assertFalse(training.getBoolean(PARAM_VISIBLE_ON_PUBLIC_STATUS));
			assertEquals(List.of(DURATION_HOURS, DURATION_HALF_DAY, DURATION_FULL_DAY), training.getStringList(PARAM_DURATION_TYPES));
			assertTrue(training.getBoolean(PARAM_ACTIVE));

			// PARENTAL_LEAVE
			Resource parental = tx.getResourceBy(TYPE_ABSENCE_TYPE, "PARENTAL_LEAVE", true);
			assertEquals("PARENTAL_LEAVE", parental.getString(PARAM_CODE));
			assertEquals("Elternurlaub", parental.getString(PARAM_NAME));
			assertTrue(parental.getBoolean(PARAM_COUNT_AS_TARGET_TIME));
			assertFalse(parental.getBoolean(PARAM_REDUCE_VACATION_CREDIT));
			assertTrue(parental.getBoolean(PARAM_PAID));
			assertTrue(parental.getBoolean(PARAM_APPROVAL_REQUIRED));
			assertFalse(parental.getBoolean(PARAM_COMMENT_REQUIRED));
			assertFalse(parental.getBoolean(PARAM_VISIBLE_ON_PUBLIC_STATUS));
			assertEquals(List.of(DURATION_HALF_DAY, DURATION_FULL_DAY), parental.getStringList(PARAM_DURATION_TYPES));
			assertTrue(parental.getBoolean(PARAM_ACTIVE));

			// UNPAID_LEAVE
			Resource unpaid = tx.getResourceBy(TYPE_ABSENCE_TYPE, "UNPAID_LEAVE", true);
			assertEquals("UNPAID_LEAVE", unpaid.getString(PARAM_CODE));
			assertEquals("Unbezahlter Urlaub", unpaid.getString(PARAM_NAME));
			assertFalse(unpaid.getBoolean(PARAM_COUNT_AS_TARGET_TIME));
			assertFalse(unpaid.getBoolean(PARAM_REDUCE_VACATION_CREDIT));
			assertFalse(unpaid.getBoolean(PARAM_PAID));
			assertTrue(unpaid.getBoolean(PARAM_APPROVAL_REQUIRED));
			assertTrue(unpaid.getBoolean(PARAM_COMMENT_REQUIRED));
			assertFalse(unpaid.getBoolean(PARAM_VISIBLE_ON_PUBLIC_STATUS));
			assertEquals(List.of(DURATION_HALF_DAY, DURATION_FULL_DAY), unpaid.getStringList(PARAM_DURATION_TYPES));
			assertTrue(unpaid.getBoolean(PARAM_ACTIVE));

			// OVERTIME_COMPENSATION
			Resource overtime = tx.getResourceBy(TYPE_ABSENCE_TYPE, "OVERTIME_COMPENSATION", true);
			assertEquals("OVERTIME_COMPENSATION", overtime.getString(PARAM_CODE));
			assertEquals("Überstundenkompensation", overtime.getString(PARAM_NAME));
			assertFalse(overtime.getBoolean(PARAM_COUNT_AS_TARGET_TIME));
			assertFalse(overtime.getBoolean(PARAM_REDUCE_VACATION_CREDIT));
			assertTrue(overtime.getBoolean(PARAM_PAID));
			assertTrue(overtime.getBoolean(PARAM_APPROVAL_REQUIRED));
			assertFalse(overtime.getBoolean(PARAM_COMMENT_REQUIRED));
			assertFalse(overtime.getBoolean(PARAM_VISIBLE_ON_PUBLIC_STATUS));
			assertEquals(List.of(DURATION_HOURS, DURATION_HALF_DAY, DURATION_FULL_DAY), overtime.getStringList(PARAM_DURATION_TYPES));
			assertTrue(overtime.getBoolean(PARAM_ACTIVE));

			// OTHER
			Resource other = tx.getResourceBy(TYPE_ABSENCE_TYPE, "OTHER", true);
			assertEquals("OTHER", other.getString(PARAM_CODE));
			assertEquals("Sonstige Abwesenheit", other.getString(PARAM_NAME));
			assertFalse(other.getBoolean(PARAM_COUNT_AS_TARGET_TIME));
			assertFalse(other.getBoolean(PARAM_REDUCE_VACATION_CREDIT));
			assertTrue(other.getBoolean(PARAM_PAID));
			assertTrue(other.getBoolean(PARAM_APPROVAL_REQUIRED));
			assertTrue(other.getBoolean(PARAM_COMMENT_REQUIRED));
			assertFalse(other.getBoolean(PARAM_VISIBLE_ON_PUBLIC_STATUS));
			assertEquals(List.of(DURATION_HOURS, DURATION_HALF_DAY, DURATION_FULL_DAY), other.getStringList(PARAM_DURATION_TYPES));
			assertTrue(other.getBoolean(PARAM_ACTIVE));
		}
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
		createArg.commentRequired = true;
		createArg.visibleOnPublicStatus = true;
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
			assertTrue(type.getBoolean(PARAM_COMMENT_REQUIRED));
			assertTrue(type.getBoolean(PARAM_VISIBLE_ON_PUBLIC_STATUS));
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
		updateArg.commentRequired = false;
		updateArg.visibleOnPublicStatus = false;
		updateArg.durationTypes = List.of(DURATION_FULL_DAY, DURATION_HALF_DAY);
		updateArg.active = true;
		ServiceResult updateResult = serviceHandler.doService(certificate, new UpdateAbsenceTypeService(), updateArg);
		assertTrue(updateResult.getMessage(), updateResult.isOk());

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			Resource type = tx.getResourceBy(TYPE_ABSENCE_TYPE, absenceTypeId, true);
			assertEquals("Updated Vacation", type.getString(PARAM_NAME));
			assertFalse(type.getBoolean(PARAM_COMMENT_REQUIRED));
			assertFalse(type.getBoolean(PARAM_VISIBLE_ON_PUBLIC_STATUS));
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
