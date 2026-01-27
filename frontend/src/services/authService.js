import axios from 'axios';

const AUTH_API_BASE_URL = 'http://localhost:8080/api/auth';
const API_BASE_URL = 'http://localhost:8080/api';

// 인증용 axios 인스턴스 생성
const authApiClient = axios.create({
    baseURL: AUTH_API_BASE_URL,
});

// 인증 응답/에러 처리를 위한 인터셉터
authApiClient.interceptors.response.use(
    (response) => response.data.data, // 성공 시 BaseResponseDto의 data 필드 바로 반환
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
            // 순환 의존성 방지를 위해 apiClient를 사용하지 않고 직접 axios 호출
            try {
                await axios.post(`${API_BASE_URL}/auth/logout`, {}, {
                    headers: { 'Authorization': `Bearer ${token}` }
                });
            } catch (error) {
                 // 에러가 발생하더라도 클라이언트 측에서는 토큰을 지우고 로그아웃 처리
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
            console.error("SaveTokens failed: Invalid token response", tokenResponse);
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
