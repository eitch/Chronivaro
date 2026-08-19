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
import CompleteRegistrationView from './pages/CompleteRegistrationView.js';

class ChronivaroApp {
    constructor() {
        this.appContainer = document.getElementById('app');
        this.nav = document.querySelector('header nav');

        window.addEventListener('unauthorized', () => {
            this.navigate('login');
        });

        window.addEventListener('hashchange', () => {
            this.route();
        });

        document.getElementById('logout-link').addEventListener('click', (e) => {
            e.preventDefault();
            AuthApi.logout();
            this.navigate('login');
        });
    }

    start() {
        this.route();
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
		this.showView(hash);
	}

	updateNavigation() {
		const roles = AuthApi.getRoles();
		this.nav.querySelectorAll('li[data-roles]').forEach(li => {
			const requiredRoles = li.getAttribute('data-roles').split(',');
			const hasRole = requiredRoles.some(role => roles.includes(role));
			li.style.display = hasRole ? 'block' : 'none';
		});
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

        this.appContainer.innerHTML = '';
        this.nav.style.display = (viewName === 'login' || viewName === 'complete-registration') ? 'none' : 'block';

        // Update active nav link
        document.querySelectorAll('.nav-link').forEach(link => {
            link.classList.remove('active');
            if (link.getAttribute('href') === `#${viewName}`) {
                link.classList.add('active');
            }
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
