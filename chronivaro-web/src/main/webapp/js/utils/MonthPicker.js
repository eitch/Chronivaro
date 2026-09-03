import I18n from '../i18n/I18n.js';

/**
 * MonthPicker utility and polyfill for browsers (such as Mozilla Firefox)
 * that do not natively support HTML5 `<input type="month">`.
 */
export function isMonthInputSupported() {
	try {
		const input = document.createElement('input');
		input.setAttribute('type', 'month');
		if (input.type !== 'month') return false;
		input.value = 'invalid-month-val';
		return input.value !== 'invalid-month-val';
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
	const popoverHeight = popoverRect.height || 220;
	const popoverWidth = popoverRect.width || 250;

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
	if (activePopover && !e.target.closest('.month-picker-popover') && !e.target.closest('.month-picker-wrapper')) {
		closeActivePopover();
	}
});

document.addEventListener('keydown', (e) => {
	if (e.key === 'Escape' && activePopover) {
		closeActivePopover();
	}
});

export function attachMonthPicker(input, options = {}) {
	if (!input) return null;
	if (input.dataset.monthPickerAttached === 'true') return input;

	input.dataset.monthPickerAttached = 'true';
	input.placeholder = input.placeholder || 'YYYY-MM';
	input.pattern = '\\d{4}-\\d{2}';
	input.autocomplete = 'off';
	input.setAttribute('data-type', 'month');

	let wrapper = input.closest('.month-picker-wrapper');
	if (!wrapper) {
		wrapper = document.createElement('span');
		wrapper.className = 'month-picker-wrapper';
		input.parentNode.insertBefore(wrapper, input);
		wrapper.appendChild(input);
	}

	let toggleBtn = wrapper.querySelector('.month-picker-toggle');
	if (!toggleBtn) {
		toggleBtn = document.createElement('button');
		toggleBtn.type = 'button';
		toggleBtn.className = 'month-picker-toggle';
		toggleBtn.setAttribute('tabindex', '-1');
		const prompt = I18n.t('common.chooseMonth') || 'Choose Month';
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
		showPickerPopover(input, wrapper);
	};

	toggleBtn.addEventListener('click', (e) => {
		e.preventDefault();
		e.stopPropagation();
		openPicker();
	});

	input.addEventListener('click', (e) => {
		// Open picker on clicking text field if empty or requested
		if (!activePopover || activeInput !== input) {
			openPicker();
		}
	});

	return input;
}

function showPickerPopover(input, wrapper) {
	const now = new Date();
	const currentRealYear = now.getFullYear();
	const currentRealMonth = now.getMonth() + 1;

	let selectedYear = null;
	let selectedMonth = null;
	let displayedYear = currentRealYear;

	const val = (input.value || '').trim();
	if (val && /^\d{4}-\d{2}$/.test(val)) {
		const [yStr, mStr] = val.split('-');
		const parsedY = parseInt(yStr, 10);
		const parsedM = parseInt(mStr, 10);
		if (!isNaN(parsedY) && parsedY >= 1900 && parsedY <= 9999) {
			selectedYear = parsedY;
			displayedYear = parsedY;
		}
		if (!isNaN(parsedM) && parsedM >= 1 && parsedM <= 12) {
			selectedMonth = parsedM;
		}
	}

	const popover = document.createElement('div');
	popover.className = 'month-picker-popover';
	popover.setAttribute('role', 'dialog');
	popover.setAttribute('aria-label', I18n.t('common.chooseMonth') || 'Choose Month');

	const renderContent = () => {
		const prevLabel = I18n.t('common.prevYear') || 'Previous Year';
		const nextLabel = I18n.t('common.nextYear') || 'Next Year';
		const currentMonthLabel = I18n.t('common.currentMonth') || 'Current Month';
		const closeLabel = I18n.t('common.close') || 'Close';

		popover.innerHTML = `
			<div class="month-picker-header">
				<button type="button" class="month-picker-nav-btn prev-year" title="${prevLabel}" aria-label="${prevLabel}">&laquo;</button>
				<span class="month-picker-year-title">${displayedYear}</span>
				<button type="button" class="month-picker-nav-btn next-year" title="${nextLabel}" aria-label="${nextLabel}">&raquo;</button>
			</div>
			<div class="month-picker-grid">
				${[1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12].map(m => {
					const isSelected = selectedYear === displayedYear && selectedMonth === m;
					const isCurrent = currentRealYear === displayedYear && currentRealMonth === m;
					const shortName = I18n.t(`shortMonths.${m}`) || ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'][m - 1];
					const fullName = I18n.t(`months.${m}`) || shortName;
					const classes = [
						'month-picker-month-btn',
						isSelected ? 'selected' : '',
						isCurrent ? 'current-month' : ''
					].filter(Boolean).join(' ');

					return `<button type="button" class="${classes}" data-month="${m}" title="${fullName} ${displayedYear}">${shortName}</button>`;
				}).join('')}
			</div>
			<div class="month-picker-footer">
				<button type="button" class="month-picker-today-btn">${currentMonthLabel}</button>
				<button type="button" class="month-picker-close-btn">${closeLabel}</button>
			</div>
		`;

		const prevBtn = popover.querySelector('.prev-year');
		const nextBtn = popover.querySelector('.next-year');
		const todayBtn = popover.querySelector('.month-picker-today-btn');
		const closeBtn = popover.querySelector('.month-picker-close-btn');

		prevBtn.addEventListener('click', (e) => {
			e.stopPropagation();
			displayedYear--;
			renderContent();
		});

		nextBtn.addEventListener('click', (e) => {
			e.stopPropagation();
			displayedYear++;
			renderContent();
		});

		todayBtn.addEventListener('click', (e) => {
			e.stopPropagation();
			const y = now.getFullYear();
			const m = String(now.getMonth() + 1).padStart(2, '0');
			setValueAndTrigger(input, `${y}-${m}`);
			closeActivePopover();
		});

		closeBtn.addEventListener('click', (e) => {
			e.stopPropagation();
			closeActivePopover();
		});

		popover.querySelectorAll('.month-picker-month-btn').forEach(btn => {
			btn.addEventListener('click', (e) => {
				e.stopPropagation();
				const m = parseInt(btn.getAttribute('data-month'), 10);
				const formatted = `${displayedYear}-${String(m).padStart(2, '0')}`;
				setValueAndTrigger(input, formatted);
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

function setValueAndTrigger(input, value) {
	input.value = value;
	input.dispatchEvent(new Event('input', { bubbles: true }));
	input.dispatchEvent(new Event('change', { bubbles: true }));
}

export function initMonthPickers(root = document, options = {}) {
	const rootEl = (root && root.querySelectorAll) ? root : document;
	const inputs = Array.from(rootEl.querySelectorAll('input')).filter(el => {
		const typeAttr = el.getAttribute('type');
		return typeAttr === 'month' || el.type === 'month' || el.dataset.type === 'month';
	});
	inputs.forEach(input => attachMonthPicker(input, options));
}

let isObserverStarted = false;

export function initAllMonthPickers(options = {}) {
	initMonthPickers(document, options);

	if (!isObserverStarted && typeof MutationObserver !== 'undefined' && document.body) {
		isObserverStarted = true;
		const observer = new MutationObserver((mutations) => {
			for (const mutation of mutations) {
				for (const addedNode of mutation.addedNodes) {
					if (addedNode.nodeType === Node.ELEMENT_NODE) {
						initMonthPickers(addedNode, options);
					}
				}
			}
		});
		observer.observe(document.body, { childList: true, subtree: true });
	}
}

export default class MonthPicker {
	static isSupported() {
		return isMonthInputSupported();
	}

	static attach(input, options = {}) {
		return attachMonthPicker(input, options);
	}

	static init(root = document, options = {}) {
		return initMonthPickers(root, options);
	}

	static initAll(options = {}) {
		return initAllMonthPickers(options);
	}
}
