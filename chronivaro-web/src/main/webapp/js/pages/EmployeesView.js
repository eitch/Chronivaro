import EmployeeApi from '../api/EmployeeApi.js';
import TeamApi from '../api/TeamApi.js';
import LocationApi from '../api/LocationApi.js';
import NotificationDialog from '../utils/NotificationDialog.js';

export default class EmployeesView {
    constructor(app) {
        this.app = app;
    }

    async render() {
        const container = document.createElement('div');
        container.id = 'employees-view';
        container.innerHTML = `
			<h2>Employees</h2>
			<div class="actions">
				<button id="add-employee-btn">Add Employee</button>
			</div>
			<table id="employees-table">
				<thead>
					<tr>
						<th>ID</th>
						<th>Pers. Nr.</th>
						<th>Name</th>
						<th>Team</th>
						<th>Location</th>
						<th>Active</th>
						<th>Actions</th>
					</tr>
				</thead>
				<tbody>
					<tr><td colspan="7">Loading...</td></tr>
				</tbody>
			</table>

			<div id="employee-modal" class="modal">
				<div class="modal-content">
					<h3 id="modal-title">Add Employee</h3>
					<form id="employee-form">
						<div class="form-group" id="emp-id-group">
							<label for="emp-id">ID:</label>
							<input type="text" id="emp-id" required>
						</div>
						<div class="form-group">
							<label for="emp-pers-nr">Personal Number:</label>
							<input type="text" id="emp-pers-nr" required>
						</div>
						<div class="form-group">
							<label for="emp-name">Display Name:</label>
							<input type="text" id="emp-name" required>
						</div>
						<div class="form-group">
							<label for="emp-team">Team:</label>
							<select id="emp-team" required></select>
						</div>
						<div class="form-group">
							<label for="emp-location">Location:</label>
							<select id="emp-location" required></select>
						</div>
						<div class="form-group">
							<label for="emp-timezone">Timezone:</label>
							<input type="text" id="emp-timezone" required placeholder="Europe/Zurich">
						</div>
						<div class="form-group">
							<label for="emp-join-date">Join Date:</label>
							<input type="date" id="emp-join-date" required>
						</div>
						<div class="form-group">
							<label for="emp-exit-date">Exit Date:</label>
							<input type="date" id="emp-exit-date">
						</div>
						<div class="form-group">
							<label for="emp-user">User ID:</label>
							<input type="text" id="emp-user" required>
						</div>
						<div class="form-group">
							<label><input type="checkbox" id="emp-active" checked> Active</label>
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
        const modal = container.querySelector('#employee-modal');
        const form = container.querySelector('#employee-form');
        const modalTitle = container.querySelector('#modal-title');
        const addBtn = container.querySelector('#add-employee-btn');
        const closeBtn = container.querySelector('#close-modal');
        const teamSelect = container.querySelector('#emp-team');
        const locationSelect = container.querySelector('#emp-location');

        let editingId = null;

        const loadOptions = async () => {
            const [teams, locations] = await Promise.all([
                TeamApi.getAll(),
                LocationApi.getAll()
            ]);

            if (teams.length === 0) {
                teamSelect.innerHTML = '<option value="">No teams available</option>';
            } else {
                teamSelect.innerHTML = teams.map(t => `<option value="${t.id}">${t.name}</option>`).join('');
            }

            if (locations.length === 0) {
                locationSelect.innerHTML = '<option value="">No locations available</option>';
            } else {
                locationSelect.innerHTML = locations.map(l => `<option value="${l.id}">${l.name}</option>`).join('');
            }
        };

        const refresh = async () => {
            try {
                const employees = await EmployeeApi.getAll();
                tbody.innerHTML = '';
                employees.forEach(emp => {
                    const row = document.createElement('tr');
                    row.innerHTML = `
						<td>${emp.id}</td>
						<td>${emp.personalNumber}</td>
						<td>${emp.displayName}</td>
						<td>${emp.teamId}</td>
						<td>${emp.locationId}</td>
						<td>${emp.active ? 'Yes' : 'No'}</td>
						<td>
							<button class="ghost edit-btn" data-id="${emp.id}">Edit</button>
							<button class="secondary delete-btn" data-id="${emp.id}">Delete</button>
						</td>
					`;
                    tbody.appendChild(row);
                });

                container.querySelectorAll('.edit-btn').forEach(btn => {
                    btn.addEventListener('click', () => editEmployee(btn.dataset.id));
                });
                container.querySelectorAll('.delete-btn').forEach(btn => {
                    btn.addEventListener('click', () => deleteEmployee(btn.dataset.id));
                });
            } catch (err) {
                console.error(err);
                tbody.innerHTML = `<tr><td colspan="7" class="error">${err.message}</td></tr>`;
            }
        };

        const editEmployee = async (id) => {
            try {
                await loadOptions();
                const employees = await EmployeeApi.getAll();
                const emp = employees.find(e => e.id === id);
                if (emp) {
                    editingId = id;
                    modalTitle.innerText = 'Edit Employee';
                    container.querySelector('#emp-id-group').style.display = 'block';
                    container.querySelector('#emp-id').value = emp.id;
                    container.querySelector('#emp-id').disabled = true;
                    container.querySelector('#emp-pers-nr').value = emp.personalNumber;
                    container.querySelector('#emp-name').value = emp.displayName;
                    container.querySelector('#emp-team').value = emp.teamId;
                    container.querySelector('#emp-location').value = emp.locationId;
                    container.querySelector('#emp-timezone').value = emp.timezone;
                    container.querySelector('#emp-join-date').value = emp.joinDate;
                    container.querySelector('#emp-exit-date').value = emp.exitDate || '';
                    container.querySelector('#emp-user').value = emp.userId;
                    container.querySelector('#emp-active').checked = emp.active;
                    modal.style.display = 'block';
                }
            } catch (err) {
                NotificationDialog.error(err.message);
            }
        };

        const deleteEmployee = async (id) => {
            if (await NotificationDialog.confirm(`Are you sure you want to delete employee ${id}?`)) {
                try {
                    await EmployeeApi.remove(id);
                    refresh();
                } catch (err) {
                    NotificationDialog.error(err.message);
                }
            }
        };

        addBtn.addEventListener('click', async () => {
            await loadOptions();
            editingId = null;
            modalTitle.innerText = 'Add Employee';
            form.reset();
            container.querySelector('#emp-id-group').style.display = 'none';
            container.querySelector('#emp-id').required = false;
            container.querySelector('#emp-active').checked = true;
            modal.style.display = 'block';
        });

        closeBtn.addEventListener('click', () => {
            modal.style.display = 'none';
        });

        form.addEventListener('submit', async (e) => {
            e.preventDefault();
            const emp = {
                personalNumber: container.querySelector('#emp-pers-nr').value,
                displayName: container.querySelector('#emp-name').value,
                teamId: container.querySelector('#emp-team').value,
                locationId: container.querySelector('#emp-location').value,
                timezone: container.querySelector('#emp-timezone').value,
                joinDate: container.querySelector('#emp-join-date').value,
                exitDate: container.querySelector('#emp-exit-date').value || null,
                userId: container.querySelector('#emp-user').value,
                active: container.querySelector('#emp-active').checked
            };

            try {
                if (editingId) {
                    emp.id = editingId;
                    await EmployeeApi.update(emp);
                } else {
                    await EmployeeApi.create(emp);
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
