import AuditLogApi from '../api/AuditLogApi.js';
import NotificationDialog from '../utils/NotificationDialog.js';
import Format from '../utils/Format.js';
import I18n from '../i18n/I18n.js';

export default class AuditLogView {
    constructor(app) {
        this.app = app;
        this.logs = [];
        this.filters = {
            from: '',
            to: '',
            entityType: '',
            entityId: '',
            username: '',
            action: '',
            offset: 0,
            limit: 25
        };
        this.total = 0;
        this.currentInspectedLog = null;
    }

    async render() {
        const container = document.createElement('div');
        container.id = 'audit-log-view';
        container.className = 'page-container';
        container.innerHTML = `
			<div class="page-header" style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 1.5rem;">
				<div>
					<h2 style="margin: 0 0 0.5rem 0;">${I18n.t('auditLog.title')}</h2>
					<p class="text-muted" style="margin: 0;">${I18n.t('auditLog.subtitle')}</p>
				</div>
			</div>

			<!-- Filter Bar -->
			<div class="filter-bar card" style="display: flex; flex-wrap: wrap; gap: 1rem; align-items: flex-end; padding: 1rem; margin-bottom: 1.5rem;">
				<div class="filter-group" style="display: flex; flex-direction: column; gap: 0.25rem;">
					<label for="audit-from" style="font-weight: 500; font-size: 0.875rem;">${I18n.t('common.from')}:</label>
					<input type="text" id="audit-from" value="${Format.date(this.filters.from)}" placeholder="DD.MM.YYYY" maxlength="10" style="padding: 0.5rem; border: 1px solid var(--border-color); border-radius: 4px;">
				</div>
				<div class="filter-group" style="display: flex; flex-direction: column; gap: 0.25rem;">
					<label for="audit-to" style="font-weight: 500; font-size: 0.875rem;">${I18n.t('common.to')}:</label>
					<input type="text" id="audit-to" value="${Format.date(this.filters.to)}" placeholder="DD.MM.YYYY" maxlength="10" style="padding: 0.5rem; border: 1px solid var(--border-color); border-radius: 4px;">
				</div>
				<div class="filter-group" style="display: flex; flex-direction: column; gap: 0.25rem;">
					<label for="audit-entity-type" style="font-weight: 500; font-size: 0.875rem;">${I18n.t('auditLog.entityType')}:</label>
					<select id="audit-entity-type" style="padding: 0.5rem; border: 1px solid var(--border-color); border-radius: 4px; min-width: 140px;">
						<option value="">${I18n.t('common.all')}</option>
						<option value="Employee">Employee</option>
						<option value="Team">Team</option>
						<option value="Location">Location</option>
						<option value="Absence">Absence</option>
						<option value="AbsenceType">AbsenceType</option>
						<option value="WorkEntry">WorkEntry</option>
						<option value="TimePeriod">TimePeriod</option>
						<option value="VacationAccountEntry">VacationAccountEntry</option>
						<option value="EmploymentSchedule">EmploymentSchedule</option>
						<option value="EmploymentScheduleTemplate">EmploymentScheduleTemplate</option>
						<option value="HolidayCalendar">HolidayCalendar</option>
						<option value="Configuration">Configuration</option>
						<option value="ChronivaroAuditEvent">ChronivaroAuditEvent</option>
					</select>
				</div>
				<div class="filter-group" style="display: flex; flex-direction: column; gap: 0.25rem;">
					<label for="audit-entity-id" style="font-weight: 500; font-size: 0.875rem;">${I18n.t('auditLog.entityId')}:</label>
					<input type="text" id="audit-entity-id" placeholder="${I18n.t('auditLog.entityIdPlaceholder')}" value="${this.filters.entityId}" style="padding: 0.5rem; border: 1px solid var(--border-color); border-radius: 4px; max-width: 150px;">
				</div>
				<div class="filter-group" style="display: flex; flex-direction: column; gap: 0.25rem;">
					<label for="audit-username" style="font-weight: 500; font-size: 0.875rem;">${I18n.t('auditLog.user')}:</label>
					<input type="text" id="audit-username" placeholder="${I18n.t('auditLog.userPlaceholder')}" value="${this.filters.username}" style="padding: 0.5rem; border: 1px solid var(--border-color); border-radius: 4px; max-width: 130px;">
				</div>
				<div class="filter-group" style="display: flex; flex-direction: column; gap: 0.25rem;">
					<label for="audit-action" style="font-weight: 500; font-size: 0.875rem;">${I18n.t('auditLog.action')}:</label>
					<select id="audit-action" style="padding: 0.5rem; border: 1px solid var(--border-color); border-radius: 4px; min-width: 130px;">
						<option value="">${I18n.t('common.all')}</option>
						<option value="CREATE">CREATE</option>
						<option value="UPDATE">UPDATE</option>
						<option value="DELETE">DELETE</option>
						<option value="REMOVE">REMOVE</option>
						<option value="SUBMIT">SUBMIT</option>
						<option value="APPROVE">APPROVE</option>
						<option value="REJECT">REJECT</option>
						<option value="CANCEL">CANCEL</option>
						<option value="CLOSE">CLOSE</option>
						<option value="REOPEN">REOPEN</option>
						<option value="LOCK">LOCK</option>
						<option value="UNLOCK">UNLOCK</option>
						<option value="CORRECT">CORRECT</option>
						<option value="CARRY_OVER">CARRY_OVER</option>
						<option value="PURGE">PURGE</option>
					</select>
				</div>
				<div class="filter-actions" style="display: flex; gap: 0.5rem; margin-left: auto;">
					<button id="audit-filter-apply-btn" class="primary-btn">${I18n.t('common.filter')}</button>
					<button id="audit-filter-reset-btn" class="secondary-btn">${I18n.t('common.reset')}</button>
				</div>
			</div>

			<!-- Table Container -->
			<div class="table-container card" style="padding: 1rem; overflow-x: auto; margin-bottom: 1.5rem;">
				<table id="audit-table" class="data-table">
					<thead>
						<tr>
							<th style="min-width: 150px;">${I18n.t('auditLog.timestamp')}</th>
							<th>${I18n.t('auditLog.user')}</th>
							<th>${I18n.t('auditLog.action')}</th>
							<th>${I18n.t('auditLog.entityType')}</th>
							<th>${I18n.t('auditLog.entityId')}</th>
							<th style="min-width: 250px;">${I18n.t('auditLog.summary')}</th>
							<th style="text-align: right; min-width: 90px;">${I18n.t('common.actions')}</th>
						</tr>
					</thead>
					<tbody id="audit-tbody">
						<tr><td colspan="7" class="loading-cell" style="text-align: center; padding: 2rem;">${I18n.t('common.loading')}</td></tr>
					</tbody>
				</table>

				<!-- Pagination Bar -->
				<div class="pagination-bar" style="display: flex; justify-content: space-between; align-items: center; margin-top: 1rem; padding-top: 1rem; border-top: 1px solid var(--border-color);">
					<div id="audit-total-info" class="text-muted" style="font-size: 0.875rem;"></div>
					<div class="pagination-controls" style="display: flex; gap: 0.5rem; align-items: center;">
						<button id="audit-prev-btn" class="secondary-btn" disabled>&laquo; ${I18n.t('common.prev')}</button>
						<span id="audit-page-info" style="font-weight: 500; font-size: 0.875rem; padding: 0 0.5rem;">-</span>
						<button id="audit-next-btn" class="secondary-btn" disabled>${I18n.t('common.next')} &raquo;</button>
					</div>
				</div>
			</div>

			<!-- Detail Modal -->
			<div id="audit-detail-modal" class="modal" style="display: none;">
				<div class="modal-content wide" style="max-width: 700px; width: 90%;">
					<div class="modal-header" style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 1.5rem; border-bottom: 1px solid var(--border-color); padding-bottom: 1rem;">
						<h3 style="margin: 0;">${I18n.t('auditLog.detailTitle')}</h3>
						<button type="button" id="audit-modal-close-icon" class="close-btn" style="background: none; border: none; font-size: 1.5rem; cursor: pointer;" aria-label="Close">&times;</button>
					</div>
					<div id="audit-detail-body" style="display: flex; flex-direction: column; gap: 1rem; max-height: 70vh; overflow-y: auto;">
						<!-- Populated dynamically -->
					</div>
					<div class="modal-actions" style="margin-top: 1.5rem; border-top: 1px solid var(--border-color); padding-top: 1rem; display: flex; justify-content: flex-end;">
						<button type="button" id="audit-modal-close-btn" class="secondary-btn">${I18n.t('common.close')}</button>
					</div>
				</div>
			</div>
		`;

        this.bindEvents(container);
        await this.loadAuditLogs(container);
        return container;
    }

    bindEvents(container) {
        const fromInput = container.querySelector('#audit-from');
        const toInput = container.querySelector('#audit-to');
        const entityTypeSelect = container.querySelector('#audit-entity-type');
        const entityIdInput = container.querySelector('#audit-entity-id');
        const usernameInput = container.querySelector('#audit-username');
        const actionSelect = container.querySelector('#audit-action');
        const applyBtn = container.querySelector('#audit-filter-apply-btn');
        const resetBtn = container.querySelector('#audit-filter-reset-btn');
        const prevBtn = container.querySelector('#audit-prev-btn');
        const nextBtn = container.querySelector('#audit-next-btn');

        [fromInput, toInput].forEach(inp => {
            if (inp) {
                inp.addEventListener('blur', () => {
                    if (inp.value) inp.value = Format.normalizeDate(inp.value);
                });
            }
        });

        applyBtn.addEventListener('click', () => {
            this.filters.from = Format.toIsoDate(fromInput.value);
            this.filters.to = Format.toIsoDate(toInput.value);
            this.filters.entityType = entityTypeSelect.value;
            this.filters.entityId = entityIdInput.value.trim();
            this.filters.username = usernameInput.value.trim();
            this.filters.action = actionSelect.value;
            this.filters.offset = 0;
            this.loadAuditLogs(container);
        });

        resetBtn.addEventListener('click', () => {
            fromInput.value = '';
            toInput.value = '';
            entityTypeSelect.value = '';
            entityIdInput.value = '';
            usernameInput.value = '';
            actionSelect.value = '';
            this.filters = {
                from: '',
                to: '',
                entityType: '',
                entityId: '',
                username: '',
                action: '',
                offset: 0,
                limit: 25
            };
            this.loadAuditLogs(container);
        });

        prevBtn.addEventListener('click', () => {
            if (this.filters.offset >= this.filters.limit) {
                this.filters.offset -= this.filters.limit;
                this.loadAuditLogs(container);
            }
        });

        nextBtn.addEventListener('click', () => {
            this.filters.offset += this.filters.limit;
            this.loadAuditLogs(container);
        });

        // Modal close handlers
        const modal = container.querySelector('#audit-detail-modal');
        const closeIcon = container.querySelector('#audit-modal-close-icon');
        const closeBtn = container.querySelector('#audit-modal-close-btn');

        const closeModal = () => {
            modal.style.display = 'none';
            this.currentInspectedLog = null;
        };

        if (closeIcon) closeIcon.addEventListener('click', closeModal);
        if (closeBtn) closeBtn.addEventListener('click', closeModal);

        modal.addEventListener('click', (e) => {
            if (e.target === modal) {
                closeModal();
            }
        });
    }

    async loadAuditLogs(container) {
        const tbody = container.querySelector('#audit-tbody');
        const prevBtn = container.querySelector('#audit-prev-btn');
        const nextBtn = container.querySelector('#audit-next-btn');
        const pageInfo = container.querySelector('#audit-page-info');
        const totalInfo = container.querySelector('#audit-total-info');

        tbody.innerHTML = `<tr><td colspan="7" class="loading-cell" style="text-align: center; padding: 2rem;">${I18n.t('common.loading')}</td></tr>`;

        try {
            const response = await AuditLogApi.getAuditLogs({
                offset: this.filters.offset,
                limit: this.filters.limit,
                entityType: this.filters.entityType,
                entityId: this.filters.entityId,
                username: this.filters.username,
                action: this.filters.action,
                from: this.filters.from,
                to: this.filters.to
            });

            if (response && response.data) {
                this.logs = response.data;
                this.total = response.total !== undefined ? response.total : response.data.length;
            } else if (Array.isArray(response)) {
                this.logs = response;
                this.total = response.length;
            } else {
                this.logs = [];
                this.total = 0;
            }

            tbody.innerHTML = '';

            if (this.logs.length === 0) {
                tbody.innerHTML = `<tr><td colspan="7" class="empty-cell" style="text-align: center; padding: 2rem; color: var(--text-muted);">${I18n.t('auditLog.noEntriesFound')}</td></tr>`;
                prevBtn.disabled = true;
                nextBtn.disabled = true;
                pageInfo.textContent = '-';
                totalInfo.textContent = I18n.t('auditLog.totalRecords', { count: 0 });
                return;
            }

            this.logs.forEach(log => {
                const tr = document.createElement('tr');
                tr.style.cursor = 'pointer';

                const formattedTime = log.timestamp ? Format.dateTime(log.timestamp) : '-';
                const actionBadgeClass = this.getActionBadgeClass(log.action);
                const summary = log.details || log.reason || (log.paramName ? `${log.paramName}: ${log.oldValue || ''} -> ${log.newValue || ''}` : '-');

                tr.innerHTML = `
					<td style="white-space: nowrap; font-size: 0.875rem;">${formattedTime}</td>
					<td><strong>${this.escapeHtml(log.username || '-')}</strong></td>
					<td><span class="badge ${actionBadgeClass}" style="padding: 0.2rem 0.5rem; border-radius: 4px; font-size: 0.75rem; font-weight: 600;">${this.escapeHtml(log.action || '-')}</span></td>
					<td><code>${this.escapeHtml(log.entityType || '-')}</code></td>
					<td><code>${this.escapeHtml(log.entityId || '-')}</code></td>
					<td style="max-width: 300px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;" title="${this.escapeHtml(summary)}">${this.escapeHtml(summary)}</td>
					<td style="text-align: right;">
						<button type="button" class="secondary-btn view-detail-btn" style="padding: 0.25rem 0.5rem; font-size: 0.8rem;">${I18n.t('common.details')}</button>
					</td>
				`;

                tr.querySelector('.view-detail-btn').addEventListener('click', (e) => {
                    e.stopPropagation();
                    this.showDetailModal(container, log);
                });

                tr.addEventListener('click', () => {
                    this.showDetailModal(container, log);
                });

                tbody.appendChild(tr);
            });

            // Update pagination
            const currentPage = Math.floor(this.filters.offset / this.filters.limit) + 1;
            const totalPages = Math.max(1, Math.ceil(this.total / this.filters.limit));
            pageInfo.textContent = `${currentPage} / ${totalPages}`;
            totalInfo.textContent = I18n.t('auditLog.totalRecords', { count: this.total });

            prevBtn.disabled = this.filters.offset <= 0;
            nextBtn.disabled = this.filters.offset + this.filters.limit >= this.total;

        } catch (err) {
            console.error('Failed to load audit logs', err);
            tbody.innerHTML = `<tr><td colspan="7" class="error-cell" style="text-align: center; padding: 2rem; color: var(--danger-color, #e53935);">${I18n.t('auditLog.errorLoading')}</td></tr>`;
            prevBtn.disabled = true;
            nextBtn.disabled = true;
            pageInfo.textContent = '-';
            totalInfo.textContent = '';
        }
    }

    showDetailModal(container, log) {
        this.currentInspectedLog = log;
        const modal = container.querySelector('#audit-detail-modal');
        const body = container.querySelector('#audit-detail-body');

        const formattedTime = log.timestamp ? Format.dateTime(log.timestamp) : '-';
        const actionBadgeClass = this.getActionBadgeClass(log.action);

        body.innerHTML = `
			<div class="detail-grid" style="display: grid; grid-template-columns: 140px 1fr; gap: 0.75rem 1rem; font-size: 0.9rem;">
				<div style="font-weight: 600; color: var(--text-muted);">${I18n.t('auditLog.logId')}:</div>
				<div><code>${this.escapeHtml(log.id || '-')}</code></div>

				<div style="font-weight: 600; color: var(--text-muted);">${I18n.t('auditLog.timestamp')}:</div>
				<div>${formattedTime}</div>

				<div style="font-weight: 600; color: var(--text-muted);">${I18n.t('auditLog.user')}:</div>
				<div><strong>${this.escapeHtml(log.username || '-')}</strong></div>

				<div style="font-weight: 600; color: var(--text-muted);">${I18n.t('auditLog.action')}:</div>
				<div><span class="badge ${actionBadgeClass}" style="padding: 0.2rem 0.5rem; border-radius: 4px; font-size: 0.75rem; font-weight: 600;">${this.escapeHtml(log.action || '-')}</span></div>

				<div style="font-weight: 600; color: var(--text-muted);">${I18n.t('auditLog.entityType')}:</div>
				<div><code>${this.escapeHtml(log.entityType || '-')}</code></div>

				<div style="font-weight: 600; color: var(--text-muted);">${I18n.t('auditLog.entityId')}:</div>
				<div><code>${this.escapeHtml(log.entityId || '-')}</code></div>

				<div style="font-weight: 600; color: var(--text-muted);">${I18n.t('auditLog.correlationId')}:</div>
				<div><code>${this.escapeHtml(log.correlationId || '-')}</code></div>

				<div style="font-weight: 600; color: var(--text-muted);">${I18n.t('auditLog.reason')}:</div>
				<div>${this.escapeHtml(log.reason || '-')}</div>

				<div style="font-weight: 600; color: var(--text-muted);">${I18n.t('auditLog.paramName')}:</div>
				<div><code>${this.escapeHtml(log.paramName || '-')}</code></div>

				<div style="font-weight: 600; color: var(--text-muted);">${I18n.t('auditLog.oldValue')}:</div>
				<div style="background: var(--bg-surface-secondary, #f8fafc); padding: 0.5rem; border-radius: 4px; border: 1px solid var(--border-color); font-family: monospace; white-space: pre-wrap; word-break: break-all; max-height: 120px; overflow-y: auto;">${this.escapeHtml(log.oldValue || '-')}</div>

				<div style="font-weight: 600; color: var(--text-muted);">${I18n.t('auditLog.newValue')}:</div>
				<div style="background: var(--bg-surface-secondary, #f8fafc); padding: 0.5rem; border-radius: 4px; border: 1px solid var(--border-color); font-family: monospace; white-space: pre-wrap; word-break: break-all; max-height: 120px; overflow-y: auto;">${this.escapeHtml(log.newValue || '-')}</div>

				<div style="font-weight: 600; color: var(--text-muted);">${I18n.t('auditLog.details')}:</div>
				<div style="background: var(--bg-surface-secondary, #f8fafc); padding: 0.5rem; border-radius: 4px; border: 1px solid var(--border-color); white-space: pre-wrap; word-break: break-word; max-height: 150px; overflow-y: auto;">${this.escapeHtml(log.details || '-')}</div>
			</div>
		`;

        modal.style.display = 'flex';
    }

    getActionBadgeClass(action) {
        if (!action) return 'badge-secondary';
        switch (action.toUpperCase()) {
            case 'CREATE':
                return 'badge-success';
            case 'UPDATE':
            case 'CORRECT':
                return 'badge-info';
            case 'DELETE':
            case 'REMOVE':
            case 'PURGE':
            case 'REJECT':
            case 'CANCEL':
                return 'badge-danger';
            case 'SUBMIT':
            case 'APPROVE':
            case 'CLOSE':
                return 'badge-primary';
            default:
                return 'badge-secondary';
        }
    }

    escapeHtml(str) {
        if (str === null || str === undefined) return '';
        return String(str)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#039;');
    }
}
