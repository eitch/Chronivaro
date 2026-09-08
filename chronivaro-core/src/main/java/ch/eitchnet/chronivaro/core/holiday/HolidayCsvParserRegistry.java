package ch.eitchnet.chronivaro.core.holiday;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class HolidayCsvParserRegistry {

	private static final Map<String, HolidayCsvParser> PARSERS = new ConcurrentHashMap<>();

	static {
		register(new FcalChDeHolidayParser());
	}

	public static void register(HolidayCsvParser parser) {
		PARSERS.put(parser.getFormatId(), parser);
	}

	public static Optional<HolidayCsvParser> getParser(String formatId) {
		if (formatId == null) {
			return Optional.empty();
		}
		return Optional.ofNullable(PARSERS.get(formatId));
	}

	public static List<HolidayCsvFormatDto> getAvailableFormats() {
		return PARSERS.values().stream()
				.map(p -> new HolidayCsvFormatDto(p.getFormatId(), p.getFormatName()))
				.toList();
	}

	public record HolidayCsvFormatDto(String id, String name) {
	}
}
