import Rest from '../utils/Rest.js';

export default class ScheduleTemplateApi {
    static async getAll() {
        return await Rest.get('rest/chronivaro/v1/admin/schedule-templates');
    }

    static async create(template) {
        return await Rest.post('rest/chronivaro/v1/admin/schedule-templates', template);
    }

    static async update(template) {
        return await Rest.put(`rest/chronivaro/v1/admin/schedule-templates/${template.id}`, template);
    }

    static async remove(id) {
        return await Rest.delete(`rest/chronivaro/v1/admin/schedule-templates/${id}`);
    }
}
