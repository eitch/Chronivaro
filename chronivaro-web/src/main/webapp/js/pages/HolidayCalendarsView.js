import HolidayCalendarApi from '../api/HolidayCalendarApi.js';
import NotificationDialog from '../utils/NotificationDialog.js';

export default class HolidayCalendarsView {
    constructor(app) {
        this.app = app;
        this.selectedCalendarId = null;
    }

    async render() {
        const container = document.createElement('div');
        container.id = 'holiday-calendars-view';
        container.innerHTML = `
			<h2>Holiday Calendars</h2>
			
			<div class="calendar-layout" style="display: flex; gap: 20px;">
				<div class="calendar-list-panel" style="flex: 1; border-right: 1px solid #ccc; padding-right: 20px;">
					<div class="actions" style="margin-bottom: 10px;">
						<button id="add-calendar-btn">Add Calendar</button>
					</div>
					<table id="calendars-table" style="width: 100%;">
						<thead>
							<tr>
								<th>Name</th>
								<th>Actions</th>
							</tr>
						</thead>
						<tbody id="calendars-body">
							<!-- Loaded via JS -->
						</tbody>
					</table>
				</div>
				
				<div class="calendar-details-panel" style="flex: 2;">
					<div id="no-selection-msg">Select a calendar to see holidays</div>
					<div id="details-content" style="display: none;">
						<h3 id="selected-calendar-name"></h3>
						<div class="actions" style="margin-bottom: 10px;">
							<button id="add-holiday-btn">Add Holiday</button>
						</div>
						<table id="holidays-table" style="width: 100%;">
							<thead>
								<tr>
									<th>Date</th>
									<th>Name</th>
									<th>Credit Factor</th>
									<th>Actions</th>
								</tr>
							</thead>
							<tbody id="holidays-body">
								<!-- Loaded via JS -->
							</tbody>
						</table>
					</div>
				</div>
			</div>
			
			<div id="calendar-modal" class="modal" style="display:none; position:fixed; z-index:100; left:0; top:0; width:100%; height:100%; overflow:auto; background-color:rgba(0,0,0,0.4);">
				<div style="background-color:#fefefe; margin:15% auto; padding:20px; border:1px solid #888; width:80%; max-width:500px;">
					<h3>Add Holiday Calendar</h3>
					<form id="calendar-form">
						<div class="form-group">
							<label for="cal-name">Name:</label>
							<input type="text" id="cal-name" required style="width: 100%;">
						</div>
						<div class="actions" style="margin-top: 20px;">
							<button type="submit">Save</button>
							<button type="button" class="close-modal">Cancel</button>
						</div>
					</form>
				</div>
			</div>

			<div id="holiday-modal" class="modal" style="display:none; position:fixed; z-index:100; left:0; top:0; width:100%; height:100%; overflow:auto; background-color:rgba(0,0,0,0.4);">
				<div style="background-color:#fefefe; margin:15% auto; padding:20px; border:1px solid #888; width:80%; max-width:500px;">
					<h3>Add Holiday</h3>
					<form id="holiday-form">
						<div class="form-group">
							<label for="hol-name">Name:</label>
							<input type="text" id="hol-name" required style="width: 100%;">
						</div>
						<div class="form-group">
							<label for="hol-date">Date:</label>
							<input type="date" id="hol-date" required style="width: 100%;">
						</div>
						<div class="form-group">
							<label for="hol-credit">Credit Factor:</label>
							<input type="number" id="hol-credit" step="0.1" min="0" max="1" value="1.0" style="width: 100%;">
						</div>
						<div class="actions" style="margin-top: 20px;">
							<button type="submit">Save</button>
							<button type="button" class="close-modal">Cancel</button>
						</div>
					</form>
				</div>
			</div>
		`;

        this.calModal = container.querySelector('#calendar-modal');
        this.holModal = container.querySelector('#holiday-modal');
        this.calForm = container.querySelector('#calendar-form');
        this.holForm = container.querySelector('#holiday-form');
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

        container.querySelectorAll('.close-modal').forEach(btn => {
            btn.addEventListener('click', () => {
                this.calModal.style.display = 'none';
                this.holModal.style.display = 'none';
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
                NotificationDialog.show('Calendar created successfully');
                this.loadCalendars();
            } catch (err) {
                NotificationDialog.error(err.message);
            }
        });

        this.holForm.addEventListener('submit', async (e) => {
            e.preventDefault();
            const holiday = {
                name: container.querySelector('#hol-name').value,
                date: container.querySelector('#hol-date').value,
                creditFactor: parseFloat(container.querySelector('#hol-credit').value) || 1.0
            };
            try {
                await HolidayCalendarApi.createHoliday(this.selectedCalendarId, holiday);
                this.holModal.style.display = 'none';
                NotificationDialog.show('Holiday created successfully');
                this.loadHolidays(this.selectedCalendarId);
            } catch (err) {
                NotificationDialog.error(err.message);
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
					<td><a href="#" class="select-cal" data-id="${cal.id}">${cal.name}</a></td>
					<td><button class="delete-cal" data-id="${cal.id}">Delete</button></td>
				`;
                tr.querySelector('.select-cal').addEventListener('click', (e) => {
                    e.preventDefault();
                    this.selectCalendar(cal);
                });
                tr.querySelector('.delete-cal').addEventListener('click', async () => {
                    if (confirm(`Are you sure you want to delete calendar "${cal.name}"?`)) {
                        try {
                            await HolidayCalendarApi.deleteCalendar(cal.id);
                            NotificationDialog.show('Calendar deleted');
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

    async loadHolidays(calendarId) {
        try {
            const holidays = await HolidayCalendarApi.getHolidays(calendarId);
            this.holidaysBody.innerHTML = '';
            holidays.sort((a, b) => a.date.localeCompare(b.date)).forEach(hol => {
                const tr = document.createElement('tr');
                tr.innerHTML = `
					<td>${hol.date}</td>
					<td>${hol.name}</td>
					<td>${hol.creditFactor}</td>
					<td><button class="delete-hol" data-id="${hol.id}">Delete</button></td>
				`;
                tr.querySelector('.delete-hol').addEventListener('click', async () => {
                    if (confirm(`Are you sure you want to delete holiday "${hol.name}"?`)) {
                        try {
                            await HolidayCalendarApi.deleteHoliday(calendarId, hol.id);
                            NotificationDialog.show('Holiday deleted');
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
