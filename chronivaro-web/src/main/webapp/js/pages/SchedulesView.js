import ScheduleApi from '../api/ScheduleApi.js';
import ScheduleTemplateApi from '../api/ScheduleTemplateApi.js';
import EmployeeApi from '../api/EmployeeApi.js';
import NotificationDialog from '../utils/NotificationDialog.js';
import Format from '../utils/Format.js';
import I18n from '../i18n/I18n.js';

export default class SchedulesView {
    constructor(app) {
        this.app = app;
    }

    async render(params) {
        const employeeId = params.employeeId;
        const container = document.createElement('div');
        container.id = 'schedules-view';
        container.innerHTML = `
			<div class="header-actions">
				<button id="back-btn" class="ghost">${I18n.t('schedules.backToEmployees')}</button>
				<h2 id="employee-name">${I18n.t('schedules.schedulesFor', { name: employeeId })}</h2>
			</div>
			<div class="actions">
				<button id="add-schedule-btn">${I18n.t('schedules.addSchedule')}</button>
			</div>
			<table id="schedules-table">
				<thead>
					<tr>
						<th>${I18n.t('schedules.validFrom')}</th>
						<th>${I18n.t('schedules.validTo')}</th>
						<th>${I18n.t('scheduleTemplates.mon')}</th>
						<th>${I18n.t('scheduleTemplates.tue')}</th>
						<th>${I18n.t('scheduleTemplates.wed')}</th>
						<th>${I18n.t('scheduleTemplates.thu')}</th>
						<th>${I18n.t('scheduleTemplates.fri')}</th>
						<th>${I18n.t('scheduleTemplates.sat')}</th>
						<th>${I18n.t('scheduleTemplates.sun')}</th>
						<th>${I18n.t('common.actions')}</th>
					</tr>
				</thead>
				<tbody>
					<tr><td colspan="10">${I18n.t('common.loading')}</td></tr>
				</tbody>
				<tfoot>
					<tr id="weekly-summary-row">
						<th colspan="2">${I18n.t('schedules.weeklyTotal')}</th>
						<th colspan="7" id="weekly-total">0h 0m</th>
						<th></th>
					</tr>
				</tfoot>
			</table>

			<div id="schedule-modal" class="modal">
				<div class="modal-content">
					<h3 id="modal-title">${I18n.t('schedules.addSchedule')}</h3>
					<form id="schedule-form">
						<div class="form-group">
							<label for="sched-template">${I18n.t('schedules.applyTemplate')}</label>
							<select id="sched-template">
								<option value="">${I18n.t('schedules.selectTemplatePrompt')}</option>
							</select>
						</div>
						<hr>
						<div class="form-group">
							<label for="sched-valid-from">${I18n.t('schedules.validFrom')}:</label>
							<input type="date" id="sched-valid-from" required>
						</div>
						<div class="form-group">
							<label for="sched-valid-to">${I18n.t('schedules.validTo')}:</label>
							<input type="date" id="sched-valid-to">
						</div>
						<div class="form-group">
							<label for="sched-mon">${I18n.t('scheduleTemplates.monday')}:</label>
							<input type="text" id="sched-mon" placeholder="08:00" maxlength="5" pattern="^([01]?[0-9]|2[0-3]):[0-5][0-9]$" required value="08:00">
						</div>
						<div class="form-group">
							<label for="sched-tue">${I18n.t('scheduleTemplates.tuesday')}:</label>
							<input type="text" id="sched-tue" placeholder="08:00" maxlength="5" pattern="^([01]?[0-9]|2[0-3]):[0-5][0-9]$" required value="08:00">
						</div>
						<div class="form-group">
							<label for="sched-wed">${I18n.t('scheduleTemplates.wednesday')}:</label>
							<input type="text" id="sched-wed" placeholder="08:00" maxlength="5" pattern="^([01]?[0-9]|2[0-3]):[0-5][0-9]$" required value="08:00">
						</div>
						<div class="form-group">
							<label for="sched-thu">${I18n.t('scheduleTemplates.thursday')}:</label>
							<input type="text" id="sched-thu" placeholder="08:00" maxlength="5" pattern="^([01]?[0-9]|2[0-3]):[0-5][0-9]$" required value="08:00">
						</div>
						<div class="form-group">
							<label for="sched-fri">${I18n.t('scheduleTemplates.friday')}:</label>
							<input type="text" id="sched-fri" placeholder="08:00" maxlength="5" pattern="^([01]?[0-9]|2[0-3]):[0-5][0-9]$" required value="08:00">
						</div>
						<div class="form-group">
							<label for="sched-sat">${I18n.t('scheduleTemplates.saturday')}:</label>
							<input type="text" id="sched-sat" placeholder="00:00" maxlength="5" pattern="^([01]?[0-9]|2[0-3]):[0-5][0-9]$" required value="00:00">
						</div>
						<div class="form-group">
							<label for="sched-sun">${I18n.t('scheduleTemplates.sunday')}:</label>
							<input type="text" id="sched-sun" placeholder="00:00" maxlength="5" pattern="^([01]?[0-9]|2[0-3]):[0-5][0-9]$" required value="00:00">
						</div>
						<div class="actions">
							<button type="submit">${I18n.t('common.save')}</button>
							<button type="button" id="close-modal">${I18n.t('common.cancel')}</button>
						</div>
					</form>
				</div>
			</div>
		`;

        const tbody = container.querySelector('tbody');
        const modal = container.querySelector('#schedule-modal');
        const form = container.querySelector('#schedule-form');
        const modalTitle = container.querySelector('#modal-title');
        const addBtn = container.querySelector('#add-schedule-btn');
        const closeBtn = container.querySelector('#close-modal');
        const backBtn = container.querySelector('#back-btn');
        const employeeNameHeader = container.querySelector('#employee-name');
        const templateSelect = container.querySelector('#sched-template');
        const weeklyTotal = container.querySelector('#weekly-total');

        let editingId = null;
        let templates = [];

        const formatDate = (isoDate) => {
            if (!isoDate) return '';
            return isoDate.split('T')[0];
        };

        const formatTime = (minutes) => {
            const h = Math.floor(minutes / 60);
            const m = minutes % 60;
            return `${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}`;
        };

        const parseTime = (timeStr) => {
            if (!timeStr) return 0;
            const normalized = Format.normalizeTime(timeStr);
            const parts = normalized.split(':');
            if (parts.length !== 2) return 0;
            return parseInt(parts[0], 10) * 60 + parseInt(parts[1], 10);
        };

        const schedDayInputs = ['#sched-mon', '#sched-tue', '#sched-wed', '#sched-thu', '#sched-fri', '#sched-sat', '#sched-sun']
            .map(sel => container.querySelector(sel));
        schedDayInputs.forEach(inp => {
            if (inp) {
                inp.addEventListener('blur', () => {
                    if (inp.value) inp.value = Format.normalizeTime(inp.value);
                });
            }
        });

        const updateWeeklySummary = (schedules) => {
            if (schedules.length === 0) {
                weeklyTotal.innerText = '0h 0m';
                return;
            }
            // Typically we show the summary for the active/latest schedule
            const latest = schedules.sort((a, b) => b.validFrom.localeCompare(a.validFrom))[0];
            const totalMinutes = latest.monday + latest.tuesday + latest.wednesday + latest.thursday + latest.friday + latest.saturday + latest.sunday;
            weeklyTotal.innerText = Format.duration(totalMinutes);
        };

        const refresh = async () => {
            try {
                const employee = await EmployeeApi.get(employeeId);
                employeeNameHeader.innerText = I18n.t('schedules.schedulesFor', { name: `${employee.firstname} ${employee.lastname}` });

                const schedules = await ScheduleApi.getAll(employeeId);
                tbody.innerHTML = '';
                schedules.sort((a, b) => b.validFrom.localeCompare(a.validFrom));
                schedules.forEach(sched => {
                    const row = document.createElement('tr');
                    row.innerHTML = `
						<td>${formatDate(sched.validFrom)}</td>
						<td>${formatDate(sched.validTo)}</td>
						<td>${Format.duration(sched.monday)}</td>
						<td>${Format.duration(sched.tuesday)}</td>
						<td>${Format.duration(sched.wednesday)}</td>
						<td>${Format.duration(sched.thursday)}</td>
						<td>${Format.duration(sched.friday)}</td>
						<td>${Format.duration(sched.saturday)}</td>
						<td>${Format.duration(sched.sunday)}</td>
						<td>
							<button class="ghost edit-btn" data-id="${sched.id}">${I18n.t('common.edit')}</button>
							<button class="secondary delete-btn" data-id="${sched.id}">${I18n.t('common.delete')}</button>
						</td>
					`;
                    tbody.appendChild(row);
                });

                updateWeeklySummary(schedules);

                container.querySelectorAll('.edit-btn').forEach(btn => {
                    btn.addEventListener('click', () => editSchedule(btn.dataset.id));
                });
                container.querySelectorAll('.delete-btn').forEach(btn => {
                    btn.addEventListener('click', () => deleteSchedule(btn.dataset.id));
                });

                templates = await ScheduleTemplateApi.getAll();
                templateSelect.innerHTML = `<option value="">${I18n.t('schedules.selectTemplatePrompt')}</option>`;
                templates.forEach(t => {
                    const opt = document.createElement('option');
                    opt.value = t.id;
                    opt.innerText = t.name;
                    templateSelect.appendChild(opt);
                });

            } catch (err) {
                console.error(err);
                tbody.innerHTML = `<tr><td colspan="10" class="error">${err.message}</td></tr>`;
            }
        };

        templateSelect.addEventListener('change', () => {
            const template = templates.find(t => t.id === templateSelect.value);
            if (template) {
                container.querySelector('#sched-mon').value = formatTime(template.monday);
                container.querySelector('#sched-tue').value = formatTime(template.tuesday);
                container.querySelector('#sched-wed').value = formatTime(template.wednesday);
                container.querySelector('#sched-thu').value = formatTime(template.thursday);
                container.querySelector('#sched-fri').value = formatTime(template.friday);
                container.querySelector('#sched-sat').value = formatTime(template.saturday);
                container.querySelector('#sched-sun').value = formatTime(template.sunday);
            }
        });

        const editSchedule = async (id) => {
            try {
                const schedules = await ScheduleApi.getAll(employeeId);
                const sched = schedules.find(s => s.id === id);
                if (sched) {
                    editingId = id;
                    modalTitle.innerText = I18n.t('schedules.editSchedule');
                    container.querySelector('#sched-valid-from').value = formatDate(sched.validFrom);
                    container.querySelector('#sched-valid-to').value = formatDate(sched.validTo);
                    container.querySelector('#sched-mon').value = formatTime(sched.monday);
                    container.querySelector('#sched-tue').value = formatTime(sched.tuesday);
                    container.querySelector('#sched-wed').value = formatTime(sched.wednesday);
                    container.querySelector('#sched-thu').value = formatTime(sched.thursday);
                    container.querySelector('#sched-fri').value = formatTime(sched.friday);
                    container.querySelector('#sched-sat').value = formatTime(sched.saturday);
                    container.querySelector('#sched-sun').value = formatTime(sched.sunday);
                    templateSelect.value = '';
                    modal.style.display = 'block';
                }
            } catch (err) {
                NotificationDialog.error(err.message);
            }
        };

        const deleteSchedule = async (id) => {
            if (await NotificationDialog.confirm(I18n.t('schedules.confirmDelete'))) {
                try {
                    await ScheduleApi.remove(employeeId, id);
                    refresh();
                } catch (err) {
                    NotificationDialog.error(err.message);
                }
            }
        };

        addBtn.addEventListener('click', () => {
            editingId = null;
            modalTitle.innerText = I18n.t('schedules.addSchedule');
            form.reset();
            modal.style.display = 'block';
        });

        closeBtn.addEventListener('click', () => {
            modal.style.display = 'none';
        });

        backBtn.addEventListener('click', () => {
            this.app.navigate('employees');
        });

        form.addEventListener('submit', async (e) => {
            e.preventDefault();
            const sched = {
                validFrom: container.querySelector('#sched-valid-from').value + 'T00:00:00Z',
                validTo: container.querySelector('#sched-valid-to').value ? container.querySelector('#sched-valid-to').value + 'T23:59:59Z' : null,
                monday: parseTime(container.querySelector('#sched-mon').value),
                tuesday: parseTime(container.querySelector('#sched-tue').value),
                wednesday: parseTime(container.querySelector('#sched-wed').value),
                thursday: parseTime(container.querySelector('#sched-thu').value),
                friday: parseTime(container.querySelector('#sched-fri').value),
                saturday: parseTime(container.querySelector('#sched-sat').value),
                sunday: parseTime(container.querySelector('#sched-sun').value)
            };

            try {
                if (editingId) {
                    sched.id = editingId;
                    await ScheduleApi.update(employeeId, sched);
                } else {
                    await ScheduleApi.create(employeeId, sched);
                }
                modal.style.display = 'none';
                refresh();
            } catch (err) {
                NotificationDialog.error(err.message);
            }
        });

        refresh();
        return container;
    }
}
