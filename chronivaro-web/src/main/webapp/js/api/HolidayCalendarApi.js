import Rest from '../utils/Rest.js';

export default class HolidayCalendarApi {
    static async getCalendars() {
        return await Rest.get('rest/chronivaro/v1/admin/holiday-calendars');
    }

    static async getCalendar(id) {
        return await Rest.get(`rest/chronivaro/v1/admin/holiday-calendars/${id}`);
    }

    static async getHolidays(calendarId) {
        return await Rest.get(`rest/chronivaro/v1/admin/holiday-calendars/${calendarId}/holidays`);
    }

    static async createCalendar(calendar) {
        return await Rest.post('rest/chronivaro/v1/admin/holiday-calendars', calendar);
    }

    static async createHoliday(calendarId, holiday) {
        return await Rest.post(`rest/chronivaro/v1/admin/holiday-calendars/${calendarId}/holidays`, holiday);
    }

    static async deleteCalendar(id) {
        return await Rest.delete(`rest/chronivaro/v1/admin/holiday-calendars/${id}`);
    }

    static async deleteHoliday(calendarId, holidayId) {
        return await Rest.delete(`rest/chronivaro/v1/admin/holiday-calendars/${calendarId}/holidays/${holidayId}`);
    }
}
