import Rest from '../utils/Rest.js';

export default class AbsenceTypeApi {
    static async getAll() {
        return await Rest.get('rest/chronivaro/v1/admin/absence-types');
    }

    static async create(absenceType) {
        return await Rest.post('rest/chronivaro/v1/admin/absence-types', absenceType);
    }

    static async update(absenceType) {
        return await Rest.put(`rest/chronivaro/v1/admin/absence-types/${absenceType.id}`, absenceType);
    }

    static async remove(id) {
        return await Rest.delete(`rest/chronivaro/v1/admin/absence-types/${id}`);
    }
}
