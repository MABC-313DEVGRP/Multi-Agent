package com.arms.api.multiagent.service.sheet;

import com.arms.api.multiagent.model.dto.ReqSheetDTO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 첨부 요구사항정의서를 모델 입력으로 옮긴다.
 * 오케스트레이터(외부 라우팅 모델)에는 파일이 붙었다는 사실만, PM expert 에는 행 원문까지 넘긴다.
 */
@Component
public class ReqSheetPromptComposer {

    private static final String SCOPE_ALL = "ALL";
    private static final String SCOPE_IDS = "IDS";

    public String routingText(String queryText, ReqSheetDTO attachment) {
        if (attachment == null) {
            return queryText;
        }
        return queryText + "\n[첨부 파일] " + attachment.getFileName() + sheetLabel(attachment)
                + " · 총 " + attachment.getTotalRows() + "건";
    }

    public String agentText(String queryText, ReqSheetDTO attachment) {
        if (attachment == null) {
            return queryText;
        }
        List<String> headers = attachment.getHeaders() == null ? List.of() : attachment.getHeaders();
        List<List<String>> rows = attachment.getRows() == null ? List.of() : attachment.getRows();

        List<String> lines = new ArrayList<>();
        lines.add("[첨부 파일]");
        lines.add("- 파일: " + attachment.getFileName());
        lines.add(attachment.getSheetName() == null
                ? "- 형식: CSV 파일"
                : "- 시트: " + attachment.getSheetName() + otherSheets(attachment));
        lines.add("- 데이터 행: 총 " + attachment.getTotalRows() + "건");
        lines.add("- 전달 범위: " + scopeLabel(attachment.getScope(), rows.size()));
        lines.add("");
        lines.add("[사용자 요청]");
        lines.add(queryText);
        lines.add("");
        lines.add("[첨부 행]");

        // 행 경계를 확정해 넘긴다 — 셀 안 줄바꿈 때문에 행 수를 다시 세지 않게 한다
        for (int i = 0; i < rows.size(); i++) {
            lines.add("--- " + (i + 1) + "번째 행 ---");
            List<String> row = rows.get(i);
            for (int column = 0; column < headers.size(); column++) {
                String value = column < row.size() && row.get(column) != null ? row.get(column) : "";
                lines.add(headers.get(column) + (value.contains("\n") ? ":\n" : ": ") + value);
            }
        }
        return String.join("\n", lines);
    }

    private String sheetLabel(ReqSheetDTO attachment) {
        return attachment.getSheetName() == null ? " · CSV" : " · 시트 " + attachment.getSheetName();
    }

    private String otherSheets(ReqSheetDTO attachment) {
        List<String> names = attachment.getSheetNames();
        return names == null || names.size() < 2 ? "" : " (파일의 시트: " + String.join(", ", names) + ")";
    }

    private String scopeLabel(String scope, int count) {
        if (SCOPE_ALL.equals(scope)) {
            return "전체 " + count + "건 상세";
        }
        if (SCOPE_IDS.equals(scope)) {
            return "지정한 " + count + "건 상세";
        }
        return "목록만 (6건 이상이라 상세는 \"전체\" 또는 요구사항 ID 를 지정하면 전달됨)";
    }
}
