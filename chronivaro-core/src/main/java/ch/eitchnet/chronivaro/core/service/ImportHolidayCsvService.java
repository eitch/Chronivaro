package ch.eitchnet.chronivaro.core.service;

import ch.eitchnet.chronivaro.core.holiday.HolidayCsvParser;
import ch.eitchnet.chronivaro.core.holiday.HolidayCsvParserRegistry;
import ch.eitchnet.chronivaro.core.holiday.ParsedHoliday;
import ch.eitchnet.chronivaro.core.model.ChronivaroAuditHelper;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.service.api.AbstractService;
import li.strolch.service.api.ServiceArgument;
import li.strolch.service.api.ServiceResult;
import li.strolch.service.api.ServiceResultState;
import li.strolch.utils.dbc.DBC;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static ch.eitchnet.chronivaro.core.model.ChronivaroConstants.*;
import static ch.eitchnet.chronivaro.core.model.ChronivaroVersionHelper.initVersion;

public class ImportHolidayCsvService extends AbstractService<ImportHolidayCsvService.ImportHolidayCsvArgument, ImportHolidayCsvService.ImportHolidayCsvResult> {

	@Override
	protected ImportHolidayCsvResult internalDoService(ImportHolidayCsvArgument arg) throws Exception {
		DBC.PRE.assertNotEmpty("holidayCalendarId must be set", arg.holidayCalendarId);
		DBC.PRE.assertNotEmpty("format must be set", arg.format);
		DBC.PRE.assertNotEmpty("csvData must be set", arg.csvData);

		HolidayCsvParser parser = HolidayCsvParserRegistry.getParser(arg.format)
				.orElseThrow(() -> new IllegalArgumentException("Unsupported holiday CSV format: " + arg.format));

		List<ParsedHoliday> parsedHolidays = parser.parse(arg.csvData);

		int importedCount = 0;
		int skippedCount = 0;

		try (StrolchTransaction tx = openArgOrUserTx(arg)) {
			Resource calendar = tx.getResourceBy(TYPE_HOLIDAY_CALENDAR, arg.holidayCalendarId, true);
			ZoneId zoneId = ZoneId.of("Europe/Zurich");

			// Get all existing holiday dates for this calendar
			Set<LocalDate> existingDates = new HashSet<>();
			tx.streamResources(TYPE_HOLIDAY)
					.filter(h -> h.hasRelation(PARAM_HOLIDAY_CALENDAR) && h
							.getRelationId(PARAM_HOLIDAY_CALENDAR)
							.equals(arg.holidayCalendarId))
					.forEach(h -> {
						if (h.hasParameter(PARAM_DATE)) {
							existingDates.add(h.getDate(PARAM_DATE).toLocalDate());
						}
					});

			// Also track dates within the parsed CSV to avoid duplicates inside the same file
			Set<LocalDate> importedDates = new HashSet<>();

			for (ParsedHoliday ph : parsedHolidays) {
				if (existingDates.contains(ph.date()) || importedDates.contains(ph.date())) {
					skippedCount++;
					continue;
				}

				ZonedDateTime date = ph.date().atStartOfDay(zoneId);

				Resource holiday = tx.getResourceTemplate(TYPE_HOLIDAY, true);
				holiday.setName(ph.name());
				holiday.setRelation(PARAM_HOLIDAY_CALENDAR, calendar);
				holiday.setDate(PARAM_DATE, date);
				holiday.setString(PARAM_NAME, ph.name());
				holiday.setDouble(PARAM_CREDIT_FACTOR, ph.creditFactor() == 0.0 ? 1.0 : ph.creditFactor());
				initVersion(holiday, tx);
				tx.add(holiday);

				importedDates.add(ph.date());
				importedCount++;
			}

			if (importedCount > 0) {
				ChronivaroAuditHelper.audit(tx, TYPE_HOLIDAY_CALENDAR, calendar.getId(), AUDIT_ACTION_UPDATE,
						"Imported " + importedCount + " holidays (skipped " + skippedCount + ") using format '" + arg.format + "'");
			}

			tx.commitOnClose();
		}

		ImportHolidayCsvResult result = new ImportHolidayCsvResult(ServiceResultState.SUCCESS);
		result.setImportedCount(importedCount);
		result.setSkippedCount(skippedCount);
		result.setTotalCount(parsedHolidays.size());
		result.setMessage("Successfully processed " + parsedHolidays.size() + " holidays: imported " + importedCount + ", skipped " + skippedCount);
		return result;
	}

	@Override
	public ImportHolidayCsvArgument getArgumentInstance() {
		return new ImportHolidayCsvArgument();
	}

	@Override
	public ImportHolidayCsvResult getResultInstance() {
		return new ImportHolidayCsvResult(ServiceResultState.FAILED);
	}

	public static class ImportHolidayCsvArgument extends ServiceArgument {
		public String holidayCalendarId;
		public String format;
		public String csvData;
	}

	public static class ImportHolidayCsvResult extends ServiceResult {
		private int importedCount;
		private int skippedCount;
		private int totalCount;

		public ImportHolidayCsvResult() {
			super();
		}

		public ImportHolidayCsvResult(ServiceResultState state) {
			super(state);
		}

		public int getImportedCount() {
			return importedCount;
		}

		public void setImportedCount(int importedCount) {
			this.importedCount = importedCount;
		}

		public int getSkippedCount() {
			return skippedCount;
		}

		public void setSkippedCount(int skippedCount) {
			this.skippedCount = skippedCount;
		}

		public int getTotalCount() {
			return totalCount;
		}

		public void setTotalCount(int totalCount) {
			this.totalCount = totalCount;
		}
	}
}
