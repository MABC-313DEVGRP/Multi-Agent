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
package com.arms.egovframework.javaservice.aigenerate.l_query.service;

import com.arms.egovframework.javaservice.aigenerate.l_query.model.UserQueryDTO;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * AI Query 파이프라인 서비스 인터페이스
 *
 * <p>{@link com.arms.egovframework.javaservice.aigenerate.l_query.controller.UserQueryAbstractController}와
 * 쌍을 이루는 서비스 계층 규약입니다.</p>
 *
 * <pre>
 * 구현 예시:
 *
 * {@code
 * @Service
 * @RequiredArgsConstructor
 * public class PerformanceQueryServiceImpl
 *         implements UserQueryService<PerformanceQuery> {
 *
 *     private final ChatModel chatModel;
 *     private final VectorStore vectorStore;
 *
 *     @Override
 *     public Flux<String> stream(PerformanceQuery query) { ... }
 *
 *     @Override
 *     public Mono<String> generate(PerformanceQuery query) { ... }
 *
 *     @Override
 *     public String stopStream(String sessionId) { ... }
 * }
 * }
 * </pre>
 *
 * @param <Q> {@link UserQueryDTO} 하위 질의 객체 타입
 */
public interface UserQueryService<Q extends UserQueryDTO> {

    /**
     * 질의를 AI 파이프라인에 전달하고 응답을 실시간 스트림으로 반환합니다.
     *
     * @param query 사용자 질의 객체
     * @return 토큰 단위 스트리밍 응답
     */
    Flux<String> stream(Q query);

    /**
     * 질의를 AI 파이프라인에 전달하고 완성된 응답을 단건으로 반환합니다.
     *
     * @param query 사용자 질의 객체
     * @return 완성된 응답 문자열
     */
    Mono<String> generate(Q query);

    /**
     * 진행 중인 스트림을 sessionId 기준으로 중단합니다.
     *
     * @param sessionId 중단할 스트림의 세션 ID
     * @return 처리 결과 메시지
     */
    String stopStream(String sessionId);
}
