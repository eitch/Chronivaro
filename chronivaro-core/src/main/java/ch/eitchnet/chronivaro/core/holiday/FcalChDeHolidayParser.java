package ch.eitchnet.chronivaro.core.holiday;

import li.strolch.utils.helper.StringHelper;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

public class FcalChDeHolidayParser implements HolidayCsvParser {

	public static final String FORMAT_ID = "fcal.ch DE";
	public static final String FORMAT_NAME = "fcal.ch DE";

	private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");

	@Override
	public String getFormatId() {
		return FORMAT_ID;
	}

	@Override
	public String getFormatName() {
		return FORMAT_NAME;
	}

	@Override
	public List<ParsedHoliday> parse(String csvContent) {
		if (csvContent == null || csvContent.isBlank()) {
			return List.of();
		}

		String cleanContent = csvContent;
		if (cleanContent.startsWith("\uFEFF")) {
			cleanContent = cleanContent.substring(1);
		}

		String[] lines = cleanContent.split("\\r?\\n");
		List<ParsedHoliday> holidays = new ArrayList<>();

		for (String rawLine : lines) {
			String line = rawLine.trim();
			if (line.isEmpty() || line.startsWith("#")) {
				continue;
			}

			List<String> tokens = parseSemicolonDelimitedLine(line);
			if (tokens.isEmpty()) {
				continue;
			}

			// Check if header line (e.g. "Datum";"Bezeichnung";...)
			String firstToken = tokens.getFirst().trim();
			if (firstToken.equalsIgnoreCase("Datum") || firstToken.equalsIgnoreCase("\"Datum\"")) {
				continue;
			}

			if (tokens.size() < 2) {
				throw new IllegalArgumentException("Invalid line format, expected at least 2 columns: " + line);
			}

			String dateStr = tokens.get(0).trim();
			String nameStr = tokens.get(1).trim();

			if (dateStr.isEmpty() || nameStr.isEmpty()) {
				continue;
			}

			LocalDate date;
			try {
				date = LocalDate.parse(dateStr, DATE_FORMATTER);
			} catch (DateTimeParseException e) {
				throw new IllegalArgumentException("Invalid date '" + dateStr + "' in line: " + line, e);
			}

			holidays.add(new ParsedHoliday(date, nameStr, 1.0));
		}

		return List.copyOf(holidays);
	}

	private List<String> parseSemicolonDelimitedLine(String line) {
		List<String> tokens = new ArrayList<>();
		StringBuilder sb = new StringBuilder();
		boolean inQuotes = false;

		for (int i = 0; i < line.length(); i++) {
			char c = line.charAt(i);
			if (c == '\"') {
				if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '\"') {
					sb.append('\"');
					i++;
				} else {
					inQuotes = !inQuotes;
				}
			} else if (c == ';' && !inQuotes) {
				tokens.add(sb.toString().trim());
				sb.setLength(0);
			} else {
				sb.append(c);
			}
		}
		tokens.add(sb.toString().trim());

		return tokens;
	}
}
