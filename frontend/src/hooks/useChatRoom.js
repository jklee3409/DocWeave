import { useState, useEffect } from 'react';
import { toast } from 'react-hot-toast';
import { api } from '../services/api';

export function useChatRoom(isAuthenticated) {
    const [rooms, setRooms] = useState([]);
    const [currentRoomId, setCurrentRoomId] = useState(null);
    const [messages, setMessages] = useState([]);
    const [isLoading, setIsLoading] = useState(false);
    const [uploadStatus, setUploadStatus] = useState(null);
    const [isProcessing, setIsProcessing] = useState(false);

    useEffect(() => {
        if (isAuthenticated) {
            fetchRooms();
        }
    }, [isAuthenticated]);

    useEffect(() => {
        if (currentRoomId) {
            fetchMessages(currentRoomId);
        } else {
            setMessages([]);
            setIsProcessing(false);
        }
    }, [currentRoomId]);

    useEffect(() => {
        let intervalId;
        if (currentRoomId && !isLoading) {
            intervalId = setInterval(() => {
                fetchMessages(currentRoomId, true);
            }, 3000);
        }
        return () => clearInterval(intervalId);
    }, [currentRoomId, isLoading]);

    useEffect(() => {
        if (messages.length > 0) {
            const lastMsg = messages[messages.length - 1];
            if (lastMsg.role === 'ai') {
                if (lastMsg.content.includes('분석을 시작합니다')) {
                    setIsProcessing(true);
                    setUploadStatus('uploading');
                } else if (lastMsg.content.includes('분석이 완료되었습니다')) {
                    setIsProcessing(false);
                    setUploadStatus('done');
                    setTimeout(() => setUploadStatus(null), 3000);
                } else if (lastMsg.content.includes('오류가 발생했습니다')) {
                    setIsProcessing(false);
                    setUploadStatus(null);
                    toast.error("문서 분석 중 오류가 발생했습니다.");
                }
            } else {
                setIsProcessing(false);
            }
        }
    }, [messages]);

    const fetchRooms = async () => {
        try {
            const data = await api.fetchRooms();
            setRooms(data);
        } catch (err) {
            // 404는 채팅방이 없는 초기 상태일 수 있으므로 에러 토스트를 띄우지 않음
            if (err.message.includes('CHATROOM_NOT_FOUND')) {
                setRooms([]);
            } else {
                toast.error(err.message || "채팅방 목록을 불러오지 못했습니다.");
            }
        }
    };

    const fetchMessages = async (roomId, isSilent = false) => {
        try {
            if (isLoading && isSilent) return;

            const data = await api.fetchMessages(roomId);

            setMessages(prev => {
                if (isSilent && prev.length === data.length) return prev;
                return data;
            });
        } catch (err) {
            toast.error(err.message || "메시지를 불러오는 중 오류가 발생했습니다.");
        }
    };

    const moveRoomToTop = (roomId) => {
        setRooms(prevRooms => {
            const targetRoom = prevRooms.find(r => r.id === roomId);
            const otherRooms = prevRooms.filter(r => r.id !== roomId);
            return targetRoom ? [targetRoom, ...otherRooms] : prevRooms;
        });
    };

    return {
        rooms,
        setRooms,
        currentRoomId,
        setCurrentRoomId,
        messages,
        setMessages,
        isLoading,
        setIsLoading,
        uploadStatus,
        setUploadStatus,
        isProcessing,
        setIsProcessing,
        fetchMessages,
        moveRoomToTop
    };
}
