import axios from 'axios';

const API_BASE_URL = 'http://localhost:8080/api/doc';

export const api = {
    async fetchRooms() {
        const response = await axios.get(`${API_BASE_URL}/rooms`);
        return response.data.data;
    },

    async fetchMessages(roomId) {
        const response = await axios.get(`${API_BASE_URL}/rooms/${roomId}/messages`);
        return response.data.data;
    },

    async uploadFile(roomId, formData) {
        if (roomId) {
            await axios.post(`${API_BASE_URL}/rooms/${roomId}/files`, formData, {
                headers: { 'Content-Type': 'multipart/form-data' }
            });
            return null;
        } else {
            const response = await axios.post(`${API_BASE_URL}/rooms`, formData, {
                headers: { 'Content-Type': 'multipart/form-data' }
            });
            return response.data.data;
        }
    },

    async sendMessage(roomId, message) {
        const response = await axios.post(`${API_BASE_URL}/rooms/${roomId}/chat`, {
            message
        });
        return response.data;
    },

    async deleteRoom(roomId) {
        await axios.delete(`${API_BASE_URL}/rooms/${roomId}`);
    }
};
