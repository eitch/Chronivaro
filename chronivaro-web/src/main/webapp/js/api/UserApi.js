import Rest from '../utils/Rest.js';

export default class UserApi {
    static async getUsers() {
        const result = await Rest.get('rest/strolch/privilege/users');
        return result.data;
    }
}
