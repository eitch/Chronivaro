import AuthApi from '../api/AuthApi.js';
import PresenceApi from '../api/PresenceApi.js';
import TeamApi from '../api/TeamApi.js';
import LocationApi from '../api/LocationApi.js';
import Format from '../utils/Format.js';
import I18n from '../i18n/I18n.js';

export default class PresenceView {

	constructor(app) {
		this.app = app;
	}

	async render(params) {
		const container = document.createElement('div');
		container.id = 'presence-view';
		container.innerHTML = `
			<div class="view-header">
				<h2>${I18n.t('presence.whoIsWorking')}</h2>
				<button id="settings-toggle" class="icon-button" title="${I18n.t('presence.settings')}">⚙️</button>
			</div>
			<div id="settings-menu" class="settings-menu hidden">
				<div class="filters">
					<label>${I18n.t('common.team')}: <select id="team-filter"><option value="">${I18n.t('common.all')}</option></select></label>
					<label>${I18n.t('common.location')}: <select id="location-filter"><option value="">${I18n.t('common.all')}</option></select></label>
				</div>
			</div>
			<div id="presence-list" class="presence-list">
				<p>${I18n.t('common.loading')}</p>
			</div>
		`;

		const isPrivileged = AuthApi.hasRole('Supervisor') || AuthApi.hasRole('HR') || AuthApi.hasRole('Administrator');
		const settingsToggle = container.querySelector('#settings-toggle');
		const settingsMenu = container.querySelector('#settings-menu');
		const teamFilter = container.querySelector('#team-filter');
		const locationFilter = container.querySelector('#location-filter');
		const presenceList = container.querySelector('#presence-list');

		if (!isPrivileged) {
			const teamFilterLabel = teamFilter.closest('label');
			if (teamFilterLabel) teamFilterLabel.style.display = 'none';
		}

		settingsToggle.addEventListener('click', () => {
			settingsMenu.classList.toggle('hidden');
		});

		let allTeams = [];
		const loadFilters = async () => {
			if (isPrivileged) {
				allTeams = await TeamApi.getAll();
				allTeams.forEach(t => {
					const opt = document.createElement('option');
					opt.value = t.id;
					opt.textContent = t.name;
					if (params && params.teamId === t.id) opt.selected = true;
					teamFilter.appendChild(opt);
				});
			}

			const locations = await LocationApi.getAll();
			locations.forEach(l => {
				const opt = document.createElement('option');
				opt.value = l.id;
				opt.textContent = l.name;
				if (params && params.locationId === l.id) opt.selected = true;
				locationFilter.appendChild(opt);
			});
		};

		const refresh = async () => {
			try {
				const teamId = isPrivileged ? teamFilter.value : '';
				const locationId = locationFilter.value;
				const presenceInfos = await PresenceApi.getPresence(teamId, locationId);

				if (presenceInfos.length === 0 && (!isPrivileged || allTeams.length === 0)) {
					presenceList.innerHTML = `<p>${I18n.t('presence.noEmployeesFound')}</p>`;
					return;
				}

				let teamsToShow;
				if (isPrivileged) {
					teamsToShow = teamId ? allTeams.filter(t => t.id === teamId) : allTeams;
				} else {
					teamsToShow = Array.from(new Map(presenceInfos.map(i => [i.teamId, { id: i.teamId, name: i.teamName }])).values());
				}

				presenceList.innerHTML = '';
				teamsToShow.forEach(team => {
					const teamGroup = document.createElement('div');
					teamGroup.className = 'presence-team-group';
					teamGroup.innerHTML = `<h3>${team.name}</h3>`;
					
					const teamEmployees = presenceInfos.filter(info => info.teamId === team.id);
					if (teamEmployees.length === 0) {
						const noEmployees = document.createElement('p');
						noEmployees.className = 'no-employees';
						noEmployees.textContent = I18n.t('presence.noEmployeesPresent');
						teamGroup.appendChild(noEmployees);
					} else {
						teamEmployees.forEach(info => {
							const card = document.createElement('div');
							card.className = 'presence-card';
							
							let statusClass = 'status-not-working';
							if (info.status === 'WORKING') {
								statusClass = 'status-working';
							} else if (info.isOff) {
								statusClass = 'status-off-duty';
							}

							let statusText = info.status === 'WORKING'
								? I18n.t('presence.working')
								: (info.isOff ? I18n.t('dashboard.offDuty') : I18n.t('presence.notWorking'));
							
							let extraInfo = '';
							if (info.absenceTypeCode) {
								extraInfo = `<span class="presence-absence">(${info.absenceTypeName || info.absenceTypeCode})</span>`;
							} else if (info.isOff) {
								extraInfo = `<span class="presence-off">(${I18n.t('dashboard.offDuty')})</span>`;
							}

							const locationText = info.workingLocation 
								? I18n.t(`enums.workingLocation.${info.workingLocation}`, {}, info.workingLocation)
								: '';

							card.innerHTML = `
								<div class="presence-info">
									<span class="presence-name">${info.firstname} ${info.lastname}</span>
									<div class="presence-status-line">
										<span class="presence-status ${statusClass}">${statusText}</span>
										${info.status === 'WORKING' && locationText ? `<span class="presence-working-location">${locationText}</span>` : ''}
									</div>
									${extraInfo}
								</div>
								<div class="presence-stats">
									${I18n.t('presence.todayStats', { time: Format.duration(info.minutesToday) })}
								</div>
							`;
							teamGroup.appendChild(card);
						});
					}
					presenceList.appendChild(teamGroup);
				});
			} catch (err) {
				console.error(err);
				presenceList.innerHTML = `<p class="error">${I18n.t('presence.failedToLoad')}</p>`;
			}
		};

		teamFilter.addEventListener('change', () => {
			this.app.navigate('presence', {
				teamId: teamFilter.value,
				locationId: locationFilter.value
			});
		});
		locationFilter.addEventListener('change', () => {
			this.app.navigate('presence', {
				teamId: teamFilter.value,
				locationId: locationFilter.value
			});
		});

		loadFilters().then(() => refresh());

		return container;
	}
}
