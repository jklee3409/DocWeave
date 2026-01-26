import { FaBrain, FaFilePdf, FaPaperPlane } from 'react-icons/fa';

function EmptyState({ onUploadClick }) {
    return (
        <div className="empty-state">
            <div className="logo-wrapper">
                <FaBrain className="logo-large" />
            </div>
            <h1 className="empty-title">DocWeave</h1>
            <div className="empty-search-bar" onClick={onUploadClick}>
                <FaFilePdf className="search-icon" />
                <span>무엇을 알고 싶으세요? PDF 업로드하기</span>
                <div className="search-actions">
                    <FaPaperPlane />
                </div>
            </div>
            <div className="suggestion-chips">
                <div className="chip">문서 요약하기</div>
                <div className="chip">핵심 키워드 추출</div>
                <div className="chip">번역 및 분석</div>
            </div>
        </div>
    );
}

export default EmptyState;
