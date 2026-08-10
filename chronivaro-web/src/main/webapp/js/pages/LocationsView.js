import LocationApi from '../api/LocationApi.js';
import HolidayCalendarApi from '../api/HolidayCalendarApi.js';
import NotificationDialog from '../utils/NotificationDialog.js';

export default class LocationsView {
    constructor(app) {
        this.app = app;
    }

    async render() {
        const container = document.createElement('div');
        container.id = 'locations-view';
        container.innerHTML = `
			<h2>Locations</h2>
			<div class="actions">
				<button id="add-location-btn">Add Location</button>
			</div>
			<table id="locations-table">
				<thead>
					<tr>
						<th>ID</th>
						<th>Name</th>
						<th>Timezone</th>
						<th>Holiday Calendar</th>
						<th>Actions</th>
					</tr>
				</thead>
				<tbody>
					<tr><td colspan="5">Loading...</td></tr>
				</tbody>
			</table>

			<div id="location-modal" class="modal">
				<div class="modal-content">
					<h3 id="modal-title">Add Location</h3>
					<form id="location-form">
						<div class="form-group" id="loc-id-group">
							<label for="loc-id">ID:</label>
							<input type="text" id="loc-id" required>
						</div>
						<div class="form-group">
							<label for="loc-name">Name:</label>
							<input type="text" id="loc-name" required>
						</div>
						<div class="form-group">
							<label for="loc-timezone">Timezone:</label>
							<input type="text" id="loc-timezone" required placeholder="Europe/Zurich">
						</div>
						<div class="form-group">
							<label for="loc-holiday-calendar">Holiday Calendar:</label>
							<select id="loc-holiday-calendar"></select>
						</div>
						<div class="actions">
							<button type="submit">Save</button>
							<button type="button" id="close-modal">Cancel</button>
						</div>
					</form>
				</div>
			</div>
		`;

        const tbody = container.querySelector('tbody');
        const modal = container.querySelector('#location-modal');
        const form = container.querySelector('#location-form');
        const modalTitle = container.querySelector('#modal-title');
        const addBtn = container.querySelector('#add-location-btn');
        const closeBtn = container.querySelector('#close-modal');
        const holidayCalendarSelect = container.querySelector('#loc-holiday-calendar');

        let editingId = null;

        const loadOptions = async () => {
            try {
                const calendars = await HolidayCalendarApi.getCalendars();
                if (calendars.length === 0) {
                    holidayCalendarSelect.innerHTML = '<option value="">No calendars available</option>';
                } else {
                    holidayCalendarSelect.innerHTML = '<option value=""></option>' +
                        calendars.map(c => `<option value="${c.id}">${c.name}</option>`).join('');
                }
            } catch (err) {
                console.error(err);
                holidayCalendarSelect.innerHTML = '<option value="">Error loading calendars</option>';
            }
        };

        const refresh = async () => {
            try {
                const locations = await LocationApi.getAll();
                tbody.innerHTML = '';
                locations.forEach(loc => {
                    const row = document.createElement('tr');
                    row.innerHTML = `
						<td>${loc.id}</td>
						<td>${loc.name}</td>
						<td>${loc.timezone}</td>
						<td>${loc.holidayCalendarId || ''}</td>
						<td>
							<button class="ghost edit-btn" data-id="${loc.id}">Edit</button>
							<button class="secondary delete-btn" data-id="${loc.id}">Delete</button>
						</td>
					`;
                    tbody.appendChild(row);
                });

                container.querySelectorAll('.edit-btn').forEach(btn => {
                    btn.addEventListener('click', () => editLocation(btn.dataset.id));
                });
                container.querySelectorAll('.delete-btn').forEach(btn => {
                    btn.addEventListener('click', () => deleteLocation(btn.dataset.id));
                });
            } catch (err) {
                console.error(err);
                tbody.innerHTML = `<tr><td colspan="5" class="error">${err.message}</td></tr>`;
            }
        };

        const editLocation = async (id) => {
            try {
                await loadOptions();
                const locations = await LocationApi.getAll();
                const loc = locations.find(l => l.id === id);
                if (loc) {
                    editingId = id;
                    modalTitle.innerText = 'Edit Location';
                    container.querySelector('#loc-id-group').style.display = 'block';
                    container.querySelector('#loc-id').value = loc.id;
                    container.querySelector('#loc-id').disabled = true;
                    container.querySelector('#loc-name').value = loc.name;
                    container.querySelector('#loc-timezone').value = loc.timezone;
                    container.querySelector('#loc-holiday-calendar').value = loc.holidayCalendarId || '';
                    modal.style.display = 'block';
                }
            } catch (err) {
                NotificationDialog.error(err.message);
            }
        };

        const deleteLocation = async (id) => {
            if (await NotificationDialog.confirm(`Are you sure you want to delete location ${id}?`)) {
                try {
                    await LocationApi.remove(id);
                    refresh();
                } catch (err) {
                    NotificationDialog.error(err.message);
                }
            }
        };

        addBtn.addEventListener('click', async () => {
            editingId = null;
            await loadOptions();
            modalTitle.innerText = 'Add Location';
            form.reset();
            container.querySelector('#loc-id-group').style.display = 'none';
            container.querySelector('#loc-id').required = false;
            container.querySelector('#loc-timezone').value = Intl.DateTimeFormat().resolvedOptions().timeZone || 'Europe/Zurich';
            modal.style.display = 'block';
        });

        closeBtn.addEventListener('click', () => {
            modal.style.display = 'none';
        });

        form.addEventListener('submit', async (e) => {
            e.preventDefault();
            const loc = {
                name: container.querySelector('#loc-name').value,
                timezone: container.querySelector('#loc-timezone').value,
                holidayCalendarId: container.querySelector('#loc-holiday-calendar').value || null
            };

            try {
                if (editingId) {
                    loc.id = editingId;
                    await LocationApi.update(loc);
                } else {
                    await LocationApi.create(loc);
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
