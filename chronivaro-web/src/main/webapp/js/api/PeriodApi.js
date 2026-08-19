import Rest from '../utils/Rest.js';

export default class PeriodApi {

    static async getMyPeriodStatus(yearMonth) {
        return await Rest.get(`rest/chronivaro/v1/me/periods/${encodeURIComponent(yearMonth)}`);
    }

    static async submitMyPeriod(yearMonth, comment = null, etag = null) {
        const headers = etag ? {'If-Match': etag} : {};
        const body = comment !== null && comment !== undefined ? {comment} : {};
        return await Rest.post(`rest/chronivaro/v1/me/periods/${encodeURIComponent(yearMonth)}/submit`, body, headers);
    }

    static async getMonthSummary(yearMonth) {
        return await Rest.get(`rest/chronivaro/v1/me/month-summary/${encodeURIComponent(yearMonth)}`);
    }

    static async getPeriodStatus(yearMonth, employeeId = null) {
        let url = `rest/chronivaro/v1/periods/status?yearMonth=${encodeURIComponent(yearMonth)}`;
        if (employeeId) {
            url += `&employeeId=${encodeURIComponent(employeeId)}`;
        }
        return await Rest.get(url);
    }
}
