package ch.eitchnet.chronivaro.core.holiday;

import java.util.List;

public interface HolidayCsvParser {

	String getFormatId();

	String getFormatName();

	List<ParsedHoliday> parse(String csvContent);
}
