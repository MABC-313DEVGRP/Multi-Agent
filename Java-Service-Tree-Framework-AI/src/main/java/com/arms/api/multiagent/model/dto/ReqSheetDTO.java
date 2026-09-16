package com.arms.api.multiagent.model.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 채팅 요청에 붙어 오는 첨부 요구사항정의서. 서버는 파일을 보관하지 않으므로 화면이 매 요청마다 필요한 행을 다시 보낸다.
 */
@Getter
@NoArgsConstructor
public class ReqSheetDTO {

    private String fileName;

    // CSV 면 null
    private String sheetName;

    private List<String> sheetNames;

    private int totalRows;

    // ALL(전체 상세) | IDS(지정한 건 상세) | LIST(목록만)
    private String scope;

    private List<String> headers;

    private List<List<String>> rows;
}
