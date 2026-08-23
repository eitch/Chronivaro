import AuthApi from '../api/AuthApi.js';
import I18n from '../i18n/I18n.js';

export default class CompleteRegistrationView {

    constructor(app) {
        this.app = app;
    }

    async render(params) {
        const container = document.createElement('div');
        container.id = 'complete-registration-view';
        const currentLang = I18n.getLanguage();
        
        const username = params.username || '';
        const challenge = params.challenge || '';

        container.innerHTML = `
			<form id="complete-registration-form">
				<div class="login-header-row" style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 1.5rem;">
					<h2 style="margin: 0; font-size: 1.5rem; color: var(--primary-color);">${I18n.t('auth.completeRegistrationTitle')}</h2>
					<select id="complete-reg-language-select" class="language-select" aria-label="${I18n.t('auth.language')}">
						<option value="de" ${currentLang === 'de' ? 'selected' : ''}>DE</option>
						<option value="en" ${currentLang === 'en' ? 'selected' : ''}>EN</option>
					</select>
				</div>
				<p>${I18n.t('auth.choosePasswordPrompt')}</p>
				<div class="form-group">
					<label for="username">${I18n.t('auth.username')}</label>
					<input type="text" id="username" value="${username}" required ${username ? 'readonly' : ''}>
				</div>
				<div class="form-group">
					<label for="challenge">${I18n.t('auth.challengeCode')}</label>
					<input type="text" id="challenge" value="${challenge}" required>
				</div>
				<div class="form-group">
					<label for="password">${I18n.t('auth.newPassword')}</label>
					<input type="password" id="password" required>
				</div>
				<div class="form-group">
					<label for="password-confirm">${I18n.t('auth.confirmPassword')}</label>
					<input type="password" id="password-confirm" required>
				</div>
				<div id="registration-error" class="error" style="display: none;"></div>
				<button type="submit">${I18n.t('auth.setPassword')}</button>
			</form>
			<div id="registration-success" class="success" style="display: none;">
				${I18n.t('auth.registrationSuccess')} <a href="#login">${I18n.t('auth.loginLinkText')}</a>.
			</div>
		`;

        const form = container.querySelector('#complete-registration-form');
        const errorDiv = container.querySelector('#registration-error');
        const successDiv = container.querySelector('#registration-success');
        const langSelect = container.querySelector('#complete-reg-language-select');

        if (langSelect) {
            langSelect.addEventListener('change', async (e) => {
                await I18n.setLanguage(e.target.value, true);
            });
        }

        form.addEventListener('submit', async (e) => {
            e.preventDefault();
            errorDiv.style.display = 'none';
            
            const usernameVal = form.username.value;
            const challengeVal = form.challenge.value;
            const password = form.password.value;
            const passwordConfirm = form.querySelector('#password-confirm').value;

            if (password !== passwordConfirm) {
                errorDiv.textContent = I18n.t('auth.passwordMismatch');
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
                errorDiv.textContent = err.message || I18n.t('errors.unexpected');
                errorDiv.style.display = 'block';
            }
        });

        return container;
    }
}
