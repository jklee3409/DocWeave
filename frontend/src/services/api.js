import apiClient from './apiClient';

export const api = {
    async fetchRooms() {
        return apiClient.get(`/doc/rooms`);
    },

    async fetchMessages(roomId) {
        return apiClient.get(`/doc/rooms/${roomId}/messages`);
    },

    async uploadFile(roomId, formData) {
        if (roomId) {
            await apiClient.post(`/doc/rooms/${roomId}/files`, formData, {
                headers: { 'Content-Type': 'multipart/form-data' }
            });
            return null;
        } else {
            return apiClient.post(`/doc/rooms`, formData, {
                headers: { 'Content-Type': 'multipart/form-data' }
            });
        }
    },

    async sendMessage(roomId, message) {
        return apiClient.post(`/doc/rooms/${roomId}/chat`, {
            message
        });
    },

    async deleteRoom(roomId) {
        await apiClient.delete(`/doc/rooms/${roomId}`);
    }
};
