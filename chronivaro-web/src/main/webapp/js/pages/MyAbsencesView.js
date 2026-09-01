import AbsenceApi from '../api/AbsenceApi.js';
import VacationAccountApi from '../api/VacationAccountApi.js';
import TeamApi from '../api/TeamApi.js';
import EmployeeApi from '../api/EmployeeApi.js';
import AuthApi from '../api/AuthApi.js';
import NotificationDialog from '../utils/NotificationDialog.js';
import Format from '../utils/Format.js';
import I18n from '../i18n/I18n.js';

export default class MyAbsencesView {
    constructor(app) {
        this.app = app;
        this.absenceTypes = [];
        this.selectedYear = new Date().getFullYear();
        this.teams = [];
        this.employees = [];
        this.selectedTeamId = '';
        this.selectedEmployeeId = '';
        this.currentUserEmployeeId = null;
        this.canManage = AuthApi.hasRole('Supervisor') || AuthApi.hasRole('HR')
                || AuthApi.hasRole('Administrator') || AuthApi.hasRole('StrolchAdmin');
    }

    async render() {
        const container = document.createElement('div');
        container.id = 'my-absences-view';
        container.innerHTML = `
            <div class="view-header" style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 1rem;">
                <h2 id="absences-view-title">${I18n.t('absences.title')}</h2>
                <div class="actions">
                    <button id="request-absence-btn" class="primary-btn">
                        <span class="icon">➕</span> ${I18n.t('absences.requestAbsence')}
                    </button>
                </div>
            </div>

            ${this.canManage ? `
            <!-- Supervisor / HR Filter Controls -->
            <section class="card" style="margin-bottom: 1.5rem; padding: 1rem 1.25rem;">
                <div class="filter-bar" id="absences-manager-filter-bar" style="display: flex; flex-wrap: wrap; gap: 1rem; align-items: flex-end;">
                    <div class="filter-group">
                        <label for="absences-team-filter">${I18n.t('common.team')}:</label>
                        <select id="absences-team-filter" style="min-width: 160px;">
                            <option value="">${I18n.t('common.allTeams')}</option>
                        </select>
                    </div>
                    <div class="filter-group">
                        <label for="absences-employee-filter">${I18n.t('common.employee')}:</label>
                        <select id="absences-employee-filter" style="min-width: 200px;">
                            <option value="">${I18n.t('common.loading')}</option>
                        </select>
                    </div>
                </div>
            </section>
            ` : ''}

            <!-- Vacation Account Summary Section -->
            <section class="vacation-summary-section card">
                <div class="section-title-bar">
                    <div>
                        <h3>${I18n.t('absences.vacationAccount')}</h3>
                        <span class="text-muted" id="vacation-employee-info" style="font-size: 0.85rem; display: none;"></span>
                    </div>
                    <div class="year-picker">
                        <label for="vacation-year-select">${I18n.t('common.year')}:</label>
                        <select id="vacation-year-select"></select>
                    </div>
                </div>

                <div class="vacation-cards-grid" id="vacation-cards">
                    <div class="summary-card">
                        <span class="card-label">${I18n.t('absences.initialEntitlement')}</span>
                        <span class="card-value" id="card-entitlement">--</span>
                    </div>
                    <div class="summary-card">
                        <span class="card-label">${I18n.t('absences.carryOver')}</span>
                        <span class="card-value" id="card-carry-over">--</span>
                    </div>
                    <div class="summary-card">
                        <span class="card-label">${I18n.t('absences.adjustments')}</span>
                        <span class="card-value" id="card-corrections">--</span>
                    </div>
                    <div class="summary-card">
                        <span class="card-label">${I18n.t('absences.usedApproved')}</span>
                        <span class="card-value" id="card-usage">--</span>
                    </div>
                    <div class="summary-card highlight" id="card-remaining-container">
                        <span class="card-label">${I18n.t('absences.currentBalance')}</span>
                        <span class="card-value" id="card-remaining">--</span>
                    </div>
                </div>

                <details class="vacation-journal-details" id="vacation-journal-details">
                    <summary>${I18n.t('absences.viewVacationJournal', { year: `<span id="journal-year-label">${this.selectedYear}</span>` })}</summary>
                    <table class="data-table" id="vacation-journal-table">
                        <thead>
                            <tr>
                                <th>${I18n.t('common.effectiveDate')}</th>
                                <th>${I18n.t('common.createdAt')}</th>
                                <th>${I18n.t('common.type')}</th>
                                <th>${I18n.t('common.amount')}</th>
                                <th>${I18n.t('common.comment')}</th>
                                <th>${I18n.t('common.createdBy')}</th>
                            </tr>
                        </thead>
                        <tbody id="vacation-journal-tbody">
                            <tr><td colspan="6">${I18n.t('absences.loadingJournal')}</td></tr>
                        </tbody>
                    </table>
                </details>
            </section>

            <!-- Personal Absences History Section -->
            <section class="absences-history-section card">
                <div class="section-title-bar">
                    <h3>${I18n.t('absences.history')}</h3>
                </div>

                <div class="filter-controls">
                    <div class="filter-group">
                        <label for="absence-filter-from">${I18n.t('common.from')}:</label>
                        <input type="date" id="absence-filter-from">
                    </div>
                    <div class="filter-group">
                        <label for="absence-filter-to">${I18n.t('common.to')}:</label>
                        <input type="date" id="absence-filter-to">
                    </div>
                    <div class="filter-group">
                        <label for="absence-filter-status">${I18n.t('common.status')}:</label>
                        <select id="absence-filter-status">
                            <option value="">${I18n.t('common.allStatuses')}</option>
                            <option value="SUBMITTED">${I18n.t('enums.absenceState.SUBMITTED')}</option>
                            <option value="APPROVED">${I18n.t('enums.absenceState.APPROVED')}</option>
                            <option value="REJECTED">${I18n.t('enums.absenceState.REJECTED')}</option>
                            <option value="CANCELLED">${I18n.t('enums.absenceState.CANCELLED')}</option>
                            <option value="DRAFT">${I18n.t('enums.absenceState.DRAFT')}</option>
                        </select>
                    </div>
                    <div class="filter-group">
                        <label for="absence-filter-type">${I18n.t('common.type')}:</label>
                        <select id="absence-filter-type">
                            <option value="">${I18n.t('common.allTypes')}</option>
                        </select>
                    </div>
                    <button id="refresh-absences-btn" class="secondary-btn">${I18n.t('common.filter')}</button>
                </div>

                <table class="data-table" id="absences-table">
                    <thead>
                        <tr>
                            <th>${I18n.t('common.type')}</th>
                            <th>${I18n.t('absences.startDate')}</th>
                            <th>${I18n.t('absences.endDate')}</th>
                            <th>${I18n.t('common.duration')}</th>
                            <th>${I18n.t('common.status')}</th>
                            <th>${I18n.t('common.comment')}</th>
                            <th>${I18n.t('common.createdBy')}</th>
                            <th>${I18n.t('common.actions')}</th>
                        </tr>
                    </thead>
                    <tbody id="absences-tbody">
                        <tr><td colspan="8">${I18n.t('absences.loadingAbsences')}</td></tr>
                    </tbody>
                </table>
            </section>

            <!-- Request / Add Absence Modal -->
            <div id="absence-modal" class="modal">
                <div class="modal-content">
                    <h3 id="absence-modal-title">${I18n.t('absences.requestAbsence')}</h3>
                    <form id="absence-form">
                        <div class="form-grid">
                            <div class="form-group full-width" id="modal-employee-group" style="display: none;">
                                <label for="modal-target-employee">${I18n.t('common.employee')}:</label>
                                <input type="text" id="modal-target-employee" readonly disabled style="background-color: var(--input-disabled-bg, #f1f5f9);">
                            </div>
                            <div class="form-group full-width">
                                <label for="modal-absence-type">${I18n.t('absences.absenceType')}:</label>
                                <select id="modal-absence-type" required>
                                    <option value="">${I18n.t('absences.selectAbsenceTypePrompt')}</option>
                                </select>
                            </div>
                            <div class="form-group">
                                <label for="modal-start-date">${I18n.t('absences.startDate')}:</label>
                                <input type="date" id="modal-start-date" required>
                            </div>
                            <div class="form-group">
                                <label for="modal-end-date">${I18n.t('absences.endDate')}:</label>
                                <input type="date" id="modal-end-date" required>
                            </div>
                            <div class="form-group">
                                <label for="modal-duration-type">${I18n.t('absences.durationType')}:</label>
                                <select id="modal-duration-type" required>
                                    <option value="FULL_DAY">${I18n.t('enums.durationType.FULL_DAY')}</option>
                                    <option value="HALF_DAY">${I18n.t('absences.halfDayMorning')} / ${I18n.t('absences.halfDayAfternoon')}</option>
                                    <option value="HOURS">${I18n.t('enums.durationType.HOURLY')}</option>
                                </select>
                            </div>
                            <div class="form-group" id="modal-half-day-group" style="display: none;">
                                <label for="modal-half-day-part">${I18n.t('absences.halfDayPart')}:</label>
                                <select id="modal-half-day-part">
                                    <option value="MORNING">${I18n.t('absences.morning')}</option>
                                    <option value="AFTERNOON">${I18n.t('absences.afternoon')}</option>
                                </select>
                            </div>
                            <div class="form-group" id="modal-hours-group" style="display: none;">
                                <label for="modal-hours">${I18n.t('absences.hoursCount')}:</label>
                                <input type="number" id="modal-hours" min="0.25" max="24" step="0.25" placeholder="e.g. 4">
                            </div>
                            <div class="form-group full-width">
                                <label for="modal-comment" id="modal-comment-label">${I18n.t('common.comment')}:</label>
                                <textarea id="modal-comment" rows="3" placeholder="${I18n.t('common.comment')}..."></textarea>
                            </div>
                            <div class="form-group full-width" id="modal-approval-mode-group" style="display: none;">
                                <label style="display: flex; align-items: center; gap: 0.5rem; cursor: pointer;">
                                    <input type="checkbox" id="modal-direct-approve" checked>
                                    <span>${I18n.t('absences.createDirectlyApproved')}</span>
                                </label>
                            </div>
                        </div>
                        <div class="modal-actions">
                            <button type="submit" id="modal-submit-btn" class="primary-btn">${I18n.t('absences.submitDraft')}</button>
                            <button type="button" id="save-draft-btn" class="secondary-btn">${I18n.t('absences.saveDraft')}</button>
                            <button type="button" id="close-absence-modal-btn" class="secondary-btn">${I18n.t('common.cancel')}</button>
                        </div>
                    </form>
                </div>
            </div>
        `;

        // References
        const titleEl = container.querySelector('#absences-view-title');
        const teamFilter = container.querySelector('#absences-team-filter');
        const employeeFilter = container.querySelector('#absences-employee-filter');

        const yearSelect = container.querySelector('#vacation-year-select');
        const journalYearLabel = container.querySelector('#journal-year-label');
        const cardEntitlement = container.querySelector('#card-entitlement');
        const cardCarryOver = container.querySelector('#card-carry-over');
        const cardCorrections = container.querySelector('#card-corrections');
        const cardUsage = container.querySelector('#card-usage');
        const cardRemaining = container.querySelector('#card-remaining');
        const cardRemainingContainer = container.querySelector('#card-remaining-container');
        const journalTbody = container.querySelector('#vacation-journal-tbody');

        const filterFrom = container.querySelector('#absence-filter-from');
        const filterTo = container.querySelector('#absence-filter-to');
        const filterStatus = container.querySelector('#absence-filter-status');
        const filterType = container.querySelector('#absence-filter-type');
        const refreshAbsencesBtn = container.querySelector('#refresh-absences-btn');
        const absencesTbody = container.querySelector('#absences-tbody');

        const modal = container.querySelector('#absence-modal');
        const modalTitle = container.querySelector('#absence-modal-title');
        const modalEmployeeGroup = container.querySelector('#modal-employee-group');
        const modalTargetEmployee = container.querySelector('#modal-target-employee');
        const modalApprovalModeGroup = container.querySelector('#modal-approval-mode-group');
        const modalDirectApprove = container.querySelector('#modal-direct-approve');
        const modalSubmitBtn = container.querySelector('#modal-submit-btn');

        const requestAbsenceBtn = container.querySelector('#request-absence-btn');
        const saveDraftBtn = container.querySelector('#save-draft-btn');
        const closeAbsenceModalBtn = container.querySelector('#close-absence-modal-btn');
        const absenceForm = container.querySelector('#absence-form');
        const modalAbsenceType = container.querySelector('#modal-absence-type');
        const modalStartDate = container.querySelector('#modal-start-date');
        const modalEndDate = container.querySelector('#modal-end-date');
        const modalDurationType = container.querySelector('#modal-duration-type');
        const modalHalfDayGroup = container.querySelector('#modal-half-day-group');
        const modalHalfDayPart = container.querySelector('#modal-half-day-part');
        const modalHoursGroup = container.querySelector('#modal-hours-group');
        const modalHours = container.querySelector('#modal-hours');
        const modalComment = container.querySelector('#modal-comment');
        const modalCommentLabel = container.querySelector('#modal-comment-label');

        let currentEditingAbsence = null;

        // Populate Years (current year - 2 to current year + 2)
        const currentYear = new Date().getFullYear();
        for (let y = currentYear - 2; y <= currentYear + 2; y++) {
            const opt = document.createElement('option');
            opt.value = y;
            opt.textContent = y;
            if (y === this.selectedYear) opt.selected = true;
            yearSelect.appendChild(opt);
        }

        // Set default filter date range (Jan 1 of current year to Dec 31 of current year)
        filterFrom.value = `${currentYear}-01-01`;
        filterTo.value = `${currentYear}-12-31`;

        const isManagingOther = () => {
            return this.canManage && this.selectedEmployeeId && this.selectedEmployeeId !== this.currentUserEmployeeId;
        };

        const updateViewTitle = () => {
            if (titleEl) {
                if (isManagingOther()) {
                    titleEl.textContent = I18n.t('absences.employeeAbsencesTitle');
                } else {
                    titleEl.textContent = I18n.t('absences.title');
                }
            }
            if (requestAbsenceBtn) {
                if (isManagingOther()) {
                    requestAbsenceBtn.innerHTML = `<span class="icon">➕</span> ${I18n.t('absences.addAbsenceForEmployee')}`;
                } else {
                    requestAbsenceBtn.innerHTML = `<span class="icon">➕</span> ${I18n.t('absences.requestAbsence')}`;
                }
            }
        };

        // Load Absence Types
        const loadAbsenceTypes = async () => {
            try {
                this.absenceTypes = await AbsenceApi.getAbsenceTypes();
                // Populate filter dropdown
                filterType.innerHTML = `<option value="">${I18n.t('common.allTypes')}</option>`;
                modalAbsenceType.innerHTML = `<option value="">${I18n.t('absences.selectAbsenceTypePrompt')}</option>`;

                this.absenceTypes.forEach(t => {
                    const opt1 = document.createElement('option');
                    opt1.value = t.code;
                    opt1.textContent = t.name;
                    filterType.appendChild(opt1);

                    const opt2 = document.createElement('option');
                    opt2.value = t.code;
                    opt2.textContent = t.name;
                    opt2.dataset.allowedDurations = JSON.stringify(t.durationTypes || t.allowedDurations || []);
                    opt2.dataset.commentRequired = t.commentRequired ? 'true' : 'false';
                    modalAbsenceType.appendChild(opt2);
                });
            } catch (err) {
                console.error('Failed to load absence types', err);
            }
        };

        // Load Vacation Account
        const loadVacationAccount = async () => {
            const year = parseInt(yearSelect.value, 10);
            if (journalYearLabel) journalYearLabel.textContent = year;
            journalTbody.innerHTML = `<tr><td colspan="6">${I18n.t('absences.loadingJournal')}</td></tr>`;
            try {
                let response;
                if (isManagingOther()) {
                    response = await VacationAccountApi.getEmployeeVacationAccount(this.selectedEmployeeId, year);
                } else {
                    response = await VacationAccountApi.getMyVacationAccount(year);
                }
                const summary = (response && response.summary) ? response.summary : (response || {});
                const entries = response.entries || [];

                const empInfoEl = document.getElementById('vacation-employee-info');
                if (empInfoEl) {
                    const empName = summary.employeeName;
                    const username = summary.username;
                    const persNr = summary.personalNumber;
                    let display = '';
                    if (username && persNr && username !== persNr) {
                        display = `${username} (${persNr})`;
                    } else if (username) {
                        display = username;
                    } else if (persNr) {
                        display = persNr;
                    } else if (empName) {
                        display = empName;
                    }
                    if (display) {
                        empInfoEl.textContent = display;
                        empInfoEl.style.display = 'inline';
                    } else {
                        empInfoEl.style.display = 'none';
                    }
                }

                cardEntitlement.textContent = Format.durationDays(summary.entitlementMinutes ?? 0);
                cardCarryOver.textContent = Format.durationDays(summary.carryOverMinutes ?? 0);
                cardCorrections.textContent = Format.durationDays(summary.correctionsMinutes ?? 0);
                cardUsage.textContent = Format.durationDays(summary.usageMinutes ?? 0);
                cardRemaining.textContent = Format.durationDays(summary.remainingMinutes ?? 0);

                if ((summary.remainingMinutes ?? 0) < 0) {
                    cardRemainingContainer.className = 'summary-card danger';
                } else {
                    cardRemainingContainer.className = 'summary-card highlight';
                }

                // Render journal table
                journalTbody.innerHTML = '';
                if (entries.length === 0) {
                    journalTbody.innerHTML = `<tr><td colspan="6" class="empty-state">${I18n.t('absences.noJournalEntries')}</td></tr>`;
                } else {
                    entries.forEach(entry => {
                        const row = document.createElement('tr');
                        const entryValue = entry.value !== undefined ? entry.value : (entry.amountMinutes !== undefined ? entry.amountMinutes : 0);
                        const isPositive = entryValue >= 0;
                        const sign = isPositive ? '+' : '';
                        const formattedAmount = `${sign}${Format.durationDays(entryValue)}`;
                        const amountClass = isPositive ? 'text-success' : 'text-danger';
                        const rawEntryType = entry.vacationType || entry.entryType || entry.type || entry.bookingType;
                        const entryType = rawEntryType ? String(rawEntryType).toUpperCase() : null;
                        const typeLabel = entryType ? I18n.t(`enums.vacationEntryType.${entryType}`, {}, entryType) : '--';

                        row.innerHTML = `
                            <td>${Format.date(entry.date || entry.effectiveDate)}</td>
                            <td>${Format.dateTime(entry.createdAt || entry.date)}</td>
                            <td><span class="journal-type-badge">${typeLabel}</span></td>
                            <td class="${amountClass}">${formattedAmount}</td>
                            <td>${entry.comment || '--'}</td>
                            <td>${entry.createdBy || '--'}</td>
                        `;
                        journalTbody.appendChild(row);
                    });
                }
            } catch (err) {
                console.error('Failed to load vacation account', err);
                cardEntitlement.textContent = I18n.t('common.error');
                cardCarryOver.textContent = I18n.t('common.error');
                cardCorrections.textContent = I18n.t('common.error');
                cardUsage.textContent = I18n.t('common.error');
                cardRemaining.textContent = I18n.t('common.error');
                journalTbody.innerHTML = `<tr><td colspan="6" class="error">${err.message || I18n.t('app.error')}</td></tr>`;
            }
        };

        // Load Absences
        const loadAbsences = async () => {
            absencesTbody.innerHTML = `<tr><td colspan="8">${I18n.t('absences.loadingAbsences')}</td></tr>`;
            try {
                const params = {
                    from: filterFrom.value || undefined,
                    to: filterTo.value || undefined,
                    status: filterStatus.value || undefined,
                    absenceTypeCode: filterType.value || undefined
                };

                let absences;
                if (isManagingOther()) {
                    absences = await AbsenceApi.getEmployeeAbsences(this.selectedEmployeeId, params);
                } else {
                    absences = await AbsenceApi.getMyAbsences(params);
                }

                absencesTbody.innerHTML = '';

                if (absences.length === 0) {
                    absencesTbody.innerHTML = `<tr><td colspan="8" class="empty-state">${I18n.t('absences.noAbsenceRequests')}</td></tr>`;
                    return;
                }

                absences.forEach(absence => {
                    const row = document.createElement('tr');

                    // Determine duration label
                    let durationLabel = absence.durationType;
                    if (absence.durationType === 'HALF_DAY') {
                        const dayPartVal = absence.dayPart || absence.halfDayPart;
                        const partName = dayPartVal 
                            ? I18n.t(`absences.${dayPartVal.toLowerCase()}`, {}, dayPartVal)
                            : '';
                        durationLabel = `${I18n.t('absences.halfDayMorning')} (${partName})`;
                    } else if (absence.durationType === 'HOURS') {
                        const hoursCount = absence.hours || (absence.minutes ? (absence.minutes / 60) : (absence.durationMinutes ? absence.durationMinutes / 60 : ''));
                        durationLabel = `${hoursCount} ${I18n.t('common.hours')}`;
                    } else {
                        durationLabel = I18n.t('enums.durationType.FULL_DAY');
                    }

                    // Determine status badge
                    const absenceState = absence.state || absence.status;
                    let statusClass = 'badge-draft';
                    if (absenceState === 'SUBMITTED') statusClass = 'badge-submitted';
                    else if (absenceState === 'APPROVED') statusClass = 'badge-approved';
                    else if (absenceState === 'REJECTED') statusClass = 'badge-rejected';
                    else if (absenceState === 'CANCELLED') statusClass = 'badge-cancelled';

                    const statusLabel = I18n.t(`enums.absenceState.${absenceState}`, {}, absenceState);

                    // Notes / Reason
                    let notes = absence.comment || '';
                    if (absenceState === 'REJECTED' && (absence.rejectionReason || absence.decisionComment)) {
                        const reason = absence.rejectionReason || absence.decisionComment;
                        notes += (notes ? ' | ' : '') + `${I18n.t('common.rejectionReason')}: ${reason}`;
                    }

                    // Actions
                    let actionsHtml = '--';
                    if (absenceState === 'DRAFT' && !isManagingOther()) {
                        actionsHtml = `
                            <button class="action-btn edit-btn" data-id="${absence.id}" data-version="${absence.version || 0}">${I18n.t('common.edit')}</button>
                            <button class="action-btn submit-btn" data-id="${absence.id}" data-version="${absence.version || 0}">${I18n.t('common.submit')}</button>
                            <button class="action-btn cancel-btn" data-id="${absence.id}" data-version="${absence.version || 0}">${I18n.t('common.cancel')}</button>
                        `;
                    } else if (absenceState === 'SUBMITTED' || absenceState === 'APPROVED') {
                        if (!isManagingOther()) {
                            actionsHtml = `<button class="action-btn cancel-btn" data-id="${absence.id}" data-version="${absence.version || 0}">${I18n.t('common.cancel')}</button>`;
                        }
                    }

                    const createdByDisplay = absence.createdBy || '--';

                    row.innerHTML = `
                        <td><strong>${absence.absenceTypeName || absence.absenceTypeCode || absence.absenceTypeId}</strong></td>
                        <td>${Format.date(absence.start || absence.startDate)}</td>
                        <td>${Format.date(absence.end || absence.endDate)}</td>
                        <td>${durationLabel}</td>
                        <td><span class="badge ${statusClass}">${statusLabel}</span></td>
                        <td>${notes || '--'}</td>
                        <td>${createdByDisplay}</td>
                        <td>${actionsHtml}</td>
                    `;

                    // Attach edit action
                    const editBtn = row.querySelector('.edit-btn');
                    if (editBtn) {
                        editBtn.addEventListener('click', () => {
                            currentEditingAbsence = absence;
                            if (modalTitle) modalTitle.textContent = I18n.t('absences.editAbsence');

                            const typeCode = absence.absenceTypeCode || absence.absenceTypeId;
                            if (typeCode) {
                                modalAbsenceType.value = typeCode;
                            }
                            updateModalFields();

                            const startStr = (absence.start || absence.startDate || '').substring(0, 10);
                            const endStr = (absence.end || absence.endDate || '').substring(0, 10);
                            modalStartDate.value = startStr || new Date().toISOString().split('T')[0];
                            modalEndDate.value = endStr || modalStartDate.value;

                            const duration = absence.durationType || 'FULL_DAY';
                            modalDurationType.value = duration;
                            onDurationTypeChange();

                            if (duration === 'HALF_DAY') {
                                modalHalfDayPart.value = absence.dayPart || absence.halfDayPart || 'MORNING';
                            } else if (duration === 'HOURS') {
                                const hoursVal = absence.hours || (absence.minutes ? (absence.minutes / 60) : (absence.durationMinutes ? absence.durationMinutes / 60 : ''));
                                modalHours.value = hoursVal;
                            }

                            modalComment.value = absence.comment || '';
                            if (modalEmployeeGroup) modalEmployeeGroup.style.display = 'none';
                            if (modalApprovalModeGroup) modalApprovalModeGroup.style.display = 'none';
                            if (saveDraftBtn) saveDraftBtn.style.display = 'inline-block';
                            if (modalSubmitBtn) modalSubmitBtn.textContent = I18n.t('absences.submitDraft');

                            modal.style.display = 'block';
                        });
                    }

                    // Attach submit action
                    const submitBtn = row.querySelector('.submit-btn');
                    if (submitBtn) {
                        submitBtn.addEventListener('click', async () => {
                            try {
                                await AbsenceApi.submitAbsence(absence.id, absence.version);
                                await NotificationDialog.info(I18n.t('absences.requestSubmitted'));
                                await loadAbsences();
                                await loadVacationAccount();
                            } catch (err) {
                                NotificationDialog.error(err.message || I18n.t('app.error'));
                            }
                        });
                    }

                    // Attach cancel action
                    const cancelBtn = row.querySelector('.cancel-btn');
                    if (cancelBtn) {
                        cancelBtn.addEventListener('click', async () => {
                            const confirmed = await NotificationDialog.confirm(
                                I18n.t('absences.confirmCancel', { type: absence.absenceTypeName || I18n.t('common.type') }),
                                I18n.t('absences.cancelAbsence')
                            );
                            if (confirmed) {
                                try {
                                    await AbsenceApi.cancelAbsence(absence.id, absence.version, 'Cancelled by employee');
                                    await NotificationDialog.info(I18n.t('absences.requestCancelled'));
                                    await loadAbsences();
                                    await loadVacationAccount();
                                } catch (err) {
                                    NotificationDialog.error(err.message || I18n.t('app.error'));
                                }
                            }
                        });
                    }

                    absencesTbody.appendChild(row);
                });
            } catch (err) {
                console.error('Failed to load absences', err);
                absencesTbody.innerHTML = `<tr><td colspan="8" class="error">${err.message || I18n.t('app.error')}</td></tr>`;
            }
        };

        // Form logic
        const updateModalFields = () => {
            const selectedOpt = modalAbsenceType.options[modalAbsenceType.selectedIndex];
            if (!selectedOpt || !selectedOpt.value) return;

            const isCommentRequired = selectedOpt.dataset.commentRequired === 'true';
            modalCommentLabel.textContent = isCommentRequired 
                ? `${I18n.t('common.comment')} *:` 
                : `${I18n.t('common.comment')}:`;
            modalComment.required = isCommentRequired;

            let allowed = [];
            try {
                allowed = JSON.parse(selectedOpt.dataset.allowedDurations || '[]');
            } catch (e) {}

            modalDurationType.innerHTML = '';
            const allDurations = [
                { value: 'FULL_DAY', label: I18n.t('enums.durationType.FULL_DAY') },
                { value: 'HALF_DAY', label: `${I18n.t('absences.halfDayMorning')} / ${I18n.t('absences.halfDayAfternoon')}` },
                { value: 'HOURS', label: I18n.t('enums.durationType.HOURLY') }
            ];

            const available = allowed.length > 0
                ? allDurations.filter(d => allowed.includes(d.value))
                : allDurations;

            available.forEach(d => {
                const opt = document.createElement('option');
                opt.value = d.value;
                opt.textContent = d.label;
                modalDurationType.appendChild(opt);
            });

            onDurationTypeChange();
        };

        const onDurationTypeChange = () => {
            const duration = modalDurationType.value;
            if (duration === 'HALF_DAY') {
                modalHalfDayGroup.style.display = 'block';
                modalHoursGroup.style.display = 'none';
                modalEndDate.value = modalStartDate.value;
                modalEndDate.disabled = true;
            } else if (duration === 'HOURS') {
                modalHalfDayGroup.style.display = 'none';
                modalHoursGroup.style.display = 'block';
                modalEndDate.value = modalStartDate.value;
                modalEndDate.disabled = true;
            } else {
                modalHalfDayGroup.style.display = 'none';
                modalHoursGroup.style.display = 'none';
                modalEndDate.disabled = false;
            }
        };

        modalAbsenceType.addEventListener('change', updateModalFields);
        modalDurationType.addEventListener('change', onDurationTypeChange);
        modalStartDate.addEventListener('change', () => {
            if (modalDurationType.value === 'HALF_DAY' || modalDurationType.value === 'HOURS') {
                modalEndDate.value = modalStartDate.value;
            }
        });

        // Event Listeners
        yearSelect.addEventListener('change', () => {
            this.selectedYear = parseInt(yearSelect.value, 10);
            loadVacationAccount();
        });

        refreshAbsencesBtn.addEventListener('click', loadAbsences);

        requestAbsenceBtn.addEventListener('click', () => {
            currentEditingAbsence = null;
            const managingOther = isManagingOther();

            if (modalTitle) {
                modalTitle.textContent = managingOther 
                    ? I18n.t('absences.addAbsenceForEmployee')
                    : I18n.t('absences.requestAbsence');
            }

            if (modalEmployeeGroup) {
                if (managingOther) {
                    const empObj = this.employees.find(e => e.id === this.selectedEmployeeId);
                    modalTargetEmployee.value = empObj ? `${empObj.firstname || ''} ${empObj.lastname || ''}`.trim() || empObj.name : this.selectedEmployeeId;
                    modalEmployeeGroup.style.display = 'block';
                } else {
                    modalEmployeeGroup.style.display = 'none';
                }
            }

            if (modalApprovalModeGroup) {
                modalApprovalModeGroup.style.display = managingOther ? 'block' : 'none';
                if (modalDirectApprove) modalDirectApprove.checked = true;
            }

            if (saveDraftBtn) {
                saveDraftBtn.style.display = managingOther ? 'none' : 'inline-block';
            }

            if (modalSubmitBtn) {
                modalSubmitBtn.textContent = managingOther ? I18n.t('common.save') : I18n.t('absences.submitDraft');
            }

            const todayStr = new Date().toISOString().split('T')[0];
            modalStartDate.value = todayStr;
            modalEndDate.value = todayStr;
            modalEndDate.disabled = false;
            modalDurationType.value = 'FULL_DAY';
            modalHalfDayGroup.style.display = 'none';
            modalHoursGroup.style.display = 'none';
            modalComment.value = '';
            if (modalAbsenceType.options.length > 1) {
                modalAbsenceType.selectedIndex = 1;
                updateModalFields();
            }
            modal.style.display = 'block';
        });

        saveDraftBtn.addEventListener('click', async () => {
            const absenceTypeCode = modalAbsenceType.value;
            if (!absenceTypeCode) {
                NotificationDialog.error(I18n.t('absences.selectAbsenceTypeError'));
                return;
            }

            const startDate = modalStartDate.value;
            const endDate = modalDurationType.value === 'FULL_DAY' ? modalEndDate.value : startDate;
            if (startDate > endDate) {
                NotificationDialog.error(I18n.t('absences.invalidDateRange'));
                return;
            }

            const payload = {
                absenceTypeCode,
                start: startDate + 'T00:00:00.000+01:00',
                end: endDate + 'T23:59:59.999+01:00',
                durationType: modalDurationType.value,
                dayPart: modalDurationType.value === 'HALF_DAY' ? modalHalfDayPart.value : undefined,
                halfDayPart: modalDurationType.value === 'HALF_DAY' ? modalHalfDayPart.value : undefined,
                hours: modalDurationType.value === 'HOURS' ? parseFloat(modalHours.value) : undefined,
                minutes: modalDurationType.value === 'HOURS' ? Math.round(parseFloat(modalHours.value) * 60) : undefined,
                comment: modalComment.value.trim() || undefined,
                state: 'DRAFT'
            };

            try {
                if (currentEditingAbsence) {
                    await AbsenceApi.updateAbsence(currentEditingAbsence.id, payload, currentEditingAbsence.version);
                } else {
                    await AbsenceApi.requestAbsence(payload);
                }
                modal.style.display = 'none';
                currentEditingAbsence = null;
                await NotificationDialog.info(I18n.t('absences.draftSaved'));
                await loadAbsences();
                await loadVacationAccount();
            } catch (err) {
                NotificationDialog.error(err.message || I18n.t('app.error'));
            }
        });

        closeAbsenceModalBtn.addEventListener('click', () => {
            modal.style.display = 'none';
            currentEditingAbsence = null;
        });

        absenceForm.addEventListener('submit', async (e) => {
            e.preventDefault();
            const absenceTypeCode = modalAbsenceType.value;
            if (!absenceTypeCode) {
                NotificationDialog.error(I18n.t('absences.selectAbsenceTypeError'));
                return;
            }

            const startDate = modalStartDate.value;
            const endDate = modalDurationType.value === 'FULL_DAY' ? modalEndDate.value : startDate;
            if (startDate > endDate) {
                NotificationDialog.error(I18n.t('absences.invalidDateRange'));
                return;
            }

            const managingOther = isManagingOther();
            const directApprove = managingOther && modalDirectApprove && modalDirectApprove.checked;

            const payload = {
                absenceTypeCode,
                start: startDate + 'T00:00:00.000+01:00',
                end: endDate + 'T23:59:59.999+01:00',
                durationType: modalDurationType.value,
                dayPart: modalDurationType.value === 'HALF_DAY' ? modalHalfDayPart.value : undefined,
                halfDayPart: modalDurationType.value === 'HALF_DAY' ? modalHalfDayPart.value : undefined,
                hours: modalDurationType.value === 'HOURS' ? parseFloat(modalHours.value) : undefined,
                minutes: modalDurationType.value === 'HOURS' ? Math.round(parseFloat(modalHours.value) * 60) : undefined,
                comment: modalComment.value.trim() || undefined,
                state: directApprove ? 'APPROVED' : (managingOther ? 'SUBMITTED' : undefined)
            };

            try {
                if (currentEditingAbsence) {
                    const updateRes = await AbsenceApi.updateAbsence(currentEditingAbsence.id, payload, currentEditingAbsence.version);
                    const updatedVersion = (updateRes && updateRes.version !== undefined) ? updateRes.version : currentEditingAbsence.version;
                    await AbsenceApi.submitAbsence(currentEditingAbsence.id, updatedVersion);
                    await NotificationDialog.info(I18n.t('absences.requestSubmitted'));
                } else if (managingOther) {
                    await AbsenceApi.createEmployeeAbsence(this.selectedEmployeeId, payload);
                    const msg = directApprove 
                        ? I18n.t('absences.absenceCreatedDirectlyApproved')
                        : I18n.t('absences.absenceCreatedSubmitted');
                    await NotificationDialog.info(msg);
                } else {
                    await AbsenceApi.requestAbsence(payload);
                    await NotificationDialog.info(I18n.t('absences.requestSubmitted'));
                }
                modal.style.display = 'none';
                currentEditingAbsence = null;
                await loadAbsences();
                await loadVacationAccount();
            } catch (err) {
                NotificationDialog.error(err.message || I18n.t('app.error'));
            }
        });

        // Initialize Manager Dropdowns if applicable
        const initManagerControls = async () => {
            if (!this.canManage) return;

            try {
                const [teamsRes, employeesRes] = await Promise.all([
                    TeamApi.getTeams().catch(() => []),
                    EmployeeApi.getEmployees().catch(() => [])
                ]);

                this.teams = Array.isArray(teamsRes) ? teamsRes : (teamsRes.data || []);
                this.employees = Array.isArray(employeesRes) ? employeesRes : (employeesRes.data || []);

                // Determine current user's linked employee
                const user = AuthApi.getCurrentUser();
                if (user) {
                    const matchedEmp = this.employees.find(e =>
                        (e.userId && e.userId === user.username) ||
                        (e.username && e.username === user.username) ||
                        (e.name && e.name.toLowerCase() === user.username.toLowerCase())
                    );
                    if (matchedEmp) {
                        this.currentUserEmployeeId = matchedEmp.id;
                    }
                }

                if (teamFilter) {
                    teamFilter.innerHTML = `<option value="">${I18n.t('common.allTeams')}</option>`;
                    this.teams.forEach(t => {
                        const opt = document.createElement('option');
                        opt.value = t.id;
                        opt.textContent = t.name;
                        teamFilter.appendChild(opt);
                    });

                    teamFilter.addEventListener('change', () => {
                        this.selectedTeamId = teamFilter.value;
                        updateEmployeeDropdown();
                    });
                }

                const updateEmployeeDropdown = () => {
                    if (!employeeFilter) return;
                    employeeFilter.innerHTML = '';

                    let filtered = this.employees;
                    if (this.selectedTeamId) {
                        filtered = this.employees.filter(e => e.primaryTeamId === this.selectedTeamId || e.teamId === this.selectedTeamId);
                    }

                    if (filtered.length === 0) {
                        employeeFilter.innerHTML = `<option value="">${I18n.t('common.noData')}</option>`;
                        this.selectedEmployeeId = '';
                        return;
                    }

                    filtered.forEach(e => {
                        const opt = document.createElement('option');
                        opt.value = e.id;
                        const name = `${e.firstname || ''} ${e.lastname || ''}`.trim() || e.name || e.id;
                        opt.textContent = e.personalNumber ? `${name} (${e.personalNumber})` : name;
                        if (this.currentUserEmployeeId && e.id === this.currentUserEmployeeId) {
                            opt.textContent += ` [${I18n.t('common.self') || 'Self'}]`;
                        }
                        employeeFilter.appendChild(opt);
                    });

                    if (this.currentUserEmployeeId && filtered.some(e => e.id === this.currentUserEmployeeId)) {
                        employeeFilter.value = this.currentUserEmployeeId;
                        this.selectedEmployeeId = this.currentUserEmployeeId;
                    } else if (filtered.length > 0) {
                        employeeFilter.value = filtered[0].id;
                        this.selectedEmployeeId = filtered[0].id;
                    }

                    updateViewTitle();
                };

                updateEmployeeDropdown();

                if (employeeFilter) {
                    employeeFilter.addEventListener('change', () => {
                        this.selectedEmployeeId = employeeFilter.value;
                        updateViewTitle();
                        loadVacationAccount();
                        loadAbsences();
                    });
                }
            } catch (err) {
                console.error('Failed to init manager controls', err);
            }
        };

        // Initial Load
        await loadAbsenceTypes();
        await initManagerControls();
        updateViewTitle();
        await loadVacationAccount();
        await loadAbsences();

        return container;
    }
}
