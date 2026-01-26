import { useState, useRef, useEffect } from 'react';
import axios from 'axios';
import { marked } from 'marked';
import { FaPaperPlane, FaPlus, FaBrain, FaRegCommentDots, FaFilePdf, FaTrash, FaSpinner, FaCheckCircle, FaGlobe, FaUser } from 'react-icons/fa';
import './App.css';

function App() {
    const [rooms, setRooms] = useState([]);
    const [currentRoomId, setCurrentRoomId] = useState(null);
    const [messages, setMessages] = useState([]);
    const [input, setInput] = useState('');
    const [isLoading, setIsLoading] = useState(false);
    const [uploadStatus, setUploadStatus] = useState(null);
    const [isProcessing, setIsProcessing] = useState(false);

    const messagesEndRef = useRef(null);
    const fileInputRef = useRef(null);
    const textareaRef = useRef(null);

    useEffect(() => {
        marked.setOptions({
            breaks: true,
            gfm: true
        });
        fetchRooms();
    }, []);

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
        messagesEndRef.current?.scrollIntoView({behavior: 'smooth'});
    }, [messages]);

    useEffect(() => {
        if (textareaRef.current) {
            textareaRef.current.style.height = 'auto';
            textareaRef.current.style.height = `${Math.min(textareaRef.current.scrollHeight, 200)}px`;
        }
    }, [input]);

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
                }
            } else {
                setIsProcessing(false);
            }
        }
    }, [messages]);

    const fetchRooms = async () => {
        try {
            const res = await axios.get('http://localhost:8080/api/doc/rooms');
            setRooms(res.data.data);
        } catch (err) {
            console.error("Failed to fetch rooms", err);
        }
    };

    const fetchMessages = async (roomId, isSilent = false) => {
        try {
            if (isLoading && isSilent) return;

            const res = await axios.get(`http://localhost:8080/api/doc/rooms/${roomId}/messages`);

            setMessages(prev => {
                if (isSilent && prev.length === res.data.data.length) return prev;
                return res.data.data;
            });
        } catch (err) {
            console.error("Failed to fetch messages", err);
        }
    };

    const handleUpload = async (e) => {
        const selectedFile = e.target.files[0];
        if (!selectedFile) return;

        setUploadStatus('uploading');
        setIsProcessing(true);
        const formData = new FormData();
        formData.append('file', selectedFile);

        try {
            if (currentRoomId) {
                await axios.post(`http://localhost:8080/api/doc/rooms/${currentRoomId}/files`, formData, {
                    headers: {'Content-Type': 'multipart/form-data'}
                });
                fetchMessages(currentRoomId);

                setRooms(prevRooms => {
                    const targetRoom = prevRooms.find(r => r.id === currentRoomId);
                    const otherRooms = prevRooms.filter(r => r.id !== currentRoomId);
                    return targetRoom ? [targetRoom, ...otherRooms] : prevRooms;
                });
            } else {
                const res = await axios.post('http://localhost:8080/api/doc/rooms', formData, {
                    headers: {'Content-Type': 'multipart/form-data'}
                });
                const newRoom = res.data.data;
                setRooms([newRoom, ...rooms]);
                setCurrentRoomId(newRoom.id);
            }
        } catch (error) {
            console.error(error);
            alert('업로드 요청 실패');
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
            {role: 'user', content: userMessage},
            {role: 'ai', content: '', isStreaming: true}
        ]);

        setRooms(prevRooms => {
            const targetRoom = prevRooms.find(r => r.id === currentRoomId);
            const otherRooms = prevRooms.filter(r => r.id !== currentRoomId);
            return targetRoom ? [targetRoom, ...otherRooms] : prevRooms;
        });

        if (textareaRef.current) textareaRef.current.style.height = 'auto';

        try {
            const response = await axios.post(`http://localhost:8080/api/doc/rooms/${currentRoomId}/chat`, {
                message: userMessage
            });

            const responseData = response.data;
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
        } finally {
            setIsLoading(false);
        }
    };

    const handleDeleteRoom = async (e, roomId) => {
        e.stopPropagation();
        if (!window.confirm("삭제하시겠습니까?")) return;
        try {
            await axios.delete(`http://localhost:8080/api/doc/rooms/${roomId}`);
            setRooms(prev => prev.filter(room => room.id !== roomId));
            if (currentRoomId === roomId) {
                setCurrentRoomId(null);
                setMessages([]);
            }
        } catch (error) {
            alert("삭제 실패");
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

    return (
        <div className="app-container">
            <div className="sidebar">
                <div className="sidebar-header">
                    <div className="sidebar-brand">
                        <FaBrain/> <span>DocWeave</span>
                    </div>
                </div>
                <button className="new-chat-btn" onClick={handleNewChatClick}>
                    <FaPlus className="btn-icon"/> <span>New Chat</span>
                </button>
                <div className="room-list">
                    {rooms.map(room => (
                        <div key={room.id} className={`room-item ${currentRoomId === room.id ? 'active' : ''}`}
                             onClick={() => setCurrentRoomId(room.id)}>
                            <FaRegCommentDots className="room-icon"/>
                            <span className="room-item-title">{room.title}</span>
                            <button className="delete-room-btn" onClick={(e) => handleDeleteRoom(e, room.id)}><FaTrash
                                size={10}/></button>
                        </div>
                    ))}
                </div>
                <input type="file" accept=".pdf" ref={fileInputRef} onChange={handleUpload} style={{display: 'none'}}/>

                <div className="sidebar-footer">
                    <div className="footer-item"><FaGlobe/> <span>Explore</span></div>
                    <div className="footer-item"><FaUser/> <span>Profile</span></div>
                </div>
            </div>

            <div className="main-content">
                <header className="app-header">
                    {currentRoomId &&
                        <span className="room-title-display">{rooms.find(r => r.id === currentRoomId)?.title}</span>}
                    {uploadStatus === 'uploading' && (
                        <div className="status-badge uploading">
                            <FaSpinner className="spin-icon"/>
                            <span>Processing Document...</span>
                        </div>
                    )}
                    {uploadStatus === 'done' && (
                        <div className="status-badge done">
                            <FaCheckCircle/>
                            <span>Ready</span>
                        </div>
                    )}
                </header>

                <div className="chat-feed">
                    {!currentRoomId ? (
                        <div className="empty-state">
                            <div className="logo-wrapper">
                                <FaBrain className="logo-large"/>
                            </div>
                            <h1 className="empty-title">DocWeave</h1>
                            <div className="empty-search-bar" onClick={() => fileInputRef.current.click()}>
                                <FaFilePdf className="search-icon"/>
                                <span>무엇을 알고 싶으세요? PDF 업로드하기</span>
                                <div className="search-actions">
                                    <FaPaperPlane/>
                                </div>
                            </div>
                            <div className="suggestion-chips">
                                <div className="chip">문서 요약하기</div>
                                <div className="chip">핵심 키워드 추출</div>
                                <div className="chip">번역 및 분석</div>
                            </div>
                        </div>
                    ) : (
                        <div className="message-list">
                            {messages.map((msg, index) => {
                                const isStreamingMessage = msg.isStreaming && isLoading;
                                const rawContent = msg.content || '';
                                const htmlContent = marked.parse(rawContent);

                                return (
                                    <div key={index} className={`message-row ${msg.role}`}>
                                        <div className="message-container">
                                            {msg.role === 'ai' && <div className="avatar ai"><FaBrain/></div>}
                                            <div className="message-content">
                                                <div
                                                    className="user-name">{msg.role === 'ai' ? 'DocWeave' : 'You'}</div>
                                                <div
                                                    className="markdown-content"
                                                    dangerouslySetInnerHTML={{__html: htmlContent}}
                                                />
                                                {isStreamingMessage && <span className="typing-cursor">●</span>}
                                            </div>
                                        </div>
                                    </div>
                                );
                            })}
                            <div ref={messagesEndRef}/>
                        </div>
                    )}
                </div>

                {currentRoomId && (
                    <div className="input-container">
                        <div className="input-wrapper">
                            <button className="file-btn" onClick={() => fileInputRef.current.click()}
                                    disabled={isProcessing}>
                                <FaPlus size={16}/>
                            </button>
                            <textarea
                                ref={textareaRef}
                                value={input}
                                onChange={(e) => setInput(e.target.value)}
                                onKeyDown={handleKeyDown}
                                placeholder={isProcessing ? "문서를 분석 중입니다. 잠시만 기다려주세요..." : "무엇을 알고 싶으세요?"}
                                disabled={isLoading || isProcessing}
                                rows={1}
                            />
                            <button className="send-btn" onClick={handleSend}
                                    disabled={isLoading || !input.trim() || isProcessing}>
                                <FaPaperPlane size={16}/>
                            </button>
                        </div>
                        <div className="footer-note">AI는 실수를 할 수 있습니다. 중요한 정보를 확인하세요.</div>
                    </div>
                )}
            </div>
        </div>
    );
}

export default App;