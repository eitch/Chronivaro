import Rest from '../utils/Rest.js';

export default class PresenceApi {

    static async getPresence(teamId, locationId) {
        let url = 'rest/chronivaro/v1/presence';
        const params = [];
        if (teamId) params.push(`teamId=${encodeURIComponent(teamId)}`);
        if (locationId) params.push(`locationId=${encodeURIComponent(locationId)}`);
        if (params.length > 0) url += '?' + params.join('&');
        return await Rest.get(url);
    }
}
