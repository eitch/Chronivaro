package ch.atexxi.chronivaro.core;

import ch.atexxi.chronivaro.core.service.CreateScheduleTemplateService;
import ch.atexxi.chronivaro.core.service.RemoveScheduleTemplateService;
import ch.atexxi.chronivaro.core.service.UpdateScheduleTemplateService;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.privilege.model.Certificate;
import li.strolch.service.StringArgument;
import li.strolch.service.StringResult;
import li.strolch.service.api.ServiceHandler;
import li.strolch.service.api.ServiceResult;
import li.strolch.testbase.runtime.RuntimeMock;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.TYPE_EMPLOYMENT_SCHEDULE_TEMPLATE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ScheduleTemplateServiceTest {

	private static final String TARGET_PATH = "target/ScheduleTemplateServiceTest";
	private static final String SOURCE_PATH = "src/test/resources";
	private static RuntimeMock runtimeMock;

	@BeforeClass
	public static void beforeClass() {
		runtimeMock = new RuntimeMock().mockRuntime(TARGET_PATH, SOURCE_PATH);
		runtimeMock.startContainer();
	}

	@AfterClass
	public static void afterClass() {
		if (runtimeMock != null)
			runtimeMock.destroyRuntime();
	}

	@Test
	public void shouldManageScheduleTemplates() {
		Certificate cert = runtimeMock.loginAdmin();
		ServiceHandler serviceHandler = runtimeMock.getServiceHandler();

		// 1. Create
		CreateScheduleTemplateService.CreateScheduleTemplateArgument createArg
				= new CreateScheduleTemplateService.CreateScheduleTemplateArgument();
		createArg.name = "Test Template";
		createArg.monday = 480;
		createArg.tuesday = 480;
		createArg.wednesday = 480;
		createArg.thursday = 480;
		createArg.friday = 480;
		createArg.saturday = 0;
		createArg.sunday = 0;

		StringResult stringResult = serviceHandler.doService(cert, new CreateScheduleTemplateService(), createArg);
		assertTrue(stringResult.isOk());

		// 2. Verify
		String scheduleId = stringResult.getValue();
		try (StrolchTransaction tx = runtimeMock.openUserTx(cert, true)) {
			Resource template = tx.getResourceBy(TYPE_EMPLOYMENT_SCHEDULE_TEMPLATE, scheduleId, true);
			assertEquals("Test Template", template.getName());
			assertEquals(480, (int) template.getInteger("dailyTargetMinutesMonday"));
		}

		// 3. Update
		UpdateScheduleTemplateService.UpdateScheduleTemplateArgument updateArg
				= new UpdateScheduleTemplateService.UpdateScheduleTemplateArgument();
		updateArg.id = scheduleId;
		updateArg.name = "Updated Template";
		updateArg.monday = 420;
		updateArg.tuesday = 420;
		updateArg.wednesday = 420;
		updateArg.thursday = 420;
		updateArg.friday = 420;
		updateArg.saturday = 60;
		updateArg.sunday = 0;

		ServiceResult result = serviceHandler.doService(cert, new UpdateScheduleTemplateService(), updateArg);
		assertTrue(result.isOk());

		// 4. Verify Update
		try (StrolchTransaction tx = runtimeMock.openUserTx(cert, true)) {
			Resource template = tx.getResourceBy(TYPE_EMPLOYMENT_SCHEDULE_TEMPLATE, scheduleId, true);
			assertEquals("Updated Template", template.getName());
			assertEquals(420, (int) template.getInteger("dailyTargetMinutesMonday"));
			assertEquals(60, (int) template.getInteger("dailyTargetMinutesSaturday"));
		}

		// 5. Remove
		result = serviceHandler.doService(cert, new RemoveScheduleTemplateService(), new StringArgument(scheduleId));
		assertTrue(result.isOk());

		// 6. Verify Removal
		try (StrolchTransaction tx = runtimeMock.openUserTx(cert, true)) {
			List<Resource> templates = tx
					.streamResources(TYPE_EMPLOYMENT_SCHEDULE_TEMPLATE)
					.filter(t -> t.getId().equals(scheduleId))
					.toList();
			assertTrue(templates.isEmpty());
		}
	}
}
