import ReportApi from '../api/ReportApi.js';
import AbsenceTypeApi from '../api/AbsenceTypeApi.js';
import TeamApi from '../api/TeamApi.js';
import EmployeeApi from '../api/EmployeeApi.js';
import AuthApi from '../api/AuthApi.js';
import NotificationDialog from '../utils/NotificationDialog.js';
import Format from '../utils/Format.js';
import MonthPicker from '../utils/MonthPicker.js';
import I18n from '../i18n/I18n.js';

export default class ReportsView {

	constructor(app) {
		this.app = app;
		this.activeReportType = 'day'; // 'day', 'month', 'vacation', 'team', 'absences', 'on-call'

		const now = new Date();
		const year = now.getFullYear();
		const month = String(now.getMonth() + 1).padStart(2, '0');
		const day = String(now.getDate()).padStart(2, '0');

		this.filters = {
			day: {
				date: `${year}-${month}-${day}`,
				teamId: '',
				employeeId: ''
			},
			month: {
				yearMonth: `${year}-${month}`,
				teamId: '',
				employeeId: ''
			},
			vacation: {
				year: year,
				teamId: '',
				employeeId: ''
			},
			team: {
				teamId: '',
				yearMonth: `${year}-${month}`
			},
			absences: {
				from: `${year}-${month}-01`,
				to: `${year}-${month}-${day}`,
				teamId: '',
				employeeId: '',
				type: '',
				state: ''
			},
			'on-call': {
				from: `${year}-${month}-01`,
				to: `${year}-${month}-${day}`,
				teamId: '',
				employeeId: ''
			}
		};

		Object.defineProperty(this.filters, 'onCall', {
			get: () => this.filters['on-call'],
			set: (v) => { this.filters['on-call'] = v; }
		});

		this.absenceTypes = [];
		this.teams = [];
		this.employees = [];
	}

	async render(params) {
		if (params && params.type) {
			this.activeReportType = params.type;
		}
		if (this.activeReportType === 'team' && !this.canViewTeamReport()) {
			this.activeReportType = 'day';
		}

		const container = document.createElement('div');
		container.id = 'reports-view';
		container.innerHTML = `
			<div class="view-header">
				<h2>${I18n.t('reports.reportsAndExport')}</h2>
			</div>

			<!-- Report Type Selector -->
			<div class="tabs-container">
				<div class="tab-buttons">
					<button id="report-type-day-btn" class="tab-btn ${this.activeReportType === 'day' ? 'active' : ''}">
						${I18n.t('reports.dayReport')}
					</button>
					<button id="report-type-month-btn" class="tab-btn ${this.activeReportType === 'month' ? 'active' : ''}">
						${I18n.t('reports.monthReport')}
					</button>
					<button id="report-type-vacation-btn" class="tab-btn ${this.activeReportType === 'vacation' ? 'active' : ''}">
						${I18n.t('reports.vacationReport')}
					</button>
					${this.canViewTeamReport() ? `
					<button id="report-type-team-btn" class="tab-btn ${this.activeReportType === 'team' ? 'active' : ''}">
						${I18n.t('reports.teamReport')}
					</button>
					` : ''}
					<button id="report-type-absences-btn" class="tab-btn ${this.activeReportType === 'absences' ? 'active' : ''}">
						${I18n.t('reports.absencesReport')}
					</button>
					<button id="report-type-on-call-btn" class="tab-btn ${this.activeReportType === 'on-call' ? 'active' : ''}">
						${I18n.t('reports.onCallReport')}
					</button>
				</div>
			</div>

			<!-- Filter Controls Section -->
			<section class="card report-filter-card">
				<div class="filter-bar" id="report-filter-bar">
					<!-- Dynamic Filter Fields will be injected here -->
				</div>
				<div class="report-actions-bar">
					<button id="btn-run-report" class="primary-btn">${I18n.t('reports.generateReport')}</button>
					<button id="btn-export-csv" class="secondary-btn btn-export">
						<span class="icon">📥</span> ${I18n.t('reports.exportCsvBom')}
					</button>
					<button id="btn-export-pdf" class="secondary-btn btn-export">
						<span class="icon">📄</span> ${I18n.t('reports.exportPdf')}
					</button>
				</div>
			</section>

			<!-- Report Content Container -->
			<div id="report-results-container">
				<div class="empty-state">${I18n.t('reports.selectParamsPrompt')}</div>
			</div>
		`;

		this.container = container;
		this.filterBar = container.querySelector('#report-filter-bar');
		this.resultsContainer = container.querySelector('#report-results-container');
		this.runBtn = container.querySelector('#btn-run-report');
		this.exportBtn = container.querySelector('#btn-export-csv');
		this.exportPdfBtn = container.querySelector('#btn-export-pdf');

		this.setupTabs(container);

		// Load reference data first so dropdowns are populated properly
		await this.loadReferenceData();

		this.renderFilterFields();

		this.runBtn.addEventListener('click', () => this.generateReport());
		this.exportBtn.addEventListener('click', () => this.exportCsv());
		this.exportPdfBtn.addEventListener('click', () => this.exportPdf());
		this.updateActionButtons();

		// Only users with an employee profile can use the implicit self-report.
		// Administrative users must select an employee explicitly.
		if (AuthApi.hasRole('Employee') || AuthApi.hasRole('Supervisor')) {
			this.generateReport();
		}

		return container;
	}

	updateActionButtons() {
		const isAdmin = AuthApi.hasRole('Administrator') || AuthApi.hasRole('StrolchAdmin');
		const employeeId = this.filters[this.activeReportType]?.employeeId?.trim();
		const teamId = this.filters.team.teamId?.trim();
		const hasExplicitTarget = Boolean(employeeId || (this.activeReportType === 'team' && teamId) || this.activeReportType === 'absences' || this.activeReportType === 'on-call');
		const disabled = isAdmin && !hasExplicitTarget;

		[this.runBtn, this.exportBtn, this.exportPdfBtn].forEach(button => {
			if (button) button.disabled = disabled;
		});
	}

	setupTabs(container) {
		const types = ['day', 'month', 'vacation', 'team', 'absences', 'on-call'];
		types.forEach(type => {
			const btn = container.querySelector(`#report-type-${type}-btn`);
			if (btn) {
				btn.addEventListener('click', () => {
					this.activeReportType = type;
					container.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));
					btn.classList.add('active');
					this.renderFilterFields();
					this.generateReport();
				});
			}
		});
	}

	async loadReferenceData() {
		try {
			const [types, teams, employees] = await Promise.all([
				AbsenceTypeApi.getAll().catch(e => { console.warn('Could not load absence types', e); return []; }),
				TeamApi.getAll().catch(e => { console.warn('Could not load teams', e); return []; }),
				EmployeeApi.getAll().catch(e => { console.warn('Could not load employees', e); return []; })
			]);
			this.absenceTypes = types || [];
			this.teams = teams || [];
			this.employees = employees || [];

			if (this.filterBar) {
				if (this.activeReportType === 'absences') {
					this.populateAbsenceTypeSelect();
				}
				if (this.activeReportType === 'team' && this.canViewTeamReport()) {
					this.populateTeamSelect('team');
				}
				if (this.canSelectEmployee()) {
					this.populateTeamSelect(this.activeReportType);
					this.populateEmployeeSelect(this.activeReportType);
				}
				this.updateActionButtons();
			}
		} catch (e) {
			console.warn('Could not load reference data for reports', e);
		}
	}

	populateAbsenceTypeSelect() {
		const select = this.filterBar ? this.filterBar.querySelector('#filter-absence-type') : null;
		if (!select) return;
		const currentVal = this.filters.absences.type;
		select.innerHTML = `<option value="">${I18n.t('common.allTypes')}</option>`;
		this.absenceTypes.forEach(t => {
			const opt = document.createElement('option');
			opt.value = t.code || t.id;
			opt.textContent = `${t.name} (${t.code || t.id})`;
			select.appendChild(opt);
		});
		if (currentVal) select.value = currentVal;
	}

	populateTeamSelect(type) {
		const select = type === 'team'
			? (this.filterBar ? this.filterBar.querySelector('#filter-team-id-select') : null)
			: (this.filterBar ? this.filterBar.querySelector(`#filter-${type}-team`) : null);
		if (!select) return;

		const currentVal = this.filters[type]?.teamId || '';
		select.innerHTML = `<option value="">${type === 'team' ? I18n.t('reports.selectTeamPrompt') : I18n.t('common.allTeams')}</option>`;

		const sortedTeams = [...(this.teams || [])].sort((a, b) => (a.name || '').localeCompare(b.name || ''));
		sortedTeams.forEach(t => {
			const opt = document.createElement('option');
			opt.value = t.id;
			opt.textContent = `${t.name}`;
			select.appendChild(opt);
		});

		if (currentVal) select.value = currentVal;
	}

	populateEmployeeSelect(type) {
		const select = this.filterBar ? this.filterBar.querySelector(`#filter-${type}-emp`) : null;
		if (!select) return;

		const selectedTeamId = this.filters[type]?.teamId;
		const currentVal = this.filters[type]?.employeeId || '';

		const isAdmin = AuthApi.hasRole('Administrator') || AuthApi.hasRole('StrolchAdmin');
		let defaultPrompt;
		if (type === 'absences' || type === 'on-call' || type === 'onCall') {
			defaultPrompt = I18n.t('common.allEmployees');
		} else if (isAdmin) {
			defaultPrompt = I18n.t('reports.selectEmployeePrompt');
		} else {
			defaultPrompt = I18n.t('reports.defaultCurrentUser');
		}

		select.innerHTML = `<option value="">${defaultPrompt}</option>`;

		let filteredEmployees = this.employees || [];
		if (selectedTeamId) {
			filteredEmployees = filteredEmployees.filter(e => e.teamId === selectedTeamId);
		}

		filteredEmployees = [...filteredEmployees].sort((a, b) => {
			const nameA = `${a.lastname || ''} ${a.firstname || ''}`.toLowerCase();
			const nameB = `${b.lastname || ''} ${b.firstname || ''}`.toLowerCase();
			return nameA.localeCompare(nameB);
		});

		filteredEmployees.forEach(emp => {
			const opt = document.createElement('option');
			opt.value = emp.id;
			const name = `${emp.firstname || ''} ${emp.lastname || ''}`.trim() || emp.username || emp.id;
			const persNr = emp.personalNumber ? ` (${emp.personalNumber})` : '';
			opt.textContent = `${name}${persNr}`;
			select.appendChild(opt);
		});

		if (currentVal && filteredEmployees.some(e => e.id === currentVal)) {
			select.value = currentVal;
		} else {
			select.value = '';
			if (this.filters[type]) {
				this.filters[type].employeeId = '';
			}
		}
	}

	getEmployeeDisplay(employeeId, data = null) {
		const targetId = employeeId || (data ? data.employeeId : null) || AuthApi.getUserId();
		const emp = (this.employees || []).find(e => e.id === targetId || e.username === targetId || e.userId === targetId);

		let employeeName = emp ? `${emp.firstname || ''} ${emp.lastname || ''}`.trim() : (data?.employeeName || '');
		if (!employeeName && (!emp || !targetId || targetId === AuthApi.getUserId() || targetId === AuthApi.getUsername())) {
			employeeName = AuthApi.getFullName();
		}

		const username = emp?.username || data?.username || (targetId === AuthApi.getUserId() ? AuthApi.getUsername() : null);
		const personalNumber = emp?.personalNumber || data?.personalNumber || null;

		const name = employeeName || username || targetId || '-';
		const persNr = personalNumber && !name.includes(`(${personalNumber})`) ? ` (${personalNumber})` : '';

		return `${name}${persNr}`;
	}

	canSelectEmployee() {
		return AuthApi.hasRole('Supervisor') || AuthApi.hasRole('HR') || AuthApi.hasRole('Administrator') || AuthApi.hasRole('StrolchAdmin');
	}

	canViewTeamReport() {
		return AuthApi.hasRole('Supervisor') || AuthApi.hasRole('HR') || AuthApi.hasRole('Administrator') || AuthApi.hasRole('StrolchAdmin');
	}

	renderFilterFields() {
		const canSelectEmp = this.canSelectEmployee();

		if (this.activeReportType === 'day') {
			this.filterBar.innerHTML = `
				<div class="filter-group">
					<label for="filter-day-date">${I18n.t('common.date')} * (DD.MM.YYYY):</label>
					<input type="text" id="filter-day-date" value="${Format.date(this.filters.day.date)}" placeholder="DD.MM.YYYY" maxlength="10" required>
				</div>
				${canSelectEmp ? `
				<div class="filter-group">
					<label for="filter-day-team">${I18n.t('common.team')}:</label>
					<select id="filter-day-team">
						<option value="">${I18n.t('common.allTeams')}</option>
					</select>
				</div>
				<div class="filter-group">
					<label for="filter-day-emp">${I18n.t('common.employee')}:</label>
					<select id="filter-day-emp">
						<option value="">${I18n.t('reports.defaultCurrentUser')}</option>
					</select>
				</div>
				` : ''}
			`;
			const dateInput = this.filterBar.querySelector('#filter-day-date');
			dateInput.addEventListener('blur', () => {
				if (dateInput.value) dateInput.value = Format.normalizeDate(dateInput.value);
			});
			dateInput.addEventListener('change', () => { this.filters.day.date = Format.toIsoDate(dateInput.value); });

			if (canSelectEmp) {
				const teamSelect = this.filterBar.querySelector('#filter-day-team');
				const empSelect = this.filterBar.querySelector('#filter-day-emp');
				this.populateTeamSelect('day');
				this.populateEmployeeSelect('day');

				teamSelect.addEventListener('change', () => {
					this.filters.day.teamId = teamSelect.value;
					this.populateEmployeeSelect('day');
					this.updateActionButtons();
				});
				empSelect.addEventListener('change', () => {
					this.filters.day.employeeId = empSelect.value;
					this.updateActionButtons();
				});
			}

		} else if (this.activeReportType === 'month') {
			this.filterBar.innerHTML = `
				<div class="filter-group">
					<label for="filter-month-ym">${I18n.t('common.month')} (YYYY-MM) *:</label>
					<input type="month" id="filter-month-ym" value="${this.filters.month.yearMonth}" required>
				</div>
				${canSelectEmp ? `
				<div class="filter-group">
					<label for="filter-month-team">${I18n.t('common.team')}:</label>
					<select id="filter-month-team">
						<option value="">${I18n.t('common.allTeams')}</option>
					</select>
				</div>
				<div class="filter-group">
					<label for="filter-month-emp">${I18n.t('common.employee')}:</label>
					<select id="filter-month-emp">
						<option value="">${I18n.t('reports.defaultCurrentUser')}</option>
					</select>
				</div>
				` : ''}
			`;
			const ymInput = this.filterBar.querySelector('#filter-month-ym');
			ymInput.addEventListener('change', () => { this.filters.month.yearMonth = ymInput.value; });

			if (canSelectEmp) {
				const teamSelect = this.filterBar.querySelector('#filter-month-team');
				const empSelect = this.filterBar.querySelector('#filter-month-emp');
				this.populateTeamSelect('month');
				this.populateEmployeeSelect('month');

				teamSelect.addEventListener('change', () => {
					this.filters.month.teamId = teamSelect.value;
					this.populateEmployeeSelect('month');
					this.updateActionButtons();
				});
				empSelect.addEventListener('change', () => {
					this.filters.month.employeeId = empSelect.value;
					this.updateActionButtons();
				});
			}

		} else if (this.activeReportType === 'vacation') {
			this.filterBar.innerHTML = `
				<div class="filter-group">
					<label for="filter-vacation-year">${I18n.t('common.year')} *:</label>
					<input type="number" id="filter-vacation-year" min="2000" max="2100" value="${this.filters.vacation.year}" required>
				</div>
				${canSelectEmp ? `
				<div class="filter-group">
					<label for="filter-vacation-team">${I18n.t('common.team')}:</label>
					<select id="filter-vacation-team">
						<option value="">${I18n.t('common.allTeams')}</option>
					</select>
				</div>
				<div class="filter-group">
					<label for="filter-vacation-emp">${I18n.t('common.employee')}:</label>
					<select id="filter-vacation-emp">
						<option value="">${I18n.t('reports.defaultCurrentUser')}</option>
					</select>
				</div>
				` : ''}
			`;
			const yearInput = this.filterBar.querySelector('#filter-vacation-year');
			yearInput.addEventListener('change', () => { this.filters.vacation.year = yearInput.value; });

			if (canSelectEmp) {
				const teamSelect = this.filterBar.querySelector('#filter-vacation-team');
				const empSelect = this.filterBar.querySelector('#filter-vacation-emp');
				this.populateTeamSelect('vacation');
				this.populateEmployeeSelect('vacation');

				teamSelect.addEventListener('change', () => {
					this.filters.vacation.teamId = teamSelect.value;
					this.populateEmployeeSelect('vacation');
					this.updateActionButtons();
				});
				empSelect.addEventListener('change', () => {
					this.filters.vacation.employeeId = empSelect.value;
					this.updateActionButtons();
				});
			}

		} else if (this.activeReportType === 'team') {
			if (!this.canViewTeamReport()) {
				this.filterBar.innerHTML = `<div class="empty-state">${I18n.t('errors.forbidden')}</div>`;
				return;
			}
			this.filterBar.innerHTML = `
				<div class="filter-group">
					<label for="filter-team-ym">${I18n.t('common.month')} (YYYY-MM) *:</label>
					<input type="month" id="filter-team-ym" value="${this.filters.team.yearMonth}" required>
				</div>
				<div class="filter-group">
					<label for="filter-team-id-select">${I18n.t('common.team')} *:</label>
					<select id="filter-team-id-select" required>
						<option value="">${I18n.t('reports.selectTeamPrompt')}</option>
					</select>
				</div>
			`;
			const ymInput = this.filterBar.querySelector('#filter-team-ym');
			const teamSelect = this.filterBar.querySelector('#filter-team-id-select');
			this.populateTeamSelect('team');

			ymInput.addEventListener('change', () => {
				this.filters.team.yearMonth = ymInput.value;
				this.updateActionButtons();
			});
			teamSelect.addEventListener('change', () => {
				this.filters.team.teamId = teamSelect.value;
				this.updateActionButtons();
			});

		} else if (this.activeReportType === 'absences') {
			this.filterBar.innerHTML = `
				<div class="filter-group">
					<label for="filter-absences-from">${I18n.t('common.from')} (DD.MM.YYYY):</label>
					<input type="text" id="filter-absences-from" value="${Format.date(this.filters.absences.from)}" placeholder="DD.MM.YYYY" maxlength="10">
				</div>
				<div class="filter-group">
					<label for="filter-absences-to">${I18n.t('common.to')} (DD.MM.YYYY):</label>
					<input type="text" id="filter-absences-to" value="${Format.date(this.filters.absences.to)}" placeholder="DD.MM.YYYY" maxlength="10">
				</div>
				${canSelectEmp ? `
				<div class="filter-group">
					<label for="filter-absences-team">${I18n.t('common.team')}:</label>
					<select id="filter-absences-team">
						<option value="">${I18n.t('common.allTeams')}</option>
					</select>
				</div>
				<div class="filter-group">
					<label for="filter-absences-emp">${I18n.t('common.employee')}:</label>
					<select id="filter-absences-emp">
						<option value="">${I18n.t('common.allEmployees')}</option>
					</select>
				</div>
				` : ''}
				<div class="filter-group">
					<label for="filter-absence-type">${I18n.t('absences.absenceType')}:</label>
					<select id="filter-absence-type">
						<option value="">${I18n.t('common.allTypes')}</option>
					</select>
				</div>
				<div class="filter-group">
					<label for="filter-absence-state">${I18n.t('common.status')}:</label>
					<select id="filter-absence-state">
						<option value="">${I18n.t('common.allStates')}</option>
						<option value="SUBMITTED">${I18n.t('enums.absenceState.SUBMITTED')}</option>
						<option value="APPROVED">${I18n.t('enums.absenceState.APPROVED')}</option>
						<option value="REJECTED">${I18n.t('enums.absenceState.REJECTED')}</option>
						<option value="CANCELLED">${I18n.t('enums.absenceState.CANCELLED')}</option>
					</select>
				</div>
			`;
			const fromInput = this.filterBar.querySelector('#filter-absences-from');
			const toInput = this.filterBar.querySelector('#filter-absences-to');
			const typeSelect = this.filterBar.querySelector('#filter-absence-type');
			const stateSelect = this.filterBar.querySelector('#filter-absence-state');

			[fromInput, toInput].forEach(inp => {
				if (inp) {
					inp.addEventListener('blur', () => {
						if (inp.value) inp.value = Format.normalizeDate(inp.value);
					});
				}
			});

			fromInput.addEventListener('change', () => { this.filters.absences.from = Format.toIsoDate(fromInput.value); });
			toInput.addEventListener('change', () => { this.filters.absences.to = Format.toIsoDate(toInput.value); });
			typeSelect.addEventListener('change', () => { this.filters.absences.type = typeSelect.value; });
			stateSelect.addEventListener('change', () => { this.filters.absences.state = stateSelect.value; });

			if (this.filters.absences.type) typeSelect.value = this.filters.absences.type;
			if (this.filters.absences.state) stateSelect.value = this.filters.absences.state;
			this.populateAbsenceTypeSelect();

			if (canSelectEmp) {
				const teamSelect = this.filterBar.querySelector('#filter-absences-team');
				const empSelect = this.filterBar.querySelector('#filter-absences-emp');
				this.populateTeamSelect('absences');
				this.populateEmployeeSelect('absences');

				teamSelect.addEventListener('change', () => {
					this.filters.absences.teamId = teamSelect.value;
					this.populateEmployeeSelect('absences');
					this.updateActionButtons();
				});
				empSelect.addEventListener('change', () => {
					this.filters.absences.employeeId = empSelect.value;
					this.updateActionButtons();
				});
			}
		} else if (this.activeReportType === 'on-call') {
			this.filterBar.innerHTML = `
				<div class="filter-group">
					<label for="filter-on-call-from">${I18n.t('common.from')} (DD.MM.YYYY):</label>
					<input type="text" id="filter-on-call-from" value="${Format.date(this.filters['on-call'].from)}" placeholder="DD.MM.YYYY" maxlength="10">
				</div>
				<div class="filter-group">
					<label for="filter-on-call-to">${I18n.t('common.to')} (DD.MM.YYYY):</label>
					<input type="text" id="filter-on-call-to" value="${Format.date(this.filters['on-call'].to)}" placeholder="DD.MM.YYYY" maxlength="10">
				</div>
				${canSelectEmp ? `
				<div class="filter-group">
					<label for="filter-on-call-team">${I18n.t('common.team')}:</label>
					<select id="filter-on-call-team">
						<option value="">${I18n.t('common.allTeams')}</option>
					</select>
				</div>
				<div class="filter-group">
					<label for="filter-on-call-emp">${I18n.t('common.employee')}:</label>
					<select id="filter-on-call-emp">
						<option value="">${I18n.t('common.allEmployees')}</option>
					</select>
				</div>
				` : ''}
			`;
			const fromInput = this.filterBar.querySelector('#filter-on-call-from');
			const toInput = this.filterBar.querySelector('#filter-on-call-to');

			[fromInput, toInput].forEach(inp => {
				if (inp) {
					inp.addEventListener('blur', () => {
						if (inp.value) inp.value = Format.normalizeDate(inp.value);
					});
				}
			});

			fromInput.addEventListener('change', () => { this.filters['on-call'].from = Format.toIsoDate(fromInput.value); });
			toInput.addEventListener('change', () => { this.filters['on-call'].to = Format.toIsoDate(toInput.value); });

			if (canSelectEmp) {
				const teamSelect = this.filterBar.querySelector('#filter-on-call-team');
				const empSelect = this.filterBar.querySelector('#filter-on-call-emp');
				this.populateTeamSelect('on-call');
				this.populateEmployeeSelect('on-call');

				teamSelect.addEventListener('change', () => {
					this.filters['on-call'].teamId = teamSelect.value;
					this.populateEmployeeSelect('on-call');
					this.updateActionButtons();
				});
				empSelect.addEventListener('change', () => {
					this.filters['on-call'].employeeId = empSelect.value;
					this.updateActionButtons();
				});
			}
		}

		this.filterBar.querySelectorAll('input, select').forEach(input => {
			input.addEventListener('input', () => this.updateActionButtons());
			input.addEventListener('change', () => this.updateActionButtons());
		});
		MonthPicker.init(this.filterBar);
		this.updateActionButtons();
	}

	async generateReport() {
		const employeeId = this.filters[this.activeReportType]?.employeeId?.trim();
		const teamId = this.filters.team?.teamId?.trim();
		const hasExplicitTarget = Boolean(employeeId || (this.activeReportType === 'team' && teamId) || this.activeReportType === 'absences' || this.activeReportType === 'on-call');
		if (AuthApi.hasRole('Administrator') && !hasExplicitTarget) {
			this.resultsContainer.innerHTML = '';
			return;
		}

		this.resultsContainer.innerHTML = `<div class="loading-spinner">${I18n.t('reports.generatingReport')}</div>`;

		try {
			if (this.activeReportType === 'day') {
				const date = this.filters.day.date;
				if (!date) {
					this.resultsContainer.innerHTML = `<div class="error-msg">${I18n.t('reports.pleaseSpecifyDate')}</div>`;
					return;
				}
				const data = await ReportApi.getDayReport(date, this.filters.day.employeeId);
				this.renderDayReport(data);

			} else if (this.activeReportType === 'month') {
				const ym = this.filters.month.yearMonth;
				if (!ym) {
					this.resultsContainer.innerHTML = `<div class="error-msg">${I18n.t('reports.pleaseSpecifyMonth')}</div>`;
					return;
				}
				const data = await ReportApi.getMonthReport(ym, this.filters.month.employeeId);
				this.renderMonthReport(data);

			} else if (this.activeReportType === 'vacation') {
				const year = this.filters.vacation.year;
				const data = await ReportApi.getVacationReport(year, this.filters.vacation.employeeId);
				this.renderVacationReport(data);

			} else if (this.activeReportType === 'team') {
				if (!this.canViewTeamReport()) {
					this.resultsContainer.innerHTML = `<div class="error-msg">${I18n.t('errors.forbidden')}</div>`;
					return;
				}
				const teamId = this.filters.team.teamId;
				const ym = this.filters.team.yearMonth;
				if (!teamId || !ym) {
					this.resultsContainer.innerHTML = `<div class="error-msg">${I18n.t('reports.pleaseSpecifyTeam')}</div>`;
					return;
				}
				const data = await ReportApi.getTeamReport(teamId, ym);
				this.renderTeamReport(data);

			} else if (this.activeReportType === 'absences') {
				const data = await ReportApi.getAbsenceReport(this.filters.absences);
				this.renderAbsenceReport(data);

			} else if (this.activeReportType === 'on-call') {
				const data = await ReportApi.getOnCallReport(this.filters.onCall);
				this.renderOnCallReport(data);
			}
		} catch (err) {
			console.error('Error generating report', err);
			this.resultsContainer.innerHTML = `<div class="error-msg">${err.message || I18n.t('app.error')}</div>`;
		}
	}

	async exportCsv() {
		try {
			if (this.activeReportType === 'day') {
				const date = this.filters.day.date;
				if (!date) {
					NotificationDialog.error(I18n.t('reports.pleaseSpecifyDate'));
					return;
				}
				await ReportApi.downloadDayReportCsv(date, this.filters.day.employeeId);

			} else if (this.activeReportType === 'month') {
				const ym = this.filters.month.yearMonth;
				if (!ym) {
					NotificationDialog.error(I18n.t('reports.pleaseSpecifyMonth'));
					return;
				}
				await ReportApi.downloadMonthReportCsv(ym, this.filters.month.employeeId);

			} else if (this.activeReportType === 'vacation') {
				await ReportApi.downloadVacationReportCsv(this.filters.vacation.year, this.filters.vacation.employeeId);

			} else if (this.activeReportType === 'team') {
				if (!this.canViewTeamReport()) {
					NotificationDialog.error(I18n.t('errors.forbidden'));
					return;
				}
				const teamId = this.filters.team.teamId;
				const ym = this.filters.team.yearMonth;
				if (!teamId || !ym) {
					NotificationDialog.error(I18n.t('reports.pleaseSpecifyTeam'));
					return;
				}
				await ReportApi.downloadTeamReportCsv(teamId, ym);

			} else if (this.activeReportType === 'absences') {
				await ReportApi.downloadAbsenceReportCsv(this.filters.absences);

			} else if (this.activeReportType === 'on-call') {
				await ReportApi.downloadOnCallReportCsv(this.filters.onCall);
			}
		} catch (err) {
			console.error('Error exporting CSV', err);
			NotificationDialog.error(`${I18n.t('app.error')}: ${err.message}`);
		}
	}

	async exportPdf() {
		try {
			const lang = (window.I18n && window.I18n.getLanguage) ? window.I18n.getLanguage() : 'de';
			if (this.activeReportType === 'month') {
				const ym = this.filters.month.yearMonth;
				if (!ym) {
					NotificationDialog.error(I18n.t('reports.pleaseSpecifyMonth'));
					return;
				}
				await ReportApi.downloadMonthReportPdf(ym, this.filters.month.employeeId, lang);

			} else if (this.activeReportType === 'vacation') {
				await ReportApi.downloadVacationReportPdf(this.filters.vacation.year, this.filters.vacation.employeeId, lang);

			} else if (this.activeReportType === 'absences') {
				const params = { ...this.filters.absences, lang };
				await ReportApi.downloadAbsenceReportPdf(params);

			} else if (this.activeReportType === 'on-call') {
				const params = { ...this.filters.onCall, lang };
				await ReportApi.downloadOnCallReportPdf(params);

			} else {
				NotificationDialog.error(I18n.t('reports.pdfOnlySupportedForMonthVacationAbsence') || 'PDF export is available for Month, Vacation, Absence, and On-Call reports.');
			}
		} catch (err) {
			console.error('Error exporting PDF', err);
			NotificationDialog.error(`${I18n.t('app.error')}: ${err.message}`);
		}
	}

	renderDayReport(data) {
		if (!data) {
			this.resultsContainer.innerHTML = `<div class="empty-state">${I18n.t('common.noData')}</div>`;
			return;
		}

		const balanceClass = data.balance > 0 ? 'positive' : (data.balance < 0 ? 'negative' : 'neutral');
		const balanceSign = data.balance > 0 ? '+' : '';

		let entriesHtml = '';
		if (!data.workEntries || data.workEntries.length === 0) {
			entriesHtml = `<tr><td colspan="7" class="empty-cell">${I18n.t('times.noEntries')}</td></tr>`;
		} else {
			entriesHtml = data.workEntries.map(entry => {
				const sourceBadge = entry.source === 'MANUAL'
						? `<span class="badge badge-manual" style="background: #fef3c7; color: #92400e; padding: 2px 6px; border-radius: 4px; font-size: 0.8rem; font-weight: 500;">${I18n.t('times.manualBadge')}</span>`
						: `<span class="badge badge-timer" style="background: #e0e7ff; color: #3730a3; padding: 2px 6px; border-radius: 4px; font-size: 0.8rem; font-weight: 500;">${I18n.t('times.timerBadge')}</span>`;
				const modifiedBadge = entry.modified
						? `<span class="badge badge-modified" style="background: #fed7aa; color: #9a3412; padding: 2px 6px; border-radius: 4px; font-size: 0.8rem; font-weight: 500;">${I18n.t('times.modifiedBadge')}</span>`
						: '-';
				const onCallBadge = entry.isOnCall
						? `<span class="badge badge-on-call" style="background: #e0f2fe; color: #0369a1; padding: 2px 6px; border-radius: 4px; font-size: 0.8rem; font-weight: 500; margin-left: 4px;">${I18n.t('times.onCallBadge')}</span>`
						: '';
				const creatorAttr = entry.createdBy
						? `<span style="font-size: 0.75rem; color: #6b7280;">${entry.createdBy}</span>`
						: '-';

				return `
					<tr>
						<td>${Format.dateTime(entry.start)}</td>
						<td>${entry.end ? Format.dateTime(entry.end) : `<span class="status-badge state-open">${I18n.t('reports.inProgress')}</span>`}</td>
						<td><strong>${Format.duration(entry.durationMinutes)}</strong></td>
						<td>${sourceBadge}${onCallBadge}</td>
						<td>${modifiedBadge}</td>
						<td>${creatorAttr}</td>
						<td>${entry.id}</td>
					</tr>
				`;
			}).join('');
		}

		let breaksHtml = '';
		if (data.breaks && data.breaks.length > 0) {
			breaksHtml = data.breaks.map(b => `
				<tr>
					<td>${Format.dateTime(b.start)}</td>
					<td>${Format.dateTime(b.end)}</td>
					<td><span class="break-tag">${Format.duration(b.durationMinutes)}</span></td>
					<td>${b.durationMinutes} min</td>
				</tr>
			`).join('');
		}

		const stateLabel = I18n.t(`enums.dayState.${data.state}`, {}, data.stateLabel || data.state);
		const targetEmpId = data.employeeId || this.filters.day?.employeeId || AuthApi.getUserId();
		const empDisplay = this.getEmployeeDisplay(targetEmpId, data);

		this.resultsContainer.innerHTML = `
			<div class="report-result-header">
				<h3>${I18n.t('reports.dayReport')}: ${data.date}</h3>
				<span class="report-emp-tag">${I18n.t('common.employee')}: ${empDisplay}</span>
				<span class="status-badge state-${(data.state || 'OPEN').toLowerCase()}">${stateLabel}</span>
			</div>

			<!-- Summary Cards Grid -->
			<div class="summary-grid report-summary-grid">
				<div class="summary-card">
					<div class="card-title">${I18n.t('times.targetTime')}</div>
					<div class="card-value">${Format.duration(data.targetMinutes)}</div>
					<div class="card-sub">${data.targetMinutes} min</div>
				</div>
				<div class="summary-card">
					<div class="card-title">${I18n.t('times.actualTime')}</div>
					<div class="card-value">${Format.duration(data.actualMinutes)}</div>
					<div class="card-sub">${data.actualMinutes} min</div>
				</div>
				<div class="summary-card">
					<div class="card-title">${I18n.t('periods.holidayHours')}</div>
					<div class="card-value">${Format.duration(data.holidayMinutes)}</div>
					<div class="card-sub">${data.holidayMinutes} min</div>
				</div>
				<div class="summary-card">
					<div class="card-title">${I18n.t('periods.paidAbsence')}</div>
					<div class="card-value">${Format.duration(data.absenceMinutes)}</div>
					<div class="card-sub">${data.absenceMinutes} min</div>
				</div>
				<div class="summary-card highlight-card">
					<div class="card-title">${I18n.t('reports.dayBalance')}</div>
					<div class="card-value ${balanceClass}">${balanceSign}${Format.duration(data.balance)}</div>
					<div class="card-sub">${balanceSign}${data.balance} min</div>
				</div>
			</div>

			<!-- Work Entries Section -->
			<div class="report-section card">
				<h4>${I18n.t('reports.workBlocks')}</h4>
				<div class="table-container">
					<table class="data-table">
						<thead>
							<tr>
								<th>${I18n.t('times.startTime')}</th>
								<th>${I18n.t('times.endTime')}</th>
								<th>${I18n.t('common.duration')}</th>
								<th>${I18n.t('times.source')}</th>
								<th>${I18n.t('times.modified')}</th>
								<th>${I18n.t('common.creator')}</th>
								<th>ID</th>
							</tr>
						</thead>
						<tbody>
							${entriesHtml}
						</tbody>
					</table>
				</div>
			</div>

			<!-- Breaks & Interruptions Section -->
			${data.breaks && data.breaks.length > 0 ? `
				<div class="report-section card">
					<h4>${I18n.t('reports.workInterruptions')}</h4>
					<div class="table-container">
						<table class="data-table">
							<thead>
								<tr>
									<th>${I18n.t('reports.breakStart')}</th>
									<th>${I18n.t('reports.breakEnd')}</th>
									<th>${I18n.t('common.duration')}</th>
									<th>${I18n.t('common.minutes')}</th>
								</tr>
							</thead>
							<tbody>
								${breaksHtml}
							</tbody>
						</table>
					</div>
				</div>
			` : ''}
		`;
	}

	renderMonthReport(data) {
		if (!data) {
			this.resultsContainer.innerHTML = `<div class="empty-state">${I18n.t('common.noData')}</div>`;
			return;
		}

		const periodBalClass = data.periodBalanceMinutes > 0 ? 'positive' : (data.periodBalanceMinutes < 0 ? 'negative' : 'neutral');
		const periodBalSign = data.periodBalanceMinutes > 0 ? '+' : '';
		const endBalClass = data.endBalanceMinutes > 0 ? 'positive' : (data.endBalanceMinutes < 0 ? 'negative' : 'neutral');
		const endBalSign = data.endBalanceMinutes > 0 ? '+' : '';

		let daysHtml = '';
		if (!data.daySummaries || data.daySummaries.length === 0) {
			daysHtml = `<tr><td colspan="8" class="empty-cell">${I18n.t('periods.noDailyRecords')}</td></tr>`;
		} else {
			daysHtml = data.daySummaries.map(day => {
				const dayBalClass = day.balance > 0 ? 'positive' : (day.balance < 0 ? 'negative' : 'neutral');
				const dayBalSign = day.balance > 0 ? '+' : '';
				const isWeekend = day.isOff && day.targetMinutes === 0;
				const dayStateText = I18n.t(`enums.dayState.${day.state}`, {}, day.stateLabel || day.state);
				const onCallTag = day.onCallMinutes > 0
						? `<span class="badge badge-on-call" style="background: #e0f2fe; color: #0369a1; padding: 2px 6px; border-radius: 4px; font-size: 0.75rem; font-weight: 500; margin-left: 4px;">${I18n.t('times.onCallBadge')} (${Format.duration(day.onCallMinutes)})</span>`
						: '';

				return `
					<tr class="${isWeekend ? 'row-off' : ''}">
						<td><strong>${day.date}</strong>${onCallTag}</td>
						<td>${Format.duration(day.targetMinutes)}</td>
						<td>${Format.duration(day.actualMinutes)}</td>
						<td>${day.holidayMinutes > 0 ? Format.duration(day.holidayMinutes) : '-'}</td>
						<td>${day.absenceMinutes > 0 ? Format.duration(day.absenceMinutes) : '-'}</td>
						<td class="${dayBalClass}"><strong>${dayBalSign}${Format.duration(day.balance)}</strong></td>
						<td>${day.workEntries ? day.workEntries.length : 0}</td>
						<td><span class="status-badge state-${(day.state || 'OPEN').toLowerCase()}">${dayStateText}</span></td>
					</tr>
				`;
			}).join('');
		}

		const targetEmpId = data.employeeId || this.filters.month?.employeeId || AuthApi.getUserId();
		const empDisplay = this.getEmployeeDisplay(targetEmpId, data);

		this.resultsContainer.innerHTML = `
			<div class="report-result-header">
				<h3>${I18n.t('reports.monthReport')}: ${data.yearMonth}</h3>
				<span class="report-emp-tag">${I18n.t('common.employee')}: ${empDisplay}</span>
			</div>

			<!-- Summary Metrics Grid -->
			<div class="summary-grid report-summary-grid">
				<div class="summary-card">
					<div class="card-title">${I18n.t('times.targetTime')}</div>
					<div class="card-value">${Format.duration(data.totalTargetMinutes)}</div>
					<div class="card-sub">${data.totalTargetMinutes} min</div>
				</div>
				<div class="summary-card">
					<div class="card-title">${I18n.t('times.actualTime')}</div>
					<div class="card-value">${Format.duration(data.totalActualMinutes)}</div>
					<div class="card-sub">${data.totalActualMinutes} min</div>
				</div>
				<div class="summary-card">
					<div class="card-title">${I18n.t('periods.holidayHours')}</div>
					<div class="card-value">${Format.duration(data.totalHolidayMinutes)}</div>
					<div class="card-sub">${data.totalHolidayMinutes} min</div>
				</div>
				<div class="summary-card">
					<div class="card-title">${I18n.t('periods.paidAbsence')}</div>
					<div class="card-value">${Format.duration(data.paidAbsenceMinutes ?? data.totalPaidAbsenceMinutes ?? data.totalAbsenceMinutes ?? 0)}</div>
					<div class="card-sub">${data.paidAbsenceMinutes ?? data.totalPaidAbsenceMinutes ?? data.totalAbsenceMinutes ?? 0} min</div>
				</div>
				<div class="summary-card">
					<div class="card-title">${I18n.t('onCall.totalOnCallTime')}</div>
					<div class="card-value">${Format.duration(data.totalOnCallMinutes || 0)}</div>
					<div class="card-sub">${data.totalOnCallMinutes || 0} min</div>
				</div>
				<div class="summary-card">
					<div class="card-title">${I18n.t('reports.initialBalance')}</div>
					<div class="card-value">${Format.duration(data.initialBalanceMinutes)}</div>
					<div class="card-sub">${data.initialBalanceMinutes} min</div>
				</div>
				<div class="summary-card">
					<div class="card-title">${I18n.t('reports.periodBalance')}</div>
					<div class="card-value ${periodBalClass}">${periodBalSign}${Format.duration(data.periodBalanceMinutes)}</div>
					<div class="card-sub">${periodBalSign}${data.periodBalanceMinutes} min</div>
				</div>
				<div class="summary-card highlight-card">
					<div class="card-title">${I18n.t('reports.endBalance')}</div>
					<div class="card-value ${endBalClass}">${endBalSign}${Format.duration(data.endBalanceMinutes)}</div>
					<div class="card-sub">${endBalSign}${data.endBalanceMinutes} min</div>
				</div>
			</div>

			<!-- Daily Breakdown Table -->
			<div class="report-section card">
				<h4>${I18n.t('reports.dailyBreakdown')}</h4>
				<div class="table-container">
					<table class="data-table">
						<thead>
							<tr>
								<th>${I18n.t('common.date')}</th>
								<th>${I18n.t('common.target')}</th>
								<th>${I18n.t('common.actual')}</th>
								<th>${I18n.t('periods.holiday')}</th>
								<th>${I18n.t('periods.absence')}</th>
								<th>${I18n.t('reports.dayBalance')}</th>
								<th>${I18n.t('reports.workBlocks')}</th>
								<th>${I18n.t('common.status')}</th>
							</tr>
						</thead>
						<tbody>
							${daysHtml}
						</tbody>
					</table>
				</div>
			</div>
		`;
	}

	renderVacationReport(data) {
		if (!data) {
			this.resultsContainer.innerHTML = `<div class="empty-state">${I18n.t('common.noData')}</div>`;
			return;
		}

		const entitlement = data.entitlementMinutes ?? data.annualEntitlementMinutes ?? 0;
		const carryOver = data.carryOverMinutes ?? 0;
		const corrections = data.correctionsMinutes ?? data.correctionMinutes ?? 0;
		const usage = data.usageMinutes ?? data.takenMinutes ?? 0;
		const planned = data.plannedMinutes ?? 0;
		const remaining = data.remainingMinutes ?? data.remainingBalanceMinutes ?? 0;
		const remBalClass = remaining >= 0 ? 'positive' : 'negative';

		let entriesHtml = '';
		if (!data.entries || data.entries.length === 0) {
			entriesHtml = `<tr><td colspan="7" class="empty-cell">${I18n.t('absences.noJournalEntries')}</td></tr>`;
		} else {
			entriesHtml = data.entries.map(entry => {
				const rawEntryType = entry.vacationType || entry.bookingType || entry.entryType || entry.type;
				const entryType = rawEntryType ? String(rawEntryType).toUpperCase() : null;
				const bTypeLabel = entryType ? I18n.t(`enums.vacationEntryType.${entryType}`, {}, entryType) : '-';
				const amountVal = entry.valueMinutes !== undefined ? entry.valueMinutes : (entry.value !== undefined ? entry.value : (entry.amountMinutes !== undefined ? entry.amountMinutes : 0));
				return `
					<tr>
						<td>${Format.date(entry.date)}</td>
						<td><span class="status-badge">${bTypeLabel}</span></td>
						<td><strong>${Format.durationDays(amountVal)}</strong></td>
						<td>${entry.targetPeriod || '-'}</td>
						<td>${entry.comment || '-'}</td>
						<td>${entry.createdBy || '-'}</td>
						<td>${Format.dateTime(entry.createdAt || entry.date)}</td>
					</tr>
				`;
			}).join('');
		}

		const targetEmpId = data.employeeId || this.filters.vacation?.employeeId || AuthApi.getUserId();
		const empDisplay = this.getEmployeeDisplay(targetEmpId, data);

		this.resultsContainer.innerHTML = `
			<div class="report-result-header">
				<h3>${I18n.t('reports.vacationReport')}: ${I18n.t('common.year')} ${data.year || new Date().getFullYear()}</h3>
				<span class="report-emp-tag">${I18n.t('common.employee')}: ${empDisplay}</span>
			</div>

			<!-- Summary Grid -->
			<div class="summary-grid report-summary-grid">
				<div class="summary-card">
					<div class="card-title">${I18n.t('reports.annualEntitlement')}</div>
					<div class="card-value">${Format.durationDays(entitlement)}</div>
					<div class="card-sub">${entitlement} min</div>
				</div>
				<div class="summary-card">
					<div class="card-title">${I18n.t('reports.carryOver')}</div>
					<div class="card-value">${Format.durationDays(carryOver)}</div>
					<div class="card-sub">${carryOver} min</div>
				</div>
				<div class="summary-card">
					<div class="card-title">${I18n.t('reports.corrections')}</div>
					<div class="card-value">${Format.durationDays(corrections)}</div>
					<div class="card-sub">${corrections} min</div>
				</div>
				<div class="summary-card">
					<div class="card-title">${I18n.t('reports.takenUsed')}</div>
					<div class="card-value">${Format.durationDays(usage)}</div>
					<div class="card-sub">${usage} min</div>
				</div>
				<div class="summary-card">
					<div class="card-title">${I18n.t('reports.plannedFuture')}</div>
					<div class="card-value">${Format.durationDays(planned)}</div>
					<div class="card-sub">${planned} min</div>
				</div>
				<div class="summary-card highlight-card">
					<div class="card-title">${I18n.t('reports.remainingBalance')}</div>
					<div class="card-value ${remBalClass}">${Format.durationDays(remaining)}</div>
					<div class="card-sub">${remaining} min</div>
				</div>
			</div>

			<!-- Vacation Journal Entries Table -->
			<div class="report-section card">
				<h4>${I18n.t('reports.vacationJournalTransactions')}</h4>
				<div class="table-container">
					<table class="data-table">
						<thead>
							<tr>
								<th>${I18n.t('common.effectiveDate')}</th>
								<th>${I18n.t('reports.bookingType')}</th>
								<th>${I18n.t('reports.valueDaysDuration')}</th>
								<th>${I18n.t('reports.targetPeriod')}</th>
								<th>${I18n.t('common.comment')}</th>
								<th>${I18n.t('common.createdBy')}</th>
								<th>${I18n.t('common.createdAt')}</th>
							</tr>
						</thead>
						<tbody>
							${entriesHtml}
						</tbody>
					</table>
				</div>
			</div>
		`;
	}

	renderTeamReport(data) {
		if (!data) {
			this.resultsContainer.innerHTML = `<div class="empty-state">${I18n.t('common.noData')}</div>`;
			return;
		}

		let totalTarget = 0;
		let totalActual = 0;
		let totalMissing = 0;
		let totalPeriodBal = 0;

		const employees = data.employees || [];
		employees.forEach(e => {
			totalTarget += e.targetMinutes || 0;
			totalActual += e.actualMinutes || 0;
			totalMissing += e.missingBookingsCount || 0;
			totalPeriodBal += e.periodBalanceMinutes || 0;
		});

		const totalBalClass = totalPeriodBal > 0 ? 'positive' : (totalPeriodBal < 0 ? 'negative' : 'neutral');
		const totalBalSign = totalPeriodBal > 0 ? '+' : '';

		let rowsHtml = '';
		if (employees.length === 0) {
			rowsHtml = `<tr><td colspan="10" class="empty-cell">${I18n.t('presence.noEmployeesFound')}</td></tr>`;
		} else {
			rowsHtml = employees.map(emp => {
				const balClass = emp.periodBalanceMinutes > 0 ? 'positive' : (emp.periodBalanceMinutes < 0 ? 'negative' : 'neutral');
				const balSign = emp.periodBalanceMinutes > 0 ? '+' : '';
				const missingBadge = emp.missingBookingsCount > 0 
					? `<span class="badge badge-warning">${emp.missingBookingsCount} ${I18n.t('reports.missingBookings')}</span>`
					: `<span class="badge badge-success">0</span>`;
				const pStateLabel = I18n.t(`enums.periodState.${emp.periodState}`, {}, emp.periodState);

				return `
					<tr>
						<td><strong>${emp.employeeName}</strong><br><small class="text-muted">${emp.employeeId}</small></td>
						<td>${Format.duration(emp.targetMinutes)}</td>
						<td>${Format.duration(emp.actualMinutes)}</td>
						<td>${emp.holidayMinutes > 0 ? Format.duration(emp.holidayMinutes) : '-'}</td>
						<td>${emp.absenceMinutes > 0 ? Format.duration(emp.absenceMinutes) : '-'}</td>
						<td>${Format.duration(emp.initialBalanceMinutes)}</td>
						<td class="${balClass}"><strong>${balSign}${Format.duration(emp.periodBalanceMinutes)}</strong></td>
						<td><strong>${Format.duration(emp.endBalanceMinutes)}</strong></td>
						<td><span class="status-badge state-${(emp.periodState || 'OPEN').toLowerCase()}">${pStateLabel}</span></td>
						<td>${missingBadge}</td>
					</tr>
				`;
			}).join('');
		}

		this.resultsContainer.innerHTML = `
			<div class="report-result-header">
				<h3>${I18n.t('reports.teamReport')}: ${data.teamName}</h3>
				<span class="report-emp-tag">${I18n.t('common.month')}: ${data.yearMonth}</span>
			</div>

			<!-- Team Summary Cards -->
			<div class="summary-grid report-summary-grid">
				<div class="summary-card">
					<div class="card-title">${I18n.t('reports.teamMembers')}</div>
					<div class="card-value">${employees.length}</div>
					<div class="card-sub">${I18n.t('reports.activeEmployees')}</div>
				</div>
				<div class="summary-card">
					<div class="card-title">${I18n.t('reports.totalTarget')}</div>
					<div class="card-value">${Format.duration(totalTarget)}</div>
					<div class="card-sub">${totalTarget} min</div>
				</div>
				<div class="summary-card">
					<div class="card-title">${I18n.t('reports.totalWorked')}</div>
					<div class="card-value">${Format.duration(totalActual)}</div>
					<div class="card-sub">${totalActual} min</div>
				</div>
				<div class="summary-card">
					<div class="card-title">${I18n.t('reports.netBalance')}</div>
					<div class="card-value ${totalBalClass}">${totalBalSign}${Format.duration(totalPeriodBal)}</div>
					<div class="card-sub">${totalBalSign}${totalPeriodBal} min</div>
				</div>
				<div class="summary-card ${totalMissing > 0 ? 'warning-card' : ''}">
					<div class="card-title">${I18n.t('reports.missingBookings')}</div>
					<div class="card-value">${totalMissing}</div>
					<div class="card-sub">${I18n.t('reports.unrecordedTargetDays')}</div>
				</div>
			</div>

			<!-- Team Members Table -->
			<div class="report-section card">
				<h4>${I18n.t('reports.employeeSummaries')}</h4>
				<div class="table-container">
					<table class="data-table">
						<thead>
							<tr>
								<th>${I18n.t('common.employee')}</th>
								<th>${I18n.t('common.target')}</th>
								<th>${I18n.t('common.actual')}</th>
								<th>${I18n.t('periods.holiday')}</th>
								<th>${I18n.t('periods.absence')}</th>
								<th>${I18n.t('reports.initialBalance')}</th>
								<th>${I18n.t('reports.periodBalance')}</th>
								<th>${I18n.t('reports.endBalance')}</th>
								<th>${I18n.t('reports.periodStatus')}</th>
								<th>${I18n.t('reports.missingBookings')}</th>
							</tr>
						</thead>
						<tbody>
							${rowsHtml}
						</tbody>
					</table>
				</div>
			</div>
		`;
	}

	renderAbsenceReport(data) {
		const items = data && data.items ? data.items : [];

		let totalMinutes = 0;
		let paidMinutes = 0;
		let unpaidMinutes = 0;
		let approvedCount = 0;

		items.forEach(item => {
			totalMinutes += item.minutes || 0;
			if (item.paid) {
				paidMinutes += item.minutes || 0;
			} else {
				unpaidMinutes += item.minutes || 0;
			}
			if (item.state === 'APPROVED') {
				approvedCount++;
			}
		});

		let rowsHtml = '';
		if (items.length === 0) {
			rowsHtml = `<tr><td colspan="9" class="empty-cell">${I18n.t('absences.noAbsences')}</td></tr>`;
		} else {
			rowsHtml = items.map(item => {
				const paidBadge = item.paid
					? `<span class="badge badge-success">${I18n.t('reports.paid')}</span>`
					: `<span class="badge badge-neutral">${I18n.t('reports.unpaid')}</span>`;
				const aStateLabel = I18n.t(`enums.absenceState.${item.state}`, {}, item.state);

				return `
					<tr>
						<td><strong>${item.employeeName || item.employeeId}</strong><br><small class="text-muted">${item.employeeId}</small></td>
						<td><span class="status-badge">${item.absenceTypeName || item.absenceTypeCode}</span></td>
						<td>${Format.date(item.start)} - ${Format.date(item.end)}</td>
						<td>${item.durationType || '-'} ${item.dayPart ? `(${item.dayPart})` : ''}</td>
						<td><strong>${Format.duration(item.minutes)}</strong> (${item.minutes}m)</td>
						<td><span class="status-badge state-${(item.state || 'SUBMITTED').toLowerCase()}">${aStateLabel}</span></td>
						<td>${paidBadge}</td>
						<td>${item.approvedBy ? `${item.approvedBy}<br><small class="text-muted">${Format.dateTime(item.approvedAt)}</small>` : '-'}</td>
						<td>${item.comment || '-'}</td>
					</tr>
				`;
			}).join('');
		}

		this.resultsContainer.innerHTML = `
			<div class="report-result-header">
				<h3>${I18n.t('reports.absencesReport')}</h3>
				<span class="report-emp-tag">${items.length} ${I18n.t('reports.records')}</span>
			</div>

			<!-- Summary Cards Grid -->
			<div class="summary-grid report-summary-grid">
				<div class="summary-card">
					<div class="card-title">${I18n.t('reports.totalAbsences')}</div>
					<div class="card-value">${items.length}</div>
					<div class="card-sub">${approvedCount} ${I18n.t('reports.approved')}</div>
				</div>
				<div class="summary-card">
					<div class="card-title">${I18n.t('reports.totalDuration')}</div>
					<div class="card-value">${Format.duration(totalMinutes)}</div>
					<div class="card-sub">${Format.durationDays(totalMinutes)}</div>
				</div>
				<div class="summary-card">
					<div class="card-title">${I18n.t('reports.paidAbsenceTime')}</div>
					<div class="card-value">${Format.duration(paidMinutes)}</div>
					<div class="card-sub">${Format.durationDays(paidMinutes)}</div>
				</div>
				<div class="summary-card">
					<div class="card-title">${I18n.t('reports.unpaidAbsenceTime')}</div>
					<div class="card-value">${Format.duration(unpaidMinutes)}</div>
					<div class="card-sub">${Format.durationDays(unpaidMinutes)}</div>
				</div>
			</div>

			<!-- Absences Table -->
			<div class="report-section card">
				<h4>${I18n.t('reports.absencesListing')}</h4>
				<div class="table-container">
					<table class="data-table">
						<thead>
							<tr>
								<th>${I18n.t('common.employee')}</th>
								<th>${I18n.t('absences.absenceType')}</th>
								<th>${I18n.t('reports.dateRange')}</th>
								<th>${I18n.t('absences.durationType')}</th>
								<th>${I18n.t('common.duration')}</th>
								<th>${I18n.t('common.status')}</th>
								<th>${I18n.t('reports.remuneration')}</th>
								<th>${I18n.t('common.createdBy')}</th>
								<th>${I18n.t('common.comment')}</th>
							</tr>
						</thead>
						<tbody>
							${rowsHtml}
						</tbody>
					</table>
				</div>
			</div>
		`;
	}

	renderOnCallReport(data) {
		const periods = data && data.periods ? data.periods : [];
		const workEntries = data && data.workEntries ? data.workEntries : [];
		const totalPeriods = data && data.totalPeriodsCount != null ? data.totalPeriodsCount : periods.length;
		const totalEntries = data && data.totalWorkEntriesCount != null ? data.totalWorkEntriesCount : workEntries.length;
		const totalMinutes = data && data.totalWorkEntryMinutes != null ? data.totalWorkEntryMinutes : 0;

		let periodsRowsHtml = '';
		if (periods.length === 0) {
			periodsRowsHtml = `<tr><td colspan="6" class="empty-cell">${I18n.t('onCall.noPeriodsFound') || I18n.t('common.noData')}</td></tr>`;
		} else {
			periodsRowsHtml = periods.map(p => `
				<tr>
					<td>${p.employeeName || p.employeeId}</td>
					<td>${Format.date(p.startDate)}</td>
					<td>${Format.date(p.endDate)}</td>
					<td>${p.startTime || '00:00'}</td>
					<td>${p.endTime || '23:59'}</td>
					<td>${p.comment || '-'}</td>
				</tr>
			`).join('');
		}

		let workEntriesRowsHtml = '';
		if (workEntries.length === 0) {
			workEntriesRowsHtml = `<tr><td colspan="7" class="empty-cell">${I18n.t('times.noEntries')}</td></tr>`;
		} else {
			workEntriesRowsHtml = workEntries.map(w => {
				const sourceBadge = w.source === 'MANUAL'
					? `<span class="badge badge-manual" style="background: #fef3c7; color: #92400e; padding: 2px 6px; border-radius: 4px; font-size: 0.8rem; font-weight: 500;">${I18n.t('times.manualBadge')}</span>`
					: `<span class="badge badge-timer" style="background: #e0e7ff; color: #3730a3; padding: 2px 6px; border-radius: 4px; font-size: 0.8rem; font-weight: 500;">${I18n.t('times.timerBadge')}</span>`;
				const modifiedBadge = w.modified
					? `<span class="badge badge-modified" style="background: #fed7aa; color: #9a3412; padding: 2px 6px; border-radius: 4px; font-size: 0.8rem; font-weight: 500;">${I18n.t('times.modifiedBadge')}</span>`
					: '-';

				return `
					<tr>
						<td>${w.employeeName || w.employeeId}</td>
						<td>${Format.date(w.date)}</td>
						<td>${Format.dateTime(w.start)}</td>
						<td>${w.end ? Format.dateTime(w.end) : `<span class="status-badge state-open">${I18n.t('reports.inProgress')}</span>`}</td>
						<td><strong>${Format.duration(w.durationMinutes)}</strong> (${w.durationMinutes}m)</td>
						<td>${sourceBadge} ${modifiedBadge}</td>
						<td>${w.comment || '-'}</td>
					</tr>
				`;
			}).join('');
		}

		this.resultsContainer.innerHTML = `
			<div class="report-result-header">
				<h3>${I18n.t('reports.onCallReport')}</h3>
				<span class="report-emp-tag">${totalPeriods} ${I18n.t('onCall.periods')}, ${totalEntries} ${I18n.t('onCall.deployments')}</span>
			</div>

			<!-- Summary Cards Grid -->
			<div class="summary-grid report-summary-grid">
				<div class="summary-card">
					<div class="card-title">${I18n.t('onCall.totalOnCallPeriods')}</div>
					<div class="card-value">${totalPeriods}</div>
					<div class="card-sub">${I18n.t('onCall.configuredPeriods')}</div>
				</div>
				<div class="summary-card">
					<div class="card-title">${I18n.t('onCall.totalDeployments')}</div>
					<div class="card-value">${totalEntries}</div>
					<div class="card-sub">${I18n.t('onCall.onCallDeployments')}</div>
				</div>
				<div class="summary-card highlight-card">
					<div class="card-title">${I18n.t('onCall.totalOnCallTime')}</div>
					<div class="card-value">${Format.duration(totalMinutes)}</div>
					<div class="card-sub">${totalMinutes} min</div>
				</div>
			</div>

			<!-- On-Call Periods Section -->
			<div class="report-section card">
				<h4>${I18n.t('onCall.periods')}</h4>
				<div class="table-container">
					<table class="data-table">
						<thead>
							<tr>
								<th>${I18n.t('common.employee')}</th>
								<th>${I18n.t('common.startDate')}</th>
								<th>${I18n.t('common.endDate')}</th>
								<th>${I18n.t('onCall.startTime')}</th>
								<th>${I18n.t('onCall.endTime')}</th>
								<th>${I18n.t('common.comment')}</th>
							</tr>
						</thead>
						<tbody>
							${periodsRowsHtml}
						</tbody>
					</table>
				</div>
			</div>

			<!-- On-Call Work Entries Section -->
			<div class="report-section card">
				<h4>${I18n.t('onCall.deployments')}</h4>
				<div class="table-container">
					<table class="data-table">
						<thead>
							<tr>
								<th>${I18n.t('common.employee')}</th>
								<th>${I18n.t('common.date')}</th>
								<th>${I18n.t('times.startTime')}</th>
								<th>${I18n.t('times.endTime')}</th>
								<th>${I18n.t('common.duration')}</th>
								<th>${I18n.t('times.source')}</th>
								<th>${I18n.t('common.comment')}</th>
							</tr>
						</thead>
						<tbody>
							${workEntriesRowsHtml}
						</tbody>
					</table>
				</div>
			</div>
		`;
	}
}
