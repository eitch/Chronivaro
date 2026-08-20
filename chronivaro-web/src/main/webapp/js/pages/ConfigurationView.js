import ConfigurationApi from '../api/ConfigurationApi.js';
import NotificationDialog from '../utils/NotificationDialog.js';

export default class ConfigurationView {

	constructor(app) {
		this.app = app;
		this.currentVersion = null;
		this.configuration = null;
	}

	async render() {
		const container = document.createElement('div');
		container.id = 'configuration-view';
		container.innerHTML = `
			<div class="view-header">
				<h2>System Configuration</h2>
				<p class="subtitle">Manage global parameters for weekly target hours, vacation calculation rules, and default absence codes.</p>
			</div>

			<div class="configuration-container">
				<div class="card config-card">
					<div class="card-header">
						<h3>Global Settings</h3>
						<div class="config-metadata" id="config-metadata">
							<span class="badge" id="config-version-badge">Version: -</span>
							<span class="badge secondary" id="config-updated-by-badge">Updated by: -</span>
						</div>
					</div>

					<form id="configuration-form" class="config-form">
						<div class="form-group">
							<label for="config-company-name">Company Name *:</label>
							<input type="text" id="config-company-name" placeholder="Chronivaro" required>
							<small class="form-hint">Global company name displayed across all views, headers, and reports.</small>
						</div>

						<div class="form-group">
							<label for="config-company-logo">Company Logo URL / Data URI (Optional):</label>
							<input type="text" id="config-company-logo" placeholder="https://example.com/logo.png or data:image/png;base64,...">
							<small class="form-hint">Optional URL, relative asset path, or base64 data URI for the global company logo.</small>
							<div class="config-logo-preview" id="config-logo-preview-box" style="display: none;">
								<img id="config-logo-preview" src="" alt="Logo Preview">
							</div>
						</div>

						<div class="form-group">
							<label for="config-default-language">Default Language *:</label>
							<select id="config-default-language" required>
								<option value="de">Deutsch (German)</option>
								<option value="en">English</option>
							</select>
							<small class="form-hint">System-wide default language used when no user preference is set.</small>
						</div>

						<div class="form-group">
							<label for="config-weekly-target">Standard Weekly Target (Minutes) *:</label>
							<input type="number" id="config-weekly-target" min="0" max="10080" required>
							<small class="form-hint" id="weekly-target-hint">Default full-time weekly working time in minutes (e.g. 2520 = 42h).</small>
						</div>

						<div class="form-group">
							<label for="config-vacation-days">Annual Vacation Entitlement (Days) *:</label>
							<input type="number" id="config-vacation-days" min="0" max="365" required>
							<small class="form-hint">Standard annual vacation allowance in full days (e.g. 25 days).</small>
						</div>

						<div class="form-group">
							<label for="config-day-minutes">Minutes per Vacation Day *:</label>
							<input type="number" id="config-day-minutes" min="1" max="1440" required>
							<small class="form-hint" id="day-minutes-hint">Conversion factor between days and minutes (e.g. 480 min = 8h/day).</small>
						</div>

						<div class="form-group">
							<label for="config-vacation-code">Vacation Absence Type Code *:</label>
							<input type="text" id="config-vacation-code" placeholder="VACATION" required>
							<small class="form-hint">Technical code used to identify vacation absence requests.</small>
						</div>

						<div class="form-actions">
							<button type="submit" id="save-config-btn" class="primary-btn">Save Configuration</button>
							<button type="button" id="reload-config-btn" class="secondary-btn">Reload</button>
						</div>
					</form>
				</div>
			</div>
		`;

		this.container = container;
		this.form = container.querySelector('#configuration-form');
		this.companyNameInput = container.querySelector('#config-company-name');
		this.companyLogoInput = container.querySelector('#config-company-logo');
		this.defaultLanguageSelect = container.querySelector('#config-default-language');
		this.logoPreview = container.querySelector('#config-logo-preview');
		this.logoPreviewBox = container.querySelector('#config-logo-preview-box');
		this.weeklyTargetInput = container.querySelector('#config-weekly-target');
		this.vacationDaysInput = container.querySelector('#config-vacation-days');
		this.dayMinutesInput = container.querySelector('#config-day-minutes');
		this.vacationCodeInput = container.querySelector('#config-vacation-code');
		this.versionBadge = container.querySelector('#config-version-badge');
		this.updatedByBadge = container.querySelector('#config-updated-by-badge');
		this.saveBtn = container.querySelector('#save-config-btn');
		this.reloadBtn = container.querySelector('#reload-config-btn');

		this.companyLogoInput.addEventListener('input', () => this.updateLogoPreview());
		this.weeklyTargetInput.addEventListener('input', () => this.updateHints());
		this.dayMinutesInput.addEventListener('input', () => this.updateHints());

		this.form.addEventListener('submit', (e) => this.handleSave(e));
		this.reloadBtn.addEventListener('click', () => this.loadConfiguration());

		await this.loadConfiguration();
		return container;
	}

	updateLogoPreview() {
		const url = this.companyLogoInput.value.trim();
		if (url) {
			this.logoPreview.src = url;
			this.logoPreviewBox.style.display = 'inline-block';
			this.logoPreview.onerror = () => {
				this.logoPreviewBox.style.display = 'none';
			};
		} else {
			this.logoPreviewBox.style.display = 'none';
			this.logoPreview.removeAttribute('src');
		}
	}

	updateHints() {
		const weeklyMin = parseInt(this.weeklyTargetInput.value, 10);
		const hint1 = this.container.querySelector('#weekly-target-hint');
		if (hint1 && !isNaN(weeklyMin)) {
			const hours = (weeklyMin / 60).toFixed(1);
			hint1.textContent = `Default full-time weekly working time: ${weeklyMin} min (${hours}h/week).`;
		}

		const dayMin = parseInt(this.dayMinutesInput.value, 10);
		const hint2 = this.container.querySelector('#day-minutes-hint');
		if (hint2 && !isNaN(dayMin)) {
			const hours = (dayMin / 60).toFixed(1);
			hint2.textContent = `Conversion factor: ${dayMin} min = ${hours}h/day.`;
		}
	}

	async loadConfiguration() {
		try {
			const config = await ConfigurationApi.getConfiguration();
			this.configuration = config;
			this.currentVersion = config.version;

			this.companyNameInput.value = config.companyName || 'Chronivaro';
			this.companyLogoInput.value = config.companyLogo || '';
			this.defaultLanguageSelect.value = config.defaultLanguage || 'de';
			this.weeklyTargetInput.value = config.weeklyTargetMinutes != null ? config.weeklyTargetMinutes : 2520;
			this.vacationDaysInput.value = config.annualVacationDays != null ? config.annualVacationDays : 25;
			this.dayMinutesInput.value = config.minutesPerVacationDay != null ? config.minutesPerVacationDay : 480;
			this.vacationCodeInput.value = config.vacationAbsenceTypeCode || 'VACATION';

			this.versionBadge.textContent = `Version: ${config.version != null ? config.version : 1}`;
			this.updatedByBadge.textContent = `Updated by: ${config.updatedBy || 'system'}`;

			this.updateLogoPreview();
			this.updateHints();
		} catch (err) {
			console.error('Failed to load configuration', err);
			NotificationDialog.error('Could not load global configuration: ' + err.message);
		}
	}

	async handleSave(e) {
		e.preventDefault();

		const companyName = this.companyNameInput.value.trim();
		const companyLogo = this.companyLogoInput.value.trim();
		const defaultLanguage = this.defaultLanguageSelect.value;
		const weeklyTargetMinutes = parseInt(this.weeklyTargetInput.value, 10);
		const annualVacationDays = parseInt(this.vacationDaysInput.value, 10);
		const minutesPerVacationDay = parseInt(this.dayMinutesInput.value, 10);
		const vacationAbsenceTypeCode = this.vacationCodeInput.value.trim();

		if (!companyName) {
			NotificationDialog.error('Company name cannot be empty.');
			return;
		}

		if (defaultLanguage !== 'de' && defaultLanguage !== 'en') {
			NotificationDialog.error('Default language must be either "de" or "en".');
			return;
		}

		if (isNaN(weeklyTargetMinutes) || weeklyTargetMinutes < 0 || weeklyTargetMinutes > 10080) {
			NotificationDialog.error('Weekly target minutes must be between 0 and 10080 (168h).');
			return;
		}

		if (isNaN(annualVacationDays) || annualVacationDays < 0 || annualVacationDays > 365) {
			NotificationDialog.error('Annual vacation days must be between 0 and 365.');
			return;
		}

		if (isNaN(minutesPerVacationDay) || minutesPerVacationDay <= 0 || minutesPerVacationDay > 1440) {
			NotificationDialog.error('Minutes per vacation day must be between 1 and 1440 (24h).');
			return;
		}

		if (!vacationAbsenceTypeCode) {
			NotificationDialog.error('Vacation absence type code cannot be empty.');
			return;
		}

		const payload = {
			companyName,
			companyLogo,
			defaultLanguage,
			weeklyTargetMinutes,
			annualVacationDays,
			minutesPerVacationDay,
			vacationAbsenceTypeCode
		};

		this.saveBtn.disabled = true;
		this.saveBtn.textContent = 'Saving...';

		try {
			const updated = await ConfigurationApi.updateConfiguration(payload, this.currentVersion);
			this.configuration = updated;
			this.currentVersion = updated.version;

			this.versionBadge.textContent = `Version: ${updated.version != null ? updated.version : 1}`;
			this.updatedByBadge.textContent = `Updated by: ${updated.updatedBy || 'system'}`;

			if (this.app && typeof this.app.updateBranding === 'function') {
				this.app.updateBranding(updated);
			}

			NotificationDialog.info('Global configuration updated successfully.');
		} catch (err) {
			console.error('Failed to update configuration', err);
			NotificationDialog.error('Failed to update configuration: ' + err.message);
		} finally {
			this.saveBtn.disabled = false;
			this.saveBtn.textContent = 'Save Configuration';
		}
	}
}
