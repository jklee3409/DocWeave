import { useState } from 'react';
import { authService } from '../services/authService';
import { toast } from 'react-hot-toast';
import './Auth.css';

function Auth({ onLoginSuccess }) {
    const [isLogin, setIsLogin] = useState(true);
    const [formData, setFormData] = useState({
        email: '',
        password: '',
        name: ''
    });
    const [isLoading, setIsLoading] = useState(false);

    const handleChange = (e) => {
        setFormData({
            ...formData,
            [e.target.name]: e.target.value
        });
    };

    const handleSubmit = async (e) => {
        e.preventDefault();
        setIsLoading(true);

        try {
            if (isLogin) {
                await authService.login(formData.email, formData.password);
                toast.success('로그인 성공!');
            } else {
                if (!formData.name.trim()) {
                    toast.error('이름을 입력해주세요.');
                    setIsLoading(false);
                    return;
                }
                await authService.signup(formData.email, formData.password, formData.name);
                toast.success('회원가입 성공! 로그인되었습니다.');
            }
            
            onLoginSuccess();
        } catch (error) {
            const errorMessage = error.message || (isLogin ? '로그인에 실패했습니다.' : '회원가입에 실패했습니다.');
            toast.error(errorMessage);
        } finally {
            setIsLoading(false);
        }
    };

    const toggleMode = () => {
        setIsLogin(!isLogin);
        setFormData({
            email: '',
            password: '',
            name: ''
        });
    };

    return (
        <div className="auth-container">
            <div className="auth-card">
                <div className="auth-header">
                    <h1>📄 DocWeave</h1>
                    <p>{isLogin ? '로그인하여 문서를 분석하세요' : '회원가입하고 시작하세요'}</p>
                </div>

                <form onSubmit={handleSubmit} className="auth-form">
                    {!isLogin && (
                        <div className="form-group">
                            <label htmlFor="name">이름</label>
                            <input
                                type="text"
                                id="name"
                                name="name"
                                value={formData.name}
                                onChange={handleChange}
                                placeholder="이름을 입력하세요"
                                required={!isLogin}
                            />
                        </div>
                    )}

                    <div className="form-group">
                        <label htmlFor="email">이메일</label>
                        <input
                            type="email"
                            id="email"
                            name="email"
                            value={formData.email}
                            onChange={handleChange}
                            placeholder="이메일을 입력하세요"
                            required
                        />
                    </div>

                    <div className="form-group">
                        <label htmlFor="password">비밀번호</label>
                        <input
                            type="password"
                            id="password"
                            name="password"
                            value={formData.password}
                            onChange={handleChange}
                            placeholder="비밀번호를 입력하세요"
                            required
                        />
                    </div>

                    <button type="submit" className="auth-button" disabled={isLoading}>
                        {isLoading ? '처리 중...' : (isLogin ? '로그인' : '회원가입')}
                    </button>
                </form>

                <div className="auth-toggle">
                    <p>
                        {isLogin ? '계정이 없으신가요?' : '이미 계정이 있으신가요?'}
                        <button type="button" onClick={toggleMode} className="toggle-button">
                            {isLogin ? '회원가입' : '로그인'}
                        </button>
                    </p>
                </div>
            </div>
        </div>
    );
}

export default Auth;
