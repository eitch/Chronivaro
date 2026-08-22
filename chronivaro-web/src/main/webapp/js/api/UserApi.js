import Rest from '../utils/Rest.js';

export default class UserApi {
    static async getAll(query = null, offset = null, limit = null) {
        const params = new URLSearchParams();
        if (query) params.append('query', query);
        if (offset !== null && offset !== undefined) params.append('offset', offset);
        if (limit !== null && limit !== undefined) params.append('limit', limit);
        const q = params.toString();
        const url = `rest/chronivaro/v1/admin/users${q ? `?${q}` : ''}`;
        return await Rest.get(url);
    }

    static async getById(userId) {
        return await Rest.get(`rest/chronivaro/v1/admin/users/${encodeURIComponent(userId)}`);
    }

    static async create(user) {
        return await Rest.post('rest/chronivaro/v1/admin/users', user);
    }

    static async update(userId, user) {
        return await Rest.put(`rest/chronivaro/v1/admin/users/${encodeURIComponent(userId)}`, user);
    }

    static async initiateRegistration(userId) {
        return await Rest.post(`rest/chronivaro/v1/admin/users/${encodeURIComponent(userId)}/register`, {});
    }

    static async getUsers() {
        const result = await Rest.get('rest/strolch/privilege/users');
        return result.data;
    }

    static async setUserPassword(userId, password) {
        password = btoa(password);
        await Rest.put(`rest/strolch/privilege/users/${userId}/password`, {
            password
        });
    }
}
