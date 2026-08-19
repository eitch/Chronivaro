export default class NotificationDialog {
    static show(message, title = 'Notification') {
        const dialog = document.createElement('div');
        dialog.className = 'notification-dialog-overlay';
        dialog.innerHTML = `
            <div class="notification-dialog">
                <div class="notification-dialog-header">
                    <h3>${title}</h3>
                </div>
                <div class="notification-dialog-body">
                    <p>${message}</p>
                </div>
                <div class="notification-dialog-footer">
                    <button class="notification-dialog-button">OK</button>
                </div>
            </div>
        `;

        document.body.appendChild(dialog);

        return new Promise((resolve) => {
            const button = dialog.querySelector('.notification-dialog-button');
            button.focus();
            button.addEventListener('click', () => {
                document.body.removeChild(dialog);
                resolve();
            });
        });
    }

    static error(message) {
        return this.show(message, 'Error');
    }

    static info(message) {
        return this.show(message, 'Info');
    }

    static confirm(message, title = 'Confirm') {
        const dialog = document.createElement('div');
        dialog.className = 'notification-dialog-overlay';
        dialog.innerHTML = `
            <div class="notification-dialog">
                <div class="notification-dialog-header">
                    <h3>${title}</h3>
                </div>
                <div class="notification-dialog-body">
                    <p>${message}</p>
                </div>
                <div class="notification-dialog-footer">
                    <button class="notification-dialog-button secondary">Cancel</button>
                    <button class="notification-dialog-button primary">OK</button>
                </div>
            </div>
        `;

        document.body.appendChild(dialog);

        return new Promise((resolve) => {
            const okBtn = dialog.querySelector('.notification-dialog-button.primary');
            const cancelBtn = dialog.querySelector('.notification-dialog-button.secondary');
            okBtn.focus();

            okBtn.addEventListener('click', () => {
                document.body.removeChild(dialog);
                resolve(true);
            });

            cancelBtn.addEventListener('click', () => {
                document.body.removeChild(dialog);
                resolve(false);
            });
        });
    }

    static prompt(message, title = 'Input Required', placeholder = '', defaultValue = '', required = true) {
        const dialog = document.createElement('div');
        dialog.className = 'notification-dialog-overlay';
        dialog.innerHTML = `
            <div class="notification-dialog">
                <div class="notification-dialog-header">
                    <h3>${title}</h3>
                </div>
                <div class="notification-dialog-body">
                    <p>${message}</p>
                    <textarea class="notification-dialog-input form-textarea" placeholder="${placeholder}" rows="3" style="width: 100%; margin-top: 8px; box-sizing: border-box; padding: 8px;">${defaultValue}</textarea>
                    <div class="notification-dialog-error error hidden" style="margin-top: 4px; font-size: 0.85em; color: var(--error-color);">Input is required.</div>
                </div>
                <div class="notification-dialog-footer">
                    <button class="notification-dialog-button secondary">Cancel</button>
                    <button class="notification-dialog-button primary">Submit</button>
                </div>
            </div>
        `;

        document.body.appendChild(dialog);

        return new Promise((resolve) => {
            const okBtn = dialog.querySelector('.notification-dialog-button.primary');
            const cancelBtn = dialog.querySelector('.notification-dialog-button.secondary');
            const textarea = dialog.querySelector('.notification-dialog-input');
            const errorDiv = dialog.querySelector('.notification-dialog-error');

            textarea.focus();

            okBtn.addEventListener('click', () => {
                const val = textarea.value.trim();
                if (required && !val) {
                    errorDiv.classList.remove('hidden');
                    textarea.focus();
                    return;
                }
                document.body.removeChild(dialog);
                resolve(val);
            });

            cancelBtn.addEventListener('click', () => {
                document.body.removeChild(dialog);
                resolve(null);
            });
        });
    }
}
