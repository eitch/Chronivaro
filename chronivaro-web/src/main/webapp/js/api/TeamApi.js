import Rest from '../utils/Rest.js';

export default class TeamApi {
    static async getAll() {
        return await Rest.get('rest/chronivaro/v1/admin/teams');
    }

    static async create(team) {
        return await Rest.post('rest/chronivaro/v1/admin/teams', team);
    }

    static async update(team) {
        return await Rest.put(`rest/chronivaro/v1/admin/teams/${team.id}`, team);
    }

    static async remove(id) {
        return await Rest.delete(`rest/chronivaro/v1/admin/teams/${id}`);
    }
}
