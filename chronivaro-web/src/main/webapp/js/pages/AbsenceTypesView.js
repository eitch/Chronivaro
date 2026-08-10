import AbsenceTypeApi from '../api/AbsenceTypeApi.js';

export default class AbsenceTypesView {
    constructor(app) {
        this.app = app;
    }

    async render() {
        const container = document.createElement('div');
        container.id = 'absence-types-view';
        container.innerHTML = `
			<h2>Absence Types</h2>
			<div class="actions">
				<button id="add-absence-type-btn">Add Absence Type</button>
			</div>
			<table id="absence-types-table">
				<thead>
					<tr>
						<th>ID</th>
						<th>Code</th>
						<th>Name</th>
						<th>Paid</th>
						<th>Appr. Req.</th>
						<th>Actions</th>
					</tr>
				</thead>
				<tbody>
					<tr><td colspan="6">Loading...</td></tr>
				</tbody>
			</table>

			<div id="absence-type-modal" style="display:none; position:fixed; z-index:1; left:0; top:0; width:100%; height:100%; overflow:auto; background-color:rgba(0,0,0,0.4);">
				<div style="background-color:#fefefe; margin:5% auto; padding:20px; border:1px solid #888; width:80%; max-width:500px;">
					<h3 id="modal-title">Add Absence Type</h3>
					<form id="absence-type-form">
						<div class="form-group" id="at-id-group">
							<label for="at-id">ID:</label>
							<input type="text" id="at-id" required>
						</div>
						<div class="form-group">
							<label for="at-code">Code:</label>
							<input type="text" id="at-code" required>
						</div>
						<div class="form-group">
							<label for="at-name">Name:</label>
							<input type="text" id="at-name" required>
						</div>
						<div class="form-group">
							<label><input type="checkbox" id="at-paid"> Paid</label>
						</div>
						<div class="form-group">
							<label><input type="checkbox" id="at-approval-required"> Approval Required</label>
						</div>
						<div class="form-group">
							<label><input type="checkbox" id="at-count-as-target-time"> Count as Target Time</label>
						</div>
						<div class="form-group">
							<label><input type="checkbox" id="at-reduce-vacation-credit"> Reduce Vacation Credit</label>
						</div>
						<div class="form-group">
							<label><input type="checkbox" id="at-active" checked> Active</label>
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
        const modal = container.querySelector('#absence-type-modal');
        const form = container.querySelector('#absence-type-form');
        const modalTitle = container.querySelector('#modal-title');
        const addBtn = container.querySelector('#add-absence-type-btn');
        const closeBtn = container.querySelector('#close-modal');

        let editingId = null;

        const refresh = async () => {
            try {
                const types = await AbsenceTypeApi.getAll();
                tbody.innerHTML = '';
                types.forEach(type => {
                    const row = document.createElement('tr');
                    row.innerHTML = `
						<td>${type.id}</td>
						<td>${type.code}</td>
						<td>${type.name}</td>
						<td>${type.paid ? 'Yes' : 'No'}</td>
						<td>${type.approvalRequired ? 'Yes' : 'No'}</td>
						<td>
							<button class="edit-btn" data-id="${type.id}">Edit</button>
							<button class="delete-btn" data-id="${type.id}">Delete</button>
						</td>
					`;
                    tbody.appendChild(row);
                });

                container.querySelectorAll('.edit-btn').forEach(btn => {
                    btn.addEventListener('click', () => editAbsenceType(btn.dataset.id));
                });
                container.querySelectorAll('.delete-btn').forEach(btn => {
                    btn.addEventListener('click', () => deleteAbsenceType(btn.dataset.id));
                });
            } catch (err) {
                console.error(err);
                tbody.innerHTML = `<tr><td colspan="6" class="error">${err.message}</td></tr>`;
            }
        };

        const editAbsenceType = async (id) => {
            try {
                const types = await AbsenceTypeApi.getAll();
                const type = types.find(t => t.id === id);
                if (type) {
                    editingId = id;
                    modalTitle.innerText = 'Edit Absence Type';
                    container.querySelector('#at-id-group').style.display = 'block';
                    container.querySelector('#at-id').value = type.id;
                    container.querySelector('#at-id').disabled = true;
                    container.querySelector('#at-code').value = type.code;
                    container.querySelector('#at-name').value = type.name;
                    container.querySelector('#at-paid').checked = type.paid;
                    container.querySelector('#at-approval-required').checked = type.approvalRequired;
                    container.querySelector('#at-count-as-target-time').checked = type.countAsTargetTime;
                    container.querySelector('#at-reduce-vacation-credit').checked = type.reduceVacationCredit;
                    container.querySelector('#at-active').checked = type.active;
                    modal.style.display = 'block';
                }
            } catch (err) {
                alert(err.message);
            }
        };

        const deleteAbsenceType = async (id) => {
            if (confirm(`Are you sure you want to delete absence type ${id}?`)) {
                try {
                    await AbsenceTypeApi.remove(id);
                    refresh();
                } catch (err) {
                    alert(err.message);
                }
            }
        };

        addBtn.addEventListener('click', () => {
            editingId = null;
            modalTitle.innerText = 'Add Absence Type';
            form.reset();
            container.querySelector('#at-id-group').style.display = 'none';
            container.querySelector('#at-id').required = false;
            container.querySelector('#at-active').checked = true;
            modal.style.display = 'block';
        });

        closeBtn.addEventListener('click', () => {
            modal.style.display = 'none';
        });

        form.addEventListener('submit', async (e) => {
            e.preventDefault();
            const type = {
                code: container.querySelector('#at-code').value,
                name: container.querySelector('#at-name').value,
                paid: container.querySelector('#at-paid').checked,
                approvalRequired: container.querySelector('#at-approval-required').checked,
                countAsTargetTime: container.querySelector('#at-count-as-target-time').checked,
                reduceVacationCredit: container.querySelector('#at-reduce-vacation-credit').checked,
                active: container.querySelector('#at-active').checked,
                durationTypes: ['HOURS', 'HALF_DAY', 'FULL_DAY'] // Default for now
            };

            try {
                if (editingId) {
                    type.id = editingId;
                    await AbsenceTypeApi.update(type);
                } else {
                    await AbsenceTypeApi.create(type);
                }
                modal.style.display = 'none';
                refresh();
            } catch (err) {
                alert(err.message);
            }
        });

        refresh();
        return container;
    }
}
