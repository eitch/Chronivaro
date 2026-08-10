import WorkEntryApi from '../api/WorkEntryApi.js';
import NotificationDialog from '../utils/NotificationDialog.js';

export default class DashboardView {

    constructor(app) {
        this.app = app;
    }

    async render() {
        const container = document.createElement('div');
        container.id = 'dashboard-view';
        container.innerHTML = `
			<h2>Dashboard</h2>
			<div id="status-container">
				<p>Current Status: <span id="presence-status">Loading...</span></p>
				<div id="timer-controls">
					<button id="start-timer" disabled>Start</button>
					<button id="stop-timer" disabled>Stop</button>
				</div>
			</div>
			<div id="day-summary">
				<h3>Today's Summary</h3>
				<p>Worked: <span id="worked-time">...</span></p>
				<p>Required: <span id="required-time">...</span></p>
				<p>Balance: <span id="day-balance">...</span></p>
			</div>
		`;

        const statusSpan = container.querySelector('#presence-status');
        const startBtn = container.querySelector('#start-timer');
        const stopBtn = container.querySelector('#stop-timer');
        const workedSpan = container.querySelector('#worked-time');
        const requiredSpan = container.querySelector('#required-time');
        const balanceSpan = container.querySelector('#day-balance');

        const refresh = async () => {
            try {
                const summary = await WorkEntryApi.getDaySummary(new Date());
                statusSpan.textContent = summary.state;
                statusSpan.className = summary.state === 'WORKING' ? 'status-working' : 'status-not-working';

                workedSpan.textContent = `${summary.actualTime} min`;
                requiredSpan.textContent = `${summary.targetTime} min`;
                balanceSpan.textContent = `${summary.balance} min`;

                startBtn.disabled = summary.state === 'WORKING';
                stopBtn.disabled = summary.state !== 'WORKING';
            } catch (err) {
                console.error(err);
                statusSpan.textContent = 'Error loading status';
            }
        };

        startBtn.addEventListener('click', async () => {
            try {
                await WorkEntryApi.startTimer();
                await refresh();
            } catch (err) {
                NotificationDialog.error(err.message);
            }
        });

        stopBtn.addEventListener('click', async () => {
            try {
                await WorkEntryApi.stopTimer();
                await refresh();
            } catch (err) {
                NotificationDialog.error(err.message);
            }
        });

        refresh();

        return container;
    }
}
