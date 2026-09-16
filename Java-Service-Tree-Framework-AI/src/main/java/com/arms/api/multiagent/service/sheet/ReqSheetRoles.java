package com.arms.api.multiagent.service.sheet;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 스킬(ReqToTaskSkill) STEP 1 역할 인식 표. 컬럼명·블록명은 조직마다 달라 이름이 아니라 역할로 매칭한다.
 */
public final class ReqSheetRoles {

    public static final String ID = "id";
    public static final String CATEGORY = "category";
    public static final String NAME = "name";
    public static final String CONTENT = "content";
    public static final String ADOPTION = "adoption";
    public static final String PRIORITY = "priority";
    public static final String STATUS = "status";
    public static final String ASSIGNEE = "assignee";
    public static final String REQUESTER = "requester";
    public static final String REMARK = "remark";

    public static final String BACKGROUND = "background";
    public static final String SCOPE = "scope";
    public static final String CONSTRAINT = "constraint";
    public static final String ACCEPTANCE = "acceptance";

    private static final List<Map.Entry<String, List<String>>> COLUMN_ROLES = List.of(
            Map.entry(ID, List.of("요구사항 ID", "ID", "No", "번호", "항목번호")),
            Map.entry(CATEGORY, List.of("대분류", "중분류", "소분류", "대/중/소분류", "구분", "모듈", "기능분류", "카테고리")),
            Map.entry(NAME, List.of("요구사항명", "기능명", "제목", "요구사항")),
            Map.entry(CONTENT, List.of("상세 내용", "상세", "설명", "내용", "요구사항 상세")),
            Map.entry(ADOPTION, List.of("수용 여부", "채택", "반영 여부", "검토 결과")),
            Map.entry(PRIORITY, List.of("우선순위", "중요도", "등급", "Priority")),
            Map.entry(STATUS, List.of("진행 현황", "상태", "진행 상태", "Status")),
            Map.entry(ASSIGNEE, List.of("담당자", "담당", "개발자", "Assignee")),
            Map.entry(REQUESTER, List.of("요청자", "요청 부서", "고객")),
            Map.entry(REMARK, List.of("비고", "특이사항", "Remark")));

    // 포함 일치라 검사 순서가 중요하다 — "비기능 요구사항"이 작업 범위의 "기능"보다 먼저 걸려야 한다
    private static final List<Map.Entry<String, List<String>>> BLOCK_ROLES = List.of(
            Map.entry(ACCEPTANCE, List.of("검증 기준", "인수 조건", "완료 조건", "테스트 기준")),
            Map.entry(CONSTRAINT, List.of("제약", "비기능 요구사항")),
            Map.entry(BACKGROUND, List.of("개요", "배경", "목적")),
            Map.entry(SCOPE, List.of("상세 기능 요구사항", "기능", "요구사항 상세", "입력/출력", "I/O")));

    private ReqSheetRoles() {
    }

    // 공백을 뺀 정확 일치 — "요구사항"과 "요구사항 ID"가 섞이지 않게 한다
    public static String roleOf(String header) {
        String key = normalize(header);
        if (key.isEmpty()) {
            return null;
        }
        return COLUMN_ROLES.stream()
                .filter(entry -> entry.getValue().stream().anyMatch(alias -> normalize(alias).equals(key)))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    // 포함 일치 — "[제약 조건 / 비기능 요구사항]" 같은 조직 표기를 받는다
    public static String blockRoleOf(String label) {
        String key = normalize(label);
        if (key.isEmpty()) {
            return null;
        }
        return BLOCK_ROLES.stream()
                .filter(entry -> entry.getValue().stream().anyMatch(alias -> key.contains(normalize(alias))))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    public static long score(List<String> row) {
        return row.stream().map(ReqSheetRoles::roleOf).filter(Objects::nonNull).distinct().count();
    }

    // 역할이 두 가지 이상 잡히고 그중 이름이나 내용이 있어야 요구사항 헤더로 본다
    public static boolean isHeader(List<String> row) {
        List<String> roles = row.stream().map(ReqSheetRoles::roleOf).filter(Objects::nonNull).toList();
        return roles.stream().distinct().count() >= 2 && (roles.contains(NAME) || roles.contains(CONTENT));
    }

    // 역할별 첫 컬럼 위치
    public static Map<String, Integer> roles(List<String> headers) {
        Map<String, Integer> roles = new LinkedHashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            String role = roleOf(headers.get(i));
            if (role != null) {
                roles.putIfAbsent(role, i);
            }
        }
        return roles;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }
}
