import Rest from '../utils/Rest.js';

export default class VersionApi {

	static async getVersion() {
		return await Rest.get('rest/strolch/version');
	}
}
