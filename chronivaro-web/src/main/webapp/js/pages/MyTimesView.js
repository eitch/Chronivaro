import WorkEntryApi from '../api/WorkEntryApi.js';
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
					</tr>
				</thead>
				<tbody>
					<tr><td colspan="5">${I18n.t('common.loading')}</td></tr>
				</tbody>
			</table>
		`;

        const fromInput = container.querySelector('#date-from');
        const toInput = container.querySelector('#date-to');
        const refreshBtn = container.querySelector('#refresh-times');
        const tbody = container.querySelector('tbody');

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
                    tbody.innerHTML = `<tr><td colspan="5">${I18n.t('times.noEntries')}</td></tr>`;
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
					`;
                    tbody.appendChild(row);
                });
            } catch (err) {
                console.error(err);
                tbody.innerHTML = `<tr><td colspan="5" class="error">${err.message || I18n.t('app.error')}</td></tr>`;
            }
        };

        refreshBtn.addEventListener('click', refresh);

        refresh();

        return container;
    }
}
