package ch.atexxi.chronivaro.core.model;

import ch.atexxi.chronivaro.core.service.MonthSummaryService;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
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
		String empName = employee.hasParameter(PARAM_FIRSTNAME) && employee.hasParameter(PARAM_LASTNAME)
				? employee.getString(PARAM_FIRSTNAME) + " " + employee.getString(PARAM_LASTNAME)
				: employee.getName();
		period.setName("Period " + yearMonth + " (" + empName + ")");
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
		return serializeCalculationSnapshot(summary);
	}

	public static String serializeCalculationSnapshot(MonthSummary summary) {
		JsonObject json = new JsonObject();
		json.addProperty("employeeId", summary.employeeId());
		json.addProperty("yearMonth", summary.yearMonth().toString());
		json.addProperty("totalTargetMinutes", summary.totalTargetMinutes());
		json.addProperty("totalActualMinutes", summary.totalActualMinutes());
		json.addProperty("paidAbsenceMinutes", summary.paidAbsenceMinutes());
		json.addProperty("unpaidAbsenceMinutes", summary.unpaidAbsenceMinutes());
		json.addProperty("vacationMinutes", summary.vacationMinutes());
		json.addProperty("totalHolidayMinutes", summary.totalHolidayMinutes());
		json.addProperty("totalAbsenceMinutes", summary.totalAbsenceMinutes());
		json.addProperty("periodBalanceMinutes", summary.getPeriodBalance());
		json.addProperty("initialBalanceMinutes", summary.initialBalanceMinutes());
		json.addProperty("manualCorrectionsMinutes", summary.manualCorrectionsMinutes());
		json.addProperty("finalBalanceMinutes", summary.getEndBalance());
		json.addProperty("endBalanceMinutes", summary.getEndBalance());
		json.addProperty("calculatedAt", ZonedDateTime.now().toString());

		if (summary.daySummaries() != null) {
			JsonArray dayArray = new JsonArray();
			for (DaySummary ds : summary.daySummaries()) {
				JsonObject dayJson = new JsonObject();
				dayJson.addProperty("date", ds.date().toString());
				dayJson.addProperty("state", ds.state() != null ? ds.state().name() : DayState.NOT_WORKING.name());
				dayJson.addProperty("stateLabel", ds.stateLabel());
				dayJson.addProperty("targetMinutes", ds.targetMinutes());
				dayJson.addProperty("actualMinutes", ds.actualMinutes());
				dayJson.addProperty("holidayMinutes", ds.holidayMinutes());
				dayJson.addProperty("absenceMinutes", ds.absenceMinutes());
				dayJson.addProperty("isOff", ds.isOff());
				if (ds.workingLocation() != null) {
					dayJson.addProperty("workingLocation", ds.workingLocation().name());
				}
				if (ds.workEntries() != null && !ds.workEntries().isEmpty()) {
					JsonArray weArray = new JsonArray();
					for (WorkEntryRange we : ds.workEntries()) {
						JsonObject weObj = new JsonObject();
						weObj.addProperty("id", we.id());
						weObj.addProperty("start", we.start());
						weObj.addProperty("end", we.end());
						weObj.addProperty("durationMinutes", we.durationMinutes());
						weArray.add(weObj);
					}
					dayJson.add("workEntries", weArray);
				}
				if (ds.breaks() != null && !ds.breaks().isEmpty()) {
					JsonArray bArray = new JsonArray();
					for (BreakRange b : ds.breaks()) {
						JsonObject bObj = new JsonObject();
						bObj.addProperty("start", b.start());
						bObj.addProperty("end", b.end());
						bObj.addProperty("durationMinutes", b.durationMinutes());
						bArray.add(bObj);
					}
					dayJson.add("breaks", bArray);
				}
				dayArray.add(dayJson);
			}
			json.add("daySummaries", dayArray);
		}

		return json.toString();
	}

	public static MonthSummary parseCalculationSnapshot(String snapshotJson) {
		JsonObject json = JsonParser.parseString(snapshotJson).getAsJsonObject();
		String employeeId = json.has("employeeId") ? json.get("employeeId").getAsString() : "";
		YearMonth yearMonth = json.has("yearMonth") ? YearMonth.parse(json.get("yearMonth").getAsString()) : YearMonth.now();
		int totalTarget = json.has("totalTargetMinutes") ? json.get("totalTargetMinutes").getAsInt() : 0;
		int totalActual = json.has("totalActualMinutes") ? json.get("totalActualMinutes").getAsInt() : 0;
		int totalHoliday = json.has("totalHolidayMinutes") ? json.get("totalHolidayMinutes").getAsInt() : 0;
		int totalAbsence = json.has("totalAbsenceMinutes") ? json.get("totalAbsenceMinutes").getAsInt() : 0;
		int paidAbsence = json.has("paidAbsenceMinutes") ? json.get("paidAbsenceMinutes").getAsInt() : totalAbsence;
		int unpaidAbsence = json.has("unpaidAbsenceMinutes") ? json.get("unpaidAbsenceMinutes").getAsInt() : 0;
		int vacationMinutes = json.has("vacationMinutes") ? json.get("vacationMinutes").getAsInt() : 0;
		int initialBalance = json.has("initialBalanceMinutes") ? json.get("initialBalanceMinutes").getAsInt() : 0;
		int manualCorrections = json.has("manualCorrectionsMinutes") ? json.get("manualCorrectionsMinutes").getAsInt() : 0;

		List<DaySummary> daySummaries = new ArrayList<>();
		if (json.has("daySummaries") && json.get("daySummaries").isJsonArray()) {
			JsonArray dayArray = json.getAsJsonArray("daySummaries");
			for (JsonElement elem : dayArray) {
				if (elem.isJsonObject()) {
					JsonObject d = elem.getAsJsonObject();
					LocalDate date = LocalDate.parse(d.get("date").getAsString());
					DayState state = d.has("state") ? DayState.valueOf(d.get("state").getAsString()) : DayState.NOT_WORKING;
					String stateLabel = d.has("stateLabel") ? d.get("stateLabel").getAsString() : state.getLabel();
					int target = d.has("targetMinutes") ? d.get("targetMinutes").getAsInt() : 0;
					int actual = d.has("actualMinutes") ? d.get("actualMinutes").getAsInt() : 0;
					int holiday = d.has("holidayMinutes") ? d.get("holidayMinutes").getAsInt() : 0;
					int absence = d.has("absenceMinutes") ? d.get("absenceMinutes").getAsInt() : 0;
					boolean isOff = d.has("isOff") && d.get("isOff").getAsBoolean();
					WorkingLocation loc = d.has("workingLocation") && !d.get("workingLocation").isJsonNull()
							? WorkingLocation.valueOf(d.get("workingLocation").getAsString()) : null;
					List<WorkEntryRange> ranges = new ArrayList<>();
					if (d.has("workEntries") && d.get("workEntries").isJsonArray()) {
						for (JsonElement weElem : d.getAsJsonArray("workEntries")) {
							if (weElem.isJsonObject()) {
								JsonObject weObj = weElem.getAsJsonObject();
								ranges.add(new WorkEntryRange(
										weObj.has("id") ? weObj.get("id").getAsString() : "",
										weObj.has("start") ? weObj.get("start").getAsString() : "",
										weObj.has("end") ? weObj.get("end").getAsString() : "",
										weObj.has("durationMinutes") ? weObj.get("durationMinutes").getAsInt() : 0
								));
							}
						}
					}
					List<BreakRange> breaks = new ArrayList<>();
					if (d.has("breaks") && d.get("breaks").isJsonArray()) {
						for (JsonElement bElem : d.getAsJsonArray("breaks")) {
							if (bElem.isJsonObject()) {
								JsonObject bObj = bElem.getAsJsonObject();
								breaks.add(new BreakRange(
										bObj.has("start") ? bObj.get("start").getAsString() : "",
										bObj.has("end") ? bObj.get("end").getAsString() : "",
										bObj.has("durationMinutes") ? bObj.get("durationMinutes").getAsInt() : 0
								));
							}
						}
					}
					daySummaries.add(new DaySummary(date, state, stateLabel, target, actual, holiday, absence, isOff, loc, ranges, breaks));
				}
			}
		}

		return new MonthSummary(employeeId, yearMonth, totalTarget, totalActual, paidAbsence, unpaidAbsence,
				vacationMinutes, totalHoliday, totalAbsence, initialBalance, manualCorrections, daySummaries);
	}
}
