export default class Rest {

    static get(url) {
        return Rest.fetch(url, 'GET');
    }

    static post(url, body) {
        return Rest.fetch(url, 'POST', body);
    }

    static put(url, body) {
        return Rest.fetch(url, 'PUT', body);
    }

    static delete(url) {
        return Rest.fetch(url, 'DELETE');
    }

    static async fetch(url, method, body) {
        const headers = {
            'Content-Type': 'application/json',
            'Accept': 'application/json'
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
            throw new Error(error.msg || response.statusText);
        }

        if (response.status === 204) {
            return null;
        }

        return response.json();
    }
}
