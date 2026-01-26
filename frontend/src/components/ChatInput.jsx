import { useRef, useEffect } from 'react';
import { FaPlus, FaPaperPlane } from 'react-icons/fa';

function ChatInput({ value, onChange, onSend, onKeyDown, onFileClick, isLoading, isProcessing }) {
    const textareaRef = useRef(null);

    useEffect(() => {
        if (textareaRef.current) {
            textareaRef.current.style.height = 'auto';
            textareaRef.current.style.height = `${Math.min(textareaRef.current.scrollHeight, 200)}px`;
        }
    }, [value]);

    return (
        <div className="input-container">
            <div className="input-wrapper">
                <button
                    className="file-btn"
                    onClick={onFileClick}
                    disabled={isProcessing}
                >
                    <FaPlus size={16} />
                </button>
                <textarea
                    ref={textareaRef}
                    value={value}
                    onChange={onChange}
                    onKeyDown={onKeyDown}
                    placeholder={
                        isProcessing
                            ? "문서를 분석 중입니다. 잠시만 기다려주세요..."
                            : "무엇을 알고 싶으세요?"
                    }
                    disabled={isLoading || isProcessing}
                    rows={1}
                />
                <button
                    className="send-btn"
                    onClick={onSend}
                    disabled={isLoading || !value.trim() || isProcessing}
                >
                    <FaPaperPlane size={16} />
                </button>
            </div>
            <div className="footer-note">
                AI는 실수를 할 수 있습니다. 중요한 정보를 확인하세요.
            </div>
        </div>
    );
}

export default ChatInput;
