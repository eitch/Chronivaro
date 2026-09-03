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
        const lang = (typeof I18n !== 'undefined' && I18n.getLanguage) ? I18n.getLanguage() : 'de';
        const pad = (n) => String(n).padStart(2, '0');
        if (dateStr instanceof Date) {
            if (isNaN(dateStr.getTime())) return '';
            return `${pad(dateStr.getDate())}.${pad(dateStr.getMonth() + 1)}.${dateStr.getFullYear()}`;
        }
        const str = String(dateStr).trim();
        const isoMatch = str.match(/^(\d{4})-(\d{1,2})-(\d{1,2})/);
        if (isoMatch) {
            return `${pad(isoMatch[3])}.${pad(isoMatch[2])}.${isoMatch[1]}`;
        }
        const dmyMatch = str.match(/^(\d{1,2})\.(\d{1,2})\.(\d{4})$/);
        if (dmyMatch) {
            return `${pad(dmyMatch[1])}.${pad(dmyMatch[2])}.${dmyMatch[3]}`;
        }
        const date = new Date(str);
        if (isNaN(date.getTime())) return str;
        if (lang === 'de') {
            return `${pad(date.getDate())}.${pad(date.getMonth() + 1)}.${date.getFullYear()}`;
        }
        return `${pad(date.getDate())}.${pad(date.getMonth() + 1)}.${date.getFullYear()}`;
    }

    static toIsoDate(dateStr) {
        if (!dateStr) return '';
        if (dateStr instanceof Date) {
            if (isNaN(dateStr.getTime())) return '';
            const pad = (n) => String(n).padStart(2, '0');
            return `${dateStr.getFullYear()}-${pad(dateStr.getMonth() + 1)}-${pad(dateStr.getDate())}`;
        }
        const str = String(dateStr).trim();
        if (/^\d{4}-\d{2}-\d{2}$/.test(str)) {
            return str;
        }
        const isoPrefixMatch = str.match(/^(\d{4})-(\d{1,2})-(\d{1,2})/);
        if (isoPrefixMatch) {
            const pad = (n) => String(n).padStart(2, '0');
            return `${isoPrefixMatch[1]}-${pad(isoPrefixMatch[2])}-${pad(isoPrefixMatch[3])}`;
        }
        const dmyMatch = str.match(/^(\d{1,2})[./-](\d{1,2})[./-](\d{2,4})$/);
        if (dmyMatch) {
            const pad = (n) => String(n).padStart(2, '0');
            let y = parseInt(dmyMatch[3], 10);
            if (dmyMatch[3].length === 2) {
                y = y < 70 ? 2000 + y : 1900 + y;
            }
            return `${String(y).padStart(4, '0')}-${pad(dmyMatch[2])}-${pad(dmyMatch[1])}`;
        }
        return '';
    }

    static normalizeDate(dateStr) {
        if (!dateStr) return '';
        const iso = Format.toIsoDate(dateStr);
        if (iso) {
            return Format.date(iso);
        }
        return String(dateStr).trim();
    }

    static isValidDate(dateStr) {
        if (!dateStr) return false;
        const iso = Format.toIsoDate(dateStr);
        if (!iso) return false;
        const [y, m, d] = iso.split('-').map(Number);
        if (isNaN(y) || isNaN(m) || isNaN(d)) return false;
        if (y < 1900 || y > 9999 || m < 1 || m > 12 || d < 1) return false;
        const daysInMonth = new Date(y, m, 0).getDate();
        return d <= daysInMonth;
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
        return `${pad(date.getDate())}.${pad(date.getMonth() + 1)}.${date.getFullYear()} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
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
