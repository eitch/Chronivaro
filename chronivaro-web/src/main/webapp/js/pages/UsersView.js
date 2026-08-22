import UserApi from '../api/UserApi.js';
import NotificationDialog from '../utils/NotificationDialog.js';
import I18n from '../i18n/I18n.js';

export default class UsersView {
    constructor(app) {
        this.app = app;
        this.users = [];
        this.searchQuery = '';
    }

    async render() {
        const container = document.createElement('div');
        container.id = 'users-view';
        container.className = 'page-container';
        container.innerHTML = `
			<div class="page-header" style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 1.5rem;">
				<div>
					<h2 style="margin: 0 0 0.5rem 0;">${I18n.t('users.title')}</h2>
					<p class="text-muted" style="margin: 0;">${I18n.t('users.subtitle')}</p>
				</div>
				<div class="actions">
					<button id="add-user-btn" class="primary-btn">${I18n.t('users.addUser')}</button>
				</div>
			</div>

			<div class="filter-bar card" style="display: flex; gap: 1rem; align-items: center; padding: 1rem; margin-bottom: 1.5rem;">
				<div style="flex: 1; display: flex; align-items: center; gap: 0.5rem;">
					<label for="users-search" style="font-weight: 500;">${I18n.t('common.search')}:</label>
					<input type="search" id="users-search" placeholder="${I18n.t('users.searchPlaceholder')}" style="flex: 1; max-width: 400px; padding: 0.5rem; border: 1px solid var(--border-color); border-radius: 4px;">
				</div>
			</div>

			<div class="table-container card" style="padding: 1rem; overflow-x: auto;">
				<table id="users-table" class="data-table">
					<thead>
						<tr>
							<th>${I18n.t('users.username')}</th>
							<th>${I18n.t('common.name')}</th>
							<th>${I18n.t('common.email')}</th>
							<th>${I18n.t('users.roles')}</th>
							<th>${I18n.t('users.type')}</th>
							<th>${I18n.t('common.status')}</th>
							<th>${I18n.t('common.actions')}</th>
						</tr>
					</thead>
					<tbody>
						<tr><td colspan="7" class="loading-cell" style="text-align: center; padding: 2rem;">${I18n.t('common.loading')}</td></tr>
					</tbody>
				</table>
			</div>

			<!-- User Modal -->
			<div id="user-modal" class="modal">
				<div class="modal-content" style="max-width: 600px; width: 90%;">
					<div class="modal-header" style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 1.5rem; border-bottom: 1px solid var(--border-color); padding-bottom: 1rem;">
						<h3 id="modal-title" style="margin: 0;">${I18n.t('users.addUser')}</h3>
						<button type="button" id="modal-close-icon" class="close-btn" style="background: none; border: none; font-size: 1.5rem; cursor: pointer;">&times;</button>
					</div>
					<form id="user-form">
						<div class="form-grid" style="display: grid; grid-template-columns: 1fr 1fr; gap: 1rem; margin-bottom: 1rem;">
							<div class="form-group" id="user-username-group" style="grid-column: span 2;">
								<label for="user-username" style="display: block; margin-bottom: 0.25rem; font-weight: 500;">${I18n.t('users.username')} *</label>
								<input type="text" id="user-username" required style="width: 100%; padding: 0.5rem; border: 1px solid var(--border-color); border-radius: 4px;">
							</div>
							<div class="form-group">
								<label for="user-firstname" style="display: block; margin-bottom: 0.25rem; font-weight: 500;">${I18n.t('employees.firstName')} *</label>
								<input type="text" id="user-firstname" required style="width: 100%; padding: 0.5rem; border: 1px solid var(--border-color); border-radius: 4px;">
							</div>
							<div class="form-group">
								<label for="user-lastname" style="display: block; margin-bottom: 0.25rem; font-weight: 500;">${I18n.t('employees.lastName')} *</label>
								<input type="text" id="user-lastname" required style="width: 100%; padding: 0.5rem; border: 1px solid var(--border-color); border-radius: 4px;">
							</div>
							<div class="form-group" style="grid-column: span 2;">
								<label for="user-email" style="display: block; margin-bottom: 0.25rem; font-weight: 500;">${I18n.t('common.email')}</label>
								<input type="email" id="user-email" style="width: 100%; padding: 0.5rem; border: 1px solid var(--border-color); border-radius: 4px;">
							</div>
							<div class="form-group">
								<label for="user-locale" style="display: block; margin-bottom: 0.25rem; font-weight: 500;">${I18n.t('users.language')}</label>
								<select id="user-locale" style="width: 100%; padding: 0.5rem; border: 1px solid var(--border-color); border-radius: 4px;">
									<option value="de">Deutsch (de)</option>
									<option value="en">English (en)</option>
								</select>
							</div>
							<div class="form-group">
								<label for="user-state" style="display: block; margin-bottom: 0.25rem; font-weight: 500;">${I18n.t('common.status')}</label>
								<select id="user-state" style="width: 100%; padding: 0.5rem; border: 1px solid var(--border-color); border-radius: 4px;">
									<option value="ENABLED">${I18n.t('enums.state.ENABLED') || 'Enabled'}</option>
									<option value="DISABLED">${I18n.t('enums.state.DISABLED') || 'Disabled'}</option>
								</select>
							</div>
						</div>

						<div class="form-group" style="margin-bottom: 1.5rem;">
							<label style="display: block; margin-bottom: 0.5rem; font-weight: 500;">${I18n.t('users.roles')} *</label>
							<div class="roles-selection" style="display: flex; flex-wrap: wrap; gap: 1rem; padding: 0.75rem; border: 1px solid var(--border-color); border-radius: 4px; background: var(--bg-surface-secondary, #fafafa);">
								<label style="display: flex; align-items: center; gap: 0.5rem; cursor: pointer;">
									<input type="checkbox" name="user-role" value="Administrator">
									<span>${I18n.t('enums.roles.Administrator') || 'Administrator'}</span>
								</label>
								<label style="display: flex; align-items: center; gap: 0.5rem; cursor: pointer;">
									<input type="checkbox" name="user-role" value="HR">
									<span>${I18n.t('enums.roles.HR') || 'HR'}</span>
								</label>
								<label style="display: flex; align-items: center; gap: 0.5rem; cursor: pointer;">
									<input type="checkbox" name="user-role" value="Supervisor">
									<span>${I18n.t('enums.roles.Supervisor') || 'Supervisor'}</span>
								</label>
								<label style="display: flex; align-items: center; gap: 0.5rem; cursor: pointer;">
									<input type="checkbox" name="user-role" value="Employee">
									<span>${I18n.t('enums.roles.Employee') || 'Employee'}</span>
								</label>
							</div>
						</div>

						<div class="modal-actions" style="display: flex; justify-content: flex-end; gap: 0.75rem; border-top: 1px solid var(--border-color); padding-top: 1rem;">
							<button type="submit" class="primary-btn">${I18n.t('common.save')}</button>
							<button type="button" id="close-modal-btn" class="secondary-btn">${I18n.t('common.cancel')}</button>
						</div>
					</form>
				</div>
			</div>
		`;

        const tbody = container.querySelector('tbody');
        const searchInput = container.querySelector('#users-search');
        const modal = container.querySelector('#user-modal');
        const form = container.querySelector('#user-form');
        const modalTitle = container.querySelector('#modal-title');
        const addBtn = container.querySelector('#add-user-btn');
        const closeModalBtn = container.querySelector('#close-modal-btn');
        const modalCloseIcon = container.querySelector('#modal-close-icon');

        let editingUserId = null;

        const refresh = async () => {
            try {
                tbody.innerHTML = `<tr><td colspan="7" class="loading-cell" style="text-align: center; padding: 2rem;">${I18n.t('common.loading')}</td></tr>`;
                const users = await UserApi.getAll(this.searchQuery);
                this.users = Array.isArray(users) ? users : [];
                tbody.innerHTML = '';

                if (this.users.length === 0) {
                    tbody.innerHTML = `<tr><td colspan="7" class="empty-cell" style="text-align: center; padding: 2rem; color: var(--text-muted);">${I18n.t('common.noData')}</td></tr>`;
                    return;
                }

                this.users.forEach(user => {
                    const row = document.createElement('tr');
                    const fullName = [user.firstname, user.lastname].filter(Boolean).join(' ').trim() || '-';
                    const email = user.email || `<span class="text-muted">-</span>`;

                    // Format roles as badges
                    const roles = (user.roles || []).filter(r => r !== 'ModelAccessor' && r !== 'PrivilegeAdmin');
                    const rolesBadges = roles.map(r => {
                        const roleKey = `enums.roles.${r}`;
                        const roleLabel = I18n.t(roleKey) !== roleKey ? I18n.t(roleKey) : r;
                        return `<span class="badge badge-role" style="display: inline-block; padding: 0.2rem 0.5rem; font-size: 0.8rem; border-radius: 3px; background: var(--bg-hover, #e2e8f0); margin-right: 0.25rem;">${roleLabel}</span>`;
                    }).join('') || '<span class="text-muted">-</span>';

                    // User Type
                    let typeBadge = '';
                    if (user.hasLinkedEmployee) {
                        typeBadge = `<span class="badge badge-emp" style="display: inline-block; padding: 0.2rem 0.5rem; font-size: 0.8rem; border-radius: 3px; background: #ebf8ff; color: #2b6cb0;">${I18n.t('users.typeEmployee')}</span>`;
                    } else {
                        typeBadge = `<span class="badge badge-pure" style="display: inline-block; padding: 0.2rem 0.5rem; font-size: 0.8rem; border-radius: 3px; background: #fefcbf; color: #744210;">${I18n.t('users.typePureUser')}</span>`;
                    }

                    // Status
                    const isEnabled = user.state === 'ENABLED';
                    const stateBadge = isEnabled
                        ? `<span class="status-badge state-enabled" style="color: #276749; font-weight: 600;">${I18n.t('enums.state.ENABLED') || 'Enabled'}</span>`
                        : `<span class="status-badge state-disabled" style="color: #c53030; font-weight: 600;">${I18n.t('enums.state.DISABLED') || 'Disabled'}</span>`;

                    row.innerHTML = `
						<td><strong>${user.username}</strong></td>
						<td>${fullName}</td>
						<td>${email}</td>
						<td>${rolesBadges}</td>
						<td>${typeBadge}</td>
						<td>${stateBadge}</td>
						<td class="action-buttons-cell" style="white-space: nowrap;">
							<button type="button" class="secondary-btn edit-user-btn" data-id="${user.id}">${I18n.t('common.edit')}</button>
							<button type="button" class="primary-btn invite-user-btn" data-id="${user.id}" title="${I18n.t('users.sendInvitation')}">${I18n.t('users.sendInvitation')}</button>
						</td>
					`;
                    tbody.appendChild(row);
                });

                container.querySelectorAll('.edit-user-btn').forEach(btn => {
                    btn.addEventListener('click', () => editUser(btn.dataset.id));
                });
                container.querySelectorAll('.invite-user-btn').forEach(btn => {
                    btn.addEventListener('click', () => inviteUser(btn.dataset.id));
                });
            } catch (err) {
                console.error(err);
                tbody.innerHTML = `<tr><td colspan="7" class="error-cell" style="text-align: center; color: var(--error-color); padding: 2rem;">${err.message || I18n.t('app.error')}</td></tr>`;
            }
        };

        const editUser = (id) => {
            const user = this.users.find(u => u.id === id || u.username === id);
            if (!user) return;

            editingUserId = user.id || user.username;
            modalTitle.textContent = I18n.t('users.editUser');

            const usernameInput = container.querySelector('#user-username');
            usernameInput.value = user.username;
            usernameInput.disabled = true;

            container.querySelector('#user-firstname').value = user.firstname || '';
            container.querySelector('#user-lastname').value = user.lastname || '';
            container.querySelector('#user-email').value = user.email || '';
            container.querySelector('#user-locale').value = user.locale || 'de';
            container.querySelector('#user-state').value = user.state || 'ENABLED';

            const roleBoxes = container.querySelectorAll('input[name="user-role"]');
            const userRoles = user.roles || [];
            roleBoxes.forEach(box => {
                box.checked = userRoles.includes(box.value);
            });

            modal.style.display = 'flex';
        };

        const inviteUser = async (id) => {
            const user = this.users.find(u => u.id === id || u.username === id);
            const username = user ? user.username : id;

            if (await NotificationDialog.confirm(I18n.t('users.confirmSendInvitation', { username }))) {
                try {
                    await UserApi.initiateRegistration(id);
                    await NotificationDialog.info(I18n.t('users.invitationSentSuccess', { username }), I18n.t('common.success'));
                } catch (err) {
                    console.error(err);
                    await NotificationDialog.error(err.message || I18n.t('app.error'));
                }
            }
        };

        const closeModal = () => {
            modal.style.display = 'none';
            form.reset();
            editingUserId = null;
        };

        addBtn.addEventListener('click', () => {
            editingUserId = null;
            modalTitle.textContent = I18n.t('users.addUser');
            form.reset();
            const usernameInput = container.querySelector('#user-username');
            usernameInput.disabled = false;
            usernameInput.required = true;
            container.querySelector('#user-locale').value = 'de';
            container.querySelector('#user-state').value = 'ENABLED';
            modal.style.display = 'flex';
        });

        closeModalBtn.addEventListener('click', closeModal);
        modalCloseIcon.addEventListener('click', closeModal);

        searchInput.addEventListener('input', (e) => {
            this.searchQuery = e.target.value;
            refresh();
        });

        form.addEventListener('submit', async (e) => {
            e.preventDefault();

            const selectedRoles = Array.from(container.querySelectorAll('input[name="user-role"]:checked')).map(cb => cb.value);
            if (selectedRoles.length === 0) {
                await NotificationDialog.error(I18n.t('users.errorSelectAtLeastOneRole'));
                return;
            }

            const userData = {
                username: container.querySelector('#user-username').value.trim(),
                firstname: container.querySelector('#user-firstname').value.trim(),
                lastname: container.querySelector('#user-lastname').value.trim(),
                email: container.querySelector('#user-email').value.trim() || null,
                locale: container.querySelector('#user-locale').value,
                state: container.querySelector('#user-state').value,
                roles: selectedRoles
            };

            try {
                if (editingUserId) {
                    await UserApi.update(editingUserId, userData);
                    await NotificationDialog.info(I18n.t('users.userUpdatedSuccess', { username: userData.username }), I18n.t('common.success'));
                } else {
                    await UserApi.create(userData);
                    await NotificationDialog.info(I18n.t('users.userCreatedSuccess', { username: userData.username }), I18n.t('common.success'));
                }
                closeModal();
                await refresh();
            } catch (err) {
                console.error(err);
                await NotificationDialog.error(err.message || I18n.t('app.error'));
            }
        });

        await refresh();
        return container;
    }
}
