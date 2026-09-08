import VersionApi from '../api/VersionApi.js';
import I18n from '../i18n/I18n.js';

export default class VersionView {

	constructor(app) {
		this.app = app;
		this.versionData = null;
	}

	async render() {
		const container = document.createElement('div');
		container.id = 'version-view';
		container.className = 'page-container';
		container.innerHTML = `
			<div class="page-header" style="margin-bottom: 2rem;">
				<h2 style="margin: 0 0 0.5rem 0;">${I18n.t('version.title')}</h2>
				<p class="text-muted" style="margin: 0;">${I18n.t('version.subtitle')}</p>
			</div>

			<div class="grid-2-col" style="display: grid; grid-template-columns: repeat(auto-fit, minmax(320px, 1fr)); gap: 1.5rem; max-width: 900px;">
				<div class="card" style="padding: 1.5rem;">
					<h3 style="margin-top: 0; margin-bottom: 1.25rem; color: var(--primary-color); display: flex; align-items: center; gap: 0.5rem;">
						<span aria-hidden="true">📦</span> ${I18n.t('version.appVersion')}
					</h3>
					<div style="display: flex; flex-direction: column; gap: 0.75rem;">
						<div style="display: flex; justify-content: space-between; border-bottom: 1px solid var(--border-color); padding-bottom: 0.5rem;">
							<span class="text-muted">${I18n.t('version.artifact')}:</span>
							<strong id="app-artifact-id">-</strong>
						</div>
						<div style="display: flex; justify-content: space-between; border-bottom: 1px solid var(--border-color); padding-bottom: 0.5rem;">
							<span class="text-muted">${I18n.t('version.version')}:</span>
							<strong id="app-artifact-version">-</strong>
						</div>
						<div style="display: flex; justify-content: space-between; padding-bottom: 0.25rem;">
							<span class="text-muted">${I18n.t('version.groupId')}:</span>
							<span id="app-group-id">-</span>
						</div>
					</div>
				</div>

				<div class="card" style="padding: 1.5rem;">
					<h3 style="margin-top: 0; margin-bottom: 1.25rem; color: var(--primary-color); display: flex; align-items: center; gap: 0.5rem;">
						<span aria-hidden="true">⚙️</span> ${I18n.t('version.agentVersion')}
					</h3>
					<div style="display: flex; flex-direction: column; gap: 0.75rem;">
						<div style="display: flex; justify-content: space-between; border-bottom: 1px solid var(--border-color); padding-bottom: 0.5rem;">
							<span class="text-muted">${I18n.t('version.artifact')}:</span>
							<strong id="agent-artifact-id">-</strong>
						</div>
						<div style="display: flex; justify-content: space-between; border-bottom: 1px solid var(--border-color); padding-bottom: 0.5rem;">
							<span class="text-muted">${I18n.t('version.version')}:</span>
							<strong id="agent-artifact-version">-</strong>
						</div>
						<div style="display: flex; justify-content: space-between; border-bottom: 1px solid var(--border-color); padding-bottom: 0.5rem;">
							<span class="text-muted">${I18n.t('version.environment')}:</span>
							<span id="agent-environment">-</span>
						</div>
						<div style="display: flex; justify-content: space-between; border-bottom: 1px solid var(--border-color); padding-bottom: 0.5rem;">
							<span class="text-muted">${I18n.t('version.timezone')}:</span>
							<span id="agent-timezone">-</span>
						</div>
						<div style="display: flex; justify-content: space-between; padding-bottom: 0.25rem;">
							<span class="text-muted">${I18n.t('version.groupId')}:</span>
							<span id="agent-group-id">-</span>
						</div>
					</div>
				</div>
			</div>
		`;

		this.loadVersion(container);
		return container;
	}

	async loadVersion(container) {
		try {
			const data = await VersionApi.getVersion();
			this.versionData = data;

			if (data.appVersion) {
				const appArtifact = container.querySelector('#app-artifact-id');
				const appVersion = container.querySelector('#app-artifact-version');
				const appGroup = container.querySelector('#app-group-id');
				if (appArtifact) appArtifact.textContent = data.appVersion.artifactId || '-';
				if (appVersion) appVersion.textContent = data.appVersion.artifactVersion || '-';
				if (appGroup) appGroup.textContent = data.appVersion.groupId || '-';
			}

			if (data.agentVersion) {
				const agentArtifact = container.querySelector('#agent-artifact-id');
				const agentVersion = container.querySelector('#agent-artifact-version');
				const agentEnv = container.querySelector('#agent-environment');
				const agentTz = container.querySelector('#agent-timezone');
				const agentGroup = container.querySelector('#agent-group-id');
				if (agentArtifact) agentArtifact.textContent = data.agentVersion.artifactId || '-';
				if (agentVersion) agentVersion.textContent = data.agentVersion.artifactVersion || '-';
				if (agentEnv) agentEnv.textContent = data.agentVersion.environment || '-';
				if (agentTz) agentTz.textContent = data.agentVersion.timezone || '-';
				if (agentGroup) agentGroup.textContent = data.agentVersion.groupId || '-';
			}
		} catch (error) {
			console.error('Failed to load version details:', error);
		}
	}
}
