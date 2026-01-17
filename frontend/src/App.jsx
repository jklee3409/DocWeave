import { useState, useRef, useEffect } from 'react';
import axios from 'axios';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { FaPaperPlane, FaPlus, FaBrain, FaRobot, FaUser, FaRegCommentDots, FaFilePdf } from 'react-icons/fa';
import './App.css';

function App() {
    const [rooms, setRooms] = useState([]); // 사이드바 채팅방 목록
    const [currentRoomId, setCurrentRoomId] = useState(null); // 현재 선택된 방 ID
    const [messages, setMessages] = useState([]); // 현재 방의 메시지들

    const [input, setInput] = useState(''); // 입력창 텍스트
    const [isLoading, setIsLoading] = useState(false); // 로딩 상태

    const messagesEndRef = useRef(null);
    const fileInputRef = useRef(null);
    const textareaRef = useRef(null);

    // 1. 초기 로딩: 채팅방 목록 가져오기
    useEffect(() => {
        fetchRooms();
    }, []);

    // 2. 방 변경 시: 메시지 내역 불러오기
    useEffect(() => {
        if (currentRoomId) {
            fetchMessages(currentRoomId);
        } else {
            setMessages([]); // 방 선택 안됨 -> 초기화
        }
    }, [currentRoomId]);

    // 스크롤 자동 이동 (메시지 추가 시)
    useEffect(() => {
        messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
    }, [messages, isLoading]);

    // 입력창 높이 자동 조절
    useEffect(() => {
        if (textareaRef.current) {
            textareaRef.current.style.height = 'auto';
            textareaRef.current.style.height = `${Math.min(textareaRef.current.scrollHeight, 200)}px`;
        }
    }, [input]);

    // API: 방 목록 조회
    const fetchRooms = async () => {
        try {
            const res = await axios.get('http://localhost:8080/api/doc/rooms');
            setRooms(res.data.data);
        } catch (err) {
            console.error("Failed to fetch rooms", err);
        }
    };

    // API: 메시지 내역 조회
    const fetchMessages = async (roomId) => {
        try {
            const res = await axios.get(`http://localhost:8080/api/doc/rooms/${roomId}/messages`);
            setMessages(res.data.data);
        } catch (err) {
            console.error("Failed to fetch messages", err);
        }
    };

    /**
     * 파일 업로드 핸들러 (통합)
     * Case A (currentRoomId 없음): 새 채팅방 생성 (POST /rooms)
     * Case B (currentRoomId 있음): 기존 방에 파일 추가 (POST /rooms/{id}/files)
     */
    const handleUpload = async (e) => {
        const selectedFile = e.target.files[0];
        if (!selectedFile) return;

        setIsLoading(true);
        const formData = new FormData();
        formData.append('file', selectedFile);

        try {
            if (currentRoomId) {
                // [Case B] 기존 방에 파일 추가
                await axios.post(`http://localhost:8080/api/doc/rooms/${currentRoomId}/files`, formData, {
                    headers: { 'Content-Type': 'multipart/form-data' }
                });
                // 시스템 메시지 확인을 위해 메시지 목록 갱신
                fetchMessages(currentRoomId);
            } else {
                // [Case A] 새 방 생성
                const res = await axios.post('http://localhost:8080/api/doc/rooms', formData, {
                    headers: { 'Content-Type': 'multipart/form-data' }
                });
                const newRoom = res.data.data;
                setRooms([newRoom, ...rooms]); // 목록 맨 앞에 추가
                setCurrentRoomId(newRoom.id); // 해당 방으로 이동
            }
        } catch (error) {
            console.error(error);
            alert('파일 업로드 중 오류가 발생했습니다.');
        } finally {
            setIsLoading(false);
            // 같은 파일을 다시 선택할 수 있도록 인풋 초기화
            if (fileInputRef.current) fileInputRef.current.value = '';
        }
    };

    // 메시지 전송 핸들러
    const handleSend = async () => {
        if (!input.trim() || !currentRoomId) return;

        const userMessage = input;
        setInput(''); // 입력창 비우기

        // UI에 먼저 메시지 표시 (Optimistic Update)
        setMessages(prev => [...prev, { role: 'user', content: userMessage }]);
        setIsLoading(true);

        if (textareaRef.current) textareaRef.current.style.height = 'auto';

        try {
            const res = await axios.post(`http://localhost:8080/api/doc/rooms/${currentRoomId}/chat`, {
                message: userMessage
            });
            // AI 응답 추가
            setMessages(prev => [...prev, { role: 'ai', content: res.data.data.answer }]);
        } catch (error) {
            console.error(error);
            setMessages(prev => [...prev, { role: 'ai', content: '⚠️ **오류:** 서버와 연결할 수 없습니다.' }]);
        } finally {
            setIsLoading(false);
        }
    };

    const handleKeyDown = (e) => {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            handleSend();
        }
    };

    // 사이드바 "새 문서 등록" 버튼 클릭 시
    const handleNewChatClick = () => {
        setCurrentRoomId(null); // 현재 방 선택 해제 (Empty State로 이동)
        setTimeout(() => {
            fileInputRef.current.click(); // 파일 선택창 열기
        }, 0);
    };

    return (
        <div className="app-container">
            {/* 1. Left Sidebar */}
            <div className="sidebar">
                <button className="new-chat-btn" onClick={handleNewChatClick}>
                    <FaPlus /> 새 PDF 업로드
                </button>

                <div className="room-list-label">Recent Chats</div>
                <div className="room-list">
                    {rooms.map(room => (
                        <div
                            key={room.id}
                            className={`room-item ${currentRoomId === room.id ? 'active' : ''}`}
                            onClick={() => setCurrentRoomId(room.id)}
                        >
                            <FaRegCommentDots />
                            <span style={{overflow:'hidden', textOverflow:'ellipsis'}}>{room.title}</span>
                        </div>
                    ))}
                </div>

                {/* 숨겨진 파일 인풋 (하나로 공유) */}
                <input
                    type="file" accept=".pdf"
                    ref={fileInputRef}
                    onChange={handleUpload}
                    style={{ display: 'none' }}
                />
            </div>

            {/* 2. Right Main Content */}
            <div className="main-content">
                {/* Header */}
                <header className="app-header">
                    <div className="brand" onClick={() => window.location.reload()}>
                        <FaBrain size={24} color="#4f46e5" />
                        <span>DocWeave</span>
                        {currentRoomId && (
                            <span className="room-title-display">
                 / {rooms.find(r => r.id === currentRoomId)?.title}
               </span>
                        )}
                    </div>
                </header>

                {/* Chat Feed */}
                <div className="chat-feed">
                    {!currentRoomId ? (
                        /* Case A: 초기 화면 (방 없음) */
                        <div className="empty-state">
                            <FaBrain className="logo-large" />
                            <h1 className="empty-title">무엇을 도와드릴까요?</h1>
                            <p className="empty-desc">
                                <strong>'새 PDF 업로드'</strong> 버튼을 눌러 시작하세요.<br/>
                            </p>
                        </div>
                    ) : (
                        /* Case B: 채팅 화면 */
                        <div className="message-list">
                            {messages.map((msg, index) => (
                                <div key={index} className="message-row">
                                    <div className={`avatar ${msg.role}`}>
                                        {msg.role === 'ai' ? <FaRobot /> : <FaUser size={14} />}
                                    </div>
                                    <div className="message-content">
                                        <div className="user-name">{msg.role === 'ai' ? 'DocWeave' : 'You'}</div>
                                        {/* div로 감싸서 className 에러 방지 */}
                                        <div className="markdown-content">
                                            <ReactMarkdown remarkPlugins={[remarkGfm]}>
                                                {msg.content}
                                            </ReactMarkdown>
                                        </div>
                                    </div>
                                </div>
                            ))}

                            {isLoading && (
                                <div className="message-row">
                                    <div className="avatar ai"><FaRobot /></div>
                                    <div className="message-content">
                                        <span className="loading-dots">생각 중</span>
                                    </div>
                                </div>
                            )}
                            <div ref={messagesEndRef} />
                        </div>
                    )}
                </div>

                {/* Input Area (방 선택 시에만 표시) */}
                {currentRoomId && (
                    <div className="input-container">
                        <div className="input-wrapper">
                            <button
                                className="file-btn"
                                onClick={() => fileInputRef.current.click()}
                                title="현재 채팅방에 PDF 추가"
                                disabled={isLoading}
                            >
                                <FaFilePdf size={18} />
                            </button>

                            <textarea
                                ref={textareaRef}
                                value={input}
                                onChange={(e) => setInput(e.target.value)}
                                onKeyDown={handleKeyDown}
                                placeholder="문서 내용에 대해 질문하세요..."
                                disabled={isLoading}
                                rows={1}
                            />

                            <button
                                className="send-btn"
                                onClick={handleSend}
                                disabled={isLoading || !input.trim()}
                            >
                                <FaPaperPlane size={16} />
                            </button>
                        </div>
                    </div>
                )}
            </div>
        </div>
    );
}

export default App;