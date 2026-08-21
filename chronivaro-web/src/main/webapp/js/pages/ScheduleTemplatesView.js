import ScheduleTemplateApi from '../api/ScheduleTemplateApi.js';
import NotificationDialog from '../utils/NotificationDialog.js';
import Format from '../utils/Format.js';
import I18n from '../i18n/I18n.js';

export default class ScheduleTemplatesView {
	constructor(app) {
		this.app = app;
	}

	async render() {
		const container = document.createElement('div');
		container.id = 'schedule-templates-view';
		container.innerHTML = `
			<h2>${I18n.t('scheduleTemplates.title')}</h2>
			<div class="actions">
				<button id="add-template-btn">${I18n.t('scheduleTemplates.addTemplate')}</button>
			</div>
			<table id="templates-table">
				<thead>
					<tr>
						<th>${I18n.t('common.name')}</th>
						<th>${I18n.t('scheduleTemplates.mon')}</th>
						<th>${I18n.t('scheduleTemplates.tue')}</th>
						<th>${I18n.t('scheduleTemplates.wed')}</th>
						<th>${I18n.t('scheduleTemplates.thu')}</th>
						<th>${I18n.t('scheduleTemplates.fri')}</th>
						<th>${I18n.t('scheduleTemplates.sat')}</th>
						<th>${I18n.t('scheduleTemplates.sun')}</th>
						<th>${I18n.t('common.total')}</th>
						<th>${I18n.t('common.actions')}</th>
					</tr>
				</thead>
				<tbody>
					<tr><td colspan="10">${I18n.t('common.loading')}</td></tr>
				</tbody>
			</table>

			<div id="template-modal" class="modal">
				<div class="modal-content">
					<h3 id="modal-title">${I18n.t('scheduleTemplates.addTemplate')}</h3>
					<form id="template-form">
						<div class="form-group">
							<label for="template-name">${I18n.t('common.name')}:</label>
							<input type="text" id="template-name" required>
						</div>
						<div class="form-group">
							<label for="template-mon">${I18n.t('scheduleTemplates.monday')}:</label>
							<input type="text" id="template-mon" required placeholder="HH:mm" value="08:00">
						</div>
						<div class="form-group">
							<label for="template-tue">${I18n.t('scheduleTemplates.tuesday')}:</label>
							<input type="text" id="template-tue" required placeholder="HH:mm" value="08:00">
						</div>
						<div class="form-group">
							<label for="template-wed">${I18n.t('scheduleTemplates.wednesday')}:</label>
							<input type="text" id="template-wed" required placeholder="HH:mm" value="08:00">
						</div>
						<div class="form-group">
							<label for="template-thu">${I18n.t('scheduleTemplates.thursday')}:</label>
							<input type="text" id="template-thu" required placeholder="HH:mm" value="08:00">
						</div>
						<div class="form-group">
							<label for="template-fri">${I18n.t('scheduleTemplates.friday')}:</label>
							<input type="text" id="template-fri" required placeholder="HH:mm" value="08:00">
						</div>
						<div class="form-group">
							<label for="template-sat">${I18n.t('scheduleTemplates.saturday')}:</label>
							<input type="text" id="template-sat" required placeholder="HH:mm" value="00:00">
						</div>
						<div class="form-group">
							<label for="template-sun">${I18n.t('scheduleTemplates.sunday')}:</label>
							<input type="text" id="template-sun" required placeholder="HH:mm" value="00:00">
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
		const modal = container.querySelector('#template-modal');
		const form = container.querySelector('#template-form');
		const modalTitle = container.querySelector('#modal-title');
		const addBtn = container.querySelector('#add-template-btn');
		const closeBtn = container.querySelector('#close-modal');

		let editingId = null;

		const formatTime = (minutes) => {
			const h = Math.floor(minutes / 60);
			const m = minutes % 60;
			return `${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}`;
		};

		const parseTime = (timeStr) => {
			const parts = timeStr.split(':');
			if (parts.length !== 2) return 0;
			return parseInt(parts[0]) * 60 + parseInt(parts[1]);
		};

		const refresh = async () => {
			try {
				const templates = await ScheduleTemplateApi.getAll();
				tbody.innerHTML = '';
				templates.forEach(t => {
					const totalMinutes = t.monday + t.tuesday + t.wednesday + t.thursday + t.friday + t.saturday + t.sunday;
					const row = document.createElement('tr');
					row.innerHTML = `
						<td>${t.name}</td>
						<td>${Format.duration(t.monday)}</td>
						<td>${Format.duration(t.tuesday)}</td>
						<td>${Format.duration(t.wednesday)}</td>
						<td>${Format.duration(t.thursday)}</td>
						<td>${Format.duration(t.friday)}</td>
						<td>${Format.duration(t.saturday)}</td>
						<td>${Format.duration(t.sunday)}</td>
						<td>${Format.duration(totalMinutes)}</td>
						<td>
							<button class="ghost edit-btn" data-id="${t.id}">${I18n.t('common.edit')}</button>
							<button class="secondary delete-btn" data-id="${t.id}">${I18n.t('common.delete')}</button>
						</td>
					`;
					tbody.appendChild(row);
				});

				container.querySelectorAll('.edit-btn').forEach(btn => {
					btn.addEventListener('click', () => editTemplate(btn.dataset.id));
				});
				container.querySelectorAll('.delete-btn').forEach(btn => {
					btn.addEventListener('click', () => deleteTemplate(btn.dataset.id));
				});
			} catch (err) {
				console.error(err);
				tbody.innerHTML = `<tr><td colspan="10" class="error">${err.message}</td></tr>`;
			}
		};

		const editTemplate = async (id) => {
			try {
				const templates = await ScheduleTemplateApi.getAll();
				const template = templates.find(t => t.id === id);
				if (template) {
					editingId = id;
					modalTitle.innerText = I18n.t('scheduleTemplates.editTemplate');
					container.querySelector('#template-name').value = template.name;
					container.querySelector('#template-mon').value = formatTime(template.monday);
					container.querySelector('#template-tue').value = formatTime(template.tuesday);
					container.querySelector('#template-wed').value = formatTime(template.wednesday);
					container.querySelector('#template-thu').value = formatTime(template.thursday);
					container.querySelector('#template-fri').value = formatTime(template.friday);
					container.querySelector('#template-sat').value = formatTime(template.saturday);
					container.querySelector('#template-sun').value = formatTime(template.sunday);
					modal.style.display = 'block';
				}
			} catch (err) {
				NotificationDialog.error(err.message);
			}
		};

		const deleteTemplate = async (id) => {
			if (await NotificationDialog.confirm(I18n.t('scheduleTemplates.confirmDelete'))) {
				try {
					await ScheduleTemplateApi.remove(id);
					refresh();
				} catch (err) {
					NotificationDialog.error(err.message);
				}
			}
		};

		addBtn.addEventListener('click', () => {
			editingId = null;
			modalTitle.innerText = I18n.t('scheduleTemplates.addTemplate');
			form.reset();
			modal.style.display = 'block';
		});

		closeBtn.addEventListener('click', () => {
			modal.style.display = 'none';
		});

		form.addEventListener('submit', async (e) => {
			e.preventDefault();
			const template = {
				name: container.querySelector('#template-name').value,
				monday: parseTime(container.querySelector('#template-mon').value),
				tuesday: parseTime(container.querySelector('#template-tue').value),
				wednesday: parseTime(container.querySelector('#template-wed').value),
				thursday: parseTime(container.querySelector('#template-thu').value),
				friday: parseTime(container.querySelector('#template-fri').value),
				saturday: parseTime(container.querySelector('#template-sat').value),
				sunday: parseTime(container.querySelector('#template-sun').value)
			};

			try {
				if (editingId) {
					template.id = editingId;
					await ScheduleTemplateApi.update(template);
				} else {
					await ScheduleTemplateApi.create(template);
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
