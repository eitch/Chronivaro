import ApprovalsApi from '../api/ApprovalsApi.js';
import AbsenceTypeApi from '../api/AbsenceTypeApi.js';
import TeamApi from '../api/TeamApi.js';
import NotificationDialog from '../utils/NotificationDialog.js';
import Format from '../utils/Format.js';
import MonthPicker from '../utils/MonthPicker.js';
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
						<input type="text" id="absence-from-filter" value="${Format.date(this.absenceFilters.from)}" placeholder="DD.MM.YYYY" maxlength="10">
					</div>
					<div class="filter-group">
						<label for="absence-to-filter">${I18n.t('common.to')}:</label>
						<input type="text" id="absence-to-filter" value="${Format.date(this.absenceFilters.to)}" placeholder="DD.MM.YYYY" maxlength="10">
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

			<!-- Period Inspection Modal -->
			<div id="period-inspect-modal" class="modal">
				<div class="modal-content extra-wide" style="max-width: 1050px; max-height: 90vh; overflow-y: auto; width: 95%;">
					<div class="modal-header" style="display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 1.5rem; border-bottom: 1px solid var(--border-color); padding-bottom: 1rem;">
						<div>
							<h3 id="inspect-modal-title" style="margin: 0 0 0.5rem 0;">${I18n.t('approvals.inspectPeriodTitle')}</h3>
							<div id="inspect-modal-subtitle" class="text-muted" style="margin: 0; font-size: 0.95rem; line-height: 1.5;"></div>
						</div>
						<button type="button" id="inspect-modal-close-icon-btn" class="close-btn" style="background: none; border: none; font-size: 1.5rem; cursor: pointer; color: var(--text-muted);">&times;</button>
					</div>

					<div id="inspect-modal-body">
						<div class="loading-spinner" style="text-align: center; padding: 2rem;">${I18n.t('common.loading')}</div>
					</div>

					<div class="modal-actions" style="margin-top: 1.5rem; border-top: 1px solid var(--border-color); padding-top: 1rem; display: flex; justify-content: flex-end; gap: 0.75rem;">
						<button type="button" id="inspect-modal-approve-btn" class="primary-btn">${I18n.t('common.approve')}</button>
						<button type="button" id="inspect-modal-reject-btn" class="danger-btn">${I18n.t('common.reject')}</button>
						<button type="button" id="inspect-modal-close-btn" class="secondary-btn">${I18n.t('common.close')}</button>
					</div>
				</div>
			</div>
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

		[absenceFromFilter, absenceToFilter].forEach(inp => {
			if (inp) {
				inp.addEventListener('blur', () => {
					if (inp.value) inp.value = Format.normalizeDate(inp.value);
				});
			}
		});

		absenceApplyBtn.addEventListener('click', () => {
			this.absenceFilters.teamId = absenceTeamFilter.value.trim();
			this.absenceFilters.employeeId = absenceEmpFilter.value.trim();
			this.absenceFilters.absenceTypeCode = absenceTypeFilter.value;
			this.absenceFilters.from = Format.toIsoDate(absenceFromFilter.value);
			this.absenceFilters.to = Format.toIsoDate(absenceToFilter.value);
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

		// Modal close buttons
		const modal = container.querySelector('#period-inspect-modal');
		const closeIconBtn = container.querySelector('#inspect-modal-close-icon-btn');
		const closeBtn = container.querySelector('#inspect-modal-close-btn');
		const modalApproveBtn = container.querySelector('#inspect-modal-approve-btn');
		const modalRejectBtn = container.querySelector('#inspect-modal-reject-btn');

		const closeModal = () => {
			modal.style.display = 'none';
			this.currentInspectedPeriod = null;
		};

		if (closeIconBtn) closeIconBtn.addEventListener('click', closeModal);
		if (closeBtn) closeBtn.addEventListener('click', closeModal);

		if (modalApproveBtn) {
			modalApproveBtn.addEventListener('click', async () => {
				if (!this.currentInspectedPeriod) return;
				const { period, periodId } = this.currentInspectedPeriod;
				const success = await this.handleApprovePeriod(period, periodId, container);
				if (success) {
					closeModal();
				}
			});
		}

		if (modalRejectBtn) {
			modalRejectBtn.addEventListener('click', async () => {
				if (!this.currentInspectedPeriod) return;
				const { period, periodId } = this.currentInspectedPeriod;
				const success = await this.handleRejectPeriod(period, periodId, container);
				if (success) {
					closeModal();
				}
			});
		}

		MonthPicker.init(container);
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
						<button class="secondary-btn inspect-btn" data-id="${periodId}">${I18n.t('approvals.inspect')}</button>
						<button class="primary-btn approve-btn" data-id="${periodId}">${I18n.t('common.approve')}</button>
						<button class="danger-btn reject-btn" data-id="${periodId}">${I18n.t('common.reject')}</button>
					</td>
				`;

				const inspectBtn = tr.querySelector('.inspect-btn');
				const approveBtn = tr.querySelector('.approve-btn');
				const rejectBtn = tr.querySelector('.reject-btn');

				inspectBtn.addEventListener('click', () => this.handleInspectPeriod(period, periodId, container));
				approveBtn.addEventListener('click', () => this.handleApprovePeriod(period, periodId, container));
				rejectBtn.addEventListener('click', () => this.handleRejectPeriod(period, periodId, container));

				tbody.appendChild(tr);
			});

		} catch (err) {
			console.error('Error loading submitted periods:', err);
			tbody.innerHTML = `<tr><td colspan="6" class="error">${err.message || I18n.t('app.error')}</td></tr>`;
		}
	}

	async handleInspectPeriod(period, periodId, container) {
		const modal = container.querySelector('#period-inspect-modal');
		const modalSubtitle = container.querySelector('#inspect-modal-subtitle');
		const modalBody = container.querySelector('#inspect-modal-body');

		const empDisplayName = period.employeeName || period.employeeId;
		modalSubtitle.innerHTML = `
			<strong>${empDisplayName}</strong> (${period.teamName || I18n.t('common.none')}) • 
			${I18n.t('periods.period')}: <strong>${period.yearMonth}</strong> • 
			${I18n.t('periods.submittedAt', { time: Format.dateTime(period.submittedAt) })}
			${period.comment ? `<br><em>${I18n.t('common.comment')}: "${period.comment}"</em>` : ''}
		`;

		modalBody.innerHTML = `<div class="loading-spinner" style="text-align: center; padding: 2rem;">${I18n.t('common.loading')}</div>`;
		modal.style.display = 'block';

		this.currentInspectedPeriod = { period, periodId };

		try {
			const data = await ApprovalsApi.getSubmittedPeriodDetail(periodId);
			this.renderPeriodDetailModalContent(modalBody, data, period);
		} catch (err) {
			console.error('Error loading submitted period details:', err);
			modalBody.innerHTML = `<div class="error-msg" style="color: var(--error-color); padding: 1.5rem; text-align: center;">${err.message || I18n.t('app.error')}</div>`;
		}
	}

	renderPeriodDetailModalContent(container, data, period) {
		if (!data) {
			container.innerHTML = `<div class="empty-state">${I18n.t('common.noData')}</div>`;
			return;
		}

		const periodBalClass = data.periodBalanceMinutes > 0 ? 'positive' : (data.periodBalanceMinutes < 0 ? 'negative' : 'neutral');
		const periodBalSign = data.periodBalanceMinutes > 0 ? '+' : '';
		const endBalClass = data.endBalanceMinutes > 0 ? 'positive' : (data.endBalanceMinutes < 0 ? 'negative' : 'neutral');
		const endBalSign = data.endBalanceMinutes > 0 ? '+' : '';

		let daysHtml = '';
		if (!data.daySummaries || data.daySummaries.length === 0) {
			daysHtml = `<tr><td colspan="9" class="empty-cell">${I18n.t('periods.noDailyRecords')}</td></tr>`;
		} else {
			daysHtml = data.daySummaries.map(day => {
				const dayBalClass = day.balance > 0 ? 'positive' : (day.balance < 0 ? 'negative' : 'neutral');
				const dayBalSign = day.balance > 0 ? '+' : '';
				const isWeekend = day.isOff && day.targetMinutes === 0;
				const dayStateText = I18n.t(`enums.dayState.${day.state}`, {}, day.stateLabel || day.state);
				const locText = day.workingLocation ? I18n.t(`enums.workingLocation.${day.workingLocation}`, {}, day.workingLocation) : '-';

				let entriesSummary = '-';
				if (day.workEntries && day.workEntries.length > 0) {
					entriesSummary = day.workEntries.map(e => {
						let tag = `${e.start}-${e.end} (${Format.duration(e.durationMinutes)})`;
						const badges = [];
						if (e.source === 'MANUAL') badges.push(I18n.t('times.manualBadge'));
						if (e.modified) badges.push(I18n.t('times.modifiedBadge'));
						if (e.isOnCall) badges.push(I18n.t('times.onCallBadge'));
						if (badges.length > 0) {
							tag += ` [${badges.join(', ')}]`;
						}
						if (e.createdBy && period && e.createdBy !== period.employeeId && e.createdBy !== period.username && e.createdBy !== (period.employeeName || '')) {
							tag += ` (${I18n.t('times.createdBy', { user: e.createdBy })})`;
						}
						return tag;
					}).join(', ');
				}

				return `
					<tr class="${isWeekend ? 'row-off' : ''}">
						<td><strong>${day.date}</strong></td>
						<td>${Format.duration(day.targetMinutes)}</td>
						<td>${Format.duration(day.actualMinutes)}</td>
						<td>${day.holidayMinutes > 0 ? Format.duration(day.holidayMinutes) : '-'}</td>
						<td>${day.absenceMinutes > 0 ? Format.duration(day.absenceMinutes) : '-'}</td>
						<td class="${dayBalClass}"><strong>${dayBalSign}${Format.duration(day.balance)}</strong></td>
						<td><small>${locText}</small></td>
						<td><small>${entriesSummary}</small></td>
						<td><span class="status-badge state-${(day.state || 'OPEN').toLowerCase()}">${dayStateText}</span></td>
					</tr>
				`;
			}).join('');
		}

		container.innerHTML = `
			<!-- Summary Metrics Grid -->
			<div class="summary-grid report-summary-grid" style="margin-bottom: 1.5rem;">
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
			<div class="report-section card" style="padding: 1rem;">
				<h4 style="margin-top: 0; margin-bottom: 0.75rem;">${I18n.t('reports.dailyBreakdown')}</h4>
				<div class="table-container" style="max-height: 400px; overflow-y: auto;">
					<table class="data-table">
						<thead>
							<tr>
								<th>${I18n.t('common.date')}</th>
								<th>${I18n.t('common.target')}</th>
								<th>${I18n.t('common.actual')}</th>
								<th>${I18n.t('periods.holiday')}</th>
								<th>${I18n.t('periods.absence')}</th>
								<th>${I18n.t('reports.dayBalance')}</th>
								<th>${I18n.t('common.location')}</th>
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

	async handleApprovePeriod(period, periodId, container) {
		const empDisplayName = period.employeeName || period.employeeId;
		const comment = await NotificationDialog.prompt(
			I18n.t('approvals.approvePeriodPrompt', { employee: empDisplayName, period: period.yearMonth }),
			I18n.t('approvals.approvePeriodTitle'),
			`${I18n.t('common.comment')}...`,
			'',
			false
		);
		if (comment === null) return false; // User cancelled

		try {
			await ApprovalsApi.approvePeriod(periodId, comment || null);
			await NotificationDialog.info(I18n.t('approvals.periodApprovedSuccess', { employee: empDisplayName, period: period.yearMonth }), I18n.t('common.success'));
			await this.loadPeriods(container);
			return true;
		} catch (err) {
			console.error('Error approving period:', err);
			await NotificationDialog.error(err.message || I18n.t('app.error'));
			return false;
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
		if (reason === null) return false; // User cancelled

		try {
			await ApprovalsApi.rejectPeriod(periodId, reason);
			await NotificationDialog.info(I18n.t('approvals.periodRejectedSuccess', { period: period.yearMonth }), I18n.t('common.success'));
			await this.loadPeriods(container);
			return true;
		} catch (err) {
			console.error('Error rejecting period:', err);
			await NotificationDialog.error(err.message || I18n.t('app.error'));
			return false;
		}
	}
}
