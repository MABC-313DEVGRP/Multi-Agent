package com.arms.egovframework.javaservice.aigenerate;


/*

User Query          →  사용자의 원본 자연어 질의       ← 이것
    ↓
RAG                 →  RAG 유사 검색
    ↓
Keyword             →  주제어 추출
    ↓
SearchEngine        →  검색엔진 조회
    ↓
Prompt              →  페르소나 + 컨텍스트 조합 - LLM에 최종 전달되는 완성된 입력
    ↓
Response            →  LLM 응답

 */