import AuthApi from '../api/AuthApi.js';
import VersionApi from '../api/VersionApi.js';
import I18n from '../i18n/I18n.js';

export default class LoginView {

    constructor(app) {
        this.app = app;
    }

    render() {
        const container = document.createElement('div');
        container.id = 'login-view';
        const currentLang = I18n.getLanguage();

        container.innerHTML = `
			<form id="login-form">
				<div class="login-header-row" style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 2rem;">
					<h2 style="margin: 0; font-size: 1.5rem; color: var(--primary-color);">${I18n.t('auth.loginTitle')}</h2>
					<select id="login-language-select" class="language-select" aria-label="${I18n.t('auth.language')}">
						<option value="de" ${currentLang === 'de' ? 'selected' : ''}>DE</option>
						<option value="en" ${currentLang === 'en' ? 'selected' : ''}>EN</option>
					</select>
				</div>
				<div class="form-group">
					<label for="username">${I18n.t('auth.username')}</label>
					<input type="text" id="username" required>
				</div>
				<div class="form-group">
					<label for="password">${I18n.t('auth.password')}</label>
					<input type="password" id="password" required>
				</div>
				<div id="login-error" class="error" style="display: none;"></div>
				<button type="submit">
					<span class="spinner" style="display: none;"></span>
					<span class="button-text">${I18n.t('auth.loginButton')}</span>
				</button>
				<div style="margin-top: 1rem; text-align: center;">
					<a href="#complete-registration">${I18n.t('auth.completeRegistrationTitle')}</a>
				</div>
				<div id="login-app-version" style="margin-top: 1.5rem; text-align: center; font-size: 0.75rem; color: var(--text-muted);"></div>
			</form>
		`;

        const form = container.querySelector('#login-form');
        const errorDiv = container.querySelector('#login-error');
        const langSelect = container.querySelector('#login-language-select');
        const versionDiv = container.querySelector('#login-app-version');
        const spinner = container.querySelector('.spinner');
        const buttons = container.querySelectorAll('button');

        VersionApi.getVersion().then(data => {
            if (data && data.appVersion && data.appVersion.artifactVersion) {
                versionDiv.textContent = `v${data.appVersion.artifactVersion}`;
            }
        }).catch(err => {
            console.debug('Failed to load version for login screen:', err);
        });

        if (langSelect) {
            langSelect.addEventListener('change', async (e) => {
                await I18n.setLanguage(e.target.value, true);
            });
        }

        form.addEventListener('submit', async (e) => {
            e.preventDefault();
            errorDiv.style.display = 'none';
            errorDiv.textContent = '';

            const username = form.username.value;
            const password = form.password.value;

            buttons.forEach(btn => btn.disabled = true);
            if (spinner) {
                spinner.style.display = 'inline-block';
            }

            try {
                const result = await AuthApi.login(username, password);
                if (result && result.locale) {
                    await I18n.init({ userLocale: result.locale });
                }
                const activeLang = I18n.getLanguage();
                if (activeLang) {
                    await AuthApi.updateLanguage(activeLang);
                }
                this.app.navigate('');
            } catch (err) {
                errorDiv.textContent = err.message || I18n.t('auth.invalidCredentials');
                errorDiv.style.display = 'block';
            } finally {
                buttons.forEach(btn => btn.disabled = false);
                if (spinner) {
                    spinner.style.display = 'none';
                }
            }
        });

        return container;
    }
}
