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
        const lang = (typeof I18n !== 'undefined' && I18n.getLanguage) ? I18n.getLanguage() : 'de';
        if (isNaN(date.getTime())) return dateStr;
        const pad = (n) => String(n).padStart(2, '0');
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
        const lang = (typeof I18n !== 'undefined' && I18n.getLanguage) ? I18n.getLanguage() : 'de';
        if (isNaN(date.getTime())) return dateStr;
        const pad = (n) => String(n).padStart(2, '0');
        return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
    }
}
