package com.arms.api.multiagent.service;

import com.arms.egovframework.javaservice.aigenerate.l_query.service.ChatModelRouter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class MultiAgentOrchestrator {

    private static final Duration ORCHESTRATION_TIMEOUT = Duration.ofSeconds(20);

    private static final String SYSTEM_PROMPT = """
            너는 PMBOK 기반 PM 어시스턴트다.
            SI·SW 프로젝트에서 요구사항을 개발 착수 지시서로 바꾸고, 끝났는지 판정할 수 없는 완료 조건을 착수 전에 짚어 준다.
            상대는 요구사항정의서를 받아 개발팀에 착수를 지시하는 PM·PL 이다. 채널은 웹 채팅이다.

            너는 답을 쓰지 않고 길만 고른다. 길은 둘뿐이다. PM 전문가 에이전트에게 맡기거나, 직접 짧게 답하거나.

            [에이전트에게 맡기는 경우]
            - 요구사항을 다루는 요청이면 반드시 pm_expert 를 호출한다.
              요구사항정의서 행이나 요구사항 문장 붙여넣기, 착수 지시서 변환, 완료 조건 검토, 확인 질문에 대한 답이 전부 여기 해당한다.
              예: 엑셀에서 복사한 요구사항 행, "이 요구사항 정리해줘", "완료 조건 뽑아줘", "응답시간은 3초 이내로 해줘"
            - 요청에 [첨부 파일] 이 붙어 있으면 무엇을 물었든 pm_expert 를 호출한다.
            - 샘플을 보여 달라는 요청이면 pm_expert 를 호출한다.
              예: "샘플로 보여줘"
            - 범위·일정·WBS·수용 기준 같은 프로젝트 관리 질문이면 pm_expert 를 호출한다.
              예: "범위 기술서랑 WBS 차이가 뭐야?", "인수 조건은 어떻게 써야 해?"
            - 확신이 서지 않아도 되묻지 않는다. pm_expert 를 호출한다.
            - task 에는 맡길 일을 한두 문장으로 쓴다. 사용자 원문은 따로 전달되므로 옮겨 적지 않는다.
            - 부를 거면 곧바로 부른다. 부르기 전에 예고 문장을 쓰지 않는다.
              도구를 부를 때 네가 쓴 문장은 사용자에게 전달되지 않는다. 답변은 에이전트가 따로 작성한다.

            [직접 답하는 경우]
            - 인사, 잡담, 네가 누구인지, 네가 무엇을 할 수 있는지, 프로젝트 관리와 무관한 질문.
              이때만 텍스트로 답한다.

            절대 지켜야 할 선:
            - 요구사항을 직접 변환하거나 완료 조건을 판정하지 않는다. 그 일은 반드시 에이전트에게 맡긴다.
            - 에이전트·스킬·도구 이름(pm_expert, req_to_task 등)을 답변에 쓰지 않는다.

            말하는 방식 (직접 답할 때만 적용):
            - 아래 문구는 틀이지 대본이 아니다. 그대로 옮기지 말고 그때 상황에 맞게 네 말로 쓴다.
              같은 인사에 늘 같은 문장을 내놓지 않는다.
            - 2~3줄. 이모지·마크다운 제목(#, ##)·답변 제목·목록을 쓰지 않는다.
            - 해요체로 쓴다. "~입니다"보다 "~예요", "~있어요", "~드릴게요" 를 쓴다.
              옆자리 동료가 알려주듯 편하게. 다만 반말은 쓰지 않는다.
            - 사전적 자기소개를 하지 않는다. 무엇인지가 아니라 무엇을 해주는지 말한다.
              "요구사항을 착수 지시서로 변환하는 PM 어시스턴트입니다"
                -> "요구사항 한 줄 붙여 주시면 개발팀에 바로 넘길 수 있게 정리하고, 애매한 완료 조건을 먼저 짚어 드려요."
            - 기능을 명사로 나열하지 않는다. 사용자가 실제로 할 법한 요청으로 보여준다.
              "요구사항 변환, 완료 조건 판정, 결손 진단을 지원합니다"
                -> "엑셀에서 요구사항 행을 복사해 붙여 보시거나, 자료가 없으면 '샘플로 보여줘' 라고 해 보세요."
            - 인사에는 인사로 짧게 답하고 어떤 요구사항을 보고 싶은지 묻는다.
              묻지도 않은 기능 목록을 나열하지 않는다.
            - 답할 수 없는 질문이라도 거기서 끝내지 않는다. 갈 곳을 준다.
              못 하는 것을 먼저 늘어놓지 말고, 대신 봐 줄 수 있는 것을 준다.
              "안 됩니다" -> "그건 어렵고, 대신 ~는 봐 드릴 수 있어요"
              "잘 모르겠습니다" -> "그건 제가 다루는 범위 밖이에요"
              "확인해 볼게요", "잠시만요" -> (쓰지 않는다. 예고 없이 바로 답한다)
            - 한국어로 답한다.
            """;

    private final ChatModelRouter chatModelRouter;
    private final MultiAgentSubAgentTools subAgentTools;
    private final ObjectMapper objectMapper;

    public Mono<Decision> decide(String userText) {
        return Mono.fromCallable(() -> ChatClient.builder(chatModelRouter.routingModel()).build()
                        .prompt()
                        .system(SYSTEM_PROMPT)
                        .user(userText)
                        .tools(subAgentTools)
                        .toolContext(Map.of(MultiAgentSubAgentTools.USER_TEXT, userText))
                        .options(ToolCallingChatOptions.builder().internalToolExecutionEnabled(false).build())
                        .call()
                        .chatResponse())
                .subscribeOn(Schedulers.boundedElastic())
                .timeout(ORCHESTRATION_TIMEOUT)
                .map(this::parse);
    }

    private Decision parse(ChatResponse response) {
        if (response == null || response.getResults() == null) {
            return Decision.answered(null);
        }

        StringBuilder text = new StringBuilder();
        for (Generation generation : response.getResults()) {
            AssistantMessage message = generation.getOutput();
            if (message == null) {
                continue;
            }
            for (AssistantMessage.ToolCall call : message.getToolCalls()) {
                log.info("MultiAgentOrchestrator :: 서브에이전트 선택 | tool={}", call.name());
                if (MultiAgentSubAgentTools.PM_EXPERT.equals(call.name())) {
                    return Decision.delegated(call.name(), readTask(call.arguments()));
                }
                log.warn("MultiAgentOrchestrator :: 알 수 없는 서브에이전트 | tool={}", call.name());
            }
            if (message.getText() != null && !message.getText().isBlank()) {
                text.append(message.getText().strip());
            }
        }

        log.info("MultiAgentOrchestrator :: 직접 답변 | 길이={}", text.length());
        return Decision.answered(text.toString());
    }

    private String readTask(String arguments) {
        try {
            JsonNode task = objectMapper.readTree(arguments).get("task");
            return task == null ? null : task.asText();
        } catch (Exception e) {
            log.warn("MultiAgentOrchestrator :: task 인자 파싱 실패 | error={}", e.getMessage());
            return null;
        }
    }

    public static final class Decision {

        private final String subAgent;
        private final String task;
        private final String answer;

        private Decision(String subAgent, String task, String answer) {
            this.subAgent = subAgent;
            this.task = task;
            this.answer = answer;
        }

        private static Decision delegated(String subAgent, String task) {
            return new Decision(subAgent, task, null);
        }

        private static Decision answered(String answer) {
            return new Decision(null, null, answer);
        }

        public boolean isDelegated() {
            return subAgent != null;
        }

        public String subAgent() {
            return subAgent;
        }

        public String task() {
            return task;
        }

        public String answer() {
            return answer;
        }

        public boolean hasAnswer() {
            return answer != null && !answer.isBlank();
        }
    }
}
