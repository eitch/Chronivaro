import Rest from '../utils/Rest.js';

export default class ApprovalsApi {

	static async getSubmittedPeriods(params = {}) {
		const queryParams = new URLSearchParams();
		if (params.teamId) queryParams.set('teamId', params.teamId);
		if (params.employeeId) queryParams.set('employeeId', params.employeeId);
		if (params.yearMonth) queryParams.set('yearMonth', params.yearMonth);
		if (params.offset !== undefined && params.offset !== null) queryParams.set('offset', params.offset);
		if (params.limit !== undefined && params.limit !== null) queryParams.set('limit', params.limit);

		const queryString = queryParams.toString();
		const url = queryString ? `rest/chronivaro/v1/approvals/periods?${queryString}` : 'rest/chronivaro/v1/approvals/periods';
		return await Rest.get(url);
	}

	static async getSubmittedPeriodDetail(periodId) {
		return await Rest.get(`rest/chronivaro/v1/approvals/periods/${encodeURIComponent(periodId)}`);
	}

	static async approvePeriod(periodId, comment = null, etag = null) {
		const headers = etag ? {'If-Match': etag} : {};
		const body = comment !== null && comment !== undefined ? {comment} : {};
		return await Rest.post(`rest/chronivaro/v1/approvals/periods/${encodeURIComponent(periodId)}/approve`, body, headers);
	}

	static async rejectPeriod(periodId, comment, etag = null) {
		const headers = etag ? {'If-Match': etag} : {};
		const body = {comment};
		return await Rest.post(`rest/chronivaro/v1/approvals/periods/${encodeURIComponent(periodId)}/reject`, body, headers);
	}

	static async getSubmittedAbsences(params = {}) {
		const queryParams = new URLSearchParams();
		if (params.teamId) queryParams.set('teamId', params.teamId);
		if (params.employeeId) queryParams.set('employeeId', params.employeeId);
		if (params.absenceTypeCode) queryParams.set('absenceTypeCode', params.absenceTypeCode);
		if (params.from) queryParams.set('from', params.from);
		if (params.to) queryParams.set('to', params.to);
		if (params.offset !== undefined && params.offset !== null) queryParams.set('offset', params.offset);
		if (params.limit !== undefined && params.limit !== null) queryParams.set('limit', params.limit);

		const queryString = queryParams.toString();
		const url = queryString ? `rest/chronivaro/v1/approvals/absences?${queryString}` : 'rest/chronivaro/v1/approvals/absences';
		return await Rest.get(url);
	}

	static async approveAbsence(absenceId, etag = null) {
		const headers = etag ? {'If-Match': etag} : {};
		return await Rest.post(`rest/chronivaro/v1/approvals/absences/${encodeURIComponent(absenceId)}/approve`, {}, headers);
	}

	static async rejectAbsence(absenceId, comment, etag = null) {
		const headers = etag ? {'If-Match': etag} : {};
		const body = {comment};
		return await Rest.post(`rest/chronivaro/v1/approvals/absences/${encodeURIComponent(absenceId)}/reject`, body, headers);
	}
}
