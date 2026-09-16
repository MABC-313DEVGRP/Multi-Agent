package com.arms.api.multiagent.service.sheet;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 스킬 출력(ReqToTaskSkill §8 마크다운)을 요구사항 ID 별 블록으로 나눈다.
 * 파일 산출물은 LLM 을 다시 부르지 않고, 이미 대화에 나온 답변을 이 규칙으로 옮긴다.
 */
@Component
public class ReqTaskAnswerParser {

    public static final String TITLE = "title";
    public static final String BACKGROUND = ReqSheetRoles.BACKGROUND;
    public static final String SCOPE = ReqSheetRoles.SCOPE;
    public static final String ACCEPTANCE = ReqSheetRoles.ACCEPTANCE;
    public static final String CONSTRAINT = ReqSheetRoles.CONSTRAINT;
    public static final String PREMISE = "premise";
    public static final String DELIVERABLE = "deliverable";
    public static final String DEFECT = "defect";

    // "### [REQ-F-014] 주문 취소 처리"
    private static final Pattern SECTION = Pattern.compile("^#{2,4}\\s*\\[([^\\]]+)\\]\\s*(.*)$");
    // "**■ 완료 조건**"
    private static final Pattern BLOCK = Pattern.compile("^\\**\\s*■\\s*(배경|작업 범위|완료 조건|제약|전제|산출물|우선순위)");
    private static final Pattern DEFECT_HEAD = Pattern.compile("^#{3,4}\\s*결손 진단");
    // 확인 질문·요약은 파일에 옮기지 않는다
    private static final Pattern SECTION_END = Pattern.compile("^#{3,4}\\s*(확인 질문|요약)");

    private static final Map<String, String> BLOCK_KEYS = Map.of(
            "배경", BACKGROUND,
            "작업 범위", SCOPE,
            "완료 조건", ACCEPTANCE,
            "제약", CONSTRAINT,
            "전제", PREMISE,
            "산출물", DELIVERABLE,
            "우선순위", "priority");

    private static final Set<String> CONTENT_KEYS = Set.of(BACKGROUND, SCOPE, ACCEPTANCE, CONSTRAINT, PREMISE, DELIVERABLE);

    public Map<String, Map<String, List<String>>> parse(List<String> answers) {
        Map<String, Map<String, List<String>>> sections = new LinkedHashMap<>();
        if (answers == null) {
            return sections;
        }

        for (String answer : answers) {
            Map<String, List<String>> current = null;
            String block = null;
            for (String raw : answer.split("\n")) {
                String line = raw.strip();

                Matcher head = SECTION.matcher(line);
                if (head.matches()) {
                    current = new LinkedHashMap<>();
                    current.put(TITLE, List.of(clean(head.group(2))));
                    // 같은 ID 가 다시 나오면 나중 답변(답변 반영)이 이긴다
                    sections.put(head.group(1).strip(), current);
                    block = null;
                    continue;
                }
                if (current == null) {
                    continue;
                }
                if (SECTION_END.matcher(line).find()) {
                    current = null;
                    block = null;
                    continue;
                }
                if (DEFECT_HEAD.matcher(line).find()) {
                    block = DEFECT;
                    continue;
                }
                Matcher blockHead = BLOCK.matcher(line);
                if (blockHead.find()) {
                    block = BLOCK_KEYS.get(blockHead.group(1));
                    continue;
                }
                if (block == null || line.isEmpty() || line.startsWith("━")) {
                    continue;
                }
                current.computeIfAbsent(block, key -> new ArrayList<>()).add(clean(line));
            }
        }

        // 블록이 하나도 없는 섹션(🚫 변환 제외 안내 등)은 변환되지 않은 것으로 본다
        sections.values().removeIf(section -> section.keySet().stream().noneMatch(CONTENT_KEYS::contains));
        return sections;
    }

    private String clean(String line) {
        return line.replace("**", "").strip();
    }
}
