package com.arms.api.multiagent.service;

import com.arms.api.multiagent.model.dto.MultiAgentDTO;
import com.arms.api.multiagent.service.agent.PmExpertAgent;
import com.arms.api.multiagent.service.sheet.ReqSheetPromptComposer;
import com.arms.egovframework.javaservice.aigenerate.l_query.service.UserQueryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class MultiAgentServiceImpl implements UserQueryService<MultiAgentDTO> {

    private static final String PROGRESS_ORCHESTRATING = "질문을 이해하고 있어요";

    private static final String PROGRESS_PM_EXPERT = "PM expert agent 에게 맡겼어요";

    private static final String FALLBACK_ANSWER =
            "요구사항정의서 행이나 요구사항 문장을 붙여넣어 주세요. 자료가 마땅치 않으시면 \"샘플로 보여줘\" 라고 입력해 주세요.";

    private final MultiAgentOrchestrator orchestrator;
    private final PmExpertAgent pmExpertAgent;
    private final ReqSheetPromptComposer promptComposer;
    private final ObjectMapper objectMapper;

    private final ConcurrentHashMap<String, AtomicBoolean> streamStatus = new ConcurrentHashMap<>();

    @Override
    public Flux<String> stream(MultiAgentDTO query) {
        if (query.getSessionId() == null || query.getSessionId().isBlank()) {
            return Flux.error(new IllegalArgumentException("sessionId 가 비어 있습니다."));
        }

        // 오케스트레이터에는 첨부 사실만, PM expert 에는 첨부 행 원문까지 넘긴다
        String routingText = promptComposer.routingText(query.getQueryText(), query.getAttachment());
        String agentText = promptComposer.agentText(query.getQueryText(), query.getAttachment());
        String streamId = query.getSessionId();
        log.info("MultiAgentServiceImpl :: 요청 | streamId={}, pdServiceId={}, 입력길이={}, 첨부={}",
                streamId, query.getPdServiceId(), agentText.length(), query.getAttachment() != null);

        return Flux.concat(
                Flux.just(toProgressJson(PROGRESS_ORCHESTRATING)),
                orchestrator.decide(routingText).flatMapMany(decision -> decision.isDelegated()
                        ? Flux.concat(
                                Flux.just(toProgressJson(PROGRESS_PM_EXPERT)),
                                streamPmExpert(decision.task(), agentText, streamId))
                        : Flux.just(decision.hasAnswer() ? decision.answer() : FALLBACK_ANSWER)));
    }

    private Flux<String> streamPmExpert(String task, String userText, String streamId) {
        return Flux.defer(() -> {
                    streamStatus.put(streamId, new AtomicBoolean(false));
                    log.info("MultiAgentServiceImpl :: PM expert 스트리밍 시작 | streamId={}", streamId);

                    long requestStart = System.currentTimeMillis();
                    boolean[] firstToken = {true};

                    return pmExpertAgent.stream(task, userText)
                            .takeUntil(chunk -> streamStatus.getOrDefault(streamId, new AtomicBoolean(false)).get())
                            .doOnNext(chunk -> {
                                if (firstToken[0]) {
                                    firstToken[0] = false;
                                    log.info("MultiAgentServiceImpl :: 첫 토큰 도착 | streamId={}, 소요={}ms",
                                            streamId, System.currentTimeMillis() - requestStart);
                                }
                            })
                            .doOnComplete(() -> log.info("MultiAgentServiceImpl :: 스트리밍 완료 | streamId={}, 총 소요={}ms",
                                    streamId, System.currentTimeMillis() - requestStart))
                            .doOnError(e -> log.error("MultiAgentServiceImpl :: 스트리밍 오류 | streamId={} | error={}",
                                    streamId, e.getMessage(), e));
                })
                .doFinally(signalType -> streamStatus.remove(streamId));
    }

    @Override
    public Mono<String> generate(MultiAgentDTO query) {
        String routingText = promptComposer.routingText(query.getQueryText(), query.getAttachment());
        String agentText = promptComposer.agentText(query.getQueryText(), query.getAttachment());
        log.info("MultiAgentServiceImpl :: generate | sessionId={}, pdServiceId={}, 입력길이={}, 첨부={}",
                query.getSessionId(), query.getPdServiceId(), agentText.length(), query.getAttachment() != null);

        return orchestrator.decide(routingText).flatMap(decision -> decision.isDelegated()
                ? Mono.fromCallable(() -> pmExpertAgent.generate(decision.task(), agentText))
                        .subscribeOn(Schedulers.boundedElastic())
                : Mono.just(decision.hasAnswer() ? decision.answer() : FALLBACK_ANSWER));
    }

    @Override
    public String stopStream(String sessionId) {
        AtomicBoolean flag = streamStatus.get(sessionId);
        if (flag != null) {
            flag.set(true);
            return "스트림 중단 요청이 접수되었습니다. sessionId=" + sessionId;
        }
        return "진행 중인 스트림이 없습니다. sessionId=" + sessionId;
    }

    private String toProgressJson(String message) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("progress", message);
        return root.toString();
    }
}
