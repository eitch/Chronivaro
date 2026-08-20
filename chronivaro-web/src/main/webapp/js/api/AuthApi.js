import Rest from '../utils/Rest.js';

export default class AuthApi {

	static async login(username, password) {
		password = btoa(password);
		const result = await Rest.post('rest/strolch/authentication', {
			username,
			password
		});
		localStorage.setItem('authToken', result.authToken);
		localStorage.setItem('roles', JSON.stringify(result.roles));
		if (result.locale) {
			localStorage.setItem('userLocale', result.locale);
		}
		return result;
	}

	static async completeRegistration(username, challenge, password) {
		return await Rest.post('rest/chronivaro/v1/complete-registration', {
			username,
			challenge,
			password
		});
	}

	static logout() {
		localStorage.removeItem('authToken');
		localStorage.removeItem('roles');
		localStorage.removeItem('userLocale');
		// We could call the logout endpoint too if needed
	}

	static isLoggedIn() {
		return !!localStorage.getItem('authToken');
	}

	static getRoles() {
		const roles = localStorage.getItem('roles');
		return roles ? JSON.parse(roles) : [];
	}

	static getUserLocale() {
		return localStorage.getItem('userLocale');
	}

	static hasRole(role) {
		return this.getRoles().includes(role);
	}
}
