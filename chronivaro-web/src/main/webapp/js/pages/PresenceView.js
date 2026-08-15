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
			<h2>Who is working?</h2>
			<div class="filters">
				<label>Team: <select id="team-filter"><option value="">All</option></select></label>
				<label>Location: <select id="location-filter"><option value="">All</option></select></label>
			</div>
			<div id="presence-list" class="presence-list">
				<p>Loading presence data...</p>
			</div>
		`;

		const teamFilter = container.querySelector('#team-filter');
		const locationFilter = container.querySelector('#location-filter');
		const presenceList = container.querySelector('#presence-list');

		const loadFilters = async () => {
			const teams = await TeamApi.getAll();
			teams.forEach(t => {
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

				if (presenceInfos.length === 0) {
					presenceList.innerHTML = '<p>No employees found matching the filters.</p>';
					return;
				}

				presenceList.innerHTML = '';
				presenceInfos.forEach(info => {
					const card = document.createElement('div');
					card.className = 'presence-card';
					
					let statusClass = info.status === 'WORKING' ? 'status-working' : 'status-not-working';
					let statusText = info.status === 'WORKING' ? 'WORKING' : 'NOT WORKING';
					
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
					presenceList.appendChild(card);
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
