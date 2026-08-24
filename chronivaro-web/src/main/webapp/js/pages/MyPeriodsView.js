import PeriodApi from '../api/PeriodApi.js';
import ReportApi from '../api/ReportApi.js';
import NotificationDialog from '../utils/NotificationDialog.js';
import Format from '../utils/Format.js';
import MonthPicker from '../utils/MonthPicker.js';
import I18n from '../i18n/I18n.js';

export default class MyPeriodsView {

	constructor(app) {
		this.app = app;
		const now = new Date();
		const year = now.getFullYear();
		const month = String(now.getMonth() + 1).padStart(2, '0');
		this.selectedYearMonth = `${year}-${month}`;
		this.currentPeriodStatus = null;
		this.currentMonthSummary = null;
	}

	async render(params) {
		if (params && params.yearMonth) {
			this.selectedYearMonth = params.yearMonth;
		}

		const container = document.createElement('div');
		container.id = 'my-periods-view';
		container.innerHTML = `
			<div class="view-header">
				<h2>${I18n.t('periods.title')}</h2>
				<div class="month-navigation-controls">
					<button id="prev-period-btn" class="secondary-btn" title="Previous Month">&laquo; ${I18n.t('common.prev')}</button>
					<div class="month-input-wrapper">
						<label for="period-month-picker">${I18n.t('periods.period')}:</label>
						<input type="month" id="period-month-picker" value="${this.selectedYearMonth}">
					</div>
					<button id="next-period-btn" class="secondary-btn" title="Next Month">${I18n.t('common.next')} &raquo;</button>
					<button id="current-period-btn" class="secondary-btn">${I18n.t('common.currentMonth')}</button>
					<button id="refresh-period-btn" class="secondary-btn">${I18n.t('common.refresh')}</button>
					<button id="download-period-pdf-btn" class="secondary-btn btn-export" title="Download Month Report PDF">
						<span class="icon">📄</span> ${I18n.t('reports.exportPdf')}
					</button>
				</div>
			</div>

			<!-- Status & Alert Section -->
			<div id="period-status-banner" class="period-status-banner card">
				<div class="status-header-row">
					<div>
						<span class="period-label">${I18n.t('periods.periodStatusFor', { period: `<strong id="banner-period-title">${this.selectedYearMonth}</strong>` })}</span>
						<span id="period-status-badge" class="status-badge badge-open">${I18n.t('enums.periodState.OPEN')}</span>
					</div>
					<div id="period-timestamps" class="period-timestamps">
						<!-- Dynamically populated timestamps -->
					</div>
				</div>
				<div id="rejection-alert" class="rejection-alert hidden">
					<strong>${I18n.t('common.rejectionReason')}:</strong>
					<p id="rejection-comment-text"></p>
				</div>
			</div>

			<!-- Period Submission Action Box (when OPEN or REJECTED) -->
			<section id="period-action-section" class="period-action-section card">
				<div class="section-title-bar">
					<h3>${I18n.t('periods.periodSubmissionClosing')}</h3>
				</div>
				<div id="period-submission-content">
					<!-- Dynamically rendered based on lifecycle state -->
				</div>
			</section>

			<!-- Monthly Summary Cards -->
			<section class="period-summary-section card">
				<div class="section-title-bar">
					<h3>${I18n.t('periods.monthlyBalanceSummary')}</h3>
				</div>
				<div class="period-cards-grid" id="period-summary-cards">
					<div class="summary-card">
						<span class="card-label">${I18n.t('periods.targetHours')}</span>
						<span class="card-value" id="summary-target-time">--</span>
					</div>
					<div class="summary-card">
						<span class="card-label">${I18n.t('periods.actualHours')}</span>
						<span class="card-value" id="summary-actual-time">--</span>
					</div>
					<div class="summary-card">
						<span class="card-label">${I18n.t('periods.holidayHours')}</span>
						<span class="card-value" id="summary-holiday-time">--</span>
					</div>
					<div class="summary-card">
						<span class="card-label">${I18n.t('absences.title')}</span>
						<span class="card-value" id="summary-absence-time">--</span>
					</div>
					<div class="summary-card highlight" id="card-period-balance-container">
						<span class="card-label">${I18n.t('periods.balanceHours')}</span>
						<span class="card-value" id="summary-period-balance">--</span>
					</div>
					<div class="summary-card">
						<span class="card-label">${I18n.t('common.balance')} (${I18n.t('common.prev')})</span>
						<span class="card-value" id="summary-initial-balance">--</span>
					</div>
					<div class="summary-card highlight" id="card-end-balance-container">
						<span class="card-label">${I18n.t('common.total')} ${I18n.t('common.balance')}</span>
						<span class="card-value" id="summary-end-balance">--</span>
					</div>
				</div>

				<div id="calculation-snapshot-container" class="calculation-snapshot-container hidden">
					<details class="snapshot-details" id="snapshot-details">
						<summary>${I18n.t('periods.calculationSnapshotDetails')}</summary>
						<pre id="snapshot-json-view" class="snapshot-json-view"></pre>
					</details>
				</div>
			</section>

			<!-- Daily Breakdown Table -->
			<section class="period-daily-breakdown card">
				<div class="section-title-bar">
					<h3>${I18n.t('periods.dailyTimeBreakdown')}</h3>
				</div>
				<table class="data-table" id="daily-breakdown-table">
					<thead>
						<tr>
							<th>${I18n.t('common.date')}</th>
							<th>${I18n.t('periods.day')}</th>
							<th>${I18n.t('common.status')} / ${I18n.t('times.workingLocation')}</th>
							<th>${I18n.t('common.target')}</th>
							<th>${I18n.t('periods.worked')}</th>
							<th>${I18n.t('periods.holiday')}</th>
							<th>${I18n.t('periods.absence')}</th>
							<th>${I18n.t('periods.dailyBalance')}</th>
						</tr>
					</thead>
					<tbody id="daily-breakdown-tbody">
						<tr><td colspan="8">${I18n.t('common.loading')}</td></tr>
					</tbody>
				</table>
			</section>
		`;

		this.bindEvents(container);
		await this.loadPeriodData(container);

		return container;
	}

	bindEvents(container) {
		const monthPicker = container.querySelector('#period-month-picker');
		const prevBtn = container.querySelector('#prev-period-btn');
		const nextBtn = container.querySelector('#next-period-btn');
		const currentBtn = container.querySelector('#current-period-btn');
		const refreshBtn = container.querySelector('#refresh-period-btn');

		monthPicker.addEventListener('change', () => {
			if (monthPicker.value) {
				this.selectedYearMonth = monthPicker.value;
				this.loadPeriodData(container);
			}
		});

		prevBtn.addEventListener('click', () => {
			this.navigateMonth(-1);
			monthPicker.value = this.selectedYearMonth;
			this.loadPeriodData(container);
		});

		nextBtn.addEventListener('click', () => {
			this.navigateMonth(1);
			monthPicker.value = this.selectedYearMonth;
			this.loadPeriodData(container);
		});

		currentBtn.addEventListener('click', () => {
			const now = new Date();
			const year = now.getFullYear();
			const month = String(now.getMonth() + 1).padStart(2, '0');
			this.selectedYearMonth = `${year}-${month}`;
			monthPicker.value = this.selectedYearMonth;
			this.loadPeriodData(container);
		});

		refreshBtn.addEventListener('click', () => {
			this.loadPeriodData(container);
		});

		const downloadPdfBtn = container.querySelector('#download-period-pdf-btn');
		if (downloadPdfBtn) {
			downloadPdfBtn.addEventListener('click', () => this.downloadPdf());
		}

		MonthPicker.init(container);
	}

	async downloadPdf() {
		try {
			const lang = (window.I18n && window.I18n.getLanguage) ? window.I18n.getLanguage() : 'de';
			await ReportApi.downloadMonthReportPdf(this.selectedYearMonth, null, lang);
		} catch (err) {
			console.error('Failed to download period PDF', err);
			NotificationDialog.error(err.message || I18n.t('app.error'));
		}
	}

	navigateMonth(offset) {
		const [yearStr, monthStr] = this.selectedYearMonth.split('-');
		let year = parseInt(yearStr, 10);
		let month = parseInt(monthStr, 10) + offset;

		if (month < 1) {
			month = 12;
			year -= 1;
		} else if (month > 12) {
			month = 1;
			year += 1;
		}

		this.selectedYearMonth = `${year}-${String(month).padStart(2, '0')}`;
	}

	async loadPeriodData(container) {
		const titleEl = container.querySelector('#banner-period-title');
		const statusBadge = container.querySelector('#period-status-badge');
		const timestampsEl = container.querySelector('#period-timestamps');
		const rejectionAlert = container.querySelector('#rejection-alert');
		const rejectionCommentText = container.querySelector('#rejection-comment-text');
		const submissionContent = container.querySelector('#period-submission-content');
		const dailyTbody = container.querySelector('#daily-breakdown-tbody');
		const snapshotContainer = container.querySelector('#calculation-snapshot-container');
		const snapshotJsonView = container.querySelector('#snapshot-json-view');

		if (titleEl) titleEl.textContent = this.selectedYearMonth;
		statusBadge.textContent = I18n.t('common.loading');
		statusBadge.className = 'status-badge';
		dailyTbody.innerHTML = `<tr><td colspan="8">${I18n.t('common.loading')}</td></tr>`;

		try {
			const [statusResult, summaryResult] = await Promise.all([
				PeriodApi.getMyPeriodStatus(this.selectedYearMonth),
				PeriodApi.getMonthSummary(this.selectedYearMonth)
			]);

			this.currentPeriodStatus = statusResult;
			this.currentMonthSummary = summaryResult;

			this.renderStatusBanner(container);
			this.renderSummaryCards(container);
			this.renderDailyBreakdown(container);
			this.renderSubmissionSection(container);

		} catch (err) {
			console.error('Error loading period data:', err);
			statusBadge.textContent = I18n.t('common.error');
			statusBadge.className = 'status-badge badge-rejected';
			dailyTbody.innerHTML = `<tr><td colspan="8" class="error">${err.message || I18n.t('app.error')}</td></tr>`;
			submissionContent.innerHTML = `<div class="error-box"><p>${err.message || I18n.t('app.error')}</p></div>`;
		}
	}

	renderStatusBanner(container) {
		const statusBadge = container.querySelector('#period-status-badge');
		const timestampsEl = container.querySelector('#period-timestamps');
		const rejectionAlert = container.querySelector('#rejection-alert');
		const rejectionCommentText = container.querySelector('#rejection-comment-text');

		const status = this.currentPeriodStatus ? this.currentPeriodStatus.status : 'OPEN';
		statusBadge.textContent = I18n.t(`enums.periodState.${status}`, {}, status);
		statusBadge.className = `status-badge badge-${status.toLowerCase()}`;

		let timestampHtml = '';
		if (this.currentPeriodStatus) {
			if (this.currentPeriodStatus.submittedAt) {
				timestampHtml += `<span>${I18n.t('periods.submittedAt', { time: Format.dateTime(this.currentPeriodStatus.submittedAt) })}</span>`;
			}
			if (this.currentPeriodStatus.approvedAt) {
				const by = this.currentPeriodStatus.approvedBy ? ` (${this.currentPeriodStatus.approvedBy})` : '';
				timestampHtml += `<span>${I18n.t('periods.approvedAt', { by: by, time: Format.dateTime(this.currentPeriodStatus.approvedAt) })}</span>`;
			}
			if (this.currentPeriodStatus.rejectedAt) {
				const by = this.currentPeriodStatus.rejectedBy ? ` (${this.currentPeriodStatus.rejectedBy})` : '';
				timestampHtml += `<span>${I18n.t('periods.rejectedAt', { by: by, time: Format.dateTime(this.currentPeriodStatus.rejectedAt) })}</span>`;
			}
		}
		timestampsEl.innerHTML = timestampHtml;

		if (status === 'REJECTED' && this.currentPeriodStatus && this.currentPeriodStatus.comment) {
			rejectionAlert.classList.remove('hidden');
			rejectionCommentText.textContent = this.currentPeriodStatus.comment;
		} else {
			rejectionAlert.classList.add('hidden');
		}
	}

	renderSummaryCards(container) {
		const targetEl = container.querySelector('#summary-target-time');
		const actualEl = container.querySelector('#summary-actual-time');
		const holidayEl = container.querySelector('#summary-holiday-time');
		const absenceEl = container.querySelector('#summary-absence-time');
		const periodBalanceEl = container.querySelector('#summary-period-balance');
		const initialBalanceEl = container.querySelector('#summary-initial-balance');
		const endBalanceEl = container.querySelector('#summary-end-balance');
		const snapshotContainer = container.querySelector('#calculation-snapshot-container');
		const snapshotJsonView = container.querySelector('#snapshot-json-view');

		if (!this.currentMonthSummary) {
			targetEl.textContent = '--';
			actualEl.textContent = '--';
			holidayEl.textContent = '--';
			absenceEl.textContent = '--';
			periodBalanceEl.textContent = '--';
			initialBalanceEl.textContent = '--';
			endBalanceEl.textContent = '--';
			snapshotContainer.classList.add('hidden');
			return;
		}

		const s = this.currentMonthSummary;
		targetEl.textContent = Format.duration(s.totalTargetMinutes);
		actualEl.textContent = Format.duration(s.totalActualMinutes);
		holidayEl.textContent = Format.duration(s.totalHolidayMinutes);
		absenceEl.textContent = Format.duration(s.totalAbsenceMinutes);

		periodBalanceEl.textContent = Format.duration(s.periodBalanceMinutes);
		periodBalanceEl.className = s.periodBalanceMinutes >= 0 ? 'card-value positive' : 'card-value negative';

		initialBalanceEl.textContent = Format.duration(s.initialBalanceMinutes);
		initialBalanceEl.className = s.initialBalanceMinutes >= 0 ? 'card-value positive' : 'card-value negative';

		endBalanceEl.textContent = Format.duration(s.endBalanceMinutes);
		endBalanceEl.className = s.endBalanceMinutes >= 0 ? 'card-value positive' : 'card-value negative';

		// Calculation Snapshot view if available
		if (this.currentPeriodStatus && this.currentPeriodStatus.calculationSnapshot) {
			try {
				const snapshotObj = JSON.parse(this.currentPeriodStatus.calculationSnapshot);
				snapshotJsonView.textContent = JSON.stringify(snapshotObj, null, 2);
				snapshotContainer.classList.remove('hidden');
			} catch (e) {
				snapshotJsonView.textContent = this.currentPeriodStatus.calculationSnapshot;
				snapshotContainer.classList.remove('hidden');
			}
		} else {
			snapshotContainer.classList.add('hidden');
		}
	}

	renderDailyBreakdown(container) {
		const tbody = container.querySelector('#daily-breakdown-tbody');
		if (!this.currentMonthSummary || !this.currentMonthSummary.daySummaries || this.currentMonthSummary.daySummaries.length === 0) {
			tbody.innerHTML = `<tr><td colspan="8">${I18n.t('periods.noDailyRecords')}</td></tr>`;
			return;
		}

		const lang = I18n.getLanguage();
		const daysDe = ['So', 'Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa'];
		const daysEn = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
		const days = lang === 'de' ? daysDe : daysEn;

		tbody.innerHTML = '';

		this.currentMonthSummary.daySummaries.forEach(day => {
			const tr = document.createElement('tr');
			const dateObj = new Date(day.date + 'T00:00:00');
			const dayName = isNaN(dateObj.getTime()) ? '' : days[dateObj.getDay()];

			let stateClass = '';
			if (day.isOff) {
				stateClass = 'badge-off-duty';
			} else if (day.state === 'WORKING') {
				stateClass = 'badge-working';
			} else {
				stateClass = 'badge-not-working';
			}

			const locName = day.workingLocation 
				? I18n.t(`enums.workingLocation.${day.workingLocation}`, {}, day.workingLocation)
				: '';
			const locationLabel = locName ? ` (${locName})` : '';
			const stateText = I18n.t(`enums.dayState.${day.state}`, {}, day.stateLabel || day.state);
			const stateDisplay = `<span class="status-badge ${stateClass}">${stateText}${locationLabel}</span>`;
			const balanceClass = day.balance >= 0 ? 'positive' : 'negative';

			tr.innerHTML = `
				<td><strong>${day.date}</strong></td>
				<td>${dayName}</td>
				<td>${stateDisplay}</td>
				<td>${Format.duration(day.targetMinutes)}</td>
				<td>${Format.duration(day.actualMinutes)}</td>
				<td>${Format.duration(day.holidayMinutes)}</td>
				<td>${Format.duration(day.absenceMinutes)}</td>
				<td class="${balanceClass}">${Format.duration(day.balance)}</td>
			`;
			tbody.appendChild(tr);
		});
	}

	renderSubmissionSection(container) {
		const submissionContent = container.querySelector('#period-submission-content');
		const status = this.currentPeriodStatus ? this.currentPeriodStatus.status : 'OPEN';

		if (status === 'OPEN' || status === 'REJECTED') {
			submissionContent.innerHTML = `
				<div class="submit-form-container">
					<p class="submit-instruction">
						${I18n.t('periods.submitInstruction')}
					</p>
					<div class="form-group">
						<label for="submit-period-comment">${I18n.t('periods.submissionComment')}</label>
						<textarea id="submit-period-comment" class="form-textarea" rows="2" placeholder="${I18n.t('periods.submissionCommentPlaceholder')}"></textarea>
					</div>
					<div class="form-actions">
						<button id="submit-period-btn" class="primary-btn">${I18n.t('periods.submitPeriodForApproval')}</button>
					</div>
				</div>
			`;

			const submitBtn = submissionContent.querySelector('#submit-period-btn');
			const commentInput = submissionContent.querySelector('#submit-period-comment');

			submitBtn.addEventListener('click', async () => {
				const confirmed = await NotificationDialog.confirm(
					I18n.t('periods.confirmSubmitDialog', { period: this.selectedYearMonth }),
					I18n.t('periods.submitPeriodForApproval')
				);
				if (!confirmed) return;

				try {
					submitBtn.disabled = true;
					submitBtn.textContent = I18n.t('periods.submitting');
					const comment = commentInput.value.trim() || null;
					await PeriodApi.submitMyPeriod(this.selectedYearMonth, comment);
					await NotificationDialog.info(I18n.t('periods.submitSuccess', { period: this.selectedYearMonth }));
					await this.loadPeriodData(container);
				} catch (err) {
					console.error('Failed to submit period:', err);
					NotificationDialog.error(err.message || I18n.t('app.error'));
					submitBtn.disabled = false;
					submitBtn.textContent = I18n.t('periods.submitPeriodForApproval');
				}
			});

		} else if (status === 'SUBMITTED') {
			submissionContent.innerHTML = `
				<div class="info-notice info-submitted">
					<span class="notice-icon">&#9432;</span>
					<div>
						<strong>${I18n.t('periods.periodSubmittedTitle')}</strong>
						<p>${I18n.t('periods.periodSubmittedDesc', { period: this.selectedYearMonth })}</p>
					</div>
				</div>
			`;
		} else if (status === 'APPROVED') {
			submissionContent.innerHTML = `
				<div class="info-notice info-approved">
					<span class="notice-icon">&#10003;</span>
					<div>
						<strong>${I18n.t('periods.periodApprovedTitle')}</strong>
						<p>${I18n.t('periods.periodApprovedDesc', { period: this.selectedYearMonth })}</p>
					</div>
				</div>
			`;
		} else if (status === 'LOCKED' || status === 'CLOSED') {
			submissionContent.innerHTML = `
				<div class="info-notice info-locked">
					<span class="notice-icon">&#128274;</span>
					<div>
						<strong>${I18n.t('periods.periodLockedTitle')}</strong>
						<p>${I18n.t('periods.periodLockedDesc', { period: this.selectedYearMonth })}</p>
					</div>
				</div>
			`;
		}
	}
}
