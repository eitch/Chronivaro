package ch.eitchnet.chronivaro.core;

import ch.eitchnet.chronivaro.core.model.ChronivaroModelHelper;
import ch.eitchnet.chronivaro.core.model.DayState;
import ch.eitchnet.chronivaro.core.model.MonthSummary;
import ch.eitchnet.chronivaro.core.service.MonthSummaryService;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.privilege.model.Certificate;
import li.strolch.service.api.ServiceHandler;
import li.strolch.service.api.ServiceResult;
import li.strolch.testbase.runtime.RuntimeMock;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZonedDateTime;

import static ch.eitchnet.chronivaro.core.ChronivaroTestHelper.createEmployee;
import static ch.eitchnet.chronivaro.core.ChronivaroTestHelper.createWorkEntry;
import static ch.eitchnet.chronivaro.core.model.ChronivaroConstants.PARAM_TIMEZONE;
import static org.junit.Assert.assertEquals;

public class MonthSummaryServiceTest {

	private static RuntimeMock runtimeMock;
	private static Certificate certificate;

	@BeforeClass
	public static void beforeClass() {
		runtimeMock = new RuntimeMock().mockRuntime("target/" + MonthSummaryServiceTest.class.getSimpleName(),
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
	public void shouldCalculateMonthSummaryWithActiveEntry() {
		String employeeId = "empMonth";
		LocalDate today = LocalDate.now();
		YearMonth yearMonth = YearMonth.from(today);

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, false)) {
			Resource employee = createEmployee(tx, employeeId, "Month Doe");
			employee = tx.readLock(employee);
			employee.setString(PARAM_TIMEZONE, "Europe/Zurich");
			tx.update(employee);

			// Active Work Entry: started 15 minutes ago
			ZonedDateTime start = ZonedDateTime
					.now(ChronivaroModelHelper.getEmployeeTimezone(employee))
					.minusMinutes(15);
			createWorkEntry(tx, employee, start, ZonedDateTime.parse("1970-01-01T00:00:00+01:00"));

			tx.commitOnClose();
		}

		ServiceHandler serviceHandler = runtimeMock.getServiceHandler();
		MonthSummaryService.MonthSummaryArgument arg = new MonthSummaryService.MonthSummaryArgument();
		arg.employeeId = employeeId;
		arg.yearMonth = yearMonth;

		MonthSummaryService.MonthSummaryResult result = serviceHandler.doService(certificate, new MonthSummaryService(),
				arg);
		assertEquals(ServiceResult.success().getState(), result.getState());

		MonthSummary summary = result.monthSummary;
		assertEquals(15, summary.totalActualMinutes());
		assertEquals(DayState.WORKING, summary.daySummaries().get(today.getDayOfMonth() - 1).state());
		assertEquals(DayState.WORKING.getLabel(), summary.daySummaries().get(today.getDayOfMonth() - 1).stateLabel());
		assertEquals(15, summary.daySummaries().get(today.getDayOfMonth() - 1).actualMinutes());
	}
}
