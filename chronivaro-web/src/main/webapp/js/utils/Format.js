export default class Format {
    static duration(minutes) {
        if (minutes === undefined || minutes === null) return '';
        const h = Math.floor(Math.abs(minutes) / 60);
        const m = Math.abs(minutes) % 60;
        const sign = minutes < 0 ? '-' : '';
        return `${sign}${h}h ${m}m`;
    }
}
