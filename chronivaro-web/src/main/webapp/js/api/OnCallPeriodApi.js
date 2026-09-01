import Rest from '../utils/Rest.js';

export default class OnCallPeriodApi {

    static async getAdminOnCallPeriods(params = {}) {
        const queryParams = new URLSearchParams();
        if (params.employeeId) queryParams.append('employeeId', params.employeeId);
        if (params.from) queryParams.append('from', params.from);
        if (params.to) queryParams.append('to', params.to);

        const query = queryParams.toString();
        const url = `rest/chronivaro/v1/admin/on-call-periods${query ? '?' + query : ''}`;
        const response = await Rest.get(url);
        return Array.isArray(response) ? response : (response.data || []);
    }

    static async getMyOnCallPeriods(params = {}) {
        const queryParams = new URLSearchParams();
        if (params.from) queryParams.append('from', params.from);
        if (params.to) queryParams.append('to', params.to);

        const query = queryParams.toString();
        const url = `rest/chronivaro/v1/me/on-call-periods${query ? '?' + query : ''}`;
        const response = await Rest.get(url);
        return Array.isArray(response) ? response : (response.data || []);
    }

    static async getEmployeeOnCallPeriods(employeeId, params = {}) {
        const queryParams = new URLSearchParams();
        if (params.from) queryParams.append('from', params.from);
        if (params.to) queryParams.append('to', params.to);

        const query = queryParams.toString();
        const url = `rest/chronivaro/v1/employees/${encodeURIComponent(employeeId)}/on-call-periods${query ? '?' + query : ''}`;
        const response = await Rest.get(url);
        return Array.isArray(response) ? response : (response.data || []);
    }

    static async createOnCallPeriod(data) {
        return await Rest.post('rest/chronivaro/v1/admin/on-call-periods', data);
    }

    static async updateOnCallPeriod(id, data, version) {
        const headers = version !== undefined && version !== null ? { 'If-Match': `"${version}"` } : {};
        return await Rest.put(`rest/chronivaro/v1/admin/on-call-periods/${id}`, data, headers);
    }

    static async deleteOnCallPeriod(id, version) {
        const headers = version !== undefined && version !== null ? { 'If-Match': `"${version}"` } : {};
        return await Rest.delete(`rest/chronivaro/v1/admin/on-call-periods/${id}`, headers);
    }
}
