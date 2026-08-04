
import AuthApi from '../api/AuthApi.js';

export default class LoginView {

	constructor(app) {
		this.app = app;
	}

	render() {
		const container = document.createElement('div');
		container.id = 'login-view';
		container.innerHTML = `
			<form id="login-form">
				<h2>Login to Chronivaro</h2>
				<div class="form-group">
					<label for="username">Username</label>
					<input type="text" id="username" required>
				</div>
				<div class="form-group">
					<label for="password">Password</label>
					<input type="password" id="password" required>
				</div>
				<div id="login-error" class="error" style="display: none;"></div>
				<button type="submit">Login</button>
			</form>
		`;

		const form = container.querySelector('#login-form');
		const errorDiv = container.querySelector('#login-error');

		form.addEventListener('submit', async (e) => {
			e.preventDefault();
			const username = form.username.value;
			const password = form.password.value;

			try {
				await AuthApi.login(username, password);
				this.app.navigate('dashboard');
			} catch (err) {
				errorDiv.textContent = err.message;
				errorDiv.style.display = 'block';
			}
		});

		return container;
	}
}
