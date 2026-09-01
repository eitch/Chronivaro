import Rest from '../utils/Rest.js';

export default class AbsenceApi {

    static async getMyAbsences(params = {}) {
        const queryParams = new URLSearchParams();
        if (params.from) queryParams.append('from', params.from);
        if (params.to) queryParams.append('to', params.to);
        if (params.status) queryParams.append('status', params.status);
        if (params.absenceTypeCode) queryParams.append('absenceTypeCode', params.absenceTypeCode);
        if (params.offset !== undefined) queryParams.append('offset', params.offset);
        if (params.limit !== undefined) queryParams.append('limit', params.limit);

        const query = queryParams.toString();
        const url = `rest/chronivaro/v1/me/absences${query ? '?' + query : ''}`;
        const response = await Rest.get(url);
        return Array.isArray(response) ? response : (response.data || []);
    }

    static async getEmployeeAbsences(employeeId, params = {}) {
        const queryParams = new URLSearchParams();
        if (params.from) queryParams.append('from', params.from);
        if (params.to) queryParams.append('to', params.to);
        if (params.status) queryParams.append('status', params.status);
        if (params.absenceTypeCode) queryParams.append('absenceTypeCode', params.absenceTypeCode);
        if (params.offset !== undefined) queryParams.append('offset', params.offset);
        if (params.limit !== undefined) queryParams.append('limit', params.limit);

        const query = queryParams.toString();
        const url = `rest/chronivaro/v1/employees/${encodeURIComponent(employeeId)}/absences${query ? '?' + query : ''}`;
        const response = await Rest.get(url);
        return Array.isArray(response) ? response : (response.data || []);
    }

    static async createEmployeeAbsence(employeeId, absence) {
        return await Rest.post(`rest/chronivaro/v1/employees/${encodeURIComponent(employeeId)}/absences`, absence);
    }

    static async getAbsenceTypes() {
        const response = await Rest.get('rest/chronivaro/v1/admin/absence-types');
        return Array.isArray(response) ? response : (response.data || []);
    }

    static async requestAbsence(absence) {
        return await Rest.post('rest/chronivaro/v1/me/absences', absence);
    }

    static async updateAbsence(id, absence, version) {
        const headers = version !== undefined && version !== null ? { 'If-Match': `"${version}"` } : {};
        return await Rest.put(`rest/chronivaro/v1/me/absences/${id}`, absence, headers);
    }

    static async submitAbsence(id, version) {
        const headers = version !== undefined && version !== null ? { 'If-Match': `"${version}"` } : {};
        return await Rest.post(`rest/chronivaro/v1/me/absences/${id}/submit`, {}, headers);
    }

    static async getAbsence(id) {
        return await Rest.get(`rest/chronivaro/v1/me/absences/${id}`);
    }

    static async cancelAbsence(id, version, reason = '') {
        const headers = version !== undefined && version !== null ? { 'If-Match': `"${version}"` } : {};
        return await Rest.post(`rest/chronivaro/v1/me/absences/${id}/cancel`, { reason }, headers);
    }

    static async getAbsencesReport({from, to, employeeId, teamId, type, state} = {}) {
        let queryParts = ['format=json'];
        if (from) queryParts.push(`from=${encodeURIComponent(from)}`);
        if (to) queryParts.push(`to=${encodeURIComponent(to)}`);
        if (employeeId && employeeId.trim()) queryParts.push(`employeeId=${encodeURIComponent(employeeId.trim())}`);
        if (teamId && teamId.trim()) queryParts.push(`teamId=${encodeURIComponent(teamId.trim())}`);
        if (type && type.trim()) queryParts.push(`absenceTypeCode=${encodeURIComponent(type.trim())}`);
        if (state && state.trim()) queryParts.push(`status=${encodeURIComponent(state.trim())}`);

        const url = `rest/chronivaro/v1/reports/absences?${queryParts.join('&')}`;
        const response = await Rest.get(url);
        return Array.isArray(response) ? response : (response.items || []);
    }
}
