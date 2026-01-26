import { FaBrain, FaPlus, FaRegCommentDots, FaTrash, FaGlobe, FaUser } from 'react-icons/fa';

function Sidebar({ rooms, currentRoomId, onRoomSelect, onNewChat, onDeleteRoom, fileInputRef }) {
    return (
        <div className="sidebar">
            <div className="sidebar-header">
                <div className="sidebar-brand">
                    <FaBrain /> <span>DocWeave</span>
                </div>
            </div>
            <button className="new-chat-btn" onClick={onNewChat}>
                <FaPlus className="btn-icon" /> <span>New Chat</span>
            </button>
            <div className="room-list">
                {rooms.map(room => (
                    <div
                        key={room.id}
                        className={`room-item ${currentRoomId === room.id ? 'active' : ''}`}
                        onClick={() => onRoomSelect(room.id)}
                    >
                        <FaRegCommentDots className="room-icon" />
                        <span className="room-item-title">{room.title}</span>
                        <button
                            className="delete-room-btn"
                            onClick={(e) => onDeleteRoom(e, room.id)}
                        >
                            <FaTrash size={10} />
                        </button>
                    </div>
                ))}
            </div>
            <div className="sidebar-footer">
                <div className="footer-item"><FaGlobe /> <span>Explore</span></div>
                <div className="footer-item"><FaUser /> <span>Profile</span></div>
            </div>
        </div>
    );
}

export default Sidebar;
