import WorkEntryApi from '../api/WorkEntryApi.js';
import Format from '../utils/Format.js';

export default class MyTimesView {

    constructor(app) {
        this.app = app;
    }

    async render() {
        const container = document.createElement('div');
        container.id = 'my-times-view';
        container.innerHTML = `
			<h2>My Times</h2>
			<div id="filter-controls">
				<label for="date-from">From:</label>
				<input type="date" id="date-from">
				<label for="date-to">To:</label>
				<input type="date" id="date-to">
				<button id="refresh-times">Refresh</button>
			</div>
			<table id="work-entries-table">
				<thead>
					<tr>
						<th>Start</th>
						<th>End</th>
						<th>Duration</th>
						<th>Comment</th>
					</tr>
				</thead>
				<tbody>
					<tr><td colspan="4">Loading...</td></tr>
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
                    tbody.innerHTML = '<tr><td colspan="4">No entries found.</td></tr>';
                    return;
                }

                entries.forEach(entry => {
                    const row = document.createElement('tr');
                    row.innerHTML = `
						<td>${new Date(entry.start).toLocaleString()}</td>
						<td>${entry.end ? new Date(entry.end).toLocaleString() : 'Running...'}</td>
						<td>${Format.duration(entry.durationMinutes)}</td>
						<td>${entry.comment || ''}</td>
					`;
                    tbody.appendChild(row);
                });
            } catch (err) {
                console.error(err);
                tbody.innerHTML = `<tr><td colspan="4" class="error">${err.message}</td></tr>`;
            }
        };

        refreshBtn.addEventListener('click', refresh);

        refresh();

        return container;
    }
}
