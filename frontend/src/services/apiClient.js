import axios from 'axios';
import { authService } from './authService';

const API_BASE_URL = 'http://localhost:8080/api';

const apiClient = axios.create({
    baseURL: API_BASE_URL,
});

apiClient.interceptors.request.use(
    (config) => {
        const token = authService.getAccessToken();
        if (token) {
            config.headers.Authorization = `Bearer ${token}`;
        }
        return config;
    },
    (error) => Promise.reject(error)
);

apiClient.interceptors.response.use(
    (response) => {
        return response.data.data;
    },
    async (error) => {
        const originalRequest = error.config;
        const status = error.response?.status;

        if (status === 403) {
            authService.logout();
            return Promise.reject(new Error("USER_LOGOUT"));
        }

        if (status === 401 && !originalRequest._retry) {
            originalRequest._retry = true;

            try {
                const tokenResponse = await authService.refreshToken();
                authService.saveTokens(tokenResponse);

                originalRequest.headers.Authorization = `Bearer ${tokenResponse.accessToken}`;
                return apiClient(originalRequest);
            } catch (refreshError) {
                authService.logout();
                return Promise.reject(new Error("USER_LOGOUT"));
            }
        }

        if (error.response?.data && error.response.data.data?.detailMessage) {
            const errorData = error.response.data.data;
            const errorMessage = `[${errorData.statusCodeName}] ${errorData.detailMessage}`;
            return Promise.reject(new Error(errorMessage));
        }

        return Promise.reject(error);
    }
);

export default apiClient;