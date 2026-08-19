import Rest from '../utils/Rest.js';

export default class ReportApi {

	static async getDayReport(date, employeeId) {
		let url = `/chronivaro/v1/reports/day?date=${encodeURIComponent(date)}&format=json`;
		if (employeeId && employeeId.trim()) {
			url += `&employeeId=${encodeURIComponent(employeeId.trim())}`;
		}
		return Rest.get(url);
	}

	static async downloadDayReportCsv(date, employeeId) {
		let url = `/chronivaro/v1/reports/day?date=${encodeURIComponent(date)}&format=csv`;
		if (employeeId && employeeId.trim()) {
			url += `&employeeId=${encodeURIComponent(employeeId.trim())}`;
		}
		const blob = await Rest.getBlob(url, {'Accept': 'text/csv'});
		const filename = `day-report-${date}${employeeId ? '-' + employeeId : ''}.csv`;
		ReportApi.triggerBlobDownload(blob, filename);
	}

	static async getMonthReport(yearMonth, employeeId) {
		let url = `/chronivaro/v1/reports/month?yearMonth=${encodeURIComponent(yearMonth)}&format=json`;
		if (employeeId && employeeId.trim()) {
			url += `&employeeId=${encodeURIComponent(employeeId.trim())}`;
		}
		return Rest.get(url);
	}

	static async downloadMonthReportCsv(yearMonth, employeeId) {
		let url = `/chronivaro/v1/reports/month?yearMonth=${encodeURIComponent(yearMonth)}&format=csv`;
		if (employeeId && employeeId.trim()) {
			url += `&employeeId=${encodeURIComponent(employeeId.trim())}`;
		}
		const blob = await Rest.getBlob(url, {'Accept': 'text/csv'});
		const filename = `month-report-${yearMonth}${employeeId ? '-' + employeeId : ''}.csv`;
		ReportApi.triggerBlobDownload(blob, filename);
	}

	static async getVacationReport(year, employeeId) {
		let url = `/chronivaro/v1/reports/vacation?format=json`;
		if (year) {
			url += `&year=${encodeURIComponent(year)}`;
		}
		if (employeeId && employeeId.trim()) {
			url += `&employeeId=${encodeURIComponent(employeeId.trim())}`;
		}
		return Rest.get(url);
	}

	static async downloadVacationReportCsv(year, employeeId) {
		let url = `/chronivaro/v1/reports/vacation?format=csv`;
		if (year) {
			url += `&year=${encodeURIComponent(year)}`;
		}
		if (employeeId && employeeId.trim()) {
			url += `&employeeId=${encodeURIComponent(employeeId.trim())}`;
		}
		const blob = await Rest.getBlob(url, {'Accept': 'text/csv'});
		const filename = `vacation-report-${year || new Date().getFullYear()}${employeeId ? '-' + employeeId : ''}.csv`;
		ReportApi.triggerBlobDownload(blob, filename);
	}

	static async getTeamReport(teamId, yearMonth) {
		const url = `/chronivaro/v1/reports/team?teamId=${encodeURIComponent(teamId)}&yearMonth=${encodeURIComponent(yearMonth)}&format=json`;
		return Rest.get(url);
	}

	static async downloadTeamReportCsv(teamId, yearMonth) {
		const url = `/chronivaro/v1/reports/team?teamId=${encodeURIComponent(teamId)}&yearMonth=${encodeURIComponent(yearMonth)}&format=csv`;
		const blob = await Rest.getBlob(url, {'Accept': 'text/csv'});
		const filename = `team-report-${teamId}-${yearMonth}.csv`;
		ReportApi.triggerBlobDownload(blob, filename);
	}

	static async getAbsenceReport({from, to, employeeId, type, state}) {
		let queryParts = ['format=json'];
		if (from) queryParts.push(`from=${encodeURIComponent(from)}`);
		if (to) queryParts.push(`to=${encodeURIComponent(to)}`);
		if (employeeId && employeeId.trim()) queryParts.push(`employeeId=${encodeURIComponent(employeeId.trim())}`);
		if (type && type.trim()) queryParts.push(`type=${encodeURIComponent(type.trim())}`);
		if (state && state.trim()) queryParts.push(`state=${encodeURIComponent(state.trim())}`);

		const url = `/chronivaro/v1/reports/absences?${queryParts.join('&')}`;
		return Rest.get(url);
	}

	static async downloadAbsenceReportCsv({from, to, employeeId, type, state}) {
		let queryParts = ['format=csv'];
		if (from) queryParts.push(`from=${encodeURIComponent(from)}`);
		if (to) queryParts.push(`to=${encodeURIComponent(to)}`);
		if (employeeId && employeeId.trim()) queryParts.push(`employeeId=${encodeURIComponent(employeeId.trim())}`);
		if (type && type.trim()) queryParts.push(`type=${encodeURIComponent(type.trim())}`);
		if (state && state.trim()) queryParts.push(`state=${encodeURIComponent(state.trim())}`);

		const url = `/chronivaro/v1/reports/absences?${queryParts.join('&')}`;
		const blob = await Rest.getBlob(url, {'Accept': 'text/csv'});
		const filename = `absence-report-${from || 'all'}-to-${to || 'all'}.csv`;
		ReportApi.triggerBlobDownload(blob, filename);
	}

	static triggerBlobDownload(blob, filename) {
		const url = window.URL.createObjectURL(blob);
		const a = document.createElement('a');
		a.style.display = 'none';
		a.href = url;
		a.download = filename;
		document.body.appendChild(a);
		a.click();
		setTimeout(() => {
			document.body.removeChild(a);
			window.URL.revokeObjectURL(url);
		}, 100);
	}
}
