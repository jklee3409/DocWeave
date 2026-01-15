import { useState, useRef, useEffect } from 'react';
import axios from 'axios';
import './App.css';

function App() {
    // 상태 관리
    const [messages, setMessages] = useState([
        { role: 'ai', content: '안녕하세요! PDF 문서를 업로드하면 내용을 요약하거나 답변해 드릴게요.' }
    ]);
    const [input, setInput] = useState('');
    const [isLoading, setIsLoading] = useState(false);
    const [file, setFile] = useState(null);

    // 스크롤 자동 이동을 위한 Ref
    const messagesEndRef = useRef(null);

    // 메시지 추가될 때마다 스크롤 아래로 이동
    useEffect(() => {
        messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
    }, [messages]);

    // 1. 파일 업로드 핸들러
    const handleUpload = async () => {
        if (!file) {
            alert('파일을 선택해주세요!');
            return;
        }

        const formData = new FormData();
        formData.append('file', file);

        setIsLoading(true);
        try {
            // 백엔드 업로드 API 호출
            await axios.post('http://localhost:8080/api/doc/upload', formData, {
                headers: { 'Content-Type': 'multipart/form-data' }
            });

            alert('문서 학습이 완료되었습니다! 이제 질문해보세요.');
            setMessages(prev => [...prev, { role: 'ai', content: '문서 내용을 다 읽었습니다. 무엇이든 물어보세요!' }]);
        } catch (error) {
            console.error(error);
            alert('업로드 실패: 서버 로그를 확인하세요.');
        } finally {
            setIsLoading(false);
        }
    };

    // 2. 채팅 전송 핸들러
    const handleSend = async () => {
        if (!input.trim()) return;

        // 사용자 메시지 먼저 화면에 표시
        const userMessage = input;
        setMessages(prev => [...prev, { role: 'user', content: userMessage }]);
        setInput('');
        setIsLoading(true);

        try {
            // 백엔드 채팅 API 호출 (RAG)
            const res = await axios.post('http://localhost:8080/api/doc/chat', {
                message: userMessage
            });

            // AI 응답 표시
            setMessages(prev => [...prev, { role: 'ai', content: res.data.answer }]);
        } catch (error) {
            console.error(error);
            setMessages(prev => [...prev, { role: 'ai', content: '오류가 발생했습니다. 잠시 후 다시 시도해주세요.' }]);
        } finally {
            setIsLoading(false);
        }
    };

    // 엔터키 입력 처리
    const handleKeyDown = (e) => {
        if (e.key === 'Enter' && !e.nativeEvent.isComposing) {
            handleSend();
        }
    };

    return (
        <div className="container">
            <header>
                <h1>🧠 DocuMind</h1>
                <p>나만의 AI 문서 비서</p>
            </header>

            {/* 파일 업로드 섹션 */}
            <div className="upload-section">
                <input
                    type="file"
                    accept=".pdf"
                    onChange={(e) => setFile(e.target.files[0])}
                />
                <button onClick={handleUpload} disabled={isLoading || !file} style={{padding: '5px 15px', marginLeft: '10px'}}>
                    {isLoading ? '학습 중...' : 'PDF 업로드 및 학습'}
                </button>
            </div>

            {/* 채팅창 섹션 */}
            <div className="chat-window">
                <div className="messages-area">
                    {messages.map((msg, index) => (
                        <div key={index} className={`message ${msg.role === 'user' ? 'user-message' : 'ai-message'}`}>
                            {msg.content}
                        </div>
                    ))}
                    {isLoading && <div className="message ai-message">...생각 중...</div>}
                    <div ref={messagesEndRef} />
                </div>

                <div className="input-area">
                    <input
                        type="text"
                        value={input}
                        onChange={(e) => setInput(e.target.value)}
                        onKeyDown={handleKeyDown}
                        placeholder="문서 내용에 대해 질문하세요..."
                        disabled={isLoading}
                    />
                    <button className="send-btn" onClick={handleSend} disabled={isLoading}>
                        전송
                    </button>
                </div>
            </div>
        </div>
    );
}

export default App;