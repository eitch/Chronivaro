import I18n from '../i18n/I18n.js';

export default class Format {
    static duration(minutes) {
        if (minutes === undefined || minutes === null) return '';
        const h = Math.floor(Math.abs(minutes) / 60);
        const m = Math.abs(minutes) % 60;
        const sign = minutes < 0 ? '-' : '';
        return `${sign}${h}h ${m}m`;
    }

    static durationDays(minutes, minutesPerDay = 480) {
        if (minutes === undefined || minutes === null) return '';
        const days = (minutes / minutesPerDay).toFixed(1).replace(/\.0$/, '');
        const durationStr = Format.duration(minutes);
        return `${days}d (${durationStr})`;
    }

    static date(dateStr) {
        if (!dateStr) return '';
        const date = new Date(dateStr);
        if (isNaN(date.getTime())) return dateStr;
        const lang = (typeof I18n !== 'undefined' && I18n.getLanguage) ? I18n.getLanguage() : 'de';
        const pad = (n) => String(n).padStart(2, '0');
        if (lang === 'de') {
            return `${pad(date.getDate())}.${pad(date.getMonth() + 1)}.${date.getFullYear()}`;
        }
        return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
    }

    static time(dateStr) {
        if (!dateStr) return '';
        const date = new Date(dateStr);
        if (isNaN(date.getTime())) return dateStr;
        const pad = (n) => String(n).padStart(2, '0');
        return `${pad(date.getHours())}:${pad(date.getMinutes())}`;
    }

    static dateTime(dateStr) {
        if (!dateStr) return '';
        const date = new Date(dateStr);
        if (isNaN(date.getTime())) return dateStr;
        const lang = (typeof I18n !== 'undefined' && I18n.getLanguage) ? I18n.getLanguage() : 'de';
        const pad = (n) => String(n).padStart(2, '0');
        if (lang === 'de') {
            return `${pad(date.getDate())}.${pad(date.getMonth() + 1)}.${date.getFullYear()} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
        }
        return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
    }

    static normalizeTime(timeStr) {
        if (!timeStr) return '';
        const trimmed = String(timeStr).trim();
        const match = trimmed.match(/^(\d{1,2})[:.](\d{2})$/);
        if (match) {
            const h = parseInt(match[1], 10);
            const m = parseInt(match[2], 10);
            if (h >= 0 && h <= 23 && m >= 0 && m <= 59) {
                return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`;
            }
        }
        const matchH = trimmed.match(/^(\d{1,2})$/);
        if (matchH) {
            const h = parseInt(matchH[1], 10);
            if (h >= 0 && h <= 23) {
                return `${String(h).padStart(2, '0')}:00`;
            }
        }
        return trimmed;
    }

    static isValidTime(timeStr) {
        if (!timeStr) return false;
        const normalized = Format.normalizeTime(timeStr);
        return /^([01]\d|2[0-3]):[0-5]\d$/.test(normalized);
    }
}
