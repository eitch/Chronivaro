package ch.eitchnet.chronivaro.core.model;

public class ChronivaroConstants {

	public static final String BAG_PARAMETERS = "parameters";
	public static final String BAG_RELATIONS = "relations";

	public static final String TYPE_EMPLOYEE = "Employee";
	public static final String TYPE_TEAM = "Team";
	public static final String TYPE_LOCATION = "Location";
	public static final String TYPE_WORK_ENTRY = "WorkEntry";
	public static final String TYPE_ABSENCE_TYPE = "AbsenceType";
	public static final String TYPE_ABSENCE = "Absence";
	public static final String TYPE_VACATION_ACCOUNT_ENTRY = "VacationAccountEntry";
	public static final String TYPE_HOLIDAY_CALENDAR = "HolidayCalendar";
	public static final String TYPE_HOLIDAY = "Holiday";
	public static final String TYPE_TIME_PERIOD = "TimePeriod";
	public static final String TYPE_AUDIT_EVENT = "ChronivaroAuditEvent";
	public static final String TYPE_EMPLOYMENT_SCHEDULE = "EmploymentSchedule";
	public static final String TYPE_EMPLOYMENT_SCHEDULE_TEMPLATE = "EmploymentScheduleTemplate";
	public static final String TYPE_GLOBAL_CONFIGURATION = "GlobalConfiguration";
	public static final String TYPE_WORK_DAY = "WorkDay";
	public static final String TYPE_WORKING_LOCATION_DEFAULT = "WorkingLocationDefault";

	public static final String PARAM_PERSONAL_NUMBER = "personalNumber";
	public static final String PARAM_FIRSTNAME = "firstname";
	public static final String PARAM_LASTNAME = "lastname";
	public static final String PARAM_BIRTHDATE = "birthdate";
	public static final String PARAM_PRIMARY_TEAM = "primaryTeam";
	public static final String PARAM_LOCATION = "location";
	public static final String PARAM_TIMEZONE = "timezone";
	public static final String PARAM_JOIN_DATE = "joinDate";
	public static final String PARAM_EXIT_DATE = "exitDate";
	public static final String PARAM_ACTIVE = "active";
	public static final String PARAM_USER_ID = "userId";
	public static final String PARAM_USERNAME = "username";
	public static final String PARAM_EMAIL = "email";
	public static final String PARAM_EMPLOYEE = "employee";
	public static final String PARAM_ABSENCE = "absence";
	public static final String PARAM_TEAM = "team";
	public static final String PARAM_LEADER = "leader";
	public static final String PARAM_CURRENT_SCHEDULE = "currentSchedule";
	public static final String PARAM_SCHEDULE = "schedule";
	public static final String PARAM_WORK_DAY = "workDay";
	public static final String PARAM_WORK_ENTRIES = "workEntries";
	public static final String PARAM_CURRENT_WORK_DAY = "currentWorkDay";

	public static final String PARAM_VALID_FROM = "validFrom";
	public static final String PARAM_VALID_TO = "validTo";
	public static final String PARAM_WEEKLY_SCHEDULE_ID = "weeklyScheduleId";
	public static final String PARAM_EMPLOYMENT_RATE = "employmentRate";
	public static final String PARAM_WEEKLY_TARGET_MINUTES = "weeklyTargetMinutes";
	public static final String PARAM_DAILY_TARGET_MINUTES = "dailyTargetMinutes";
	public static final String PARAM_DAILY_TARGET_MINUTES_MONDAY = "dailyTargetMinutesMonday";
	public static final String PARAM_DAILY_TARGET_MINUTES_TUESDAY = "dailyTargetMinutesTuesday";
	public static final String PARAM_DAILY_TARGET_MINUTES_WEDNESDAY = "dailyTargetMinutesWednesday";
	public static final String PARAM_DAILY_TARGET_MINUTES_THURSDAY = "dailyTargetMinutesThursday";
	public static final String PARAM_DAILY_TARGET_MINUTES_FRIDAY = "dailyTargetMinutesFriday";
	public static final String PARAM_DAILY_TARGET_MINUTES_SATURDAY = "dailyTargetMinutesSaturday";
	public static final String PARAM_DAILY_TARGET_MINUTES_SUNDAY = "dailyTargetMinutesSunday";

	public static final String PARAM_START = "start";
	public static final String PARAM_END = "end";
	public static final String PARAM_SOURCE = "source";
	public static final String PARAM_COMMENT = "comment";
	public static final String PARAM_CREATED_BY = "createdBy";
	public static final String PARAM_WORKING_LOCATION = "workingLocation";
	public static final String PARAM_WEEKDAY = "weekday";

	public static final String PARAM_CODE = "code";
	public static final String PARAM_NAME = "name";
	public static final String PARAM_COUNT_AS_TARGET_TIME = "countAsTargetTime";
	public static final String PARAM_REDUCE_VACATION_CREDIT = "reduceVacationCredit";
	public static final String PARAM_PAID = "paid";
	public static final String PARAM_APPROVAL_REQUIRED = "approvalRequired";
	public static final String PARAM_COMMENT_REQUIRED = "commentRequired";
	public static final String PARAM_VISIBLE_ON_PUBLIC_STATUS = "visibleOnPublicStatus";
	public static final String PARAM_DURATION_TYPES = "durationTypes";

	public static final String PARAM_ABSENCE_TYPE = "absenceType";
	public static final String PARAM_DURATION_TYPE = "durationType";
	public static final String PARAM_DAY_PART = "dayPart";
	public static final String PARAM_MINUTES = "minutes";
	public static final String PARAM_STATE = "state";

	public static final String PARAM_VACATION_TYPE = "vacationType";
	public static final String PARAM_VALUE = "value";
	public static final String PARAM_ANNUAL_VACATION_DAYS = "annualVacationDays";
	public static final String PARAM_MINUTES_PER_VACATION_DAY = "minutesPerVacationDay";
	public static final String PARAM_VACATION_ABSENCE_TYPE_CODE = "vacationAbsenceTypeCode";
	public static final String PARAM_COMPANY_NAME = "companyName";
	public static final String PARAM_COMPANY_LOGO = "companyLogo";
	public static final String PARAM_DEFAULT_LANGUAGE = "defaultLanguage";
	public static final String PARAM_SERVER_BASE_URL = "serverBaseUrl";

	public static final int DEFAULT_ANNUAL_VACATION_DAYS = 25;
	public static final int DEFAULT_MINUTES_PER_VACATION_DAY = 480;
	public static final String DEFAULT_VACATION_ABSENCE_TYPE_CODE = "VACATION";
	public static final int DEFAULT_WEEKLY_TARGET_MINUTES = 2520;
	public static final String DEFAULT_COMPANY_NAME = "Chronivaro";
	public static final String DEFAULT_LANGUAGE = "de";
	public static final String DEFAULT_SERVER_BASE_URL = "http://localhost:9000";

	public static final String PARAM_HOLIDAY_CALENDAR = "holidayCalendar";
	public static final String PARAM_DATE = "date";
	public static final String PARAM_CREDIT_FACTOR = "creditFactor";

	public static final String PARAM_YEAR_MONTH = "yearMonth";
	public static final String PARAM_SUBMITTED_AT = "submittedAt";
	public static final String PARAM_APPROVED_AT = "approvedAt";
	public static final String PARAM_APPROVED_BY = "approvedBy";
	public static final String PARAM_REJECTED_AT = "rejectedAt";
	public static final String PARAM_REJECTED_BY = "rejectedBy";
	public static final String PARAM_CALCULATION_SNAPSHOT = "calculationSnapshot";
	public static final String PARAM_ELEMENT_TYPE = "elementType";
	public static final String PARAM_ELEMENT_ID = "elementId";
	public static final String PARAM_ACTION = "action";
	public static final String PARAM_REASON = "reason";
	public static final String PARAM_CORRELATION_ID = "correlationId";
	public static final String PARAM_DETAILS = "details";
	public static final String PARAM_OLD_VALUE = "oldValue";
	public static final String PARAM_NEW_VALUE = "newValue";
	public static final String PARAM_VERSION = "version";
	public static final String PARAM_UPDATED_BY = "updatedBy";

	public static final String AUDIT_ACTION_CREATE = "CREATE";
	public static final String AUDIT_ACTION_UPDATE = "UPDATE";
	public static final String AUDIT_ACTION_REMOVE = "REMOVE";
	public static final String AUDIT_ACTION_SUBMIT = "SUBMIT";
	public static final String AUDIT_ACTION_APPROVE = "APPROVE";
	public static final String AUDIT_ACTION_REJECT = "REJECT";
	public static final String AUDIT_ACTION_CANCEL = "CANCEL";
	public static final String AUDIT_ACTION_LOCK = "LOCK";
	public static final String AUDIT_ACTION_REOPEN = "REOPEN";
	public static final String AUDIT_ACTION_START = "START";
	public static final String AUDIT_ACTION_STOP = "STOP";
	public static final String AUDIT_ACTION_PURGE = "PURGE";
	public static final String AUDIT_ACTION_CORRECT = "CORRECT";
	public static final String AUDIT_ACTION_DEACTIVATE = "DEACTIVATE";
	public static final String AUDIT_ACTION_REACTIVATE = "REACTIVATE";
	public static final String AUDIT_ACTION_REGISTRATION_INITIATED = "REGISTRATION_INITIATED";
	public static final String AUDIT_ACTION_REGISTRATION_COMPLETED = "REGISTRATION_COMPLETED";

	public static final String SOURCE_TIMER = "TIMER";
	public static final String SOURCE_MANUAL = "MANUAL";
	public static final String SOURCE_IMPORT = "IMPORT";
	public static final String SOURCE_ADMIN = "ADMIN";

	public static final String STATE_DRAFT = "DRAFT";
	public static final String STATE_OPEN = "OPEN";
	public static final String STATE_SUBMITTED = "SUBMITTED";
	public static final String STATE_APPROVED = "APPROVED";
	public static final String STATE_LOCKED = "LOCKED";
	public static final String STATE_REJECTED = "REJECTED";
	public static final String STATE_CANCELLED = "CANCELLED";

	public static final String DURATION_HOURS = "HOURS";
	public static final String DURATION_HALF_DAY = "HALF_DAY";
	public static final String DURATION_FULL_DAY = "FULL_DAY";

	public static final String DAY_PART_MORNING = "MORNING";
	public static final String DAY_PART_AFTERNOON = "AFTERNOON";

	public static final String VACATION_ENTITLEMENT = "ENTITLEMENT";
	public static final String VACATION_CARRY_OVER = "CARRY_OVER";
	public static final String VACATION_USAGE = "USAGE";
	public static final String VACATION_CORRECTION = "CORRECTION";
	public static final String VACATION_EXPIRY = "EXPIRY";

	public static final String ROLE_MODEL_ACCESSOR = "ModelAccessor";
	public static final String ROLE_EMPLOYEE = "Employee";
	public static final String ROLE_SUPERVISOR = "Supervisor";
	public static final String ROLE_HR = "HR";
	public static final String ROLE_ADMIN = "StrolchAdmin";
	public static final String ROLE_ADMINISTRATOR = "Administrator";
}
