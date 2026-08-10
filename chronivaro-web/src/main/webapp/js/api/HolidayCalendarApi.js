import Rest from '../utils/Rest.js';

export default class HolidayCalendarApi {
	static async createCalendar(calendar) {
		return await Rest.post('rest/chronivaro/v1/admin/holiday-calendars', calendar);
	}
	static async createHoliday(calendarId, holiday) {
		return await Rest.post(`rest/chronivaro/v1/admin/holiday-calendars/${calendarId}/holidays`, holiday);
	}
}
