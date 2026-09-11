package ch.eitchnet.chronivaro.core.service;

import ch.eitchnet.chronivaro.core.model.ChronivaroAuditHelper;
import ch.eitchnet.chronivaro.core.model.PeriodHelper;
import ch.eitchnet.chronivaro.core.model.VacationHelper;
import com.google.gson.JsonObject;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.privilege.base.PrivilegeConstants;
import li.strolch.privilege.handler.PrivilegeHandler;
import li.strolch.privilege.model.UserRep;
import li.strolch.service.StringResult;
import li.strolch.service.api.AbstractService;
import li.strolch.service.api.ServiceArgument;
import li.strolch.service.api.ServiceResultState;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static ch.eitchnet.chronivaro.core.model.ChronivaroConstants.*;
import static ch.eitchnet.chronivaro.core.model.ChronivaroVersionHelper.initVersion;
import static li.strolch.privilege.model.UserState.ENABLED;

public class CreateEmployeeService extends AbstractService<CreateEmployeeService.EmployeeArgument, StringResult> {

	@Override
	protected StringResult internalDoService(EmployeeArgument arg) throws Exception {
		String timeZone = arg.timezone == null || arg.timezone.isEmpty() ? getAgent().getTimezone() : arg.timezone;
		ZoneId zoneId = ZoneId.of(timeZone);

		String employeeId;
		try (StrolchTransaction tx = openArgOrUserTx(arg)) {

			Resource employee = tx.getResourceTemplate(TYPE_EMPLOYEE, true);
			employee.setName(arg.firstname + " " + arg.lastname);

			if (arg.personalNumber == null || arg.personalNumber.isBlank())
				arg.personalNumber = arg.username;

			employee.setRelation(PARAM_PRIMARY_TEAM, tx.getResourceBy(TYPE_TEAM, arg.teamId, true));
			employee.setString(PARAM_PERSONAL_NUMBER, arg.personalNumber);
			employee.setString(PARAM_FIRSTNAME, arg.firstname);
			employee.setString(PARAM_LASTNAME, arg.lastname);
			if (arg.birthdate != null)
				employee.setDate(PARAM_BIRTHDATE, arg.birthdate.atStartOfDay(zoneId));
			else
				employee.removeParameter(PARAM_BIRTHDATE);
			employee.setRelation(PARAM_LOCATION, tx.getResourceBy(TYPE_LOCATION, arg.locationId, true));
			employee.setString(PARAM_TIMEZONE, timeZone);
			employee.setDate(PARAM_JOIN_DATE, arg.joinDate.atStartOfDay(zoneId));
			if (arg.exitDate != null)
				employee.setDate(PARAM_EXIT_DATE, arg.exitDate.atStartOfDay(zoneId));
			employee.setBoolean(PARAM_ACTIVE, arg.active);
			if (arg.email != null && !arg.email.isBlank())
				employee.setString(PARAM_EMAIL, arg.email);
			else
				employee.removeParameter(PARAM_EMAIL);

			UserRep userRep = createOrUpdateUser(tx, arg);
			employee.setString(PARAM_USER_ID, userRep.getUserId());
			employee.setString(PARAM_USERNAME, userRep.getUsername());

			employeeId = employee.getId();
			initVersion(employee, tx);
			tx.add(employee);
			ChronivaroAuditHelper.audit(tx, TYPE_EMPLOYEE, employee.getId(), AUDIT_ACTION_CREATE,
					"Created employee " + employee.getName());

			LocalDate scheduleValidFrom = arg.scheduleValidFrom != null ? arg.scheduleValidFrom : arg.joinDate;

			if (arg.scheduleTemplateId != null && !arg.scheduleTemplateId.isBlank()) {
				Resource template = tx.getResourceBy(TYPE_EMPLOYMENT_SCHEDULE_TEMPLATE, arg.scheduleTemplateId, true);
				Resource schedule = tx.getResourceTemplate(TYPE_EMPLOYMENT_SCHEDULE, true);
				schedule.setName("Schedule for " + employee.getName());
				schedule.setRelation(PARAM_EMPLOYEE, employee);
				schedule.setDate(PARAM_VALID_FROM, scheduleValidFrom.atStartOfDay(zoneId));

				schedule.setInteger(PARAM_DAILY_TARGET_MINUTES_MONDAY, template.getInteger(PARAM_DAILY_TARGET_MINUTES_MONDAY));
				schedule.setInteger(PARAM_DAILY_TARGET_MINUTES_TUESDAY, template.getInteger(PARAM_DAILY_TARGET_MINUTES_TUESDAY));
				schedule.setInteger(PARAM_DAILY_TARGET_MINUTES_WEDNESDAY, template.getInteger(PARAM_DAILY_TARGET_MINUTES_WEDNESDAY));
				schedule.setInteger(PARAM_DAILY_TARGET_MINUTES_THURSDAY, template.getInteger(PARAM_DAILY_TARGET_MINUTES_THURSDAY));
				schedule.setInteger(PARAM_DAILY_TARGET_MINUTES_FRIDAY, template.getInteger(PARAM_DAILY_TARGET_MINUTES_FRIDAY));
				schedule.setInteger(PARAM_DAILY_TARGET_MINUTES_SATURDAY, template.getInteger(PARAM_DAILY_TARGET_MINUTES_SATURDAY));
				schedule.setInteger(PARAM_DAILY_TARGET_MINUTES_SUNDAY, template.getInteger(PARAM_DAILY_TARGET_MINUTES_SUNDAY));

				int weeklyMin = template.getInteger(PARAM_DAILY_TARGET_MINUTES_MONDAY)
						+ template.getInteger(PARAM_DAILY_TARGET_MINUTES_TUESDAY)
						+ template.getInteger(PARAM_DAILY_TARGET_MINUTES_WEDNESDAY)
						+ template.getInteger(PARAM_DAILY_TARGET_MINUTES_THURSDAY)
						+ template.getInteger(PARAM_DAILY_TARGET_MINUTES_FRIDAY)
						+ template.getInteger(PARAM_DAILY_TARGET_MINUTES_SATURDAY)
						+ template.getInteger(PARAM_DAILY_TARGET_MINUTES_SUNDAY);
				schedule.setInteger(PARAM_WEEKLY_TARGET_MINUTES, weeklyMin);
				int minPerDay = VacationHelper.getMinutesPerVacationDay(tx);
				schedule.setDouble(PARAM_EMPLOYMENT_RATE, (double) weeklyMin / (5.0 * minPerDay));

				initVersion(schedule, tx);
				tx.add(schedule);
				ChronivaroAuditHelper.audit(tx, TYPE_EMPLOYMENT_SCHEDULE, schedule.getId(), AUDIT_ACTION_CREATE,
						"Created schedule for " + employee.getName() + " from template " + template.getName());
				employee.setRelation(PARAM_CURRENT_SCHEDULE, schedule);
			} else {
				employee.removeParameter(BAG_RELATIONS, PARAM_CURRENT_SCHEDULE);
			}

			if (arg.initialOvertimeMinutes != null && arg.initialOvertimeMinutes != 0) {
				YearMonth baselineYm = YearMonth.from(scheduleValidFrom).minusMonths(1);
				Resource period = PeriodHelper.getOrCreatePeriod(tx, employeeId, baselineYm);
				period.setString(PARAM_STATE, STATE_LOCKED);
				period.setString(PARAM_COMMENT, "Baseline opening balance snapshot");

				JsonObject json = new JsonObject();
				json.addProperty("employeeId", employeeId);
				json.addProperty("yearMonth", baselineYm.toString());
				json.addProperty("totalTargetMinutes", 0);
				json.addProperty("totalActualMinutes", 0);
				json.addProperty("paidAbsenceMinutes", 0);
				json.addProperty("unpaidAbsenceMinutes", 0);
				json.addProperty("vacationMinutes", 0);
				json.addProperty("totalHolidayMinutes", 0);
				json.addProperty("totalAbsenceMinutes", 0);
				json.addProperty("totalOnCallMinutes", 0);
				json.addProperty("periodBalanceMinutes", 0);
				json.addProperty("initialBalanceMinutes", arg.initialOvertimeMinutes);
				json.addProperty("manualCorrectionsMinutes", 0);
				json.addProperty("finalBalanceMinutes", arg.initialOvertimeMinutes);
				json.addProperty("endBalanceMinutes", arg.initialOvertimeMinutes);
				json.addProperty("calculatedAt", ZonedDateTime.now(zoneId).toString());

				period.setString(PARAM_CALCULATION_SNAPSHOT, json.toString());
				ChronivaroAuditHelper.audit(tx, TYPE_TIME_PERIOD, period.getId(), AUDIT_ACTION_CREATE,
						"Created baseline period snapshot for " + employee.getName() + " with opening balance "
								+ arg.initialOvertimeMinutes + " minutes");
			}

			if (arg.initialVacationDays != null && arg.initialVacationDays != 0.0) {
				int minutesPerDay = VacationHelper.getMinutesPerVacationDay(tx);
				int carryOverMinutes = (int) Math.round(arg.initialVacationDays * minutesPerDay);
				Resource carryOver = tx.getResourceTemplate(TYPE_VACATION_ACCOUNT_ENTRY, true);
				String empName = employee.hasParameter(PARAM_FIRSTNAME) && employee.hasParameter(PARAM_LASTNAME)
						? employee.getString(PARAM_FIRSTNAME) + " " + employee.getString(PARAM_LASTNAME)
						: employee.getName();
				carryOver.setName("Initial Vacation Carry-Over " + scheduleValidFrom.getYear() + " (" + empName + ")");
				carryOver.setRelation(PARAM_EMPLOYEE, employee);
				carryOver.setString(PARAM_VACATION_TYPE, VACATION_CARRY_OVER);
				carryOver.setDate(PARAM_DATE, scheduleValidFrom.atStartOfDay(zoneId));
				carryOver.setDate(PARAM_CREATED_AT, ZonedDateTime.now(zoneId));
				carryOver.setInteger(PARAM_VALUE, carryOverMinutes);
				carryOver.setString(PARAM_COMMENT, "Initial vacation carry-over from onboarding (" + arg.initialVacationDays + " days)");
				carryOver.setString(PARAM_CREATED_BY, tx.getCertificate() != null ? tx.getCertificate().getUsername() : "system");

				initVersion(carryOver, tx);
				tx.add(carryOver);
				ChronivaroAuditHelper.audit(tx, TYPE_VACATION_ACCOUNT_ENTRY, carryOver.getId(), AUDIT_ACTION_CREATE,
						"Credited initial vacation carry-over for " + empName + " (" + carryOverMinutes + " minutes)");
			}

			VacationHelper.creditOrRecalculateEntitlement(tx, employeeId, scheduleValidFrom.getYear(), false);

			tx.commitOnClose();
		}
		return new StringResult(employeeId);
	}

	static UserRep createOrUpdateUser(StrolchTransaction tx, EmployeeArgument arg) {
		PrivilegeHandler privilegeHandler = tx.getContainer().getPrivilegeHandler().getPrivilegeHandler();
		Map<String, String> properties = new HashMap<>();
		String organisation = tx.getCertificate().getOrganisation();
		if (organisation != null)
			properties.put(PrivilegeConstants.ORGANISATION, organisation);
		if (arg.email != null && !arg.email.isBlank())
			properties.put(PrivilegeConstants.EMAIL, arg.email);
		UserRep userRep = new UserRep(null, arg.username, arg.firstname, arg.lastname, ENABLED, null,
				Set.of(ROLE_EMPLOYEE, ROLE_MODEL_ACCESSOR), tx.getCertificate().getLocale(), properties, null);
		UserRep existingUser = privilegeHandler.getUser(tx.getCertificate(), userRep.getUsername());
		if (existingUser == null) {
			return privilegeHandler.addUser(tx.getCertificate(), userRep, null);
		} else {
			userRep.setUserId(existingUser.getUserId());
			Set<String> roles = new HashSet<>(existingUser.getRoles());
			roles.add(ROLE_EMPLOYEE);
			roles.add(ROLE_MODEL_ACCESSOR);
			userRep.setRoles(roles);
			return privilegeHandler.updateUser(tx.getCertificate(), userRep, null);
		}
	}

	@Override
	public EmployeeArgument getArgumentInstance() {
		return new EmployeeArgument();
	}

	@Override
	public StringResult getResultInstance() {
		return new StringResult(ServiceResultState.FAILED);
	}

	public static class EmployeeArgument extends ServiceArgument {
		public String personalNumber;
		public String firstname;
		public String lastname;
		public LocalDate birthdate;
		public String teamId;
		public String locationId;
		public String timezone;
		public LocalDate joinDate;
		public LocalDate exitDate;
		public boolean active;
		public String username;
		public String email;
		public String scheduleTemplateId;
		public LocalDate scheduleValidFrom;
		public Integer initialOvertimeMinutes;
		public Double initialVacationDays;
	}

	public static class UpdateEmployeeArgument extends EmployeeArgument {
		public String id;
	}
}
