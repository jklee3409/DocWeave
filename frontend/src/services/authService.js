import axios from 'axios';

const AUTH_API_BASE_URL = 'http://localhost:8080/api/auth';
const API_BASE_URL = 'http://localhost:8080/api';

const authApiClient = axios.create({
    baseURL: AUTH_API_BASE_URL,
});

authApiClient.interceptors.response.use(
    (response) => response.data.data,
    (error) => {
        if (error.response?.data && error.response.data.data?.detailMessage) {
            const errorData = error.response.data.data;
            const errorMessage = `[${errorData.statusCodeName}] ${errorData.detailMessage}`;
            return Promise.reject(new Error(errorMessage));
        }
        return Promise.reject(error);
    }
);

export const authService = {
    async login(email, password) {
        const tokenData = await authApiClient.post(`/login`, { email, password });
        this.saveTokens(tokenData);
        return tokenData;
    },

    async signup(email, password, name) {
        const tokenData = await authApiClient.post(`/signup`, { email, password, name });
        this.saveTokens(tokenData);
        return tokenData;
    },

    async logout() {
        const token = this.getAccessToken();
        if (token) {
            try {
                await axios.post(`${API_BASE_URL}/auth/logout`, {}, {
                    headers: { 'Authorization': `Bearer ${token}` }
                });
            } catch (error) {
                console.error("Logout API call failed, proceeding with client-side logout.", error);
            }
        }
        this.clearTokens();
    },

    async refreshToken() {
        const refreshToken = localStorage.getItem('refreshToken');
        if (!refreshToken) {
            throw new Error('No refresh token');
        }
        const tokenData = await authApiClient.post(`/refresh`, { refreshToken });
        this.saveTokens(tokenData);
        return tokenData;
    },

    saveTokens(tokenResponse) {
        if (!tokenResponse.accessToken || !tokenResponse.refreshToken) {
            return;
        }
        localStorage.setItem('accessToken', tokenResponse.accessToken);
        localStorage.setItem('refreshToken', tokenResponse.refreshToken);
        if (tokenResponse.userId && tokenResponse.email && tokenResponse.name) {
            localStorage.setItem('user', JSON.stringify({
                userId: tokenResponse.userId,
                email: tokenResponse.email,
                name: tokenResponse.name
            }));
        }
    },

    clearTokens() {
        localStorage.removeItem('accessToken');
        localStorage.removeItem('refreshToken');
        localStorage.removeItem('user');
        window.dispatchEvent(new Event('auth:logout'));
    },

    getAccessToken() {
        return localStorage.getItem('accessToken');
    },

    getUser() {
        const user = localStorage.getItem('user');
        return user ? JSON.parse(user) : null;
    },

    isAuthenticated() {
        return !!this.getAccessToken();
    }
};