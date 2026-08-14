import AuthApi from '../api/AuthApi.js';

export default class CompleteRegistrationView {

    constructor(app) {
        this.app = app;
    }

    async render(params) {
        const container = document.createElement('div');
        container.id = 'complete-registration-view';
        
        const username = params.username || '';
        const challenge = params.challenge || '';

        container.innerHTML = `
			<form id="complete-registration-form">
				<h2>Complete Registration</h2>
				<p>Please enter your challenge code and choose a new password.</p>
				<div class="form-group">
					<label for="username">Username</label>
					<input type="text" id="username" value="${username}" required ${username ? 'readonly' : ''}>
				</div>
				<div class="form-group">
					<label for="challenge">Challenge Code</label>
					<input type="text" id="challenge" value="${challenge}" required>
				</div>
				<div class="form-group">
					<label for="password">New Password</label>
					<input type="password" id="password" required>
				</div>
				<div class="form-group">
					<label for="password-confirm">Confirm Password</label>
					<input type="password" id="password-confirm" required>
				</div>
				<div id="registration-error" class="error" style="display: none;"></div>
				<div id="registration-success" class="success" style="display: none;">
					Registration complete! You can now <a href="#login">login</a>.
				</div>
				<button type="submit">Complete Registration</button>
			</form>
		`;

        const form = container.querySelector('#complete-registration-form');
        const errorDiv = container.querySelector('#registration-error');
        const successDiv = container.querySelector('#registration-success');

        form.addEventListener('submit', async (e) => {
            e.preventDefault();
            errorDiv.style.display = 'none';
            
            const usernameVal = form.username.value;
            const challengeVal = form.challenge.value;
            const password = form.password.value;
            const passwordConfirm = form.querySelector('#password-confirm').value;

            if (password !== passwordConfirm) {
                errorDiv.textContent = 'Passwords do not match!';
                errorDiv.style.display = 'block';
                return;
            }

            try {
                // Complete registration in one call
                await AuthApi.completeRegistration(usernameVal, challengeVal, password);
                
                // Success!
                form.style.display = 'none';
                successDiv.style.display = 'block';

                setTimeout(() => {
                    this.app.navigate('login');
                }, 3000);
                
            } catch (err) {
                errorDiv.textContent = err.message || 'An error occurred during registration completion.';
                errorDiv.style.display = 'block';
            }
        });

        return container;
    }
}
