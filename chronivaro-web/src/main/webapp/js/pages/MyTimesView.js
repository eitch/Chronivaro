import WorkEntryApi from '../api/WorkEntryApi.js';
import TeamApi from '../api/TeamApi.js';
import EmployeeApi from '../api/EmployeeApi.js';
import AuthApi from '../api/AuthApi.js';
import NotificationDialog from '../utils/NotificationDialog.js';
import Format from '../utils/Format.js';
import I18n from '../i18n/I18n.js';

export default class MyTimesView {

	constructor(app) {
		this.app = app;
		this.teams = [];
		this.employees = [];
		this.selectedTeamId = '';
		this.selectedEmployeeId = '';
		this.currentUserEmployeeId = null;
		this.canManage = AuthApi.hasRole('Supervisor') || AuthApi.hasRole('HR')
				|| AuthApi.hasRole('Administrator') || AuthApi.hasRole('StrolchAdmin');
		this.currentEditingEntry = null;
	}

	async render(params) {
		const container = document.createElement('div');
		container.id = 'my-times-view';

		const now = new Date();
		const firstDay = new Date(now.getFullYear(), now.getMonth(), 1);
		const lastDay = new Date(now.getFullYear(), now.getMonth() + 1, 0);

		const defaultFromStr = firstDay.toISOString().split('T')[0];
		const defaultToStr = lastDay.toISOString().split('T')[0];

		container.innerHTML = `
			<div class="view-header" style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 1rem;">
				<h2 id="times-view-title">${I18n.t('times.title')}</h2>
				${this.canManage ? `
				<button id="btn-add-work-entry" class="primary-btn">
					<span class="icon">➕</span> ${I18n.t('times.addEntry')}
				</button>
				` : ''}
			</div>

			<!-- Filter Controls Section -->
			<section class="card" style="margin-bottom: 1.5rem; padding: 1rem 1.25rem;">
				<div class="filter-bar" id="times-filter-bar" style="display: flex; flex-wrap: wrap; gap: 1rem; align-items: flex-end;">
					${this.canManage ? `
					<div class="filter-group">
						<label for="times-team-filter">${I18n.t('common.team')}:</label>
						<select id="times-team-filter" style="min-width: 160px;">
							<option value="">${I18n.t('common.allTeams')}</option>
						</select>
					</div>
					<div class="filter-group">
						<label for="times-employee-filter">${I18n.t('common.employee')}:</label>
						<select id="times-employee-filter" style="min-width: 200px;">
							<option value="">${I18n.t('common.loading')}</option>
						</select>
					</div>
					` : ''}
					<div class="filter-group">
						<label for="date-from">${I18n.t('common.from')}:</label>
						<input type="date" id="date-from" value="${defaultFromStr}">
					</div>
					<div class="filter-group">
						<label for="date-to">${I18n.t('common.to')}:</label>
						<input type="date" id="date-to" value="${defaultToStr}">
					</div>
					<div class="filter-actions" style="margin-left: auto;">
						<button id="refresh-times" class="secondary-btn">${I18n.t('common.refresh')}</button>
					</div>
				</div>
			</section>

			<!-- Summary Stats Bar -->
			<section class="card summary-card" style="margin-bottom: 1.5rem; padding: 0.75rem 1.25rem; display: flex; gap: 2rem; background-color: var(--card-bg, #ffffff);">
				<div class="stat-item">
					<span class="text-muted" style="font-size: 0.875rem;">${I18n.t('times.entriesCount')}:</span>
					<strong id="stat-entries-count" style="margin-left: 0.5rem; font-size: 1.1rem;">0</strong>
				</div>
				<div class="stat-item">
					<span class="text-muted" style="font-size: 0.875rem;">${I18n.t('times.totalDuration')}:</span>
					<strong id="stat-total-duration" style="margin-left: 0.5rem; font-size: 1.1rem; color: var(--primary-color, #6366f1);">0h 00m</strong>
				</div>
			</section>

			<!-- Work Entries Table -->
			<div class="table-container card" style="padding: 0; overflow-x: auto;">
				<table id="work-entries-table" class="data-table" style="width: 100%; border-collapse: collapse;">
					<thead>
						<tr>
							<th>${I18n.t('times.startTime')}</th>
							<th>${I18n.t('times.endTime')}</th>
							<th>${I18n.t('common.duration')}</th>
							<th>${I18n.t('times.workingLocation')}</th>
							<th>${I18n.t('times.source')}</th>
							<th>${I18n.t('common.comment')}</th>
							<th style="text-align: right;">${I18n.t('common.actions')}</th>
						</tr>
					</thead>
					<tbody id="work-entries-tbody">
						<tr><td colspan="7" style="text-align: center; padding: 2rem;">${I18n.t('common.loading')}</td></tr>
					</tbody>
				</table>
			</div>

			<!-- Add Work Entry Modal (Supervisor / HR / Admin) -->
			<div id="add-work-entry-modal" class="modal" style="display: none; align-items: center; justify-content: center; position: fixed; z-index: 1000; left: 0; top: 0; width: 100%; height: 100%; background-color: rgba(0, 0, 0, 0.5);">
				<div class="modal-content" style="max-width: 500px; width: 90%; background: var(--card-bg, #ffffff); border-radius: 8px; padding: 1.5rem; box-shadow: 0 4px 6px -1px rgba(0,0,0,0.1);">
					<div class="modal-header" style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 1rem; border-bottom: 1px solid var(--border-color, #e2e8f0); padding-bottom: 0.75rem;">
						<h3 style="margin: 0;">${I18n.t('times.addDialogTitle')}</h3>
						<button type="button" id="close-add-modal-icon" class="close-btn" style="background: none; border: none; font-size: 1.5rem; cursor: pointer;">&times;</button>
					</div>
					<form id="add-work-entry-form">
						<div class="form-grid" style="display: grid; grid-template-columns: 1fr 1fr; gap: 1rem;">
							<div class="form-group" style="grid-column: span 2;">
								<label for="add-entry-date" style="display: block; margin-bottom: 0.25rem; font-weight: 500;">${I18n.t('common.date')} *:</label>
								<input type="date" id="add-entry-date" required style="width: 100%; padding: 0.5rem; border: 1px solid var(--border-color, #e2e8f0); border-radius: 4px; box-sizing: border-box;">
							</div>
							<div class="form-group">
								<label for="add-start-time" style="display: block; margin-bottom: 0.25rem; font-weight: 500;">${I18n.t('times.startTime')} * (24h):</label>
								<input type="text" id="add-start-time" required placeholder="08:00" maxlength="5" pattern="^([01]?[0-9]|2[0-3]):[0-5][0-9]$" style="width: 100%; padding: 0.5rem; border: 1px solid var(--border-color, #e2e8f0); border-radius: 4px; box-sizing: border-box;">
							</div>
							<div class="form-group">
								<label for="add-end-time" style="display: block; margin-bottom: 0.25rem; font-weight: 500;">${I18n.t('times.endTime')} * (24h):</label>
								<input type="text" id="add-end-time" required placeholder="17:00" maxlength="5" pattern="^([01]?[0-9]|2[0-3]):[0-5][0-9]$" style="width: 100%; padding: 0.5rem; border: 1px solid var(--border-color, #e2e8f0); border-radius: 4px; box-sizing: border-box;">
							</div>
							<div class="form-group" style="grid-column: span 2;">
								<label style="display: flex; align-items: center; gap: 0.5rem; cursor: pointer; font-weight: 500;">
									<input type="checkbox" id="add-past-midnight">
									<span>${I18n.t('times.pastMidnight')}</span>
								</label>
								<div id="add-end-date-display" style="display: none; font-size: 0.85rem; color: #4b5563; margin-top: 0.25rem;">
									${I18n.t('times.endDate')}: <span id="add-end-date-text" style="font-weight: 600;"></span>
								</div>
							</div>
							<div class="form-group" style="grid-column: span 2;">
								<label for="add-working-location" style="display: block; margin-bottom: 0.25rem; font-weight: 500;">${I18n.t('times.workingLocation')}:</label>
								<select id="add-working-location" style="width: 100%; padding: 0.5rem; border: 1px solid var(--border-color, #e2e8f0); border-radius: 4px; box-sizing: border-box;">
									<option value="">-- ${I18n.t('common.selectOption')} --</option>
									<option value="OFFICE">${I18n.t('enums.workingLocation.OFFICE', {}, 'OFFICE')}</option>
									<option value="HOME">${I18n.t('enums.workingLocation.HOME', {}, 'HOME')}</option>
									<option value="CUSTOMER">${I18n.t('enums.workingLocation.CUSTOMER', {}, 'CUSTOMER')}</option>
									<option value="REMOTE">${I18n.t('enums.workingLocation.REMOTE', {}, 'REMOTE')}</option>
								</select>
							</div>
							<div class="form-group" style="grid-column: span 2;">
								<label style="display: flex; align-items: center; gap: 0.5rem; cursor: pointer; font-weight: 500;">
									<input type="checkbox" id="add-is-on-call">
									<span>${I18n.t('times.onCall')}</span>
								</label>
							</div>
							<div class="form-group" style="grid-column: span 2;">
								<label for="add-comment" style="display: block; margin-bottom: 0.25rem; font-weight: 500;">${I18n.t('common.comment')}:</label>
								<textarea id="add-comment" rows="3" placeholder="${I18n.t('common.comment')}..." style="width: 100%; padding: 0.5rem; border: 1px solid var(--border-color, #e2e8f0); border-radius: 4px; box-sizing: border-box;"></textarea>
							</div>
						</div>
						<div class="modal-actions" style="display: flex; justify-content: flex-end; gap: 0.75rem; margin-top: 1.5rem; border-top: 1px solid var(--border-color, #e2e8f0); padding-top: 1rem;">
							<button type="submit" class="primary-btn">${I18n.t('common.save')}</button>
							<button type="button" id="close-add-modal-btn" class="secondary-btn">${I18n.t('common.cancel')}</button>
						</div>
					</form>
				</div>
			</div>

			<!-- Edit / Shorten Work Entry Modal -->
			<div id="work-entry-modal" class="modal" style="display: none; align-items: center; justify-content: center; position: fixed; z-index: 1000; left: 0; top: 0; width: 100%; height: 100%; background-color: rgba(0, 0, 0, 0.5);">
				<div class="modal-content" style="max-width: 500px; width: 90%; background: var(--card-bg, #ffffff); border-radius: 8px; padding: 1.5rem; box-shadow: 0 4px 6px -1px rgba(0,0,0,0.1);">
					<div class="modal-header" style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 1rem; border-bottom: 1px solid var(--border-color, #e2e8f0); padding-bottom: 0.75rem;">
						<h3 id="work-entry-modal-title">${I18n.t('times.shortenDialogTitle')}</h3>
						<button type="button" id="close-work-entry-modal-icon" class="close-btn" style="background: none; border: none; font-size: 1.5rem; cursor: pointer;">&times;</button>
					</div>
					<p class="help-text" id="edit-help-text" style="margin-bottom: 15px; color: #666; font-size: 0.9em;">
						${I18n.t('times.shortenHelpText')}
					</p>
					<form id="work-entry-form">
						<div class="form-grid" style="display: grid; grid-template-columns: 1fr 1fr; gap: 1rem;">
							<div class="form-group" id="edit-date-group" style="grid-column: span 2;">
								<label for="modal-entry-date" style="display: block; margin-bottom: 0.25rem; font-weight: 500;">${I18n.t('common.date')} *:</label>
								<input type="date" id="modal-entry-date" required style="width: 100%; padding: 0.5rem; border: 1px solid var(--border-color, #e2e8f0); border-radius: 4px; box-sizing: border-box;">
							</div>
							<div class="form-group" id="edit-start-group">
								<label for="modal-start-time" style="display: block; margin-bottom: 0.25rem; font-weight: 500;">${I18n.t('times.startTime')} * (24h):</label>
								<input type="text" id="modal-start-time" required placeholder="08:00" maxlength="5" pattern="^([01]?[0-9]|2[0-3]):[0-5][0-9]$" style="width: 100%; padding: 0.5rem; border: 1px solid var(--border-color, #e2e8f0); border-radius: 4px; box-sizing: border-box;">
							</div>
							<div class="form-group">
								<label for="modal-end-time" id="edit-end-label" style="display: block; margin-bottom: 0.25rem; font-weight: 500;">${I18n.t('times.shortenTime')} * (24h):</label>
								<input type="text" id="modal-end-time" required placeholder="17:00" maxlength="5" pattern="^([01]?[0-9]|2[0-3]):[0-5][0-9]$" style="width: 100%; padding: 0.5rem; border: 1px solid var(--border-color, #e2e8f0); border-radius: 4px; box-sizing: border-box;">
							</div>
							<div class="form-group" id="edit-past-midnight-group" style="grid-column: span 2;">
								<label style="display: flex; align-items: center; gap: 0.5rem; cursor: pointer; font-weight: 500;">
									<input type="checkbox" id="modal-past-midnight">
									<span>${I18n.t('times.pastMidnight')}</span>
								</label>
								<div id="modal-end-date-display" style="display: none; font-size: 0.85rem; color: #4b5563; margin-top: 0.25rem;">
									${I18n.t('times.endDate')}: <span id="modal-end-date-text" style="font-weight: 600;"></span>
								</div>
							</div>
							<div class="form-group" id="edit-location-group" style="grid-column: span 2;">
								<label for="modal-working-location" style="display: block; margin-bottom: 0.25rem; font-weight: 500;">${I18n.t('times.workingLocation')}:</label>
								<select id="modal-working-location" style="width: 100%; padding: 0.5rem; border: 1px solid var(--border-color, #e2e8f0); border-radius: 4px; box-sizing: border-box;">
									<option value="">-- ${I18n.t('common.selectOption')} --</option>
									<option value="OFFICE">${I18n.t('enums.workingLocation.OFFICE', {}, 'OFFICE')}</option>
									<option value="HOME">${I18n.t('enums.workingLocation.HOME', {}, 'HOME')}</option>
									<option value="CUSTOMER">${I18n.t('enums.workingLocation.CUSTOMER', {}, 'CUSTOMER')}</option>
									<option value="REMOTE">${I18n.t('enums.workingLocation.REMOTE', {}, 'REMOTE')}</option>
								</select>
							</div>
							<div class="form-group" style="grid-column: span 2;">
								<label style="display: flex; align-items: center; gap: 0.5rem; cursor: pointer; font-weight: 500;">
									<input type="checkbox" id="modal-is-on-call">
									<span>${I18n.t('times.onCall')}</span>
								</label>
							</div>
							<div class="form-group" style="grid-column: span 2;">
								<label for="modal-comment" style="display: block; margin-bottom: 0.25rem; font-weight: 500;">${I18n.t('common.comment')}:</label>
								<textarea id="modal-comment" rows="3" placeholder="${I18n.t('common.comment')}..." style="width: 100%; padding: 0.5rem; border: 1px solid var(--border-color, #e2e8f0); border-radius: 4px; box-sizing: border-box;"></textarea>
							</div>
						</div>
						<div class="modal-actions" style="display: flex; justify-content: flex-end; gap: 0.75rem; margin-top: 1.5rem; border-top: 1px solid var(--border-color, #e2e8f0); padding-top: 1rem;">
							<button type="submit" class="primary-btn">${I18n.t('common.save')}</button>
							<button type="button" id="close-work-entry-modal-btn" class="secondary-btn">${I18n.t('common.cancel')}</button>
						</div>
					</form>
				</div>
			</div>
		`;

		const fromInput = container.querySelector('#date-from');
		const toInput = container.querySelector('#date-to');
		const refreshBtn = container.querySelector('#refresh-times');
		const tbody = container.querySelector('#work-entries-tbody');
		const statEntriesCount = container.querySelector('#stat-entries-count');
		const statTotalDuration = container.querySelector('#stat-total-duration');
		const titleEl = container.querySelector('#times-view-title');

		// Add Modal elements
		const btnAddEntry = container.querySelector('#btn-add-work-entry');
		const addModal = container.querySelector('#add-work-entry-modal');
		const addModalIcon = container.querySelector('#close-add-modal-icon');
		const addModalBtn = container.querySelector('#close-add-modal-btn');
		const addForm = container.querySelector('#add-work-entry-form');
		const addDateInput = container.querySelector('#add-entry-date');
		const addStartInput = container.querySelector('#add-start-time');
		const addEndInput = container.querySelector('#add-end-time');
		const addPastMidnightCheckbox = container.querySelector('#add-past-midnight');
		const addEndDateDisplay = container.querySelector('#add-end-date-display');
		const addEndDateText = container.querySelector('#add-end-date-text');
		const addLocationSelect = container.querySelector('#add-working-location');
		const addIsOnCallCheckbox = container.querySelector('#add-is-on-call');
		const addCommentInput = container.querySelector('#add-comment');

		// Edit Modal elements
		const editModal = container.querySelector('#work-entry-modal');
		const editModalTitle = container.querySelector('#work-entry-modal-title');
		const editHelpText = container.querySelector('#edit-help-text');
		const editModalIcon = container.querySelector('#close-work-entry-modal-icon');
		const editModalBtn = container.querySelector('#close-work-entry-modal-btn');
		const editForm = container.querySelector('#work-entry-form');
		const editDateInput = container.querySelector('#modal-entry-date');
		const editStartInput = container.querySelector('#modal-start-time');
		const editEndLabel = container.querySelector('#edit-end-label');
		const editEndInput = container.querySelector('#modal-end-time');
		const editPastMidnightCheckbox = container.querySelector('#modal-past-midnight');
		const editEndDateDisplay = container.querySelector('#modal-end-date-display');
		const editEndDateText = container.querySelector('#modal-end-date-text');
		const editLocationSelect = container.querySelector('#modal-working-location');
		const editIsOnCallCheckbox = container.querySelector('#modal-is-on-call');
		const editCommentInput = container.querySelector('#modal-comment');

		const getNextDayString = (dateStr) => {
			if (!dateStr) return '';
			const [y, m, d] = dateStr.split('-').map(Number);
			const date = new Date(y, m - 1, d);
			date.setDate(date.getDate() + 1);
			const pad = (n) => String(n).padStart(2, '0');
			return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
		};

		const updateAddEndDateDisplay = () => {
			if (!addPastMidnightCheckbox || !addEndDateDisplay || !addEndDateText) return;
			if (addPastMidnightCheckbox.checked) {
				const nextDay = getNextDayString(addDateInput.value);
				addEndDateText.textContent = nextDay;
				addEndDateDisplay.style.display = 'block';
			} else {
				addEndDateDisplay.style.display = 'none';
			}
		};

		const updateEditEndDateDisplay = () => {
			if (!editPastMidnightCheckbox || !editEndDateDisplay || !editEndDateText) return;
			if (editPastMidnightCheckbox.checked) {
				const nextDay = getNextDayString(editDateInput.value);
				editEndDateText.textContent = nextDay;
				editEndDateDisplay.style.display = 'block';
			} else {
				editEndDateDisplay.style.display = 'none';
			}
		};

		if (addPastMidnightCheckbox) {
			addPastMidnightCheckbox.addEventListener('change', updateAddEndDateDisplay);
		}
		if (addDateInput) {
			addDateInput.addEventListener('change', updateAddEndDateDisplay);
		}
		if (editPastMidnightCheckbox) {
			editPastMidnightCheckbox.addEventListener('change', updateEditEndDateDisplay);
		}
		if (editDateInput) {
			editDateInput.addEventListener('change', updateEditEndDateDisplay);
		}

		[addStartInput, addEndInput, editStartInput, editEndInput].forEach(inp => {
			if (inp) {
				inp.addEventListener('blur', () => {
					if (inp.value) inp.value = Format.normalizeTime(inp.value);
				});
			}
		});

		// Filter elements (if manage role)
		const teamFilter = container.querySelector('#times-team-filter');
		const employeeFilter = container.querySelector('#times-employee-filter');

		const toLocalDateTimeInputString = (isoStr) => {
			if (!isoStr) return '';
			const d = new Date(isoStr);
			const pad = (n) => String(n).padStart(2, '0');
			return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
		};

		const updateTitle = () => {
			if (!this.canManage || !this.selectedEmployeeId || (this.currentUserEmployeeId && this.selectedEmployeeId === this.currentUserEmployeeId)) {
				titleEl.textContent = I18n.t('times.title');
			} else {
				const emp = this.employees.find(e => e.id === this.selectedEmployeeId);
				const empName = emp ? `${emp.firstname || ''} ${emp.lastname || ''}`.trim() || emp.username : '';
				titleEl.textContent = empName ? `${I18n.t('times.employeeTimesTitle')} - ${empName}` : I18n.t('times.employeeTimesTitle');
			}
		};

		const populateEmployees = () => {
			if (!employeeFilter) return;
			let filtered = this.employees;
			if (this.selectedTeamId) {
				filtered = filtered.filter(e => e.teamId === this.selectedTeamId);
			}

			filtered = [...filtered].sort((a, b) => {
				const nameA = `${a.lastname || ''} ${a.firstname || ''}`.toLowerCase();
				const nameB = `${b.lastname || ''} ${b.firstname || ''}`.toLowerCase();
				return nameA.localeCompare(nameB);
			});

			employeeFilter.innerHTML = '';
			filtered.forEach(emp => {
				const opt = document.createElement('option');
				opt.value = emp.id;
				const name = `${emp.firstname || ''} ${emp.lastname || ''}`.trim() || emp.username || emp.id;
				const persNr = emp.personalNumber ? ` (${emp.personalNumber})` : '';
				opt.textContent = `${name}${persNr}`;
				employeeFilter.appendChild(opt);
			});

			if (this.selectedEmployeeId && filtered.some(e => e.id === this.selectedEmployeeId)) {
				employeeFilter.value = this.selectedEmployeeId;
			} else if (filtered.length > 0) {
				this.selectedEmployeeId = filtered[0].id;
				employeeFilter.value = this.selectedEmployeeId;
			} else {
				this.selectedEmployeeId = '';
			}
			updateTitle();
		};

		const refresh = async () => {
			try {
				tbody.innerHTML = `<tr><td colspan="6" style="text-align: center; padding: 2rem;">${I18n.t('common.loading')}</td></tr>`;

				const from = new Date(fromInput.value);
				const to = new Date(toInput.value);
				to.setHours(23, 59, 59, 999);

				let entries = [];
				const isViewingOther = this.canManage && this.selectedEmployeeId && (this.selectedEmployeeId !== this.currentUserEmployeeId);

				if (isViewingOther) {
					entries = await WorkEntryApi.getEmployeeWorkEntries(this.selectedEmployeeId, from, to);
				} else {
					entries = await WorkEntryApi.getMyWorkEntries(from, to);
				}

				this.workEntries = entries || [];
				tbody.innerHTML = '';

				let totalMinutes = 0;
				this.workEntries.forEach(e => {
					if (e.durationMinutes) totalMinutes += e.durationMinutes;
				});

				statEntriesCount.textContent = String(this.workEntries.length);
				statTotalDuration.textContent = Format.duration(totalMinutes);

				if (this.workEntries.length === 0) {
					tbody.innerHTML = `<tr><td colspan="7" style="text-align: center; padding: 2rem;">${I18n.t('times.noEntries')}</td></tr>`;
					return;
				}

				this.workEntries.forEach(entry => {
					const row = document.createElement('tr');
					const locationText = entry.workingLocation
							? I18n.t(`enums.workingLocation.${entry.workingLocation}`, {}, entry.workingLocation)
							: '';
					const endText = entry.end
							? Format.dateTime(entry.end)
							: `<span class="badge badge-working">${I18n.t('common.running')}</span>`;

					const sourceBadge = entry.source === 'MANUAL'
							? `<span class="badge badge-manual" style="background: #fef3c7; color: #92400e; padding: 2px 6px; border-radius: 4px; font-size: 0.8rem; font-weight: 500; margin-right: 4px;">${I18n.t('times.manualBadge')}</span>`
							: `<span class="badge badge-timer" style="background: #e0e7ff; color: #3730a3; padding: 2px 6px; border-radius: 4px; font-size: 0.8rem; font-weight: 500; margin-right: 4px;">${I18n.t('times.timerBadge')}</span>`;
					const modifiedBadge = entry.modified
							? `<span class="badge badge-modified" style="background: #fed7aa; color: #9a3412; padding: 2px 6px; border-radius: 4px; font-size: 0.8rem; font-weight: 500; margin-right: 4px;">${I18n.t('times.modifiedBadge')}</span>`
							: '';
					const onCallBadge = entry.isOnCall
							? `<span class="badge badge-on-call" style="background: #e0f2fe; color: #0369a1; padding: 2px 6px; border-radius: 4px; font-size: 0.8rem; font-weight: 500; margin-right: 4px;">${I18n.t('times.onCallBadge')}</span>`
							: '';

					const targetEmp = this.employees.find(e => e.id === (this.selectedEmployeeId || entry.employeeId));
					const targetUsername = targetEmp ? targetEmp.username : AuthApi.getUsername();
					const creatorAttr = (entry.createdBy && entry.createdBy !== targetUsername)
							? `<div style="font-size: 0.75rem; color: #6b7280; margin-top: 2px;">${I18n.t('times.createdBy', { user: entry.createdBy })}</div>`
							: '';
					row.innerHTML = `
						<td>${Format.dateTime(entry.start)}</td>
						<td>${endText}</td>
						<td>${Format.duration(entry.durationMinutes)}</td>
						<td>${locationText}</td>
						<td>
							<div>${sourceBadge}${modifiedBadge}${onCallBadge}</div>
							${creatorAttr}
						</td>
						<td>${entry.comment || ''}</td>
						<td style="text-align: right; white-space: nowrap;">
							<button class="secondary-btn edit-entry-btn" style="margin-right: 0.5rem;" title="${I18n.t('times.editEntry')}">${I18n.t('common.edit')}</button>
							<button class="danger-btn delete-entry-btn" title="${I18n.t('times.deleteEntry')}">${I18n.t('common.delete')}</button>
						</td>
					`;

					const editBtn = row.querySelector('.edit-entry-btn');
					if (editBtn) {
						editBtn.addEventListener('click', () => {
							this.currentEditingEntry = entry;

							const isRunning = !entry.end;
							editModalTitle.textContent = I18n.t('times.editDialogTitle');
							editHelpText.textContent = I18n.t('times.editHelpText');

							const startDate = new Date(entry.start);
							const pad = (n) => String(n).padStart(2, '0');
							const dateStr = !isNaN(startDate.getTime())
									? `${startDate.getFullYear()}-${pad(startDate.getMonth() + 1)}-${pad(startDate.getDate())}`
									: '';
							const startTimeStr = !isNaN(startDate.getTime())
									? `${pad(startDate.getHours())}:${pad(startDate.getMinutes())}`
									: '';

							editDateInput.value = dateStr;
							editStartInput.value = startTimeStr;
							editStartInput.removeAttribute('readonly');
							editStartInput.removeAttribute('disabled');
							editStartInput.style.background = '';

							if (isRunning) {
								editEndLabel.textContent = `${I18n.t('times.endTime')} (24h):`;
								editEndInput.removeAttribute('required');
								editEndInput.value = '';
								if (editPastMidnightCheckbox) {
									editPastMidnightCheckbox.checked = false;
									updateEditEndDateDisplay();
								}
							} else {
								editEndLabel.textContent = `${I18n.t('times.endTime')} * (24h):`;
								editEndInput.setAttribute('required', 'required');

								const endIso = entry.end;
								const endDate = new Date(endIso);
								const endTimeStr = !isNaN(endDate.getTime())
										? `${pad(endDate.getHours())}:${pad(endDate.getMinutes())}`
										: '';
								editEndInput.value = endTimeStr;

								const isEntryPastMidnight = !isNaN(startDate.getTime()) && !isNaN(endDate.getTime()) && (
										endDate.getFullYear() > startDate.getFullYear() ||
										endDate.getMonth() > startDate.getMonth() ||
										endDate.getDate() > startDate.getDate()
								);
								if (editPastMidnightCheckbox) {
									editPastMidnightCheckbox.checked = isEntryPastMidnight;
									updateEditEndDateDisplay();
								}
							}

							editLocationSelect.value = entry.workingLocation || '';
							if (editIsOnCallCheckbox) {
								editIsOnCallCheckbox.checked = Boolean(entry.isOnCall);
							}
							editCommentInput.value = entry.comment || '';

							editModal.style.display = 'flex';
						});
					}

					const deleteBtn = row.querySelector('.delete-entry-btn');
					if (deleteBtn) {
						deleteBtn.addEventListener('click', async () => {
							const confirmed = await NotificationDialog.confirm(
									I18n.t('times.confirmDelete'),
									I18n.t('times.deleteEntry')
							);
							if (!confirmed) return;

							try {
								if (this.canManage) {
									await WorkEntryApi.adminDeleteWorkEntry(entry.id);
								} else {
									await WorkEntryApi.deleteWorkEntry(entry.id);
								}
								NotificationDialog.info(I18n.t('times.entryDeleted'));
								await refresh();
							} catch (err) {
								NotificationDialog.error(err.message || I18n.t('app.error'));
							}
						});
					}

					tbody.appendChild(row);
				});
			} catch (err) {
				console.error(err);
				tbody.innerHTML = `<tr><td colspan="6" class="error" style="text-align: center; padding: 2rem;">${err.message || I18n.t('app.error')}</td></tr>`;
			}
		};

		// Event handlers for Add Modal
		if (btnAddEntry) {
			btnAddEntry.addEventListener('click', () => {
				const nowD = new Date();
				const pad = (n) => String(n).padStart(2, '0');
				const todayDate = `${nowD.getFullYear()}-${pad(nowD.getMonth() + 1)}-${pad(nowD.getDate())}`;

				addDateInput.value = todayDate;
				addStartInput.value = '08:00';
				addEndInput.value = '17:00';
				if (addPastMidnightCheckbox) addPastMidnightCheckbox.checked = false;
				updateAddEndDateDisplay();
				addLocationSelect.value = '';
				if (addIsOnCallCheckbox) addIsOnCallCheckbox.checked = false;
				addCommentInput.value = '';
				addModal.style.display = 'flex';
			});
		}

		const closeAddModal = () => {
			addModal.style.display = 'none';
		};
		if (addModalIcon) addModalIcon.addEventListener('click', closeAddModal);
		if (addModalBtn) addModalBtn.addEventListener('click', closeAddModal);

		if (addForm) {
			addForm.addEventListener('submit', async (e) => {
				e.preventDefault();
				const targetEmpId = this.selectedEmployeeId || this.currentUserEmployeeId;
				if (!targetEmpId) {
					NotificationDialog.error(I18n.t('reports.selectEmployeePrompt'));
					return;
				}

				const dateVal = addDateInput.value;
				const startVal = Format.normalizeTime(addStartInput.value);
				const endVal = Format.normalizeTime(addEndInput.value);

				if (!Format.isValidTime(startVal) || !Format.isValidTime(endVal)) {
					NotificationDialog.error(I18n.t('times.invalidDuration'));
					return;
				}

				const isPastMidnight = addPastMidnightCheckbox && addPastMidnightCheckbox.checked;
				const endDateVal = isPastMidnight ? getNextDayString(dateVal) : dateVal;
				const startDate = new Date(`${dateVal}T${startVal}:00`);
				const endDate = new Date(`${endDateVal}T${endVal}:00`);
				if (isNaN(startDate.getTime()) || isNaN(endDate.getTime()) || endDate <= startDate) {
					NotificationDialog.error(I18n.t('times.invalidDuration'));
					return;
				}

				const payload = {
					employeeId: targetEmpId,
					start: startDate.toISOString(),
					end: endDate.toISOString(),
					workingLocation: addLocationSelect.value || undefined,
					comment: addCommentInput.value.trim() || undefined,
					isOnCall: addIsOnCallCheckbox ? addIsOnCallCheckbox.checked : false
				};

				try {
					await WorkEntryApi.createEmployeeWorkEntry(targetEmpId, payload);
					closeAddModal();
					NotificationDialog.info(I18n.t('times.entryCreated'));
					await refresh();
				} catch (err) {
					NotificationDialog.error(err.message || I18n.t('app.error'));
				}
			});
		}

		// Event handlers for Edit Modal
		const closeEditModal = () => {
			editModal.style.display = 'none';
			this.currentEditingEntry = null;
		};
		if (editModalIcon) editModalIcon.addEventListener('click', closeEditModal);
		if (editModalBtn) editModalBtn.addEventListener('click', closeEditModal);

		if (editForm) {
			editForm.addEventListener('submit', async (e) => {
				e.preventDefault();
				if (!this.currentEditingEntry) return;

				const dateVal = editDateInput.value;
				const startVal = Format.normalizeTime(editStartInput.value);
				const endVal = editEndInput.value ? Format.normalizeTime(editEndInput.value) : '';

				if (!Format.isValidTime(startVal)) {
					NotificationDialog.error(I18n.t('times.invalidDuration'));
					return;
				}

				const isRunning = !this.currentEditingEntry.end;

				if (!isRunning && (!endVal || !Format.isValidTime(endVal))) {
					NotificationDialog.error(I18n.t('times.invalidDuration'));
					return;
				}

				if (isRunning && endVal && !Format.isValidTime(endVal)) {
					NotificationDialog.error(I18n.t('times.invalidDuration'));
					return;
				}

				const startDate = new Date(`${dateVal}T${startVal}:00`);
				if (isNaN(startDate.getTime())) {
					NotificationDialog.error(I18n.t('times.invalidDuration'));
					return;
				}

				let endDate = null;
				if (endVal) {
					const isPastMidnight = editPastMidnightCheckbox && editPastMidnightCheckbox.checked;
					const endDateVal = isPastMidnight ? getNextDayString(dateVal) : dateVal;
					endDate = new Date(`${endDateVal}T${endVal}:00`);
					if (isNaN(endDate.getTime()) || endDate <= startDate) {
						NotificationDialog.error(I18n.t('times.invalidDuration'));
						return;
					}
				}

				const isManagerEdit = this.canManage;

				const payload = {
					id: this.currentEditingEntry.id,
					employeeId: this.currentEditingEntry.employeeId,
					start: startDate.toISOString(),
					end: endDate ? endDate.toISOString() : null,
					workingLocation: editLocationSelect.value || undefined,
					comment: editCommentInput.value.trim() || undefined,
					isOnCall: editIsOnCallCheckbox ? editIsOnCallCheckbox.checked : false
				};

				try {
					if (isManagerEdit) {
						await WorkEntryApi.adminUpdateWorkEntry(this.currentEditingEntry.id, payload);
					} else {
						await WorkEntryApi.updateWorkEntry(this.currentEditingEntry.id, payload);
					}

					closeEditModal();
					NotificationDialog.info(I18n.t('times.entryUpdated'));
					await refresh();
				} catch (err) {
					NotificationDialog.error(err.message || I18n.t('app.error'));
				}
			});
		}

		// Load reference data for supervisors/HR/Admins
		if (this.canManage) {
			try {
				const [teams, employees] = await Promise.all([
					TeamApi.getAll().catch(() => []),
					EmployeeApi.getAll().catch(() => [])
				]);
				this.teams = teams || [];
				this.employees = employees || [];

				const currentUsername = AuthApi.getUsername();
				const currentEmp = this.employees.find(e => e.username === currentUsername);
				if (currentEmp) {
					this.currentUserEmployeeId = currentEmp.id;
					this.selectedEmployeeId = currentEmp.id;
					this.selectedTeamId = currentEmp.teamId || '';
				} else if (this.employees.length > 0) {
					this.selectedEmployeeId = this.employees[0].id;
					this.selectedTeamId = this.employees[0].teamId || '';
				}

				if (teamFilter) {
					teamFilter.innerHTML = `<option value="">${I18n.t('common.allTeams')}</option>`;
					this.teams.forEach(t => {
						const opt = document.createElement('option');
						opt.value = t.id;
						opt.textContent = t.name;
						teamFilter.appendChild(opt);
					});
					teamFilter.value = this.selectedTeamId;
					teamFilter.addEventListener('change', () => {
						this.selectedTeamId = teamFilter.value;
						populateEmployees();
						refresh();
					});
				}

				if (employeeFilter) {
					populateEmployees();
					employeeFilter.addEventListener('change', () => {
						this.selectedEmployeeId = employeeFilter.value;
						updateTitle();
						refresh();
					});
				}
			} catch (e) {
				console.warn('Could not load teams or employees for time view', e);
			}
		}

		refreshBtn.addEventListener('click', refresh);
		refresh();

		return container;
	}
}
