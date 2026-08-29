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
				<div id="timer-comment-container" class="form-group" style="margin-top: 10px; width: 500px">
					<label for="timer-comment">${I18n.t('common.comment')}:</label>
					<input type="text" id="timer-comment" placeholder="${I18n.t('dashboard.optionalComment')}" style="width: 100%; max-width: 500px; padding: 6px 10px; margin-top: 4px; display: block;">
				</div>
			</div>
			<div id="day-summary">
				<h3>${I18n.t('dashboard.todaySummary')}</h3>
				<p>${I18n.t('dashboard.worked')}: <span id="worked-time">...</span></p>
				<p>${I18n.t('dashboard.required')}: <span id="required-time">...</span></p>
				<p>${I18n.t('dashboard.balance')}: <span id="day-balance">...</span></p>
			</div>

			<!-- Fix Stop Timer Modal (when timer was forgotten on previous day) -->
			<div id="fix-stop-modal" class="modal" style="display: none; align-items: center; justify-content: center; position: fixed; z-index: 1000; left: 0; top: 0; width: 100%; height: 100%; background-color: rgba(0, 0, 0, 0.5);">
				<div class="modal-content" style="max-width: 500px; width: 90%; background: var(--card-bg, #ffffff); border-radius: 8px; padding: 1.5rem; box-shadow: 0 4px 6px -1px rgba(0,0,0,0.1);">
					<div class="modal-header" style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 1rem; border-bottom: 1px solid var(--border-color, #e2e8f0); padding-bottom: 0.75rem;">
						<h3 id="fix-stop-modal-title" style="margin: 0;">${I18n.t('dashboard.fixStopDialogTitle')}</h3>
						<button type="button" id="close-fix-stop-modal-icon" class="close-btn" style="background: none; border: none; font-size: 1.5rem; cursor: pointer;">&times;</button>
					</div>
					<p class="help-text" id="fix-stop-help-text" style="margin-bottom: 15px; color: var(--text-muted, #64748b); font-size: 0.9em;"></p>
					<form id="fix-stop-form">
						<div class="form-grid" style="display: grid; gap: 1rem;">
							<div class="form-group">
								<label style="display: block; margin-bottom: 0.25rem; font-weight: 500;">${I18n.t('dashboard.startDate')}:</label>
								<input type="date" id="fix-stop-start-date" readonly disabled style="width: 100%; padding: 0.5rem; border: 1px solid var(--border-color, #e2e8f0); border-radius: 4px; box-sizing: border-box; background: var(--bg-disabled, #f1f5f9);">
							</div>
							<div class="form-group">
								<label style="display: block; margin-bottom: 0.25rem; font-weight: 500;">${I18n.t('times.startTime')}:</label>
								<input type="text" id="fix-stop-start-time" readonly disabled placeholder="HH:mm" style="width: 100%; padding: 0.5rem; border: 1px solid var(--border-color, #e2e8f0); border-radius: 4px; box-sizing: border-box; background: var(--bg-disabled, #f1f5f9);">
							</div>
							<div class="form-group">
								<label for="fix-stop-end-time" style="display: block; margin-bottom: 0.25rem; font-weight: 500;">${I18n.t('times.endTime')} * (24h):</label>
								<input type="text" id="fix-stop-end-time" required placeholder="17:00" maxlength="5" pattern="^([01]?[0-9]|2[0-3]):[0-5][0-9]$" style="width: 100%; padding: 0.5rem; border: 1px solid var(--border-color, #e2e8f0); border-radius: 4px; box-sizing: border-box;">
							</div>
							<div class="form-group">
								<label for="fix-stop-comment" style="display: block; margin-bottom: 0.25rem; font-weight: 500;">${I18n.t('common.comment')}:</label>
								<input type="text" id="fix-stop-comment" placeholder="${I18n.t('dashboard.optionalComment')}" style="width: 100%; padding: 0.5rem; border: 1px solid var(--border-color, #e2e8f0); border-radius: 4px; box-sizing: border-box;">
							</div>
						</div>
						<div class="modal-actions" style="display: flex; justify-content: flex-end; gap: 0.75rem; margin-top: 1.5rem; border-top: 1px solid var(--border-color, #e2e8f0); padding-top: 1rem;">
							<button type="submit" class="primary-btn">${I18n.t('dashboard.stop')}</button>
							<button type="button" id="close-fix-stop-modal-btn" class="secondary-btn">${I18n.t('common.cancel')}</button>
						</div>
					</form>
				</div>
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
		const timerCommentInput = container.querySelector('#timer-comment');
		let locationSelectionCleared = false;
        const workedSpan = container.querySelector('#worked-time');
        const requiredSpan = container.querySelector('#required-time');
        const balanceSpan = container.querySelector('#day-balance');

		// Modal elements
		const fixStopModal = container.querySelector('#fix-stop-modal');
		const fixStopHelpText = container.querySelector('#fix-stop-help-text');
		const fixStopStartDate = container.querySelector('#fix-stop-start-date');
		const fixStopStartTime = container.querySelector('#fix-stop-start-time');
		const fixStopEndTime = container.querySelector('#fix-stop-end-time');
		const fixStopComment = container.querySelector('#fix-stop-comment');
		const fixStopForm = container.querySelector('#fix-stop-form');
		const closeFixStopModalIcon = container.querySelector('#close-fix-stop-modal-icon');
		const closeFixStopModalBtn = container.querySelector('#close-fix-stop-modal-btn');

		let currentSummary = null;

		const openFixStopModal = (activeTimer) => {
			const startStr = activeTimer.start;
			const startDateStr = startStr.substring(0, 10);
			const timeMatch = startStr.match(/T(\d{2}:\d{2})/);
			const startTimeStr = timeMatch ? timeMatch[1] : '';

			fixStopHelpText.textContent = I18n.t('dashboard.fixStopHelpText', { date: startDateStr });
			fixStopStartDate.value = startDateStr;
			fixStopStartTime.value = startTimeStr;
			fixStopEndTime.value = '';
			fixStopEndTime.min = startTimeStr;
			fixStopComment.value = timerCommentInput ? timerCommentInput.value.trim() : '';

			fixStopModal.style.display = 'flex';
			fixStopEndTime.focus();
		};

		const closeFixStopModal = () => {
			fixStopModal.style.display = 'none';
		};

		if (closeFixStopModalIcon) closeFixStopModalIcon.addEventListener('click', closeFixStopModal);
		if (closeFixStopModalBtn) closeFixStopModalBtn.addEventListener('click', closeFixStopModal);

		if (fixStopEndTime) {
			fixStopEndTime.addEventListener('blur', () => {
				if (fixStopEndTime.value) fixStopEndTime.value = Format.normalizeTime(fixStopEndTime.value);
			});
		}

		if (fixStopForm) {
			fixStopForm.addEventListener('submit', async (e) => {
				e.preventDefault();
				if (!currentSummary || !currentSummary.activeTimer) return;

				const activeTimer = currentSummary.activeTimer;
				const startStr = activeTimer.start;
				const startDateStr = startStr.substring(0, 10);
				const timeMatch = startStr.match(/T(\d{2}:\d{2})/);
				const startTimeStr = timeMatch ? timeMatch[1] : '';
				const stopTimeStr = Format.normalizeTime(fixStopEndTime.value);

				if (!Format.isValidTime(stopTimeStr)) {
					NotificationDialog.error(I18n.t('times.invalidDuration'));
					return;
				}

				if (stopTimeStr <= startTimeStr) {
					NotificationDialog.error(I18n.t('dashboard.stopTimeMustBeAfterStart'));
					return;
				}

				const offsetMatch = startStr.match(/([+-]\d{2}:\d{2}|Z)$/);
				const offset = offsetMatch ? offsetMatch[0] : 'Z';
				const stopDateTimeIso = `${startDateStr}T${stopTimeStr}:00${offset}`;
				const comment = fixStopComment.value.trim() || (timerCommentInput ? timerCommentInput.value.trim() : null);

				try {
					await WorkEntryApi.stopTimer(comment, stopDateTimeIso);
					if (timerCommentInput) timerCommentInput.value = '';
					closeFixStopModal();
					NotificationDialog.info(I18n.t('dashboard.timerStoppedSuccess'));
					await refresh();
				} catch (err) {
					NotificationDialog.error(err.message || I18n.t('app.error'));
				}
			});
		}

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
				currentSummary = summary;
				const isWorking = summary.state === 'WORKING';
				const isPreviousDayTimer = isWorking && summary.activeTimer && summary.activeTimer.isPreviousDay;

                if (isWorking) {
					if (isPreviousDayTimer) {
						const startDate = summary.activeTimer.start.substring(0, 10);
						const infoMsg = I18n.t('dashboard.timerRunningSince', {date: startDate});
						statusSpan.innerHTML = `${I18n.t('presence.working')} <span id="timer-warning-icon" class="timer-warning-icon" role="button" tabindex="0" title="${infoMsg}">!</span>`;
						statusSpan.className = 'status-danger';
						const warningIcon = statusSpan.querySelector('#timer-warning-icon');
						if (warningIcon) {
							warningIcon.addEventListener('click', (e) => {
								e.stopPropagation();
								NotificationDialog.info(infoMsg);
							});
							warningIcon.addEventListener('keydown', (e) => {
								if (e.key === 'Enter' || e.key === ' ') {
									e.preventDefault();
									e.stopPropagation();
									NotificationDialog.info(infoMsg);
								}
							});
						}
					} else {
						statusSpan.textContent = I18n.t('presence.working');
						statusSpan.className = 'status-working';
					}
					if (summary.workingLocation) {
						workingLocations.forEach(input => input.checked = input.value === summary.workingLocation);
					}
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

				startBtn.disabled = isWorking;
				stopBtn.disabled = !isWorking;
				changeWorkingLocationBtn.disabled = !isWorking || isPreviousDayTimer;
				workingLocations.forEach(input => input.disabled = isWorking);
				workingLocationGroup.classList.toggle('read-only', isWorking);
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

        stopBtn.addEventListener('click', async () => {
            try {
				if (currentSummary && currentSummary.activeTimer && currentSummary.activeTimer.isPreviousDay) {
					openFixStopModal(currentSummary.activeTimer);
					return;
				}
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
				if (currentSummary && currentSummary.activeTimer && currentSummary.activeTimer.isPreviousDay) {
					openFixStopModal(currentSummary.activeTimer);
					return;
				}
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
