
import Rest from '../utils/Rest.js';

export default class AuthApi {

	static async login(username, password) {
		password = btoa(password);
		const result = await Rest.post('rest/strolch/authentication', {
			username,
			password
		});
		localStorage.setItem('authToken', result.authToken);
		return result;
	}

	static logout() {
		localStorage.removeItem('authToken');
		// We could call the logout endpoint too if needed
	}

	static isLoggedIn() {
		return !!localStorage.getItem('authToken');
	}
}
