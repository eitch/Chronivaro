import ReportApi from '../api/ReportApi.js';
import AbsenceTypeApi from '../api/AbsenceTypeApi.js';
import TeamApi from '../api/TeamApi.js';
import Format from '../utils/Format.js';

export default class ReportsView {

	constructor(app) {
		this.app = app;
		this.activeReportType = 'day'; // 'day', 'month', 'vacation', 'team', 'absences'

		const now = new Date();
		const year = now.getFullYear();
		const month = String(now.getMonth() + 1).padStart(2, '0');
		const day = String(now.getDate()).padStart(2, '0');

		this.filters = {
			day: {
				date: `${year}-${month}-${day}`,
				employeeId: ''
			},
			month: {
				yearMonth: `${year}-${month}`,
				employeeId: ''
			},
			vacation: {
				year: year,
				employeeId: ''
			},
			team: {
				teamId: '',
				yearMonth: `${year}-${month}`
			},
			absences: {
				from: `${year}-${month}-01`,
				to: `${year}-${month}-${day}`,
				employeeId: '',
				type: '',
				state: ''
			}
		};

		this.absenceTypes = [];
		this.teams = [];
	}

	async render(params) {
		if (params && params.type) {
			this.activeReportType = params.type;
		}

		const container = document.createElement('div');
		container.id = 'reports-view';
		container.innerHTML = `
			<div class="view-header">
				<h2>Reports & CSV Export</h2>
			</div>

			<!-- Report Type Selector -->
			<div class="tabs-container">
				<div class="tab-buttons">
					<button id="report-type-day-btn" class="tab-btn ${this.activeReportType === 'day' ? 'active' : ''}">
						Day Report
					</button>
					<button id="report-type-month-btn" class="tab-btn ${this.activeReportType === 'month' ? 'active' : ''}">
						Month Report
					</button>
					<button id="report-type-vacation-btn" class="tab-btn ${this.activeReportType === 'vacation' ? 'active' : ''}">
						Vacation Account
					</button>
					<button id="report-type-team-btn" class="tab-btn ${this.activeReportType === 'team' ? 'active' : ''}">
						Team Report
					</button>
					<button id="report-type-absences-btn" class="tab-btn ${this.activeReportType === 'absences' ? 'active' : ''}">
						Absences Report
					</button>
				</div>
			</div>

			<!-- Filter Controls Section -->
			<section class="card report-filter-card">
				<div class="filter-bar" id="report-filter-bar">
					<!-- Dynamic Filter Fields will be injected here -->
				</div>
				<div class="report-actions-bar">
					<button id="btn-run-report" class="primary-btn">Generate Report</button>
					<button id="btn-export-csv" class="secondary-btn btn-export">
						<span class="icon">📥</span> Export CSV (UTF-8 BOM)
					</button>
				</div>
			</section>

			<!-- Report Content Container -->
			<div id="report-results-container">
				<div class="empty-state">Select report parameters and click <strong>Generate Report</strong>.</div>
			</div>
		`;

		this.container = container;
		this.filterBar = container.querySelector('#report-filter-bar');
		this.resultsContainer = container.querySelector('#report-results-container');
		this.runBtn = container.querySelector('#btn-run-report');
		this.exportBtn = container.querySelector('#btn-export-csv');

		this.setupTabs(container);
		this.renderFilterFields();

		this.runBtn.addEventListener('click', () => this.generateReport());
		this.exportBtn.addEventListener('click', () => this.exportCsv());

		// Load background data for selects
		this.loadReferenceData();

		// Auto run default report
		this.generateReport();

		return container;
	}

	setupTabs(container) {
		const types = ['day', 'month', 'vacation', 'team', 'absences'];
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
			const types = await AbsenceTypeApi.getAbsenceTypes();
			this.absenceTypes = types || [];
			if (this.activeReportType === 'absences') {
				this.populateAbsenceTypeSelect();
			}
		} catch (e) {
			console.warn('Could not load absence types', e);
		}

		try {
			const teams = await TeamApi.getAll();
			this.teams = teams || [];
			if (this.activeReportType === 'team') {
				this.populateTeamSelect();
			}
		} catch (e) {
			console.warn('Could not load teams', e);
		}
	}

	populateAbsenceTypeSelect() {
		const select = this.container.querySelector('#filter-absence-type');
		if (!select) return;
		const currentVal = select.value;
		select.innerHTML = '<option value="">All Types</option>';
		this.absenceTypes.forEach(t => {
			const opt = document.createElement('option');
			opt.value = t.code || t.id;
			opt.textContent = `${t.name} (${t.code || t.id})`;
			select.appendChild(opt);
		});
		if (currentVal) select.value = currentVal;
	}

	populateTeamSelect() {
		const select = this.container.querySelector('#filter-team-id-select');
		if (!select) return;
		const currentVal = this.filters.team.teamId;
		select.innerHTML = '<option value="">-- Select Team --</option>';
		this.teams.forEach(t => {
			const opt = document.createElement('option');
			opt.value = t.id;
			opt.textContent = `${t.name} (${t.id})`;
			select.appendChild(opt);
		});
		if (currentVal) select.value = currentVal;
	}

	renderFilterFields() {
		if (this.activeReportType === 'day') {
			this.filterBar.innerHTML = `
				<div class="filter-group">
					<label for="filter-day-date">Date *:</label>
					<input type="date" id="filter-day-date" value="${this.filters.day.date}" required>
				</div>
				<div class="filter-group">
					<label for="filter-day-emp">Employee ID (optional):</label>
					<input type="text" id="filter-day-emp" placeholder="Default: Current User" value="${this.filters.day.employeeId}">
				</div>
			`;
			const dateInput = this.filterBar.querySelector('#filter-day-date');
			const empInput = this.filterBar.querySelector('#filter-day-emp');
			dateInput.addEventListener('change', () => { this.filters.day.date = dateInput.value; });
			empInput.addEventListener('input', () => { this.filters.day.employeeId = empInput.value; });

		} else if (this.activeReportType === 'month') {
			this.filterBar.innerHTML = `
				<div class="filter-group">
					<label for="filter-month-ym">Month (YYYY-MM) *:</label>
					<input type="month" id="filter-month-ym" value="${this.filters.month.yearMonth}" required>
				</div>
				<div class="filter-group">
					<label for="filter-month-emp">Employee ID (optional):</label>
					<input type="text" id="filter-month-emp" placeholder="Default: Current User" value="${this.filters.month.employeeId}">
				</div>
			`;
			const ymInput = this.filterBar.querySelector('#filter-month-ym');
			const empInput = this.filterBar.querySelector('#filter-month-emp');
			ymInput.addEventListener('change', () => { this.filters.month.yearMonth = ymInput.value; });
			empInput.addEventListener('input', () => { this.filters.month.employeeId = empInput.value; });

		} else if (this.activeReportType === 'vacation') {
			this.filterBar.innerHTML = `
				<div class="filter-group">
					<label for="filter-vacation-year">Year *:</label>
					<input type="number" id="filter-vacation-year" min="2000" max="2100" value="${this.filters.vacation.year}" required>
				</div>
				<div class="filter-group">
					<label for="filter-vacation-emp">Employee ID (optional):</label>
					<input type="text" id="filter-vacation-emp" placeholder="Default: Current User" value="${this.filters.vacation.employeeId}">
				</div>
			`;
			const yearInput = this.filterBar.querySelector('#filter-vacation-year');
			const empInput = this.filterBar.querySelector('#filter-vacation-emp');
			yearInput.addEventListener('change', () => { this.filters.vacation.year = yearInput.value; });
			empInput.addEventListener('input', () => { this.filters.vacation.employeeId = empInput.value; });

		} else if (this.activeReportType === 'team') {
			this.filterBar.innerHTML = `
				<div class="filter-group">
					<label for="filter-team-id">Team ID *:</label>
					<input type="text" id="filter-team-id" placeholder="e.g. team-1" value="${this.filters.team.teamId}" required>
				</div>
				<div class="filter-group">
					<label for="filter-team-ym">Month (YYYY-MM) *:</label>
					<input type="month" id="filter-team-ym" value="${this.filters.team.yearMonth}" required>
				</div>
			`;
			const teamInput = this.filterBar.querySelector('#filter-team-id');
			const ymInput = this.filterBar.querySelector('#filter-team-ym');
			teamInput.addEventListener('input', () => { this.filters.team.teamId = teamInput.value; });
			ymInput.addEventListener('change', () => { this.filters.team.yearMonth = ymInput.value; });

		} else if (this.activeReportType === 'absences') {
			this.filterBar.innerHTML = `
				<div class="filter-group">
					<label for="filter-absences-from">From Date:</label>
					<input type="date" id="filter-absences-from" value="${this.filters.absences.from}">
				</div>
				<div class="filter-group">
					<label for="filter-absences-to">To Date:</label>
					<input type="date" id="filter-absences-to" value="${this.filters.absences.to}">
				</div>
				<div class="filter-group">
					<label for="filter-absences-emp">Employee ID:</label>
					<input type="text" id="filter-absences-emp" placeholder="All / Optional" value="${this.filters.absences.employeeId}">
				</div>
				<div class="filter-group">
					<label for="filter-absence-type">Absence Type:</label>
					<select id="filter-absence-type">
						<option value="">All Types</option>
					</select>
				</div>
				<div class="filter-group">
					<label for="filter-absence-state">State:</label>
					<select id="filter-absence-state">
						<option value="">All States</option>
						<option value="SUBMITTED">SUBMITTED</option>
						<option value="APPROVED">APPROVED</option>
						<option value="REJECTED">REJECTED</option>
						<option value="CANCELLED">CANCELLED</option>
					</select>
				</div>
			`;
			const fromInput = this.filterBar.querySelector('#filter-absences-from');
			const toInput = this.filterBar.querySelector('#filter-absences-to');
			const empInput = this.filterBar.querySelector('#filter-absences-emp');
			const typeSelect = this.filterBar.querySelector('#filter-absence-type');
			const stateSelect = this.filterBar.querySelector('#filter-absence-state');

			fromInput.addEventListener('change', () => { this.filters.absences.from = fromInput.value; });
			toInput.addEventListener('change', () => { this.filters.absences.to = toInput.value; });
			empInput.addEventListener('input', () => { this.filters.absences.employeeId = empInput.value; });
			typeSelect.addEventListener('change', () => { this.filters.absences.type = typeSelect.value; });
			stateSelect.addEventListener('change', () => { this.filters.absences.state = stateSelect.value; });

			if (this.filters.absences.type) typeSelect.value = this.filters.absences.type;
			if (this.filters.absences.state) stateSelect.value = this.filters.absences.state;
			this.populateAbsenceTypeSelect();
		}
	}

	async generateReport() {
		this.resultsContainer.innerHTML = '<div class="loading-spinner">Generating report...</div>';

		try {
			if (this.activeReportType === 'day') {
				const date = this.filters.day.date;
				if (!date) {
					this.resultsContainer.innerHTML = '<div class="error-msg">Please specify a date.</div>';
					return;
				}
				const data = await ReportApi.getDayReport(date, this.filters.day.employeeId);
				this.renderDayReport(data);

			} else if (this.activeReportType === 'month') {
				const ym = this.filters.month.yearMonth;
				if (!ym) {
					this.resultsContainer.innerHTML = '<div class="error-msg">Please specify a month.</div>';
					return;
				}
				const data = await ReportApi.getMonthReport(ym, this.filters.month.employeeId);
				this.renderMonthReport(data);

			} else if (this.activeReportType === 'vacation') {
				const year = this.filters.vacation.year;
				const data = await ReportApi.getVacationReport(year, this.filters.vacation.employeeId);
				this.renderVacationReport(data);

			} else if (this.activeReportType === 'team') {
				const teamId = this.filters.team.teamId;
				const ym = this.filters.team.yearMonth;
				if (!teamId || !ym) {
					this.resultsContainer.innerHTML = '<div class="error-msg">Please specify Team ID and Month.</div>';
					return;
				}
				const data = await ReportApi.getTeamReport(teamId, ym);
				this.renderTeamReport(data);

			} else if (this.activeReportType === 'absences') {
				const data = await ReportApi.getAbsenceReport(this.filters.absences);
				this.renderAbsenceReport(data);
			}
		} catch (err) {
			console.error('Error generating report', err);
			this.resultsContainer.innerHTML = `<div class="error-msg">Failed to generate report: ${err.message}</div>`;
		}
	}

	async exportCsv() {
		try {
			if (this.activeReportType === 'day') {
				const date = this.filters.day.date;
				if (!date) {
					alert('Please specify a date.');
					return;
				}
				await ReportApi.downloadDayReportCsv(date, this.filters.day.employeeId);

			} else if (this.activeReportType === 'month') {
				const ym = this.filters.month.yearMonth;
				if (!ym) {
					alert('Please specify a month.');
					return;
				}
				await ReportApi.downloadMonthReportCsv(ym, this.filters.month.employeeId);

			} else if (this.activeReportType === 'vacation') {
				await ReportApi.downloadVacationReportCsv(this.filters.vacation.year, this.filters.vacation.employeeId);

			} else if (this.activeReportType === 'team') {
				const teamId = this.filters.team.teamId;
				const ym = this.filters.team.yearMonth;
				if (!teamId || !ym) {
					alert('Please specify Team ID and Month.');
					return;
				}
				await ReportApi.downloadTeamReportCsv(teamId, ym);

			} else if (this.activeReportType === 'absences') {
				await ReportApi.downloadAbsenceReportCsv(this.filters.absences);
			}
		} catch (err) {
			console.error('Error exporting CSV', err);
			alert(`CSV export failed: ${err.message}`);
		}
	}

	renderDayReport(data) {
		if (!data) {
			this.resultsContainer.innerHTML = '<div class="empty-state">No data returned for this day.</div>';
			return;
		}

		const balanceClass = data.balance > 0 ? 'positive' : (data.balance < 0 ? 'negative' : 'neutral');
		const balanceSign = data.balance > 0 ? '+' : '';

		let entriesHtml = '';
		if (!data.workEntries || data.workEntries.length === 0) {
			entriesHtml = '<tr><td colspan="5" class="empty-cell">No work entries recorded for this day.</td></tr>';
		} else {
			entriesHtml = data.workEntries.map(entry => `
				<tr>
					<td>${Format.dateTime(entry.start)}</td>
					<td>${entry.end ? Format.dateTime(entry.end) : '<span class="status-badge state-open">In Progress</span>'}</td>
					<td><strong>${Format.duration(entry.durationMinutes)}</strong></td>
					<td>${entry.durationMinutes} min</td>
					<td>${entry.id}</td>
				</tr>
			`).join('');
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

		this.resultsContainer.innerHTML = `
			<div class="report-result-header">
				<h3>Day Report: ${data.date}</h3>
				<span class="status-badge state-${(data.state || 'OPEN').toLowerCase()}">${data.stateLabel || data.state}</span>
			</div>

			<!-- Summary Cards Grid -->
			<div class="summary-grid report-summary-grid">
				<div class="summary-card">
					<div class="card-title">Target Time</div>
					<div class="card-value">${Format.duration(data.targetMinutes)}</div>
					<div class="card-sub">${data.targetMinutes} min</div>
				</div>
				<div class="summary-card">
					<div class="card-title">Actual Time</div>
					<div class="card-value">${Format.duration(data.actualMinutes)}</div>
					<div class="card-sub">${data.actualMinutes} min</div>
				</div>
				<div class="summary-card">
					<div class="card-title">Holiday Credit</div>
					<div class="card-value">${Format.duration(data.holidayMinutes)}</div>
					<div class="card-sub">${data.holidayMinutes} min</div>
				</div>
				<div class="summary-card">
					<div class="card-title">Absence Credit</div>
					<div class="card-value">${Format.duration(data.absenceMinutes)}</div>
					<div class="card-sub">${data.absenceMinutes} min</div>
				</div>
				<div class="summary-card highlight-card">
					<div class="card-title">Day Balance</div>
					<div class="card-value ${balanceClass}">${balanceSign}${Format.duration(data.balance)}</div>
					<div class="card-sub">${balanceSign}${data.balance} min</div>
				</div>
			</div>

			<!-- Work Entries Section -->
			<div class="report-section card">
				<h4>Work Blocks</h4>
				<div class="table-container">
					<table class="data-table">
						<thead>
							<tr>
								<th>Start</th>
								<th>End</th>
								<th>Duration</th>
								<th>Minutes</th>
								<th>Entry ID</th>
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
					<h4>Work Interruptions / Breaks</h4>
					<div class="table-container">
						<table class="data-table">
							<thead>
								<tr>
									<th>Break Start</th>
									<th>Break End</th>
									<th>Duration</th>
									<th>Minutes</th>
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
			this.resultsContainer.innerHTML = '<div class="empty-state">No data returned for this month.</div>';
			return;
		}

		const periodBalClass = data.periodBalanceMinutes > 0 ? 'positive' : (data.periodBalanceMinutes < 0 ? 'negative' : 'neutral');
		const periodBalSign = data.periodBalanceMinutes > 0 ? '+' : '';
		const endBalClass = data.endBalanceMinutes > 0 ? 'positive' : (data.endBalanceMinutes < 0 ? 'negative' : 'neutral');
		const endBalSign = data.endBalanceMinutes > 0 ? '+' : '';

		let daysHtml = '';
		if (!data.daySummaries || data.daySummaries.length === 0) {
			daysHtml = '<tr><td colspan="8" class="empty-cell">No day summaries available.</td></tr>';
		} else {
			daysHtml = data.daySummaries.map(day => {
				const dayBalClass = day.balance > 0 ? 'positive' : (day.balance < 0 ? 'negative' : 'neutral');
				const dayBalSign = day.balance > 0 ? '+' : '';
				const isWeekend = day.isOff && day.targetMinutes === 0;
				return `
					<tr class="${isWeekend ? 'row-off' : ''}">
						<td><strong>${day.date}</strong></td>
						<td>${Format.duration(day.targetMinutes)}</td>
						<td>${Format.duration(day.actualMinutes)}</td>
						<td>${day.holidayMinutes > 0 ? Format.duration(day.holidayMinutes) : '-'}</td>
						<td>${day.absenceMinutes > 0 ? Format.duration(day.absenceMinutes) : '-'}</td>
						<td class="${dayBalClass}"><strong>${dayBalSign}${Format.duration(day.balance)}</strong></td>
						<td>${day.workEntries ? day.workEntries.length : 0} block(s)</td>
						<td><span class="status-badge state-${(day.state || 'OPEN').toLowerCase()}">${day.stateLabel || day.state}</span></td>
					</tr>
				`;
			}).join('');
		}

		this.resultsContainer.innerHTML = `
			<div class="report-result-header">
				<h3>Monthly Report: ${data.yearMonth}</h3>
			</div>

			<!-- Summary Metrics Grid -->
			<div class="summary-grid report-summary-grid">
				<div class="summary-card">
					<div class="card-title">Target Time</div>
					<div class="card-value">${Format.duration(data.totalTargetMinutes)}</div>
					<div class="card-sub">${data.totalTargetMinutes} min</div>
				</div>
				<div class="summary-card">
					<div class="card-title">Actual Time</div>
					<div class="card-value">${Format.duration(data.totalActualMinutes)}</div>
					<div class="card-sub">${data.totalActualMinutes} min</div>
				</div>
				<div class="summary-card">
					<div class="card-title">Holidays</div>
					<div class="card-value">${Format.duration(data.totalHolidayMinutes)}</div>
					<div class="card-sub">${data.totalHolidayMinutes} min</div>
				</div>
				<div class="summary-card">
					<div class="card-title">Paid Absences</div>
					<div class="card-value">${Format.duration(data.totalPaidAbsenceMinutes)}</div>
					<div class="card-sub">${data.totalPaidAbsenceMinutes} min</div>
				</div>
				<div class="summary-card">
					<div class="card-title">Initial Balance</div>
					<div class="card-value">${Format.duration(data.initialBalanceMinutes)}</div>
					<div class="card-sub">${data.initialBalanceMinutes} min</div>
				</div>
				<div class="summary-card">
					<div class="card-title">Period Balance</div>
					<div class="card-value ${periodBalClass}">${periodBalSign}${Format.duration(data.periodBalanceMinutes)}</div>
					<div class="card-sub">${periodBalSign}${data.periodBalanceMinutes} min</div>
				</div>
				<div class="summary-card highlight-card">
					<div class="card-title">End Balance</div>
					<div class="card-value ${endBalClass}">${endBalSign}${Format.duration(data.endBalanceMinutes)}</div>
					<div class="card-sub">${endBalSign}${data.endBalanceMinutes} min</div>
				</div>
			</div>

			<!-- Daily Breakdown Table -->
			<div class="report-section card">
				<h4>Daily Breakdown</h4>
				<div class="table-container">
					<table class="data-table">
						<thead>
							<tr>
								<th>Date</th>
								<th>Target</th>
								<th>Actual</th>
								<th>Holiday</th>
								<th>Absence</th>
								<th>Day Balance</th>
								<th>Blocks</th>
								<th>Status</th>
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
			this.resultsContainer.innerHTML = '<div class="empty-state">No vacation account data available.</div>';
			return;
		}

		const remBalClass = data.remainingBalanceMinutes >= 0 ? 'positive' : 'negative';

		let entriesHtml = '';
		if (!data.entries || data.entries.length === 0) {
			entriesHtml = '<tr><td colspan="7" class="empty-cell">No vacation transactions recorded for this year.</td></tr>';
		} else {
			entriesHtml = data.entries.map(entry => `
				<tr>
					<td>${Format.date(entry.date)}</td>
					<td><span class="status-badge">${entry.bookingType}</span></td>
					<td><strong>${Format.durationDays(entry.valueMinutes)}</strong></td>
					<td>${entry.targetPeriod || '-'}</td>
					<td>${entry.comment || '-'}</td>
					<td>${entry.createdBy || '-'}</td>
					<td>${Format.dateTime(entry.createdAt)}</td>
				</tr>
			`).join('');
		}

		this.resultsContainer.innerHTML = `
			<div class="report-result-header">
				<h3>Vacation Account Report: Year ${data.year}</h3>
				<span class="report-emp-tag">Employee: ${data.employeeId}</span>
			</div>

			<!-- Summary Grid -->
			<div class="summary-grid report-summary-grid">
				<div class="summary-card">
					<div class="card-title">Annual Entitlement</div>
					<div class="card-value">${Format.durationDays(data.annualEntitlementMinutes)}</div>
					<div class="card-sub">${data.annualEntitlementMinutes} min</div>
				</div>
				<div class="summary-card">
					<div class="card-title">Carry-Over</div>
					<div class="card-value">${Format.durationDays(data.carryOverMinutes)}</div>
					<div class="card-sub">${data.carryOverMinutes} min</div>
				</div>
				<div class="summary-card">
					<div class="card-title">Corrections</div>
					<div class="card-value">${Format.durationDays(data.correctionMinutes)}</div>
					<div class="card-sub">${data.correctionMinutes} min</div>
				</div>
				<div class="summary-card">
					<div class="card-title">Taken / Used</div>
					<div class="card-value">${Format.durationDays(data.takenMinutes)}</div>
					<div class="card-sub">${data.takenMinutes} min</div>
				</div>
				<div class="summary-card">
					<div class="card-title">Planned Future</div>
					<div class="card-value">${Format.durationDays(data.plannedMinutes)}</div>
					<div class="card-sub">${data.plannedMinutes} min</div>
				</div>
				<div class="summary-card highlight-card">
					<div class="card-title">Remaining Balance</div>
					<div class="card-value ${remBalClass}">${Format.durationDays(data.remainingBalanceMinutes)}</div>
					<div class="card-sub">${data.remainingBalanceMinutes} min</div>
				</div>
			</div>

			<!-- Vacation Journal Entries Table -->
			<div class="report-section card">
				<h4>Vacation Journal Transactions</h4>
				<div class="table-container">
					<table class="data-table">
						<thead>
							<tr>
								<th>Date</th>
								<th>Booking Type</th>
								<th>Value (Days / Duration)</th>
								<th>Target Period</th>
								<th>Comment</th>
								<th>Created By</th>
								<th>Created At</th>
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
			this.resultsContainer.innerHTML = '<div class="empty-state">No team data returned.</div>';
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
			rowsHtml = '<tr><td colspan="10" class="empty-cell">No employees found for this team.</td></tr>';
		} else {
			rowsHtml = employees.map(emp => {
				const balClass = emp.periodBalanceMinutes > 0 ? 'positive' : (emp.periodBalanceMinutes < 0 ? 'negative' : 'neutral');
				const balSign = emp.periodBalanceMinutes > 0 ? '+' : '';
				const missingBadge = emp.missingBookingsCount > 0 
					? `<span class="badge badge-warning">${emp.missingBookingsCount} missing</span>`
					: `<span class="badge badge-success">0</span>`;

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
						<td><span class="status-badge state-${(emp.periodState || 'OPEN').toLowerCase()}">${emp.periodState}</span></td>
						<td>${missingBadge}</td>
					</tr>
				`;
			}).join('');
		}

		this.resultsContainer.innerHTML = `
			<div class="report-result-header">
				<h3>Team Report: ${data.teamName} (${data.teamId})</h3>
				<span class="report-emp-tag">Month: ${data.yearMonth}</span>
			</div>

			<!-- Team Summary Cards -->
			<div class="summary-grid report-summary-grid">
				<div class="summary-card">
					<div class="card-title">Team Members</div>
					<div class="card-value">${employees.length}</div>
					<div class="card-sub">Active Employees</div>
				</div>
				<div class="summary-card">
					<div class="card-title">Total Target</div>
					<div class="card-value">${Format.duration(totalTarget)}</div>
					<div class="card-sub">${totalTarget} min</div>
				</div>
				<div class="summary-card">
					<div class="card-title">Total Actual</div>
					<div class="card-value">${Format.duration(totalActual)}</div>
					<div class="card-sub">${totalActual} min</div>
				</div>
				<div class="summary-card">
					<div class="card-title">Net Balance</div>
					<div class="card-value ${totalBalClass}">${totalBalSign}${Format.duration(totalPeriodBal)}</div>
					<div class="card-sub">${totalBalSign}${totalPeriodBal} min</div>
				</div>
				<div class="summary-card ${totalMissing > 0 ? 'warning-card' : ''}">
					<div class="card-title">Missing Bookings</div>
					<div class="card-value">${totalMissing}</div>
					<div class="card-sub">Unrecorded target days</div>
				</div>
			</div>

			<!-- Team Members Table -->
			<div class="report-section card">
				<h4>Employee Summaries</h4>
				<div class="table-container">
					<table class="data-table">
						<thead>
							<tr>
								<th>Employee</th>
								<th>Target</th>
								<th>Actual</th>
								<th>Holiday</th>
								<th>Absence</th>
								<th>Initial Balance</th>
								<th>Period Balance</th>
								<th>End Balance</th>
								<th>Period Status</th>
								<th>Missing Bookings</th>
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
			rowsHtml = '<tr><td colspan="9" class="empty-cell">No absences found matching filter criteria.</td></tr>';
		} else {
			rowsHtml = items.map(item => {
				const paidBadge = item.paid
					? '<span class="badge badge-success">Paid</span>'
					: '<span class="badge badge-neutral">Unpaid</span>';

				return `
					<tr>
						<td><strong>${item.employeeName || item.employeeId}</strong><br><small class="text-muted">${item.employeeId}</small></td>
						<td><span class="status-badge">${item.absenceTypeName || item.absenceTypeCode}</span></td>
						<td>${Format.date(item.start)} - ${Format.date(item.end)}</td>
						<td>${item.durationType || '-'} ${item.dayPart ? `(${item.dayPart})` : ''}</td>
						<td><strong>${Format.duration(item.minutes)}</strong> (${item.minutes}m)</td>
						<td><span class="status-badge state-${(item.state || 'SUBMITTED').toLowerCase()}">${item.state}</span></td>
						<td>${paidBadge}</td>
						<td>${item.approvedBy ? `${item.approvedBy}<br><small class="text-muted">${Format.dateTime(item.approvedAt)}</small>` : '-'}</td>
						<td>${item.comment || '-'}</td>
					</tr>
				`;
			}).join('');
		}

		this.resultsContainer.innerHTML = `
			<div class="report-result-header">
				<h3>Absences Report</h3>
				<span class="report-emp-tag">${items.length} records</span>
			</div>

			<!-- Summary Cards Grid -->
			<div class="summary-grid report-summary-grid">
				<div class="summary-card">
					<div class="card-title">Total Absences</div>
					<div class="card-value">${items.length}</div>
					<div class="card-sub">${approvedCount} approved</div>
				</div>
				<div class="summary-card">
					<div class="card-title">Total Duration</div>
					<div class="card-value">${Format.duration(totalMinutes)}</div>
					<div class="card-sub">${Format.durationDays(totalMinutes)}</div>
				</div>
				<div class="summary-card">
					<div class="card-title">Paid Absence Time</div>
					<div class="card-value">${Format.duration(paidMinutes)}</div>
					<div class="card-sub">${Format.durationDays(paidMinutes)}</div>
				</div>
				<div class="summary-card">
					<div class="card-title">Unpaid Absence Time</div>
					<div class="card-value">${Format.duration(unpaidMinutes)}</div>
					<div class="card-sub">${Format.durationDays(unpaidMinutes)}</div>
				</div>
			</div>

			<!-- Absences Table -->
			<div class="report-section card">
				<h4>Absences Listing</h4>
				<div class="table-container">
					<table class="data-table">
						<thead>
							<tr>
								<th>Employee</th>
								<th>Absence Type</th>
								<th>Date Range</th>
								<th>Duration Type</th>
								<th>Duration</th>
								<th>Status</th>
								<th>Remuneration</th>
								<th>Approved By</th>
								<th>Comment</th>
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
}
