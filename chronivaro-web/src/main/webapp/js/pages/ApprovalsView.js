import ApprovalsApi from '../api/ApprovalsApi.js';
import AbsenceTypeApi from '../api/AbsenceTypeApi.js';
import TeamApi from '../api/TeamApi.js';
import NotificationDialog from '../utils/NotificationDialog.js';
import Format from '../utils/Format.js';
import I18n from '../i18n/I18n.js';

export default class ApprovalsView {

	constructor(app) {
		this.app = app;
		this.activeTab = 'absences'; // 'absences' or 'periods'

		// Absences filter & pagination state
		this.absenceFilters = {
			teamId: '',
			employeeId: '',
			absenceTypeCode: '',
			from: '',
			to: '',
			offset: 0,
			limit: 20
		};

		// Periods filter & pagination state
		this.periodFilters = {
			teamId: '',
			employeeId: '',
			yearMonth: '',
			offset: 0,
			limit: 20
		};

		this.absenceTypes = [];
		this.teams = [];
	}

	async render(params) {
		if (params && params.tab) {
			this.activeTab = params.tab;
		}

		const container = document.createElement('div');
		container.id = 'approvals-view';
		container.innerHTML = `
			<div class="view-header">
				<h2>${I18n.t('approvals.supervisorApprovalQueues')}</h2>
			</div>

			<!-- Tab Navigation -->
			<div class="tabs-container">
				<div class="tab-buttons">
					<button id="tab-absences-btn" class="tab-btn ${this.activeTab === 'absences' ? 'active' : ''}">
						${I18n.t('approvals.pendingAbsences')}
					</button>
					<button id="tab-periods-btn" class="tab-btn ${this.activeTab === 'periods' ? 'active' : ''}">
						${I18n.t('approvals.pendingPeriods')}
					</button>
				</div>
			</div>

			<!-- Pending Absences Section -->
			<section id="section-absences" class="tab-content-section card ${this.activeTab === 'absences' ? '' : 'hidden'}">
				<div class="section-title-bar">
					<h3>${I18n.t('approvals.pendingAbsences')}</h3>
				</div>

				<!-- Absences Filter Bar -->
				<div class="filter-bar" id="absences-filter-bar">
					<div class="filter-group">
						<label for="absence-team-filter">${I18n.t('common.team')}:</label>
						<select id="absence-team-filter">
							<option value="">${I18n.t('common.allTeams')}</option>
						</select>
					</div>
					<div class="filter-group">
						<label for="absence-emp-filter">${I18n.t('common.employee')}:</label>
						<input type="text" id="absence-emp-filter" placeholder="e.g. employee_emp" value="${this.absenceFilters.employeeId}">
					</div>
					<div class="filter-group">
						<label for="absence-type-filter">${I18n.t('absences.absenceType')}:</label>
						<select id="absence-type-filter">
							<option value="">${I18n.t('common.allTypes')}</option>
						</select>
					</div>
					<div class="filter-group">
						<label for="absence-from-filter">${I18n.t('common.from')}:</label>
						<input type="date" id="absence-from-filter" value="${this.absenceFilters.from}">
					</div>
					<div class="filter-group">
						<label for="absence-to-filter">${I18n.t('common.to')}:</label>
						<input type="date" id="absence-to-filter" value="${this.absenceFilters.to}">
					</div>
					<div class="filter-actions">
						<button id="absence-apply-filter-btn" class="primary-btn">${I18n.t('common.filter')}</button>
						<button id="absence-reset-filter-btn" class="secondary-btn">${I18n.t('common.reset')}</button>
					</div>
				</div>

				<!-- Absences Table -->
				<div class="table-container">
					<table class="data-table" id="absences-table">
						<thead>
							<tr>
								<th>${I18n.t('common.employee')}</th>
								<th>${I18n.t('absences.absenceType')}</th>
								<th>${I18n.t('approvals.dateRangeTime')}</th>
								<th>${I18n.t('common.duration')}</th>
								<th>${I18n.t('common.comment')}</th>
								<th>${I18n.t('common.actions')}</th>
							</tr>
						</thead>
						<tbody id="absences-tbody">
							<tr><td colspan="6">${I18n.t('approvals.loadingPendingAbsences')}</td></tr>
						</tbody>
					</table>
				</div>

				<!-- Absences Pagination -->
				<div class="pagination-bar" id="absences-pagination">
					<button id="absence-prev-btn" class="secondary-btn" disabled>&laquo; ${I18n.t('common.previous')}</button>
					<span id="absence-page-info" class="page-info">${I18n.t('approvals.pageInfo', { page: 1, totalPages: 1, total: 0 })}</span>
					<button id="absence-next-btn" class="secondary-btn" disabled>${I18n.t('common.next')} &raquo;</button>
				</div>
			</section>

			<!-- Submitted Periods Section -->
			<section id="section-periods" class="tab-content-section card ${this.activeTab === 'periods' ? '' : 'hidden'}">
				<div class="section-title-bar">
					<h3>${I18n.t('approvals.submittedMonthlyPeriods')}</h3>
				</div>

				<!-- Periods Filter Bar -->
				<div class="filter-bar" id="periods-filter-bar">
					<div class="filter-group">
						<label for="period-team-filter">${I18n.t('common.team')}:</label>
						<select id="period-team-filter">
							<option value="">${I18n.t('common.allTeams')}</option>
						</select>
					</div>
					<div class="filter-group">
						<label for="period-emp-filter">${I18n.t('common.employee')}:</label>
						<input type="text" id="period-emp-filter" placeholder="e.g. employee_emp" value="${this.periodFilters.employeeId}">
					</div>
					<div class="filter-group">
						<label for="period-month-filter">${I18n.t('common.month')}:</label>
						<input type="month" id="period-month-filter" value="${this.periodFilters.yearMonth}">
					</div>
					<div class="filter-actions">
						<button id="period-apply-filter-btn" class="primary-btn">${I18n.t('common.filter')}</button>
						<button id="period-reset-filter-btn" class="secondary-btn">${I18n.t('common.reset')}</button>
					</div>
				</div>

				<!-- Periods Table -->
				<div class="table-container">
					<table class="data-table" id="periods-table">
						<thead>
							<tr>
								<th>${I18n.t('common.employee')}</th>
								<th>${I18n.t('periods.period')}</th>
								<th>${I18n.t('periods.submittedPeriod')}</th>
								<th>${I18n.t('approvals.submissionCommentHeader')}</th>
								<th>${I18n.t('approvals.calculationSummary')}</th>
								<th>${I18n.t('common.actions')}</th>
							</tr>
						</thead>
						<tbody id="periods-tbody">
							<tr><td colspan="6">${I18n.t('approvals.loadingSubmittedPeriods')}</td></tr>
						</tbody>
					</table>
				</div>

				<!-- Periods Pagination -->
				<div class="pagination-bar" id="periods-pagination">
					<button id="period-prev-btn" class="secondary-btn" disabled>&laquo; ${I18n.t('common.previous')}</button>
					<span id="period-page-info" class="page-info">${I18n.t('approvals.pageInfo', { page: 1, totalPages: 1, total: 0 })}</span>
					<button id="period-next-btn" class="secondary-btn" disabled>${I18n.t('common.next')} &raquo;</button>
				</div>
			</section>
		`;

		this.bindEvents(container);
		await this.init(container);

		return container;
	}

	async init(container) {
		await Promise.all([this.loadAbsenceTypes(container), this.loadTeams(container)]);
		if (this.activeTab === 'absences') {
			await this.loadAbsences(container);
		} else {
			await this.loadPeriods(container);
		}
	}

	async loadTeams(container) {
		try {
			this.teams = await TeamApi.getAll();
			['#absence-team-filter', '#period-team-filter'].forEach(selId => {
				const select = container.querySelector(selId);
				if (select) {
					select.innerHTML = `<option value="">${I18n.t('common.allTeams')}</option>`;
					this.teams.forEach(t => {
						const opt = document.createElement('option');
						opt.value = t.id;
						opt.textContent = `${t.name} (${t.id})`;
						select.appendChild(opt);
					});
				}
			});
		} catch (e) {
			console.warn('Could not load teams for filter:', e);
		}
	}

	async loadAbsenceTypes(container) {
		try {
			this.absenceTypes = await AbsenceTypeApi.getAll();
			const select = container.querySelector('#absence-type-filter');
			if (select) {
				select.innerHTML = `<option value="">${I18n.t('common.allTypes')}</option>`;
				this.absenceTypes.forEach(t => {
					const opt = document.createElement('option');
					opt.value = t.code;
					opt.textContent = `${t.name} (${t.code})`;
					if (this.absenceFilters.absenceTypeCode === t.code) {
						opt.selected = true;
					}
					select.appendChild(opt);
				});
			}
		} catch (e) {
			console.warn('Could not load absence types for filter:', e);
		}
	}

	bindEvents(container) {
		const tabAbsencesBtn = container.querySelector('#tab-absences-btn');
		const tabPeriodsBtn = container.querySelector('#tab-periods-btn');
		const sectionAbsences = container.querySelector('#section-absences');
		const sectionPeriods = container.querySelector('#section-periods');

		tabAbsencesBtn.addEventListener('click', () => {
			this.activeTab = 'absences';
			tabAbsencesBtn.classList.add('active');
			tabPeriodsBtn.classList.remove('active');
			sectionAbsences.classList.remove('hidden');
			sectionPeriods.classList.add('hidden');
			this.loadAbsences(container);
		});

		tabPeriodsBtn.addEventListener('click', () => {
			this.activeTab = 'periods';
			tabPeriodsBtn.classList.add('active');
			tabAbsencesBtn.classList.remove('active');
			sectionPeriods.classList.remove('hidden');
			sectionAbsences.classList.add('hidden');
			this.loadPeriods(container);
		});

		// Absences filtering
		const absenceTeamFilter = container.querySelector('#absence-team-filter');
		const absenceEmpFilter = container.querySelector('#absence-emp-filter');
		const absenceTypeFilter = container.querySelector('#absence-type-filter');
		const absenceFromFilter = container.querySelector('#absence-from-filter');
		const absenceToFilter = container.querySelector('#absence-to-filter');
		const absenceApplyBtn = container.querySelector('#absence-apply-filter-btn');
		const absenceResetBtn = container.querySelector('#absence-reset-filter-btn');
		const absencePrevBtn = container.querySelector('#absence-prev-btn');
		const absenceNextBtn = container.querySelector('#absence-next-btn');

		absenceApplyBtn.addEventListener('click', () => {
			this.absenceFilters.teamId = absenceTeamFilter.value.trim();
			this.absenceFilters.employeeId = absenceEmpFilter.value.trim();
			this.absenceFilters.absenceTypeCode = absenceTypeFilter.value;
			this.absenceFilters.from = absenceFromFilter.value;
			this.absenceFilters.to = absenceToFilter.value;
			this.absenceFilters.offset = 0;
			this.loadAbsences(container);
		});

		absenceResetBtn.addEventListener('click', () => {
			absenceTeamFilter.value = '';
			absenceEmpFilter.value = '';
			absenceTypeFilter.value = '';
			absenceFromFilter.value = '';
			absenceToFilter.value = '';
			this.absenceFilters = { teamId: '', employeeId: '', absenceTypeCode: '', from: '', to: '', offset: 0, limit: 20 };
			this.loadAbsences(container);
		});

		absencePrevBtn.addEventListener('click', () => {
			if (this.absenceFilters.offset >= this.absenceFilters.limit) {
				this.absenceFilters.offset -= this.absenceFilters.limit;
				this.loadAbsences(container);
			}
		});

		absenceNextBtn.addEventListener('click', () => {
			this.absenceFilters.offset += this.absenceFilters.limit;
			this.loadAbsences(container);
		});

		// Periods filtering
		const periodTeamFilter = container.querySelector('#period-team-filter');
		const periodEmpFilter = container.querySelector('#period-emp-filter');
		const periodMonthFilter = container.querySelector('#period-month-filter');
		const periodApplyBtn = container.querySelector('#period-apply-filter-btn');
		const periodResetBtn = container.querySelector('#period-reset-filter-btn');
		const periodPrevBtn = container.querySelector('#period-prev-btn');
		const periodNextBtn = container.querySelector('#period-next-btn');

		periodApplyBtn.addEventListener('click', () => {
			this.periodFilters.teamId = periodTeamFilter.value.trim();
			this.periodFilters.employeeId = periodEmpFilter.value.trim();
			this.periodFilters.yearMonth = periodMonthFilter.value;
			this.periodFilters.offset = 0;
			this.loadPeriods(container);
		});

		periodResetBtn.addEventListener('click', () => {
			periodTeamFilter.value = '';
			periodEmpFilter.value = '';
			periodMonthFilter.value = '';
			this.periodFilters = { teamId: '', employeeId: '', yearMonth: '', offset: 0, limit: 20 };
			this.loadPeriods(container);
		});

		periodPrevBtn.addEventListener('click', () => {
			if (this.periodFilters.offset >= this.periodFilters.limit) {
				this.periodFilters.offset -= this.periodFilters.limit;
				this.loadPeriods(container);
			}
		});

		periodNextBtn.addEventListener('click', () => {
			this.periodFilters.offset += this.periodFilters.limit;
			this.loadPeriods(container);
		});
	}

	async loadAbsences(container) {
		const tbody = container.querySelector('#absences-tbody');
		const prevBtn = container.querySelector('#absence-prev-btn');
		const nextBtn = container.querySelector('#absence-next-btn');
		const pageInfo = container.querySelector('#absence-page-info');

		tbody.innerHTML = `<tr><td colspan="6">${I18n.t('approvals.loadingPendingAbsences')}</td></tr>`;

		try {
			const res = await ApprovalsApi.getSubmittedAbsences(this.absenceFilters);
			const items = Array.isArray(res) ? res : (res.data || []);
			const total = res.total !== undefined ? res.total : items.length;

			const offset = this.absenceFilters.offset;
			const limit = this.absenceFilters.limit;
			const pageNum = Math.floor(offset / limit) + 1;
			const totalPages = Math.max(1, Math.ceil(total / limit));

			pageInfo.textContent = I18n.t('approvals.pageInfo', { page: pageNum, totalPages: totalPages, total: total });
			prevBtn.disabled = offset <= 0;
			nextBtn.disabled = offset + items.length >= total;

			if (items.length === 0) {
				tbody.innerHTML = `<tr><td colspan="6" class="no-data">${I18n.t('approvals.noPendingAbsences')}</td></tr>`;
				return;
			}

			tbody.innerHTML = '';
			items.forEach(absence => {
				const tr = document.createElement('tr');
				tr.className = 'approval-row';

				const dateRangeStr = `${Format.date(absence.start)} - ${Format.date(absence.end)}`;
				let durationStr = '';
				if (absence.durationType === 'FULL_DAY') {
					durationStr = I18n.t('enums.durationType.FULL_DAY');
				} else if (absence.durationType === 'HALF_DAY') {
					const partText = absence.dayPart ? I18n.t(`absences.${absence.dayPart.toLowerCase()}`, {}, absence.dayPart) : '';
					durationStr = `${I18n.t('absences.halfDayMorning')} (${partText})`;
				} else if (absence.durationType === 'HOURS') {
					durationStr = Format.duration(absence.minutes);
				} else {
					durationStr = absence.durationType || '';
				}

				const empDisplayName = absence.employeeName || absence.employeeId;
				const empSubDetails = [
					absence.personalNumber ? `#${absence.personalNumber}` : null,
					absence.teamName || null
				].filter(Boolean).join(' • ');

				const typeDisplayName = absence.absenceTypeName || absence.absenceTypeCode || 'ABSENCE';

				tr.innerHTML = `
					<td>
						<div class="employee-info-cell">
							<strong>${empDisplayName}</strong>
							${empSubDetails ? `<br><small class="text-muted">${empSubDetails}</small>` : ''}
						</div>
					</td>
					<td><span class="status-badge badge-working">${typeDisplayName}</span></td>
					<td>${dateRangeStr}</td>
					<td>${durationStr}</td>
					<td>${absence.comment || `<span class="text-muted">${I18n.t('common.none')}</span>`}</td>
					<td class="action-buttons-cell">
						<button class="primary-btn approve-btn" data-id="${absence.id}">${I18n.t('common.approve')}</button>
						<button class="danger-btn reject-btn" data-id="${absence.id}">${I18n.t('common.reject')}</button>
					</td>
				`;

				const approveBtn = tr.querySelector('.approve-btn');
				const rejectBtn = tr.querySelector('.reject-btn');

				approveBtn.addEventListener('click', () => this.handleApproveAbsence(absence, container));
				rejectBtn.addEventListener('click', () => this.handleRejectAbsence(absence, container));

				tbody.appendChild(tr);
			});

		} catch (err) {
			console.error('Error loading submitted absences:', err);
			tbody.innerHTML = `<tr><td colspan="6" class="error">${err.message || I18n.t('app.error')}</td></tr>`;
		}
	}

	async handleApproveAbsence(absence, container) {
		const empDisplayName = absence.employeeName || absence.employeeId;
		const typeDisplayName = absence.absenceTypeName || absence.absenceTypeCode;
		const confirmed = await NotificationDialog.confirm(
			I18n.t('approvals.confirmApproveAbsence', { employee: empDisplayName, type: typeDisplayName, date: Format.date(absence.start) }),
			I18n.t('approvals.approveAbsenceTitle')
		);
		if (!confirmed) return;

		try {
			await ApprovalsApi.approveAbsence(absence.id);
			await NotificationDialog.info(I18n.t('approvals.absenceApprovedSuccess'), I18n.t('common.success'));
			await this.loadAbsences(container);
		} catch (err) {
			console.error('Error approving absence:', err);
			await NotificationDialog.error(err.message || I18n.t('app.error'));
		}
	}

	async handleRejectAbsence(absence, container) {
		const empDisplayName = absence.employeeName || absence.employeeId;
		const reason = await NotificationDialog.prompt(
			I18n.t('approvals.rejectAbsencePrompt', { employee: empDisplayName }),
			I18n.t('approvals.rejectAbsenceTitle'),
			`${I18n.t('common.rejectionReason')}...`,
			'',
			true
		);
		if (reason === null) return; // User cancelled

		try {
			await ApprovalsApi.rejectAbsence(absence.id, reason);
			await NotificationDialog.info(I18n.t('approvals.absenceRejectedSuccess'), I18n.t('common.success'));
			await this.loadAbsences(container);
		} catch (err) {
			console.error('Error rejecting absence:', err);
			await NotificationDialog.error(err.message || I18n.t('app.error'));
		}
	}

	async loadPeriods(container) {
		const tbody = container.querySelector('#periods-tbody');
		const prevBtn = container.querySelector('#period-prev-btn');
		const nextBtn = container.querySelector('#period-next-btn');
		const pageInfo = container.querySelector('#period-page-info');

		tbody.innerHTML = `<tr><td colspan="6">${I18n.t('approvals.loadingSubmittedPeriods')}</td></tr>`;

		try {
			const res = await ApprovalsApi.getSubmittedPeriods(this.periodFilters);
			const items = Array.isArray(res) ? res : (res.data || []);
			const total = res.total !== undefined ? res.total : items.length;

			const offset = this.periodFilters.offset;
			const limit = this.periodFilters.limit;
			const pageNum = Math.floor(offset / limit) + 1;
			const totalPages = Math.max(1, Math.ceil(total / limit));

			pageInfo.textContent = I18n.t('approvals.pageInfo', { page: pageNum, totalPages: totalPages, total: total });
			prevBtn.disabled = offset <= 0;
			nextBtn.disabled = offset + items.length >= total;

			if (items.length === 0) {
				tbody.innerHTML = `<tr><td colspan="6" class="no-data">${I18n.t('approvals.noSubmittedPeriods')}</td></tr>`;
				return;
			}

			tbody.innerHTML = '';
			items.forEach(period => {
				const tr = document.createElement('tr');
				tr.className = 'approval-row';

				const periodId = `period-${period.employeeId}-${period.yearMonth}`;
				let snapshotHtml = `<span class="text-muted">${I18n.t('common.none')}</span>`;

				if (period.calculationSnapshot) {
					try {
						const s = JSON.parse(period.calculationSnapshot);
						const target = Format.duration(s.totalTargetMinutes);
						const actual = Format.duration(s.totalActualMinutes);
						const balance = Format.duration(s.periodBalanceMinutes);
						const balanceClass = s.periodBalanceMinutes >= 0 ? 'positive' : 'negative';

						snapshotHtml = `
							<div class="period-snapshot-mini">
								<span>${I18n.t('common.target')}: <strong>${target}</strong></span> |
								<span>${I18n.t('periods.worked')}: <strong>${actual}</strong></span> |
								<span>${I18n.t('common.balance')}: <strong class="${balanceClass}">${balance}</strong></span>
							</div>
						`;
					} catch (e) {
						snapshotHtml = `<span class="text-muted">${period.calculationSnapshot}</span>`;
					}
				}

				const empDisplayName = period.employeeName || period.employeeId;
				const empSubDetails = [
					period.personalNumber ? `#${period.personalNumber}` : null,
					period.teamName || null
				].filter(Boolean).join(' • ');

				tr.innerHTML = `
					<td>
						<div class="employee-info-cell">
							<strong>${empDisplayName}</strong>
							${empSubDetails ? `<br><small class="text-muted">${empSubDetails}</small>` : ''}
						</div>
					</td>
					<td><span class="status-badge badge-submitted">${period.yearMonth}</span></td>
					<td>${Format.dateTime(period.submittedAt)}</td>
					<td>${period.comment || `<span class="text-muted">${I18n.t('common.none')}</span>`}</td>
					<td>${snapshotHtml}</td>
					<td class="action-buttons-cell">
						<button class="primary-btn approve-btn" data-id="${periodId}">${I18n.t('common.approve')}</button>
						<button class="danger-btn reject-btn" data-id="${periodId}">${I18n.t('common.reject')}</button>
					</td>
				`;

				const approveBtn = tr.querySelector('.approve-btn');
				const rejectBtn = tr.querySelector('.reject-btn');

				approveBtn.addEventListener('click', () => this.handleApprovePeriod(period, periodId, container));
				rejectBtn.addEventListener('click', () => this.handleRejectPeriod(period, periodId, container));

				tbody.appendChild(tr);
			});

		} catch (err) {
			console.error('Error loading submitted periods:', err);
			tbody.innerHTML = `<tr><td colspan="6" class="error">${err.message || I18n.t('app.error')}</td></tr>`;
		}
	}

	async handleApprovePeriod(period, periodId, container) {
		const empDisplayName = period.employeeName || period.employeeId;
		const comment = await NotificationDialog.prompt(
			I18n.t('approvals.approvePeriodPrompt', { employee: empDisplayName, period: period.yearMonth }),
			I18n.t('approvals.approvePeriodTitle'),
			`${I18n.t('common.comment')}...`,
			'',
			false
		);
		if (comment === null) return; // User cancelled

		try {
			await ApprovalsApi.approvePeriod(periodId, comment || null);
			await NotificationDialog.info(I18n.t('approvals.periodApprovedSuccess', { employee: empDisplayName, period: period.yearMonth }), I18n.t('common.success'));
			await this.loadPeriods(container);
		} catch (err) {
			console.error('Error approving period:', err);
			await NotificationDialog.error(err.message || I18n.t('app.error'));
		}
	}

	async handleRejectPeriod(period, periodId, container) {
		const empDisplayName = period.employeeName || period.employeeId;
		const reason = await NotificationDialog.prompt(
			I18n.t('approvals.rejectPeriodPrompt', { employee: empDisplayName, period: period.yearMonth }),
			I18n.t('approvals.rejectPeriodTitle'),
			`${I18n.t('common.rejectionReason')}...`,
			'',
			true
		);
		if (reason === null) return; // User cancelled

		try {
			await ApprovalsApi.rejectPeriod(periodId, reason);
			await NotificationDialog.info(I18n.t('approvals.periodRejectedSuccess', { period: period.yearMonth }), I18n.t('common.success'));
			await this.loadPeriods(container);
		} catch (err) {
			console.error('Error rejecting period:', err);
			await NotificationDialog.error(err.message || I18n.t('app.error'));
		}
	}
}
