package com.arms.api.multiagent.service.tool;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PmbokSearchTool {

    public static final String NAME = "pmbok_search";

    private static final String NO_RESULT = "검색 결과 없음";

    // 본문 청크만 찾는다. summary 청크는 요약이라 근거로 인용하기에 부족하다.
    private static final String FILTER_EXPRESSION = "chunk_type == 'content'";

    // 질문 하나에 정의·관계·절차가 섞여 있어 support(1건)보다 넉넉히 가져온다.
    private static final int TOP_K = 3;

    // support 와 같은 기준. 이보다 멀면 근거로 쓰지 않는다.
    private static final double THRESHOLD = 0.5;

    private final VectorStore vectorStore;

    @Tool(name = NAME, description = "PMBOK 문서에서 질문과 관련된 본문을 찾아 출처와 함께 돌려준다. "
            + "범위·일정·원가·품질·자원·리스크·WBS·수용 기준·범위 검증 같은 프로젝트 관리 질문에 답하기 전에 근거를 찾을 때 사용한다. "
            + "찾지 못하면 '검색 결과 없음' 을 돌려준다.")
    public String pmbokSearch(@ToolParam(description = "찾을 내용. 질문의 핵심 개념을 한국어 한 문장으로 쓴다.") String query) {
        if (query == null || query.isBlank()) {
            return NO_RESULT;
        }

        List<Document> docs;
        try {
            docs = vectorStore.similaritySearch(SearchRequest.builder()
                    .query(query)
                    .topK(TOP_K)
                    .similarityThreshold(THRESHOLD)
                    .filterExpression(FILTER_EXPRESSION)
                    .build());
        } catch (Exception e) {
            log.warn("PmbokSearchTool :: 검색 실패 | query={} | error={}", query, e.getMessage());
            return "검색 실패";
        }

        log.info("PmbokSearchTool :: 검색 | query={}, 결과={}", query, docs == null ? 0 : docs.size());
        if (docs == null || docs.isEmpty()) {
            return NO_RESULT;
        }

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < docs.size(); i++) {
            Document doc = docs.get(i);
            result.append("[문서 ").append(i + 1).append("] ").append(label(doc)).append('\n')
                    .append(doc.getText() == null ? "" : doc.getText().strip())
                    .append("\n\n");
        }
        return result.toString().strip();
    }

    // support 의 참고 문서 표기와 같다: 출처 · 절 제목(없으면 계층 경로) · 쪽
    private String label(Document doc) {
        List<String> parts = new ArrayList<>();
        addIfPresent(parts, meta(doc, "source"));
        String section = meta(doc, "section_title");
        addIfPresent(parts, section != null ? section : meta(doc, "hierarchy_path"));
        String page = meta(doc, "page_number");
        if (page != null) {
            parts.add("p." + page);
        }
        return parts.isEmpty() ? "PMBOK" : String.join(" · ", parts);
    }

    private void addIfPresent(List<String> parts, String value) {
        if (value != null) {
            parts.add(value);
        }
    }

    private String meta(Document doc, String key) {
        Object value = doc.getMetadata().get(key);
        if (value == null) {
            return null;
        }
        String text = value.toString().strip();
        return text.isEmpty() ? null : text;
    }
}
