import TokenApi from '../api/TokenApi.js';
import NotificationDialog from '../utils/NotificationDialog.js';
import Format from '../utils/Format.js';
import I18n from '../i18n/I18n.js';

export default class TokensView {
    constructor(app) {
        this.app = app;
        this.tokens = [];
    }

    async render() {
        const container = document.createElement('div');
        container.id = 'tokens-view';
        container.className = 'page-container';
        container.innerHTML = `
            <div class="page-header" style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 1.5rem;">
                <div>
                    <h2 style="margin: 0 0 0.5rem 0;">${I18n.t('tokens.title')}</h2>
                    <p class="text-muted" style="margin: 0;">${I18n.t('tokens.subtitle')}</p>
                </div>
                <div class="actions">
                    <button id="create-token-btn" class="primary-btn">${I18n.t('tokens.createToken')}</button>
                </div>
            </div>

            <div class="table-container card" style="padding: 1rem; overflow: visible;">
                <table id="tokens-table" class="data-table">
                    <thead>
                        <tr>
                            <th>${I18n.t('tokens.name')}</th>
                            <th>${I18n.t('tokens.preset')}</th>
                            <th>${I18n.t('tokens.validFrom')}</th>
                            <th>${I18n.t('tokens.validTo')}</th>
                            <th>${I18n.t('tokens.lastUsed')}</th>
                            <th>${I18n.t('common.status')}</th>
                            <th>${I18n.t('common.actions')}</th>
                        </tr>
                    </thead>
                    <tbody>
                        <tr><td colspan="7" class="loading-cell" style="text-align: center; padding: 2rem;">${I18n.t('common.loading')}</td></tr>
                    </tbody>
                </table>
            </div>

            <!-- Create Token Modal -->
            <div id="create-token-modal" class="modal" style="display: none;">
                <div class="modal-content" style="max-width: 550px; width: 90%;">
                    <div class="modal-header" style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 1.5rem; border-bottom: 1px solid var(--border-color); padding-bottom: 1rem;">
                        <h3 id="create-token-modal-title" style="margin: 0;">${I18n.t('tokens.createToken')}</h3>
                        <button type="button" id="create-token-close-icon" class="close-btn" style="background: none; border: none; font-size: 1.5rem; cursor: pointer;">&times;</button>
                    </div>
                    <form id="create-token-form">
                        <div class="form-group" style="margin-bottom: 1rem;">
                            <label for="token-name" style="display: block; margin-bottom: 0.25rem; font-weight: 500;">${I18n.t('tokens.name')} *</label>
                            <input type="text" id="token-name" required placeholder="${I18n.t('tokens.namePlaceholder')}" style="width: 100%; padding: 0.5rem; border: 1px solid var(--border-color); border-radius: 4px;">
                        </div>
                        <div class="form-group" style="margin-bottom: 1rem;">
                            <label for="token-preset" style="display: block; margin-bottom: 0.25rem; font-weight: 500;">${I18n.t('tokens.preset')} *</label>
                            <select id="token-preset" style="width: 100%; padding: 0.5rem; border: 1px solid var(--border-color); border-radius: 4px;">
                                <option value="DESKTOP_TIMER">${I18n.t('tokens.presetDesktopTimer')}</option>
                                <option value="READ_ONLY_TIMES">${I18n.t('tokens.presetReadOnlyTimes')}</option>
                                <option value="FULL_PERSONAL">${I18n.t('tokens.presetFullPersonal')}</option>
                            </select>
                            <small id="token-preset-desc" class="text-muted" style="display: block; margin-top: 0.25rem; font-size: 0.85rem;">
                                ${I18n.t('tokens.presetDesktopTimerDesc')}
                            </small>
                        </div>
                        <div class="form-group" style="margin-bottom: 1rem;">
                            <label for="token-valid-to" style="display: block; margin-bottom: 0.25rem; font-weight: 500;">${I18n.t('tokens.validTo')}</label>
                            <input type="date" id="token-valid-to" style="width: 100%; padding: 0.5rem; border: 1px solid var(--border-color); border-radius: 4px;">
                        </div>
                        <div class="form-group" style="margin-bottom: 1.5rem;">
                            <label style="display: flex; align-items: center; gap: 0.5rem; cursor: pointer;">
                                <input type="checkbox" id="token-no-expiry">
                                <span>${I18n.t('tokens.noExpiry')}</span>
                            </label>
                        </div>
                        <div class="modal-actions" style="display: flex; justify-content: flex-end; gap: 0.75rem; border-top: 1px solid var(--border-color); padding-top: 1rem;">
                            <button type="submit" id="save-token-btn" class="primary-btn">${I18n.t('tokens.generate')}</button>
                            <button type="button" id="create-token-cancel-btn" class="secondary-btn">${I18n.t('common.cancel')}</button>
                        </div>
                    </form>
                </div>
            </div>

            <!-- Secret Display Modal -->
            <div id="token-secret-modal" class="modal" style="display: none;">
                <div class="modal-content" style="max-width: 550px; width: 90%;">
                    <div class="modal-header" style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 1rem; border-bottom: 1px solid var(--border-color); padding-bottom: 1rem;">
                        <h3 style="margin: 0;">${I18n.t('tokens.tokenCreatedTitle')}</h3>
                        <button type="button" id="token-secret-close-icon" class="close-btn" style="background: none; border: none; font-size: 1.5rem; cursor: pointer;">&times;</button>
                    </div>
                    <div class="modal-body">
                        <div class="alert alert-warning" style="background: #fffaf0; border: 1px solid #feebc8; color: #7b341e; padding: 0.75rem 1rem; border-radius: 4px; margin-bottom: 1rem; font-size: 0.9rem;">
                            <strong>${I18n.t('tokens.warningTitle')}:</strong> ${I18n.t('tokens.warningCopyOnce')}
                        </div>
                        <div class="form-group" style="margin-bottom: 1.5rem;">
                            <label style="display: block; margin-bottom: 0.25rem; font-weight: 500;">${I18n.t('tokens.tokenValue')}</label>
                            <div style="display: flex; gap: 0.5rem;">
                                <input type="text" id="token-secret-input" readonly style="flex: 1; padding: 0.5rem; font-family: monospace; font-size: 0.9rem; background: var(--bg-hover, #f7fafc); border: 1px solid var(--border-color); border-radius: 4px;">
                                <button type="button" id="token-copy-btn" class="secondary-btn" title="${I18n.t('tokens.copyToClipboard')}">${I18n.t('tokens.copy')}</button>
                            </div>
                        </div>
                    </div>
                    <div class="modal-actions" style="display: flex; justify-content: flex-end; border-top: 1px solid var(--border-color); padding-top: 1rem;">
                        <button type="button" id="token-secret-done-btn" class="primary-btn">${I18n.t('common.done')}</button>
                    </div>
                </div>
            </div>
        `;

        const tbody = container.querySelector('#tokens-table tbody');
        const createModal = container.querySelector('#create-token-modal');
        const secretModal = container.querySelector('#token-secret-modal');
        const createForm = container.querySelector('#create-token-form');
        const presetSelect = container.querySelector('#token-preset');
        const presetDesc = container.querySelector('#token-preset-desc');
        const validToInput = container.querySelector('#token-valid-to');
        const noExpiryCheckbox = container.querySelector('#token-no-expiry');

        const updatePresetDesc = () => {
            const val = presetSelect.value;
            if (val === 'DESKTOP_TIMER') {
                presetDesc.textContent = I18n.t('tokens.presetDesktopTimerDesc');
            } else if (val === 'READ_ONLY_TIMES') {
                presetDesc.textContent = I18n.t('tokens.presetReadOnlyTimesDesc');
            } else if (val === 'FULL_PERSONAL') {
                presetDesc.textContent = I18n.t('tokens.presetFullPersonalDesc');
            }
        };

        presetSelect.addEventListener('change', updatePresetDesc);

        noExpiryCheckbox.addEventListener('change', () => {
            if (noExpiryCheckbox.checked) {
                validToInput.value = '';
                validToInput.disabled = true;
            } else {
                validToInput.disabled = false;
                // Default to 1 year from now
                const d = new Date();
                d.setFullYear(d.getFullYear() + 1);
                validToInput.value = d.toISOString().substring(0, 10);
            }
        });

        const loadTokens = async () => {
            try {
                tbody.innerHTML = `<tr><td colspan="7" class="loading-cell" style="text-align: center; padding: 2rem;">${I18n.t('common.loading')}</td></tr>`;
                this.tokens = await TokenApi.getMyTokens();
                if (!Array.isArray(this.tokens)) {
                    this.tokens = [];
                }
                renderTokensTable();
            } catch (err) {
                console.error('Failed to load tokens', err);
                tbody.innerHTML = `<tr><td colspan="7" class="error-cell" style="text-align: center; color: var(--error-color); padding: 2rem;">${err.message || I18n.t('app.error')}</td></tr>`;
            }
        };

        const renderTokensTable = () => {
            tbody.innerHTML = '';
            if (this.tokens.length === 0) {
                tbody.innerHTML = `<tr><td colspan="7" class="empty-cell" style="text-align: center; padding: 2rem; color: var(--text-muted);">${I18n.t('tokens.noTokens')}</td></tr>`;
                return;
            }

            const now = new Date();

            this.tokens.forEach(tok => {
                const tr = document.createElement('tr');

                // Determine preset badge
                const presetKey = `tokens.preset${tok.preset === 'DESKTOP_TIMER' ? 'DesktopTimer' : tok.preset === 'READ_ONLY_TIMES' ? 'ReadOnlyTimes' : 'FullPersonal'}`;
                const presetLabel = I18n.t(presetKey) || tok.preset;

                // Dates
                const validFrom = tok.validFrom ? Format.date(tok.validFrom) : '-';
                const validTo = tok.validTo ? Format.date(tok.validTo) : I18n.t('tokens.noExpiry');
                const lastUsed = tok.lastUsed ? Format.dateTime(tok.lastUsed) : `<span class="text-muted">${I18n.t('tokens.neverUsed')}</span>`;

                // Status
                let isExpired = false;
                if (tok.validTo) {
                    const toDate = new Date(tok.validTo);
                    if (toDate < now) {
                        isExpired = true;
                    }
                }

                const statusBadge = isExpired
                    ? `<span class="badge badge-expired" style="background: #fed7d7; color: #9b2c2c; padding: 0.2rem 0.5rem; border-radius: 3px; font-size: 0.8rem;">${I18n.t('tokens.statusExpired')}</span>`
                    : `<span class="badge badge-active" style="background: #c6f6d5; color: #22543d; padding: 0.2rem 0.5rem; border-radius: 3px; font-size: 0.8rem;">${I18n.t('tokens.statusActive')}</span>`;

                tr.innerHTML = `
                    <td><strong>${tok.name}</strong></td>
                    <td><span class="badge badge-preset" style="background: var(--bg-hover, #edf2f7); padding: 0.2rem 0.5rem; border-radius: 3px; font-size: 0.8rem;">${presetLabel}</span></td>
                    <td>${validFrom}</td>
                    <td>${validTo}</td>
                    <td>${lastUsed}</td>
                    <td>${statusBadge}</td>
                    <td>
                        <button class="delete-btn revoke-token-btn" data-id="${tok.tokenId}" style="color: var(--error-color, #e53e3e); background: none; border: 1px solid var(--border-color); padding: 0.3rem 0.6rem; border-radius: 4px; cursor: pointer; font-size: 0.85rem;">
                            ${I18n.t('tokens.revoke')}
                        </button>
                    </td>
                `;
                tbody.appendChild(tr);
            });

            container.querySelectorAll('.revoke-token-btn').forEach(btn => {
                btn.addEventListener('click', () => revokeToken(btn.dataset.id));
            });
        };

        const openCreateModal = () => {
            createForm.reset();
            const d = new Date();
            d.setFullYear(d.getFullYear() + 1);
            validToInput.value = d.toISOString().substring(0, 10);
            validToInput.disabled = false;
            noExpiryCheckbox.checked = false;
            updatePresetDesc();
            createModal.style.display = 'block';
            container.querySelector('#token-name').focus();
        };

        const closeCreateModal = () => {
            createModal.style.display = 'none';
        };

        const closeSecretModal = () => {
            secretModal.style.display = 'none';
            loadTokens();
        };

        const revokeToken = async (tokenId) => {
            const token = this.tokens.find(t => t.tokenId === tokenId);
            const tokenName = token ? token.name : tokenId;

            if (await NotificationDialog.confirm(I18n.t('tokens.confirmRevoke', { name: tokenName }))) {
                try {
                    await TokenApi.deleteMyToken(tokenId);
                    await NotificationDialog.info(I18n.t('tokens.revokeSuccess'), I18n.t('common.success'));
                    await loadTokens();
                } catch (err) {
                    console.error('Failed to revoke token', err);
                    await NotificationDialog.error(err.message || I18n.t('app.error'));
                }
            }
        };

        container.querySelector('#create-token-btn').addEventListener('click', openCreateModal);
        container.querySelector('#create-token-close-icon').addEventListener('click', closeCreateModal);
        container.querySelector('#create-token-cancel-btn').addEventListener('click', closeCreateModal);

        createForm.addEventListener('submit', async (e) => {
            e.preventDefault();
            const name = container.querySelector('#token-name').value.trim();
            const preset = presetSelect.value;
            const noExpiry = noExpiryCheckbox.checked;
            const validToDateStr = validToInput.value;

            if (!name) return;

            let validTo = null;
            if (!noExpiry && validToDateStr) {
                // ISO timestamp format e.g. 2027-09-17T23:59:59Z
                validTo = new Date(`${validToDateStr}T23:59:59`).toISOString();
            }

            const payload = {
                name,
                preset,
                validTo
            };

            try {
                const created = await TokenApi.createMyToken(payload);
                closeCreateModal();

                const secretInput = container.querySelector('#token-secret-input');
                secretInput.value = created.token || `${created.tokenId}:${created.name}`;
                secretModal.style.display = 'block';
            } catch (err) {
                console.error('Failed to create token', err);
                await NotificationDialog.error(err.message || I18n.t('app.error'));
            }
        });

        const copyBtn = container.querySelector('#token-copy-btn');
        copyBtn.addEventListener('click', async () => {
            const secretInput = container.querySelector('#token-secret-input');
            try {
                await navigator.clipboard.writeText(secretInput.value);
                copyBtn.textContent = I18n.t('tokens.copied');
                setTimeout(() => {
                    copyBtn.textContent = I18n.t('tokens.copy');
                }, 2000);
            } catch (e) {
                secretInput.select();
                document.execCommand('copy');
                copyBtn.textContent = I18n.t('tokens.copied');
                setTimeout(() => {
                    copyBtn.textContent = I18n.t('tokens.copy');
                }, 2000);
            }
        });

        container.querySelector('#token-secret-close-icon').addEventListener('click', closeSecretModal);
        container.querySelector('#token-secret-done-btn').addEventListener('click', closeSecretModal);

        loadTokens();
        return container;
    }
}
