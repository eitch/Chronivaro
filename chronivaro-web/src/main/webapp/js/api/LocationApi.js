import Rest from '../utils/Rest.js';

export default class LocationApi {
	static async getAll() {
		return await Rest.get('rest/chronivaro/v1/admin/locations');
	}
	static async create(location) {
		return await Rest.post('rest/chronivaro/v1/admin/locations', location);
	}
	static async update(location) {
		return await Rest.put(`rest/chronivaro/v1/admin/locations/${location.id}`, location);
	}
	static async remove(id) {
		return await Rest.delete(`rest/chronivaro/v1/admin/locations/${id}`);
	}
}
