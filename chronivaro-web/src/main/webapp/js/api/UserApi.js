import Rest from '../utils/Rest.js';

export default class UserApi {
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
