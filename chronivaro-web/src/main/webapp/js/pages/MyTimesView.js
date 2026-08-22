import WorkEntryApi from '../api/WorkEntryApi.js';
import NotificationDialog from '../utils/NotificationDialog.js';
import Format from '../utils/Format.js';
import I18n from '../i18n/I18n.js';

export default class MyTimesView {

    constructor(app) {
        this.app = app;
    }

    async render() {
        const container = document.createElement('div');
        container.id = 'my-times-view';
        container.innerHTML = `
			<h2>${I18n.t('times.title')}</h2>
			<div id="filter-controls">
				<label for="date-from">${I18n.t('common.from')}:</label>
				<input type="date" id="date-from">
				<label for="date-to">${I18n.t('common.to')}:</label>
				<input type="date" id="date-to">
				<button id="refresh-times" class="secondary-btn">${I18n.t('common.refresh')}</button>
			</div>
			<table id="work-entries-table" class="data-table">
				<thead>
					<tr>
						<th>${I18n.t('times.startTime')}</th>
						<th>${I18n.t('times.endTime')}</th>
						<th>${I18n.t('common.duration')}</th>
						<th>${I18n.t('times.workingLocation')}</th>
						<th>${I18n.t('common.comment')}</th>
						<th>${I18n.t('common.actions')}</th>
					</tr>
				</thead>
				<tbody>
					<tr><td colspan="6">${I18n.t('common.loading')}</td></tr>
				</tbody>
			</table>

			<!-- Shorten / Edit Work Entry Modal -->
			<div id="work-entry-modal" class="modal">
				<div class="modal-content">
					<h3 id="work-entry-modal-title">${I18n.t('times.shortenDialogTitle')}</h3>
					<p class="help-text" style="margin-bottom: 15px; color: #666; font-size: 0.9em;">
						${I18n.t('times.shortenHelpText')}
					</p>
					<form id="work-entry-form">
						<div class="form-grid">
							<div class="form-group">
								<label>${I18n.t('times.startTime')}:</label>
								<input type="text" id="modal-start-time" readonly disabled style="background: #f0f0f0;">
							</div>
							<div class="form-group">
								<label for="modal-end-time">${I18n.t('times.shortenTime')}:</label>
								<input type="datetime-local" id="modal-end-time" required>
							</div>
							<div class="form-group full-width">
								<label for="modal-comment">${I18n.t('common.comment')}:</label>
								<textarea id="modal-comment" rows="3" placeholder="${I18n.t('common.comment')}..."></textarea>
							</div>
						</div>
						<div class="modal-actions">
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
        const tbody = container.querySelector('tbody');

        const modal = container.querySelector('#work-entry-modal');
        const modalStartTime = container.querySelector('#modal-start-time');
        const modalEndTime = container.querySelector('#modal-end-time');
        const modalComment = container.querySelector('#modal-comment');
        const closeModalBtn = container.querySelector('#close-work-entry-modal-btn');
        const workEntryForm = container.querySelector('#work-entry-form');

        let currentEditingEntry = null;

        const toLocalDateTimeInputString = (isoStr) => {
            if (!isoStr) return '';
            const d = new Date(isoStr);
            const pad = (n) => String(n).padStart(2, '0');
            return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
        };

        // Set default range: current month
        const now = new Date();
        const firstDay = new Date(now.getFullYear(), now.getMonth(), 1);
        const lastDay = new Date(now.getFullYear(), now.getMonth() + 1, 0);

        fromInput.value = firstDay.toISOString().split('T')[0];
        toInput.value = lastDay.toISOString().split('T')[0];

        const refresh = async () => {
            try {
                const from = new Date(fromInput.value);
                const to = new Date(toInput.value);
                // Set to end of day
                to.setHours(23, 59, 59, 999);

                const entries = await WorkEntryApi.getMyWorkEntries(from, to);
                tbody.innerHTML = '';

                if (entries.length === 0) {
                    tbody.innerHTML = `<tr><td colspan="6">${I18n.t('times.noEntries')}</td></tr>`;
                    return;
                }

                entries.forEach(entry => {
                    const row = document.createElement('tr');
                    const locationText = entry.workingLocation 
                        ? I18n.t(`enums.workingLocation.${entry.workingLocation}`, {}, entry.workingLocation)
                        : '';
                    const endText = entry.end 
                        ? Format.dateTime(entry.end)
                        : `<span class="badge badge-working">${I18n.t('common.running')}</span>`;

                    row.innerHTML = `
						<td>${Format.dateTime(entry.start)}</td>
						<td>${endText}</td>
						<td>${Format.duration(entry.durationMinutes)}</td>
						<td>${locationText}</td>
						<td>${entry.comment || ''}</td>
						<td>
							<button class="secondary-btn edit-entry-btn" title="${I18n.t('times.shortenEntry')}">${I18n.t('common.edit')}</button>
						</td>
					`;

                    const editBtn = row.querySelector('.edit-entry-btn');
                    if (editBtn) {
                        editBtn.addEventListener('click', () => {
                            currentEditingEntry = entry;
                            modalStartTime.value = Format.dateTime(entry.start);
                            const endIso = entry.end || new Date().toISOString();
                            modalEndTime.value = toLocalDateTimeInputString(endIso);
                            modalEndTime.min = toLocalDateTimeInputString(entry.start);
                            if (entry.end) {
                                modalEndTime.max = toLocalDateTimeInputString(entry.end);
                            } else {
                                modalEndTime.max = toLocalDateTimeInputString(new Date().toISOString());
                            }
                            modalComment.value = entry.comment || '';
                            modal.style.display = 'block';
                        });
                    }

                    tbody.appendChild(row);
                });
            } catch (err) {
                console.error(err);
                tbody.innerHTML = `<tr><td colspan="6" class="error">${err.message || I18n.t('app.error')}</td></tr>`;
            }
        };

        closeModalBtn.addEventListener('click', () => {
            modal.style.display = 'none';
            currentEditingEntry = null;
        });

        workEntryForm.addEventListener('submit', async (e) => {
            e.preventDefault();
            if (!currentEditingEntry) return;

            try {
                const newEndDate = new Date(modalEndTime.value);
                const startDate = new Date(currentEditingEntry.start);
                if (newEndDate <= startDate) {
                    NotificationDialog.error(I18n.t('times.invalidDuration'));
                    return;
                }

                if (currentEditingEntry.end) {
                    const originalEndDate = new Date(currentEditingEntry.end);
                    if (newEndDate > originalEndDate) {
                        NotificationDialog.error(I18n.t('times.shortenOnlyError'));
                        return;
                    }
                }

                const payload = {
                    id: currentEditingEntry.id,
                    start: currentEditingEntry.start,
                    end: newEndDate.toISOString(),
                    comment: modalComment.value.trim() || undefined,
                    workingLocation: currentEditingEntry.workingLocation
                };

                await WorkEntryApi.updateWorkEntry(currentEditingEntry.id, payload);
                modal.style.display = 'none';
                currentEditingEntry = null;
                NotificationDialog.info(I18n.t('times.entryUpdated'));
                await refresh();
            } catch (err) {
                NotificationDialog.error(err.message || I18n.t('app.error'));
            }
        });

        refreshBtn.addEventListener('click', refresh);

        refresh();

        return container;
    }
}
