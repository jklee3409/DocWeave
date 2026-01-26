import { FaSpinner, FaCheckCircle } from 'react-icons/fa';

function ChatHeader({ roomTitle, uploadStatus }) {
    return (
        <header className="app-header">
            {roomTitle && <span className="room-title-display">{roomTitle}</span>}
            {uploadStatus === 'uploading' && (
                <div className="status-badge uploading">
                    <FaSpinner className="spin-icon" />
                    <span>Processing Document...</span>
                </div>
            )}
            {uploadStatus === 'done' && (
                <div className="status-badge done">
                    <FaCheckCircle />
                    <span>Ready</span>
                </div>
            )}
        </header>
    );
}

export default ChatHeader;
