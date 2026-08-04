import AuthApi from './api/AuthApi.js';
import LoginView from './pages/LoginView.js';
import DashboardView from './pages/DashboardView.js';
import MyTimesView from './pages/MyTimesView.js';

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
		let hash = window.location.hash.substring(1) || 'dashboard';

		if (!AuthApi.isLoggedIn() && hash !== 'login') {
			this.navigate('login');
			return;
		}

		this.showView(hash);
	}

	navigate(page) {
		window.location.hash = page;
	}

	async showView(viewName) {
		this.appContainer.innerHTML = '';
		this.nav.style.display = viewName === 'login' ? 'none' : 'block';

		let view;
		switch (viewName) {
			case 'login':
				view = new LoginView(this);
				break;
			case 'dashboard':
				view = new DashboardView(this);
				break;
			case 'my-times':
				view = new MyTimesView(this);
				break;
			default:
				this.appContainer.innerHTML = `<h2>404</h2><p>View ${viewName} not found.</p>`;
				return;
		}

		const renderedView = await view.render();
		this.appContainer.appendChild(renderedView);
	}
}

const app = new ChronivaroApp();
app.start();
