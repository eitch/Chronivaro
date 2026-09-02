import AuthApi from '../api/AuthApi.js';
import EmployeeApi from '../api/EmployeeApi.js';
import TeamApi from '../api/TeamApi.js';
import LocationApi from '../api/LocationApi.js';
import AbsenceApi from '../api/AbsenceApi.js';
import AbsenceTypeApi from '../api/AbsenceTypeApi.js';
import OnCallPeriodApi from '../api/OnCallPeriodApi.js';
import ReportApi from '../api/ReportApi.js';
import NotificationDialog from '../utils/NotificationDialog.js';
import MonthPicker from '../utils/MonthPicker.js';
import I18n from '../i18n/I18n.js';

export default class AbsenceCalendarView {
    constructor(app) {
        this.app = app;
        const now = new Date();
        this.currentYear = now.getFullYear();
        this.currentMonth = now.getMonth() + 1;
        this.viewMode = 'timeline'; // 'timeline' or 'monthGrid'

        this.teams = [];
        this.locations = [];
        this.employees = [];
        this.absenceTypes = [];
        this.absences = [];
        this.onCallPeriods = [];

        this.filterTeamId = '';
        this.filterLocationId = '';
        this.filterEmployeeId = '';
        this.filterAbsenceTypeCode = '';
        this.showOnCall = true;

        this.currentUserEmployeeId = null;
        this.isManager = AuthApi.hasRole('Supervisor') || AuthApi.hasRole('HR')
                || AuthApi.hasRole('Administrator') || AuthApi.hasRole('StrolchAdmin');
    }

    async render(params = {}) {
        if (params.year) this.currentYear = parseInt(params.year, 10);
        if (params.month) this.currentMonth = parseInt(params.month, 10);
        if (params.view) this.viewMode = params.view;

        const container = document.createElement('div');
        container.id = 'absence-calendar-view';
        container.className = 'absence-calendar-container';

        container.innerHTML = `
            <div class="view-header calendar-header">
                <div>
                    <h2 id="calendar-title">${I18n.t('calendar.title')}</h2>
                    <p class="text-muted" style="margin: 0.25rem 0 0 0; font-size: 0.9rem;">${I18n.t('calendar.subtitle')}</p>
                </div>
                <div class="actions calendar-top-actions" style="display: flex; gap: 0.5rem;">
                    ${this.isManager ? `
                    <button type="button" id="calendar-new-oncall-btn" class="secondary-btn" style="display: inline-flex; align-items: center; gap: 0.35rem;">
                        <span class="icon" aria-hidden="true">📞</span> ${I18n.t('calendar.newOnCallPeriod')}
                    </button>
                    ` : ''}
                    <button type="button" id="calendar-new-absence-btn" class="primary-btn" style="display: inline-flex; align-items: center; gap: 0.35rem;">
                        <span class="icon" aria-hidden="true">➕</span> ${I18n.t('calendar.newAbsence')}
                    </button>
                </div>
            </div>

            <!-- Calendar Control Toolbar -->
            <div class="card calendar-controls-card" style="margin-bottom: 1.25rem; padding: 1rem 1.25rem;">
                <div class="calendar-toolbar" style="display: flex; flex-wrap: wrap; justify-content: space-between; align-items: center; gap: 1rem;">
                    <!-- Month Navigation -->
                    <div class="calendar-nav-group" style="display: flex; align-items: center; gap: 0.5rem;">
                        <button type="button" id="cal-prev-btn" class="secondary-btn" title="${I18n.t('calendar.previousMonth')}">&larr; ${I18n.t('common.prev')}</button>
                        <div id="cal-month-picker-container">
                            <input type="month" id="cal-month-picker" value="${this.currentYear}-${String(this.currentMonth).padStart(2, '0')}" style="font-weight: 600; font-size: 0.95rem; text-align: center;">
                        </div>
                        <button type="button" id="cal-next-btn" class="secondary-btn" title="${I18n.t('calendar.nextMonth')}">${I18n.t('common.next')} &rarr;</button>
                        <button type="button" id="cal-today-btn" class="secondary-btn" style="margin-left: 0.25rem;">${I18n.t('calendar.today')}</button>
                    </div>

                    <!-- View Mode Toggle & On-Call Toggle -->
                    <div class="calendar-view-toggle" style="display: flex; align-items: center; gap: 1rem;">
                        <label style="display: flex; align-items: center; gap: 0.4rem; font-size: 0.85rem; cursor: pointer; font-weight: 500;">
                            <input type="checkbox" id="cal-toggle-oncall" ${this.showOnCall ? 'checked' : ''}>
                            <span>${I18n.t('calendar.showOnCall')}</span>
                        </label>
                        <div class="btn-group" role="group" aria-label="Calendar view switcher">
                            <button type="button" id="view-mode-timeline" class="btn ${this.viewMode === 'timeline' ? 'btn-active' : 'secondary-btn'}">${I18n.t('calendar.timelineView')}</button>
                            <button type="button" id="view-mode-month" class="btn ${this.viewMode === 'monthGrid' ? 'btn-active' : 'secondary-btn'}">${I18n.t('calendar.monthGridView')}</button>
                        </div>
                    </div>
                </div>

                <!-- Filters -->
                <div class="calendar-filters-row" style="display: flex; flex-wrap: wrap; gap: 1rem; align-items: flex-end; margin-top: 1rem; padding-top: 1rem; border-top: 1px solid var(--border-color);">
                    <div class="filter-group">
                        <label for="cal-filter-team" style="font-size: 0.85rem; font-weight: 500;">${I18n.t('calendar.filterTeam')}:</label>
                        <select id="cal-filter-team" style="min-width: 150px;">
                            <option value="">${I18n.t('calendar.allTeams')}</option>
                        </select>
                    </div>
                    <div class="filter-group">
                        <label for="cal-filter-location" style="font-size: 0.85rem; font-weight: 500;">${I18n.t('calendar.filterLocation')}:</label>
                        <select id="cal-filter-location" style="min-width: 150px;">
                            <option value="">${I18n.t('calendar.allLocations')}</option>
                        </select>
                    </div>
                    <div class="filter-group">
                        <label for="cal-filter-employee" style="font-size: 0.85rem; font-weight: 500;">${I18n.t('calendar.filterEmployee')}:</label>
                        <select id="cal-filter-employee" style="min-width: 180px;">
                            <option value="">${I18n.t('calendar.allEmployees')}</option>
                        </select>
                    </div>
                    <div class="filter-group">
                        <label for="cal-filter-type" style="font-size: 0.85rem; font-weight: 500;">${I18n.t('calendar.filterAbsenceType')}:</label>
                        <select id="cal-filter-type" style="min-width: 160px;">
                            <option value="">${I18n.t('calendar.allTypes')}</option>
                        </select>
                    </div>
                    <div class="filter-group">
                        <button type="button" id="cal-filter-reset" class="secondary-btn" style="padding: 0.4rem 0.75rem;">${I18n.t('common.reset')}</button>
                    </div>
                </div>
            </div>

            <!-- Main Calendar Display -->
            <div id="calendar-content-container" class="card" style="padding: 1rem; overflow: hidden; min-height: 400px;">
                <div class="loading-spinner" style="text-align: center; padding: 2rem;">${I18n.t('common.loading')}</div>
            </div>

            <!-- Legend -->
            <div class="card calendar-legend-card" style="margin-top: 1rem; padding: 0.75rem 1.25rem;">
                <div class="calendar-legend" style="display: flex; flex-wrap: wrap; gap: 1.5rem; align-items: center; font-size: 0.85rem;">
                    <span style="font-weight: 600;">${I18n.t('calendar.legend')}:</span>
                    <div style="display: flex; align-items: center; gap: 0.4rem;">
                        <span class="legend-badge badge-approved" style="display: inline-block; width: 12px; height: 12px; border-radius: 3px; background-color: var(--primary-color, #2563eb);"></span>
                        <span>${I18n.t('calendar.approved')}</span>
                    </div>
                    <div style="display: flex; align-items: center; gap: 0.4rem;">
                        <span class="legend-badge badge-submitted" style="display: inline-block; width: 12px; height: 12px; border-radius: 3px; background-color: #f59e0b; border: 1px dashed #d97706;"></span>
                        <span>${I18n.t('calendar.submitted')}</span>
                    </div>
                    <div style="display: flex; align-items: center; gap: 0.4rem;">
                        <span class="legend-badge badge-draft" style="display: inline-block; width: 12px; height: 12px; border-radius: 3px; background-color: #9ca3af; opacity: 0.7;"></span>
                        <span>${I18n.t('calendar.draft')}</span>
                    </div>
                    <div style="display: flex; align-items: center; gap: 0.4rem;">
                        <span class="legend-badge badge-oncall" style="display: inline-block; width: 12px; height: 12px; border-radius: 3px; background-color: #fef08a; border: 1px solid #eab308;"></span>
                        <span>${I18n.t('calendar.onCallBadge')}</span>
                    </div>
                    <div style="display: flex; align-items: center; gap: 0.4rem;">
                        <span class="legend-badge badge-weekend" style="display: inline-block; width: 12px; height: 12px; border-radius: 3px; background-color: rgba(0,0,0,0.06);"></span>
                        <span>${I18n.t('calendar.weekend')}</span>
                    </div>
                </div>
            </div>

            <!-- Modals Container -->
            <div id="calendar-modals"></div>
        `;

        this.initEventListeners(container);
        this.loadInitialData(container);

        return container;
    }

    initEventListeners(container) {
        // Top Actions
        const newAbsenceBtn = container.querySelector('#calendar-new-absence-btn');
        if (newAbsenceBtn) {
            newAbsenceBtn.addEventListener('click', () => {
                this.openCreateAbsenceModal(container);
            });
        }

        const newOnCallBtn = container.querySelector('#calendar-new-oncall-btn');
        if (newOnCallBtn) {
            newOnCallBtn.addEventListener('click', () => {
                this.openCreateOnCallModal(container);
            });
        }

        // On-Call Toggle
        const onCallToggle = container.querySelector('#cal-toggle-oncall');
        if (onCallToggle) {
            onCallToggle.addEventListener('change', (e) => {
                this.showOnCall = e.target.checked;
                this.renderCalendarContent(container);
            });
        }

        // Navigation
        const prevBtn = container.querySelector('#cal-prev-btn');
        if (prevBtn) {
            prevBtn.addEventListener('click', () => {
                if (this.currentMonth === 1) {
                    this.currentMonth = 12;
                    this.currentYear--;
                } else {
                    this.currentMonth--;
                }
                this.refreshMonthPicker(container);
                this.loadCalendarData(container);
            });
        }

        const nextBtn = container.querySelector('#cal-next-btn');
        if (nextBtn) {
            nextBtn.addEventListener('click', () => {
                if (this.currentMonth === 12) {
                    this.currentMonth = 1;
                    this.currentYear++;
                } else {
                    this.currentMonth++;
                }
                this.refreshMonthPicker(container);
                this.loadCalendarData(container);
            });
        }

        const todayBtn = container.querySelector('#cal-today-btn');
        if (todayBtn) {
            todayBtn.addEventListener('click', () => {
                const now = new Date();
                this.currentYear = now.getFullYear();
                this.currentMonth = now.getMonth() + 1;
                this.refreshMonthPicker(container);
                this.loadCalendarData(container);
            });
        }

        // View Mode Toggle
        const timelineBtn = container.querySelector('#view-mode-timeline');
        const monthGridBtn = container.querySelector('#view-mode-month');
        if (timelineBtn && monthGridBtn) {
            timelineBtn.addEventListener('click', () => {
                this.viewMode = 'timeline';
                timelineBtn.className = 'btn btn-active';
                monthGridBtn.className = 'btn secondary-btn';
                this.renderCalendarContent(container);
            });
            monthGridBtn.addEventListener('click', () => {
                this.viewMode = 'monthGrid';
                monthGridBtn.className = 'btn btn-active';
                timelineBtn.className = 'btn secondary-btn';
                this.renderCalendarContent(container);
            });
        }

        // Filters
        const teamFilter = container.querySelector('#cal-filter-team');
        if (teamFilter) {
            teamFilter.addEventListener('change', (e) => {
                this.filterTeamId = e.target.value;
                this.populateEmployeeDropdown(container);
                this.loadCalendarData(container);
            });
        }

        const locFilter = container.querySelector('#cal-filter-location');
        if (locFilter) {
            locFilter.addEventListener('change', (e) => {
                this.filterLocationId = e.target.value;
                this.populateEmployeeDropdown(container);
                this.loadCalendarData(container);
            });
        }

        const empFilter = container.querySelector('#cal-filter-employee');
        if (empFilter) {
            empFilter.addEventListener('change', (e) => {
                this.filterEmployeeId = e.target.value;
                this.loadCalendarData(container);
            });
        }

        const typeFilter = container.querySelector('#cal-filter-type');
        if (typeFilter) {
            typeFilter.addEventListener('change', (e) => {
                this.filterAbsenceTypeCode = e.target.value;
                this.loadCalendarData(container);
            });
        }

        const monthInput = container.querySelector('#cal-month-picker');
        if (monthInput) {
            monthInput.addEventListener('change', () => {
                if (monthInput.value && /^\d{4}-\d{2}$/.test(monthInput.value)) {
                    const [yearStr, monthStr] = monthInput.value.split('-');
                    this.currentYear = parseInt(yearStr, 10);
                    this.currentMonth = parseInt(monthStr, 10);
                    this.loadCalendarData(container);
                }
            });
        }

        const resetBtn = container.querySelector('#cal-filter-reset');
        if (resetBtn) {
            resetBtn.addEventListener('click', () => {
                this.filterTeamId = '';
                this.filterLocationId = '';
                this.filterEmployeeId = '';
                this.filterAbsenceTypeCode = '';
                if (teamFilter) teamFilter.value = '';
                if (locFilter) locFilter.value = '';
                if (empFilter) empFilter.value = '';
                if (typeFilter) typeFilter.value = '';
                this.populateEmployeeDropdown(container);
                this.loadCalendarData(container);
            });
        }

        MonthPicker.init(container);
    }

    refreshMonthPicker(container) {
        const monthInput = container.querySelector('#cal-month-picker');
        if (monthInput) {
            monthInput.value = `${this.currentYear}-${String(this.currentMonth).padStart(2, '0')}`;
        }
    }

    async loadInitialData(container) {
        this.refreshMonthPicker(container);

        try {
            // Load current user employee profile
            const currentProfile = await EmployeeApi.getMyProfile().catch(() => null);
            if (currentProfile && currentProfile.id) {
                this.currentUserEmployeeId = currentProfile.id;
            }

            // Load master data in parallel
            const [teamsRes, locsRes, empsRes, typesRes] = await Promise.all([
                TeamApi.getAll().catch(() => []),
                LocationApi.getAll().catch(() => []),
                EmployeeApi.getAll().catch(() => []),
                AbsenceTypeApi.getAll().catch(() => [])
            ]);

            this.teams = Array.isArray(teamsRes) ? teamsRes : (teamsRes.data || []);
            this.locations = Array.isArray(locsRes) ? locsRes : (locsRes.data || []);
            this.employees = Array.isArray(empsRes) ? empsRes : (empsRes.data || []);
            this.absenceTypes = Array.isArray(typesRes) ? typesRes : (typesRes.data || []);

            this.populateFilterDropdowns(container);
            await this.loadCalendarData(container);
        } catch (err) {
            console.error('Failed to load initial calendar data:', err);
            const content = container.querySelector('#calendar-content-container');
            if (content) {
                content.innerHTML = `<div class="error-banner">${I18n.t('common.error')}: ${err.message || err}</div>`;
            }
        }
    }

    populateFilterDropdowns(container) {
        // Teams
        const teamSelect = container.querySelector('#cal-filter-team');
        if (teamSelect) {
            teamSelect.innerHTML = `<option value="">${I18n.t('calendar.allTeams')}</option>`;
            this.teams.forEach(t => {
                const opt = document.createElement('option');
                opt.value = t.id;
                opt.textContent = t.name || t.id;
                teamSelect.appendChild(opt);
            });
        }

        // Locations
        const locSelect = container.querySelector('#cal-filter-location');
        if (locSelect) {
            locSelect.innerHTML = `<option value="">${I18n.t('calendar.allLocations')}</option>`;
            this.locations.forEach(l => {
                const opt = document.createElement('option');
                opt.value = l.id;
                opt.textContent = l.name || l.id;
                locSelect.appendChild(opt);
            });
        }

        // Absence Types
        const typeSelect = container.querySelector('#cal-filter-type');
        if (typeSelect) {
            typeSelect.innerHTML = `<option value="">${I18n.t('calendar.allTypes')}</option>`;
            this.absenceTypes.forEach(t => {
                const opt = document.createElement('option');
                opt.value = t.id;
                opt.textContent = t.name || t.id;
                typeSelect.appendChild(opt);
            });
        }

        this.populateEmployeeDropdown(container);
    }

    populateEmployeeDropdown(container) {
        const empSelect = container.querySelector('#cal-filter-employee');
        if (!empSelect) return;

        let filtered = this.employees;
        if (this.filterTeamId) {
            filtered = filtered.filter(e => e.primaryTeamId === this.filterTeamId);
        }
        if (this.filterLocationId) {
            filtered = filtered.filter(e => e.primaryLocationId === this.filterLocationId);
        }

        empSelect.innerHTML = `<option value="">${I18n.t('calendar.allEmployees')}</option>`;
        filtered.forEach(e => {
            const opt = document.createElement('option');
            opt.value = e.id;
            opt.textContent = `${e.firstname || ''} ${e.lastname || ''}`.trim() || e.name || e.id;
            if (e.id === this.filterEmployeeId) {
                opt.selected = true;
            }
            empSelect.appendChild(opt);
        });
    }

    async loadCalendarData(container) {
        const content = container.querySelector('#calendar-content-container');
        if (content) {
            content.innerHTML = `<div class="loading-spinner" style="text-align: center; padding: 2rem;">${I18n.t('common.loading')}</div>`;
        }

        const daysInMonth = new Date(this.currentYear, this.currentMonth, 0).getDate();
        const from = `${this.currentYear}-${String(this.currentMonth).padStart(2, '0')}-01`;
        const to = `${this.currentYear}-${String(this.currentMonth).padStart(2, '0')}-${String(daysInMonth).padStart(2, '0')}`;

        try {
            // Load absences and on-call periods concurrently
            const absencePromise = ReportApi.getAbsenceReport({
                from,
                to,
                teamId: this.filterTeamId,
                employeeId: this.filterEmployeeId,
                type: this.filterAbsenceTypeCode
            }).catch(async (err) => {
                // If report call fails (e.g. standard employee without team), fallback to personal absences
                if (!this.isManager) {
                    const myAbs = await AbsenceApi.getMyAbsences({ from, to });
                    return (Array.isArray(myAbs) ? myAbs : (myAbs.data || [])).map(a => ({
                        id: a.id,
                        employeeId: this.currentUserEmployeeId,
                        employeeName: 'Me',
                        absenceTypeCode: a.absenceTypeCode,
                        absenceTypeName: a.absenceTypeName || a.absenceTypeCode,
                        start: a.start,
                        end: a.end,
                        durationType: a.durationType,
                        dayPart: a.dayPart,
                        minutes: a.minutes,
                        state: a.state || a.status,
                        comment: a.comment,
                        paid: a.paid
                    }));
                }
                throw err;
            });

            const onCallPromise = (async () => {
                try {
                    if (this.isManager) {
                        return await OnCallPeriodApi.getAdminOnCallPeriods({
                            from,
                            to,
                            employeeId: this.filterEmployeeId || undefined
                        });
                    } else {
                        return await OnCallPeriodApi.getMyOnCallPeriods({ from, to });
                    }
                } catch (e) {
                    console.warn('Could not load on-call periods:', e);
                    return [];
                }
            })();

            const [absences, onCallList] = await Promise.all([absencePromise, onCallPromise]);

            this.absences = Array.isArray(absences) ? absences : (absences.items || []);
            this.onCallPeriods = Array.isArray(onCallList) ? onCallList : (onCallList.data || []);

            this.renderCalendarContent(container);
        } catch (err) {
            console.error('Failed to load absence report for calendar:', err);
            if (content) {
                content.innerHTML = `<div class="error-banner">${I18n.t('common.error')}: ${err.message || err}</div>`;
            }
        }
    }

    renderCalendarContent(container) {
        const content = container.querySelector('#calendar-content-container');
        if (!content) return;

        if (this.viewMode === 'timeline') {
            this.renderTimelineView(container, content);
        } else {
            this.renderMonthGridView(container, content);
        }
    }

    getEffectiveEmployees() {
        let list = this.employees;
        if (this.filterTeamId) {
            list = list.filter(e => e.primaryTeamId === this.filterTeamId);
        }
        if (this.filterLocationId) {
            list = list.filter(e => e.primaryLocationId === this.filterLocationId);
        }
        if (this.filterEmployeeId) {
            list = list.filter(e => e.id === this.filterEmployeeId);
        }

        // If not manager and no employee list loaded, ensure at least current employee is shown
        if (list.length === 0 && this.currentUserEmployeeId) {
            list = [{
                id: this.currentUserEmployeeId,
                name: 'Me',
                firstname: 'Me',
                lastname: ''
            }];
        }
        return list;
    }

    renderTimelineView(container, content) {
        const daysInMonth = new Date(this.currentYear, this.currentMonth, 0).getDate();
        const employees = this.getEffectiveEmployees();
        const now = new Date();
        const isCurrentMonth = (now.getFullYear() === this.currentYear && (now.getMonth() + 1) === this.currentMonth);
        const todayDay = isCurrentMonth ? now.getDate() : -1;

        if (employees.length === 0) {
            content.innerHTML = `<div class="empty-state" style="text-align: center; padding: 2.5rem; color: var(--text-muted);">${I18n.t('calendar.emptyTimeline')}</div>`;
            return;
        }

        // Map absences: empId -> dayNumber -> list of absences
        const absenceMap = new Map();
        // Map on-call periods: empId -> dayNumber -> list of on-call periods
        const onCallMap = new Map();

        for (const emp of employees) {
            absenceMap.set(emp.id, new Map());
            onCallMap.set(emp.id, new Map());
        }

        for (const abs of this.absences) {
            const empId = abs.employeeId;
            let empDays = absenceMap.get(empId);
            if (!empDays) {
                empDays = new Map();
                absenceMap.set(empId, empDays);
            }

            for (let d = 1; d <= daysInMonth; d++) {
                const curStr = `${this.currentYear}-${String(this.currentMonth).padStart(2, '0')}-${String(d).padStart(2, '0')}`;
                if (curStr >= abs.start && curStr <= abs.end) {
                    if (!empDays.has(d)) {
                        empDays.set(d, []);
                    }
                    empDays.get(d).push(abs);
                }
            }
        }

        if (this.showOnCall) {
            for (const oc of this.onCallPeriods) {
                const empId = oc.employeeId;
                let empDays = onCallMap.get(empId);
                if (!empDays) {
                    empDays = new Map();
                    onCallMap.set(empId, empDays);
                }

                for (let d = 1; d <= daysInMonth; d++) {
                    const curStr = `${this.currentYear}-${String(this.currentMonth).padStart(2, '0')}-${String(d).padStart(2, '0')}`;
                    if (curStr >= oc.startDate && curStr <= oc.endDate) {
                        if (!empDays.has(d)) {
                            empDays.set(d, []);
                        }
                        empDays.get(d).push(oc);
                    }
                }
            }
        }

        // Build Matrix HTML
        let tableHtml = `
            <div class="calendar-timeline-scroll-wrapper" style="overflow-x: auto; max-width: 100%;">
                <table class="calendar-timeline-table" style="width: 100%; border-collapse: collapse; min-width: 900px;">
                    <thead>
                        <tr>
                            <th class="cal-col-emp" style="position: sticky; left: 0; z-index: 5; background: var(--card-bg, #fff); min-width: 180px; max-width: 220px; text-align: left; padding: 0.6rem 0.75rem; border-bottom: 2px solid var(--border-color);">${I18n.t('calendar.employee')}</th>
        `;

        const weekdayNames = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];
        for (let d = 1; d <= daysInMonth; d++) {
            const dateObj = new Date(this.currentYear, this.currentMonth - 1, d);
            const dayOfWeek = (dateObj.getDay() + 6) % 7; // 0=Mon, 6=Sun
            const isWeekend = (dayOfWeek === 5 || dayOfWeek === 6);
            const isToday = (d === todayDay);
            const weekendClass = isWeekend ? 'cal-col-weekend' : '';
            const todayClass = isToday ? 'cal-col-today' : '';

            tableHtml += `
                <th class="cal-day-header ${weekendClass} ${todayClass}" style="text-align: center; padding: 0.4rem 0.2rem; min-width: 32px; font-size: 0.75rem; border-bottom: 2px solid var(--border-color); ${isWeekend ? 'background-color: rgba(0,0,0,0.03);' : ''} ${isToday ? 'background-color: rgba(37,99,235,0.08);' : ''}">
                    <div style="font-weight: 400; color: var(--text-muted); font-size: 0.7rem;">${weekdayNames[dayOfWeek]}</div>
                    <div style="font-weight: 700; ${isToday ? 'color: var(--primary-color, #2563eb);' : ''}">${d}</div>
                </th>
            `;
        }

        tableHtml += `
                        </tr>
                    </thead>
                    <tbody>
        `;

        for (const emp of employees) {
            const empName = `${emp.firstname || ''} ${emp.lastname || ''}`.trim() || emp.name || emp.id;
            const team = this.teams.find(t => t.id === emp.primaryTeamId);
            const teamName = team ? team.name : '';
            const empDays = absenceMap.get(emp.id) || new Map();
            const empOnCallDays = onCallMap.get(emp.id) || new Map();

            tableHtml += `
                <tr class="cal-emp-row" data-emp-id="${emp.id}">
                    <td class="cal-emp-cell" style="position: sticky; left: 0; z-index: 4; background: var(--card-bg, #fff); padding: 0.6rem 0.75rem; border-bottom: 1px solid var(--border-color); border-right: 1px solid var(--border-color); white-space: nowrap; overflow: hidden; text-overflow: ellipsis;">
                        <div style="font-weight: 600; font-size: 0.85rem;" title="${empName}">${empName}</div>
                        ${teamName ? `<div style="font-size: 0.75rem; color: var(--text-muted);">${teamName}</div>` : ''}
                    </td>
            `;

            for (let d = 1; d <= daysInMonth; d++) {
                const dateObj = new Date(this.currentYear, this.currentMonth - 1, d);
                const dayOfWeek = (dateObj.getDay() + 6) % 7;
                const isWeekend = (dayOfWeek === 5 || dayOfWeek === 6);
                const isToday = (d === todayDay);
                const dateStr = `${this.currentYear}-${String(this.currentMonth).padStart(2, '0')}-${String(d).padStart(2, '0')}`;
                const absencesOnDay = empDays.get(d) || [];
                const onCallOnDay = empOnCallDays.get(d) || [];

                let cellContent = '';

                // Render Absences
                if (absencesOnDay.length > 0) {
                    for (const abs of absencesOnDay) {
                        const statusClass = this.getStatusClass(abs.state || abs.status);
                        const typeClass = this.getAbsenceTypeClass(abs.absenceTypeCode);
                        const label = this.getAbsenceShortLabel(abs);
                        cellContent += `
                            <div class="cal-badge ${typeClass} ${statusClass}" data-abs-id="${abs.id}" style="padding: 2px 4px; border-radius: 3px; font-size: 0.7rem; font-weight: 500; cursor: pointer; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; margin-bottom: 2px;" title="${abs.absenceTypeName || abs.absenceTypeCode} (${abs.state || 'APPROVED'}) - ${abs.comment || ''}">
                                ${label}
                            </div>
                        `;
                    }
                }

                // Render On-Call Periods
                if (this.showOnCall && onCallOnDay.length > 0) {
                    for (const oc of onCallOnDay) {
                        const timeInfo = (oc.startTime || oc.endTime) ? ` (${oc.startTime || ''}-${oc.endTime || ''})` : '';
                        cellContent += `
                            <div class="cal-badge type-oncall" data-oncall-id="${oc.id}" style="padding: 2px 4px; border-radius: 3px; font-size: 0.7rem; font-weight: 600; cursor: pointer; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; margin-bottom: 2px;" title="📞 ${I18n.t('calendar.onCallBadge')}${timeInfo}: ${oc.startDate} - ${oc.endDate} ${oc.comment ? ' - ' + oc.comment : ''}">
                                📞 ${I18n.t('calendar.onCallBadge')}${oc.startTime ? ' ' + oc.startTime : ''}
                            </div>
                        `;
                    }
                }

                const hasEntries = absencesOnDay.length > 0 || (this.showOnCall && onCallOnDay.length > 0);

                tableHtml += `
                    <td class="cal-day-cell ${isWeekend ? 'cal-cell-weekend' : ''} ${isToday ? 'cal-cell-today' : ''}" data-emp-id="${emp.id}" data-date="${dateStr}" style="padding: 2px; text-align: center; vertical-align: top; border-bottom: 1px solid var(--border-color); border-right: 1px solid var(--border-color); min-height: 44px; height: 44px; position: relative; ${isWeekend ? 'background-color: rgba(0,0,0,0.02);' : ''} ${isToday ? 'background-color: rgba(37,99,235,0.04);' : ''}">
                        <div class="cal-cell-inner" style="height: 100%; display: flex; flex-direction: column; justify-content: center;">
                            ${cellContent}
                            ${!hasEntries ? `<button type="button" class="cal-cell-add-btn" title="${I18n.t('calendar.createAbsencePrompt', { name: empName, date: dateStr })}" style="display: none; background: none; border: none; font-size: 0.8rem; cursor: pointer; color: var(--primary-color, #2563eb);">+</button>` : ''}
                        </div>
                    </td>
                `;
            }

            tableHtml += `
                </tr>
            `;
        }

        tableHtml += `
                    </tbody>
                </table>
            </div>
        `;

        content.innerHTML = tableHtml;

        // Attach interactivity for Absences
        content.querySelectorAll('.cal-badge[data-abs-id]').forEach(badge => {
            badge.addEventListener('click', (e) => {
                e.stopPropagation();
                const absId = badge.getAttribute('data-abs-id');
                const abs = this.absences.find(a => a.id === absId);
                if (abs) {
                    this.openAbsenceDetailsModal(container, abs);
                }
            });
        });

        // Attach interactivity for On-Call Periods
        content.querySelectorAll('.cal-badge[data-oncall-id]').forEach(badge => {
            badge.addEventListener('click', (e) => {
                e.stopPropagation();
                const ocId = badge.getAttribute('data-oncall-id');
                const oc = this.onCallPeriods.find(o => o.id === ocId);
                if (oc) {
                    this.openOnCallDetailsModal(container, oc);
                }
            });
        });

        // Cell hover & direct absence creation
        content.querySelectorAll('.cal-day-cell').forEach(cell => {
            const addBtn = cell.querySelector('.cal-cell-add-btn');
            if (addBtn) {
                cell.addEventListener('mouseenter', () => {
                    addBtn.style.display = 'inline-block';
                });
                cell.addEventListener('mouseleave', () => {
                    addBtn.style.display = 'none';
                });
            }

            cell.addEventListener('click', (e) => {
                if (e.target.closest('.cal-badge')) return;
                const empId = cell.getAttribute('data-emp-id');
                const dateStr = cell.getAttribute('data-date');
                this.openCreateAbsenceModal(container, { employeeId: empId, startDate: dateStr, endDate: dateStr });
            });
        });
    }

    renderMonthGridView(container, content) {
        const daysInMonth = new Date(this.currentYear, this.currentMonth, 0).getDate();
        const firstDayOfWeek = (new Date(this.currentYear, this.currentMonth - 1, 1).getDay() + 6) % 7; // 0=Mon
        const now = new Date();
        const isCurrentMonth = (now.getFullYear() === this.currentYear && (now.getMonth() + 1) === this.currentMonth);
        const todayDay = isCurrentMonth ? now.getDate() : -1;

        // Map absences: dayNumber -> list of absences
        const dayMap = new Map();
        for (const abs of this.absences) {
            for (let d = 1; d <= daysInMonth; d++) {
                const curStr = `${this.currentYear}-${String(this.currentMonth).padStart(2, '0')}-${String(d).padStart(2, '0')}`;
                if (curStr >= abs.start && curStr <= abs.end) {
                    if (!dayMap.has(d)) dayMap.set(d, []);
                    dayMap.get(d).push(abs);
                }
            }
        }

        // Map on-call: dayNumber -> list of on-call
        const onCallDayMap = new Map();
        if (this.showOnCall) {
            for (const oc of this.onCallPeriods) {
                for (let d = 1; d <= daysInMonth; d++) {
                    const curStr = `${this.currentYear}-${String(this.currentMonth).padStart(2, '0')}-${String(d).padStart(2, '0')}`;
                    if (curStr >= oc.startDate && curStr <= oc.endDate) {
                        if (!onCallDayMap.has(d)) onCallDayMap.set(d, []);
                        onCallDayMap.get(d).push(oc);
                    }
                }
            }
        }

        let gridHtml = `
            <div class="calendar-month-grid" style="display: grid; grid-template-columns: repeat(7, 1fr); gap: 1px; background-color: var(--border-color); border: 1px solid var(--border-color); border-radius: 6px; overflow: hidden;">
        `;

        const weekdayLabels = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];
        for (let i = 0; i < 7; i++) {
            gridHtml += `
                <div class="calendar-grid-header" style="background-color: var(--surface-bg, #f8fafc); padding: 0.6rem; text-align: center; font-weight: 600; font-size: 0.85rem;">
                    ${weekdayLabels[i]}
                </div>
            `;
        }

        // Empty cells before first day
        for (let i = 0; i < firstDayOfWeek; i++) {
            gridHtml += `
                <div class="calendar-grid-cell cal-cell-empty" style="background-color: var(--card-bg, #fff); min-height: 100px; opacity: 0.3;"></div>
            `;
        }

        // Days of current month
        for (let d = 1; d <= daysInMonth; d++) {
            const dateObj = new Date(this.currentYear, this.currentMonth - 1, d);
            const dayOfWeek = (dateObj.getDay() + 6) % 7;
            const isWeekend = (dayOfWeek === 5 || dayOfWeek === 6);
            const isToday = (d === todayDay);
            const dateStr = `${this.currentYear}-${String(this.currentMonth).padStart(2, '0')}-${String(d).padStart(2, '0')}`;
            const absencesOnDay = dayMap.get(d) || [];
            const onCallsOnDay = onCallDayMap.get(d) || [];

            let badgesHtml = '';

            // Absences
            for (const abs of absencesOnDay) {
                const statusClass = this.getStatusClass(abs.state || abs.status);
                const typeClass = this.getAbsenceTypeClass(abs.absenceTypeCode);
                const empName = abs.employeeName || abs.employeeId;
                const label = `${empName}: ${this.getAbsenceShortLabel(abs)}`;

                badgesHtml += `
                    <div class="cal-grid-badge ${typeClass} ${statusClass}" data-abs-id="${abs.id}" style="padding: 2px 6px; border-radius: 4px; font-size: 0.75rem; margin-bottom: 3px; cursor: pointer; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;" title="${empName} - ${abs.absenceTypeName || abs.absenceTypeCode} (${abs.state || 'APPROVED'})">
                        ${label}
                    </div>
                `;
            }

            // On-Call Periods
            if (this.showOnCall) {
                for (const oc of onCallsOnDay) {
                    const empName = oc.employeeName || oc.employeeId;
                    const timeInfo = oc.startTime ? ` (${oc.startTime})` : '';
                    badgesHtml += `
                        <div class="cal-grid-badge type-oncall" data-oncall-id="${oc.id}" style="padding: 2px 6px; border-radius: 4px; font-size: 0.75rem; margin-bottom: 3px; cursor: pointer; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;" title="📞 ${empName} - ${I18n.t('calendar.onCallBadge')}${timeInfo}">
                            📞 ${empName}: ${I18n.t('calendar.onCallBadge')}${timeInfo}
                        </div>
                    `;
                }
            }

            const totalCount = absencesOnDay.length + (this.showOnCall ? onCallsOnDay.length : 0);

            gridHtml += `
                <div class="calendar-grid-cell ${isWeekend ? 'cal-cell-weekend' : ''} ${isToday ? 'cal-cell-today' : ''}" data-date="${dateStr}" style="background-color: var(--card-bg, #fff); min-height: 110px; padding: 0.4rem; position: relative; cursor: pointer; ${isWeekend ? 'background-color: rgba(0,0,0,0.02);' : ''} ${isToday ? 'border: 2px solid var(--primary-color, #2563eb);' : ''}">
                    <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 0.25rem;">
                        <span class="cal-grid-day-number" style="font-weight: 700; font-size: 0.85rem; ${isToday ? 'color: var(--primary-color, #2563eb);' : ''}">${d}</span>
                        ${totalCount > 0 ? `<span class="badge" style="font-size: 0.65rem; padding: 1px 4px; background: rgba(0,0,0,0.08); border-radius: 10px;">${totalCount}</span>` : ''}
                    </div>
                    <div class="cal-grid-badges-container" style="max-height: 80px; overflow-y: auto;">
                        ${badgesHtml}
                    </div>
                </div>
            `;
        }

        // Fill trailing empty cells to complete the 7-col grid
        const totalCells = firstDayOfWeek + daysInMonth;
        const trailingCells = (7 - (totalCells % 7)) % 7;
        for (let i = 0; i < trailingCells; i++) {
            gridHtml += `
                <div class="calendar-grid-cell cal-cell-empty" style="background-color: var(--card-bg, #fff); min-height: 100px; opacity: 0.3;"></div>
            `;
        }

        gridHtml += `
            </div>
        `;

        content.innerHTML = gridHtml;

        // Click events on grid badges for absences
        content.querySelectorAll('.cal-grid-badge[data-abs-id]').forEach(badge => {
            badge.addEventListener('click', (e) => {
                e.stopPropagation();
                const absId = badge.getAttribute('data-abs-id');
                const abs = this.absences.find(a => a.id === absId);
                if (abs) {
                    this.openAbsenceDetailsModal(container, abs);
                }
            });
        });

        // Click events on grid badges for on-call
        content.querySelectorAll('.cal-grid-badge[data-oncall-id]').forEach(badge => {
            badge.addEventListener('click', (e) => {
                e.stopPropagation();
                const ocId = badge.getAttribute('data-oncall-id');
                const oc = this.onCallPeriods.find(o => o.id === ocId);
                if (oc) {
                    this.openOnCallDetailsModal(container, oc);
                }
            });
        });

        // Click event on day cell to create absence
        content.querySelectorAll('.calendar-grid-cell:not(.cal-cell-empty)').forEach(cell => {
            cell.addEventListener('click', (e) => {
                if (e.target.closest('.cal-grid-badge')) return;
                const dateStr = cell.getAttribute('data-date');
                this.openCreateAbsenceModal(container, { startDate: dateStr, endDate: dateStr });
            });
        });
    }

    getStatusClass(state) {
        if (!state) return 'badge-approved';
        const st = state.toUpperCase();
        if (st === 'APPROVED') return 'badge-approved';
        if (st === 'SUBMITTED') return 'badge-submitted';
        if (st === 'DRAFT') return 'badge-draft';
        if (st === 'REJECTED') return 'badge-rejected';
        return 'badge-approved';
    }

    getAbsenceTypeClass(typeCode) {
        if (!typeCode) return 'type-other';
        const code = typeCode.toUpperCase();
        if (code.includes('VACATION') || code.includes('FERIEN')) return 'type-vacation';
        if (code.includes('ILLNESS') || code.includes('KRANK')) return 'type-illness';
        if (code.includes('ACCIDENT') || code.includes('UNFALL')) return 'type-accident';
        if (code.includes('TRAINING') || code.includes('WEITERBILDUNG')) return 'type-training';
        if (code.includes('MILITARY')) return 'type-military';
        if (code.includes('OVERTIME') || code.includes('KOMPENSATION')) return 'type-overtime';
        return 'type-other';
    }

    getAbsenceShortLabel(abs) {
        let label = abs.absenceTypeName || abs.absenceTypeCode || 'Absence';
        if (abs.durationType === 'HALF_DAY') {
            const part = abs.dayPart === 'MORNING' ? 'AM' : 'PM';
            label = `½ ${part} ${label}`;
        } else if (abs.durationType === 'HOURS') {
            const hrs = (abs.minutes / 60).toFixed(1);
            label = `${hrs}h ${label}`;
        }
        return label;
    }

    openAbsenceDetailsModal(container, abs) {
        const modalsContainer = container.querySelector('#calendar-modals');
        if (!modalsContainer) return;

        const modal = document.createElement('div');
        modal.className = 'modal-backdrop active';
        modal.innerHTML = `
            <div class="modal-dialog" style="max-width: 500px; width: 90%; background: var(--card-bg, #fff); border-radius: 8px; box-shadow: 0 10px 25px rgba(0,0,0,0.2); overflow: hidden;">
                <div class="modal-header" style="display: flex; justify-content: space-between; align-items: center; padding: 1.25rem 1.5rem; border-bottom: 1px solid var(--border-color);">
                    <h3 style="margin: 0; font-size: 1.15rem;">${I18n.t('calendar.details')}</h3>
                    <button type="button" class="modal-close-btn" style="background: none; border: none; font-size: 1.25rem; cursor: pointer;">&times;</button>
                </div>
                <div class="modal-body" style="padding: 1.25rem 1.5rem; font-size: 0.9rem;">
                    <div style="margin-bottom: 0.75rem;">
                        <span style="font-weight: 600; color: var(--text-muted); display: block; font-size: 0.75rem;">${I18n.t('calendar.employee')}:</span>
                        <span style="font-weight: 600; font-size: 1rem;">${abs.employeeName || abs.employeeId}</span>
                    </div>
                    <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 0.75rem; margin-bottom: 0.75rem;">
                        <div>
                            <span style="font-weight: 600; color: var(--text-muted); display: block; font-size: 0.75rem;">${I18n.t('common.type')}:</span>
                            <span>${abs.absenceTypeName || abs.absenceTypeCode}</span>
                        </div>
                        <div>
                            <span style="font-weight: 600; color: var(--text-muted); display: block; font-size: 0.75rem;">${I18n.t('common.status')}:</span>
                            <span class="badge ${this.getStatusClass(abs.state || abs.status)}" style="padding: 2px 8px; border-radius: 4px; font-size: 0.8rem;">${abs.state || abs.status || 'APPROVED'}</span>
                        </div>
                    </div>
                    <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 0.75rem; margin-bottom: 0.75rem;">
                        <div>
                            <span style="font-weight: 600; color: var(--text-muted); display: block; font-size: 0.75rem;">${I18n.t('common.from')}:</span>
                            <span>${abs.start}</span>
                        </div>
                        <div>
                            <span style="font-weight: 600; color: var(--text-muted); display: block; font-size: 0.75rem;">${I18n.t('common.to')}:</span>
                            <span>${abs.end}</span>
                        </div>
                    </div>
                    <div style="margin-bottom: 0.75rem;">
                        <span style="font-weight: 600; color: var(--text-muted); display: block; font-size: 0.75rem;">${I18n.t('common.duration')}:</span>
                        <span>${abs.durationType || 'FULL_DAY'} ${abs.dayPart ? `(${abs.dayPart})` : ''} ${abs.minutes ? `(${abs.minutes} min)` : ''}</span>
                    </div>
                    ${abs.comment ? `
                    <div style="margin-bottom: 0.75rem;">
                        <span style="font-weight: 600; color: var(--text-muted); display: block; font-size: 0.75rem;">${I18n.t('common.comment')}:</span>
                        <div style="padding: 0.5rem; background: rgba(0,0,0,0.03); border-radius: 4px; font-style: italic;">${abs.comment}</div>
                    </div>
                    ` : ''}
                    ${abs.approvedBy ? `
                    <div style="margin-bottom: 0.75rem; font-size: 0.8rem; color: var(--text-muted);">
                        <span>${I18n.t('common.approved')}: ${abs.approvedBy}</span>
                    </div>
                    ` : ''}
                </div>
                <div class="modal-footer" style="display: flex; justify-content: flex-end; gap: 0.5rem; padding: 1rem 1.5rem; border-top: 1px solid var(--border-color); background: var(--surface-bg, #f8fafc);">
                    <button type="button" class="primary-btn modal-ok-btn">${I18n.t('common.close')}</button>
                </div>
            </div>
        `;

        modalsContainer.appendChild(modal);

        const close = () => {
            if (modal.parentElement) modal.parentElement.removeChild(modal);
        };

        modal.querySelector('.modal-close-btn').addEventListener('click', close);
        modal.querySelector('.modal-ok-btn').addEventListener('click', close);
        modal.addEventListener('click', (e) => {
            if (e.target === modal) close();
        });
    }

    openOnCallDetailsModal(container, oc) {
        const modalsContainer = container.querySelector('#calendar-modals');
        if (!modalsContainer) return;

        const modal = document.createElement('div');
        modal.className = 'modal-backdrop active';
        modal.innerHTML = `
            <div class="modal-dialog" style="max-width: 500px; width: 90%; background: var(--card-bg, #fff); border-radius: 8px; box-shadow: 0 10px 25px rgba(0,0,0,0.2); overflow: hidden;">
                <div class="modal-header" style="display: flex; justify-content: space-between; align-items: center; padding: 1.25rem 1.5rem; border-bottom: 1px solid var(--border-color);">
                    <h3 style="margin: 0; font-size: 1.15rem;">${I18n.t('calendar.onCallDetails')}</h3>
                    <button type="button" class="modal-close-btn" style="background: none; border: none; font-size: 1.25rem; cursor: pointer;">&times;</button>
                </div>
                <div class="modal-body" style="padding: 1.25rem 1.5rem; font-size: 0.9rem;">
                    <div style="margin-bottom: 0.75rem;">
                        <span style="font-weight: 600; color: var(--text-muted); display: block; font-size: 0.75rem;">${I18n.t('calendar.employee')}:</span>
                        <span style="font-weight: 600; font-size: 1rem;">${oc.employeeName || oc.employeeId}</span>
                    </div>
                    <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 0.75rem; margin-bottom: 0.75rem;">
                        <div>
                            <span style="font-weight: 600; color: var(--text-muted); display: block; font-size: 0.75rem;">${I18n.t('common.from')}:</span>
                            <span style="font-weight: 500;">${oc.startDate} ${oc.startTime ? `(${oc.startTime})` : ''}</span>
                        </div>
                        <div>
                            <span style="font-weight: 600; color: var(--text-muted); display: block; font-size: 0.75rem;">${I18n.t('common.to')}:</span>
                            <span style="font-weight: 500;">${oc.endDate} ${oc.endTime ? `(${oc.endTime})` : ''}</span>
                        </div>
                    </div>
                    ${oc.comment ? `
                    <div style="margin-bottom: 0.75rem;">
                        <span style="font-weight: 600; color: var(--text-muted); display: block; font-size: 0.75rem;">${I18n.t('common.comment')}:</span>
                        <div style="padding: 0.5rem; background: rgba(0,0,0,0.03); border-radius: 4px; font-style: italic;">${oc.comment}</div>
                    </div>
                    ` : ''}
                    ${oc.createdBy ? `
                    <div style="margin-bottom: 0.75rem; font-size: 0.8rem; color: var(--text-muted);">
                        <span>${I18n.t('common.createdBy')}: ${oc.createdBy}</span>
                    </div>
                    ` : ''}
                </div>
                <div class="modal-footer" style="display: flex; justify-content: space-between; align-items: center; padding: 1rem 1.5rem; border-top: 1px solid var(--border-color); background: var(--surface-bg, #f8fafc);">
                    <div>
                        ${this.isManager ? `
                        <button type="button" class="danger-btn modal-delete-oc-btn" style="padding: 0.4rem 0.8rem; font-size: 0.85rem;">${I18n.t('common.delete')}</button>
                        ` : ''}
                    </div>
                    <div style="display: flex; gap: 0.5rem;">
                        ${this.isManager ? `
                        <button type="button" class="secondary-btn modal-edit-oc-btn">${I18n.t('common.edit')}</button>
                        ` : ''}
                        <button type="button" class="primary-btn modal-ok-btn">${I18n.t('common.close')}</button>
                    </div>
                </div>
            </div>
        `;

        modalsContainer.appendChild(modal);

        const close = () => {
            if (modal.parentElement) modal.parentElement.removeChild(modal);
        };

        modal.querySelector('.modal-close-btn').addEventListener('click', close);
        modal.querySelector('.modal-ok-btn').addEventListener('click', close);
        modal.addEventListener('click', (e) => {
            if (e.target === modal) close();
        });

        const editBtn = modal.querySelector('.modal-edit-oc-btn');
        if (editBtn) {
            editBtn.addEventListener('click', () => {
                close();
                this.openEditOnCallModal(container, oc);
            });
        }

        const deleteBtn = modal.querySelector('.modal-delete-oc-btn');
        if (deleteBtn) {
            deleteBtn.addEventListener('click', async () => {
                const confirmed = confirm(I18n.t('calendar.deleteOnCallConfirm', {
                    name: oc.employeeName || oc.employeeId,
                    from: oc.startDate,
                    to: oc.endDate
                }));
                if (!confirmed) return;

                try {
                    await OnCallPeriodApi.deleteOnCallPeriod(oc.id, oc.version);
                    close();
                    NotificationDialog.show(I18n.t('calendar.onCallDeleteSuccess'), I18n.t('common.success'));
                    await this.loadCalendarData(container);
                } catch (err) {
                    console.error('Failed to delete on-call period:', err);
                    NotificationDialog.show(err.message || I18n.t('errors.unexpected'), I18n.t('common.error'));
                }
            });
        }
    }

    openCreateOnCallModal(container, initialData = {}) {
        this.openOnCallFormModal(container, null, initialData);
    }

    openEditOnCallModal(container, oc) {
        this.openOnCallFormModal(container, oc);
    }

    openOnCallFormModal(container, existingOc = null, initialData = {}) {
        const modalsContainer = container.querySelector('#calendar-modals');
        if (!modalsContainer) return;

        const isEdit = !!existingOc;
        const modal = document.createElement('div');
        modal.className = 'modal-backdrop active';

        const employees = this.employees.length > 0 ? this.employees : [{
            id: this.currentUserEmployeeId || 'me',
            name: 'Me',
            firstname: 'Me',
            lastname: ''
        }];

        const defaultStart = existingOc ? existingOc.startDate : (initialData.startDate || `${this.currentYear}-${String(this.currentMonth).padStart(2, '0')}-01`);
        const defaultEnd = existingOc ? existingOc.endDate : (initialData.endDate || defaultStart);
        const defaultStartTime = existingOc ? (existingOc.startTime || '') : '08:00';
        const defaultEndTime = existingOc ? (existingOc.endTime || '') : '17:00';
        const defaultComment = existingOc ? (existingOc.comment || '') : '';
        const selectedEmpId = existingOc ? existingOc.employeeId : (initialData.employeeId || this.currentUserEmployeeId || employees[0].id);

        modal.innerHTML = `
            <div class="modal-dialog" style="max-width: 550px; width: 90%; background: var(--card-bg, #fff); border-radius: 8px; box-shadow: 0 10px 25px rgba(0,0,0,0.2); overflow: hidden;">
                <div class="modal-header" style="display: flex; justify-content: space-between; align-items: center; padding: 1.25rem 1.5rem; border-bottom: 1px solid var(--border-color);">
                    <h3 style="margin: 0; font-size: 1.15rem;">${isEdit ? I18n.t('calendar.editOnCallTitle') : I18n.t('calendar.createOnCallTitle')}</h3>
                    <button type="button" class="modal-close-btn" style="background: none; border: none; font-size: 1.25rem; cursor: pointer;">&times;</button>
                </div>
                <form id="oncall-form">
                    <div class="modal-body" style="padding: 1.25rem 1.5rem; display: flex; flex-direction: column; gap: 1rem;">
                        <div id="oncall-error" class="error-banner" style="display: none;"></div>

                        <!-- Employee Selection -->
                        <div class="form-group">
                            <label for="modal-oncall-emp" style="display: block; font-weight: 500; font-size: 0.85rem; margin-bottom: 0.25rem;">${I18n.t('calendar.employee')} *</label>
                            <select id="modal-oncall-emp" ${isEdit ? 'disabled' : 'required'} style="width: 100%; padding: 0.5rem; border: 1px solid var(--border-color); border-radius: 4px;">
                                ${employees.map(e => `
                                    <option value="${e.id}" ${e.id === selectedEmpId ? 'selected' : ''}>
                                        ${`${e.firstname || ''} ${e.lastname || ''}`.trim() || e.name || e.id}
                                    </option>
                                `).join('')}
                            </select>
                        </div>

                        <!-- Quick Presets -->
                        <div class="form-group" style="padding: 0.75rem; background: var(--surface-bg, #f8fafc); border: 1px solid var(--border-color); border-radius: 4px;">
                            <label for="modal-oncall-preset" style="display: block; font-weight: 600; font-size: 0.8rem; margin-bottom: 0.35rem; color: var(--text-color);">${I18n.t('calendar.onCallPreset')}</label>
                            <select id="modal-oncall-preset" style="width: 100%; padding: 0.4rem; border: 1px solid var(--border-color); border-radius: 4px; font-size: 0.85rem;">
                                <option value="custom">${I18n.t('calendar.presetCustom')}</option>
                                <option value="standard_week">${I18n.t('calendar.presetStandardWeek')}</option>
                                <option value="weekend">${I18n.t('calendar.presetWeekend')}</option>
                                <option value="workweek">${I18n.t('calendar.presetWorkweek')}</option>
                            </select>
                        </div>

                        <!-- Dates Row -->
                        <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 1rem;">
                            <div class="form-group">
                                <label for="modal-oncall-start" style="display: block; font-weight: 500; font-size: 0.85rem; margin-bottom: 0.25rem;">${I18n.t('common.from')} *</label>
                                <input type="date" id="modal-oncall-start" value="${defaultStart}" required style="width: 100%; padding: 0.5rem; border: 1px solid var(--border-color); border-radius: 4px;">
                            </div>
                            <div class="form-group">
                                <label for="modal-oncall-end" style="display: block; font-weight: 500; font-size: 0.85rem; margin-bottom: 0.25rem;">${I18n.t('common.to')} *</label>
                                <input type="date" id="modal-oncall-end" value="${defaultEnd}" required style="width: 100%; padding: 0.5rem; border: 1px solid var(--border-color); border-radius: 4px;">
                            </div>
                        </div>

                        <!-- Times Row -->
                        <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 1rem;">
                            <div class="form-group">
                                <label for="modal-oncall-starttime" style="display: block; font-weight: 500; font-size: 0.85rem; margin-bottom: 0.25rem;">${I18n.t('onCall.startTime')}</label>
                                <input type="time" id="modal-oncall-starttime" value="${defaultStartTime}" style="width: 100%; padding: 0.5rem; border: 1px solid var(--border-color); border-radius: 4px;">
                            </div>
                            <div class="form-group">
                                <label for="modal-oncall-endtime" style="display: block; font-weight: 500; font-size: 0.85rem; margin-bottom: 0.25rem;">${I18n.t('onCall.endTime')}</label>
                                <input type="time" id="modal-oncall-endtime" value="${defaultEndTime}" style="width: 100%; padding: 0.5rem; border: 1px solid var(--border-color); border-radius: 4px;">
                            </div>
                        </div>

                        <!-- Comment / Reason -->
                        <div class="form-group">
                            <label for="modal-oncall-comment" style="display: block; font-weight: 500; font-size: 0.85rem; margin-bottom: 0.25rem;">
                                ${I18n.t('common.comment')}
                            </label>
                            <textarea id="modal-oncall-comment" rows="2" style="width: 100%; padding: 0.5rem; border: 1px solid var(--border-color); border-radius: 4px; box-sizing: border-box;">${defaultComment}</textarea>
                        </div>
                    </div>
                    <div class="modal-footer" style="display: flex; justify-content: flex-end; gap: 0.5rem; padding: 1rem 1.5rem; border-top: 1px solid var(--border-color); background: var(--surface-bg, #f8fafc);">
                        <button type="button" class="secondary-btn modal-cancel-btn">${I18n.t('common.cancel')}</button>
                        <button type="submit" class="primary-btn">${I18n.t('common.save')}</button>
                    </div>
                </form>
            </div>
        `;

        modalsContainer.appendChild(modal);

        const form = modal.querySelector('#oncall-form');
        const errorBanner = modal.querySelector('#oncall-error');
        const startInput = modal.querySelector('#modal-oncall-start');
        const endInput = modal.querySelector('#modal-oncall-end');
        const presetSelect = modal.querySelector('#modal-oncall-preset');

        // Preset calculation helper
        const applyPreset = () => {
            const val = presetSelect.value;
            if (val === 'custom') return;

            const baseDate = startInput.value ? new Date(startInput.value) : new Date(this.currentYear, this.currentMonth - 1, 1);
            if (isNaN(baseDate.getTime())) return;

            if (val === 'standard_week') {
                // Find Monday of that week
                const day = (baseDate.getDay() + 6) % 7; // 0=Mon, 6=Sun
                const monday = new Date(baseDate);
                monday.setDate(baseDate.getDate() - day);
                const sunday = new Date(monday);
                sunday.setDate(monday.getDate() + 6);

                startInput.value = monday.toISOString().split('T')[0];
                endInput.value = sunday.toISOString().split('T')[0];
            } else if (val === 'weekend') {
                // Find Saturday of that week
                const day = (baseDate.getDay() + 6) % 7;
                const saturday = new Date(baseDate);
                saturday.setDate(baseDate.getDate() - day + 5);
                const sunday = new Date(saturday);
                sunday.setDate(saturday.getDate() + 1);

                startInput.value = saturday.toISOString().split('T')[0];
                endInput.value = sunday.toISOString().split('T')[0];
            } else if (val === 'workweek') {
                // Find Monday to Friday
                const day = (baseDate.getDay() + 6) % 7;
                const monday = new Date(baseDate);
                monday.setDate(baseDate.getDate() - day);
                const friday = new Date(monday);
                friday.setDate(monday.getDate() + 4);

                startInput.value = monday.toISOString().split('T')[0];
                endInput.value = friday.toISOString().split('T')[0];
            }
        };

        presetSelect.addEventListener('change', applyPreset);

        const close = () => {
            if (modal.parentElement) modal.parentElement.removeChild(modal);
        };

        modal.querySelector('.modal-close-btn').addEventListener('click', close);
        modal.querySelector('.modal-cancel-btn').addEventListener('click', close);
        modal.addEventListener('click', (e) => {
            if (e.target === modal) close();
        });

        form.addEventListener('submit', async (e) => {
            e.preventDefault();
            errorBanner.style.display = 'none';
            errorBanner.textContent = '';

            const empId = modal.querySelector('#modal-oncall-emp').value;
            const startDate = startInput.value;
            const endDate = endInput.value;
            const startTime = modal.querySelector('#modal-oncall-starttime').value.trim() || undefined;
            const endTime = modal.querySelector('#modal-oncall-endtime').value.trim() || undefined;
            const comment = modal.querySelector('#modal-oncall-comment').value.trim() || undefined;

            if (startDate > endDate) {
                errorBanner.textContent = I18n.t('absences.invalidDateRange');
                errorBanner.style.display = 'block';
                return;
            }

            const payload = {
                employeeId: empId,
                startDate,
                startTime,
                endDate,
                endTime,
                comment
            };

            try {
                if (isEdit) {
                    await OnCallPeriodApi.updateOnCallPeriod(existingOc.id, payload, existingOc.version);
                } else {
                    await OnCallPeriodApi.createOnCallPeriod(payload);
                }

                close();
                NotificationDialog.show(I18n.t('calendar.onCallSaveSuccess'), I18n.t('common.success'));
                await this.loadCalendarData(container);
            } catch (err) {
                console.error('Failed to save on-call period:', err);
                errorBanner.textContent = err.message || I18n.t('errors.unexpected');
                errorBanner.style.display = 'block';
            }
        });
    }

    openCreateAbsenceModal(container, initialData = {}) {
        const modalsContainer = container.querySelector('#calendar-modals');
        if (!modalsContainer) return;

        const modal = document.createElement('div');
        modal.className = 'modal-backdrop active';

        const employees = this.employees.length > 0 ? this.employees : [{
            id: this.currentUserEmployeeId || 'me',
            name: 'Me',
            firstname: 'Me',
            lastname: ''
        }];

        const defaultStart = initialData.startDate || `${this.currentYear}-${String(this.currentMonth).padStart(2, '0')}-01`;
        const defaultEnd = initialData.endDate || defaultStart;
        const selectedEmpId = initialData.employeeId || this.currentUserEmployeeId || employees[0].id;

        modal.innerHTML = `
            <div class="modal-dialog" style="max-width: 550px; width: 90%; background: var(--card-bg, #fff); border-radius: 8px; box-shadow: 0 10px 25px rgba(0,0,0,0.2); overflow: hidden;">
                <div class="modal-header" style="display: flex; justify-content: space-between; align-items: center; padding: 1.25rem 1.5rem; border-bottom: 1px solid var(--border-color);">
                    <h3 style="margin: 0; font-size: 1.15rem;">${I18n.t('calendar.createAbsenceTitle')}</h3>
                    <button type="button" class="modal-close-btn" style="background: none; border: none; font-size: 1.25rem; cursor: pointer;">&times;</button>
                </div>
                <form id="create-absence-form">
                    <div class="modal-body" style="padding: 1.25rem 1.5rem; display: flex; flex-direction: column; gap: 1rem;">
                        <div id="create-absence-error" class="error-banner" style="display: none;"></div>

                        <!-- Employee Selection (for Manager) -->
                        <div class="form-group">
                            <label for="modal-absence-emp" style="display: block; font-weight: 500; font-size: 0.85rem; margin-bottom: 0.25rem;">${I18n.t('calendar.employee')} *</label>
                            <select id="modal-absence-emp" ${!this.isManager ? 'disabled' : 'required'} style="width: 100%; padding: 0.5rem; border: 1px solid var(--border-color); border-radius: 4px;">
                                ${employees.map(e => `
                                    <option value="${e.id}" ${e.id === selectedEmpId ? 'selected' : ''}>
                                        ${`${e.firstname || ''} ${e.lastname || ''}`.trim() || e.name || e.id}
                                    </option>
                                `).join('')}
                            </select>
                        </div>

                        <!-- Absence Type -->
                        <div class="form-group">
                            <label for="modal-absence-type" style="display: block; font-weight: 500; font-size: 0.85rem; margin-bottom: 0.25rem;">${I18n.t('common.type')} *</label>
                            <select id="modal-absence-type" required style="width: 100%; padding: 0.5rem; border: 1px solid var(--border-color); border-radius: 4px;">
                                <option value="">${I18n.t('calendar.selectAbsenceType')}</option>
                                ${this.absenceTypes.map(t => `
                                    <option value="${t.id}" data-comment-req="${t.commentRequired ? 'true' : 'false'}">${t.name || t.id}</option>
                                `).join('')}
                            </select>
                        </div>

                        <!-- Dates Row -->
                        <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 1rem;">
                            <div class="form-group">
                                <label for="modal-absence-start" style="display: block; font-weight: 500; font-size: 0.85rem; margin-bottom: 0.25rem;">${I18n.t('common.from')} *</label>
                                <input type="date" id="modal-absence-start" value="${defaultStart}" required style="width: 100%; padding: 0.5rem; border: 1px solid var(--border-color); border-radius: 4px;">
                            </div>
                            <div class="form-group">
                                <label for="modal-absence-end" style="display: block; font-weight: 500; font-size: 0.85rem; margin-bottom: 0.25rem;">${I18n.t('common.to')} *</label>
                                <input type="date" id="modal-absence-end" value="${defaultEnd}" required style="width: 100%; padding: 0.5rem; border: 1px solid var(--border-color); border-radius: 4px;">
                            </div>
                        </div>

                        <!-- Duration Type Row -->
                        <div class="form-group">
                            <label for="modal-absence-duration-type" style="display: block; font-weight: 500; font-size: 0.85rem; margin-bottom: 0.25rem;">${I18n.t('common.duration')} *</label>
                            <select id="modal-absence-duration-type" required style="width: 100%; padding: 0.5rem; border: 1px solid var(--border-color); border-radius: 4px;">
                                <option value="FULL_DAY">${I18n.t('calendar.fullDay')}</option>
                                <option value="HALF_DAY_MORNING">${I18n.t('calendar.halfDayMorning')}</option>
                                <option value="HALF_DAY_AFTERNOON">${I18n.t('calendar.halfDayAfternoon')}</option>
                                <option value="HOURS">${I18n.t('calendar.hours')}</option>
                            </select>
                        </div>

                        <!-- Hours/Minutes Row (Conditional) -->
                        <div id="modal-hours-container" class="form-group" style="display: none;">
                            <label for="modal-absence-hours" style="display: block; font-weight: 500; font-size: 0.85rem; margin-bottom: 0.25rem;">${I18n.t('common.hours')} *</label>
                            <input type="number" id="modal-absence-hours" min="0.5" max="24" step="0.5" value="4.0" style="width: 100%; padding: 0.5rem; border: 1px solid var(--border-color); border-radius: 4px;">
                        </div>

                        <!-- Comment / Reason -->
                        <div class="form-group">
                            <label for="modal-absence-comment" style="display: block; font-weight: 500; font-size: 0.85rem; margin-bottom: 0.25rem;">
                                ${I18n.t('common.comment')} <span id="modal-comment-required-indicator" style="color: var(--danger-color, #dc2626); display: none;">*</span>
                            </label>
                            <textarea id="modal-absence-comment" rows="3" style="width: 100%; padding: 0.5rem; border: 1px solid var(--border-color); border-radius: 4px; box-sizing: border-box;"></textarea>
                        </div>

                        <!-- Direct Approval (Manager only) -->
                        ${this.isManager ? `
                        <div class="form-group" style="padding: 0.75rem; background: rgba(37,99,235,0.04); border: 1px solid var(--border-color); border-radius: 4px;">
                            <label style="display: flex; align-items: center; gap: 0.5rem; font-weight: 500; font-size: 0.85rem; cursor: pointer; margin: 0;">
                                <input type="checkbox" id="modal-direct-approval" checked>
                                <span>${I18n.t('calendar.directApproval')}</span>
                            </label>
                            <span class="text-muted" style="display: block; font-size: 0.75rem; margin-top: 0.25rem; margin-left: 1.5rem;">${I18n.t('calendar.directApprovalDesc')}</span>
                        </div>
                        ` : ''}
                    </div>
                    <div class="modal-footer" style="display: flex; justify-content: flex-end; gap: 0.5rem; padding: 1rem 1.5rem; border-top: 1px solid var(--border-color); background: var(--surface-bg, #f8fafc);">
                        <button type="button" class="secondary-btn modal-cancel-btn">${I18n.t('common.cancel')}</button>
                        ${!this.isManager ? `
                        <button type="button" id="modal-draft-btn" class="secondary-btn">${I18n.t('calendar.saveAsDraft')}</button>
                        ` : ''}
                        <button type="submit" class="primary-btn">${I18n.t('calendar.submitRequest')}</button>
                    </div>
                </form>
            </div>
        `;

        modalsContainer.appendChild(modal);

        const form = modal.querySelector('#create-absence-form');
        const durationSelect = modal.querySelector('#modal-absence-duration-type');
        const hoursContainer = modal.querySelector('#modal-hours-container');
        const typeSelect = modal.querySelector('#modal-absence-type');
        const commentReqIndicator = modal.querySelector('#modal-comment-required-indicator');
        const commentInput = modal.querySelector('#modal-absence-comment');
        const errorBanner = modal.querySelector('#create-absence-error');

        // Dynamic hours visibility
        durationSelect.addEventListener('change', () => {
            if (durationSelect.value === 'HOURS') {
                hoursContainer.style.display = 'block';
            } else {
                hoursContainer.style.display = 'none';
            }
        });

        // Dynamic comment required indicator
        typeSelect.addEventListener('change', () => {
            const selectedOpt = typeSelect.selectedOptions[0];
            const isReq = selectedOpt && selectedOpt.getAttribute('data-comment-req') === 'true';
            commentReqIndicator.style.display = isReq ? 'inline' : 'none';
            if (isReq) {
                commentInput.setAttribute('required', 'true');
            } else {
                commentInput.removeAttribute('required');
            }
        });

        const close = () => {
            if (modal.parentElement) modal.parentElement.removeChild(modal);
        };

        modal.querySelector('.modal-close-btn').addEventListener('click', close);
        modal.querySelector('.modal-cancel-btn').addEventListener('click', close);
        modal.addEventListener('click', (e) => {
            if (e.target === modal) close();
        });

        const submitHandler = async (isDraft = false) => {
            errorBanner.style.display = 'none';
            errorBanner.textContent = '';

            const empId = modal.querySelector('#modal-absence-emp').value;
            const absenceTypeCode = typeSelect.value;
            const start = modal.querySelector('#modal-absence-start').value;
            const end = modal.querySelector('#modal-absence-end').value;
            const durationValue = durationSelect.value;
            const comment = commentInput.value.trim();

            if (!absenceTypeCode) {
                errorBanner.textContent = I18n.t('absences.selectAbsenceTypeError');
                errorBanner.style.display = 'block';
                return;
            }

            if (start > end) {
                errorBanner.textContent = I18n.t('absences.invalidDateRange');
                errorBanner.style.display = 'block';
                return;
            }

            let durationType = 'FULL_DAY';
            let dayPart = null;
            let minutes = 0;

            if (durationValue === 'HALF_DAY_MORNING') {
                durationType = 'HALF_DAY';
                dayPart = 'MORNING';
            } else if (durationValue === 'HALF_DAY_AFTERNOON') {
                durationType = 'HALF_DAY';
                dayPart = 'AFTERNOON';
            } else if (durationValue === 'HOURS') {
                durationType = 'HOURS';
                const hrs = parseFloat(modal.querySelector('#modal-absence-hours').value) || 1;
                minutes = Math.round(hrs * 60);
            }

            const directApprovalCheckbox = modal.querySelector('#modal-direct-approval');
            const isDirectApproval = directApprovalCheckbox ? directApprovalCheckbox.checked : false;

            const payload = {
                absenceTypeCode,
                start,
                end,
                durationType,
                dayPart,
                minutes,
                comment,
                status: isDraft ? 'DRAFT' : (isDirectApproval ? 'APPROVED' : 'SUBMITTED')
            };

            try {
                if (this.isManager && empId !== this.currentUserEmployeeId) {
                    await AbsenceApi.createEmployeeAbsence(empId, payload);
                } else if (this.isManager && isDirectApproval) {
                    await AbsenceApi.createEmployeeAbsence(empId, payload);
                } else {
                    await AbsenceApi.requestAbsence(payload);
                }

                close();
                NotificationDialog.show(I18n.t('calendar.requestSuccess'), I18n.t('common.success'));
                await this.loadCalendarData(container);
            } catch (err) {
                console.error('Failed to create absence:', err);
                errorBanner.textContent = err.message || I18n.t('errors.unexpected');
                errorBanner.style.display = 'block';
            }
        };

        form.addEventListener('submit', (e) => {
            e.preventDefault();
            submitHandler(false);
        });

        const draftBtn = modal.querySelector('#modal-draft-btn');
        if (draftBtn) {
            draftBtn.addEventListener('click', (e) => {
                e.preventDefault();
                submitHandler(true);
            });
        }
    }
}
