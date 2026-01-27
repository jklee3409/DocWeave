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
        // 성공 응답의 경우, 서버의 BaseResponseDto 형식에 따라 data 필드를 직접 반환
        return response.data.data;
    },
    async (error) => {
        const originalRequest = error.config;

        // 401 에러 및 토큰 재발급 로직
        if (error.response?.status === 401 && !originalRequest._retry) {
            originalRequest._retry = true;

            try {
                const tokenResponse = await authService.refreshToken();
                authService.saveTokens(tokenResponse);

                originalRequest.headers.Authorization = `Bearer ${tokenResponse.accessToken}`;
                return apiClient(originalRequest);
            } catch (refreshError) {
                authService.logout();
                window.location.href = '/login';
                return Promise.reject(refreshError);
            }
        }

        // 서버에서 정의한 에러 응답 형식 처리
        if (error.response?.data && error.response.data.data?.detailMessage) {
            const errorData = error.response.data.data;
            const errorMessage = `[${errorData.statusCodeName}] ${errorData.detailMessage}`;
            // UI에 표시하기 좋은 형태로 에러 메시지를 가공하여 reject
            return Promise.reject(new Error(errorMessage));
        }

        // 그 외 네트워크 에러나 다른 형식의 에러
        return Promise.reject(error);
    }
);

export default apiClient;
