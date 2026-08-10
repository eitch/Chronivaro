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
}
