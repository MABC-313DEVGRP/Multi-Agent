    package com.arms.api.multiagent.service;

import com.arms.api.multiagent.model.dto.MultiAgentDTO;
import com.arms.api.multiagent.service.pm.PmAgentService;
import com.arms.egovframework.javaservice.aigenerate.l_query.service.UserQueryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.stream.Collectors;

/**
 * Orchestrator — MABC 2026 결선 프로토타입(MultiAgent)의 진입점.
 *
 * <p>PM 에이전트(req-to-task) 단일 경로로 위임한다. 라우팅 대상이 PM 하나뿐이라
 * 별도 분류 단계 없이 바로 위임한다.</p>
 */
@Slf4j
@Service
public class MultiAgentServiceImpl implements UserQueryService<MultiAgentDTO> {

    private final PmAgentService pmAgentService;

    public MultiAgentServiceImpl(PmAgentService pmAgentService) {
        this.pmAgentService = pmAgentService;
    }

    @Override
    public Flux<String> stream(MultiAgentDTO query) {
        if (query.getSessionId() == null || query.getSessionId().isBlank()) {
            return Flux.error(new IllegalArgumentException("sessionId 가 비어 있습니다."));
        }
        if (!query.isValid()) {
            return Flux.error(new IllegalArgumentException("queryText 가 비어 있습니다."));
        }

        return pmAgentService.stream(query.getQueryText(), query.getSessionId());
    }

    @Override
    public Mono<String> generate(MultiAgentDTO query) {
        if (!query.isValid()) {
            return Mono.error(new IllegalArgumentException("queryText 가 비어 있습니다."));
        }
        return pmAgentService.stream(query.getQueryText(), query.getSessionId())
                .collect(Collectors.joining());
    }

    @Override
    public String stopStream(String sessionId) {
        return pmAgentService.stopStream(sessionId);
    }
}
