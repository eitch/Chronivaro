import Rest from '../utils/Rest.js';

export default class ScheduleApi {
    static async getAll(employeeId) {
        return await Rest.get(`rest/chronivaro/v1/admin/employees/${employeeId}/schedules`);
    }

    static async create(employeeId, schedule) {
        return await Rest.post(`rest/chronivaro/v1/admin/employees/${employeeId}/schedules`, schedule);
    }

    static async update(employeeId, schedule) {
        return await Rest.put(`rest/chronivaro/v1/admin/employees/${employeeId}/schedules/${schedule.id}`, schedule);
    }

    static async remove(employeeId, scheduleId) {
        return await Rest.delete(`rest/chronivaro/v1/admin/employees/${employeeId}/schedules/${scheduleId}`);
    }
}
