/**
 * Chronivaro Internationalization (i18n) Engine.
 *
 * Implements client-side translation lookup, parameterized formatting,
 * dynamic language bundle loading, and the defined resolution priority chain:
 * 1. Explicit selection (UI switcher / login screen)
 * 2. Browser LocalStorage ('chronivaro_lang')
 * 3. Strolch User.locale
 * 4. Configured defaultLanguage (from Branding / Global Configuration)
 */

export class I18nEngine {
	static STORAGE_KEY = 'chronivaro_lang';
	static DEFAULT_LANGUAGE = 'de';
	static SUPPORTED_LANGUAGES = ['de', 'en'];

	constructor() {
		this.currentLanguage = I18nEngine.DEFAULT_LANGUAGE;
		this.configuredDefaultLanguage = I18nEngine.DEFAULT_LANGUAGE;
		this.bundles = new Map();
		this.listeners = new Set();
		this.initialized = false;
	}

	/**
	 * Normalizes a language tag to supported base code (e.g. 'de-CH' -> 'de', 'EN-us' -> 'en').
	 * @param {string} tag
	 * @returns {string}
	 */
	normalizeLanguage(tag) {
		if (!tag || typeof tag !== 'string') return '';
		const trimmed = tag.trim().toLowerCase();
		const base = trimmed.split(/[-_]/)[0];
		if (I18nEngine.SUPPORTED_LANGUAGES.includes(base)) {
			return base;
		}
		return '';
	}

	/**
	 * Resolves the effective language based on the 4-step priority chain:
	 * 1. explicit choice
	 * 2. localStorage
	 * 3. Strolch User.locale
	 * 4. configured defaultLanguage
	 */
	resolveLanguage({ explicitLanguage, userLocale, defaultLanguage } = {}) {
		if (explicitLanguage) {
			const norm = this.normalizeLanguage(explicitLanguage);
			if (norm) return norm;
		}

		try {
			const stored = localStorage.getItem(I18nEngine.STORAGE_KEY);
			if (stored) {
				const norm = this.normalizeLanguage(stored);
				if (norm) return norm;
			}
		} catch (e) {
			// localStorage might be unavailable in restricted environments
		}

		if (userLocale) {
			const norm = this.normalizeLanguage(userLocale);
			if (norm) return norm;
		}

		if (defaultLanguage) {
			const norm = this.normalizeLanguage(defaultLanguage);
			if (norm) return norm;
		}

		if (this.configuredDefaultLanguage) {
			const norm = this.normalizeLanguage(this.configuredDefaultLanguage);
			if (norm) return norm;
		}

		return I18nEngine.DEFAULT_LANGUAGE;
	}

	/**
	 * Initializes the i18n engine with optional default language and user locale.
	 */
	async init({ defaultLanguage, userLocale, explicitLanguage } = {}) {
		if (defaultLanguage) {
			const norm = this.normalizeLanguage(defaultLanguage);
			if (norm) {
				this.configuredDefaultLanguage = norm;
			}
		}

		const resolvedLang = this.resolveLanguage({ explicitLanguage, userLocale, defaultLanguage });
		await this.setLanguage(resolvedLang, false);
		this.initialized = true;
		return this.currentLanguage;
	}

	/**
	 * Sets the active language, loads dictionary bundle, persists to localStorage, and notifies listeners.
	 * @param {string} lang
	 * @param {boolean} [persist=true]
	 */
	async setLanguage(lang, persist = true) {
		const normalized = this.normalizeLanguage(lang) || this.configuredDefaultLanguage || I18nEngine.DEFAULT_LANGUAGE;
		await this.loadBundle(normalized);

		// Also ensure fallback base bundle is loaded if needed
		if (normalized !== this.configuredDefaultLanguage && this.configuredDefaultLanguage) {
			await this.loadBundle(this.configuredDefaultLanguage);
		}

		this.currentLanguage = normalized;

		if (persist) {
			try {
				localStorage.setItem(I18nEngine.STORAGE_KEY, normalized);
			} catch (e) {
				// ignore storage errors
			}
		}

		this.notifyListeners(normalized);
		return normalized;
	}

	/**
	 * Gets the current active language code.
	 * @returns {string}
	 */
	getLanguage() {
		return this.currentLanguage || I18nEngine.DEFAULT_LANGUAGE;
	}

	/**
	 * Sets the configured global default language.
	 * @param {string} lang
	 */
	setDefaultLanguage(lang) {
		const norm = this.normalizeLanguage(lang);
		if (norm) {
			this.configuredDefaultLanguage = norm;
		}
	}

	/**
	 * Loads a translation bundle for the given language.
	 * @param {string} lang
	 */
	async loadBundle(lang) {
		if (this.bundles.has(lang)) {
			return this.bundles.get(lang);
		}

		try {
			// Resolve URL relative to the webapp root or current module
			let url = `i18n/${lang}.json`;
			if (typeof window !== 'undefined' && window.location) {
				const basePath = window.location.pathname.substring(0, window.location.pathname.lastIndexOf('/') + 1);
				url = `${basePath}i18n/${lang}.json`;
			}

			const response = await fetch(url);
			if (!response.ok) {
				throw new Error(`Failed to load translation bundle: ${url} (status ${response.status})`);
			}
			const data = await response.json();
			this.bundles.set(lang, data);
			return data;
		} catch (err) {
			console.warn(`Could not load i18n bundle for '${lang}'`, err);
			return null;
		}
	}

	/**
	 * Registers a preloaded dictionary directly into the bundle cache.
	 * Useful for testing and offline fallback.
	 * @param {string} lang
	 * @param {object} dictionary
	 */
	registerBundle(lang, dictionary) {
		const norm = this.normalizeLanguage(lang) || lang;
		this.bundles.set(norm, dictionary);
	}

	/**
	 * Resolves a nested key in an object using dot-notation.
	 * @param {object} obj
	 * @param {string} path
	 * @returns {string|undefined}
	 */
	lookupKey(obj, path) {
		if (!obj || typeof obj !== 'object' || !path) return undefined;
		if (typeof obj[path] === 'string') return obj[path];

		const parts = path.split('.');
		let curr = obj;
		for (const part of parts) {
			if (curr && typeof curr === 'object' && part in curr) {
				curr = curr[part];
			} else {
				return undefined;
			}
		}
		return typeof curr === 'string' ? curr : undefined;
	}

	/**
	 * Translates a given key with optional parameters and fallback.
	 * Fallback chain: currentLanguage -> configuredDefaultLanguage -> defaultValue -> key
	 *
	 * @param {string} key
	 * @param {object|Array} [params]
	 * @param {string} [defaultValue]
	 * @returns {string}
	 */
	t(key, params = null, defaultValue = null) {
		if (!key) return '';

		let template = undefined;

		// 1. Try current language bundle
		const currentBundle = this.bundles.get(this.currentLanguage);
		if (currentBundle) {
			template = this.lookupKey(currentBundle, key);
		}

		// 2. Try configured default language bundle
		if (template === undefined && this.configuredDefaultLanguage && this.configuredDefaultLanguage !== this.currentLanguage) {
			const defaultBundle = this.bundles.get(this.configuredDefaultLanguage);
			if (defaultBundle) {
				template = this.lookupKey(defaultBundle, key);
			}
		}

		// 3. Fallback to English bundle if still not found and not already checked
		if (template === undefined && this.currentLanguage !== 'en' && this.configuredDefaultLanguage !== 'en') {
			const enBundle = this.bundles.get('en');
			if (enBundle) {
				template = this.lookupKey(enBundle, key);
			}
		}

		// 4. Fallback to defaultValue or key
		if (template === undefined) {
			template = defaultValue !== null && defaultValue !== undefined ? defaultValue : key;
		}

		// Interpolate parameters: {param} or {0}
		return this.interpolate(template, params);
	}

	/**
	 * Checks if a translation key exists in current or fallback bundles.
	 * @param {string} key
	 * @returns {boolean}
	 */
	has(key) {
		if (!key) return false;
		const currentBundle = this.bundles.get(this.currentLanguage);
		if (currentBundle && this.lookupKey(currentBundle, key) !== undefined) {
			return true;
		}
		const defaultBundle = this.bundles.get(this.configuredDefaultLanguage);
		if (defaultBundle && this.lookupKey(defaultBundle, key) !== undefined) {
			return true;
		}
		return false;
	}

	/**
	 * Replaces placeholders like '{name}' or '{0}' with values from params.
	 * @param {string} text
	 * @param {object|Array} params
	 * @returns {string}
	 */
	interpolate(text, params) {
		if (typeof text !== 'string') return String(text);
		if (!params || typeof params !== 'object') return text;

		return text.replace(/\{([a-zA-Z0-9_-]+)\}/g, (match, paramKey) => {
			if (paramKey in params && params[paramKey] !== undefined && params[paramKey] !== null) {
				return String(params[paramKey]);
			}
			return match;
		});
	}

	/**
	 * Formats minutes into localized duration string (e.g. '7h 30m').
	 * @param {number} minutes
	 * @returns {string}
	 */
	formatDuration(minutes) {
		if (minutes === undefined || minutes === null) return '';
		const h = Math.floor(Math.abs(minutes) / 60);
		const m = Math.abs(minutes) % 60;
		const sign = minutes < 0 ? '-' : '';
		return `${sign}${h}h ${m}m`;
	}

	/**
	 * Subscribes a listener to language change events.
	 * @param {Function} callback (newLanguage) => void
	 * @returns {Function} unsubscribe function
	 */
	onLanguageChange(callback) {
		if (typeof callback === 'function') {
			this.listeners.add(callback);
			return () => this.listeners.delete(callback);
		}
		return () => {};
	}

	/**
	 * Notifies all registered listeners of a language change.
	 * @param {string} lang
	 */
	notifyListeners(lang) {
		for (const listener of this.listeners) {
			try {
				listener(lang);
			} catch (err) {
				console.error('Error in i18n change listener', err);
			}
		}
	}
}

// Export singleton instance as default and named export
const I18n = new I18nEngine();
export default I18n;
