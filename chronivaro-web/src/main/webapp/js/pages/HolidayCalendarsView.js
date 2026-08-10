import HolidayCalendarApi from '../api/HolidayCalendarApi.js';
import NotificationDialog from '../utils/NotificationDialog.js';

export default class HolidayCalendarsView {
    constructor(app) {
        this.app = app;
    }

    async render() {
        const container = document.createElement('div');
        container.id = 'holiday-calendars-view';
        container.innerHTML = `
			<h2>Holiday Calendars</h2>
			<div class="actions">
				<button id="add-calendar-btn">Add Calendar</button>
			</div>
			
			<div id="calendar-modal" style="display:none; position:fixed; z-index:1; left:0; top:0; width:100%; height:100%; overflow:auto; background-color:rgba(0,0,0,0.4);">
				<div style="background-color:#fefefe; margin:15% auto; padding:20px; border:1px solid #888; width:80%; max-width:500px;">
					<h3>Add Holiday Calendar</h3>
					<form id="calendar-form">
						<div class="form-group">
							<label for="cal-name">Name:</label>
							<input type="text" id="cal-name" required>
						</div>
						<div class="actions">
							<button type="submit">Save</button>
							<button type="button" class="close-modal">Cancel</button>
						</div>
					</form>
				</div>
			</div>

			<div id="holiday-modal" style="display:none; position:fixed; z-index:1; left:0; top:0; width:100%; height:100%; overflow:auto; background-color:rgba(0,0,0,0.4);">
				<div style="background-color:#fefefe; margin:15% auto; padding:20px; border:1px solid #888; width:80%; max-width:500px;">
					<h3>Add Holiday</h3>
					<form id="holiday-form">
						<input type="hidden" id="hol-cal-id">
						<div class="form-group">
							<label for="hol-name">Name:</label>
							<input type="text" id="hol-name" required>
						</div>
						<div class="form-group">
							<label for="hol-date">Date:</label>
							<input type="date" id="hol-date" required>
						</div>
						<div class="form-group">
							<label for="hol-credit">Credit Factor:</label>
							<input type="number" id="hol-credit" step="0.1" min="0" max="1" value="1.0">
						</div>
						<div class="actions">
							<button type="submit">Save</button>
							<button type="button" class="close-modal">Cancel</button>
						</div>
					</form>
				</div>
			</div>
			
			<p>Note: Listing and editing not yet implemented in REST API for Holiday Calendars. Use this to add new calendars and holidays.</p>
		`;

        const calModal = container.querySelector('#calendar-modal');
        const holModal = container.querySelector('#holiday-modal');
        const calForm = container.querySelector('#calendar-form');
        const holForm = container.querySelector('#holiday-form');
        const addCalBtn = container.querySelector('#add-calendar-btn');

        addCalBtn.addEventListener('click', () => {
            calForm.reset();
            calModal.style.display = 'block';
        });

        container.querySelectorAll('.close-modal').forEach(btn => {
            btn.addEventListener('click', () => {
                calModal.style.display = 'none';
                holModal.style.display = 'none';
            });
        });

        calForm.addEventListener('submit', async (e) => {
            e.preventDefault();
            const cal = {
                name: container.querySelector('#cal-name').value
            };
            try {
                const result = await HolidayCalendarApi.createCalendar(cal);
                calModal.style.display = 'none';
                
                // Set the ID for the holiday form
                container.querySelector('#hol-cal-id').value = result.value;
                holModal.style.display = 'block';

                NotificationDialog.show('Calendar created successfully. Now add holidays to it.');
            } catch (err) {
                NotificationDialog.error(err.message);
            }
        });

        holForm.addEventListener('submit', async (e) => {
            e.preventDefault();
            const calendarId = container.querySelector('#hol-cal-id').value;
            const holiday = {
                name: container.querySelector('#hol-name').value,
                date: container.querySelector('#hol-date').value,
                creditFactor: parseFloat(container.querySelector('#hol-credit').value) || 1.0
            };
            try {
                await HolidayCalendarApi.createHoliday(calendarId, holiday);
                holModal.style.display = 'none';
                NotificationDialog.show('Holiday created successfully');
            } catch (err) {
                NotificationDialog.error(err.message);
            }
        });

        return container;
    }
}
