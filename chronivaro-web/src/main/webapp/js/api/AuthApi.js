import Rest from '../utils/Rest.js';

export default class AuthApi {

	static async login(username, password) {
		password = btoa(password);
		const result = await Rest.post('rest/strolch/authentication', {
			username,
			password
		});
		localStorage.setItem('authToken', result.authToken);
		localStorage.setItem('roles', JSON.stringify(result.roles || []));
		localStorage.setItem('username', result.username || username);
		if (result.userId) {
			localStorage.setItem('userId', result.userId);
		} else {
			localStorage.setItem('userId', result.username || username);
		}
		if (result.firstname) {
			localStorage.setItem('firstname', result.firstname);
		} else {
			localStorage.removeItem('firstname');
		}
		if (result.lastname) {
			localStorage.setItem('lastname', result.lastname);
		} else {
			localStorage.removeItem('lastname');
		}
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

	static async changePassword(newPassword) {
		const userId = this.getUserId();
		if (!userId) {
			throw new Error('User not logged in');
		}
		const password = btoa(newPassword);
		return await Rest.put(`rest/strolch/privilege/users/${encodeURIComponent(userId)}/password`, {
			password
		});
	}

	static logout() {
		localStorage.removeItem('authToken');
		localStorage.removeItem('roles');
		localStorage.removeItem('userLocale');
		localStorage.removeItem('username');
		localStorage.removeItem('userId');
		localStorage.removeItem('firstname');
		localStorage.removeItem('lastname');
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

	static getUsername() {
		return localStorage.getItem('username') || '';
	}

	static getUserId() {
		return localStorage.getItem('userId') || this.getUsername();
	}

	static getFirstname() {
		return localStorage.getItem('firstname') || '';
	}

	static getLastname() {
		return localStorage.getItem('lastname') || '';
	}

	static getFullName() {
		const first = this.getFirstname().trim();
		const last = this.getLastname().trim();
		if (first && last) return `${first} ${last}`;
		return first || last || '';
	}

	static getUser() {
		return {
			userId: this.getUserId(),
			username: this.getUsername(),
			firstname: this.getFirstname(),
			lastname: this.getLastname(),
			fullName: this.getFullName(),
			roles: this.getRoles()
		};
	}

	static hasRole(role) {
		return this.getRoles().includes(role);
	}
}
