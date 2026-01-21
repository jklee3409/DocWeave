
# DocWeave

**On-Device LLM 기반 PDF 문서 분석 및 RAG 챗봇**

DocWeave는 외부 API 없이 로컬 환경(On-Device)에서 구동되는 LLM을 활용하여 문서를 분석하는 서비스입니다. 사용자가 PDF를 업로드하면 내용을 로컬 모델로 임베딩하여 저장하고, 이를 기반으로 질문에 대한 답변을 생성합니다.

## 🎯 프로젝트 개요

이 프로젝트는 **Local RAG (Retrieval-Augmented Generation)** 파이프라인을 구현하여, 데이터가 외부로 유출될 걱정 없이 안전하고 비용 효율적으로 문서를 분석할 수 있도록 설계되었습니다.

*   **PDF 임베딩:** 업로드된 문서 내용을 벡터화하여 데이터베이스에 저장
*   **On-Device AI:** Llama 3.2(생성) 및 bge-m3(임베딩) 모델을 로컬에서 직접 구동
*   **문맥 기반 답변:** 사용자 질문과 가장 연관된 문서 내용을 검색하여 AI 답변 생성

## 🛠 기술 스택

*   **Backend:** Java 17, Spring Boot 3.5.10, Spring AI
*   **Frontend:** React, Vite
*   **AI Engine:** Ollama (Llama 3.2, bge-m3)
*   **Database:** PostgreSQL (pgvector)
*   **Infra:** Docker Compose

## 🚀 실행 방법

**1. Ollama 모델 설정**
```bash
ollama pull llama3.2
ollama pull bge-m3
```

**2. 인프라 실행 (DB, Redis)**
```bash
docker-compose up -d
```

**3. 애플리케이션 실행**
*   **Backend:** `./gradlew bootRun`
*   **Frontend:** `cd frontend && npm install && npm run dev`
