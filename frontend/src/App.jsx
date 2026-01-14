import { useState } from 'react'

function App() {
    const [response, setResponse] = useState('')
    const [loading, setLoading] = useState(false)

    const checkAi = async () => {
        setLoading(true)
        try {
            // Spring Boot API 호출
            const res = await fetch('http://localhost:8080/api/ai-test')
            const data = await res.json()
            setResponse(data.ai_response)
        } catch (error) {
            console.error(error)
            setResponse('Error connecting to server')
        }
        setLoading(false)
    }

    return (
        <div style={{ padding: '50px' }}>
            <h1>🧠 DocWeave</h1>
            <p>Backend Connection Test</p>
            <button onClick={checkAi} disabled={loading}>
                {loading ? 'Thinking...' : 'Ask AI "Are you ready?"'}
            </button>
            <div style={{ marginTop: '20px', padding: '10px', background: '#f0f0f0' }}>
                <strong>AI Answer:</strong> {response}
            </div>
        </div>
    )
}

export default App