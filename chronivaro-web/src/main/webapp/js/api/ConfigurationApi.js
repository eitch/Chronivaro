import Rest from '../utils/Rest.js';

export default class ConfigurationApi {

	static async getBranding() {
		return await Rest.get('rest/chronivaro/v1/system/branding');
	}

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

	static async uploadLogo(logoData) {
		const payload = typeof logoData === 'string' ? { companyLogo: logoData } : logoData;
		return await Rest.post('rest/chronivaro/v1/admin/configuration/logo', payload);
	}

	static async deleteLogo() {
		return await Rest.delete('rest/chronivaro/v1/admin/configuration/logo');
	}
}
