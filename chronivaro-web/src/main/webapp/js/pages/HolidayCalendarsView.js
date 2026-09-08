import HolidayCalendarApi from '../api/HolidayCalendarApi.js';
import NotificationDialog from '../utils/NotificationDialog.js';
import Format from '../utils/Format.js';
import I18n from '../i18n/I18n.js';

export default class HolidayCalendarsView {
    constructor(app) {
        this.app = app;
        this.selectedCalendarId = null;
    }

    async render() {
        const container = document.createElement('div');
        container.id = 'holiday-calendars-view';
        container.innerHTML = `
			<h2>${I18n.t('holidayCalendars.title')}</h2>
			
			<div class="calendar-layout">
				<div class="calendar-list-panel">
					<div class="actions">
						<button id="add-calendar-btn">${I18n.t('holidayCalendars.addCalendar')}</button>
					</div>
					<table id="calendars-table">
						<thead>
							<tr>
								<th>${I18n.t('common.name')}</th>
								<th>${I18n.t('common.actions')}</th>
							</tr>
						</thead>
						<tbody id="calendars-body">
							<!-- Loaded via JS -->
						</tbody>
					</table>
				</div>
				
				<div class="calendar-details-panel">
					<div id="no-selection-msg">${I18n.t('holidayCalendars.selectCalendarPrompt')}</div>
					<div id="details-content" style="display: none;">
						<h3 id="selected-calendar-name"></h3>
						<div class="actions">
							<button id="add-holiday-btn">${I18n.t('holidayCalendars.addHoliday')}</button>
							<button id="import-csv-btn" class="secondary">${I18n.t('holidayCalendars.importCsv')}</button>
						</div>
						<table id="holidays-table">
							<thead>
								<tr>
									<th>${I18n.t('holidayCalendars.holidayDate')}</th>
									<th>${I18n.t('holidayCalendars.holidayName')}</th>
									<th>${I18n.t('holidayCalendars.creditFactor')}</th>
									<th>${I18n.t('common.actions')}</th>
								</tr>
							</thead>
							<tbody id="holidays-body">
								<!-- Loaded via JS -->
							</tbody>
						</table>
					</div>
				</div>
			</div>
			
			<div id="calendar-modal" class="modal">
				<div class="modal-content">
					<h3>${I18n.t('holidayCalendars.addCalendar')}</h3>
					<form id="calendar-form">
						<div class="form-group">
							<label for="cal-name">${I18n.t('common.name')}:</label>
							<input type="text" id="cal-name" required>
						</div>
						<div class="actions">
							<button type="submit">${I18n.t('common.save')}</button>
							<button type="button" class="close-modal">${I18n.t('common.cancel')}</button>
						</div>
					</form>
				</div>
			</div>

			<div id="holiday-modal" class="modal">
				<div class="modal-content">
					<h3>${I18n.t('holidayCalendars.addHoliday')}</h3>
					<form id="holiday-form">
						<div class="form-group">
							<label for="hol-name">${I18n.t('common.name')}:</label>
							<input type="text" id="hol-name" required>
						</div>
						<div class="form-group">
							<label for="hol-date">${I18n.t('holidayCalendars.holidayDate')} * (DD.MM.YYYY):</label>
							<input type="text" id="hol-date" required placeholder="DD.MM.YYYY" maxlength="10">
						</div>
						<div class="form-group">
							<label for="hol-credit">${I18n.t('holidayCalendars.creditFactor')}:</label>
							<input type="number" id="hol-credit" step="0.1" min="0" max="1" value="1.0">
						</div>
						<div class="actions">
							<button type="submit">${I18n.t('common.save')}</button>
							<button type="button" class="close-modal">${I18n.t('common.cancel')}</button>
						</div>
					</form>
				</div>
			</div>

			<div id="import-csv-modal" class="modal">
				<div class="modal-content">
					<h3>${I18n.t('holidayCalendars.importCsv')}</h3>
					<form id="import-csv-form">
						<div class="form-group">
							<label for="csv-format-select">${I18n.t('holidayCalendars.csvFormat')}:</label>
							<select id="csv-format-select" required></select>
						</div>
						<div class="form-group">
							<label for="csv-file-input">${I18n.t('holidayCalendars.csvFile')}:</label>
							<input type="file" id="csv-file-input" accept=".csv,text/csv,text/plain" required>
						</div>
						<div class="actions">
							<button type="submit">${I18n.t('holidayCalendars.importHolidays')}</button>
							<button type="button" class="close-modal">${I18n.t('common.cancel')}</button>
						</div>
					</form>
				</div>
			</div>
		`;

        this.calModal = container.querySelector('#calendar-modal');
        this.holModal = container.querySelector('#holiday-modal');
        this.importModal = container.querySelector('#import-csv-modal');
        this.calForm = container.querySelector('#calendar-form');
        this.holForm = container.querySelector('#holiday-form');
        this.importForm = container.querySelector('#import-csv-form');
        this.csvFormatSelect = container.querySelector('#csv-format-select');
        this.csvFileInput = container.querySelector('#csv-file-input');
        this.calendarsBody = container.querySelector('#calendars-body');
        this.holidaysBody = container.querySelector('#holidays-body');
        this.detailsContent = container.querySelector('#details-content');
        this.noSelectionMsg = container.querySelector('#no-selection-msg');
        this.selectedCalName = container.querySelector('#selected-calendar-name');

        container.querySelector('#add-calendar-btn').addEventListener('click', () => {
            this.calForm.reset();
            this.calModal.style.display = 'block';
        });

        container.querySelector('#add-holiday-btn').addEventListener('click', () => {
            if (!this.selectedCalendarId) return;
            this.holForm.reset();
            this.holModal.style.display = 'block';
        });

        container.querySelector('#import-csv-btn').addEventListener('click', async () => {
            if (!this.selectedCalendarId) return;
            this.importForm.reset();
            await this.loadCsvFormats();
            this.importModal.style.display = 'block';
        });

        container.querySelectorAll('.close-modal').forEach(btn => {
            btn.addEventListener('click', () => {
                this.calModal.style.display = 'none';
                this.holModal.style.display = 'none';
                this.importModal.style.display = 'none';
            });
        });

        this.calForm.addEventListener('submit', async (e) => {
            e.preventDefault();
            const cal = {
                name: container.querySelector('#cal-name').value
            };
            try {
                await HolidayCalendarApi.createCalendar(cal);
                this.calModal.style.display = 'none';
                NotificationDialog.show(I18n.t('holidayCalendars.calendarCreated'));
                this.loadCalendars();
            } catch (err) {
                NotificationDialog.error(err.message);
            }
        });

        const holDateInput = container.querySelector('#hol-date');
        if (holDateInput) {
            holDateInput.addEventListener('blur', () => {
                if (holDateInput.value) holDateInput.value = Format.normalizeDate(holDateInput.value);
            });
        }

        this.holForm.addEventListener('submit', async (e) => {
            e.preventDefault();
            const holDateVal = container.querySelector('#hol-date').value;
            if (!Format.isValidDate(holDateVal)) {
                NotificationDialog.error(I18n.t('common.invalidDate') || 'Invalid date');
                return;
            }
            const holiday = {
                name: container.querySelector('#hol-name').value,
                date: Format.toIsoDate(holDateVal),
                creditFactor: parseFloat(container.querySelector('#hol-credit').value) || 1.0
            };
            try {
                await HolidayCalendarApi.createHoliday(this.selectedCalendarId, holiday);
                this.holModal.style.display = 'none';
                NotificationDialog.show(I18n.t('holidayCalendars.holidayCreated'));
                this.loadHolidays(this.selectedCalendarId);
            } catch (err) {
                NotificationDialog.error(err.message);
            }
        });

        this.importForm.addEventListener('submit', async (e) => {
            e.preventDefault();
            const format = this.csvFormatSelect.value;
            const file = this.csvFileInput.files[0];
            if (!file) {
                NotificationDialog.error(I18n.t('holidayCalendars.noFileSelected'));
                return;
            }

            try {
                const csvData = await file.text();
                const result = await HolidayCalendarApi.importCsv(this.selectedCalendarId, format, csvData);
                this.importModal.style.display = 'none';
                const msg = result && result.msg ? result.msg : I18n.t('holidayCalendars.importSuccess');
                NotificationDialog.show(msg);
                this.loadHolidays(this.selectedCalendarId);
            } catch (err) {
                NotificationDialog.error(err.message || I18n.t('holidayCalendars.importError'));
            }
        });

        this.loadCalendars();

        return container;
    }

    async loadCalendars() {
        try {
            const calendars = await HolidayCalendarApi.getCalendars();
            this.calendarsBody.innerHTML = '';
            calendars.forEach(cal => {
                const tr = document.createElement('tr');
                tr.innerHTML = `
					<td><button class="ghost select-cal" data-id="${cal.id}">${cal.name}</button></td>
					<td><button class="secondary delete-cal" data-id="${cal.id}">${I18n.t('common.delete')}</button></td>
				`;
                tr.querySelector('.select-cal').addEventListener('click', (e) => {
                    e.preventDefault();
                    this.selectCalendar(cal);
                });
                tr.querySelector('.delete-cal').addEventListener('click', async () => {
                    if (await NotificationDialog.confirm(I18n.t('holidayCalendars.confirmDeleteCalendar', { name: cal.name }))) {
                        try {
                            await HolidayCalendarApi.deleteCalendar(cal.id);
                            NotificationDialog.show(I18n.t('holidayCalendars.calendarDeleted'));
                            if (this.selectedCalendarId === cal.id) {
                                this.selectedCalendarId = null;
                                this.detailsContent.style.display = 'none';
                                this.noSelectionMsg.style.display = 'block';
                            }
                            this.loadCalendars();
                        } catch (err) {
                            NotificationDialog.error(err.message);
                        }
                    }
                });
                this.calendarsBody.appendChild(tr);
            });
        } catch (err) {
            NotificationDialog.error(err.message);
        }
    }

    async selectCalendar(cal) {
        this.selectedCalendarId = cal.id;
        this.selectedCalName.textContent = cal.name;
        this.noSelectionMsg.style.display = 'none';
        this.detailsContent.style.display = 'block';
        this.loadHolidays(cal.id);
    }

    async loadCsvFormats() {
        try {
            const formats = await HolidayCalendarApi.getCsvFormats();
            this.csvFormatSelect.innerHTML = '';
            formats.forEach(fmt => {
                const opt = document.createElement('option');
                opt.value = fmt.id;
                opt.textContent = fmt.name;
                this.csvFormatSelect.appendChild(opt);
            });
        } catch (err) {
            NotificationDialog.error(err.message);
        }
    }

    async loadHolidays(calendarId) {
        try {
            const holidays = await HolidayCalendarApi.getHolidays(calendarId);
            this.holidaysBody.innerHTML = '';
            holidays.sort((a, b) => a.date.localeCompare(b.date)).forEach(hol => {
                const tr = document.createElement('tr');
                tr.innerHTML = `
					<td>${Format.date(hol.date)}</td>
					<td>${hol.name}</td>
					<td>${hol.creditFactor}</td>
					<td><button class="secondary delete-hol" data-id="${hol.id}">${I18n.t('common.delete')}</button></td>
				`;
                tr.querySelector('.delete-hol').addEventListener('click', async () => {
                    if (await NotificationDialog.confirm(I18n.t('holidayCalendars.confirmDeleteHoliday', { name: hol.name }))) {
                        try {
                            await HolidayCalendarApi.deleteHoliday(calendarId, hol.id);
                            NotificationDialog.show(I18n.t('holidayCalendars.holidayDeleted'));
                            this.loadHolidays(calendarId);
                        } catch (err) {
                            NotificationDialog.error(err.message);
                        }
                    }
                });
                this.holidaysBody.appendChild(tr);
            });
        } catch (err) {
            NotificationDialog.error(err.message);
        }
    }
}
