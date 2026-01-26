import { useState, useRef, useEffect } from 'react';
import { marked } from 'marked';
import { Toaster, toast } from 'react-hot-toast';
import { useChatRoom } from './hooks/useChatRoom';
import { api } from './services/api';
import Sidebar from './components/Sidebar';
import ChatHeader from './components/ChatHeader';
import ChatMessage from './components/ChatMessage';
import ChatInput from './components/ChatInput';
import EmptyState from './components/EmptyState';
import './App.css';

function App() {
    const {
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
    } = useChatRoom();

    const [input, setInput] = useState('');
    const messagesEndRef = useRef(null);
    const fileInputRef = useRef(null);

    useEffect(() => {
        marked.setOptions({
            breaks: true,
            gfm: true
        });
    }, []);

    useEffect(() => {
        messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
    }, [messages]);

    const handleUpload = async (e) => {
        const selectedFile = e.target.files[0];
        if (!selectedFile) return;

        setUploadStatus('uploading');
        setIsProcessing(true);
        const formData = new FormData();
        formData.append('file', selectedFile);

        const loadingToast = toast.loading("파일을 업로드하고 있습니다...");

        try {
            const newRoom = await api.uploadFile(currentRoomId, formData);

            if (currentRoomId) {
                fetchMessages(currentRoomId);
                moveRoomToTop(currentRoomId);
            } else {
                setRooms([newRoom, ...rooms]);
                setCurrentRoomId(newRoom.id);
            }

            toast.dismiss(loadingToast);
            toast.success("PDF 분석을 시작합니다.");

        } catch (error) {
            console.error(error);
            toast.dismiss(loadingToast);
            toast.error("업로드 요청에 실패했습니다.");
            setUploadStatus(null);
            setIsProcessing(false);
        } finally {
            if (fileInputRef.current) fileInputRef.current.value = '';
        }
    };

    const animateTyping = async (fullText) => {
        const speed = 10;
        let displayedText = '';

        for (let i = 0; i < fullText.length; i += 2) {
            await new Promise(resolve => setTimeout(resolve, speed));
            displayedText += fullText.slice(i, i + 2);

            setMessages(prev => {
                const newMessages = [...prev];
                const lastMsgIndex = newMessages.length - 1;
                if (lastMsgIndex >= 0 && newMessages[lastMsgIndex].role === 'ai') {
                    newMessages[lastMsgIndex] = {
                        ...newMessages[lastMsgIndex],
                        content: displayedText
                    };
                }
                return newMessages;
            });
        }

        setMessages(prev => {
            const newMessages = [...prev];
            const lastMsgIndex = newMessages.length - 1;
            if (lastMsgIndex >= 0 && newMessages[lastMsgIndex].role === 'ai') {
                newMessages[lastMsgIndex] = {
                    ...newMessages[lastMsgIndex],
                    content: fullText
                };
            }
            return newMessages;
        });
    };

    const handleSend = async () => {
        if (!input.trim() || !currentRoomId || isProcessing) return;

        const userMessage = input;
        setInput('');
        setIsLoading(true);

        setMessages(prev => [
            ...prev,
            { role: 'user', content: userMessage },
            { role: 'ai', content: '', isStreaming: true }
        ]);

        moveRoomToTop(currentRoomId);

        try {
            const responseData = await api.sendMessage(currentRoomId, userMessage);

            if (responseData && responseData.data) {
                await animateTyping(responseData.data.answer);
            }

            setMessages(prev => {
                const newMessages = [...prev];
                const lastMsg = newMessages[newMessages.length - 1];
                if (lastMsg.role === 'ai') {
                    lastMsg.isStreaming = false;
                }
                return newMessages;
            });

        } catch (error) {
            console.error("Chat error", error);
            let errorMessage = "⚠️ **오류:** 응답을 처리할 수 없습니다.";
            if (error.response?.data?.statusCode === 30003) {
                errorMessage = "🚫 **[보안 경고]** 답변이 차단되었습니다.";
            }

            setMessages(prev => {
                const newMessages = [...prev];
                const lastMsg = newMessages[newMessages.length - 1];
                if (lastMsg.role === 'ai') {
                    lastMsg.content = errorMessage;
                    lastMsg.isStreaming = false;
                }
                return newMessages;
            });
            toast.error("답변 생성 중 오류가 발생했습니다.");
        } finally {
            setIsLoading(false);
        }
    };

    const handleDeleteRoom = async (e, roomId) => {
        e.stopPropagation();
        if (!window.confirm("정말로 이 채팅방을 삭제하시겠습니까?")) return;
        try {
            await api.deleteRoom(roomId);
            setRooms(prev => prev.filter(room => room.id !== roomId));
            if (currentRoomId === roomId) {
                setCurrentRoomId(null);
                setMessages([]);
            }
            toast.success("채팅방이 삭제되었습니다.");
        } catch (error) {
            toast.error("채팅방 삭제에 실패했습니다.");
        }
    };

    const handleKeyDown = (e) => {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            handleSend();
        }
    };

    const handleNewChatClick = () => {
        setCurrentRoomId(null);
        setTimeout(() => {
            fileInputRef.current.click();
        }, 0);
    };

    const handleCopy = (text) => {
        navigator.clipboard.writeText(text).then(() => {
            toast.success("클립보드에 복사되었습니다.", {
                style: {
                    borderRadius: '10px',
                    background: '#333',
                    color: '#fff',
                },
            });
        });
    };

    const handleFeedback = (type) => {
        toast.success(type === 'like' ? "Thanks for your feedback!" : "Thanks for your feedback!", {
            icon: type === 'like' ? '👍' : '👎',
            style: {
                borderRadius: '10px',
                background: '#333',
                color: '#fff',
            },
        });
    };

    return (
        <div className="app-container">
            <Toaster position="top-center" />

            <Sidebar
                rooms={rooms}
                currentRoomId={currentRoomId}
                onRoomSelect={setCurrentRoomId}
                onNewChat={handleNewChatClick}
                onDeleteRoom={handleDeleteRoom}
                fileInputRef={fileInputRef}
            />

            <div className="main-content">
                <ChatHeader
                    roomTitle={rooms.find(r => r.id === currentRoomId)?.title}
                    uploadStatus={uploadStatus}
                />

                <div className="chat-feed">
                    {!currentRoomId ? (
                        <EmptyState onUploadClick={() => fileInputRef.current.click()} />
                    ) : (
                        <div className="message-list">
                            {messages.map((msg, index) => (
                                <ChatMessage
                                    key={index}
                                    message={msg}
                                    isLoading={isLoading}
                                    onCopy={handleCopy}
                                    onFeedback={handleFeedback}
                                />
                            ))}
                            <div ref={messagesEndRef} />
                        </div>
                    )}
                </div>

                {currentRoomId && (
                    <ChatInput
                        value={input}
                        onChange={(e) => setInput(e.target.value)}
                        onSend={handleSend}
                        onKeyDown={handleKeyDown}
                        onFileClick={() => fileInputRef.current.click()}
                        isLoading={isLoading}
                        isProcessing={isProcessing}
                    />
                )}
            </div>

            <input
                type="file"
                accept=".pdf"
                ref={fileInputRef}
                onChange={handleUpload}
                style={{ display: 'none' }}
            />
        </div>
    );
}

export default App;