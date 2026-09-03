import I18n from '../i18n/I18n.js';
import Format from './Format.js';

/**
 * DatePicker utility and polyfill for interactive date selection.
 * Supports both standard Chronivaro formatted date strings (DD.MM.YYYY)
 * and HTML5 date inputs (YYYY-MM-DD), with full i18n support.
 */

export function isDateInputSupported() {
	try {
		const input = document.createElement('input');
		input.setAttribute('type', 'date');
		if (input.type !== 'date') return false;
		input.value = 'invalid-date-val';
		return input.value !== 'invalid-date-val';
	} catch (e) {
		return false;
	}
}

let activePopover = null;
let activeInput = null;
let activeWrapper = null;
let repositionHandler = null;

function positionPopover(popover, targetElement) {
	if (!popover || !targetElement) return;
	const rect = targetElement.getBoundingClientRect();
	const popoverRect = popover.getBoundingClientRect();
	const popoverHeight = popoverRect.height || 320;
	const popoverWidth = popoverRect.width || 280;

	const spaceBelow = window.innerHeight - rect.bottom;
	const spaceAbove = rect.top;

	let top;
	if (spaceBelow < popoverHeight && spaceAbove > spaceBelow) {
		// Place above
		top = rect.top - popoverHeight - 4;
	} else {
		// Place below
		top = rect.bottom + 4;
	}

	let left = rect.left;
	if (left + popoverWidth > window.innerWidth - 8) {
		left = Math.max(8, window.innerWidth - popoverWidth - 8);
	}
	if (left < 8) {
		left = 8;
	}

	popover.style.top = `${Math.round(top)}px`;
	popover.style.left = `${Math.round(left)}px`;
}

function closeActivePopover() {
	if (repositionHandler) {
		window.removeEventListener('scroll', repositionHandler, true);
		window.removeEventListener('resize', repositionHandler);
		repositionHandler = null;
	}
	if (activePopover) {
		activePopover.remove();
		activePopover = null;
		activeInput = null;
		activeWrapper = null;
	}
}

document.addEventListener('click', (e) => {
	if (activePopover && !e.target.closest('.date-picker-popover') && !e.target.closest('.date-picker-wrapper')) {
		closeActivePopover();
	}
});

document.addEventListener('keydown', (e) => {
	if (e.key === 'Escape' && activePopover) {
		closeActivePopover();
	}
});

/**
 * Attaches the DatePicker popover to an input element.
 * @param {HTMLInputElement} input
 * @param {Object} options
 * @returns {HTMLInputElement|null}
 */
export function attachDatePicker(input, options = {}) {
	if (!input) return null;
	if (input.dataset.datePickerAttached === 'true') return input;

	input.dataset.datePickerAttached = 'true';
	input.autocomplete = 'off';

	const isIso = input.getAttribute('type') === 'date' || input.dataset.format === 'iso' || input.dataset.format === 'YYYY-MM-DD';
	if (!isIso) {
		input.placeholder = input.placeholder || 'DD.MM.YYYY';
		input.setAttribute('data-type', 'date');
	}

	let wrapper = input.closest('.date-picker-wrapper');
	if (!wrapper) {
		wrapper = document.createElement('span');
		wrapper.className = 'date-picker-wrapper';
		input.parentNode.insertBefore(wrapper, input);
		wrapper.appendChild(input);
	}

	let toggleBtn = wrapper.querySelector('.date-picker-toggle');
	if (!toggleBtn) {
		toggleBtn = document.createElement('button');
		toggleBtn.type = 'button';
		toggleBtn.className = 'date-picker-toggle';
		toggleBtn.setAttribute('tabindex', '-1');
		const prompt = I18n.t('common.chooseDate') || 'Choose Date';
		toggleBtn.setAttribute('aria-label', prompt);
		toggleBtn.setAttribute('title', prompt);
		toggleBtn.innerHTML = '📅';
		wrapper.appendChild(toggleBtn);
	}

	const openPicker = () => {
		if (activeInput === input && activePopover) {
			closeActivePopover();
			return;
		}
		closeActivePopover();
		showPickerPopover(input, wrapper, isIso);
	};

	toggleBtn.addEventListener('click', (e) => {
		e.preventDefault();
		e.stopPropagation();
		openPicker();
	});

	input.addEventListener('click', (e) => {
		if (!activePopover || activeInput !== input) {
			openPicker();
		}
	});

	return input;
}

function showPickerPopover(input, wrapper, isIso) {
	const now = new Date();
	const currentRealYear = now.getFullYear();
	const currentRealMonth = now.getMonth() + 1; // 1-12
	const currentRealDay = now.getDate();

	let selectedYear = null;
	let selectedMonth = null;
	let selectedDay = null;

	let displayedYear = currentRealYear;
	let displayedMonth = currentRealMonth;

	const val = (input.value || '').trim();
	if (val) {
		const iso = Format.toIsoDate(val);
		if (iso && /^\d{4}-\d{2}-\d{2}$/.test(iso)) {
			const [yStr, mStr, dStr] = iso.split('-');
			const py = parseInt(yStr, 10);
			const pm = parseInt(mStr, 10);
			const pd = parseInt(dStr, 10);
			if (!isNaN(py) && py >= 1900 && py <= 9999 && pm >= 1 && pm <= 12 && pd >= 1 && pd <= 31) {
				selectedYear = py;
				selectedMonth = pm;
				selectedDay = pd;
				displayedYear = py;
				displayedMonth = pm;
			}
		}
	}

	const popover = document.createElement('div');
	popover.className = 'date-picker-popover';
	popover.setAttribute('role', 'dialog');
	popover.setAttribute('aria-label', I18n.t('common.chooseDate') || 'Choose Date');

	const renderContent = () => {
		const prevYearLabel = I18n.t('common.prevYear') || 'Previous Year';
		const nextYearLabel = I18n.t('common.nextYear') || 'Next Year';
		const prevMonthLabel = I18n.t('common.prevMonth') || 'Previous Month';
		const nextMonthLabel = I18n.t('common.nextMonth') || 'Next Month';
		const todayLabel = I18n.t('common.today') || 'Today';
		const clearLabel = I18n.t('common.clear') || 'Clear';
		const closeLabel = I18n.t('common.close') || 'Close';

		const monthName = I18n.t(`months.${displayedMonth}`) ||
			['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'][displayedMonth - 1];

		// Weekday labels (Monday to Sunday)
		const weekdayHeaders = [1, 2, 3, 4, 5, 6, 7].map(dayNum => {
			const shortDay = I18n.t(`shortWeekdays.${dayNum}`) || ['Mo', 'Tu', 'We', 'Th', 'Fr', 'Sa', 'Su'][dayNum - 1];
			const fullDay = I18n.t(`weekdays.${dayNum}`) || shortDay;
			return `<div class="date-picker-weekday" title="${fullDay}">${shortDay}</div>`;
		}).join('');

		// Calculation of days grid
		const daysInMonth = new Date(displayedYear, displayedMonth, 0).getDate();
		const daysInPrevMonth = new Date(displayedYear, displayedMonth - 1, 0).getDate();
		// First day of displayed month in week: 0=Sunday, 1=Monday, ..., 6=Saturday
		const firstDaySundayBased = new Date(displayedYear, displayedMonth - 1, 1).getDay();
		// Convert to Monday=0, Tuesday=1, ..., Sunday=6
		const firstWeekdayIndex = (firstDaySundayBased + 6) % 7;

		let dayCellsHtml = '';

		// Leading days from previous month
		for (let i = firstWeekdayIndex - 1; i >= 0; i--) {
			const dayNum = daysInPrevMonth - i;
			const prevM = displayedMonth === 1 ? 12 : displayedMonth - 1;
			const prevY = displayedMonth === 1 ? displayedYear - 1 : displayedYear;
			dayCellsHtml += `<button type="button" class="date-picker-day-btn other-month" data-year="${prevY}" data-month="${prevM}" data-day="${dayNum}">${dayNum}</button>`;
		}

		// Days of current month
		for (let d = 1; d <= daysInMonth; d++) {
			const isSelected = selectedYear === displayedYear && selectedMonth === displayedMonth && selectedDay === d;
			const isToday = currentRealYear === displayedYear && currentRealMonth === displayedMonth && currentRealDay === d;
			// Check weekend
			const dayOfWeek = (new Date(displayedYear, displayedMonth - 1, d).getDay() + 6) % 7;
			const isWeekend = dayOfWeek === 5 || dayOfWeek === 6;

			const classes = [
				'date-picker-day-btn',
				isSelected ? 'selected' : '',
				isToday ? 'today' : '',
				isWeekend ? 'weekend' : ''
			].filter(Boolean).join(' ');

			dayCellsHtml += `<button type="button" class="${classes}" data-year="${displayedYear}" data-month="${displayedMonth}" data-day="${d}">${d}</button>`;
		}

		// Trailing days from next month
		const totalRendered = firstWeekdayIndex + daysInMonth;
		const remainingSlots = (totalRendered % 7 === 0) ? 0 : (7 - (totalRendered % 7));
		for (let d = 1; d <= remainingSlots; d++) {
			const nextM = displayedMonth === 12 ? 1 : displayedMonth + 1;
			const nextY = displayedMonth === 12 ? displayedYear + 1 : displayedYear;
			dayCellsHtml += `<button type="button" class="date-picker-day-btn other-month" data-year="${nextY}" data-month="${nextM}" data-day="${d}">${d}</button>`;
		}

		popover.innerHTML = `
			<div class="date-picker-header">
				<button type="button" class="date-picker-nav-btn prev-year" title="${prevYearLabel}" aria-label="${prevYearLabel}">&laquo;</button>
				<button type="button" class="date-picker-nav-btn prev-month" title="${prevMonthLabel}" aria-label="${prevMonthLabel}">&lsaquo;</button>
				<div class="date-picker-title-wrapper">
					<span class="date-picker-month-title">${monthName}</span>
					<span class="date-picker-year-title">${displayedYear}</span>
				</div>
				<button type="button" class="date-picker-nav-btn next-month" title="${nextMonthLabel}" aria-label="${nextMonthLabel}">&rsaquo;</button>
				<button type="button" class="date-picker-nav-btn next-year" title="${nextYearLabel}" aria-label="${nextYearLabel}">&raquo;</button>
			</div>
			<div class="date-picker-weekdays">
				${weekdayHeaders}
			</div>
			<div class="date-picker-grid">
				${dayCellsHtml}
			</div>
			<div class="date-picker-footer">
				<button type="button" class="date-picker-today-btn">${todayLabel}</button>
				<button type="button" class="date-picker-clear-btn">${clearLabel}</button>
				<button type="button" class="date-picker-close-btn">${closeLabel}</button>
			</div>
		`;

		const prevYearBtn = popover.querySelector('.prev-year');
		const prevMonthBtn = popover.querySelector('.prev-month');
		const nextMonthBtn = popover.querySelector('.next-month');
		const nextYearBtn = popover.querySelector('.next-year');

		const todayBtn = popover.querySelector('.date-picker-today-btn');
		const clearBtn = popover.querySelector('.date-picker-clear-btn');
		const closeBtn = popover.querySelector('.date-picker-close-btn');

		prevYearBtn.addEventListener('click', (e) => {
			e.stopPropagation();
			displayedYear--;
			renderContent();
		});

		nextYearBtn.addEventListener('click', (e) => {
			e.stopPropagation();
			displayedYear++;
			renderContent();
		});

		prevMonthBtn.addEventListener('click', (e) => {
			e.stopPropagation();
			displayedMonth--;
			if (displayedMonth < 1) {
				displayedMonth = 12;
				displayedYear--;
			}
			renderContent();
		});

		nextMonthBtn.addEventListener('click', (e) => {
			e.stopPropagation();
			displayedMonth++;
			if (displayedMonth > 12) {
				displayedMonth = 1;
				displayedYear++;
			}
			renderContent();
		});

		todayBtn.addEventListener('click', (e) => {
			e.stopPropagation();
			const y = now.getFullYear();
			const m = now.getMonth() + 1;
			const d = now.getDate();
			formatAndSetDate(input, y, m, d, isIso);
			closeActivePopover();
		});

		clearBtn.addEventListener('click', (e) => {
			e.stopPropagation();
			setValueAndTrigger(input, '');
			closeActivePopover();
		});

		closeBtn.addEventListener('click', (e) => {
			e.stopPropagation();
			closeActivePopover();
		});

		popover.querySelectorAll('.date-picker-day-btn').forEach(btn => {
			btn.addEventListener('click', (e) => {
				e.stopPropagation();
				const y = parseInt(btn.getAttribute('data-year'), 10);
				const m = parseInt(btn.getAttribute('data-month'), 10);
				const d = parseInt(btn.getAttribute('data-day'), 10);
				formatAndSetDate(input, y, m, d, isIso);
				closeActivePopover();
			});
		});

		positionPopover(popover, wrapper || input);
	};

	renderContent();
	document.body.appendChild(popover);
	activePopover = popover;
	activeInput = input;
	activeWrapper = wrapper;

	positionPopover(popover, wrapper || input);

	repositionHandler = () => {
		if (activePopover && activeInput) {
			if (!document.body.contains(activeInput)) {
				closeActivePopover();
			} else {
				positionPopover(activePopover, activeWrapper || activeInput);
			}
		}
	};
	window.addEventListener('scroll', repositionHandler, true);
	window.addEventListener('resize', repositionHandler);
}

function formatAndSetDate(input, year, month, day, isIso) {
	const pad = (n) => String(n).padStart(2, '0');
	let formatted;
	if (isIso) {
		formatted = `${year}-${pad(month)}-${pad(day)}`;
	} else {
		formatted = `${pad(day)}.${pad(month)}.${year}`;
	}
	setValueAndTrigger(input, formatted);
}

function setValueAndTrigger(input, value) {
	input.value = value;
	input.dispatchEvent(new Event('input', { bubbles: true }));
	input.dispatchEvent(new Event('change', { bubbles: true }));
}

function isDateInputCandidate(el) {
	if (!el || el.tagName !== 'INPUT') return false;

	const type = (el.getAttribute('type') || el.type || '').toLowerCase();
	if (['hidden', 'checkbox', 'radio', 'button', 'submit', 'reset', 'file', 'range', 'color', 'password', 'number', 'email', 'tel', 'url'].includes(type)) {
		return false;
	}

	if (type === 'date' || el.dataset.type === 'date' || el.classList.contains('date-input') || el.classList.contains('date-picker-input')) {
		return true;
	}

	const placeholder = el.getAttribute('placeholder') || '';
	if (/DD\.MM\.YYYY|YYYY-MM-DD/i.test(placeholder)) {
		return true;
	}

	const id = (el.id || '').toLowerCase();
	const name = (el.name || '').toLowerCase();

	if (id.includes('time') || name.includes('time') || id.includes('comment') || id.includes('search')) {
		return false;
	}

	const dateRegex = /(?:^|[-_])(?:date|start|end|from|to|birthdate|birthday|joindate|exitdate|validfrom|hcal)(?:[-_]|$)/i;
	return dateRegex.test(id) || dateRegex.test(name);
}

export function initDatePickers(root = document, options = {}) {
	const rootEl = (root && root.querySelectorAll) ? root : document;
	const inputs = Array.from(rootEl.querySelectorAll('input')).filter(isDateInputCandidate);
	inputs.forEach(input => attachDatePicker(input, options));
}

let isObserverStarted = false;

export function initAllDatePickers(options = {}) {
	initDatePickers(document, options);

	if (!isObserverStarted && typeof MutationObserver !== 'undefined' && document.body) {
		isObserverStarted = true;
		const observer = new MutationObserver((mutations) => {
			for (const mutation of mutations) {
				for (const addedNode of mutation.addedNodes) {
					if (addedNode.nodeType === Node.ELEMENT_NODE) {
						initDatePickers(addedNode, options);
					}
				}
			}
		});
		observer.observe(document.body, { childList: true, subtree: true });
	}
}

export default class DatePicker {
	static isSupported() {
		return isDateInputSupported();
	}

	static attach(input, options = {}) {
		return attachDatePicker(input, options);
	}

	static init(root = document, options = {}) {
		return initDatePickers(root, options);
	}

	static initAll(options = {}) {
		return initAllDatePickers(options);
	}
}
