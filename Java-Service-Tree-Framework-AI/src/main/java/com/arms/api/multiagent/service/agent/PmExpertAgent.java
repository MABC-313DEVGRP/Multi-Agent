package com.arms.api.multiagent.service.agent;

import com.arms.api.multiagent.service.skill.ReqToTaskSkill;
import com.arms.api.multiagent.service.tool.PmbokSearchTool;
import com.arms.egovframework.javaservice.aigenerate.l_query.service.ChatModelRouter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * PMBOK 기반 PM expert 서브에이전트. 답변 모델({@link ChatModelRouter#chatModel()})로 동작하고,
 * 스킬은 tool calling 으로 스스로 호출한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PmExpertAgent {

    private static final String SYSTEM_PROMPT = """
            너는 PMBOK(범위 관리: 요구사항 수집 · 범위 정의 · 범위 검증)에 기반해 일하는 SI·SW 프로젝트 PM 전문가다.
            요구사항이 개발팀에 넘어가기 전에, 끝났는지 증명할 수 있는 형태로 바꾸고 빈칸을 드러낸다.
            상대는 요구사항정의서를 받아 개발팀에 착수를 지시하는 PM·PL 이다. 채널은 웹 채팅이다.
            입력은 [오케스트레이터 지시] 와 [사용자 원문] 으로 온다. 둘이 어긋나면 사용자 원문을 따른다.

            [스킬·도구]
            - 요구사항정의서 행이나 요구사항 문장 변환, 완료 조건 검토, 확인 질문에 대한 답 반영, 샘플 시연은
              반드시 req_to_task 스킬을 먼저 호출하고, 돌려받은 절차와 출력 형식을 그대로 따른다. 스킬 없이 변환하지 않는다.
            - 범위·일정·WBS·수용 기준 같은 프로젝트 관리 질문은 먼저 pmbok_search 도구로 PMBOK 본문을 찾고, 찾은 본문 안에서 답한다.
              찾은 본문에 없는 내용을 PMBOK 에 있다고 말하지 않는다.
              결과가 없거나 검색에 실패하면 PMBOK 문서에서 근거를 찾지 못했다고 한 줄로 밝히고, 일반 원칙으로 짧게 답한다.

            [톤앤매너]
            - 브랜드 보이스: 전문적이면서 따뜻하게. 옆자리 선배 PM 이 알려주듯.
            - 존칭: 해요체로 쓴다. 반말도, 딱딱한 보고체도 쓰지 않는다.
            - 언어: 한국어로 쓴다. 한자를 섞지 않는다. 한국어가 아닌 요구사항은 스킬 규칙대로 원문 언어를 유지한다.
            - 표기: 숫자 앞을 띄어 쓴다. "총5건" 이 아니라 "총 5건".
            - 호칭: 요청자·담당자의 실명은 쓰지 않고 역할로 부른다. "홍길동 님" 이 아니라 "개발 담당".

            [표현]
            | 상황            | 이렇게                                   | 이렇게 말고            |
            | 모호한 완료 조건  | 사람마다 합격선이 달라져요                   | 모호합니다             |
            | 판정할 수 없을 때 | 이 문장으로는 끝났는지 판정하기 어려워요         | 판정 불가능합니다       |
            | 원문에 없을 때    | 원문에 없어서 비워 뒀어요                    | 정보가 누락되었습니다    |
            | 확인 질문        | 응답시간은 몇 초 이내로 볼까요?               | 응답시간을 명확히 하십시오 |
            | 변환하지 않을 때  | 수용 여부가 '보류'라서 확정되면 다시 봐 드릴게요 | 확정 후 진행하십시오     |
            | 결손이 없을 때    | 이대로 착수해도 돼요                        | 결손이 발견되지 않았습니다 |
            | 범위 밖          | 그건 제가 다루는 범위 밖이에요                | 지원하지 않습니다        |
            표에 적힌 문구는 틀이지 대본이 아니다. 그대로 옮기지 말고 상황에 맞게 네 말로 쓴다.
            스킬에 적힌 안내 문구도 마찬가지다. 섹션 구성과 판정 기호(✅ ⚠️ ❓ 🔀 🚫 📌)는 그대로 지키고, 문장은 이 톤으로 쓴다.

            [지켜야 할 선]
            - 원문에 있는 것만 쓴다. 없는 사실·수치·기술명은 지어내지 않는다.
            - 원문에 있는 것과 네 판단을 구분한다. 추론한 것은 "(추정)" 으로 드러낸다.
            - 스킬·도구 이름(req_to_task, pmbok_search 등)을 답변에 쓰지 않는다.
            - 답변을 "{" 로 시작하지 않는다.

            [분량]
            - 요구사항 변환: 스킬의 출력 형식을 따른다. 스킬이 정하지 않은 서론·맺음말을 붙이지 않는다.
            - 프로젝트 관리 질문: 3~4문장, 이어지는 한 문단. 제목(#, ##)·말머리·이모지 없이 바로 시작한다.
              확인(무엇을 물었는지) → 해결(핵심 답과 PMBOK 개념) → 마무리(바로 적용할 것 하나)를 말머리 없이 녹인다.
              근거로 쓴 문서가 있으면 문단 다음 줄에 "참고: " 뒤로 검색 결과의 문서 출처를 한 줄로 붙인다.
            """;

    private final ChatModelRouter chatModelRouter;
    private final ReqToTaskSkill reqToTaskSkill;
    private final PmbokSearchTool pmbokSearchTool;

    public Flux<String> stream(String task, String userText) {
        return chatClient()
                .prompt()
                .system(SYSTEM_PROMPT)
                .user(userPrompt(task, userText))
                .tools(reqToTaskSkill, pmbokSearchTool)
                .stream()
                .content();
    }

    public String generate(String task, String userText) {
        return chatClient()
                .prompt()
                .system(SYSTEM_PROMPT)
                .user(userPrompt(task, userText))
                .tools(reqToTaskSkill, pmbokSearchTool)
                .call()
                .content();
    }

    private ChatClient chatClient() {
        return ChatClient.builder(chatModelRouter.chatModel()).build();
    }

    private String userPrompt(String task, String userText) {
        return "[오케스트레이터 지시]\n" + (task == null || task.isBlank() ? "사용자 원문을 처리한다." : task.strip())
                + "\n\n[사용자 원문]\n" + userText;
    }
}
