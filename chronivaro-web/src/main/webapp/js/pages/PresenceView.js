import PresenceApi from '../api/PresenceApi.js';
import TeamApi from '../api/TeamApi.js';
import LocationApi from '../api/LocationApi.js';
import Format from '../utils/Format.js';

export default class PresenceView {

	constructor(app) {
		this.app = app;
	}

	async render(params) {
		const container = document.createElement('div');
		container.id = 'presence-view';
		container.innerHTML = `
			<div class="view-header">
				<h2>Who is working?</h2>
				<button id="settings-toggle" class="icon-button" title="Settings">⚙️</button>
			</div>
			<div id="settings-menu" class="settings-menu hidden">
				<div class="filters">
					<label>Team: <select id="team-filter"><option value="">All</option></select></label>
					<label>Location: <select id="location-filter"><option value="">All</option></select></label>
				</div>
			</div>
			<div id="presence-list" class="presence-list">
				<p>Loading presence data...</p>
			</div>
		`;

		const settingsToggle = container.querySelector('#settings-toggle');
		const settingsMenu = container.querySelector('#settings-menu');
		const teamFilter = container.querySelector('#team-filter');
		const locationFilter = container.querySelector('#location-filter');
		const presenceList = container.querySelector('#presence-list');

		settingsToggle.addEventListener('click', () => {
			settingsMenu.classList.toggle('hidden');
		});

		let allTeams = [];
		const loadFilters = async () => {
			allTeams = await TeamApi.getAll();
			allTeams.forEach(t => {
				const opt = document.createElement('option');
				opt.value = t.id;
				opt.textContent = t.name;
				if (params.teamId === t.id) opt.selected = true;
				teamFilter.appendChild(opt);
			});

			const locations = await LocationApi.getAll();
			locations.forEach(l => {
				const opt = document.createElement('option');
				opt.value = l.id;
				opt.textContent = l.name;
				if (params.locationId === l.id) opt.selected = true;
				locationFilter.appendChild(opt);
			});
		};

		const refresh = async () => {
			try {
				const teamId = teamFilter.value;
				const locationId = locationFilter.value;
				const presenceInfos = await PresenceApi.getPresence(teamId, locationId);

				if (presenceInfos.length === 0 && allTeams.length === 0) {
					presenceList.innerHTML = '<p>No employees found matching the filters.</p>';
					return;
				}

				const teamsToShow = teamId ? allTeams.filter(t => t.id === teamId) : allTeams;

				presenceList.innerHTML = '';
				teamsToShow.forEach(team => {
					const teamGroup = document.createElement('div');
					teamGroup.className = 'presence-team-group';
					teamGroup.innerHTML = `<h3>${team.name}</h3>`;
					
					const teamEmployees = presenceInfos.filter(info => info.teamId === team.id);
					if (teamEmployees.length === 0) {
						const noEmployees = document.createElement('p');
						noEmployees.className = 'no-employees';
						noEmployees.textContent = 'No employees present.';
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
							let statusText = info.statusLabel;
							
							let extraInfo = '';
							if (info.absenceTypeCode) {
								extraInfo = `<span class="presence-absence">(${info.absenceTypeName})</span>`;
							} else if (info.isOff) {
								extraInfo = '<span class="presence-off">(Off-duty)</span>';
							}

							card.innerHTML = `
								<div class="presence-info">
									<span class="presence-name">${info.firstname} ${info.lastname}</span>
									<span class="presence-status ${statusClass}">${statusText}</span>
									${extraInfo}
								</div>
								<div class="presence-stats">
									Today: ${Format.duration(info.minutesToday)}
								</div>
							`;
							teamGroup.appendChild(card);
						});
					}
					presenceList.appendChild(teamGroup);
				});
			} catch (err) {
				console.error(err);
				presenceList.innerHTML = '<p class="error">Failed to load presence data.</p>';
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
