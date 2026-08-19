import Rest from '../utils/Rest.js';

export default class ConfigurationApi {

	static async getConfiguration() {
		return await Rest.get('rest/chronivaro/v1/admin/configuration');
	}

	static async updateConfiguration(config, currentVersion) {
		const headers = {};
		if (currentVersion !== undefined && currentVersion !== null) {
			headers['If-Match'] = `"${currentVersion}"`;
		}
		return await Rest.put('rest/chronivaro/v1/admin/configuration', config, headers);
	}
}
