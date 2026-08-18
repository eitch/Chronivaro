package ch.atexxi.chronivaro.core.model;

import ch.atexxi.chronivaro.core.service.MonthSummaryService;
import com.google.gson.JsonObject;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.util.Optional;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;
import static ch.atexxi.chronivaro.core.model.ChronivaroVersionHelper.initVersion;

public class PeriodHelper {

	public static String getPeriodId(String employeeId, YearMonth yearMonth) {
		return "period-" + employeeId + "-" + yearMonth;
	}

	public static Optional<Resource> findPeriod(StrolchTransaction tx, String employeeId, YearMonth yearMonth) {
		String periodId = getPeriodId(employeeId, yearMonth);
		Resource period = tx.getResourceBy(TYPE_TIME_PERIOD, periodId, false);
		if (period != null)
			return Optional.of(period);

		return tx.streamResources(TYPE_TIME_PERIOD)
				.filter(p -> employeeId.equals(p.getRelationId(PARAM_EMPLOYEE)))
				.filter(p -> yearMonth.toString().equals(p.getString(PARAM_YEAR_MONTH)))
				.findFirst();
	}

	public static Resource getPeriod(StrolchTransaction tx, String employeeId, YearMonth yearMonth, boolean assertExists) {
		Optional<Resource> period = findPeriod(tx, employeeId, yearMonth);
		if (period.isPresent())
			return period.get();
		if (assertExists)
			throw new IllegalStateException("Time period " + yearMonth + " for employee " + employeeId + " not found!");
		return null;
	}

	public static Resource getOrCreatePeriod(StrolchTransaction tx, String employeeId, YearMonth yearMonth) {
		Optional<Resource> existing = findPeriod(tx, employeeId, yearMonth);
		if (existing.isPresent())
			return existing.get();

		Resource employee = ChronivaroModelHelper.getEmployee(tx, employeeId);
		Resource period = tx.getResourceTemplate(TYPE_TIME_PERIOD, true);
		period.setId(getPeriodId(employeeId, yearMonth));
		period.setName("Period " + yearMonth + " (" + employee.getString(PARAM_FIRSTNAME) + " " +
				employee.getString(PARAM_LASTNAME) + ")");
		period.setString(PARAM_YEAR_MONTH, yearMonth.toString());
		period.setString(PARAM_STATE, STATE_OPEN);
		period.setRelation(PARAM_EMPLOYEE, employee);
		initVersion(period, tx);
		tx.add(period);
		return period;
	}

	public static boolean isPeriodClosed(StrolchTransaction tx, String employeeId, LocalDate date) {
		YearMonth ym = YearMonth.from(date);
		Optional<Resource> period = findPeriod(tx, employeeId, ym);
		if (period.isEmpty())
			return false;
		String state = period.get().getString(PARAM_STATE);
		return STATE_SUBMITTED.equals(state) || STATE_APPROVED.equals(state) || STATE_LOCKED.equals(state);
	}

	public static void assertPeriodOpen(StrolchTransaction tx, String employeeId, LocalDate date) {
		YearMonth ym = YearMonth.from(date);
		Optional<Resource> period = findPeriod(tx, employeeId, ym);
		if (period.isPresent()) {
			String state = period.get().getString(PARAM_STATE);
			if (STATE_SUBMITTED.equals(state) || STATE_APPROVED.equals(state) || STATE_LOCKED.equals(state)) {
				throw new IllegalStateException("Cannot modify records for period " + ym + " in state " + state +
						". Reopening is required.");
			}
		}
	}

	public static String createCalculationSnapshot(StrolchTransaction tx, String employeeId, YearMonth yearMonth) {
		MonthSummary summary = MonthSummaryService.calculateMonthSummary(tx, employeeId, yearMonth);
		JsonObject json = new JsonObject();
		json.addProperty("employeeId", employeeId);
		json.addProperty("yearMonth", yearMonth.toString());
		json.addProperty("totalTargetMinutes", summary.totalTargetMinutes());
		json.addProperty("totalActualMinutes", summary.totalActualMinutes());
		json.addProperty("totalHolidayMinutes", summary.totalHolidayMinutes());
		json.addProperty("totalAbsenceMinutes", summary.totalAbsenceMinutes());
		json.addProperty("periodBalanceMinutes", summary.getPeriodBalance());
		json.addProperty("initialBalanceMinutes", summary.initialBalanceMinutes());
		json.addProperty("finalBalanceMinutes", summary.initialBalanceMinutes() + summary.getPeriodBalance());
		json.addProperty("calculatedAt", ZonedDateTime.now().toString());
		return json.toString();
	}
}
