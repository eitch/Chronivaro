import AuthApi from '../api/AuthApi.js';
import EmployeeApi from '../api/EmployeeApi.js';
import ConfigurationApi from '../api/ConfigurationApi.js';
import Format from '../utils/Format.js';
import I18n from '../i18n/I18n.js';

export default class ProfileView {

    constructor(app) {
        this.app = app;
    }

    async render() {
        const container = document.createElement('div');
        container.id = 'profile-view';
        container.innerHTML = `
            <div class="view-header">
                <h2>${I18n.t('profile.title')}</h2>
                <p class="subtitle">${I18n.t('profile.subtitle')}</p>
            </div>
            <div id="profile-content" class="profile-content">
                <div class="card">${I18n.t('common.loading')}</div>
            </div>
        `;

        const contentEl = container.querySelector('#profile-content');

        try {
            const username = AuthApi.getUsername();
            const firstname = AuthApi.getFirstname();
            const lastname = AuthApi.getLastname();
            const roles = AuthApi.getRoles() || [];

            let employee = null;
            let schedules = [];
            let weeklyTargetMinutes = this.app?.branding?.weeklyTargetMinutes;

            if (!weeklyTargetMinutes) {
                try {
                    const branding = await ConfigurationApi.getBranding();
                    if (branding && branding.weeklyTargetMinutes) {
                        weeklyTargetMinutes = branding.weeklyTargetMinutes;
                    }
                } catch (err) {
                    // Ignore, fallback to default
                }
            }
            if (!weeklyTargetMinutes || weeklyTargetMinutes <= 0) {
                weeklyTargetMinutes = 2520;
            }

            try {
                employee = await EmployeeApi.getMyProfile();
            } catch (err) {
                // Not found or not an employee
                employee = null;
            }

            if (employee) {
                try {
                    schedules = await EmployeeApi.getMySchedules();
                    if (!Array.isArray(schedules)) {
                        schedules = schedules ? [schedules] : [];
                    }
                } catch (err) {
                    schedules = [];
                }
            }

            contentEl.innerHTML = this.buildProfileHtml(username, firstname, lastname, roles, employee, schedules, weeklyTargetMinutes);
        } catch (err) {
            console.error('Failed to load profile', err);
            contentEl.innerHTML = `<div class="card error"><p>${err.message || I18n.t('app.error')}</p></div>`;
        }

        return container;
    }

    buildProfileHtml(username, firstname, lastname, roles, employee, schedules, weeklyTargetMinutes = 2520) {
        // Build User Account Card
        const displayRoles = roles.filter(role => role !== 'ModelAccessor' && role !== 'PrivilegeAdmin');
        const roleBadges = displayRoles.length > 0
            ? displayRoles.map(role => {
                const roleKey = `enums.roles.${role}`;
                const translated = I18n.t(roleKey);
                return `<span class="badge role-badge">${(translated && translated !== roleKey) ? translated : role}</span>`;
            }).join(' ')
            : `<span class="text-muted">-</span>`;

        const fullName = [firstname, lastname].filter(Boolean).join(' ').trim() || (employee ? `${employee.firstname || ''} ${employee.lastname || ''}`.trim() : '') || username || '-';
        const email = (employee && employee.email) ? employee.email : '-';

        let html = `
            <div class="profile-cards-grid">
                <div class="card profile-card">
                    <div class="card-header">
                        <h3>${I18n.t('profile.userAccount')}</h3>
                    </div>
                    <div class="profile-details">
                        <div class="profile-row">
                            <span class="profile-label">${I18n.t('user.name')}:</span>
                            <span class="profile-value font-semibold">${fullName}</span>
                        </div>
                        <div class="profile-row">
                            <span class="profile-label">${I18n.t('user.username')}:</span>
                            <span class="profile-value">@${username || '-'}</span>
                        </div>
                        <div class="profile-row">
                            <span class="profile-label">${I18n.t('common.email')}:</span>
                            <span class="profile-value">${email}</span>
                        </div>
                        <div class="profile-row">
                            <span class="profile-label">${I18n.t('user.roles')}:</span>
                            <div class="profile-value profile-roles-container">${roleBadges}</div>
                        </div>
                    </div>
                </div>
        `;

        if (employee) {
            const personalNumber = employee.personalNumber || '-';
            const team = employee.teamName || employee.teamId || '-';
            const location = employee.locationName || employee.locationId || '-';
            const timezone = employee.timezone || 'Europe/Zurich';
            const joinDate = employee.joinDate ? Format.date(employee.joinDate) : '-';
            const exitDate = employee.exitDate ? Format.date(employee.exitDate) : '-';
            const statusBadge = employee.active
                ? `<span class="badge status-active">${I18n.t('common.active')}</span>`
                : `<span class="badge status-inactive">${I18n.t('common.inactive')}</span>`;

            html += `
                <div class="card profile-card">
                    <div class="card-header">
                        <h3>${I18n.t('profile.masterData')}</h3>
                    </div>
                    <div class="profile-details">
                        <div class="profile-row">
                            <span class="profile-label">${I18n.t('employees.personalNumber')}:</span>
                            <span class="profile-value font-semibold">${personalNumber}</span>
                        </div>
                        <div class="profile-row">
                            <span class="profile-label">${I18n.t('employees.team')}:</span>
                            <span class="profile-value">${team}</span>
                        </div>
                        <div class="profile-row">
                            <span class="profile-label">${I18n.t('employees.location')}:</span>
                            <span class="profile-value">${location}</span>
                        </div>
                        <div class="profile-row">
                            <span class="profile-label">${I18n.t('employees.timezone')}:</span>
                            <span class="profile-value">${timezone}</span>
                        </div>
                        <div class="profile-row">
                            <span class="profile-label">${I18n.t('employees.joinDate')}:</span>
                            <span class="profile-value">${joinDate}</span>
                        </div>
                        <div class="profile-row">
                            <span class="profile-label">${I18n.t('employees.exitDate')}:</span>
                            <span class="profile-value">${exitDate}</span>
                        </div>
                        <div class="profile-row">
                            <span class="profile-label">${I18n.t('common.status')}:</span>
                            <span class="profile-value">${statusBadge}</span>
                        </div>
                    </div>
                </div>
            `;
        } else {
            html += `
                <div class="card profile-card profile-card-info">
                    <div class="card-header">
                        <h3>${I18n.t('profile.masterData')}</h3>
                    </div>
                    <div class="profile-notice">
                        <p class="font-semibold">${I18n.t('profile.noEmployeeProfile')}</p>
                        <p class="text-muted">${I18n.t('profile.noEmployeeProfileDesc')}</p>
                    </div>
                </div>
            `;
        }

        html += `</div>`;

        if (employee) {
            html += `
                <div class="card profile-schedule-card" style="margin-top: 1.5rem;">
                    <div class="card-header">
                        <h3>${I18n.t('profile.employmentSchedule')}</h3>
                    </div>
            `;

            if (schedules && schedules.length > 0) {
                const now = new Date();
                const activeSchedule = schedules.find(s => {
                    const from = s.validFrom ? new Date(s.validFrom) : null;
                    const to = s.validTo ? new Date(s.validTo) : null;
                    if (from && from > now) return false;
                    if (to && to < now) return false;
                    return true;
                }) || schedules[0];

                const weeklyTarget = (activeSchedule.monday || 0) + (activeSchedule.tuesday || 0) +
                    (activeSchedule.wednesday || 0) + (activeSchedule.thursday || 0) +
                    (activeSchedule.friday || 0) + (activeSchedule.saturday || 0) +
                    (activeSchedule.sunday || 0);

                const rate = weeklyTargetMinutes > 0 ? Math.round((weeklyTarget / weeklyTargetMinutes) * 100) : 0;
                const validFrom = activeSchedule.validFrom ? Format.date(activeSchedule.validFrom) : '-';
                const validTo = activeSchedule.validTo ? Format.date(activeSchedule.validTo) : `${I18n.t('common.today')} / ∞`;

                html += `
                    <div class="schedule-summary-grid" style="display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 1rem; margin-bottom: 1.5rem; padding-bottom: 1rem; border-bottom: 1px solid var(--border-color);">
                        <div class="schedule-summary-item">
                            <span class="text-muted" style="font-size: 0.85rem; display: block;">${I18n.t('profile.weeklyWorkload')}</span>
                            <span style="font-size: 1.25rem; font-weight: 700; color: var(--primary-color);">${Format.duration(weeklyTarget)}</span>
                        </div>
                        <div class="schedule-summary-item">
                            <span class="text-muted" style="font-size: 0.85rem; display: block;">${I18n.t('profile.employmentRate')}</span>
                            <span style="font-size: 1.25rem; font-weight: 700;">${rate}%</span>
                        </div>
                        <div class="schedule-summary-item">
                            <span class="text-muted" style="font-size: 0.85rem; display: block;">${I18n.t('profile.validRange')}</span>
                            <span style="font-size: 0.95rem; font-weight: 500;">${validFrom} – ${validTo}</span>
                        </div>
                    </div>

                    <h4 style="margin: 0 0 0.75rem 0; font-size: 1rem;">${I18n.t('profile.dailyHours')}</h4>
                    <table class="data-table schedule-days-table" style="width: 100%; margin-bottom: 1.5rem;">
                        <thead>
                            <tr>
                                <th>${I18n.t('scheduleTemplates.mon')}</th>
                                <th>${I18n.t('scheduleTemplates.tue')}</th>
                                <th>${I18n.t('scheduleTemplates.wed')}</th>
                                <th>${I18n.t('scheduleTemplates.thu')}</th>
                                <th>${I18n.t('scheduleTemplates.fri')}</th>
                                <th>${I18n.t('scheduleTemplates.sat')}</th>
                                <th>${I18n.t('scheduleTemplates.sun')}</th>
                            </tr>
                        </thead>
                        <tbody>
                            <tr>
                                <td>${Format.duration(activeSchedule.monday || 0)}</td>
                                <td>${Format.duration(activeSchedule.tuesday || 0)}</td>
                                <td>${Format.duration(activeSchedule.wednesday || 0)}</td>
                                <td>${Format.duration(activeSchedule.thursday || 0)}</td>
                                <td>${Format.duration(activeSchedule.friday || 0)}</td>
                                <td>${Format.duration(activeSchedule.saturday || 0)}</td>
                                <td>${Format.duration(activeSchedule.sunday || 0)}</td>
                            </tr>
                        </tbody>
                    </table>
                `;

                if (schedules.length > 1) {
                    html += `
                        <h4 style="margin: 1.5rem 0 0.75rem 0; font-size: 1rem;">${I18n.t('profile.scheduleHistory')}</h4>
                        <table class="data-table" style="width: 100%;">
                            <thead>
                                <tr>
                                    <th>${I18n.t('schedules.validFrom')}</th>
                                    <th>${I18n.t('schedules.validTo')}</th>
                                    <th>${I18n.t('profile.weeklyWorkload')}</th>
                                    <th>${I18n.t('profile.employmentRate')}</th>
                                </tr>
                            </thead>
                            <tbody>
                                ${schedules.map(s => {
                                    const total = (s.monday || 0) + (s.tuesday || 0) + (s.wednesday || 0) + (s.thursday || 0) + (s.friday || 0) + (s.saturday || 0) + (s.sunday || 0);
                                    const pct = weeklyTargetMinutes > 0 ? Math.round((total / weeklyTargetMinutes) * 100) : 0;
                                    return `
                                        <tr>
                                            <td>${s.validFrom ? Format.date(s.validFrom) : '-'}</td>
                                            <td>${s.validTo ? Format.date(s.validTo) : '-'}</td>
                                            <td>${Format.duration(total)}</td>
                                            <td>${pct}%</td>
                                        </tr>
                                    `;
                                }).join('')}
                            </tbody>
                        </table>
                    `;
                }
            } else {
                html += `<p class="text-muted">${I18n.t('profile.noScheduleFound')}</p>`;
            }

            html += `</div>`;
        }

        return html;
    }
}
