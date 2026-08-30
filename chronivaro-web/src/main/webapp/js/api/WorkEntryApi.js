import Rest from '../utils/Rest.js';

export default class WorkEntryApi {

    static async startTimer(workingLocation, isOnCall, comment) {
        return await Rest.post('rest/chronivaro/v1/me/timer/start', {workingLocation, isOnCall, comment});
    }

    static async stopTimer(comment, time) {
        const payload = {};
        if (comment) payload.comment = comment;
        if (time) {
            payload.time = (time instanceof Date) ? time.toISOString() : time;
        }
        return await Rest.post('rest/chronivaro/v1/me/timer/stop', payload);
    }

    static async updateWorkEntry(id, data, etag) {
        const headers = etag ? {'If-Match': etag} : {};
        return await Rest.put(`rest/chronivaro/v1/me/work-entries/${id}`, data, headers);
    }

    static async adminUpdateWorkEntry(id, data, etag) {
        const headers = etag ? {'If-Match': etag} : {};
        return await Rest.put(`rest/chronivaro/v1/admin/work-entries/${id}`, data, headers);
    }

	static async deleteWorkEntry(id, etag) {
		const headers = etag ? {'If-Match': etag} : {};
		return await Rest.delete(`rest/chronivaro/v1/me/work-entries/${id}`, headers);
	}

	static async deleteMyWorkEntry(id, etag) {
		return await this.deleteWorkEntry(id, etag);
	}

	static async adminDeleteWorkEntry(id, etag) {
		const headers = etag ? {'If-Match': etag} : {};
		return await Rest.delete(`rest/chronivaro/v1/admin/work-entries/${id}`, headers);
	}

    static async getDaySummary(date) {
        const dateStr = date.toISOString().split('T')[0];
        return await Rest.get(`rest/chronivaro/v1/me/day-summary/${dateStr}`);
    }

    static async getWorkingLocationDefaults() {
        return await Rest.get('rest/chronivaro/v1/me/working-location-defaults');
    }

    static async getMyWorkEntries(from, to) {
        const fromStr = encodeURIComponent(from.toISOString());
        const toStr = encodeURIComponent(to.toISOString());
        return await Rest.get(`rest/chronivaro/v1/me/work-entries?from=${fromStr}&to=${toStr}`);
    }

    static async getEmployeeWorkEntries(employeeId, from, to) {
        const fromStr = from ? encodeURIComponent(from.toISOString()) : '';
        const toStr = to ? encodeURIComponent(to.toISOString()) : '';
        const query = [];
        if (fromStr) query.push(`from=${fromStr}`);
        if (toStr) query.push(`to=${toStr}`);
        const queryString = query.length > 0 ? `?${query.join('&')}` : '';
        return await Rest.get(`rest/chronivaro/v1/employees/${employeeId}/work-entries${queryString}`);
    }

    static async createEmployeeWorkEntry(employeeId, data) {
        return await Rest.post(`rest/chronivaro/v1/employees/${employeeId}/work-entries`, data);
    }
}
