package ch.eitchnet.chronivaro.rest.resource;

import ch.eitchnet.chronivaro.rest.dto.PersonalAccessTokenDto;
import li.strolch.privilege.model.PersonalAccessTokenRep;

import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public class PersonalAccessTokenHelper {

	public static final String PRESET_DESKTOP_TIMER = "DESKTOP_TIMER";
	public static final String PRESET_READ_ONLY_TIMES = "READ_ONLY_TIMES";
	public static final String PRESET_FULL_PERSONAL = "FULL_PERSONAL";

	public static final Set<String> SUPPORTED_PRESETS = Set.of(
			PRESET_DESKTOP_TIMER,
			PRESET_READ_ONLY_TIMES,
			PRESET_FULL_PERSONAL
	);

	public static final Set<String> SERVICES_DESKTOP_TIMER = Set.of(
			"ch.eitchnet.chronivaro.core.service.StartTimerService",
			"ch.eitchnet.chronivaro.core.service.StopTimerService",
			"ch.eitchnet.chronivaro.core.service.DaySummaryService",
			"ch.eitchnet.chronivaro.core.service.MonthSummaryService"
	);

	public static final Set<String> SERVICES_READ_ONLY_TIMES = Set.of(
			"ch.eitchnet.chronivaro.core.service.DaySummaryService",
			"ch.eitchnet.chronivaro.core.service.MonthSummaryService",
			"ch.eitchnet.chronivaro.core.service.PresenceService",
			"ch.eitchnet.chronivaro.core.service.GetVacationAccountSummaryService"
	);

	public static final Set<String> SEARCHES_DESKTOP_TIMER = Set.of(
			"ch.eitchnet.chronivaro.core.search.TimePeriodSearch"
	);

	public static final Set<String> SEARCHES_READ_ONLY_TIMES = Set.of(
			"ch.eitchnet.chronivaro.core.search.AbsenceSearch",
			"ch.eitchnet.chronivaro.core.search.TimePeriodSearch",
			"ch.eitchnet.chronivaro.core.search.VacationAccountEntrySearch",
			"ch.eitchnet.chronivaro.core.search.OnCallPeriodSearch"
	);

	public static Set<String> getServicesForPreset(String preset) {
		if (preset == null) {
			return Collections.emptySet();
		}
		return switch (preset.trim().toUpperCase()) {
			case PRESET_DESKTOP_TIMER -> SERVICES_DESKTOP_TIMER;
			case PRESET_READ_ONLY_TIMES -> SERVICES_READ_ONLY_TIMES;
			case PRESET_FULL_PERSONAL -> Collections.emptySet();
			default -> Collections.emptySet();
		};
	}

	public static Set<String> getSearchesForPreset(String preset) {
		if (preset == null) {
			return Collections.emptySet();
		}
		return switch (preset.trim().toUpperCase()) {
			case PRESET_DESKTOP_TIMER -> SEARCHES_DESKTOP_TIMER;
			case PRESET_READ_ONLY_TIMES -> SEARCHES_READ_ONLY_TIMES;
			case PRESET_FULL_PERSONAL -> Collections.emptySet();
			default -> Collections.emptySet();
		};
	}

	public static final String ROLE_DESKTOP_TIMER = "DesktopTimerAccess";
	public static final String ROLE_READ_ONLY_TIMES = "ReadOnlyTimesAccess";
	public static final String ROLE_FULL_PERSONAL = "FullPersonalAccess";

	public static Set<String> getRolesForPreset(String preset) {
		return Collections.emptySet();
	}

	public static Set<String> getPrivilegesForPreset(String preset) {
		if (preset == null) {
			return Collections.emptySet();
		}
		return switch (preset.trim().toUpperCase()) {
			case PRESET_DESKTOP_TIMER -> Set.of("li.strolch.service.api.Service", "li.strolch.search.StrolchSearch");
			case PRESET_READ_ONLY_TIMES -> Set.of("li.strolch.service.api.Service", "li.strolch.search.StrolchSearch");
			case PRESET_FULL_PERSONAL -> Collections.emptySet();
			default -> Collections.emptySet();
		};
	}

	public static String inferPreset(PersonalAccessTokenRep rep) {
		Set<String> privileges = rep.privileges();
		if (privileges == null || privileges.isEmpty()) {
			return PRESET_FULL_PERSONAL;
		}

		if (privileges.contains(ROLE_DESKTOP_TIMER)) {
			return PRESET_DESKTOP_TIMER;
		}
		if (privileges.contains(ROLE_READ_ONLY_TIMES)) {
			return PRESET_READ_ONLY_TIMES;
		}
		if (privileges.contains(ROLE_FULL_PERSONAL)) {
			return PRESET_FULL_PERSONAL;
		}

		return PRESET_FULL_PERSONAL;
	}

	public static PersonalAccessTokenDto toDto(PersonalAccessTokenRep rep) {
		return new PersonalAccessTokenDto(
				rep.tokenId(),
				rep.username(),
				rep.name(),
				inferPreset(rep),
				rep.validFrom(),
				rep.validTo(),
				rep.lastUsed()
		);
	}
}
