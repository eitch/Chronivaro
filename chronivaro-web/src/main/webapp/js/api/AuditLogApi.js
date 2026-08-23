import Rest from '../utils/Rest.js';

export default class AuditLogApi {
    static async getAuditLogs({ offset, limit, entityType, entityId, username, action, from, to } = {}) {
        const params = new URLSearchParams();
        if (offset !== null && offset !== undefined) params.append('offset', offset);
        if (limit !== null && limit !== undefined) params.append('limit', limit);
        if (entityType) params.append('entityType', entityType);
        if (entityId) params.append('entityId', entityId);
        if (username) params.append('username', username);
        if (action) params.append('action', action);
        if (from) params.append('from', from);
        if (to) params.append('to', to);
        const q = params.toString();
        const url = `rest/chronivaro/v1/admin/audit-logs${q ? `?${q}` : ''}`;
        return await Rest.get(url);
    }
}
