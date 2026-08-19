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
        return isNaN(date.getTime()) ? dateStr : date.toLocaleDateString();
    }

    static dateTime(dateStr) {
        if (!dateStr) return '';
        const date = new Date(dateStr);
        return isNaN(date.getTime()) ? dateStr : date.toLocaleString();
    }
}
