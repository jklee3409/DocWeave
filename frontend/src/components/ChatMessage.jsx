import { marked } from 'marked';
import { FaBrain, FaRegCopy, FaRegThumbsUp, FaRegThumbsDown } from 'react-icons/fa';

function ChatMessage({ message, isLoading, onCopy, onFeedback }) {
    const isStreamingMessage = message.isStreaming && isLoading;
    const rawContent = message.content || '';
    const htmlContent = marked.parse(rawContent);

    return (
        <div className={`message-row ${message.role}`}>
            <div className="message-container">
                {message.role === 'ai' && (
                    <div className="avatar ai">
                        <FaBrain />
                    </div>
                )}
                <div className="message-content">
                    <div className="user-name">
                        {message.role === 'ai' ? 'DocWeave' : 'You'}
                    </div>
                    <div
                        className="markdown-content"
                        dangerouslySetInnerHTML={{ __html: htmlContent }}
                    />
                    {isStreamingMessage && <span className="typing-cursor">●</span>}

                    {message.role === 'ai' && !isStreamingMessage && (
                        <div className="ai-response-footer">
                            <div className="response-divider"></div>
                            <div className="footer-actions">
                                <div className="feedback-buttons">
                                    <button
                                        className="icon-action-btn"
                                        onClick={() => onFeedback('like')}
                                        title="좋아요"
                                    >
                                        <FaRegThumbsUp />
                                    </button>
                                    <button
                                        className="icon-action-btn"
                                        onClick={() => onFeedback('dislike')}
                                        title="싫어요"
                                    >
                                        <FaRegThumbsDown />
                                    </button>
                                </div>
                                <button
                                    className="icon-action-btn"
                                    onClick={() => onCopy(rawContent)}
                                    title="복사하기"
                                >
                                    <FaRegCopy />
                                </button>
                            </div>
                        </div>
                    )}
                </div>
            </div>
        </div>
    );
}

export default ChatMessage;
