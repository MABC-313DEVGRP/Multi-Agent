package com.arms.api.multiagent.service.pm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * PM 에이전트 — MABC 2026 결선 프로토타입(MultiAgent)의 프로젝트 관리 담당 에이전트.
 *
 * <p>Orchestrator가 라우팅해 준 질의를 Solar Pro 4로 처리한다. {@link PmAgentTools}에 등록된
 * 스킬(현재는 req-to-task)을 tool-calling으로 스스로 판단해 호출한다 — 별도 규칙 매칭 없이
 * 모델이 스킬 description을 보고 결정한다.</p>
 */
@Slf4j
@Service
public class PmAgentService {

    private static final String SYSTEM_PROMPT = """
            너는 ARMS의 PM(프로젝트 관리) 에이전트다. 사용자 질의를 보고 네가 가진 스킬 중
            적합한 것이 있으면 반드시 먼저 호출해 상세 규칙을 확인한 뒤, 그 규칙을 정확히 따라
            응답하라. 스킬 설명과 맞지 않는 질문(잡담 등)이면 스킬을 호출하지 말고, 네가 무엇을
            도와줄 수 있는지 한두 문장으로 안내하라.
            """;

    private final ChatClient chatClient;
    private final PmAgentTools pmAgentTools;
    private final ConcurrentHashMap<String, AtomicBoolean> streamStatus = new ConcurrentHashMap<>();

    public PmAgentService(OpenAiChatModel solarProChatModel, PmAgentTools pmAgentTools) {
        this.chatClient = ChatClient.builder(solarProChatModel).build();
        this.pmAgentTools = pmAgentTools;
    }

    public Flux<String> stream(String queryText, String sessionId) {
        return Flux.defer(() -> {
                    streamStatus.put(sessionId, new AtomicBoolean(false));
                    log.info("PmAgentService :: 스트리밍 시작 | sessionId={}", sessionId);

                    return chatClient.prompt()
                            .system(SYSTEM_PROMPT)
                            .user(queryText)
                            .tools(pmAgentTools)
                            .stream()
                            .content()
                            .takeUntil(chunk -> streamStatus.getOrDefault(sessionId, new AtomicBoolean(false)).get());
                })
                .doFinally(signal -> streamStatus.remove(sessionId));
    }

    public String stopStream(String sessionId) {
        AtomicBoolean flag = streamStatus.get(sessionId);
        if (flag != null) {
            flag.set(true);
            return "스트림 중단 요청이 접수되었습니다. sessionId=" + sessionId;
        }
        return "진행 중인 스트림이 없습니다. sessionId=" + sessionId;
    }
}
