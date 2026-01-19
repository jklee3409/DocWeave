import { useState, useRef, useEffect } from 'react';
import axios from 'axios';
import { marked } from 'marked';
import { FaPaperPlane, FaPlus, FaBrain, FaRobot, FaUser, FaRegCommentDots, FaFilePdf, FaTrash } from 'react-icons/fa';
import './App.css';

function App() {
    const [rooms, setRooms] = useState([]);
    const [currentRoomId, setCurrentRoomId] = useState(null);
    const [messages, setMessages] = useState([]);
    const [input, setInput] = useState('');
    const [isLoading, setIsLoading] = useState(false);

    const messagesEndRef = useRef(null);
    const fileInputRef = useRef(null);
    const textareaRef = useRef(null);

    // 마크다운 설정
    useEffect(() => {
        marked.setOptions({
            breaks: true,
            gfm: true,
        });
    }, []);

    // 초기 데이터 로드
    useEffect(() => { fetchRooms(); }, []);

    // 방 변경 시 메시지 로드
    useEffect(() => {
        if (currentRoomId) fetchMessages(currentRoomId);
        else setMessages([]);
    }, [currentRoomId]);

    // 자동 스크롤
    useEffect(() => {
        messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
    }, [messages]);

    // 텍스트 영역 높이 조절
    useEffect(() => {
        if (textareaRef.current) {
            textareaRef.current.style.height = 'auto';
            textareaRef.current.style.height = `${Math.min(textareaRef.current.scrollHeight, 200)}px`;
        }
    }, [input]);

    const fetchRooms = async () => {
        try {
            const res = await axios.get('http://localhost:8080/api/doc/rooms');
            setRooms(res.data.data);
        } catch (err) { console.error("Failed to fetch rooms", err); }
    };

    const fetchMessages = async (roomId) => {
        try {
            const res = await axios.get(`http://localhost:8080/api/doc/rooms/${roomId}/messages`);
            setMessages(res.data.data);
        } catch (err) { console.error("Failed to fetch messages", err); }
    };

    const handleUpload = async (e) => {
        const selectedFile = e.target.files[0];
        if (!selectedFile) return;

        setIsLoading(true);
        const formData = new FormData();
        formData.append('file', selectedFile);

        try {
            if (currentRoomId) {
                await axios.post(`http://localhost:8080/api/doc/rooms/${currentRoomId}/files`, formData, {
                    headers: { 'Content-Type': 'multipart/form-data' }
                });
                fetchMessages(currentRoomId);
            } else {
                const res = await axios.post('http://localhost:8080/api/doc/rooms', formData, {
                    headers: { 'Content-Type': 'multipart/form-data' }
                });
                const newRoom = res.data.data;
                setRooms([newRoom, ...rooms]);
                setCurrentRoomId(newRoom.id);
            }
        } catch (error) {
            console.error(error);
            alert('파일 업로드 오류');
        } finally {
            setIsLoading(false);
            if (fileInputRef.current) fileInputRef.current.value = '';
        }
    };

    // 전체 텍스트를 받아서 state를 조금씩 업데이트
    const animateTyping = async (fullText) => {
        const speed = 20; // 타이핑 속도 (ms)
        let displayedText = '';

        // 청크 단위로 나누거나, 글자 단위로 처리
        const chars = fullText.split('');

        for (let i = 0; i < chars.length; i++) {
            // 비동기 지연 (타이핑 효과)
            await new Promise(resolve => setTimeout(resolve, speed));

            displayedText += chars[i];

            setMessages(prev => {
                const newMessages = [...prev];
                const lastMsg = newMessages[newMessages.length - 1];
                // 마지막 메시지가 AI인 경우에만 내용 업데이트
                if (lastMsg.role === 'ai') {
                    lastMsg.content = displayedText;
                }
                return newMessages;
            });
        }
    };

    const preprocessMarkdown = (text) => {
        if (!text) return '';
        // 리스트(*, -, 1.) 앞에 줄바꿈이 없으면 강제로 줄바꿈 2개 추가하여 렌더링 보정
        let processed = text.replace(/(?<!\n)(\s*)(\*|-|\d+\.) /g, '\n\n$2 ');
        processed = processed.replace(/(\n)(\s*)(\*|-|\d+\.) /g, '\n\n$3 ');
        return processed;
    };

    //  Axios 일반 요청
    const handleSend = async () => {
        if (!input.trim() || !currentRoomId) return;

        const userMessage = input;
        setInput('');

        // 1. 사용자 메시지 추가 + AI 로딩(빈값) 메시지 추가
        setMessages(prev => [
            ...prev,
            { role: 'user', content: userMessage },
            { role: 'ai', content: '', isStreaming: true } // isStreaming으로 커서 표시 제어
        ]);
        setIsLoading(true);

        if (textareaRef.current) textareaRef.current.style.height = 'auto';

        try {
            // 2. 백엔드 요청 (Blocking 방식)
            // 응답 타입: BaseResponseDto<ChatResponseDto>
            const response = await axios.post(`http://localhost:8080/api/doc/rooms/${currentRoomId}/chat`, {
                message: userMessage
            });

            // 3. 응답 데이터 추출 (ChatResponseDto: { question, answer })
            const responseData = response.data; // BaseResponseDto

            if (responseData && responseData.data) {
                const aiAnswer = responseData.data.answer;

                // 4. 타자기 효과 실행 (받은 전체 텍스트로 애니메이션)
                await animateTyping(aiAnswer);
            } else {
                throw new Error("Invalid response format");
            }

            // 5. 스트리밍 상태 종료
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

            // 에러 메시지 처리
            let errorMessage = "⚠️ **오류:** 응답을 처리할 수 없습니다.";

            const resBody = error.response?.data;

            const statusCode = resBody?.statusCode;
            const errorCodeName = resBody?.data?.statusCodeName;

            if (statusCode === 30003 || errorCodeName === 'GUARDRAIL_BLOCKED') {
                errorMessage = "🚫 **[보안 경고]**\n\n문서와의 연관성이 낮거나 부적절한 질문으로 판단되어 답변이 차단되었습니다.";
            }

            setMessages(prev => {
                const newMessages = [...prev];
                const lastMsg = newMessages[newMessages.length - 1];
                lastMsg.content = errorMessage;
                lastMsg.isStreaming = false;
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
            if (currentRoomId === roomId) { setCurrentRoomId(null); setMessages([]); }
        } catch (error) { alert("삭제 실패"); }
    };

    const handleKeyDown = (e) => {
        if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); handleSend(); }
    };

    const handleNewChatClick = () => {
        setCurrentRoomId(null);
        setTimeout(() => { fileInputRef.current.click(); }, 0);
    };

    return (
        <div className="app-container">
            <div className="sidebar">
                <button className="new-chat-btn" onClick={handleNewChatClick}><FaPlus /> 새 문서 시작</button>
                <div className="room-list-label">Recent Chats</div>
                <div className="room-list">
                    {rooms.map(room => (
                        <div key={room.id} className={`room-item ${currentRoomId === room.id ? 'active' : ''}`} onClick={() => setCurrentRoomId(room.id)}>
                            <FaRegCommentDots />
                            <span className="room-item-title">{room.title}</span>
                            <button className="delete-room-btn" onClick={(e) => handleDeleteRoom(e, room.id)}><FaTrash size={12} /></button>
                        </div>
                    ))}
                </div>
                <input type="file" accept=".pdf" ref={fileInputRef} onChange={handleUpload} style={{ display: 'none' }} />
            </div>

            <div className="main-content">
                <header className="app-header">
                    <div className="brand" onClick={() => window.location.reload()}>
                        <FaBrain size={24} color="#4f46e5" />
                        <span>DocWeave</span>
                        {currentRoomId && <span className="room-title-display">/ {rooms.find(r => r.id === currentRoomId)?.title}</span>}
                    </div>
                </header>

                <div className="chat-feed">
                    {!currentRoomId ? (
                        <div className="empty-state">
                            <FaBrain className="logo-large" />
                            <h1 className="empty-title">무엇을 도와드릴까요?</h1>
                            <p className="empty-desc"><strong>'새 문서 시작'</strong> 버튼을 눌러 PDF를 업로드하세요.</p>
                        </div>
                    ) : (
                        <div className="message-list">
                            {messages.map((msg, index) => {
                                const isStreamingMessage = msg.isStreaming && isLoading;
                                const rawContent = msg.content || '';
                                const processedContent = preprocessMarkdown(rawContent);
                                const htmlContent = marked.parse(processedContent);

                                return (
                                    <div key={index} className="message-row">
                                        <div className={`avatar ${msg.role}`}>
                                            {msg.role === 'ai' ? <FaRobot /> : <FaUser size={14} />}
                                        </div>
                                        <div className="message-content">
                                            <div className="user-name">{msg.role === 'ai' ? 'DocWeave' : 'You'}</div>
                                            <div
                                                className="markdown-content"
                                                dangerouslySetInnerHTML={{ __html: htmlContent }}
                                            />
                                            {isStreamingMessage && <span className="typing-cursor">▎</span>}
                                        </div>
                                    </div>
                                );
                            })}
                            <div ref={messagesEndRef} />
                        </div>
                    )}
                </div>

                {currentRoomId && (
                    <div className="input-container">
                        <div className="input-wrapper">
                            <button className="file-btn" onClick={() => fileInputRef.current.click()} disabled={isLoading}>
                                <FaFilePdf size={18} />
                            </button>
                            <textarea
                                ref={textareaRef}
                                value={input}
                                onChange={(e) => setInput(e.target.value)}
                                onKeyDown={handleKeyDown}
                                placeholder="질문하세요..."
                                disabled={isLoading}
                                rows={1}
                            />
                            <button className="send-btn" onClick={handleSend} disabled={isLoading || !input.trim()}>
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