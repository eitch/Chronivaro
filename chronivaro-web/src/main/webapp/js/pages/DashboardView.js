import WorkEntryApi from '../api/WorkEntryApi.js';
import NotificationDialog from '../utils/NotificationDialog.js';
import Format from '../utils/Format.js';
import I18n from '../i18n/I18n.js';

export default class DashboardView {

    constructor(app) {
        this.app = app;
    }

    async render() {
        const container = document.createElement('div');
        container.id = 'dashboard-view';
        container.innerHTML = `
			<h2>${I18n.t('dashboard.title')}</h2>
			<div id="status-container">
				<p><span id="presence-status">${I18n.t('common.loading')}</span></p>
				<p><span id="off-duty-badge" class="hidden">${I18n.t('dashboard.offDuty')}</span></p>
				<div id="timer-controls">
					<fieldset id="working-location-group">
						<legend>${I18n.t('dashboard.workingLocation')}</legend>
						<label><input type="radio" name="working-location" value="HOME_OFFICE"> ${I18n.t('enums.workingLocation.HOME_OFFICE')}</label>
						<label><input type="radio" name="working-location" value="OFFICE"> ${I18n.t('enums.workingLocation.OFFICE')}</label>
						<label><input type="radio" name="working-location" value="CUSTOMER"> ${I18n.t('enums.workingLocation.CUSTOMER')}</label>
						<button id="clear-working-location" type="button">${I18n.t('dashboard.clearLocation')}</button>
					</fieldset>
                </div>
                <div id="timer-controls">
					<button id="start-timer" disabled>${I18n.t('dashboard.start')}</button>
					<button id="stop-timer" disabled>${I18n.t('dashboard.stop')}</button>
					<button id="change-working-location" type="button" disabled>${I18n.t('dashboard.changeLocation')}</button>
				</div>
				<div id="timer-comment-container" class="form-group" style="margin-top: 10px;">
					<label for="timer-comment">${I18n.t('common.comment')}:</label>
					<input type="text" id="timer-comment" placeholder="${I18n.t('dashboard.optionalComment')}" style="width: 100%; max-width: 400px; padding: 6px 10px; margin-top: 4px; display: block;">
				</div>
			</div>
			<div id="day-summary">
				<h3>${I18n.t('dashboard.todaySummary')}</h3>
				<p>${I18n.t('dashboard.worked')}: <span id="worked-time">...</span></p>
				<p>${I18n.t('dashboard.required')}: <span id="required-time">...</span></p>
				<p>${I18n.t('dashboard.balance')}: <span id="day-balance">...</span></p>
			</div>
		`;

        const statusSpan = container.querySelector('#presence-status');
        const offDutyBadge = container.querySelector('#off-duty-badge');
        const startBtn = container.querySelector('#start-timer');
        const stopBtn = container.querySelector('#stop-timer');
		const changeWorkingLocationBtn = container.querySelector('#change-working-location');
		const workingLocationGroup = container.querySelector('#working-location-group');
		const workingLocations = [...container.querySelectorAll('input[name="working-location"]')];
		const clearWorkingLocationBtn = container.querySelector('#clear-working-location');
		let locationSelectionCleared = false;
        const workedSpan = container.querySelector('#worked-time');
        const requiredSpan = container.querySelector('#required-time');
        const balanceSpan = container.querySelector('#day-balance');

        const refresh = async () => {
            try {
            	if (!locationSelectionCleared && !workingLocations.some(input => input.checked)) {
                    const defaults = await WorkEntryApi.getWorkingLocationDefaults();
                    const weekday = new Intl.DateTimeFormat('en', {weekday: 'long'}).format(new Date()).toUpperCase();
                    const defaultLocation = defaults.find(item => item.weekday === weekday &&
                        (item.durationType === 'FULL_DAY' || item.dayPart === 'MORNING'));
                    const defaultInput = defaultLocation && workingLocations
                        .find(input => input.value === defaultLocation.workingLocation);
                    if (defaultInput)
                        defaultInput.checked = true;
                }
                const summary = await WorkEntryApi.getDaySummary(new Date());
                if (summary.state === 'WORKING') {
                    statusSpan.textContent = I18n.t('presence.working');
                    statusSpan.className = 'status-working';
                    workingLocations.forEach(input => input.checked = input.value === summary.workingLocation);
                    offDutyBadge.className = 'hidden';
                } else if (summary.isOff) {
                    statusSpan.textContent = I18n.t('dashboard.offDuty');
                    statusSpan.className = 'status-off-duty';
                    offDutyBadge.className = 'off-duty-badge';
                } else {
                    statusSpan.textContent = I18n.t('presence.notWorking');
                    statusSpan.className = 'status-not-working';
                    offDutyBadge.className = 'hidden';
                }

                workedSpan.textContent = Format.duration(summary.actualMinutes);
                requiredSpan.textContent = Format.duration(summary.targetMinutes);
                balanceSpan.textContent = Format.duration(summary.balance);

				startBtn.disabled = summary.state === 'WORKING';
				stopBtn.disabled = summary.state !== 'WORKING';
				changeWorkingLocationBtn.disabled = summary.state !== 'WORKING';
				workingLocations.forEach(input => input.disabled = summary.state === 'WORKING');
				workingLocationGroup.classList.toggle('read-only', summary.state === 'WORKING');
            } catch (err) {
                console.error(err);
                statusSpan.textContent = I18n.t('dashboard.errorLoadingStatus');
            }
        };

      		startBtn.addEventListener('click', async () => {
            try {
				const workingLocation = workingLocations.find(input => input.checked);
				if (!workingLocation) {
					NotificationDialog.error(I18n.t('dashboard.selectLocationFirst'));
					return;
				}
				await WorkEntryApi.startTimer(workingLocation.value);
                await refresh();
            } catch (err) {
                NotificationDialog.error(err.message);
            }
      		});

      		clearWorkingLocationBtn.addEventListener('click', () => {
      			workingLocations.forEach(input => input.checked = false);
      			locationSelectionCleared = true;
      		});

        const timerCommentInput = container.querySelector('#timer-comment');

        stopBtn.addEventListener('click', async () => {
            try {
                const comment = timerCommentInput ? timerCommentInput.value.trim() : null;
                await WorkEntryApi.stopTimer(comment);
                if (timerCommentInput) timerCommentInput.value = '';
                await refresh();
            } catch (err) {
                NotificationDialog.error(err.message);
            }
        });

		changeWorkingLocationBtn.addEventListener('click', async () => {
			try {
				const comment = timerCommentInput ? timerCommentInput.value.trim() : null;
				await WorkEntryApi.stopTimer(comment);
				if (timerCommentInput) timerCommentInput.value = '';
				locationSelectionCleared = true;
				workingLocations.forEach(input => {
					input.checked = false;
					input.disabled = false;
				});
				await refresh();
			} catch (err) {
				NotificationDialog.error(err.message);
			}
		});

        refresh();

        return container;
    }
}
