import Rest from '../utils/Rest.js';

export default class TokenApi {

    static async getMyTokens() {
        return await Rest.get('rest/chronivaro/v1/me/tokens');
    }

    static async createMyToken(tokenData) {
        return await Rest.post('rest/chronivaro/v1/me/tokens', tokenData);
    }

    static async deleteMyToken(tokenId) {
        return await Rest.delete(`rest/chronivaro/v1/me/tokens/${encodeURIComponent(tokenId)}`);
    }

    static async getUserTokens(userId) {
        return await Rest.get(`rest/chronivaro/v1/admin/users/${encodeURIComponent(userId)}/tokens`);
    }

    static async deleteUserToken(userId, tokenId) {
        return await Rest.delete(`rest/chronivaro/v1/admin/users/${encodeURIComponent(userId)}/tokens/${encodeURIComponent(tokenId)}`);
    }
}
