package com.arms.api.multiagent.service;

import com.arms.api.multiagent.service.agent.PmExpertAgent;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MultiAgentSubAgentTools {

    public static final String PM_EXPERT = "pm_expert";

    static final String USER_TEXT = "userText";

    private final PmExpertAgent pmExpertAgent;

    @Tool(name = PM_EXPERT, description = "PM 전문가. PMBOK 기반으로 요구사항정의서 행·요구사항 문장을 개발 착수 지시서로 변환하고, "
            + "완료 조건 측정 가능성 판정·결손 진단·확인 질문 답 반영·샘플 시연을 수행한다. "
            + "범위·일정·WBS 등 프로젝트 관리 질문에도 답한다.")
    public String pmExpert(
            @ToolParam(description = "서브에이전트에게 맡길 일 한두 문장. 사용자 원문은 따로 전달되므로 옮겨 적지 않는다.") String task,
            ToolContext toolContext) {
        return pmExpertAgent.generate(task, String.valueOf(toolContext.getContext().get(USER_TEXT)));
    }
}
