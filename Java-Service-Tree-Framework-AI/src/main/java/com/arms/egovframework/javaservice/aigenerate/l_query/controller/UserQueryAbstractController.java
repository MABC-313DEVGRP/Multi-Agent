/*
 * @author Dongmin.lee
 * @since 2026-04-09
 * @version 26.04.09
 * @see <pre>
 *  Copyright (C) 2007 by 313 DEV GRP, Inc - All Rights Reserved
 *  Unauthorized copying of this file, via any medium is strictly prohibited
 *  Proprietary and confidential
 *  Written by 313 developer group <313@313.co.kr>, December 2010
 * </pre>
 */
package com.arms.egovframework.javaservice.aigenerate.l_query.controller;

import com.arms.egovframework.javaservice.aigenerate.l_query.model.UserQueryDTO;
import com.arms.egovframework.javaservice.aigenerate.l_query.service.UserQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * AI Query 파이프라인 컨트롤러 슈퍼 클래스
 *
 * <p>사용자 질의({@link UserQueryDTO})를 받아 AI 파이프라인으로 전달하는
 * REST 컨트롤러의 공통 기반 클래스입니다.</p>
 *
 * <pre>
 * 사용 예시:
 *
 * {@code
 * @RestController
 * @RequestMapping("/performance/query")
 * public class PerformanceQueryController
 *         extends UserQueryAbstractController<PerformanceQueryServiceImpl, PerformanceQueryDTO> {
 *
 *     @Autowired
 *     public PerformanceQueryController(PerformanceQueryServiceImpl service) {
 *         setQueryService(service);
 *     }
 * }
 * }
 * </pre>
 *
 * <pre>
 * AI 파이프라인:
 *   UserQueryDTO (원본 질의)  ← 이 컨트롤러가 담당
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
 * @param <S> {@link UserQueryService} 구현체
 * @param <Q> {@link UserQueryDTO} 하위 질의 객체
 */
@Slf4j
@Tag(name = "UserQueryAbstractController", description = "AI Query 파이프라인 공통 컨트롤러")
public abstract class UserQueryAbstractController<S extends UserQueryService<Q>, Q extends UserQueryDTO> {

    private S queryService;

    /**
     * 하위 클래스에서 사용할 QueryService를 주입합니다.
     *
     * @param queryService 질의 처리 서비스 구현체
     */
    public void setQueryService(S queryService) {
        this.queryService = queryService;
    }

    protected S getQueryService() {
        return queryService;
    }

    // ──────────────────────────────────────────────────────────────
    // Stream
    // ──────────────────────────────────────────────────────────────

    /**
     * 질의에 대한 AI 응답을 실시간 스트리밍으로 반환합니다.
     *
     * <p>POST /query/stream</p>
     *
     * @param query 사용자 질의 객체 (하위 타입)
     * @return 토큰 단위 스트리밍 응답 ({@code text/event-stream})
     */
    @Operation(summary = "[Stream] AI 응답 스트리밍", description = "질의를 받아 LLM 응답을 실시간 스트림으로 반환합니다.")
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> queryStream(@RequestBody Q query) {

        log.info("UserQueryAbstractController :: queryStream | sessionId={}, queryText={}",
                query.getSessionId(), query.getQueryText());

        if (!query.isValid()) {
            return Flux.error(new IllegalArgumentException("queryText 가 비어 있습니다."));
        }

        return queryService.stream(query);
    }

    /**
     * 진행 중인 스트림을 중단합니다.
     *
     * <p>GET /query/stop-stream?sessionId={sessionId}</p>
     *
     * @param sessionId 중단할 스트림의 세션 ID
     * @return 처리 결과 메시지
     */
    @Operation(summary = "[Stream] 스트림 중단", description = "sessionId 에 해당하는 진행 중인 스트림을 중단합니다.")
    @GetMapping(value = "/stop-stream")
    public ResponseEntity<String> stopStream(@RequestParam("sessionId") String sessionId) {

        log.info("UserQueryAbstractController :: stopStream | sessionId={}", sessionId);
        String result = queryService.stopStream(sessionId);
        return ResponseEntity.ok(result);
    }

    // ──────────────────────────────────────────────────────────────
    // Generate (단건 응답)
    // ──────────────────────────────────────────────────────────────

    /**
     * 질의에 대한 AI 응답을 단건으로 반환합니다.
     *
     * <p>POST /query/generate</p>
     *
     * @param query 사용자 질의 객체 (하위 타입)
     * @return 완성된 응답 문자열 ({@link Mono})
     */
    @Operation(summary = "[Generate] AI 단건 응답", description = "질의를 받아 완성된 LLM 응답을 단건으로 반환합니다.")
    @PostMapping(value = "/generate")
    public Mono<ResponseEntity<String>> queryGenerate(@RequestBody Q query) {

        log.info("UserQueryAbstractController :: queryGenerate | sessionId={}, queryText={}",
                query.getSessionId(), query.getQueryText());

        if (!query.isValid()) {
            return Mono.just(ResponseEntity.badRequest().body("queryText 가 비어 있습니다."));
        }

        return queryService.generate(query)
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    log.error("queryGenerate 오류 | sessionId={} | error={}",
                            query.getSessionId(), e.getMessage(), e);
                    return Mono.just(ResponseEntity.internalServerError()
                            .body("AI 응답 생성 중 오류가 발생했습니다."));
                });
    }

    // ──────────────────────────────────────────────────────────────
    // Validate
    // ──────────────────────────────────────────────────────────────

    /**
     * 질의 유효성 검증만 수행하고 AI 호출 없이 결과를 반환합니다.
     *
     * <p>POST /query/validate</p>
     *
     * @param query 사용자 질의 객체
     * @return 유효 여부 메시지
     */
    @Operation(summary = "[Validate] 질의 유효성 검증", description = "AI 호출 없이 질의 객체의 유효성만 확인합니다.")
    @PostMapping(value = "/validate")
    public ResponseEntity<String> validateQuery(@RequestBody Q query) {

        log.info("UserQueryAbstractController :: validateQuery | sessionId={}", query.getSessionId());

        if (!query.isValid()) {
            return ResponseEntity.badRequest().body("유효하지 않은 질의입니다. (queryText 확인 필요)");
        }
        return ResponseEntity.ok("유효한 질의입니다. | " + query);
    }
}
