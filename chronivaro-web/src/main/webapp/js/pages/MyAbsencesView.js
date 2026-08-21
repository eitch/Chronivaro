import AbsenceApi from '../api/AbsenceApi.js';
import VacationAccountApi from '../api/VacationAccountApi.js';
import NotificationDialog from '../utils/NotificationDialog.js';
import Format from '../utils/Format.js';
import I18n from '../i18n/I18n.js';

export default class MyAbsencesView {
    constructor(app) {
        this.app = app;
        this.absenceTypes = [];
        this.selectedYear = new Date().getFullYear();
    }

    async render() {
        const container = document.createElement('div');
        container.id = 'my-absences-view';
        container.innerHTML = `
            <div class="view-header">
                <h2>${I18n.t('absences.title')}</h2>
                <div class="actions">
                    <button id="request-absence-btn" class="primary-btn">+ ${I18n.t('absences.requestAbsence')}</button>
                </div>
            </div>

            <!-- Vacation Account Summary Section -->
            <section class="vacation-summary-section card">
                <div class="section-title-bar">
                    <h3>${I18n.t('absences.vacationAccount')}</h3>
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
                                <th>${I18n.t('common.type')}</th>
                                <th>${I18n.t('common.amount')}</th>
                                <th>${I18n.t('common.comment')}</th>
                                <th>${I18n.t('common.createdBy')}</th>
                            </tr>
                        </thead>
                        <tbody id="vacation-journal-tbody">
                            <tr><td colspan="5">${I18n.t('absences.loadingJournal')}</td></tr>
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
                            <th>${I18n.t('common.actions')}</th>
                        </tr>
                    </thead>
                    <tbody id="absences-tbody">
                        <tr><td colspan="7">${I18n.t('absences.loadingAbsences')}</td></tr>
                    </tbody>
                </table>
            </section>

            <!-- Request Absence Modal -->
            <div id="absence-modal" class="modal">
                <div class="modal-content">
                    <h3 id="absence-modal-title">${I18n.t('absences.requestAbsence')}</h3>
                    <form id="absence-form">
                        <div class="form-grid">
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
                        </div>
                        <div class="modal-actions">
                            <button type="submit" class="primary-btn">${I18n.t('absences.submitDraft')}</button>
                            <button type="button" id="save-draft-btn" class="secondary-btn">${I18n.t('absences.saveDraft')}</button>
                            <button type="button" id="close-absence-modal-btn" class="secondary-btn">${I18n.t('common.cancel')}</button>
                        </div>
                    </form>
                </div>
            </div>
        `;

        // References
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
            journalTbody.innerHTML = `<tr><td colspan="5">${I18n.t('absences.loadingJournal')}</td></tr>`;
            try {
                const response = await VacationAccountApi.getMyVacationAccount(year);
                const summary = response.summary || {};
                const entries = response.entries || [];

                cardEntitlement.textContent = Format.durationDays(summary.entitlementMinutes);
                cardCarryOver.textContent = Format.durationDays(summary.carryOverMinutes);
                cardCorrections.textContent = Format.durationDays(summary.correctionsMinutes);
                cardUsage.textContent = Format.durationDays(summary.usageMinutes);
                cardRemaining.textContent = Format.durationDays(summary.remainingMinutes);

                if (summary.remainingMinutes < 0) {
                    cardRemainingContainer.className = 'summary-card danger';
                } else {
                    cardRemainingContainer.className = 'summary-card highlight';
                }

                // Render journal table
                journalTbody.innerHTML = '';
                if (entries.length === 0) {
                    journalTbody.innerHTML = `<tr><td colspan="5" class="empty-state">${I18n.t('absences.noJournalEntries')}</td></tr>`;
                } else {
                    entries.forEach(entry => {
                        const row = document.createElement('tr');
                        const isPositive = entry.amountMinutes >= 0;
                        const sign = isPositive ? '+' : '';
                        const formattedAmount = `${sign}${Format.durationDays(entry.amountMinutes)}`;
                        const amountClass = isPositive ? 'text-success' : 'text-danger';
                        const typeLabel = I18n.t(`enums.vacationEntryType.${entry.entryType}`, {}, entry.entryType);

                        row.innerHTML = `
                            <td>${Format.date(entry.effectiveDate)}</td>
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
                journalTbody.innerHTML = `<tr><td colspan="5" class="error">${err.message || I18n.t('app.error')}</td></tr>`;
            }
        };

        // Load Personal Absences
        const loadAbsences = async () => {
            absencesTbody.innerHTML = `<tr><td colspan="7">${I18n.t('absences.loadingAbsences')}</td></tr>`;
            try {
                const params = {
                    from: filterFrom.value || undefined,
                    to: filterTo.value || undefined,
                    status: filterStatus.value || undefined,
                    absenceTypeCode: filterType.value || undefined
                };

                const absences = await AbsenceApi.getMyAbsences(params);
                absencesTbody.innerHTML = '';

                if (absences.length === 0) {
                    absencesTbody.innerHTML = `<tr><td colspan="7" class="empty-state">${I18n.t('absences.noAbsenceRequests')}</td></tr>`;
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
                    if (absenceState === 'DRAFT') {
                        actionsHtml = `
                            <button class="action-btn submit-btn" data-id="${absence.id}" data-version="${absence.version || 0}">${I18n.t('common.submit')}</button>
                            <button class="action-btn cancel-btn" data-id="${absence.id}" data-version="${absence.version || 0}">${I18n.t('common.cancel')}</button>
                        `;
                    } else if (absenceState === 'SUBMITTED' || absenceState === 'APPROVED') {
                        actionsHtml = `<button class="action-btn cancel-btn" data-id="${absence.id}" data-version="${absence.version || 0}">${I18n.t('common.cancel')}</button>`;
                    }

                    row.innerHTML = `
                        <td><strong>${absence.absenceTypeName || absence.absenceTypeCode || absence.absenceTypeId}</strong></td>
                        <td>${Format.date(absence.start || absence.startDate)}</td>
                        <td>${Format.date(absence.end || absence.endDate)}</td>
                        <td>${durationLabel}</td>
                        <td><span class="badge ${statusClass}">${statusLabel}</span></td>
                        <td>${notes || '--'}</td>
                        <td>${actionsHtml}</td>
                    `;

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
                absencesTbody.innerHTML = `<tr><td colspan="7" class="error">${err.message || I18n.t('app.error')}</td></tr>`;
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
            modal.style.display = 'flex';
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
                halfDayPart: modalDurationType.value === 'HALF_DAY' ? modalHalfDayPart.value : undefined,
                hours: modalDurationType.value === 'HOURS' ? parseFloat(modalHours.value) : undefined,
                comment: modalComment.value.trim() || undefined,
                state: 'DRAFT'
            };

            try {
                await AbsenceApi.requestAbsence(payload);
                modal.style.display = 'none';
                await NotificationDialog.info(I18n.t('absences.draftSaved'));
                await loadAbsences();
                await loadVacationAccount();
            } catch (err) {
                NotificationDialog.error(err.message || I18n.t('app.error'));
            }
        });

        closeAbsenceModalBtn.addEventListener('click', () => {
            modal.style.display = 'none';
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

            const payload = {
                absenceTypeCode,
                start: startDate + 'T00:00:00.000+01:00',
                end: endDate + 'T23:59:59.999+01:00',
                durationType: modalDurationType.value,
                halfDayPart: modalDurationType.value === 'HALF_DAY' ? modalHalfDayPart.value : undefined,
                hours: modalDurationType.value === 'HOURS' ? parseFloat(modalHours.value) : undefined,
                comment: modalComment.value.trim() || undefined
            };

            try {
                await AbsenceApi.requestAbsence(payload);
                modal.style.display = 'none';
                await NotificationDialog.info(I18n.t('absences.requestSubmitted'));
                await loadAbsences();
                await loadVacationAccount();
            } catch (err) {
                NotificationDialog.error(err.message || I18n.t('app.error'));
            }
        });

        // Initial Load
        await loadAbsenceTypes();
        await loadVacationAccount();
        await loadAbsences();

        return container;
    }
}
