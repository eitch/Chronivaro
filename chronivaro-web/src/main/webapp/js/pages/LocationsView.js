import LocationApi from '../api/LocationApi.js';
import HolidayCalendarApi from '../api/HolidayCalendarApi.js';
import NotificationDialog from '../utils/NotificationDialog.js';
import I18n from '../i18n/I18n.js';

export default class LocationsView {
    constructor(app) {
        this.app = app;
    }

    async render() {
        const container = document.createElement('div');
        container.id = 'locations-view';
        container.innerHTML = `
			<h2>${I18n.t('locations.title')}</h2>
			<div class="actions">
				<button id="add-location-btn">${I18n.t('locations.addLocation')}</button>
			</div>
			<table id="locations-table">
				<thead>
					<tr>
						<th>${I18n.t('common.name')}</th>
						<th>${I18n.t('locations.timeZone')}</th>
						<th>${I18n.t('locations.holidayCalendar')}</th>
						<th>${I18n.t('common.actions')}</th>
					</tr>
				</thead>
				<tbody>
					<tr><td colspan="4">${I18n.t('common.loading')}</td></tr>
				</tbody>
			</table>

			<div id="location-modal" class="modal">
				<div class="modal-content">
					<h3 id="modal-title">${I18n.t('locations.addLocation')}</h3>
					<form id="location-form">
						<div class="form-group" id="loc-id-group">
							<label for="loc-id">${I18n.t('common.id')}:</label>
							<input type="text" id="loc-id" required>
						</div>
						<div class="form-group">
							<label for="loc-name">${I18n.t('common.name')}:</label>
							<input type="text" id="loc-name" required>
						</div>
						<div class="form-group">
							<label for="loc-timezone">${I18n.t('locations.timeZone')}:</label>
							<input type="text" id="loc-timezone" required placeholder="Europe/Zurich">
						</div>
						<div class="form-group">
							<label for="loc-holiday-calendar">${I18n.t('locations.holidayCalendar')}:</label>
							<select id="loc-holiday-calendar"></select>
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
        const modal = container.querySelector('#location-modal');
        const form = container.querySelector('#location-form');
        const modalTitle = container.querySelector('#modal-title');
        const addBtn = container.querySelector('#add-location-btn');
        const closeBtn = container.querySelector('#close-modal');
        const holidayCalendarSelect = container.querySelector('#loc-holiday-calendar');

        let editingId = null;
        let locationsList = [];

        const loadOptions = async () => {
            try {
                const calendars = await HolidayCalendarApi.getCalendars();
                if (calendars.length === 0) {
                    holidayCalendarSelect.innerHTML = `<option value="">${I18n.t('locations.noCalendarsAvailable')}</option>`;
                } else {
                    holidayCalendarSelect.innerHTML = '<option value=""></option>' +
                        calendars.map(c => `<option value="${c.id}">${c.name}</option>`).join('');
                }
            } catch (err) {
                console.error(err);
                holidayCalendarSelect.innerHTML = `<option value="">${I18n.t('locations.errorLoadingCalendars')}</option>`;
            }
        };

        const refresh = async () => {
            try {
                const locations = await LocationApi.getAll();
                locationsList = locations;
                tbody.innerHTML = '';
                locations.forEach(loc => {
                    const row = document.createElement('tr');
                    row.innerHTML = `
						<td>${loc.name}</td>
						<td>${loc.timezone}</td>
						<td>${loc.holidayCalendarName || ''}</td>
						<td>
							<button class="ghost edit-btn" data-id="${loc.id}">${I18n.t('common.edit')}</button>
							<button class="secondary delete-btn" data-id="${loc.id}">${I18n.t('common.delete')}</button>
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
                tbody.innerHTML = `<tr><td colspan="4" class="error">${err.message}</td></tr>`;
            }
        };

        const editLocation = async (id) => {
            try {
                await loadOptions();
                const loc = locationsList.find(l => l.id === id) || (await LocationApi.getAll()).find(l => l.id === id);
                if (loc) {
                    editingId = id;
                    modalTitle.innerText = I18n.t('locations.editLocation');
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
            const loc = locationsList.find(l => l.id === id);
            const name = loc ? loc.name : id;
            if (await NotificationDialog.confirm(I18n.t('locations.confirmDelete', { name, id }))) {
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
            modalTitle.innerText = I18n.t('locations.addLocation');
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
