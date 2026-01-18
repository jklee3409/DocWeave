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

    // marked 설정
    useEffect(() => {
        marked.setOptions({
            breaks: true,
            gfm: true,
        });
    }, []);

    useEffect(() => { fetchRooms(); }, []);

    useEffect(() => {
        if (currentRoomId) fetchMessages(currentRoomId);
        else setMessages([]);
    }, [currentRoomId]);

    useEffect(() => {
        messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
    }, [messages]);

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

    const handleSend = async () => {
        if (!input.trim() || !currentRoomId) return;

        const userMessage = input;
        setInput('');
        setMessages(prev => [
            ...prev,
            { role: 'user', content: userMessage },
            { role: 'ai', content: '', isStreaming: true }
        ]);
        setIsLoading(true);

        if (textareaRef.current) textareaRef.current.style.height = 'auto';

        try {
            const response = await fetch(`http://localhost:8080/api/doc/rooms/${currentRoomId}/chat`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ message: userMessage }),
            });

            if (!response.body) throw new Error("No response body");

            const reader = response.body.getReader();
            const decoder = new TextDecoder("utf-8");
            let aiTextAccumulated = '';
            let buffer = '';

            while (true) {
                const { done, value } = await reader.read();
                if (done) break;

                // 스트림 디코딩
                buffer += decoder.decode(value, { stream: true });
                const lines = buffer.split('\n');
                // 마지막 라인은 불완전할 수 있으므로 buffer에 남겨둠
                buffer = lines.pop() || '';

                for (const line of lines) {
                    // [중요 수정 1] trim() 제거: 코드 들여쓰기나 Markdown 공백 유지
                    if (line.startsWith('data:')) {
                        // [중요 수정 2] 정규식 대신 slice 사용 및 공백 처리 개선
                        // SSE 표준은 'data:' 뒤에 공백 1개가 오지만,
                        // LLM 토큰 자체가 공백으로 시작할 수 있으므로 주의해서 제거해야 함
                        let content = line.slice(5);

                        // data: 뒤에 첫 번째 공백만 제거 (SSE 프로토콜 공백)
                        // 만약 content가 "  hello"라면(토큰이 " hello"), 결과는 " hello"가 되어야 함
                        if (content.startsWith(' ')) {
                            content = content.slice(1);
                        }

                        // [중요 수정 3] 줄바꿈(\n) 복원
                        // Flux<String>이 '\n' 토큰을 보낼 때, split('\n')에 의해 빈 문자열이 된 경우 처리
                        if (content === '') {
                            content = '\n';
                        }

                        aiTextAccumulated += content;
                    }
                }

                // 상태 업데이트 (스트리밍 중)
                setMessages(prev => {
                    const newMessages = [...prev];
                    const lastMsg = newMessages[newMessages.length - 1];
                    if (lastMsg.role === 'ai') {
                        lastMsg.content = aiTextAccumulated;
                        lastMsg.isStreaming = true;
                    }
                    return newMessages;
                });
            }

            // 스트리밍 완료 처리
            setMessages(prev => {
                const newMessages = [...prev];
                const lastMsg = newMessages[newMessages.length - 1];
                if (lastMsg.role === 'ai') {
                    lastMsg.isStreaming = false;
                }
                return newMessages;
            });

        } catch (error) {
            console.error("Streaming error", error);
            setMessages(prev => {
                const newMessages = [...prev];
                const lastMsg = newMessages[newMessages.length - 1];
                lastMsg.content += '\n\n⚠️ **오류:** 응답 중단됨';
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
                                // [수정] 옵션이 적용된 marked 인스턴스 사용
                                const htmlContent = marked.parse(msg.content || '');

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