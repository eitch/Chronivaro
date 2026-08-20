import ConfigurationApi from '../api/ConfigurationApi.js';
import NotificationDialog from '../utils/NotificationDialog.js';
import I18n from '../utils/I18n.js';

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
				<h2>${I18n.t('configuration.title')}</h2>
				<p class="subtitle">${I18n.t('configuration.subtitle')}</p>
			</div>

			<div class="configuration-container">
				<div class="card config-card">
					<div class="card-header">
						<h3>${I18n.t('configuration.globalSettings')}</h3>
						<div class="config-metadata" id="config-metadata">
							<span class="badge" id="config-version-badge">${I18n.t('configuration.version')}: -</span>
							<span class="badge secondary" id="config-updated-by-badge">${I18n.t('configuration.updatedBy')}: -</span>
						</div>
					</div>

					<form id="configuration-form" class="config-form">
						<div class="form-group">
							<label for="config-company-name">${I18n.t('configuration.companyName')} *:</label>
							<input type="text" id="config-company-name" placeholder="Chronivaro" required>
							<small class="form-hint">${I18n.t('configuration.companyNameHint')}</small>
						</div>

						<div class="form-group">
							<label for="config-company-logo">${I18n.t('configuration.companyLogo')}:</label>
							<input type="text" id="config-company-logo" placeholder="https://example.com/logo.png or data:image/png;base64,...">
							<small class="form-hint">${I18n.t('configuration.companyLogoHint')}</small>
							<div class="config-logo-preview" id="config-logo-preview-box" style="display: none;">
								<img id="config-logo-preview" src="" alt="Logo Preview">
							</div>
						</div>

						<div class="form-group">
							<label for="config-default-language">${I18n.t('configuration.defaultLanguage')} *:</label>
							<select id="config-default-language" required>
								<option value="de">${I18n.t('configuration.langDe')}</option>
								<option value="en">${I18n.t('configuration.langEn')}</option>
							</select>
							<small class="form-hint">${I18n.t('configuration.defaultLanguageHint')}</small>
						</div>

						<div class="form-group">
							<label for="config-weekly-target">${I18n.t('configuration.weeklyTargetMinutes')} *:</label>
							<input type="number" id="config-weekly-target" min="0" max="10080" required>
							<small class="form-hint" id="weekly-target-hint">${I18n.t('configuration.weeklyTargetHint')}</small>
						</div>

						<div class="form-group">
							<label for="config-vacation-days">${I18n.t('configuration.annualVacationDays')} *:</label>
							<input type="number" id="config-vacation-days" min="0" max="365" required>
							<small class="form-hint">${I18n.t('configuration.annualVacationDaysHint')}</small>
						</div>

						<div class="form-group">
							<label for="config-day-minutes">${I18n.t('configuration.minutesPerVacationDay')} *:</label>
							<input type="number" id="config-day-minutes" min="1" max="1440" required>
							<small class="form-hint" id="day-minutes-hint">${I18n.t('configuration.dayMinutesHint')}</small>
						</div>

						<div class="form-group">
							<label for="config-vacation-code">${I18n.t('configuration.vacationAbsenceTypeCode')} *:</label>
							<input type="text" id="config-vacation-code" placeholder="VACATION" required>
							<small class="form-hint">${I18n.t('configuration.vacationCodeHint')}</small>
						</div>

						<div class="form-actions">
							<button type="submit" id="save-config-btn" class="primary-btn">${I18n.t('configuration.saveConfig')}</button>
							<button type="button" id="reload-config-btn" class="secondary-btn">${I18n.t('configuration.reloadConfig')}</button>
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
			hint1.textContent = I18n.t('configuration.weeklyTargetHintDynamic', { minutes: weeklyMin, hours });
		}

		const dayMin = parseInt(this.dayMinutesInput.value, 10);
		const hint2 = this.container.querySelector('#day-minutes-hint');
		if (hint2 && !isNaN(dayMin)) {
			const hours = (dayMin / 60).toFixed(1);
			hint2.textContent = I18n.t('configuration.dayMinutesHintDynamic', { minutes: dayMin, hours });
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

			this.versionBadge.textContent = `${I18n.t('configuration.version')}: ${config.version != null ? config.version : 1}`;
			this.updatedByBadge.textContent = `${I18n.t('configuration.updatedBy')}: ${config.updatedBy || 'system'}`;

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
			NotificationDialog.error(I18n.t('configuration.companyNameEmpty'));
			return;
		}

		if (defaultLanguage !== 'de' && defaultLanguage !== 'en') {
			NotificationDialog.error(I18n.t('configuration.invalidLanguage'));
			return;
		}

		if (isNaN(weeklyTargetMinutes) || weeklyTargetMinutes < 0 || weeklyTargetMinutes > 10080) {
			NotificationDialog.error(I18n.t('configuration.invalidWeeklyTarget'));
			return;
		}

		if (isNaN(annualVacationDays) || annualVacationDays < 0 || annualVacationDays > 365) {
			NotificationDialog.error(I18n.t('configuration.invalidVacationDays'));
			return;
		}

		if (isNaN(minutesPerVacationDay) || minutesPerVacationDay <= 0 || minutesPerVacationDay > 1440) {
			NotificationDialog.error(I18n.t('configuration.invalidDayMinutes'));
			return;
		}

		if (!vacationAbsenceTypeCode) {
			NotificationDialog.error(I18n.t('configuration.vacationCodeEmpty'));
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
		this.saveBtn.textContent = I18n.t('configuration.saving');

		try {
			const updated = await ConfigurationApi.updateConfiguration(payload, this.currentVersion);
			this.configuration = updated;
			this.currentVersion = updated.version;

			this.versionBadge.textContent = `${I18n.t('configuration.version')}: ${updated.version != null ? updated.version : 1}`;
			this.updatedByBadge.textContent = `${I18n.t('configuration.updatedBy')}: ${updated.updatedBy || 'system'}`;

			if (this.app && typeof this.app.updateBranding === 'function') {
				this.app.updateBranding(updated);
			}

			NotificationDialog.info(I18n.t('configuration.saveSuccess'));
		} catch (err) {
			console.error('Failed to update configuration', err);
			NotificationDialog.error(I18n.t('configuration.saveError') + ' ' + err.message);
		} finally {
			this.saveBtn.disabled = false;
			this.saveBtn.textContent = I18n.t('configuration.saveConfig');
		}
	}
}
