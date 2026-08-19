export default class Rest {

    static get(url, customHeaders = {}) {
        return Rest.fetch(url, 'GET', null, customHeaders);
    }

    static post(url, body, customHeaders = {}) {
        return Rest.fetch(url, 'POST', body, customHeaders);
    }

    static put(url, body, customHeaders = {}) {
        return Rest.fetch(url, 'PUT', body, customHeaders);
    }

    static delete(url, customHeaders = {}) {
        return Rest.fetch(url, 'DELETE', null, customHeaders);
    }

    static async fetch(url, method, body, customHeaders = {}) {
        const headers = {
            'Content-Type': 'application/json',
            'Accept': 'application/json',
            ...customHeaders
        };

        const authToken = localStorage.getItem('authToken');
        if (authToken) {
            headers['Authorization'] = authToken;
        }

        const options = {
            method,
            headers
        };

        if (body) {
            options.body = JSON.stringify(body);
        }

        const response = await fetch(url, options);

        if (response.status === 401) {
            localStorage.removeItem('authToken');
            window.dispatchEvent(new CustomEvent('unauthorized'));
            throw new Error('Unauthorized');
        }

        if (!response.ok) {
            const error = await response.json().catch(() => ({msg: response.statusText}));
            throw new Error(error.message || error.msg || response.statusText);
        }

        if (response.status === 204) {
            return null;
        }

        return response.json();
    }
}
