import { useState, useEffect, useRef } from 'react';
import { toast } from 'react-hot-toast';
import { api } from '../services/api';

export function useChatRoom(isAuthenticated) {
    const [rooms, setRooms] = useState([]);
    const [currentRoomId, setCurrentRoomId] = useState(null);
    const [messages, setMessages] = useState([]);
    const [isLoading, setIsLoading] = useState(false);
    const [uploadStatus, setUploadStatus] = useState(null);
    const [isProcessing, setIsProcessing] = useState(false);
    const lastProcessedMessageRef = useRef(null);

    useEffect(() => {
        if (isAuthenticated) {
            fetchRooms();
        } else {
            setRooms([]);
            setCurrentRoomId(null);
            setMessages([]);
        }
    }, [isAuthenticated]);

    useEffect(() => {
        if (currentRoomId) {
            setIsProcessing(false);
            lastProcessedMessageRef.current = null;
            fetchMessages(currentRoomId);
        } else {
            setMessages([]);
            setIsProcessing(false);
        }
    }, [currentRoomId]);

    useEffect(() => {
        let intervalId;
        if (currentRoomId && isProcessing && !isLoading && isAuthenticated) {
            intervalId = setInterval(() => {
                fetchMessages(currentRoomId, true);
            }, 3000);
        }
        return () => clearInterval(intervalId);
    }, [currentRoomId, isLoading, isProcessing, isAuthenticated]);

    useEffect(() => {
        if (messages.length > 0) {
            const lastMsg = messages[messages.length - 1];
            const lastMsgId = lastMsg.id || messages.length - 1;

            if (lastProcessedMessageRef.current === lastMsgId && !isProcessing) {
                return;
            }

            if (lastMsg.role === 'ai') {
                if (lastMsg.content.includes('분석을 시작합니다')) {
                    if (!isProcessing) {
                        setIsProcessing(true);
                        setUploadStatus('uploading');
                    }
                } else if (lastMsg.content.includes('분석이 완료되었습니다')) {
                    if (isProcessing || uploadStatus !== 'done') {
                        setIsProcessing(false);
                        setUploadStatus('done');
                        toast.success('문서 분석이 완료되었습니다.');
                        setTimeout(() => setUploadStatus(null), 3000);
                        lastProcessedMessageRef.current = lastMsgId;
                    }
                } else if (lastMsg.content.includes('오류가 발생했습니다')) {
                    if (isProcessing) {
                        setIsProcessing(false);
                        setUploadStatus(null);
                        toast.error("문서 분석 중 오류가 발생했습니다.");
                        lastProcessedMessageRef.current = lastMsgId;
                    }
                }
            }
        }
    }, [messages, isProcessing, uploadStatus]);

    const fetchRooms = async () => {
        try {
            const data = await api.fetchRooms();
            setRooms(data);
        } catch (err) {
            if (err.message === 'USER_LOGOUT') return;

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
                if (isSilent) {
                    if (prev.length === data.length) {
                        const prevLast = prev[prev.length - 1];
                        const newLast = data[data.length - 1];
                        if (prevLast && newLast && prevLast.content === newLast.content) {
                            return prev;
                        }
                    }
                }
                return data;
            });
        } catch (err) {
            if (err.message === 'USER_LOGOUT') return;

            if (!isSilent) {
                toast.error(err.message || "메시지를 불러오는 중 오류가 발생했습니다.");
            }
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