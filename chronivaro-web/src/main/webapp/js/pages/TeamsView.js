import TeamApi from '../api/TeamApi.js';
import NotificationDialog from '../utils/NotificationDialog.js';

export default class TeamsView {
    constructor(app) {
        this.app = app;
    }

    async render() {
        const container = document.createElement('div');
        container.id = 'teams-view';
        container.innerHTML = `
			<h2>Teams</h2>
			<div class="actions">
				<button id="add-team-btn">Add Team</button>
			</div>
			<table id="teams-table">
				<thead>
					<tr>
						<th>ID</th>
						<th>Name</th>
						<th>Actions</th>
					</tr>
				</thead>
				<tbody>
					<tr><td colspan="3">Loading...</td></tr>
				</tbody>
			</table>

			<div id="team-modal" style="display:none; position:fixed; z-index:1; left:0; top:0; width:100%; height:100%; overflow:auto; background-color:rgba(0,0,0,0.4);">
				<div style="background-color:#fefefe; margin:15% auto; padding:20px; border:1px solid #888; width:80%; max-width:500px;">
					<h3 id="modal-title">Add Team</h3>
					<form id="team-form">
						<div class="form-group" id="team-id-group">
							<label for="team-id">ID:</label>
							<input type="text" id="team-id" required>
						</div>
						<div class="form-group">
							<label for="team-name">Name:</label>
							<input type="text" id="team-name" required>
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
        const modal = container.querySelector('#team-modal');
        const form = container.querySelector('#team-form');
        const modalTitle = container.querySelector('#modal-title');
        const addBtn = container.querySelector('#add-team-btn');
        const closeBtn = container.querySelector('#close-modal');

        let editingId = null;

        const refresh = async () => {
            try {
                const teams = await TeamApi.getAll();
                tbody.innerHTML = '';
                teams.forEach(team => {
                    const row = document.createElement('tr');
                    row.innerHTML = `
						<td>${team.id}</td>
						<td>${team.name}</td>
						<td>
							<button class="edit-btn" data-id="${team.id}">Edit</button>
							<button class="delete-btn" data-id="${team.id}">Delete</button>
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
                tbody.innerHTML = `<tr><td colspan="3" class="error">${err.message}</td></tr>`;
            }
        };

        const editTeam = async (id) => {
            try {
                const teams = await TeamApi.getAll();
                const team = teams.find(t => t.id === id);
                if (team) {
                    editingId = id;
                    modalTitle.innerText = 'Edit Team';
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
            if (await NotificationDialog.confirm(`Are you sure you want to delete team ${id}?`)) {
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
            modalTitle.innerText = 'Add Team';
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
