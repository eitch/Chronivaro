import Rest from '../utils/Rest.js';

export default class EmployeeApi {
    static async getAll() {
        return await Rest.get('rest/chronivaro/v1/admin/employees');
    }

    static async get(id) {
        return await Rest.get(`rest/chronivaro/v1/admin/employees/${id}`);
    }

    static async create(employee) {
        return await Rest.post('rest/chronivaro/v1/admin/employees', employee);
    }

    static async update(employee) {
        return await Rest.put(`rest/chronivaro/v1/admin/employees/${employee.id}`, employee);
    }

    static async remove(id) {
        return await Rest.delete(`rest/chronivaro/v1/admin/employees/${id}`);
    }
}
