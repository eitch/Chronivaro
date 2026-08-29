import TeamApi from '../api/TeamApi.js';
import NotificationDialog from '../utils/NotificationDialog.js';
import I18n from '../i18n/I18n.js';

export default class TeamsView {
    constructor(app) {
        this.app = app;
    }

    async render() {
        const container = document.createElement('div');
        container.id = 'teams-view';
        container.innerHTML = `
			<h2>${I18n.t('teams.title')}</h2>
			<div class="actions">
				<button id="add-team-btn">${I18n.t('teams.addTeam')}</button>
			</div>
			<table id="teams-table">
				<thead>
					<tr>
						<th>${I18n.t('common.name')}</th>
						<th>${I18n.t('common.actions')}</th>
					</tr>
				</thead>
				<tbody>
					<tr><td colspan="2">${I18n.t('common.loading')}</td></tr>
				</tbody>
			</table>

			<div id="team-modal" class="modal">
				<div class="modal-content">
					<h3 id="modal-title">${I18n.t('teams.addTeam')}</h3>
					<form id="team-form">
						<div class="form-group" id="team-id-group">
							<label for="team-id">${I18n.t('common.id')}:</label>
							<input type="text" id="team-id" required>
						</div>
						<div class="form-group">
							<label for="team-name">${I18n.t('common.name')}:</label>
							<input type="text" id="team-name" required>
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
        const modal = container.querySelector('#team-modal');
        const form = container.querySelector('#team-form');
        const modalTitle = container.querySelector('#modal-title');
        const addBtn = container.querySelector('#add-team-btn');
        const closeBtn = container.querySelector('#close-modal');

        let editingId = null;
        let teamsList = [];

        const refresh = async () => {
            try {
                const teams = await TeamApi.getAll();
                teamsList = teams;
                tbody.innerHTML = '';
                teams.forEach(team => {
                    const row = document.createElement('tr');
                    row.innerHTML = `
						<td>${team.name}</td>
						<td>
							<button class="ghost edit-btn" data-id="${team.id}">${I18n.t('common.edit')}</button>
							<button class="secondary delete-btn" data-id="${team.id}">${I18n.t('common.delete')}</button>
						</td>
					`;
                    tbody.appendChild(row);
                });

                container.querySelectorAll('.edit-btn').forEach(btn => {
                    btn.addEventListener('click', () => editTeam(btn.dataset.id));
                });
                container.querySelectorAll('.delete-btn').forEach(btn => {
                    btn.addEventListener('click', () => deleteTeam(btn.dataset.id));
                });
            } catch (err) {
                console.error(err);
                tbody.innerHTML = `<tr><td colspan="2" class="error">${err.message}</td></tr>`;
            }
        };

        const editTeam = async (id) => {
            try {
                const team = teamsList.find(t => t.id === id) || (await TeamApi.getAll()).find(t => t.id === id);
                if (team) {
                    editingId = id;
                    modalTitle.innerText = I18n.t('teams.editTeam');
                    container.querySelector('#team-id-group').style.display = 'block';
                    container.querySelector('#team-id').value = team.id;
                    container.querySelector('#team-id').disabled = true;
                    container.querySelector('#team-name').value = team.name;
                    modal.style.display = 'block';
                }
            } catch (err) {
                NotificationDialog.error(err.message);
            }
        };

        const deleteTeam = async (id) => {
            const team = teamsList.find(t => t.id === id);
            const name = team ? team.name : id;
            if (await NotificationDialog.confirm(I18n.t('teams.confirmDelete', { name, id }))) {
                try {
                    await TeamApi.remove(id);
                    refresh();
                } catch (err) {
                    NotificationDialog.error(err.message);
                }
            }
        };

        addBtn.addEventListener('click', () => {
            editingId = null;
            modalTitle.innerText = I18n.t('teams.addTeam');
            form.reset();
            container.querySelector('#team-id-group').style.display = 'none';
            container.querySelector('#team-id').required = false;
            modal.style.display = 'block';
        });

        closeBtn.addEventListener('click', () => {
            modal.style.display = 'none';
        });

        form.addEventListener('submit', async (e) => {
            e.preventDefault();
            const team = {
                name: container.querySelector('#team-name').value
            };

            try {
                if (editingId) {
                    team.id = editingId;
                    await TeamApi.update(team);
                } else {
                    await TeamApi.create(team);
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
