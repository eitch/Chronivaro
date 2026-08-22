import Rest from '../utils/Rest.js';

export default class WorkEntryApi {

    static async startTimer(workingLocation) {
        return await Rest.post('rest/chronivaro/v1/me/timer/start', {workingLocation});
    }

    static async stopTimer(comment) {
        return await Rest.post('rest/chronivaro/v1/me/timer/stop', comment ? {comment} : {});
    }

    static async updateWorkEntry(id, data, etag) {
        const headers = etag ? {'If-Match': etag} : {};
        return await Rest.put(`rest/chronivaro/v1/me/work-entries/${id}`, data, headers);
    }

    static async adminUpdateWorkEntry(id, data, etag) {
        const headers = etag ? {'If-Match': etag} : {};
        return await Rest.put(`rest/chronivaro/v1/admin/work-entries/${id}`, data, headers);
    }

    static async adminDeleteWorkEntry(id) {
        return await Rest.delete(`rest/chronivaro/v1/admin/work-entries/${id}`);
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
}
