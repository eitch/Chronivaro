import AuthApi from './api/AuthApi.js';
import LoginView from './pages/LoginView.js';
import DashboardView from './pages/DashboardView.js';
import PresenceView from './pages/PresenceView.js';
import MyTimesView from './pages/MyTimesView.js';
import MyAbsencesView from './pages/MyAbsencesView.js';
import MyPeriodsView from './pages/MyPeriodsView.js';
import ApprovalsView from './pages/ApprovalsView.js';
import ReportsView from './pages/ReportsView.js';
import EmployeesView from './pages/EmployeesView.js';
import TeamsView from './pages/TeamsView.js';
import LocationsView from './pages/LocationsView.js';
import AbsenceTypesView from './pages/AbsenceTypesView.js';
import HolidayCalendarsView from './pages/HolidayCalendarsView.js';
import SchedulesView from './pages/SchedulesView.js';
import ScheduleTemplatesView from './pages/ScheduleTemplatesView.js';
import ConfigurationView from './pages/ConfigurationView.js';
import UsersView from './pages/UsersView.js';
import AuditLogView from './pages/AuditLogView.js';
import ConfigurationApi from './api/ConfigurationApi.js';
import CompleteRegistrationView from './pages/CompleteRegistrationView.js';
import NotificationDialog from './utils/NotificationDialog.js';
import MonthPicker from './utils/MonthPicker.js';
import I18n from './i18n/I18n.js';

class ChronivaroApp {
    constructor() {
        this.appContainer = document.getElementById('app');
        this.nav = document.querySelector('header nav');
        this.headerActions = document.getElementById('header-actions');
        this.currentHash = '';
        this.currentViewName = '';

        window.addEventListener('unauthorized', () => {
            this.navigate('login');
        });

        window.addEventListener('hashchange', () => {
            this.route();
        });

        const logoutBtn = document.getElementById('logout-btn') || document.getElementById('logout-link');
        if (logoutBtn) {
            logoutBtn.addEventListener('click', (e) => {
                e.preventDefault();
                this.closeNavGroups();
                AuthApi.logout();
                this.updateUserMenu();
                this.navigate('login');
            });
        }

        const headerLangSelect = document.getElementById('header-language-select');
        if (headerLangSelect) {
            headerLangSelect.addEventListener('change', async (e) => {
                await I18n.setLanguage(e.target.value, true);
            });
        }

        const headerBranding = document.getElementById('header-branding');
        if (headerBranding) {
            headerBranding.addEventListener('click', (e) => {
                this.closeNavGroups();
                if (navToggle && this.nav && this.nav.classList.contains('is-open')) {
                    navToggle.setAttribute('aria-expanded', 'false');
                    this.nav.classList.remove('is-open');
                }
                if (window.location.hash === '#dashboard' || window.location.hash === '') {
                    e.preventDefault();
                    this.route();
                }
            });
        }

        const navToggle = document.getElementById('nav-toggle');
        if (navToggle) {
            navToggle.addEventListener('click', () => {
                const expanded = navToggle.getAttribute('aria-expanded') === 'true';
                navToggle.setAttribute('aria-expanded', String(!expanded));
                this.nav.classList.toggle('is-open', !expanded);
            });
        }

        if (this.nav) {
            this.nav.addEventListener('click', (e) => {
                const link = e.target.closest('.nav-link');
                if (link) {
                    this.closeNavGroups();
                    if (navToggle && this.nav.classList.contains('is-open')) {
                        navToggle.setAttribute('aria-expanded', 'false');
                        this.nav.classList.remove('is-open');
                    }
                }
            });
        }

        document.querySelectorAll('.nav-group').forEach(group => {
            group.addEventListener('toggle', () => {
                if (group.open) {
                    document.querySelectorAll('.nav-group').forEach(other => {
                        if (other !== group && other.open) {
                            other.removeAttribute('open');
                        }
                    });
                }
            });
        });

        document.addEventListener('click', (e) => {
            if (this.nav && !e.target.closest('.nav-group')) {
                this.closeNavGroups();
            }
        });

        this.setupChangePasswordModal();

        I18n.onLanguageChange((lang) => {
            this.onLanguageChanged(lang);
        });
    }

    setupChangePasswordModal() {
        const changePasswordBtn = document.getElementById('change-password-btn');
        const changePasswordModal = document.getElementById('change-password-modal');
        const changePasswordForm = document.getElementById('change-password-form');
        const changePasswordCloseIcon = document.getElementById('change-password-close-icon');
        const changePasswordCancelBtn = document.getElementById('change-password-cancel-btn');
        const changePasswordError = document.getElementById('change-password-error');

        const closePasswordModal = () => {
            if (changePasswordModal) {
                changePasswordModal.style.display = 'none';
            }
            if (changePasswordForm) {
                changePasswordForm.reset();
            }
            if (changePasswordError) {
                changePasswordError.style.display = 'none';
                changePasswordError.textContent = '';
            }
        };

        if (changePasswordBtn) {
            changePasswordBtn.addEventListener('click', () => {
                this.closeNavGroups();
                if (changePasswordModal) {
                    closePasswordModal();
                    changePasswordModal.style.display = 'block';
                    const newPwdInput = document.getElementById('change-pwd-new');
                    if (newPwdInput) newPwdInput.focus();
                }
            });
        }

        if (changePasswordCloseIcon) {
            changePasswordCloseIcon.addEventListener('click', closePasswordModal);
        }

        if (changePasswordCancelBtn) {
            changePasswordCancelBtn.addEventListener('click', closePasswordModal);
        }

        if (changePasswordForm) {
            changePasswordForm.addEventListener('submit', async (e) => {
                e.preventDefault();
                if (changePasswordError) {
                    changePasswordError.style.display = 'none';
                    changePasswordError.textContent = '';
                }

                const newPassword = document.getElementById('change-pwd-new')?.value;
                const confirmPassword = document.getElementById('change-pwd-confirm')?.value;

                if (!newPassword) {
                    if (changePasswordError) {
                        changePasswordError.textContent = I18n.t('errors.validationError');
                        changePasswordError.style.display = 'block';
                    }
                    return;
                }

                if (newPassword !== confirmPassword) {
                    if (changePasswordError) {
                        changePasswordError.textContent = I18n.t('user.passwordMismatch') || I18n.t('auth.passwordMismatch');
                        changePasswordError.style.display = 'block';
                    }
                    return;
                }

                try {
                    await AuthApi.changePassword(newPassword);
                    closePasswordModal();
                    await NotificationDialog.info(I18n.t('user.passwordChangeSuccess') || 'Password changed successfully.', I18n.t('common.success'));
                    AuthApi.logout();
                    this.updateUserMenu();
                    this.navigate('login');
                } catch (err) {
                    console.error('Failed to change password', err);
                    if (changePasswordError) {
                        changePasswordError.textContent = err.message || I18n.t('app.error');
                        changePasswordError.style.display = 'block';
                    }
                }
            });
        }
    }

    async start() {
        await this.initI18n();
        this.loadBranding();
        MonthPicker.initAll();
        this.route();
    }

    async initI18n() {
        try {
            let defaultLang = 'en';
            try {
                const branding = await ConfigurationApi.getBranding();
                if (branding && branding.defaultLanguage) {
                    defaultLang = branding.defaultLanguage;
                }
                this.updateBranding(branding);
            } catch (e) {
                console.warn('Could not load global branding during i18n init', e);
            }
            await I18n.init({
                defaultLanguage: defaultLang,
                userLocale: AuthApi.getUserLocale()
            });
            this.syncLanguageUi(I18n.getLanguage());
        } catch (err) {
            console.error('Failed to initialize i18n', err);
        }
    }

    onLanguageChanged(lang) {
        document.documentElement.lang = lang;
        this.syncLanguageUi(lang);
        this.updateNavigation();
        if (this.currentHash) {
            this.showView(this.currentHash);
        }
    }

    syncLanguageUi(lang) {
        const headerLangSelect = document.getElementById('header-language-select');
        if (headerLangSelect) {
            headerLangSelect.value = lang;
        }
    }

    async loadBranding() {
        try {
            const branding = await ConfigurationApi.getBranding();
            this.updateBranding(branding);
        } catch (e) {
            console.warn('Could not load global branding', e);
        }
    }

    updateBranding(branding) {
        if (!branding) return;
        const titleEl = document.getElementById('header-title');
        const logoEl = document.getElementById('header-logo');

        if (titleEl && branding.companyName) {
            titleEl.textContent = branding.companyName;
            document.title = branding.companyName;
        }

        if (logoEl) {
            if (branding.companyLogo && branding.companyLogo.trim().length > 0) {
                logoEl.src = branding.companyLogo.trim();
                logoEl.alt = branding.companyName || 'Logo';
                logoEl.style.display = 'block';
                logoEl.onerror = () => {
                    logoEl.style.display = 'none';
                };
            } else {
                logoEl.style.display = 'none';
                logoEl.removeAttribute('src');
            }
        }
    }

    route() {
        let hash = window.location.hash.substring(1);
        if (!hash || hash === 'dashboard') {
            const roles = AuthApi.getRoles();
            if (roles.includes('Employee')) {
                hash = 'dashboard';
            } else if (roles.includes('Supervisor') || roles.includes('HR') || roles.includes('Administrator')) {
                hash = 'presence';
            } else {
                hash = 'dashboard';
            }
        }

        if (!AuthApi.isLoggedIn() && hash !== 'login' && !hash.startsWith('complete-registration')) {
            this.navigate('login');
            return;
        }

        this.updateNavigation();
        this.closeNavGroups();
        const navToggle = document.getElementById('nav-toggle');
        if (navToggle) {
            navToggle.setAttribute('aria-expanded', 'false');
            this.nav.classList.remove('is-open');
        }
        this.showView(hash);
    }

    closeNavGroups() {
        document.querySelectorAll('.nav-group[open]').forEach(group => {
            group.removeAttribute('open');
        });
    }

    updateNavigation() {
        const roles = AuthApi.getRoles();
        if (this.nav) {
            this.nav.querySelectorAll('li[data-roles]').forEach(li => {
                const requiredRoles = li.getAttribute('data-roles').split(',');
                const hasRole = requiredRoles.some(role => roles.includes(role));
                li.hidden = !hasRole;
            });
        }

        document.querySelectorAll('header [data-i18n], #change-password-modal [data-i18n]').forEach(el => {
            const key = el.getAttribute('data-i18n');
            if (key) {
                el.textContent = I18n.t(key);
            }
        });

        this.updateUserMenu();
    }

    updateUserMenu() {
        const userMenu = document.getElementById('user-menu');
        if (!userMenu) return;

        if (!AuthApi.isLoggedIn()) {
            userMenu.style.display = 'none';
            return;
        }
        userMenu.style.display = '';

        const username = AuthApi.getUsername();
        const firstname = AuthApi.getFirstname();
        const lastname = AuthApi.getLastname();
        const roles = AuthApi.getRoles();

        const headerUsernameEl = document.getElementById('header-username');
        if (headerUsernameEl) {
            headerUsernameEl.textContent = username || 'User';
        }

        const fullNameEl = document.getElementById('user-dropdown-fullname');
        if (fullNameEl) {
            const fullName = [firstname, lastname].filter(Boolean).join(' ').trim();
            fullNameEl.textContent = fullName || username || '-';
        }

        const handleEl = document.getElementById('user-dropdown-username');
        if (handleEl) {
            handleEl.textContent = username ? `@${username}` : '';
        }

        const rolesListEl = document.getElementById('user-dropdown-roles');
        if (rolesListEl) {
            rolesListEl.innerHTML = '';
            const displayRoles = (roles || []).filter(role => role !== 'ModelAccessor' && role !== 'PrivilegeAdmin');
            if (displayRoles.length > 0) {
                displayRoles.forEach(role => {
                    const badge = document.createElement('span');
                    badge.className = 'user-dropdown-role-badge';
                    const roleKey = `enums.roles.${role}`;
                    const translatedRole = I18n.t(roleKey);
                    badge.textContent = (translatedRole && translatedRole !== roleKey) ? translatedRole : role;
                    rolesListEl.appendChild(badge);
                });
            } else {
                const empty = document.createElement('span');
                empty.className = 'text-muted';
                empty.style.fontSize = '0.8rem';
                empty.textContent = '-';
                rolesListEl.appendChild(empty);
            }
        }
    }

    navigate(page, params) {
        if (params) {
            const query = Object.keys(params).map(k => `${encodeURIComponent(k)}=${encodeURIComponent(params[k])}`).join('&');
            window.location.hash = `${page}?${query}`;
        } else {
            window.location.hash = page;
        }
    }

    async showView(hash) {
        this.currentHash = hash;
        let viewName = hash;
        let params = {};
        if (hash.includes('?')) {
            const parts = hash.split('?');
            viewName = parts[0];
            const query = parts[1];
            query.split('&').forEach(p => {
                const kv = p.split('=');
                params[decodeURIComponent(kv[0])] = decodeURIComponent(kv[1]);
            });
        }
        this.currentViewName = viewName;

        this.appContainer.innerHTML = '';
        const isAuthView = (viewName === 'login' || viewName === 'complete-registration');
        this.nav.style.display = isAuthView ? 'none' : 'block';
        if (this.headerActions) {
            this.headerActions.style.display = isAuthView ? 'none' : 'flex';
        }

        // Update active nav link
        document.querySelectorAll('.nav-link').forEach(link => {
            link.classList.remove('active');
            if (link.getAttribute('href') === `#${viewName}`) {
                link.classList.add('active');
            }
        });
        this.nav.querySelectorAll('.nav-group').forEach(group => {
            group.hidden = !group.querySelector('li:not([hidden])');
        });

        let view;
        switch (viewName) {
            case 'login':
                view = new LoginView(this);
                break;
            case 'dashboard':
                view = new DashboardView(this);
                break;
            case 'presence':
                view = new PresenceView(this);
                break;
            case 'my-times':
                view = new MyTimesView(this);
                break;
            case 'my-absences':
                view = new MyAbsencesView(this);
                break;
            case 'my-periods':
                view = new MyPeriodsView(this);
                break;
            case 'approvals':
                view = new ApprovalsView(this);
                break;
            case 'reports':
                view = new ReportsView(this);
                break;
            case 'employees':
                view = new EmployeesView(this);
                break;
            case 'teams':
                view = new TeamsView(this);
                break;
            case 'locations':
                view = new LocationsView(this);
                break;
            case 'absence-types':
                view = new AbsenceTypesView(this);
                break;
            case 'holiday-calendars':
                view = new HolidayCalendarsView(this);
                break;
            case 'schedule-templates':
                view = new ScheduleTemplatesView(this);
                break;
            case 'configuration':
                view = new ConfigurationView(this);
                break;
            case 'users':
                view = new UsersView(this);
                break;
            case 'audit-log':
                view = new AuditLogView(this);
                break;
            case 'schedules':
                view = new SchedulesView(this);
                break;
            case 'complete-registration':
                view = new CompleteRegistrationView(this);
                break;
            default:
                this.appContainer.innerHTML = `<h2>404</h2><p>View ${viewName} not found.</p>`;
                return;
        }

        const renderedView = await view.render(params);
        this.appContainer.appendChild(renderedView);
    }
}

const app = new ChronivaroApp();
app.start();
