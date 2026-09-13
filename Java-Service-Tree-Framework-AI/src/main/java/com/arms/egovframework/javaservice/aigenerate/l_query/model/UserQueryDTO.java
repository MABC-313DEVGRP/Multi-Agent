package com.arms.egovframework.javaservice.aigenerate.l_query.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 사용자 원본 질의 슈퍼 클래스
 *
 * <p>AI 파이프라인의 시작점으로, 사용자가 입력한 자연어 질의를 표현하는 최상위 클래스입니다.</p>
 *
 * <pre>
 * AI 파이프라인:
 *   UserQuery (원본 질의)
 *       ↓
 *   RAG (유사 검색)
 *       ↓
 *   Keyword (주제어 추출)
 *       ↓
 *   SearchEngine (검색엔진 조회)
 *       ↓
 *   Prompt (페르소나 + 컨텍스트 조합)
 *       ↓
 *   Response (LLM 응답)
 * </pre>
 *
 * <p>하위 클래스는 {@code @SuperBuilder}와 {@code @EqualsAndHashCode(callSuper = true)}를 함께 선언해야 합니다.</p>
 *
 * @see com.arms.egovframework.javaservice.aigenerate.ll_rag
 */
@Getter
@SuperBuilder
@NoArgsConstructor
public abstract class UserQueryDTO {

    /**
     * 사용자가 입력한 자연어 질의 텍스트.
     * 파이프라인 전 단계에 걸쳐 원본 질의로 참조됩니다.
     */
    private String queryText;

    /**
     * 스트리밍 세션 또는 요청을 식별하는 ID.
     * 스트림 중단, 로그 추적, 응답 매핑에 사용됩니다.
     */
    private String sessionId;

    /**
     * 응답 언어 코드 (기본값: "ko").
     * 프롬프트 생성 단계에서 언어별 페르소나 선택에 활용됩니다.
     */
    @lombok.Builder.Default
    private String language = "ko";

    /**
     * 질의가 생성된 시각 (기본값: 현재 시각).
     */
    @lombok.Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    /**
     * 파이프라인 각 단계에서 자유롭게 추가할 수 있는 확장 메타데이터.
     * 예: 필터 조건, 도메인 컨텍스트, 사용자 권한 정보 등
     */
    @lombok.Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    /**
     * 메타데이터 항목을 추가합니다.
     *
     * @param key   메타데이터 키
     * @param value 메타데이터 값
     * @return 현재 인스턴스 (메서드 체이닝용)
     */
    public UserQueryDTO addMetadata(String key, Object value) {
        this.metadata.put(key, value);
        return this;
    }

    /**
     * 이 질의가 유효한지 여부를 반환합니다.
     * queryText 가 비어 있지 않을 때만 유효합니다.
     *
     * @return 유효 여부
     */
    public boolean isValid() {
        return queryText != null && !queryText.isBlank();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName()
                + "{sessionId='" + sessionId + '\''
                + ", language='" + language + '\''
                + ", queryText='" + queryText + '\''
                + ", createdAt=" + createdAt
                + '}';
    }
}
