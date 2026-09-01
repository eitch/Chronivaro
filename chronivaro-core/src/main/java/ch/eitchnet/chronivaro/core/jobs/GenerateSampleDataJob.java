package ch.eitchnet.chronivaro.core.jobs;

import ch.eitchnet.chronivaro.core.model.WorkingLocation;
import ch.eitchnet.chronivaro.core.model.WorkingLocationDurationType;
import ch.eitchnet.chronivaro.core.service.*;
import li.strolch.agent.api.StrolchAgent;
import li.strolch.job.JobMode;
import li.strolch.job.StrolchJob;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.privilege.model.Certificate;
import li.strolch.privilege.model.PrivilegeContext;
import li.strolch.service.StringResult;
import li.strolch.service.api.ServiceHandler;
import li.strolch.service.api.ServiceResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static ch.eitchnet.chronivaro.core.model.ChronivaroConstants.*;

/**
 * Job to generate extensive sample data for Chronivaro during first start / manual execution.
 */
public class GenerateSampleDataJob extends StrolchJob {

	private static final Logger logger = LoggerFactory.getLogger(GenerateSampleDataJob.class);

	public GenerateSampleDataJob(StrolchAgent agent, String id, String name, JobMode mode) {
		super(agent, id, name, mode);
	}

	@Override
	protected void execute(PrivilegeContext ctx) throws Exception {
		Certificate cert = ctx.getCertificate();
		ServiceHandler serviceHandler = getContainer().getComponent(ServiceHandler.class);

		logger.info("Starting sample data generation with user: {}", cert.getUsername());

		// 1. Check if sample data already exists
		try (StrolchTransaction tx = openTx(cert)) {
			long employeeCount = tx.streamResources(TYPE_EMPLOYEE).count();
			if (employeeCount > 1) {
				logger.info("Sample data appears to already exist (found {} employees). Skipping.", employeeCount);
				return;
			}
		}

		// 2. Holiday Calendars & Holidays
		String zurichCalId = createHolidayCalendar(cert, serviceHandler, "Zurich Holidays");
		String genevaCalId = createHolidayCalendar(cert, serviceHandler, "Geneva Holidays");

		int currentYear = LocalDate.now().getYear();
		for (int year : List.of(currentYear - 1, currentYear, currentYear + 1)) {
			createHoliday(cert, serviceHandler, zurichCalId, LocalDate.of(year, 1, 1), "New Year's Day", 1.0);
			createHoliday(cert, serviceHandler, zurichCalId, LocalDate.of(year, 1, 2), "Berchtoldstag", 1.0);
			createHoliday(cert, serviceHandler, zurichCalId, LocalDate.of(year, 8, 1), "Swiss National Day", 1.0);
			createHoliday(cert, serviceHandler, zurichCalId, LocalDate.of(year, 12, 25), "Christmas Day", 1.0);
			createHoliday(cert, serviceHandler, zurichCalId, LocalDate.of(year, 12, 26), "St. Stephen's Day", 1.0);

			createHoliday(cert, serviceHandler, genevaCalId, LocalDate.of(year, 1, 1), "Nouvel An", 1.0);
			createHoliday(cert, serviceHandler, genevaCalId, LocalDate.of(year, 8, 1), "Fête Nationale", 1.0);
			createHoliday(cert, serviceHandler, genevaCalId, LocalDate.of(year, 9, 10), "Jeûne Genevois", 1.0);
			createHoliday(cert, serviceHandler, genevaCalId, LocalDate.of(year, 12, 25), "Noël", 1.0);
			createHoliday(cert, serviceHandler, genevaCalId, LocalDate.of(year, 12, 31), "Restauration de la République", 1.0);
		}

		// 3. Locations
		String zurichLocId = createLocation(cert, serviceHandler, "Zurich Headquarter", "Europe/Zurich", zurichCalId);
		String genevaLocId = createLocation(cert, serviceHandler, "Geneva Branch", "Europe/Zurich", genevaCalId);

		// 4. Teams
		String engineeringTeamId = createTeam(cert, serviceHandler, "Engineering");
		String operationsTeamId = createTeam(cert, serviceHandler, "Operations & Support");
		String hrTeamId = createTeam(cert, serviceHandler, "Human Resources");

		// 5. Schedule Templates
		String fullTimeTemplateId = createScheduleTemplate(cert, serviceHandler, "Full Time 100% (40h)", 480, 480, 480, 480, 480, 0, 0);
		String partTime80TemplateId = createScheduleTemplate(cert, serviceHandler, "Part Time 80% (32h)", 480, 480, 480, 480, 0, 0, 0);
		String partTime60TemplateId = createScheduleTemplate(cert, serviceHandler, "Part Time 60% (24h)", 480, 480, 480, 0, 0, 0, 0);

		// 6. Employees
		// Supervisor / Engineering Lead: Alice Smith (Zurich)
		String aliceId = createEmployee(cert, serviceHandler, "alice.smith", "Alice", "Smith", "alice@example.com",
				"EMP001", zurichLocId, engineeringTeamId, "Europe/Zurich", fullTimeTemplateId,
				LocalDate.of(currentYear - 1, 1, 1), null, true);

		// Senior Engineer: Bob Jones (Zurich)
		String bobId = createEmployee(cert, serviceHandler, "bob.jones", "Bob", "Jones", "bob@example.com",
				"EMP002", zurichLocId, engineeringTeamId, "Europe/Zurich", fullTimeTemplateId,
				LocalDate.of(currentYear - 1, 1, 1), null, true);

		// Part-time Developer: Carol White (Zurich, 80%)
		String carolId = createEmployee(cert, serviceHandler, "carol.white", "Carol", "White", "carol@example.com",
				"EMP003", zurichLocId, engineeringTeamId, "Europe/Zurich", partTime80TemplateId,
				LocalDate.of(currentYear - 1, 3, 1), null, true);

		// Operations Specialist: David Miller (Geneva, 100%)
		String davidId = createEmployee(cert, serviceHandler, "david.miller", "David", "Miller", "david@example.com",
				"EMP004", genevaLocId, operationsTeamId, "Europe/Zurich", fullTimeTemplateId,
				LocalDate.of(currentYear - 1, 1, 1), null, true);

		// HR Manager: Emma Watson (Geneva, 60%)
		String emmaId = createEmployee(cert, serviceHandler, "emma.watson", "Emma", "Watson", "emma@example.com",
				"EMP005", genevaLocId, hrTeamId, "Europe/Zurich", partTime60TemplateId,
				LocalDate.of(currentYear - 1, 1, 1), null, true);

		// Assign Alice as supervisor for Engineering team
		assignTeamLeader(cert, engineeringTeamId, aliceId);
		// Assign Emma as supervisor for Operations & Support and HR
		assignTeamLeader(cert, operationsTeamId, emmaId);
		assignTeamLeader(cert, hrTeamId, emmaId);

		// 7. Working Location Defaults
		addWorkingLocationDefault(cert, serviceHandler, aliceId, DayOfWeek.MONDAY, WorkingLocationDurationType.FULL_DAY, null, WorkingLocation.OFFICE);
		addWorkingLocationDefault(cert, serviceHandler, aliceId, DayOfWeek.TUESDAY, WorkingLocationDurationType.FULL_DAY, null, WorkingLocation.OFFICE);
		addWorkingLocationDefault(cert, serviceHandler, aliceId, DayOfWeek.WEDNESDAY, WorkingLocationDurationType.FULL_DAY, null, WorkingLocation.HOME_OFFICE);
		addWorkingLocationDefault(cert, serviceHandler, aliceId, DayOfWeek.THURSDAY, WorkingLocationDurationType.FULL_DAY, null, WorkingLocation.OFFICE);
		addWorkingLocationDefault(cert, serviceHandler, aliceId, DayOfWeek.FRIDAY, WorkingLocationDurationType.FULL_DAY, null, WorkingLocation.HOME_OFFICE);

		addWorkingLocationDefault(cert, serviceHandler, bobId, DayOfWeek.MONDAY, WorkingLocationDurationType.FULL_DAY, null, WorkingLocation.HOME_OFFICE);
		addWorkingLocationDefault(cert, serviceHandler, bobId, DayOfWeek.TUESDAY, WorkingLocationDurationType.FULL_DAY, null, WorkingLocation.OFFICE);
		addWorkingLocationDefault(cert, serviceHandler, bobId, DayOfWeek.WEDNESDAY, WorkingLocationDurationType.FULL_DAY, null, WorkingLocation.OFFICE);
		addWorkingLocationDefault(cert, serviceHandler, bobId, DayOfWeek.THURSDAY, WorkingLocationDurationType.FULL_DAY, null, WorkingLocation.OFFICE);
		addWorkingLocationDefault(cert, serviceHandler, bobId, DayOfWeek.FRIDAY, WorkingLocationDurationType.FULL_DAY, null, WorkingLocation.HOME_OFFICE);

		addWorkingLocationDefault(cert, serviceHandler, davidId, DayOfWeek.MONDAY, WorkingLocationDurationType.FULL_DAY, null, WorkingLocation.OFFICE);
		addWorkingLocationDefault(cert, serviceHandler, davidId, DayOfWeek.TUESDAY, WorkingLocationDurationType.FULL_DAY, null, WorkingLocation.OFFICE);
		addWorkingLocationDefault(cert, serviceHandler, davidId, DayOfWeek.WEDNESDAY, WorkingLocationDurationType.FULL_DAY, null, WorkingLocation.OFFICE);
		addWorkingLocationDefault(cert, serviceHandler, davidId, DayOfWeek.THURSDAY, WorkingLocationDurationType.FULL_DAY, null, WorkingLocation.OFFICE);
		addWorkingLocationDefault(cert, serviceHandler, davidId, DayOfWeek.FRIDAY, WorkingLocationDurationType.FULL_DAY, null, WorkingLocation.OFFICE);

		// 8. Generate Time Entries & Work Data
		ZoneId zurichTz = ZoneId.of("Europe/Zurich");
		LocalDate today = LocalDate.now();

		// Generate entries for the last 60 days up to yesterday for Alice, Bob, Carol, David
		LocalDate startDate = today.minusDays(60);
		for (LocalDate date = startDate; date.isBefore(today); date = date.plusDays(1)) {
			DayOfWeek dow = date.getDayOfWeek();
			if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
				continue;
			}

			if (!isHoliday(cert, zurichCalId, date)) {
				// Alice: regular work days
				if (date.getDayOfMonth() % 7 != 0) {
					addWorkEntry(cert, serviceHandler, aliceId,
							date.atTime(8, 0).atZone(zurichTz),
							date.atTime(12, 0).atZone(zurichTz),
							"Morning focus", WorkingLocation.OFFICE, false);
					addWorkEntry(cert, serviceHandler, aliceId,
							date.atTime(13, 0).atZone(zurichTz),
							date.atTime(17, 0).atZone(zurichTz),
							"Team sync & review", WorkingLocation.OFFICE, false);
				}

				// Bob: regular work days
				if (date.getDayOfMonth() % 9 != 0) {
					addWorkEntry(cert, serviceHandler, bobId,
							date.atTime(8, 30).atZone(zurichTz),
							date.atTime(12, 0).atZone(zurichTz),
							"Feature implementation", WorkingLocation.OFFICE, false);
					addWorkEntry(cert, serviceHandler, bobId,
							date.atTime(13, 0).atZone(zurichTz),
							date.atTime(17, 30).atZone(zurichTz),
							"Code review & tests", WorkingLocation.OFFICE, false);
				}

				// Carol: Mon-Thu
				if (dow != DayOfWeek.FRIDAY && date.getDayOfMonth() % 8 != 0) {
					addWorkEntry(cert, serviceHandler, carolId,
							date.atTime(8, 0).atZone(zurichTz),
							date.atTime(12, 0).atZone(zurichTz),
							"Frontend development", WorkingLocation.HOME_OFFICE, false);
					addWorkEntry(cert, serviceHandler, carolId,
							date.atTime(12, 30).atZone(zurichTz),
							date.atTime(16, 30).atZone(zurichTz),
							"UI components", WorkingLocation.HOME_OFFICE, false);
				}

				// David: Operations
				addWorkEntry(cert, serviceHandler, davidId,
						date.atTime(8, 0).atZone(zurichTz),
						date.atTime(12, 0).atZone(zurichTz),
						"Incident management", WorkingLocation.OFFICE, false);
				addWorkEntry(cert, serviceHandler, davidId,
						date.atTime(13, 0).atZone(zurichTz),
						date.atTime(17, 0).atZone(zurichTz),
						"Infrastructure monitoring", WorkingLocation.OFFICE, false);
			}
		}

		// 9. Absences (Vacation, Sickness, Training)
		// Bob took a vacation 20 days ago for 3 days
		LocalDate vacStart = today.minusDays(20);
		LocalDate vacEnd = vacStart.plusDays(2);
		requestAndApproveAbsence(cert, serviceHandler, bobId, "VACATION",
				vacStart.atStartOfDay(zurichTz), vacEnd.atTime(23, 59, 59).atZone(zurichTz),
				DURATION_FULL_DAY, null, 0, "Summer holiday");

		// Alice was sick 35 days ago for 1 day
		LocalDate sickDate = today.minusDays(35);
		requestAndApproveAbsence(cert, serviceHandler, aliceId, "ILLNESS",
				sickDate.atStartOfDay(zurichTz), sickDate.atTime(23, 59, 59).atZone(zurichTz),
				DURATION_FULL_DAY, null, 0, "Flu");

		// Carol has upcoming vacation next month
		LocalDate upcomingVacStart = today.plusDays(14);
		LocalDate upcomingVacEnd = upcomingVacStart.plusDays(4);
		requestAbsence(cert, serviceHandler, carolId, "VACATION",
				upcomingVacStart.atStartOfDay(zurichTz), upcomingVacEnd.atTime(23, 59, 59).atZone(zurichTz),
				DURATION_FULL_DAY, null, 0, "Family trip", false);

		// David has training next week
		LocalDate trainingDate = today.plusDays(7);
		requestAndApproveAbsence(cert, serviceHandler, davidId, "TRAINING",
				trainingDate.atStartOfDay(zurichTz), trainingDate.atTime(23, 59, 59).atZone(zurichTz),
				DURATION_FULL_DAY, null, 0, "Kubernetes Advanced Training");

		// 10. On-Call Periods & On-Call Work
		LocalDate onCallStart = today.minusDays(14);
		LocalDate onCallEnd = today.minusDays(8);
		createOnCallPeriod(cert, serviceHandler, davidId, onCallStart, "18:00", onCallEnd, "08:00", "Weekly DevOps on-call duty");

		// Add an on-call emergency work entry for David during that period
		LocalDate incidentDate = onCallStart.plusDays(2);
		addWorkEntry(cert, serviceHandler, davidId,
				incidentDate.atTime(22, 0).atZone(zurichTz),
				incidentDate.atTime(23, 30).atZone(zurichTz),
				"Emergency server restart", WorkingLocation.OFFICE, true);

		// 11. Periods submission and approval for previous completed month
		LocalDate previousMonthDate = today.minusMonths(1).withDayOfMonth(1);
		submitAndApprovePeriod(cert, serviceHandler, aliceId, previousMonthDate);
		submitAndApprovePeriod(cert, serviceHandler, bobId, previousMonthDate);
		submitAndApprovePeriod(cert, serviceHandler, davidId, previousMonthDate);

		logger.info("Sample data generation finished successfully!");
	}

	private String createHolidayCalendar(Certificate cert, ServiceHandler serviceHandler, String name) {
		CreateHolidayCalendarService.HolidayCalendarArgument arg = new CreateHolidayCalendarService.HolidayCalendarArgument();
		arg.name = name;
		StringResult res = serviceHandler.doService(cert, new CreateHolidayCalendarService(), arg);
		if (!res.isOk()) {
			throw new IllegalStateException("Failed to create holiday calendar: " + res.getMessage());
		}
		return res.getValue();
	}

	private void createHoliday(Certificate cert, ServiceHandler serviceHandler, String calId, LocalDate date, String name, double factor) {
		CreateHolidayService.HolidayArgument arg = new CreateHolidayService.HolidayArgument();
		arg.holidayCalendarId = calId;
		arg.date = date;
		arg.name = name;
		arg.creditFactor = factor;
		ServiceResult res = serviceHandler.doService(cert, new CreateHolidayService(), arg);
		if (!res.isOk() && !res.getMessage().contains("already exists")) {
			throw new IllegalStateException("Failed to create holiday: " + res.getMessage());
		}
	}

	private boolean isHoliday(Certificate cert, String calId, LocalDate date) {
		try (StrolchTransaction tx = openTx(cert)) {
			ZonedDateTime zdt = date.atStartOfDay(ZoneId.of("Europe/Zurich"));
			return tx.streamResources(TYPE_HOLIDAY)
					.filter(h -> h.hasRelation(PARAM_HOLIDAY_CALENDAR) && calId.equals(h.getRelationId(PARAM_HOLIDAY_CALENDAR)))
					.anyMatch(h -> h.hasParameter(PARAM_DATE) && h.getDate(PARAM_DATE).equals(zdt));
		}
	}

	private String createLocation(Certificate cert, ServiceHandler serviceHandler, String name, String timezone, String holidayCalId) {
		CreateLocationService.LocationArgument arg = new CreateLocationService.LocationArgument();
		arg.name = name;
		arg.timezone = timezone;
		arg.holidayCalendarId = holidayCalId;
		ServiceResult res = serviceHandler.doService(cert, new CreateLocationService(), arg);
		if (!res.isOk()) {
			throw new IllegalStateException("Failed to create location: " + res.getMessage());
		}
		try (StrolchTransaction tx = openTx(cert)) {
			return tx.streamResources(TYPE_LOCATION)
					.filter(r -> name.equals(r.getString(PARAM_NAME)))
					.findFirst()
					.orElseThrow()
					.getId();
		}
	}

	private String createTeam(Certificate cert, ServiceHandler serviceHandler, String name) {
		CreateTeamService.TeamArgument arg = new CreateTeamService.TeamArgument();
		arg.name = name;
		ServiceResult res = serviceHandler.doService(cert, new CreateTeamService(), arg);
		if (!res.isOk()) {
			throw new IllegalStateException("Failed to create team: " + res.getMessage());
		}
		try (StrolchTransaction tx = openTx(cert)) {
			return tx.streamResources(TYPE_TEAM)
					.filter(r -> name.equals(r.getString(PARAM_NAME)))
					.findFirst()
					.orElseThrow()
					.getId();
		}
	}

	private String createScheduleTemplate(Certificate cert, ServiceHandler serviceHandler, String name,
			int mon, int tue, int wed, int thu, int fri, int sat, int sun) {
		CreateScheduleTemplateService.CreateScheduleTemplateArgument arg = new CreateScheduleTemplateService.CreateScheduleTemplateArgument();
		arg.name = name;
		arg.monday = mon;
		arg.tuesday = tue;
		arg.wednesday = wed;
		arg.thursday = thu;
		arg.friday = fri;
		arg.saturday = sat;
		arg.sunday = sun;
		StringResult res = serviceHandler.doService(cert, new CreateScheduleTemplateService(), arg);
		if (!res.isOk()) {
			throw new IllegalStateException("Failed to create schedule template: " + res.getMessage());
		}
		return res.getValue();
	}

	private String createEmployee(Certificate cert, ServiceHandler serviceHandler, String username,
			String firstname, String lastname, String email, String personalNumber, String locationId,
			String teamId, String timezone, String scheduleTemplateId,
			LocalDate joinDate, LocalDate exitDate, boolean active) {

		CreateEmployeeService.EmployeeArgument arg = new CreateEmployeeService.EmployeeArgument();
		arg.username = username;
		arg.firstname = firstname;
		arg.lastname = lastname;
		arg.email = email;
		arg.personalNumber = personalNumber;
		arg.locationId = locationId;
		arg.teamId = teamId;
		arg.timezone = timezone;
		arg.scheduleTemplateId = scheduleTemplateId;
		arg.joinDate = joinDate;
		arg.exitDate = exitDate;
		arg.active = active;

		StringResult res = serviceHandler.doService(cert, new CreateEmployeeService(), arg);
		if (!res.isOk()) {
			throw new IllegalStateException("Failed to create employee " + username + ": " + res.getMessage());
		}
		return res.getValue();
	}

	private void assignTeamLeader(Certificate cert, String teamId, String leaderEmployeeId) {
		try (StrolchTransaction tx = openTx(cert)) {
			Resource team = tx.getResourceBy(TYPE_TEAM, teamId, true);
			Resource leader = tx.getResourceBy(TYPE_EMPLOYEE, leaderEmployeeId, true);
			team.setRelation(PARAM_LEADER, leader);
			tx.update(team);
			tx.commitOnClose();
		}
	}

	private void addWorkingLocationDefault(Certificate cert, ServiceHandler serviceHandler, String employeeId,
			DayOfWeek weekday, WorkingLocationDurationType durationType, String dayPart, WorkingLocation location) {
		AddOrUpdateWorkingLocationDefaultService.Argument arg = new AddOrUpdateWorkingLocationDefaultService.Argument();
		arg.employeeId = employeeId;
		arg.weekday = weekday;
		arg.durationType = durationType;
		arg.dayPart = dayPart;
		arg.workingLocation = location.name();
		ServiceResult res = serviceHandler.doService(cert, new AddOrUpdateWorkingLocationDefaultService(), arg);
		if (!res.isOk()) {
			throw new IllegalStateException("Failed to add working location default: " + res.getMessage());
		}
	}

	private void addWorkEntry(Certificate cert, ServiceHandler serviceHandler, String employeeId,
			ZonedDateTime start, ZonedDateTime end, String comment, WorkingLocation location, boolean isOnCall) {
		AddWorkEntryService.AddWorkEntryArgument arg = new AddWorkEntryService.AddWorkEntryArgument();
		arg.employeeId = employeeId;
		arg.start = start;
		arg.end = end;
		arg.comment = comment;
		arg.workingLocation = location;
		arg.isOnCall = isOnCall;
		StringResult res = serviceHandler.doService(cert, new AddWorkEntryService(), arg);
		if (!res.isOk()) {
			logger.warn("Could not add work entry for {} at {}: {}", employeeId, start, res.getMessage());
		}
	}

	private void requestAndApproveAbsence(Certificate cert, ServiceHandler serviceHandler, String employeeId,
			String absenceTypeCode, ZonedDateTime start, ZonedDateTime end, String durationType,
			String dayPart, int minutes, String comment) {
		RequestAbsenceService.RequestAbsenceArgument arg = new RequestAbsenceService.RequestAbsenceArgument();
		arg.employeeId = employeeId;
		arg.absenceTypeCode = absenceTypeCode;
		arg.start = start;
		arg.end = end;
		arg.durationType = durationType;
		arg.dayPart = dayPart;
		arg.minutes = minutes;
		arg.comment = comment;
		arg.directApprove = true;
		arg.state = STATE_APPROVED;
		StringResult res = serviceHandler.doService(cert, new RequestAbsenceService(), arg);
		if (!res.isOk()) {
			logger.warn("Could not request/approve absence for {}: {}", employeeId, res.getMessage());
		}
	}

	private void requestAbsence(Certificate cert, ServiceHandler serviceHandler, String employeeId,
			String absenceTypeCode, ZonedDateTime start, ZonedDateTime end, String durationType,
			String dayPart, int minutes, String comment, boolean asDraft) {
		RequestAbsenceService.RequestAbsenceArgument arg = new RequestAbsenceService.RequestAbsenceArgument();
		arg.employeeId = employeeId;
		arg.absenceTypeCode = absenceTypeCode;
		arg.start = start;
		arg.end = end;
		arg.durationType = durationType;
		arg.dayPart = dayPart;
		arg.minutes = minutes;
		arg.comment = comment;
		arg.asDraft = asDraft;
		StringResult res = serviceHandler.doService(cert, new RequestAbsenceService(), arg);
		if (!res.isOk()) {
			logger.warn("Could not request absence for {}: {}", employeeId, res.getMessage());
		}
	}

	private void createOnCallPeriod(Certificate cert, ServiceHandler serviceHandler, String employeeId,
			LocalDate start, String startTime, LocalDate end, String endTime, String comment) {
		CreateOnCallPeriodService.CreateOnCallPeriodArgument arg = new CreateOnCallPeriodService.CreateOnCallPeriodArgument();
		arg.employeeId = employeeId;
		arg.startDate = start;
		arg.startTime = startTime;
		arg.endDate = end;
		arg.endTime = endTime;
		arg.comment = comment;
		ServiceResult res = serviceHandler.doService(cert, new CreateOnCallPeriodService(), arg);
		if (!res.isOk()) {
			logger.warn("Could not create on-call period for {}: {}", employeeId, res.getMessage());
		}
	}

	private void submitAndApprovePeriod(Certificate cert, ServiceHandler serviceHandler, String employeeId, LocalDate monthDate) {
		PeriodActionArgument submitArg = new PeriodActionArgument();
		submitArg.employeeId = employeeId;
		submitArg.yearMonth = java.time.YearMonth.from(monthDate);
		ServiceResult submitRes = serviceHandler.doService(cert, new SubmitPeriodService(), submitArg);
		if (submitRes.isOk()) {
			PeriodActionArgument approveArg = new PeriodActionArgument();
			approveArg.employeeId = employeeId;
			approveArg.yearMonth = java.time.YearMonth.from(monthDate);
			serviceHandler.doService(cert, new ApprovePeriodService(), approveArg);
		}
	}
}
