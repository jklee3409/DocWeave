import { useState, useRef, useEffect } from 'react';
import axios from 'axios';
import ReactMarkdown from 'react-markdown'; // 마크다운 렌더러
import remarkGfm from 'remark-gfm'; // 테이블, 리스트 등 지원
import { FaPaperPlane, FaFileUpload, FaRobot, FaUser } from 'react-icons/fa'; // 아이콘
import './App.css';

function App() {
    const [messages, setMessages] = useState([
        {
            role: 'ai',
            content: '**안녕하세요! DocWeave 입니다.** 👋\n\nPDF 문서를 업로드하시면 내용을 분석하여 답변해 드립니다.\n문서의 요약, 특정 정보 검색 등 무엇이든 물어보세요!'
        }
    ]);
    const [input, setInput] = useState('');
    const [isLoading, setIsLoading] = useState(false);
    const [file, setFile] = useState(null);

    const messagesEndRef = useRef(null);
    const fileInputRef = useRef(null); // 파일 인풋 제어용

    // 스크롤 자동 이동
    useEffect(() => {
        messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
    }, [messages]);

    const handleUpload = async (e) => {
        const selectedFile = e.target.files[0];
        if (!selectedFile) return;
        setFile(selectedFile);

        const formData = new FormData();
        formData.append('file', selectedFile);

        setIsLoading(true);
        // 업로드 시작 메시지 (UX)
        setMessages(prev => [...prev, { role: 'ai', content: `📂 **${selectedFile.name}** 문서를 분석하고 있습니다... 잠시만 기다려주세요.` }]);

        try {
            await axios.post('http://localhost:8080/api/doc/upload', formData, {
                headers: { 'Content-Type': 'multipart/form-data' }
            });

            setMessages(prev => [...prev, { role: 'ai', content: '✅ **분석이 완료되었습니다!** 이제 문서 내용에 대해 자유롭게 질문해주세요.' }]);
        } catch (error) {
            console.error(error);
            setMessages(prev => [...prev, { role: 'ai', content: '❌ **업로드 실패:** 서버에 연결할 수 없거나 파일이 너무 큽니다.' }]);
        } finally {
            setIsLoading(false);
        }
    };

    const handleSend = async () => {
        if (!input.trim()) return;

        const userMessage = input;
        setMessages(prev => [...prev, { role: 'user', content: userMessage }]);
        setInput('');
        setIsLoading(true);

        try {
            const res = await axios.post('http://localhost:8080/api/doc/chat', {
                message: userMessage
            });

            setMessages(prev => [...prev, { role: 'ai', content: res.data.answer }]);
        } catch (error) {
            console.error(error);
            setMessages(prev => [...prev, { role: 'ai', content: '⚠️ **오류 발생:** AI 응답을 받아오지 못했습니다.' }]);
        } finally {
            setIsLoading(false);
        }
    };

    const handleKeyDown = (e) => {
        if (e.key === 'Enter' && !e.shiftKey) { // Shift+Enter는 줄바꿈
            e.preventDefault();
            handleSend();
        }
    };

    return (
        <div className="app-container">
            {/* 1. 헤더 */}
            <header className="app-header">
                <div className="brand">
                    <FaRobot size={28} color="#4f46e5" />
                    <span>DocWeave</span>
                </div>
            </header>

            {/* 2. 채팅 영역 */}
            <div className="chat-feed">
                {messages.map((msg, index) => (
                    <div key={index} className={`message-row ${msg.role}`}>
                        {msg.role === 'ai' && (
                            <div className="avatar ai">
                                <FaRobot />
                            </div>
                        )}

                        <div className="message-bubble">
                            <div className="markdown-content">
                                <ReactMarkdown remarkPlugins={[remarkGfm]}>
                                    {msg.content}
                                </ReactMarkdown>
                            </div>
                        </div>
                    </div>
                ))}

                {isLoading && (
                    <div className="message-row ai">
                        <div className="avatar ai"><FaRobot /></div>
                        <div className="message-bubble">
                            <span className="loading-dots">Thinking...</span>
                        </div>
                    </div>
                )}
                <div ref={messagesEndRef} />
            </div>

            {/* 3. 입력 영역 (Sticky Bottom) */}
            <div className="input-container">
                {/* 파일 업로드 버튼 */}
                <div className="upload-area">
                    <input
                        type="file"
                        accept=".pdf"
                        ref={fileInputRef}
                        onChange={handleUpload}
                        style={{ display: 'none' }} // 기본 인풋 숨김
                    />
                    <button
                        className="upload-btn-label"
                        onClick={() => fileInputRef.current.click()}
                        disabled={isLoading}
                    >
                        <FaFileUpload />
                        {file ? '다른 파일 선택' : 'PDF 문서 업로드'}
                    </button>
                    {file && <span>{file.name}</span>}
                </div>

                {/* 텍스트 입력창 */}
                <div className="input-box">
          <textarea
              value={input}
              onChange={(e) => setInput(e.target.value)}
              onKeyDown={handleKeyDown}
              placeholder="문서 내용에 대해 질문하세요... (Enter 전송)"
              disabled={isLoading}
          />
                    <button className="send-btn" onClick={handleSend} disabled={isLoading || !input.trim()}>
                        <FaPaperPlane />
                    </button>
                </div>
            </div>
        </div>
    );
}

export default App;