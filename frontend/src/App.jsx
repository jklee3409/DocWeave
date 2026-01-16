import { useState, useRef, useEffect } from 'react';
import axios from 'axios';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { FaPaperPlane, FaPlus, FaBrain, FaRobot, FaUser } from 'react-icons/fa';
import './App.css';

function App() {
    const [messages, setMessages] = useState([]);
    const [input, setInput] = useState('');
    const [isLoading, setIsLoading] = useState(false);
    const [file, setFile] = useState(null);

    const messagesEndRef = useRef(null);
    const fileInputRef = useRef(null);
    const textareaRef = useRef(null);

    // 스크롤 자동 이동
    useEffect(() => {
        messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
    }, [messages, isLoading]);

    // 입력창 높이 자동 조절 (엔터 칠 때마다 늘어남)
    useEffect(() => {
        if (textareaRef.current) {
            textareaRef.current.style.height = 'auto'; // 높이 초기화
            textareaRef.current.style.height = `${Math.min(textareaRef.current.scrollHeight, 200)}px`; // 최대 200px까지 늘어남
        }
    }, [input]);

    const handleUpload = async (e) => {
        const selectedFile = e.target.files[0];
        if (!selectedFile) return;
        setFile(selectedFile);

        const formData = new FormData();
        formData.append('file', selectedFile);

        setIsLoading(true);
        addMessage('ai', `📂 **${selectedFile.name}** 분석 중입니다... 잠시만 기다려주세요.`);

        try {
            await axios.post('http://localhost:8080/api/doc/upload', formData, {
                headers: { 'Content-Type': 'multipart/form-data' }
            });
            addMessage('ai', `✅ **${selectedFile.name}** 분석 완료! \n이 문서에 대해 궁금한 점을 물어보세요.`);
        } catch (error) {
            console.error(error);
            addMessage('ai', '❌ **업로드 실패:** 파일을 처리하는 중 오류가 발생했습니다.');
            setFile(null);
        } finally {
            setIsLoading(false);
        }
    };

    const handleSend = async () => {
        if (!input.trim()) return;

        const userMessage = input;
        setInput(''); // 입력창 초기화
        addMessage('user', userMessage);
        setIsLoading(true);

        // 전송 후 입력창 높이 리셋
        if (textareaRef.current) {
            textareaRef.current.style.height = 'auto';
        }

        try {
            const res = await axios.post('http://localhost:8080/api/doc/chat', {
                message: userMessage
            });
            addMessage('ai', res.data.data.answer); // 백엔드 응답 구조 반영
        } catch (error) {
            console.error(error);
            addMessage('ai', '⚠️ **오류 발생:** AI 응답을 받아오지 못했습니다. 잠시 후 다시 시도해주세요.');
        } finally {
            setIsLoading(false);
        }
    };

    const addMessage = (role, content) => {
        setMessages(prev => [...prev, { role, content }]);
    };

    const handleKeyDown = (e) => {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            handleSend();
        }
    };

    return (
        <div className="app-container">
            {/* 1. Header */}
            <header className="app-header">
                <div className="brand" onClick={() => window.location.reload()}>
                    <FaBrain size={28} color="#4f46e5" />
                    <span>DocWeave</span>
                </div>
            </header>

            {/* 2. Chat Feed */}
            <div className="chat-feed">
                {messages.length === 0 ? (
                    /* Empty State (정중앙) */
                    <div className="empty-state">
                        <FaBrain className="logo-large" />
                        <h1 className="empty-title">무엇을 도와드릴까요?</h1>
                        <p className="empty-desc">
                            PDF 문서를 업로드하고 AI와 대화하며 인사이트를 얻으세요.<br/>
                        </p>
                    </div>
                ) : (
                    /* Chat List (넓게 중앙 정렬) */
                    <div className="message-list">
                        {messages.map((msg, index) => (
                            <div key={index} className="message-row">
                                <div className={`avatar ${msg.role}`}>
                                    {msg.role === 'ai' ? <FaRobot /> : <FaUser size={14} />}
                                </div>
                                <div className="message-content">
                                    <div className="user-name">{msg.role === 'ai' ? 'DocWeave' : 'You'}</div>
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
                                    <span className="loading-dots">답변 생성 중</span>
                                </div>
                            </div>
                        )}
                        <div ref={messagesEndRef} />
                    </div>
                )}
            </div>

            {/* 3. Input Area (넓게 중앙 정렬) */ }
            <div className="input-container">
                <div className="input-wrapper">
                    <input
                        type="file"
                        accept=".pdf"
                        ref={fileInputRef}
                        onChange={handleUpload}
                        style={{ display: 'none' }}
                    />
                    <button
                        className={`file-btn ${file ? 'active' : ''}`}
                        onClick={() => fileInputRef.current.click()}
                        title="PDF 파일 업로드"
                        disabled={isLoading}
                    >
                        <FaPlus />
                    </button>

                    <textarea
                        ref={textareaRef}
                        value={input}
                        onChange={(e) => setInput(e.target.value)}
                        onKeyDown={handleKeyDown}
                        placeholder={file ? "이 문서에 대해 궁금한 점을 입력하세요..." : "먼저 + 버튼을 눌러 PDF를 업로드하세요"}
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
        </div>
    );
}

export default App;