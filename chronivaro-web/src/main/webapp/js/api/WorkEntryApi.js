
import Rest from '../utils/Rest.js';

export default class WorkEntryApi {

	static async startTimer() {
		return await Rest.post('rest/chronivaro/v1/me/timer/start');
	}

	static async stopTimer() {
		return await Rest.post('rest/chronivaro/v1/me/timer/stop');
	}

	static async getDaySummary(date) {
		const dateStr = date.toISOString().split('T')[0];
		return await Rest.get(`rest/chronivaro/v1/me/day-summary/${dateStr}`);
	}

	static async getMyWorkEntries(from, to) {
		const fromStr = encodeURIComponent(from.toISOString());
		const toStr = encodeURIComponent(to.toISOString());
		return await Rest.get(`rest/chronivaro/v1/me/work-entries?from=${fromStr}&to=${toStr}`);
	}
}
