import HolidayCalendarApi from '../api/HolidayCalendarApi.js';

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
							<label for="cal-id">ID:</label>
							<input type="text" id="cal-id" required>
						</div>
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
							<label for="hol-id">ID:</label>
							<input type="text" id="hol-id" required>
						</div>
						<div class="form-group">
							<label for="hol-name">Name:</label>
							<input type="text" id="hol-name" required>
						</div>
						<div class="form-group">
							<label for="hol-date">Date:</label>
							<input type="date" id="hol-date" required>
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
				id: container.querySelector('#cal-id').value,
				name: container.querySelector('#cal-name').value
			};
			try {
				await HolidayCalendarApi.createCalendar(cal);
				calModal.style.display = 'none';
				alert('Calendar created successfully');
			} catch (err) {
				alert(err.message);
			}
		});

		holForm.addEventListener('submit', async (e) => {
			e.preventDefault();
			const calendarId = container.querySelector('#hol-cal-id').value;
			const holiday = {
				id: container.querySelector('#hol-id').value,
				name: container.querySelector('#hol-name').value,
				date: container.querySelector('#hol-date').value
			};
			try {
				await HolidayCalendarApi.createHoliday(calendarId, holiday);
				holModal.style.display = 'none';
				alert('Holiday created successfully');
			} catch (err) {
				alert(err.message);
			}
		});

		return container;
	}
}
