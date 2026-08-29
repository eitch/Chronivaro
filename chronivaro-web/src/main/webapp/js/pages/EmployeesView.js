import EmployeeApi from '../api/EmployeeApi.js';
import TeamApi from '../api/TeamApi.js';
import LocationApi from '../api/LocationApi.js';
import ScheduleApi from '../api/ScheduleApi.js';
import ScheduleTemplateApi from '../api/ScheduleTemplateApi.js';
import NotificationDialog from '../utils/NotificationDialog.js';
import I18n from '../i18n/I18n.js';

export default class EmployeesView {
    constructor(app) {
        this.app = app;
        this.statusFilter = 'all';
        this.searchQuery = '';
        this.employees = [];
    }

    async render() {
        const container = document.createElement('div');
        container.id = 'employees-view';
        container.className = 'page-container';
        container.innerHTML = `
			<div class="page-header" style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 1.5rem;">
				<div>
					<h2 style="margin: 0 0 0.5rem 0;">${I18n.t('employees.title')}</h2>
				</div>
				<div class="actions">
					<button id="add-employee-btn" class="primary-btn">${I18n.t('employees.addEmployee')}</button>
				</div>
			</div>

			<!-- Status Filter Bar -->
			<div class="filter-bar card" style="display: flex; flex-wrap: wrap; gap: 1rem; align-items: flex-end; padding: 1rem; margin-bottom: 1.5rem;">
				<div class="filter-group" style="display: flex; flex-direction: column; gap: 0.25rem;">
					<label for="employee-status-filter" style="font-weight: 500; font-size: 0.875rem;">${I18n.t('employees.filterStatus')}:</label>
					<select id="employee-status-filter" style="padding: 0.5rem; border: 1px solid var(--border-color); border-radius: 4px; min-width: 140px;">
						<option value="all" ${this.statusFilter === 'all' ? 'selected' : ''}>${I18n.t('common.all')}</option>
						<option value="active" ${this.statusFilter === 'active' ? 'selected' : ''}>${I18n.t('common.active')}</option>
						<option value="inactive" ${this.statusFilter === 'inactive' ? 'selected' : ''}>${I18n.t('common.inactive')}</option>
					</select>
				</div>
				<div class="filter-group" style="flex: 1; max-width: 350px; display: flex; flex-direction: column; gap: 0.25rem;">
					<label for="employee-search-filter" style="font-weight: 500; font-size: 0.875rem;">${I18n.t('common.search')}:</label>
					<input type="search" id="employee-search-filter" placeholder="${I18n.t('employees.searchPlaceholder')}" value="${this.searchQuery}" style="padding: 0.5rem; border: 1px solid var(--border-color); border-radius: 4px; width: 100%;">
				</div>
				<div class="filter-actions" style="display: flex; gap: 0.5rem; margin-left: auto;">
					<button id="employee-filter-reset-btn" class="secondary-btn">${I18n.t('common.reset')}</button>
				</div>
			</div>

			<div class="table-container card" style="padding: 1rem; overflow: visible;">
				<table id="employees-table" class="data-table">
					<thead>
						<tr>
							<th>${I18n.t('employees.username')}</th>
							<th>${I18n.t('employees.persNr')}</th>
							<th>${I18n.t('employees.firstName')}</th>
							<th>${I18n.t('employees.lastName')}</th>
							<th>${I18n.t('employees.birthdate')}</th>
							<th>${I18n.t('common.team')}</th>
							<th>${I18n.t('common.location')}</th>
							<th>${I18n.t('common.active')}</th>
							<th>${I18n.t('common.actions')}</th>
						</tr>
					</thead>
					<tbody>
						<tr><td colspan="9" class="loading-cell" style="text-align: center; padding: 2rem;">${I18n.t('common.loading')}</td></tr>
					</tbody>
				</table>
			</div>

			<div id="employee-modal" class="modal">
				<div class="modal-content wide">
					<h3 id="modal-title">${I18n.t('employees.addEmployee')}</h3>
					<form id="employee-form">
						<div class="form-grid">
							<div class="form-group" id="emp-username-group">
								<label for="emp-username">${I18n.t('employees.username')}:</label>
								<input type="text" id="emp-username" required>
							</div>
							<div class="form-group">
								<label for="emp-email">${I18n.t('employees.email')}:</label>
								<input type="email" id="emp-email">
							</div>
							<div class="form-group" id="emp-id-group">
								<label for="emp-id">${I18n.t('common.id')}:</label>
								<input type="text" id="emp-id">
							</div>
							<div class="form-group">
								<label for="emp-pers-nr">${I18n.t('employees.personalNumber')}:</label>
								<input type="text" id="emp-pers-nr" required>
							</div>
							<div class="form-group">
								<label for="emp-firstname">${I18n.t('employees.firstName')}:</label>
								<input type="text" id="emp-firstname" required>
							</div>
							<div class="form-group">
								<label for="emp-lastname">${I18n.t('employees.lastName')}:</label>
								<input type="text" id="emp-lastname" required>
							</div>
							<div class="form-group">
								<label for="emp-birthdate">${I18n.t('employees.birthdate')}:</label>
								<input type="date" id="emp-birthdate">
							</div>
							<div class="form-group">
								<label for="emp-team">${I18n.t('common.team')}:</label>
								<select id="emp-team" required></select>
							</div>
							<div class="form-group">
								<label for="emp-location">${I18n.t('common.location')}:</label>
								<select id="emp-location" required></select>
							</div>
							<div class="form-group">
								<label for="emp-timezone">${I18n.t('employees.timezone')}:</label>
								<input type="text" id="emp-timezone" required placeholder="Europe/Zurich">
							</div>
							<div class="form-group">
								<label for="emp-join-date">${I18n.t('employees.joinDate')}:</label>
								<input type="date" id="emp-join-date" required>
							</div>
							<div class="form-group">
								<label for="emp-exit-date">${I18n.t('employees.exitDate')}:</label>
								<input type="date" id="emp-exit-date">
							</div>
							<div class="form-group full-width">
								<label><input type="checkbox" id="emp-active" checked> ${I18n.t('common.active')}</label>
							</div>
						</div>
						<div id="schedule-section">
							<hr>
							<h3>${I18n.t('employees.initialSchedule')}</h3>
							<div class="form-grid">
								<div class="form-group">
									<label for="sched-template">${I18n.t('employees.applyTemplate')}</label>
									<select id="sched-template">
										<option value="">${I18n.t('employees.selectTemplatePrompt')}</option>
									</select>
								</div>
							</div>
						</div>
						<div class="actions">
							<button type="submit">${I18n.t('common.save')}</button>
							<button type="button" id="close-modal">${I18n.t('common.cancel')}</button>
						</div>
					</form>
				</div>
			</div>
		`;

        const tbody = container.querySelector('tbody');
        const modal = container.querySelector('#employee-modal');
        const form = container.querySelector('#employee-form');
        const modalTitle = container.querySelector('#modal-title');
        const addBtn = container.querySelector('#add-employee-btn');
        const closeBtn = container.querySelector('#close-modal');
        const teamSelect = container.querySelector('#emp-team');
        const locationSelect = container.querySelector('#emp-location');
        const templateSelect = container.querySelector('#sched-template');
        const usernameInput = container.querySelector('#emp-username');
        const personalNumberInput = container.querySelector('#emp-pers-nr');

        usernameInput.addEventListener('input', () => {
            if (!editingId) {
                personalNumberInput.value = usernameInput.value;
            }
        });

        let editingId = null;

        const loadOptions = async () => {
            const [teams, locations, templates] = await Promise.all([
                TeamApi.getAll(),
                LocationApi.getAll(),
                ScheduleTemplateApi.getAll()
            ]);

            if (teams.length === 0) {
                teamSelect.innerHTML = `<option value="">${I18n.t('employees.noTeamsAvailable')}</option>`;
            } else {
                teamSelect.innerHTML = teams.map(t => `<option value="${t.id}">${t.name}</option>`).join('');
            }

            if (locations.length === 0) {
                locationSelect.innerHTML = `<option value="">${I18n.t('employees.noLocationsAvailable')}</option>`;
            } else {
                locationSelect.innerHTML = locations.map(l => `<option value="${l.id}">${l.name}</option>`).join('');
            }

            templateSelect.innerHTML = `<option value="">${I18n.t('employees.selectTemplatePrompt')}</option>` +
                templates.map(t => `<option value="${t.id}">${t.name}</option>`).join('');
            templateSelect.templates = templates;
        };

        const statusFilterSelect = container.querySelector('#employee-status-filter');
        const searchFilterInput = container.querySelector('#employee-search-filter');
        const resetFilterBtn = container.querySelector('#employee-filter-reset-btn');

        statusFilterSelect.addEventListener('change', (e) => {
            this.statusFilter = e.target.value;
            renderEmployees();
        });

        searchFilterInput.addEventListener('input', (e) => {
            this.searchQuery = e.target.value.trim().toLowerCase();
            renderEmployees();
        });

        resetFilterBtn.addEventListener('click', () => {
            this.statusFilter = 'all';
            this.searchQuery = '';
            statusFilterSelect.value = 'all';
            searchFilterInput.value = '';
            renderEmployees();
        });

        const renderEmployees = () => {
            tbody.innerHTML = '';
            let filtered = this.employees || [];

            if (this.statusFilter === 'active') {
                filtered = filtered.filter(e => e.active);
            } else if (this.statusFilter === 'inactive') {
                filtered = filtered.filter(e => !e.active);
            }

            if (this.searchQuery) {
                const q = this.searchQuery.toLowerCase();
                filtered = filtered.filter(e =>
                    (e.username && e.username.toLowerCase().includes(q)) ||
                    (e.personalNumber && e.personalNumber.toLowerCase().includes(q)) ||
                    (e.firstname && e.firstname.toLowerCase().includes(q)) ||
                    (e.lastname && e.lastname.toLowerCase().includes(q)) ||
                    (e.teamName && e.teamName.toLowerCase().includes(q)) ||
                    (e.locationName && e.locationName.toLowerCase().includes(q))
                );
            }

            if (filtered.length === 0) {
                tbody.innerHTML = `<tr><td colspan="9" class="empty-cell" style="text-align: center; padding: 2rem; color: var(--text-muted);">${I18n.t('common.noData')}</td></tr>`;
                return;
            }

            filtered.forEach(emp => {
                const row = document.createElement('tr');
                if (!emp.active) {
                    row.classList.add('inactive-row');
                }
                const statusBadge = emp.active
                    ? `<span class="status-badge badge-active">${I18n.t('common.active')}</span>`
                    : `<span class="status-badge badge-inactive">${I18n.t('common.inactive')}</span>`;

                row.innerHTML = `
					<td>${emp.username}</td>
					<td>${emp.personalNumber}</td>
					<td>${emp.firstname}</td>
					<td>${emp.lastname}</td>
					<td>${emp.birthdate || ''}</td>
					<td>${emp.teamName || ''}</td>
					<td>${emp.locationName || ''}</td>
					<td>${statusBadge}</td>
					<td>
						<div class="dropdown">
							<button class="ghost dropdown-toggle" data-id="${emp.id}">${I18n.t('common.actions')}</button>
							<div class="dropdown-content">
								<button class="edit-btn" data-id="${emp.id}">${I18n.t('common.edit')}</button>
								${emp.active ? `<button class="register-btn" data-id="${emp.id}">${I18n.t('employees.register')}</button>` : `<button class="reactivate-btn" data-id="${emp.id}">${I18n.t('employees.reactivate')}</button>`}
								<button class="schedules-btn" data-id="${emp.id}">${I18n.t('employees.schedules')}</button>
								<button class="delete-btn" data-id="${emp.id}">${I18n.t('common.delete')}</button>
							</div>
						</div>
					</td>
				`;
                tbody.appendChild(row);
            });

            container.querySelectorAll('.dropdown-toggle').forEach(btn => {
                btn.addEventListener('click', (e) => {
                    e.stopPropagation();
                    container.querySelectorAll('.dropdown').forEach(d => {
                        if (d !== btn.parentElement) d.classList.remove('show');
                    });
                    btn.parentElement.classList.toggle('show');
                });
            });

            container.querySelectorAll('.edit-btn').forEach(btn => {
                btn.addEventListener('click', () => editEmployee(btn.dataset.id));
            });
            container.querySelectorAll('.register-btn').forEach(btn => {
                btn.addEventListener('click', () => registerEmployee(btn.dataset.id));
            });
            container.querySelectorAll('.reactivate-btn').forEach(btn => {
                btn.addEventListener('click', () => reactivateEmployee(btn.dataset.id));
            });
            container.querySelectorAll('.schedules-btn').forEach(btn => {
                btn.addEventListener('click', () => this.app.navigate('schedules', {employeeId: btn.dataset.id}));
            });
            container.querySelectorAll('.delete-btn').forEach(btn => {
                btn.addEventListener('click', () => deleteEmployee(btn.dataset.id));
            });
        };

        const refresh = async () => {
            try {
                tbody.innerHTML = `<tr><td colspan="9" class="loading-cell" style="text-align: center; padding: 2rem;">${I18n.t('common.loading')}</td></tr>`;
                this.employees = await EmployeeApi.getAll();
                renderEmployees();
            } catch (err) {
                console.error(err);
                tbody.innerHTML = `<tr><td colspan="9" class="error">${err.message}</td></tr>`;
            }
        };

        const editEmployee = async (id) => {
            try {
                await loadOptions();
                const employees = await EmployeeApi.getAll();
                const emp = employees.find(e => e.id === id);
                if (emp) {
                    editingId = id;
                    modalTitle.innerText = I18n.t('employees.editEmployee');
                    container.querySelector('#emp-id-group').style.display = 'block';
                    container.querySelector('#emp-id').value = emp.id;
                    container.querySelector('#emp-id').disabled = true;
                    container.querySelector('#emp-pers-nr').value = emp.personalNumber;
                    container.querySelector('#emp-firstname').value = emp.firstname;
                    container.querySelector('#emp-lastname').value = emp.lastname;
                    container.querySelector('#emp-birthdate').value = emp.birthdate || '';
                    container.querySelector('#emp-team').value = emp.teamId;
                    container.querySelector('#emp-location').value = emp.locationId;
                    container.querySelector('#emp-timezone').value = emp.timezone;
                    container.querySelector('#emp-join-date').value = emp.joinDate;
                    container.querySelector('#emp-exit-date').value = emp.exitDate || '';
                    container.querySelector('#emp-username').value = emp.username;
                    container.querySelector('#emp-email').value = emp.email || '';
                    container.querySelector('#emp-active').checked = emp.active;
                    container.querySelector('#schedule-section').style.display = 'none';
                    modal.style.display = 'block';
                }
            } catch (err) {
                NotificationDialog.error(err.message);
            }
        };

        const getEmployeeName = (id) => {
            const emp = (this.employees || []).find(e => e.id === id);
            if (!emp) return id;
            const full = `${emp.firstname || ''} ${emp.lastname || ''}`.trim();
            return full || emp.username || id;
        };

        const registerEmployee = async (id) => {
            const name = getEmployeeName(id);
            if (await NotificationDialog.confirm(I18n.t('employees.confirmRegister', { name, id }))) {
                try {
                    await EmployeeApi.register(id);
                    NotificationDialog.info(I18n.t('employees.registerSuccess'));
                } catch (err) {
                    NotificationDialog.error(err.message);
                }
            }
        };

        const reactivateEmployee = async (id) => {
            const name = getEmployeeName(id);
            if (await NotificationDialog.confirm(I18n.t('employees.confirmReactivate', { name, id }))) {
                try {
                    await EmployeeApi.reactivate(id);
                    NotificationDialog.info(I18n.t('employees.reactivateSuccess'));
                    refresh();
                } catch (err) {
                    NotificationDialog.error(err.message);
                }
            }
        };

        const deleteEmployee = async (id) => {
            const name = getEmployeeName(id);
            if (await NotificationDialog.confirm(I18n.t('employees.confirmDelete', { name, id }))) {
                try {
                    await EmployeeApi.remove(id);
                    refresh();
                } catch (err) {
                    NotificationDialog.error(err.message);
                }
            }
        };

        addBtn.addEventListener('click', async () => {
            await loadOptions();
            editingId = null;
            modalTitle.innerText = I18n.t('employees.addEmployee');
            form.reset();
            container.querySelector('#emp-id-group').style.display = 'none';
            container.querySelector('#emp-id').disabled = false;
            container.querySelector('#emp-username-group').style.display = 'block';
            container.querySelector('#emp-username').required = true;
            container.querySelector('#emp-username').value = '';
            container.querySelector('#emp-timezone').value = Intl.DateTimeFormat().resolvedOptions().timeZone || 'Europe/Zurich';
            container.querySelector('#emp-active').checked = true;
            container.querySelector('#schedule-section').style.display = 'block';
            modal.style.display = 'block';
        });

        closeBtn.addEventListener('click', () => {
            modal.style.display = 'none';
        });

        form.addEventListener('submit', async (e) => {
            e.preventDefault();

            const emp = {
                personalNumber: container.querySelector('#emp-pers-nr').value,
                firstname: container.querySelector('#emp-firstname').value,
                lastname: container.querySelector('#emp-lastname').value,
                birthdate: container.querySelector('#emp-birthdate').value,
                teamId: container.querySelector('#emp-team').value,
                locationId: container.querySelector('#emp-location').value,
                timezone: container.querySelector('#emp-timezone').value,
                joinDate: container.querySelector('#emp-join-date').value,
                exitDate: container.querySelector('#emp-exit-date').value || null,
                username: container.querySelector('#emp-username').value,
                email: container.querySelector('#emp-email').value,
                active: container.querySelector('#emp-active').checked,
                scheduleTemplateId: editingId ? null : container.querySelector('#sched-template').value
            };

            try {
                if (editingId) {
                    emp.id = editingId;
                    await EmployeeApi.update(emp);
                } else {
                    await EmployeeApi.create(emp);
                }
                modal.style.display = 'none';
                refresh();
            } catch (err) {
                NotificationDialog.error(err.message);
            }
        });

        refresh();

        document.addEventListener('click', () => {
            container.querySelectorAll('.dropdown').forEach(d => d.classList.remove('show'));
        });

        return container;
    }
}
